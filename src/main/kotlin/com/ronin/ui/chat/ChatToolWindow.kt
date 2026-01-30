package com.ronin.ui.chat

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.UIUtil
import com.ronin.MyBundle
import com.ronin.service.TerminalService
import com.ronin.settings.RoninSettingsNotifier
import com.ronin.settings.RoninSettingsState
import com.ronin.ui.chat.api.RoninApi
import com.ronin.ui.chat.components.ChatInputField
import com.ronin.ui.chat.components.ControlBar
import com.ronin.ui.chat.components.MessageBubble
import com.ronin.ui.chat.components.TerminalBlock
import java.awt.BorderLayout
import java.util.concurrent.Future
import javax.swing.*

/**
 * Main chat tool window - simplified to focus on UI coordination
 */
class ChatToolWindow(private val project: Project) {
    
    private val mainPanel = JPanel(BorderLayout())
    private val chatPanel = JPanel()
    private val scrollPane = JBScrollPane(chatPanel)
    
    // Components
    private lateinit var inputField: ChatInputField
    private lateinit var controlBar: ControlBar
    
    // API
    private val api = RoninApi(project)
    
    // State
    private val messageHistory = mutableListOf<Map<String, String>>()
    private var isGenerating = false
    private var lastTerminalBlock: TerminalBlock? = null
    private var currentCommandFuture: Future<*>? = null
    
    companion object {
        private const val KEY = "RoninChatToolWindow"
        
        fun getInstance(project: Project): ChatToolWindow? {
            val toolWindow = com.intellij.openapi.wm.ToolWindowManager.getInstance(project)
                .getToolWindow("Ronin Chat") ?: return null
            val content = toolWindow.contentManager.contents.firstOrNull() ?: return null
            val component = content.component
            return component.getClientProperty(KEY) as? ChatToolWindow
        }
    }
    
    init {
        mainPanel.putClientProperty(KEY, this)
        setupUI()
        connectToSettings()
        loadHistory()
    }
    
    private fun setupUI() {
        // Setup chat panel
        chatPanel.layout = BoxLayout(chatPanel, BoxLayout.Y_AXIS)
        chatPanel.background = UIUtil.getListBackground()
        chatPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        
        val wrapperPanel = JPanel(BorderLayout())
        wrapperPanel.add(chatPanel, BorderLayout.NORTH)
        wrapperPanel.background = UIUtil.getListBackground()
        scrollPane.setViewportView(wrapperPanel)
        mainPanel.add(scrollPane, BorderLayout.CENTER)
        
        // Setup bottom panel with controls and input
        val bottomPanel = JPanel(BorderLayout())
        bottomPanel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        
        // Control bar
        controlBar = ControlBar(
            onActionButtonClick = { handleActionButtonClick() },
            onAttachClick = { handleAttachImage() },
            onModelChange = { model -> 
                RoninSettingsState.instance.activeStance = model
            }
        )
        bottomPanel.add(controlBar, BorderLayout.NORTH)
        
        // Input field
        inputField = ChatInputField(project) { message -> 
            handleSendMessage(message)
        }
        bottomPanel.add(inputField, BorderLayout.CENTER)
        
        mainPanel.add(bottomPanel, BorderLayout.SOUTH)

        updateModelList()
    }
    
    private fun connectToSettings() {
        val connection = ApplicationManager.getApplication().messageBus.connect(project)
        connection.subscribe(RoninSettingsNotifier.TOPIC, object : RoninSettingsNotifier {
            override fun settingsChanged(settings: RoninSettingsState) {
                updateModelList()
            }
        })
    }
    
    private fun loadHistory() {
        val savedHistory = api.getSessionHistory()
        if (savedHistory.isNotEmpty()) {
            messageHistory.addAll(savedHistory)
            for (msg in savedHistory) {
                val role = if (msg["role"] == "user") {
                    MyBundle.message("toolwindow.you")
                } else {
                    MyBundle.message("toolwindow.ronin")
                }
                addMessageBubble(role, msg["content"] ?: "")
            }
        }
    }
    
