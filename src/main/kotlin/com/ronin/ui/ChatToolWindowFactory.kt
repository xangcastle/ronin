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
        
        // Chat History: List of Maps {"role": "user"/"assistant", "content": "..."}
        private val messageHistory = mutableListOf<Map<String, String>>()
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
            chatArea.lineWrap = true
            chatArea.wrapStyleWord = true
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
            
            // Initial placeholder
            val initialModels = arrayOf(currentModel.ifBlank { "Loading..." })
            modelComboBox.model = DefaultComboBoxModel(initialModels)
            modelComboBox.isEnabled = false
            
            val llmService = project.service<LLMService>()
            
            ApplicationManager.getApplication().executeOnPooledThread {
                // Fetch dynamic list (network call)
                val modelsList = if (provider == "OpenAI") {
                    try {
                        val fetched = llmService.fetchAvailableModels(provider)
                        if (fetched.isNotEmpty()) fetched else llmService.getAvailableModels(provider)
                    } catch (e: Exception) {
                        llmService.getAvailableModels(provider)
                    }
                } else {
                    llmService.getAvailableModels(provider)
                }
                
                SwingUtilities.invokeLater {
                    val models = modelsList.toTypedArray()
                    val model = DefaultComboBoxModel(models)
                    modelComboBox.model = model
                    modelComboBox.isEnabled = true
                    
                    if (models.contains(currentModel)) {
                        modelComboBox.selectedItem = currentModel
                    } else if (models.isNotEmpty()) {
                        modelComboBox.selectedItem = models[0]
                        // Update settings default if we switched
                        com.ronin.settings.RoninSettingsState.instance.model = models[0]
                    }
                }
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
                    val contextService = project.service<com.ronin.service.ContextService>()
                    
                    // Gather Context
                    val activeFile = contextService.getActiveFileContent()
                    val projectStructure = contextService.getProjectStructure()
                    
                    val contextBuilder = StringBuilder()
                    if (activeFile != null) {
                        contextBuilder.append("Active File Content:\n```\n$activeFile\n```\n\n")
                    }
                    contextBuilder.append(projectStructure)
                    
                    // Send message to LLM
                    val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                    
                    // Parse: File edits are applied; Command extracted
                    val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                    
                    appendMessage(MyBundle.message("toolwindow.ronin"), result.text)
                    messageHistory.add(mapOf("role" to "user", "content" to text))
                    messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                    
                    // Handle Command Execution Loop
                    val command = result.commandToRun
                    if (command != null) {
                        appendMessage("System", "Running command: `${result.commandToRun}`...")
                        
                        // Execute in background to avoid freezing UI
                        ApplicationManager.getApplication().executeOnPooledThread {
                            val terminalService = project.service<com.ronin.service.TerminalService>()
                            
                            // Streaming Output Setup
                            SwingUtilities.invokeLater {
                                appendMessage("System", "Running command: `$command`\nOutput:")
                            }
                            
                            val output = terminalService.runCommand(command) { line ->
                                // Stream logic: Append line to the output message
                                // Simplifying by just appending to the chat log for now.
                                // Ideal would be to append to the specific block, but appendMessage adds to the bottom.
                                // We can just append the text.
                                SwingUtilities.invokeLater {
                                    chatArea.append(line)
                                }
                            }
                            
                            SwingUtilities.invokeLater {
                                appendMessage("System", "Command Finished.")
                                
                                // Automatic Feedback Loop: Send output back to LLM
                                // Construct a new "User" message that is actually the system output
                                val followUpPrompt = "Command Output:\n```\n$output\n```\nIf there are errors, please fix them."
                                
                                handleFollowUp(followUpPrompt)
                            }
                        }
                    }
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

        // Helper for the feedback loop
        private fun handleFollowUp(text: String) {
            // Add to UI immediately
             appendMessage(MyBundle.message("toolwindow.user") + " (Auto)", text)
             
            // Re-run the send logic (minus the input field clearing part)
            val settings = com.ronin.settings.RoninSettingsState.instance
            val apiKeyName = if (settings.provider == "Anthropic") "anthropicApiKey" else "openaiApiKey"
            val apiKey = com.ronin.settings.CredentialHelper.getApiKey(apiKeyName)
            
            if (apiKey.isNullOrBlank()) {
                appendMessage("System", "Error: API Key missing for follow-up.")
                return
            }
            
            val llmService = project.service<LLMService>()
            
            ApplicationManager.getApplication().executeOnPooledThread {
                val contextBuilder = StringBuilder()
                 // Reuse previous context or fetch fresh? Fresh is safer if file changed.
                 val contextService = project.service<com.ronin.service.ContextService>()
                 contextService.getActiveFileContent()?.let {
                    contextBuilder.append("Active File Content:\n$it\n\n")
                 }
                 contextBuilder.append(contextService.getProjectStructure())
                 
                 val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                 val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                 
                 SwingUtilities.invokeLater {
                    appendMessage(MyBundle.message("toolwindow.ronin"), result.text)
                    messageHistory.add(mapOf("role" to "user", "content" to text))
                    messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                    
                     // Recurse again? Limit to 1 level to avoid infinite loops for now
                     // or implementing a max_depth counter.
                     // For V1, let's allow it but rely on user to stop if it goes crazy.
                 }
            }
        }

        private fun clearChat() {
            SwingUtilities.invokeLater {
                chatArea.text = ""
                messageHistory.clear()
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
