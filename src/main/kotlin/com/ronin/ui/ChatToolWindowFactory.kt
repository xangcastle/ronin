package com.ronin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.ronin.service.LLMService
import com.intellij.openapi.components.service
import java.awt.BorderLayout
import javax.swing.*
import java.util.concurrent.ConcurrentHashMap

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
        private val sendButton = JButton("Send")
        private val attachButton = JButton("Attach Image")
        private val modelComboBox = com.intellij.openapi.ui.ComboBox<String>()

        companion object {
            private val instances = ConcurrentHashMap<Project, ChatToolWindow>()

            fun getInstance(project: Project): ChatToolWindow? {
                return instances[project]
            }
        }

        init {
            instances[project] = this
            chatArea.isEditable = false
            mainPanel.add(JScrollPane(chatArea), BorderLayout.CENTER)

            val bottomPanel = JPanel(BorderLayout())
            bottomPanel.add(inputField, BorderLayout.CENTER)
            
            val controlsPanel = JPanel(BorderLayout())
            
            // Model Selector
            val modelPanel = JPanel()
            modelPanel.add(JLabel("Model:"))
            modelPanel.add(modelComboBox)
            controlsPanel.add(modelPanel, BorderLayout.WEST)

            // Buttons
            val buttonPanel = JPanel()
            buttonPanel.add(attachButton)
            buttonPanel.add(sendButton)
            controlsPanel.add(buttonPanel, BorderLayout.EAST)
            
            bottomPanel.add(controlsPanel, BorderLayout.SOUTH)
            
            mainPanel.add(bottomPanel, BorderLayout.SOUTH)

            sendButton.addActionListener { sendMessage() }
            inputField.addActionListener { sendMessage() }
            attachButton.addActionListener { attachImage() }
            
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
        
        private fun updateModelList() {
            val settings = com.ronin.settings.RoninSettingsState.instance
            val provider = settings.provider
            val currentModel = settings.model
            
            val models = when (provider) {
                "OpenAI" -> arrayOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")
                "Anthropic" -> arrayOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
                "Google" -> arrayOf("gemini-1.5-pro", "gemini-1.0-pro")
                "Kimi" -> arrayOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
                "Minimax" -> arrayOf("abab6.5-chat", "abab6-chat")
                "Ollama" -> arrayOf("llama3", "mistral", "gemma:7b", "codellama")
                else -> arrayOf("gpt-4o")
            }
            
            val model = DefaultComboBoxModel(models)
            modelComboBox.model = model
            
            if (models.contains(currentModel)) {
                modelComboBox.selectedItem = currentModel
            } else if (models.isNotEmpty()) {
                modelComboBox.selectedItem = models[0]
                settings.model = models[0] // Update state if invalid
            }
        }

        private fun sendMessage() {
            val text = inputField.text
            if (text.isNotBlank()) {
                appendMessage("You", text)
                inputField.text = ""
                
                // Asynchronously call LLM service to avoid freezing UI
                SwingUtilities.invokeLater {
                    // In a real app, this should run on a background thread
                    val llmService = project.service<LLMService>()
                    // Ensure we are using the latest model selection, although it's saved in state on change
                    val response = llmService.sendMessage(text)
                    appendMessage("Ronin", response)
                }
            }
        }

        fun appendMessage(role: String, message: String) {
            SwingUtilities.invokeLater {
                chatArea.append("$role: $message\n")
            }
        }

        private fun attachImage() {
            chatArea.append("[System]: Image attachment not yet implemented (Mock)\n")
        }

        fun getContent(): JComponent {
            // Refresh models in case settings changed while window was closed/hidden
            updateModelList() 
            return mainPanel
        }
    }
}