    /**
     * Handles sending a user message
     */
    private fun handleSendMessage(message: String) {
        addUserMessage(message)
        messageHistory.add(mapOf("role" to "user", "content" to message))
        
        setGenerating(true)
        
        api.sendUserMessage(
            message = message,
            history = messageHistory,
            onThinking = { thinking ->
                addThinkingMessage(thinking)
            },
            onResponse = { role, response ->
                messageHistory.add(mapOf("role" to role, "content" to response))
                addAssistantMessage(response)
            },
            onCommand = { command ->
                addSystemMessage("Running command: `$command`...")
                executeCommand(command)
            },
            onFollowUp = { prompt, summary ->
                handleFollowUp(prompt, summary)
            },
            onError = { error ->
                addSystemMessage("❌ Error: $error")
                setGenerating(false)
            },
            onComplete = {
                setGenerating(false)
            }
        )
    }
    
    /**
     * Handles follow-up messages
     */
    private fun handleFollowUp(prompt: String, summary: String) {
        addUserMessage("$summary (Auto)", isAuto = true)
        messageHistory.add(mapOf("role" to "user", "content" to prompt))
        
        api.sendFollowUpMessage(
            prompt = prompt,
            history = messageHistory,
            onThinking = { thinking ->
                addThinkingMessage(thinking)
            },
            onResponse = { role, response ->
                messageHistory.add(mapOf("role" to role, "content" to response))
                addAssistantMessage(response)
            },
            onCommand = { command ->
                addSystemMessage("Running command: `$command`...")
                executeCommand(command)
            },
            onFollowUp = { nextPrompt, nextSummary ->
                handleFollowUp(nextPrompt, nextSummary)
            },
            onError = { error ->
                addSystemMessage("Error: $error")
                setGenerating(false)
            },
            onComplete = {
                setGenerating(false)
            }
        )
    }
    
