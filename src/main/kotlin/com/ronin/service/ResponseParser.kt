package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

data class ParseResult(val text: String, val commandToRun: String? = null)

object ResponseParser {

    fun parseAndApply(response: String, project: Project): ParseResult {
        var processedResponse = response
        
        // 1. Handle File Updates
        val editService = project.service<EditService>()
        val fileUpdateRegex = "\\[UPDATED_FILE: (.*?)]\\s*```[a-z]*\\n([\\s\\S]*?)\\n```".toRegex()

        fileUpdateRegex.findAll(response).forEach { matchResult ->
            val filePath = matchResult.groupValues[1].trim()
            val newContent = matchResult.groupValues[2]

            val virtualFile = editService.findFile(filePath)
            if (virtualFile != null) {
                editService.replaceFileContent(virtualFile, newContent)
                // processedResponse = processedResponse.replace(matchResult.value, "✅ Updated file: $filePath") 
            }
        }
        
        // 2. Handle Command Execution
        // Syntax: [EXECUTE: ls -la]
        val executeRegex = "\\[EXECUTE: (.*?)]".toRegex()
        var commandToRun: String? = null
        
        val execMatch = executeRegex.find(response)
        if (execMatch != null) {
            commandToRun = execMatch.groupValues[1].trim()
            // We strip the command token so it doesn't look ugly, 
            // OR we keep it so the user sees what's happening.
            // Let's keep it but maybe format it? For now, keep as is.
        }
        
        return ParseResult(processedResponse, commandToRun)
    }
}
