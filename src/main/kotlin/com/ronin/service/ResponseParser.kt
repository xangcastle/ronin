package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

object ResponseParser {

    fun parseAndApply(response: String, project: Project): String {
        val editService = project.service<EditService>()
        val fileUpdateRegex = "\\[UPDATED_FILE: (.*?)]\\s*```[a-z]*\\n([\\s\\S]*?)\\n```".toRegex()

        var processedResponse = response

        fileUpdateRegex.findAll(response).forEach { matchResult ->
            val filePath = matchResult.groupValues[1].trim()
            val newContent = matchResult.groupValues[2]

            val virtualFile = editService.findFile(filePath)
            if (virtualFile != null) {
                val success = editService.replaceFileContent(virtualFile, newContent)
                if (success) {
                    // Start replacing from the original match to modify the chat output if desired
                    // For now, we just perform the side effect.
                    // processedResponse = processedResponse.replace(matchResult.value, "✅ Updated file: $filePath") 
                }
            }
        }
        
        return processedResponse
    }
}