    /**
     * Executes a terminal command
     */
    private fun executeCommand(command: String) {
        SwingUtilities.invokeLater {
            val terminalBlock = TerminalBlock(command)
            lastTerminalBlock = terminalBlock
            
            chatPanel.add(terminalBlock)
            chatPanel.add(Box.createVerticalStrut(10))
            chatPanel.revalidate()
            scrollToBottom()
        }
        
        currentCommandFuture = ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val terminalService = project.service<TerminalService>()
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
                                if (textToAppend.isNotEmpty()) {
                                    lastTerminalBlock?.appendOutput(textToAppend)
                                }
                            }
                            timer.isRepeats = false
                            timer.start()
                        }
                    }
                }
                
                SwingUtilities.invokeLater {
                    // If generation was cancelled, don't proceed with follow-up
                    if (!isGenerating) return@invokeLater

                    val remaining: String
                    synchronized(lock) { remaining = outputBuffer.toString() }
                    if (remaining.isNotEmpty()) {
                        lastTerminalBlock?.appendOutput(remaining)
                    }
                    lastTerminalBlock?.markFinished()
                    
                    // Continue with follow-up
                    val followUpPrompt = "The output of your last command [`$command`] was:\n```\n$output\n```\nAnalyze this output and decide on the next step."
                    val summary = "Command output received."
                    handleFollowUp(followUpPrompt, summary)
                }
            } catch (e: Exception) {
                SwingUtilities.invokeLater {
                    if (isGenerating) {
                        addSystemMessage("❌ Command failed: ${e.message}")
                        setGenerating(false)
                    }
                }
            }
        }
    }
    
    /**
     * Handles action button click (Stop/Reset)
     */
    private fun handleActionButtonClick() {
        if (isGenerating) {
            var cancelled = false
            
            // Cancel API task
            if (api.cancelCurrentTask()) {
                cancelled = true
            }
            
            // Cancel running command
            if (currentCommandFuture != null && !currentCommandFuture!!.isDone) {
                currentCommandFuture?.cancel(true)
                currentCommandFuture = null
                cancelled = true
            }
            
            if (cancelled) {
                addSystemMessage("🛑 Request cancelled by user.")
            }
            
            setGenerating(false)
        } else {
            clearChat()
        }
    }
    
    /**
     * Handles attach image button click
     */
    private fun handleAttachImage() {
        addSystemMessage("[Image attachment feature coming soon]")
    }
    
    /**
     * Updates the stance list (replaced old model list)
     */
    fun updateModelList() {
        val settings = RoninSettingsState.instance
        val currentStance = settings.activeStance
        
        controlBar.setModelsLoading(true)
        
        ApplicationManager.getApplication().executeOnPooledThread {
            // Get all available stances
            val stanceNames = settings.stances.map { it.name }
            
            SwingUtilities.invokeLater {
                val stances = stanceNames.toTypedArray()
                controlBar.updateModels(stances, currentStance)
                controlBar.setModelsLoading(false)
            }
        }
    }
    
    /**
     * Clears the chat
     */
    fun clearChat() {
        SwingUtilities.invokeLater {
            chatPanel.removeAll()
            chatPanel.revalidate()
            chatPanel.repaint()
            messageHistory.clear()
            api.clearSession()
        }
    }
    
    /**
     * Exports the conversation to a file
     */
    fun exportConversation() {
        val descriptor = com.intellij.openapi.fileChooser.FileSaverDescriptor(
            "Export Conversation",
            "Save conversation history",
            "json"
        )
        val baseDir = com.intellij.openapi.vfs.VfsUtil.getUserHomeDir()
        val wrapper = com.intellij.openapi.fileChooser.FileChooserFactory.getInstance()
            .createSaveFileDialog(descriptor, project)
        val fileWrapper = wrapper.save(baseDir, "ronin_chat_export.json")
        
        if (fileWrapper != null) {
            val file = fileWrapper.file
            try {
                val mapper = com.fasterxml.jackson.databind.ObjectMapper()
                    .enable(com.fasterxml.jackson.databind.SerializationFeature.INDENT_OUTPUT)
                mapper.writeValue(file, messageHistory)
                addSystemMessage("✅ Conversation exported to: ${file.absolutePath}")
            } catch (e: Exception) {
                addSystemMessage("❌ Failed to export: ${e.message}")
            }
        }
    }
    
    // UI Helper Methods
    
    private fun setGenerating(generating: Boolean) {
        isGenerating = generating
        controlBar.setGenerating(generating)
        inputField.setEnabled(!generating)
    }
    
    private fun addUserMessage(message: String, isAuto: Boolean = false) {
        val label = if (isAuto) {
            "${MyBundle.message("toolwindow.you")} (Auto)"
        } else {
            MyBundle.message("toolwindow.you")
        }
        addMessageBubble(label, message)
    }
    
    private fun addAssistantMessage(message: String) {
        addMessageBubble(MyBundle.message("toolwindow.ronin"), message)
    }
    
    private fun addSystemMessage(message: String) {
        addMessageBubble("System", message)
    }
    
    private fun addThinkingMessage(message: String) {
        addMessageBubble("Ronin Thinking", message)
    }
    
    private fun addMessageBubble(role: String, message: String) {
        SwingUtilities.invokeLater {
            val isUser = role.contains(MyBundle.message("toolwindow.you")) || role.contains("You")
            val isSystem = role == "System"
            val isThinking = role == "Ronin Thinking"
            
            val bubble = when {
                isUser -> MessageBubble.createUserMessage(message, scrollPane.viewport.width)
                isSystem -> MessageBubble.createSystemMessage(message, scrollPane.viewport.width)
                isThinking -> MessageBubble.createThinkingMessage(message, scrollPane.viewport.width)
                else -> MessageBubble.createAssistantMessage(message, scrollPane.viewport.width)
            }
            
            chatPanel.add(bubble)
            chatPanel.add(Box.createVerticalStrut(10))
            chatPanel.revalidate()
            scrollToBottom()
        }
    }
    
    private fun scrollToBottom() {
        val bar = scrollPane.verticalScrollBar
        bar.value = bar.maximum
    }
    
    /**
     * Gets the main panel content
     */
    fun getContent(): JComponent {
        return mainPanel
    }
    
    /**
     * Sends a message programmatically (for use by actions/commands)
     * This is a simplified interface for external callers that don't need
     * to handle all the callbacks
     */
    fun sendMessageProgrammatically(message: String) {
        handleSendMessage(message)
    }

}