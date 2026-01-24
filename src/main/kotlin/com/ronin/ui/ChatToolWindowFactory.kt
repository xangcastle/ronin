package com.ronin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.ronin.service.LLMService
import com.intellij.openapi.components.service
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
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
        
        // Chat UI Components
        private val chatPanel = JPanel()
        private val scrollPane = com.intellij.ui.components.JBScrollPane(chatPanel)
        
        private val inputField = JTextField()
        
        // Chat History: List of Maps {"role": "user"/"assistant", "content": "..."}
        private val messageHistory = mutableListOf<Map<String, String>>()
        private val sendButton = JButton(MyBundle.message("toolwindow.send"))
        private val attachButton = JButton(MyBundle.message("toolwindow.attach"))
        private val clearButton = JButton(MyBundle.message("toolwindow.clear"))
        private val modelComboBox = com.intellij.openapi.ui.ComboBox<String>()

        companion object {
            private const val KEY = "RoninChatToolWindow"

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
            
            // Layout Setup
            chatPanel.layout = BoxLayout(chatPanel, BoxLayout.Y_AXIS)
            chatPanel.background = com.intellij.util.ui.UIUtil.getListBackground()
            chatPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            // Align to top
            val wrapperPanel = JPanel(BorderLayout())
            wrapperPanel.add(chatPanel, BorderLayout.NORTH)
            wrapperPanel.background = com.intellij.util.ui.UIUtil.getListBackground()
            
            scrollPane.setViewportView(wrapperPanel)
            
            // Scroll behavior
            val viewport = scrollPane.viewport
            // Auto scroll logic can be added here
            
            mainPanel.add(scrollPane, BorderLayout.CENTER)

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
            
            // Load History
            val storageService = project.service<com.ronin.service.ChatStorageService>()
            val savedHistory = storageService.getHistory()
            if (savedHistory.isNotEmpty()) {
                messageHistory.addAll(savedHistory)
                // Replay messages to UI
                for (msg in savedHistory) {
                    val role = if (msg["role"] == "user") MyBundle.message("toolwindow.you") else MyBundle.message("toolwindow.ronin")
                    appendMessage(role, msg["content"] ?: "")
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
                
                // Gather Context on EDT (Required for FileEditorManager)
                val contextService = project.service<com.ronin.service.ContextService>()
                val activeFile = contextService.getActiveFileContent()
                
                // Asynchronously call LLM service to avoid freezing UI
                ApplicationManager.getApplication().executeOnPooledThread {
                    val llmService = project.service<LLMService>()
                    
                    // Persist User Message
                    val storageService = project.service<com.ronin.service.ChatStorageService>()
                    storageService.addMessage("user", text)
                    
                    // Gather Project Structure in Background with Read Lock (Slow operation ok here)
                    val projectStructure = ReadAction.compute<String, Throwable> { 
                        contextService.getProjectStructure() 
                    }
                    
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
                    
                    // Persist Assistant Message
                    storageService.addMessage("assistant", result.text)
                    
                    // Handle Command Execution Loop
                    val command = result.commandToRun
                    if (command != null) {
                        appendMessage("System", "Running command: `$command`...")
                        
                        // Execute in background to avoid freezing UI
                        executeCommandChain(command)
                    }
                }
            }
        }
        
        private fun executeCommandChain(command: String) {
             ApplicationManager.getApplication().executeOnPooledThread {
                val terminalService = project.service<com.ronin.service.TerminalService>()
                
                SwingUtilities.invokeLater {
                    // Update the last message or add new system block?
                    // For simply simulation, adding new block is fine.
                    // appendMessage("System", "Executing: $command")
                    // We might want a dedicated "Terminal Output" block that grows.
                    addTerminalBlock(command)
                }
                
                val output = terminalService.runCommand(command) { line ->
                    SwingUtilities.invokeLater {
                        appendToLastTerminalBlock(line)
                    }
                }
                
                SwingUtilities.invokeLater {
                    appendToLastTerminalBlock("\n[Finished]")
                    val followUpPrompt = "Command Output:\n```\n$output\n```\nIf there are errors, please fix them."
                    val summary = "Command executed. Output (${output.lines().size} lines) sent to Ronin."
                    handleFollowUp(followUpPrompt, summary)
                }
            }
        }

        // New UI: Bubble rendering with Responsive Layout
        fun appendMessage(role: String, message: String) {
            SwingUtilities.invokeLater {
                val isMe = role == MyBundle.message("toolwindow.you") || role.contains("You")
                val isSystem = role == "System"
                
                val rowPanel = JPanel(java.awt.GridBagLayout())
                rowPanel.isOpaque = false
                val c = java.awt.GridBagConstraints()
                c.gridx = 0
                c.gridy = 0
                c.weightx = 1.0
                c.fill = java.awt.GridBagConstraints.HORIZONTAL
                
                // Alignment via anchor and empty border hack/insets
                if (isMe) {
                    c.anchor = java.awt.GridBagConstraints.EAST
                    c.insets = java.awt.Insets(0, 50, 0, 0) // Push from left
                } else {
                    c.anchor = java.awt.GridBagConstraints.WEST
                    c.insets = java.awt.Insets(0, 0, 0, 50) // Push from right
                }
                
                // Content
                val textArea = DynamicTextArea(message)
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                textArea.isEditable = false
                textArea.isOpaque = true
                
                // Styling
                if (isMe) {
                    textArea.background = java.awt.Color(0, 122, 255) 
                    textArea.foreground = java.awt.Color.WHITE
                } else if (isSystem) {
                    textArea.background = java.awt.Color(40, 44, 52) 
                    textArea.foreground = java.awt.Color(171, 178, 191)
                    textArea.font = java.awt.Font("JetBrains Mono", java.awt.Font.PLAIN, 12)
                } else {
                    textArea.background = com.intellij.util.ui.UIUtil.getPanelBackground()
                    textArea.foreground = com.intellij.util.ui.UIUtil.getLabelForeground()
                    textArea.border = BorderFactory.createLineBorder(java.awt.Color.GRAY, 1)
                }
                
                textArea.border = BorderFactory.createCompoundBorder(
                    textArea.border, 
                    BorderFactory.createEmptyBorder(8, 12, 8, 12)
                )
                
                // Wrapper to force right/left alignment within the cell not filling full width if small text
                val bubbleWrapper = JPanel(BorderLayout())
                bubbleWrapper.isOpaque = false
                bubbleWrapper.add(textArea, BorderLayout.CENTER)
                
                rowPanel.add(bubbleWrapper, c)
                
                chatPanel.add(rowPanel)
                chatPanel.add(Box.createVerticalStrut(10)) 
                
                chatPanel.revalidate()
                val bar = scrollPane.verticalScrollBar
                bar.value = bar.maximum
            }
        }
        
        // Custom TextArea that tries to fit within parent viewport
        inner class DynamicTextArea(text: String) : JTextArea(text) {
             override fun getPreferredSize(): java.awt.Dimension {
                val d = super.getPreferredSize()
                val viewport = scrollPane.viewport
                if (viewport != null) {
                    val maxW = (viewport.width * 0.85).toInt() // Max 85% width
                    if (maxW > 100) { 
                        // Simple constraint:
                        if (d.width > maxW) {
                            // Ideally we would calculate height for this width, but JTextArea is complex.
                            // Simply constraining width allows basic wrapping.
                             return java.awt.Dimension(maxW, d.height)
                        }
                    }
                }
                return d
            }
            
            // This is crucial for word wrap to work when resized
            override fun getScrollableTracksViewportWidth(): Boolean {
                return true 
            }
            
            override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
                // Constrain width
                val viewport = scrollPane.viewport
                var w = width
                if (viewport != null) {
                    val maxW = (viewport.width * 0.85).toInt()
                    if (w > maxW) w = maxW
                }
                super.setBounds(x, y, w, height)
            }
        }
        
        // Dedicated Terminal Block
        private var lastTerminalArea: JTextArea? = null
        
        private fun addTerminalBlock(command: String) {
             SwingUtilities.invokeLater {
                val rowPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
                rowPanel.isOpaque = false
                
                val termPanel = JPanel(BorderLayout())
                termPanel.background = java.awt.Color(30, 30, 30)
                termPanel.border = BorderFactory.createLineBorder(java.awt.Color.GRAY)
                
                val header = JLabel(" $> $command")
                header.foreground = java.awt.Color.GREEN
                header.font = java.awt.Font("JetBrains Mono", java.awt.Font.BOLD, 12)
                header.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
                termPanel.add(header, BorderLayout.NORTH)
                
                val outputArea = JTextArea()
                outputArea.background = java.awt.Color(30, 30, 30)
                outputArea.foreground = java.awt.Color.LIGHT_GRAY
                outputArea.font = java.awt.Font("JetBrains Mono", java.awt.Font.PLAIN, 12)
                outputArea.isEditable = false
                outputArea.columns = 50
                outputArea.rows = 5 // Initial size
                
                termPanel.add(outputArea, BorderLayout.CENTER)
                lastTerminalArea = outputArea
                
                rowPanel.add(termPanel)
                chatPanel.add(rowPanel)
                chatPanel.add(Box.createVerticalStrut(10))
                
                chatPanel.revalidate()
                val bar = scrollPane.verticalScrollBar
                bar.value = bar.maximum
             }
        }
        
        private fun appendToLastTerminalBlock(text: String) {
            lastTerminalArea?.append(text)
            // Auto-expand rows if needed? 
            // JTextArea inside FlowLayout might not expand automatically well without revalidate
            chatPanel.revalidate() 
            // Scroll to bottom
            val bar = scrollPane.verticalScrollBar
            bar.value = bar.maximum
        }

        // Helper for the feedback loop
        private fun handleFollowUp(text: String, displayLabel: String) {
            // Add to UI immediately
             appendMessage(MyBundle.message("toolwindow.you") + " (Auto)", displayLabel)
             
            ApplicationManager.getApplication().executeOnPooledThread {
                // Re-run the send logic (minus the input field clearing part)
                val settings = com.ronin.settings.RoninSettingsState.instance
                val apiKeyName = if (settings.provider == "Anthropic") "anthropicApiKey" else "openaiApiKey"
                
                // Retrieve API Key in background thread to avoid "Slow operations are prohibited on EDT"
                val apiKey = com.ronin.settings.CredentialHelper.getApiKey(apiKeyName)
                
                if (apiKey.isNullOrBlank()) {
                    appendMessage("System", "Error: API Key missing for follow-up.")
                    return@executeOnPooledThread
                }
                
                val llmService = project.service<LLMService>()
                
                // Gather Context on EDT
                val contextService = project.service<com.ronin.service.ContextService>()
                val activeFile = contextService.getActiveFileContent()
                
                // Gather Project Structure in Background with Read Lock
                val projectStructure = ReadAction.compute<String, Throwable> { 
                    contextService.getProjectStructure() 
                }
                
                val contextBuilder = StringBuilder()
                 // Reuse previous context or fetch fresh? Fresh is safer if file changed.
                 if (activeFile != null) {
                    contextBuilder.append("Active File Content:\n```\n$activeFile\n```\n\n")
                 }
                 contextBuilder.append(projectStructure)
                 
                 val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                 val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                 
                 SwingUtilities.invokeLater {
                    appendMessage(MyBundle.message("toolwindow.ronin"), result.text)
                    messageHistory.add(mapOf("role" to "user", "content" to text))
                    messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                     
                    // Persist Messages (User Auto + Assistant)
                    val storageService = project.service<com.ronin.service.ChatStorageService>()
                    storageService.addMessage("user", text)
                    storageService.addMessage("assistant", result.text)
                     
                     // If there's ANOTHER command, handle it
                     val nextCommand = result.commandToRun
                     if (nextCommand != null) {
                         executeCommandChain(nextCommand)
                     }
                 }
            }
        }

        private fun clearChat() {
            SwingUtilities.invokeLater {
                chatPanel.removeAll()
                chatPanel.revalidate()
                chatPanel.repaint()
                messageHistory.clear()
                project.service<com.ronin.service.ChatStorageService>().clearHistory()
            }
        }

        private fun attachImage() {
            // chatArea.append(MyBundle.message("toolwindow.system.image_mock"))
            appendMessage("System", "[Image attached]")
        }

        fun getContent(): JComponent {
            // Refresh models in case settings changed while window was closed/hidden
            updateModelList() 
            return mainPanel
        }
    }
}
