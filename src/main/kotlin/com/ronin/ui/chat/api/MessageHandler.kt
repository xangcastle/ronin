package com.ronin.ui.chat.api

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.ronin.service.CommandResult
import com.ronin.service.CommandService

/**
 * Handles processing of messages and slash commands
 */
class MessageHandler(private val project: Project) {
    
    /**
     * Processes a user message and determines if it's a slash command
     * Returns null if it's a regular message, or a CommandResult if it's a slash command
     */
    fun processSlashCommand(message: String): CommandResult? {
        if (!message.startsWith("/")) {
            return null
        }
        
        val commandService = project.service<CommandService>()
        return commandService.executeCommand(message)
    }
    
    /**
     * Parses the LLM response and extracts relevant information
     */
    fun parseResponse(response: String): LLMResponse {
        val result = com.ronin.service.ResponseParser.parseAndApply(response, project)
        
        return LLMResponse(
            text = result.text,
            scratchpad = result.scratchpad,
            toolOutput = result.toolOutput,
            commandToRun = result.commandToRun,
            requiresFollowUp = result.requiresFollowUp
        )
    }
}
