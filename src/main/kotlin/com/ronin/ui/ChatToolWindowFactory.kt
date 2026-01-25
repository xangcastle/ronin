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
        
        // New Input Area (Multi-line)
        private val inputArea = com.intellij.ui.components.JBTextArea()
        
        // Chat History
        private val messageHistory = mutableListOf<Map<String, String>>()
        
        // Controls
        private val attachButton = JButton(com.intellij.icons.AllIcons.FileTypes.Any_type)
        // Dynamic Action Button: Stop (when generating) / Reset (when idle)
        private val actionButton = JButton(com.intellij.icons.AllIcons.Actions.Refresh) // Default to Reset/Refresh
        private val modelComboBox = com.intellij.openapi.ui.ComboBox<String>()
        
        // State
        private var currentTask: java.util.concurrent.Future<*>? = null
        private var isGenerating = false

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
            
            // ... (Layout code similar to before)
            chatPanel.layout = BoxLayout(chatPanel, BoxLayout.Y_AXIS)
            chatPanel.background = com.intellij.util.ui.UIUtil.getListBackground()
            chatPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            
            val wrapperPanel = JPanel(BorderLayout())
            wrapperPanel.add(chatPanel, BorderLayout.NORTH)
            wrapperPanel.background = com.intellij.util.ui.UIUtil.getListBackground()
            scrollPane.setViewportView(wrapperPanel)
            mainPanel.add(scrollPane, BorderLayout.CENTER)

            // Bottom Area
            val bottomPanel = JPanel(BorderLayout())
            bottomPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
            
            val controlBar = JPanel(BorderLayout())
            controlBar.border = BorderFactory.createEmptyBorder(0, 0, 5, 0)
            
            modelComboBox.isOpaque = false
            
            val leftControls = JPanel()
            leftControls.layout = BoxLayout(leftControls, BoxLayout.X_AXIS)
            leftControls.add(modelComboBox)
            controlBar.add(leftControls, BorderLayout.WEST)
            
            val rightControls = JPanel()
            rightControls.layout = BoxLayout(rightControls, BoxLayout.X_AXIS)
            
            // Configure Action Button
            actionButton.toolTipText = "Reset Chat"
            actionButton.addActionListener { 
                if (isGenerating) {
                    cancelGeneration()
                } else {
                    clearChat()
                }
            }
            // Fix icon size or remove border for cleaner look?
            // actionButton.border = BorderFactory.createEmptyBorder() 

            attachButton.toolTipText = "Attach Image"
            attachButton.addActionListener { attachImage() }

            rightControls.add(actionButton)
            rightControls.add(Box.createHorizontalStrut(5))
            rightControls.add(attachButton)
            
            controlBar.add(rightControls, BorderLayout.EAST)
            
            bottomPanel.add(controlBar, BorderLayout.NORTH)
            
            // ... (Input Area setup)
            inputArea.rows = 1
            inputArea.lineWrap = true
            inputArea.wrapStyleWord = true
            inputArea.border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(java.awt.Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 5, 5)
            )
            
            inputArea.addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    if (e.keyCode == java.awt.event.KeyEvent.VK_ENTER) {
                        if (e.isShiftDown) {
                            // Newline
                        } else {
                            e.consume()
                            sendMessage()
                        }
                    }
                }
            })
            
           inputArea.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                fun updateHeight() {
                    val rows = inputArea.lineCount.coerceAtMost(10).coerceAtLeast(1)
                    inputArea.rows = rows
                    bottomPanel.revalidate() 
                }
                override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = updateHeight()
                override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = updateHeight()
                override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = updateHeight()
            })

            bottomPanel.add(inputArea, BorderLayout.CENTER)
            mainPanel.add(bottomPanel, BorderLayout.SOUTH)

            updateModelList()
            
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
                for (msg in savedHistory) {
                    val role = if (msg["role"] == "user") MyBundle.message("toolwindow.you") else MyBundle.message("toolwindow.ronin")
                    appendMessage(role, msg["content"] ?: "")
                }
            }
            
            // Initial State Update
            updateActionButtonState()
        }
        
        private fun updateActionButtonState() {
             SwingUtilities.invokeLater {
                if (isGenerating) {
                    actionButton.icon = com.intellij.icons.AllIcons.Actions.Suspend
                    actionButton.toolTipText = "Stop Generation"
                } else {
                    actionButton.icon = com.intellij.icons.AllIcons.Actions.Refresh // Or GC/Trash
                    actionButton.toolTipText = "Reset Chat (Keep Settings)"
                }
            }
        }
        
        private fun setGenerating(generating: Boolean) {
            isGenerating = generating
            updateActionButtonState()
            
            SwingUtilities.invokeLater {
                inputArea.isEnabled = !generating
                if (!generating) {
                    inputArea.requestFocusInWindow()
                }
            }
        }
        
        private fun cancelGeneration() {
            if (isGenerating && currentTask != null) {
                currentTask?.cancel(true)
                setGenerating(false)
                appendMessage("System", "🛑 Request cancelled by user.")
            }
        }
        
        fun updateModelList() {
            val settings = com.ronin.settings.RoninSettingsState.instance
            val provider = settings.provider
            val currentModel = settings.model
            
            val initialModels = arrayOf(currentModel.ifBlank { "Loading..." })
            modelComboBox.model = DefaultComboBoxModel(initialModels)
            modelComboBox.isEnabled = false
            
            val llmService = project.service<LLMService>()
            
            ApplicationManager.getApplication().executeOnPooledThread {
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
                        com.ronin.settings.RoninSettingsState.instance.model = models[0]
                    }
                }
            }
        }

        private fun sendMessage() {
            val text = inputArea.text.trim()
            if (text.isNotBlank()) {
                appendMessage(MyBundle.message("toolwindow.you"), text)
                inputArea.text = ""
                setGenerating(true)
                
                // Gather Context on EDT (Required for FileEditorManager)
                val contextService = project.service<com.ronin.service.ContextService>()
                val activeFile = contextService.getActiveFileContent()
                
                // Use a generic executeOnPooledThread, but keep reference for cancellation
                currentTask = ApplicationManager.getApplication().executeOnPooledThread {
                    try {
                        if (Thread.interrupted()) throw InterruptedException()
                        
                        val llmService = project.service<LLMService>()
                        
                        // Persist User Message
                        val storageService = project.service<com.ronin.service.ChatStorageService>()
                        storageService.addMessage("user", text)
                        
                        // Gather Project Structure
                        val projectStructure = ReadAction.compute<String, Throwable> { 
                            contextService.getProjectStructure() 
                        }
                        
                        if (Thread.interrupted()) throw InterruptedException()
                        
                        val contextBuilder = StringBuilder()
                        if (activeFile != null) {
                            contextBuilder.append("Active File Content:\n```\n$activeFile\n```\n\n")
                        }
                        contextBuilder.append(projectStructure)
                        
                        // Send message to LLM
                        val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                        
                        if (Thread.interrupted()) throw InterruptedException()
                        
                        // Parse
                        val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                        
                        appendMessage(MyBundle.message("toolwindow.ronin"), result.text)
                        messageHistory.add(mapOf("role" to "user", "content" to text))
                        messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                        
                        storageService.addMessage("assistant", result.text)
                        
                        // Handle Command Execution
                        val command = result.commandToRun
                        if (command != null) {
                            appendMessage("System", "Running command: `$command`...")
                            executeCommandChain(command)
                        } else {
                            setGenerating(false)
                        }
                    } catch (e: InterruptedException) {
                        // Cancelled silently or handled by button
                        setGenerating(false)
                    } catch (e: Exception) {
                        appendMessage("System", "Error: ${e.message}")
                        setGenerating(false)
                    }
                }
            }
        }
        
        private fun executeCommandChain(command: String) {
             // We consider command execution part of the "generation" flow, so we track it too?
             // Or we consider it separate? Let's track it so Stop works on commands too eventually.
             
             val task = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val terminalService = project.service<com.ronin.service.TerminalService>()
                    
                    SwingUtilities.invokeLater {
                        addTerminalBlock(command)
                    }
                    
                    // Buffer for output to avoid flooding EDT
                    val outputBuffer = StringBuilder()
                    val lock = Any()
                    var updateScheduled = false
                    
                    val output = terminalService.runCommand(command) { line ->
                        synchronized(lock) {
                            outputBuffer.append(line)
                            if (!updateScheduled) {
                                updateScheduled = true
                                // Throttle updates to ~10fps max to keep UI responsive
                                val timer = Timer(100) {
                                    val textToAppend: String
                                    synchronized(lock) {
                                        textToAppend = outputBuffer.toString()
                                        outputBuffer.clear()
                                        updateScheduled = false
                                    }
                                    if (textToAppend.isNotEmpty()) {
                                        appendToLastTerminalBlock(textToAppend)
                                    }
                                }
                                timer.isRepeats = false
                                timer.start()
                            }
                        }
                    }
                    
                    // Flush remaining
                    SwingUtilities.invokeLater {
                        val remaining: String
                        synchronized(lock) {
                           remaining = outputBuffer.toString()
                        }
                        if (remaining.isNotEmpty()) {
                            appendToLastTerminalBlock(remaining)
                        }
                        
                        appendToLastTerminalBlock("\n[Finished]")
                        val followUpPrompt = "Command Output:\n```\n$output\n```\nIf there are errors, please fix them."
                        val summary = "Command executed. Output (${output.lines().size} lines) sent to Ronin."
                        handleFollowUp(followUpPrompt, summary)
                    }
                } catch (e: Exception) {
                    setGenerating(false)
                }
            }
            // If we want Stop to kill commands, we'd need to track this 'task' too.
            // For now, replace currentTask
            currentTask = task
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
                
                // Wrapper
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
                        if (d.width > maxW) {
                             return java.awt.Dimension(maxW, d.height)
                        }
                    }
                }
                return d
            }
            
            override fun getScrollableTracksViewportWidth(): Boolean {
                return true 
            }
            
            override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
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
            chatPanel.revalidate() 
            // Scroll to bottom
            val bar = scrollPane.verticalScrollBar
            bar.value = bar.maximum
        }

        // Helper for the feedback loop
        private fun handleFollowUp(text: String, displayLabel: String) {
             appendMessage(MyBundle.message("toolwindow.you") + " (Auto)", displayLabel)
             
            currentTask = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    if (Thread.interrupted()) throw InterruptedException()

                    val settings = com.ronin.settings.RoninSettingsState.instance
                    val apiKeyName = if (settings.provider == "Anthropic") "anthropicApiKey" else "openaiApiKey"
                    val apiKey = com.ronin.settings.CredentialHelper.getApiKey(apiKeyName)
                    
                    if (apiKey.isNullOrBlank()) {
                        appendMessage("System", "Error: API Key missing for follow-up.")
                        setGenerating(false)
                        return@executeOnPooledThread
                    }
                    
                    val llmService = project.service<LLMService>()
                    
                    val contextService = project.service<com.ronin.service.ContextService>()
                    val activeFile = contextService.getActiveFileContent()
                    
                    val projectStructure = ReadAction.compute<String, Throwable> { 
                        contextService.getProjectStructure() 
                    }
                    
                    val contextBuilder = StringBuilder()
                     if (activeFile != null) {
                        contextBuilder.append("Active File Content:\n```\n$activeFile\n```\n\n")
                     }
                     contextBuilder.append(projectStructure)
                     
                     if (Thread.interrupted()) throw InterruptedException()

                     val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                     val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                     
                     SwingUtilities.invokeLater {
                        appendMessage(MyBundle.message("toolwindow.ronin"), result.text)
                        messageHistory.add(mapOf("role" to "user", "content" to text))
                        messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                         
                        val storageService = project.service<com.ronin.service.ChatStorageService>()
                        storageService.addMessage("user", text)
                        storageService.addMessage("assistant", result.text)
                         
                         // If there's ANOTHER command, handle it
                         val nextCommand = result.commandToRun
                         if (nextCommand != null) {
                             executeCommandChain(nextCommand)
                         } else {
                             setGenerating(false)
                         }
                     }
                } catch (e: Exception) {
                    setGenerating(false)
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
            appendMessage("System", "[Image attached]")
        }

        fun getContent(): JComponent {
            updateModelList() 
            return mainPanel
        }
    }
}
