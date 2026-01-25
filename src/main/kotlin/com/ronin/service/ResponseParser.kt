package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

data class ParseResult(val text: String, val commandToRun: String? = null)

object ResponseParser {

    fun parseAndApply(response: String, project: Project): ParseResult {
        // Try JSON parsing first (Structured Output)
        try {
            val gson = com.google.gson.Gson()
            val jsonMap = gson.fromJson(response, Map::class.java) as? Map<String, Any>
            
            if (jsonMap != null && jsonMap.containsKey("explanation") && jsonMap.containsKey("edits")) {
                val explanation = jsonMap["explanation"] as? String ?: ""
                val commands = (jsonMap["commands"] as? List<String>) ?: emptyList()
                val editsRaw = (jsonMap["edits"] as? List<Map<String, String?>>) ?: emptyList()
                
                val editOps = editsRaw.mapNotNull { 
                    val path = it["path"]
                    val replace = it["replace"]
                    val search = it["search"]
                    
                    if (path != null && replace != null) {
                        EditService.EditOperation(path, search, replace)
                    } else {
                        null
                    }
                }
                
                val editService = project.service<EditService>()
                val results = editService.applyEdits(editOps)
                
                val statusMsg = if (results.isNotEmpty()) "\n\n**Edits Applied:**\n" + results.joinToString("\n") { "- $it" } else ""
                val fullText = explanation + statusMsg
                
                val commandToRun = if (commands.isNotEmpty()) commands.joinToString(" && ") else null
                
                return ParseResult(fullText, commandToRun)
            }
        } catch (e: Exception) {
            // Not JSON or schema mismatch, fall back to Regex
        }

        // Legacy / Fallback Regex Parsing
        var processedResponse = response
        
        // 1. Handle File Updates
        val editService = project.service<EditService>()
        val fileUpdateRegex = "\\[UPDATED_FILE: (.*?)]\\s*```[a-z]*\\n([\\s\\S]*?)\\n```".toRegex()

        val sb = StringBuilder(processedResponse)
        val updates = mutableListOf<String>()
        
        fileUpdateRegex.findAll(response).forEach { matchResult ->
            val filePath = matchResult.groupValues[1].trim()
            val newContent = matchResult.groupValues[2]

            // Call legacy wrapper (full rewrite)
            val status = editService.replaceFileContent(filePath, newContent)
            updates.add("📝 $status")
        }
        
        if (updates.isNotEmpty()) {
            sb.append("\n\n" + updates.joinToString("\n"))
            processedResponse = sb.toString()
        }
        
        // 2. Handle Command Execution
        val executeRegex = "\\[EXECUTE: (.*?)]".toRegex()
        var commandToRun: String? = null
        
        val execMatch = executeRegex.find(response)
        if (execMatch != null) {
            commandToRun = execMatch.groupValues[1].trim()
        }
        
        return ParseResult(processedResponse, commandToRun)
    }
}
