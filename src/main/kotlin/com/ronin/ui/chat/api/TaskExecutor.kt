package com.ronin.ui.chat.api

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.ronin.service.LLMService
import java.util.concurrent.Future

/**
 * Executes LLM tasks asynchronously with callbacks for different stages
 */
class TaskExecutor(private val project: Project) {
    
    /**
     * Executes an LLM task asynchronously
     * 
     * @param task The LLM task to execute
     * @param onThinking Callback when the LLM is "thinking" (scratchpad content)
     * @param onResponse Callback when the main response is received
     * @param onCommand Callback when a command needs to be executed
     * @param onFollowUp Callback when a follow-up is required (prompt, summary)
     * @param onError Callback when an error occurs
     * @param onComplete Callback when execution is complete (success or failure)
     * @return Future that can be used to cancel the task
     */
    fun executeTask(
        task: LLMTask,
        onThinking: (String) -> Unit,
        onResponse: (String) -> Unit,
        onCommand: (String) -> Unit,
        onFollowUp: (String, String) -> Unit,
        onError: (String) -> Unit,
        onComplete: () -> Unit
    ): Future<*> {
        val llmService = project.service<LLMService>()
        val messageHandler = MessageHandler(project)
        
        return ApplicationManager.getApplication().executeOnPooledThread {
            try {
                if (Thread.interrupted()) {
                    throw InterruptedException()
                }

                val response = llmService.sendMessage(
                    task.message,
                    task.context,
                    ArrayList(task.history)
                )
                
                if (Thread.interrupted()) {
                    throw InterruptedException()
                }
                
                // Parse response
                val llmResponse = messageHandler.parseResponse(response)
                
                // Handle scratchpad/thinking
                if (!llmResponse.scratchpad.isNullOrBlank()) {
                    onThinking(llmResponse.scratchpad)
                }
                
                // Handle main response
                val displayText = if (llmResponse.toolOutput != null) {
                    "${llmResponse.text}\n\n${llmResponse.toolOutput}"
                } else {
                    llmResponse.text
                }
                onResponse(displayText)
                
                // Handle follow-up actions
                when {
                    llmResponse.commandToRun != null -> {
                        onCommand(llmResponse.commandToRun)
                    }
                    llmResponse.requiresFollowUp -> {
                        val followUpPrompt = "The action was completed. Here is the output:\n```\n${llmResponse.toolOutput ?: llmResponse.text}\n```\nAnalyze this and continue."
                        val followUpSummary = "File action complete."
                        onFollowUp(followUpPrompt, followUpSummary)
                    }
                    else -> {
                        onComplete()
                    }
                }
                
            } catch (e: InterruptedException) {
                onComplete()
            } catch (e: Exception) {
                onError(e.message ?: "Unknown error")
                onComplete()
            }
        }
    }
    
    /**
     * Cancels a running task
     */
    fun cancelTask(future: Future<*>?) {
        future?.cancel(true)
    }
}
