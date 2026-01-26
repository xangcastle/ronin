package com.ronin.ui.chat.api

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.AppExecutorUtil

/**
 * Builds LLM tasks with appropriate context without the UI needing to know the details
 */
class TaskBuilder(private val project: Project) {
    
    /**
     * Builds a task for a user message with full project context
     */
    fun buildUserMessageTask(
        message: String,
        history: List<Map<String, String>>
    ): LLMTask {
        val context = buildContext()
        return LLMTask(
            message = message,
            context = context,
            history = history
        )
    }
    
    /**
     * Builds a task for a follow-up message (e.g., after command execution)
     */
    fun buildFollowUpTask(
        prompt: String,
        history: List<Map<String, String>>
    ): LLMTask {
        val context = buildContext()
        return LLMTask(
            message = prompt,
            context = context,
            history = history
        )
    }
    
    /**
     * Builds the context string including active file and project structure
     */
    private fun buildContext(): String {
        val configService = project.service<com.ronin.service.RoninConfigService>()

        val activeFile = configService.getActiveFileContent()

        val projectStructure = ReadAction.compute<String, Throwable> {
            configService.getProjectStructure()
        }
        
        val contextBuilder = StringBuilder()
        if (activeFile != null) {
            contextBuilder.append("Active File Content:\n```\n$activeFile\n```\n\n")
        }
        contextBuilder.append(projectStructure)
        
        return contextBuilder.toString()
    }
}
