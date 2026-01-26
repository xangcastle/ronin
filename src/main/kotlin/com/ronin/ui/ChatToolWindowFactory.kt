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
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.vfs.VfsUtil
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.intellij.ui.components.JBLabel
import java.io.File

class ChatToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatToolWindow = ChatToolWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(chatToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
        
        // Add native actions to the tool window title bar
        val actionGroup = DefaultActionGroup()
        actionGroup.add(object : AnAction("Export Conversation...", "Save chat history to JSON", com.intellij.icons.AllIcons.Actions.Download) {
            override fun actionPerformed(e: AnActionEvent) {
                chatToolWindow.exportConversation()
            }
        })
        actionGroup.add(object : AnAction("Clear Chat", "Reset conversation", com.intellij.icons.AllIcons.Actions.GC) {
            override fun actionPerformed(e: AnActionEvent) {
                chatToolWindow.clearChat()
            }
        })
        
        toolWindow.setTitleActions(listOf(actionGroup))
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
                    updateStanceList()
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
                        e.consume() // Always consume specific Enter actions to prevent double-handling
                        if (e.isShiftDown) {
                            inputArea.replaceSelection("\n")
                        } else {
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

            updateStanceList()
            
            modelComboBox.addActionListener {
                val selected = modelComboBox.selectedItem as? String
                if (selected != null) {
                    com.ronin.settings.RoninSettingsState.instance.activeStance = selected
                }
            }
            
            // Load History
            val sessionService = project.service<com.ronin.service.AgentSessionService>()
            val savedHistory = sessionService.getHistory()
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
        
        fun updateStanceList() {
            val settings = com.ronin.settings.RoninSettingsState.instance
            val stances = settings.stances.map { it.name }.toTypedArray()
            val currentStance = settings.activeStance
            
            SwingUtilities.invokeLater {
                modelComboBox.model = DefaultComboBoxModel(stances)
                modelComboBox.isEnabled = true
                
                if (stances.contains(currentStance)) {
                    modelComboBox.selectedItem = currentStance
                } else if (stances.isNotEmpty()) {
                    modelComboBox.selectedItem = stances[0]
                    settings.activeStance = stances[0]
                }
            }
        }

        private fun sendMessage() {
            val text = inputArea.text.trim()
            if (text.isNotBlank()) {
                appendMessage(MyBundle.message("toolwindow.you"), text)
                inputArea.text = ""
                setGenerating(true)
                
                // --- SLASH COMMAND INTERCEPTION ---
                if (text.startsWith("/")) {
                   val commandService = project.service<com.ronin.service.CommandService>()
                   val cmdResult = commandService.executeCommand(text)
                   
                   when (cmdResult) {
                       is com.ronin.service.CommandResult.Action -> {
                           appendMessage("System", cmdResult.message)
                           setGenerating(false)
                           return
                       }
                       is com.ronin.service.CommandResult.Error -> {
                           appendMessage("System", "❌ ${cmdResult.message}")
                           setGenerating(false)
                           return
                       }
                       is com.ronin.service.CommandResult.PromptInjection -> {
                           appendMessage("System", "🔄 Loaded command context...")
                           processUserMessage(cmdResult.prompt)
                           return
                       }
                   }
                }
                processUserMessage(text)
            }
        }

        private fun processUserMessage(text: String) {
            val configService = project.service<com.ronin.service.RoninConfigService>()
            val activeFile = configService.getActiveFileContent()
            
            currentTask = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    if (Thread.interrupted()) throw InterruptedException()
                    val llmService = project.service<LLMService>()
                    
                    val sessionService = project.service<com.ronin.service.AgentSessionService>()
                    
                    // Add to history BEFORE sending to LLM
                    messageHistory.add(mapOf("role" to "user", "content" to text))
                    sessionService.addMessage("user", text)
                    
                    val projectStructure = ReadAction.compute<String, Throwable> { 
                        configService.getProjectStructure() 
                    }
                    
                    if (Thread.interrupted()) throw InterruptedException()
                    val contextBuilder = StringBuilder()
                    if (activeFile != null) {
                        contextBuilder.append("Active File Content:\n```\n$activeFile\n```\n\n")
                    }
                    contextBuilder.append(projectStructure)
                    
                    val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                    if (Thread.interrupted()) throw InterruptedException()
                    
                    val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                    
                    if (!result.scratchpad.isNullOrBlank()) {
                        appendMessage("Ronin Thinking", result.scratchpad)
                    }
                    
                    val displayedMsg = if (result.toolOutput != null) "${result.text}\n\n${result.toolOutput}" else result.text
                    appendMessage(MyBundle.message("toolwindow.ronin"), displayedMsg)
                    
                    // History Cleanup: Save ONLY the agent text, not the potentially huge tool output
                    messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                    sessionService.addMessage("assistant", result.text)
                    
                    val command = result.commandToRun
                    if (command != null) {
                        appendMessage("System", "Running command: `$command`...")
                        executeCommandChain(command)
                    } else if (result.requiresFollowUp) {
                        val followUpPrompt = "The action was completed. Here is the output:\n```\n${result.toolOutput ?: result.text}\n```\nAnalyze this and continue."
                        val followUpSummary = "File action complete."
                        handleFollowUp(followUpPrompt, followUpSummary)
                    } else {
                        setGenerating(false)
                    }
                } catch (e: InterruptedException) {
                    setGenerating(false)
                } catch (e: Exception) {
                    appendMessage("System", "Error: ${e.message}")
                    setGenerating(false)
                }
            }
        }
        
        private fun executeCommandChain(command: String) {
            val task = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val terminalService = project.service<com.ronin.service.TerminalService>()
                    SwingUtilities.invokeLater { addTerminalBlock(command) }
                    
                    val outputBuffer = StringBuilder()
                    val lock = Any()
                    var updateScheduled = false
                    
                    val output = terminalService.runCommand(command) { line ->
                        synchronized(lock) {
                            outputBuffer.append(line)
                            if (!updateScheduled) {
                                updateScheduled = true
                                val timer = Timer(100) {
                                    val textToAppend: String
                                    synchronized(lock) {
                                        textToAppend = outputBuffer.toString()
                                        outputBuffer.clear()
                                        updateScheduled = false
                                    }
                                    if (textToAppend.isNotEmpty()) appendToLastTerminalBlock(textToAppend)
                                }
                                timer.isRepeats = false
                                timer.start()
                            }
                        }
                    }
                    
                    SwingUtilities.invokeLater {
                        val remaining: String
                        synchronized(lock) { remaining = outputBuffer.toString() }
                        if (remaining.isNotEmpty()) appendToLastTerminalBlock(remaining)
                        appendToLastTerminalBlock("\n[Finished]")
                        val followUpPrompt = "The output of your last command [`$command`] was:\n```\n$output\n```\nAnalyze this output and decide on the next step."
                        val summary = "Command output received."
                        handleFollowUp(followUpPrompt, summary)
                    }
                } catch (e: Exception) {
                    setGenerating(false)
                }
            }
            currentTask = task
        }

        fun appendMessage(role: String, message: String) {
            SwingUtilities.invokeLater {
                val isMe = role == MyBundle.message("toolwindow.you") || role.contains("You")
                val isSystem = role == "System"
                val isThinking = role == "Ronin Thinking"
                val rowPanel = JPanel(java.awt.GridBagLayout())
                rowPanel.isOpaque = false
                val c = java.awt.GridBagConstraints()
                c.gridx = 0
                c.gridy = 0
                c.weightx = 1.0
                c.fill = java.awt.GridBagConstraints.HORIZONTAL
                if (isMe) {
                    c.anchor = java.awt.GridBagConstraints.EAST
                    c.insets = java.awt.Insets(0, 50, 0, 0)
                } else {
                    c.anchor = java.awt.GridBagConstraints.WEST
                    c.insets = java.awt.Insets(0, 0, 0, 50)
                }
                val textArea = DynamicTextArea(message)
                textArea.lineWrap = true
                textArea.wrapStyleWord = true
                textArea.isEditable = false
                textArea.isOpaque = true
                if (isMe) {
                    textArea.background = java.awt.Color(0, 122, 255) 
                    textArea.foreground = java.awt.Color.WHITE
                } else if (isSystem) {
                    textArea.background = java.awt.Color(40, 44, 52) 
                    textArea.foreground = java.awt.Color(171, 178, 191)
                    textArea.font = java.awt.Font("JetBrains Mono", java.awt.Font.PLAIN, 12)
                } else if (isThinking) {
                    textArea.background = com.intellij.util.ui.UIUtil.getPanelBackground()
                    textArea.foreground = java.awt.Color(150, 150, 150)
                    textArea.font = java.awt.Font(textArea.font.name, java.awt.Font.ITALIC, 11)
                    textArea.border = BorderFactory.createLineBorder(java.awt.Color(200, 200, 200, 50), 1)
                } else {
                    textArea.background = com.intellij.util.ui.UIUtil.getPanelBackground()
                    textArea.foreground = com.intellij.util.ui.UIUtil.getLabelForeground()
                    textArea.border = BorderFactory.createLineBorder(java.awt.Color.GRAY, 1)
                }
                textArea.border = BorderFactory.createCompoundBorder(textArea.border, BorderFactory.createEmptyBorder(8, 12, 8, 12))
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
        
        inner class DynamicTextArea(text: String) : JTextArea(text) {
             override fun getPreferredSize(): java.awt.Dimension {
                val d = super.getPreferredSize()
                val viewport = scrollPane.viewport
                if (viewport != null) {
                    val maxW = (viewport.width * 0.85).toInt()
                    if (maxW > 100 && d.width > maxW) return java.awt.Dimension(maxW, d.height)
                }
                return d
            }
            override fun getScrollableTracksViewportWidth(): Boolean = true 
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
                outputArea.rows = 5
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
            val bar = scrollPane.verticalScrollBar
            bar.value = bar.maximum
        }

        private fun handleFollowUp(text: String, displayLabel: String) {
            appendMessage(MyBundle.message("toolwindow.you") + " (Auto)", displayLabel)
            currentTask = ApplicationManager.getApplication().executeOnPooledThread {
                try {
                    val llmService = project.service<LLMService>()
                    val configService = project.service<com.ronin.service.RoninConfigService>()
                    val activeFile = configService.getActiveFileContent()
                    val projectStructure = ReadAction.compute<String, Throwable> { configService.getProjectStructure() }
                    val contextBuilder = StringBuilder()
                    if (activeFile != null) contextBuilder.append("Active File:\n```\n$activeFile\n```\n\n")
                    contextBuilder.append(projectStructure)
                    val response = llmService.sendMessage(text, contextBuilder.toString(), ArrayList(messageHistory))
                    if (Thread.interrupted()) throw InterruptedException()
                    
                    val sessionService = project.service<com.ronin.service.AgentSessionService>()
                    // Add to history BEFORE LLM call for handleFollowUp too
                    messageHistory.add(mapOf("role" to "user", "content" to text))
                    sessionService.addMessage("user", text)
                    
                    val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
                    
                    if (!result.scratchpad.isNullOrBlank()) {
                        appendMessage("Ronin Thinking", result.scratchpad)
                    }
                    
                    val displayedMsg = if (result.toolOutput != null) "${result.text}\n\n${result.toolOutput}" else result.text
                    
                    SwingUtilities.invokeLater {
                        appendMessage(MyBundle.message("toolwindow.ronin"), displayedMsg)
                        
                        // History Cleanup
                        messageHistory.add(mapOf("role" to "assistant", "content" to result.text))
                        sessionService.addMessage("assistant", result.text)
                        
                        val nextCommand = result.commandToRun
                        if (nextCommand != null) {
                            executeCommandChain(nextCommand)
                        } else if (result.requiresFollowUp) {
                            val followUpPrompt = "The action was completed. Here is the output:\n```\n${result.toolOutput ?: result.text}\n```\nAnalyze this and continue."
                            val followUpSummary = "File action complete."
                            handleFollowUp(followUpPrompt, followUpSummary)
                        } else {
                            setGenerating(false)
                        }
                    }
                } catch (e: Exception) { setGenerating(false) }
            }
        }


        fun exportConversation() {
            val descriptor = FileSaverDescriptor("Export Conversation", "Save conversation history", "json")
            val baseDir = VfsUtil.getUserHomeDir()
            val wrapper = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)
            val fileWrapper = wrapper.save(baseDir, "ronin_chat_export.json")
            
            if (fileWrapper != null) {
                val file = fileWrapper.file
                try {
                    val mapper = ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT)
                    mapper.writeValue(file, messageHistory)
                    appendMessage("System", "✅ Conversation exported to: ${file.absolutePath}")
                } catch (e: Exception) {
                    appendMessage("System", "❌ Failed to export: ${e.message}")
                }
            }
        }

        fun clearChat() {
            SwingUtilities.invokeLater {
                chatPanel.removeAll()
                chatPanel.revalidate()
                chatPanel.repaint()
                messageHistory.clear()
                project.service<com.ronin.service.AgentSessionService>().clearSession()
            }
        }

        private fun attachImage() { appendMessage("System", "[Image attached]") }

        fun getContent(): JComponent {
            updateStanceList() 
            return mainPanel
        }
    }
}
