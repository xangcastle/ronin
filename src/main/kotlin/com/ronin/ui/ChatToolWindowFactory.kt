package com.ronin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.ronin.service.LLMService
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.ronin.MyBundle
import java.awt.BorderLayout
import javax.swing.*

class ChatToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatToolWindow = ChatToolWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(chatToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
    }

    class ChatToolWindow(private val project: Project) {
        private val mainPanel = JPanel(BorderLayout())
        private val chatArea = JTextArea()
        private val inputField = JTextField()
        private val sendButton = JButton(MyBundle.message("toolwindow.send"))
        private val attachButton = JButton(MyBundle.message("toolwindow.attach"))
        private val clearButton = JButton(MyBundle.message("toolwindow.clear"))
        private val modelComboBox = com.intellij.openapi.ui.ComboBox<String>()

        companion object {
            private const val KEY = "RoninChatToolWindow"
            private const val MAX_HISTORY_CHARS = 50000

            fun getInstance(project: Project): ChatToolWindow? {
                val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project).getToolWindow("Ronin Chat") ?: return null
                val content = toolWindow.contentManager.contents.firstOrNull() ?: return null
                val component = content.component as? JComponent ?: return null
                return component.getClientProperty(KEY) as? ChatToolWindow
            }
        }

        init {
            mainPanel.putClientProperty(KEY, this)
            val connection = ApplicationManager.getApplication().messageBus.connect(project)
            connection.subscribe(com.ronin.settings.RoninSettingsNotifier.TOPIC, object : com.ronin.settings.RoninSettingsNotifier {
                override fun settingsChanged(settings: com.ronin.settings.RoninSettingsState) {
                    updateModelList()
                }
            })
            chatArea.isEditable = false
            mainPanel.add(JScrollPane(chatArea), BorderLayout.CENTER)

            val bottomPanel = JPanel(BorderLayout())
            bottomPanel.add(inputField, BorderLayout.CENTER)
            
            val controlsPanel = JPanel(BorderLayout())
            
            // Model Selector
            val modelPanel = JPanel()
            modelPanel.add(JLabel(MyBundle.message("toolwindow.model")))
            modelPanel.add(modelComboBox)
            controlsPanel.add(modelPanel, BorderLayout.WEST)

            // Buttons
            val buttonPanel = JPanel()
            buttonPanel.add(attachButton)
            buttonPanel.add(clearButton)
            buttonPanel.add(sendButton)
            controlsPanel.add(buttonPanel, BorderLayout.EAST)
            
            bottomPanel.add(controlsPanel, BorderLayout.SOUTH)
            
            mainPanel.add(bottomPanel, BorderLayout.SOUTH)

            sendButton.addActionListener { sendMessage() }
            inputField.addActionListener { sendMessage() }
            attachButton.addActionListener { attachImage() }
            clearButton.addActionListener { clearChat() }
            
            // Initialize models
            updateModelList()
            
            // Listen for model changes to save state
            modelComboBox.addActionListener {
                val selected = modelComboBox.selectedItem as? String
                if (selected != null) {
                    com.ronin.settings.RoninSettingsState.instance.model = selected
                }
            }
        }
        
        fun updateModelList() {
            val settings = com.ronin.settings.RoninSettingsState.instance
            val provider = settings.provider
            val currentModel = settings.model
            
            val llmService = project.service<LLMService>()
            val modelsList = llmService.getAvailableModels(provider)
            val models = modelsList.toTypedArray()
            
            val model = DefaultComboBoxModel(models)
            modelComboBox.model = model
            
            if (models.contains(currentModel)) {
                modelComboBox.selectedItem = currentModel
            } else if (models.isNotEmpty()) {
                modelComboBox.selectedItem = models[0]
            }
        }

        private fun sendMessage() {
            val text = inputField.text
            if (text.isNotBlank()) {
                appendMessage(MyBundle.message("toolwindow.you"), text)
                inputField.text = ""
                
                // Asynchronously call LLM service to avoid freezing UI
                ApplicationManager.getApplication().executeOnPooledThread {
                    val llmService = project.service<LLMService>()
                    // Ensure we are using the latest model selection, although it's saved in state on change
                    val response = llmService.sendMessage(text)
                    appendMessage(MyBundle.message("toolwindow.ronin"), response)
                }
            }
        }

        fun appendMessage(role: String, message: String) {
            SwingUtilities.invokeLater {
                chatArea.append("$role: $message\n")
                
                // Limit chat history by characters to avoid memory issues
                val doc = chatArea.document
                if (doc.length > MAX_HISTORY_CHARS) {
                    try {
                        val charsToRemove = doc.length - MAX_HISTORY_CHARS
                        // Try to find a newline after the cutoff to delete clean lines
                        val text = chatArea.text
                        var cutoff = charsToRemove + 100 // Look a bit ahead
                        if (cutoff >= text.length) cutoff = text.length
                        
                        val newlineIndex = text.indexOf('\n', charsToRemove)
                        val removeUntil = if (newlineIndex in charsToRemove until cutoff) {
                            newlineIndex + 1
                        } else {
                            charsToRemove
                        }
                        
                        chatArea.replaceRange("", 0, removeUntil)
                    } catch (e: Exception) {
                        // Ignore potential bounds errors
                    }
                }
            }
        }

        private fun clearChat() {
            SwingUtilities.invokeLater {
                chatArea.text = ""
            }
        }

        private fun attachImage() {
            chatArea.append(MyBundle.message("toolwindow.system.image_mock"))
        }

        fun getContent(): JComponent {
            // Refresh models in case settings changed while window was closed/hidden
            updateModelList() 
            return mainPanel
        }
    }
}
