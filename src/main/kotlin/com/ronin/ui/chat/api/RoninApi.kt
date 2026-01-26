package com.ronin.ui.chat.api

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.ronin.service.AgentSessionService
import com.ronin.service.CommandResult
import java.util.concurrent.Future

/**
 * Main API facade for chat operations.
 * Coordinates TaskBuilder, TaskExecutor, and MessageHandler to provide
 * a simple interface for the UI layer.
 */
class RoninApi(private val project: Project) {
    
    private val taskBuilder = TaskBuilder(project)
    private val taskExecutor = TaskExecutor(project)
    private val messageHandler = MessageHandler(project)
    
    private var currentTask: Future<*>? = null
    val isExecuting: Boolean
        get() = currentTask != null && !currentTask!!.isDone && !currentTask!!.isCancelled
    
    /**
     * Sends a user message and handles the response through callbacks
     */
    fun sendUserMessage(
        message: String,
        history: List<Map<String, String>>,
        onThinking: (String) -> Unit,
        onResponse: (String, String) -> Unit, // (role, message)
        onCommand: (String) -> Unit,
        onFollowUp: (String, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        // Check for slash commands first
        val commandResult = messageHandler.processSlashCommand(message)
        if (commandResult != null) {
            handleCommandResult(commandResult, onResponse, onError, onComplete, message)
            return
        }
        
        // Add user message to history
        val sessionService = project.service<AgentSessionService>()
        sessionService.addMessage("user", message)
        
        // Build and execute task
        val task = taskBuilder.buildUserMessageTask(message, history)
        executeTaskWithHistory(task, sessionService, onThinking, onResponse, onCommand, onFollowUp, onError, onComplete)
    }
    
    /**
     * Sends a follow-up message (e.g., after command execution)
     */
    fun sendFollowUpMessage(
        prompt: String,
        history: List<Map<String, String>>,
        onThinking: (String) -> Unit,
        onResponse: (String, String) -> Unit,
        onCommand: (String) -> Unit,
        onFollowUp: (String, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        val sessionService = project.service<AgentSessionService>()
        sessionService.addMessage("user", prompt)
        
        val task = taskBuilder.buildFollowUpTask(prompt, history)
        executeTaskWithHistory(task, sessionService, onThinking, onResponse, onCommand, onFollowUp, onError, onComplete)
    }
    
    /**
     * Cancels the current task if one is running
     */
    fun cancelCurrentTask(): Boolean {
        if (isExecuting) {
            taskExecutor.cancelTask(currentTask)
            currentTask = null
            return true
        }
        return false
    }
    
    /**
     * Clears the session history
     */
    fun clearSession() {
        val sessionService = project.service<AgentSessionService>()
        sessionService.clearSession()
    }
    
    /**
     * Gets the saved session history
     */
    fun getSessionHistory(): List<Map<String, String>> {
        val sessionService = project.service<AgentSessionService>()
        return sessionService.getHistory()
    }
    
    // Private helper methods
    
    private fun executeTaskWithHistory(
        task: LLMTask,
        sessionService: AgentSessionService,
        onThinking: (String) -> Unit,
        onResponse: (String, String) -> Unit,
        onCommand: (String) -> Unit,
        onFollowUp: (String, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ) {
        currentTask = taskExecutor.executeTask(
            task = task,
            onThinking = onThinking,
            onResponse = { response ->
                // Extract just the text part for history (not tool output)
                val textOnly = if (response.contains("\n\n")) {
                    response.substringBefore("\n\n")
                } else {
                    response
                }
                sessionService.addMessage("assistant", textOnly)
                onResponse("assistant", response)
            },
            onCommand = onCommand,
            onFollowUp = onFollowUp,
            onError = onError,
            onComplete = {
                currentTask = null
                onComplete()
            }
        )
    }
    
    private fun handleCommandResult(
        result: CommandResult,
        onResponse: (String, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit,
        originalMessage: String
    ) {
        when (result) {
            is CommandResult.Action -> {
                onResponse("System", result.message)
                onComplete()
            }
            is CommandResult.Error -> {
                onError(result.message)
                onComplete()
            }
            is CommandResult.PromptInjection -> {
                onResponse("System", "🔄 Loaded command context...")
                onComplete()
            }
        }
    }
}