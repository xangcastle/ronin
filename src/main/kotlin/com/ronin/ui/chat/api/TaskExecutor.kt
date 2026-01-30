package com.ronin.ui.chat.api

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.ronin.service.LLMService
import java.util.concurrent.Future

/**
 * Executes LLM tasks asynchronously with callbacks for different stages
 * All callbacks are executed on the EDT to ensure thread safety
 */
class TaskExecutor(private val project: Project) {
    
    /**
     * Executes an LLM task asynchronously
     * 
     * @param task The LLM task to execute
     * @param onThinking Callback when the LLM is "thinking" (scratchpad content) - RUNS ON EDT
     * @param onResponse Callback when the main response is received - RUNS ON EDT
     * @param onCommand Callback when a command needs to be executed - RUNS ON EDT
     * @param onFollowUp Callback when a follow-up is required (prompt, summary) - RUNS ON EDT
     * @param onError Callback when an error occurs - RUNS ON EDT
     * @param onComplete Callback when execution is complete (success or failure) - RUNS ON EDT
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

                val llmResponse = messageHandler.parseResponse(response)

                val displayText = if (llmResponse.toolOutput != null) {
                    "${llmResponse.text}\n\n${llmResponse.toolOutput}"
                } else {
                    llmResponse.text
                }

                if (!llmResponse.scratchpad.isNullOrBlank()) {
                    runOnEdt {
                        onThinking(llmResponse.scratchpad)
                    }
                }

                runOnEdt {
                    onResponse(displayText)
                }

                when {
                    llmResponse.commandToRun != null -> {
                        runOnEdt {
                            onCommand(llmResponse.commandToRun)
                        }
                    }
                    llmResponse.requiresFollowUp -> {
                        val followUpPrompt = "The action was completed. Here is the output:\n```\n${llmResponse.toolOutput ?: llmResponse.text}\n```\nAnalyze this and continue."
                        val followUpSummary = "File action complete."
                        runOnEdt {
                            onFollowUp(followUpPrompt, followUpSummary)
                        }
                    }
                    else -> {
                        runOnEdt {
                            onComplete()
                        }
                    }
                }
                
            } catch (e: InterruptedException) {
                runOnEdt { 
                    onComplete() 
                }
            } catch (e: Exception) {
                runOnEdt {
                    onError(e.message ?: "Unknown error")
                    onComplete()
                }
            }
        }
    }
    
    /**
     * Cancels a running task
     */
    fun cancelTask(future: Future<*>?) {
        future?.cancel(true)
    }

    /**
     * Executes an action on the EDT (Event Dispatch Thread)
     * This is required for any UI updates in IntelliJ Platform
     */
    private fun runOnEdt(action: () -> Unit) {
        ApplicationManager.getApplication().invokeLater(action)
    }

}
