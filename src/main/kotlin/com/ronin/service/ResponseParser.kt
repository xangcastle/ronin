package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

data class ParseResult(
    val text: String, 
    val commandToRun: String? = null, 
    val scratchpad: String? = null, 
    val requiresFollowUp: Boolean = false,
    val toolOutput: String? = null
)

object ResponseParser {

    fun parseAndApply(response: String, project: Project): ParseResult {
        val sessionService = project.service<AgentSessionService>()
        // Direct execution mode: No forced planning/approval step.
        
        // 1. Try to parse JSON Step
        var jsonToParse = response.trim()
        val jsonBlockRegex = "```json([\\s\\S]*?)```".toRegex()
        val match = jsonBlockRegex.find(response)
        if (match != null) {
            jsonToParse = match.groupValues[1].trim()
        } else if (jsonToParse.startsWith("```")) {
             jsonToParse = jsonToParse.replaceFirst("```[a-z]*".toRegex(), "").replace("```$".toRegex(), "").trim()
        }

        try {
            val gson = com.google.gson.Gson()
            val step = try {
                 gson.fromJson(jsonToParse, Map::class.java) as? Map<String, Any>
            } catch (e: Exception) {
                 // Try one more time by finding the first { and last }
                 val firstBrace = jsonToParse.indexOf('{')
                 val lastBrace = jsonToParse.lastIndexOf('}')
                 if (firstBrace != -1 && lastBrace != -1 && lastBrace > firstBrace) {
                     gson.fromJson(jsonToParse.substring(firstBrace, lastBrace + 1), Map::class.java) as? Map<String, Any>
                 } else null
            }
            
            if (step != null && (step.containsKey("type") || step.containsKey("command"))) {
                val type = step["type"] as? String
                val content = step["content"] as? String ?: ""
                val scratchpad = step["scratchpad"] as? String ?: ""
                
                if (scratchpad.isNotEmpty()) {
                    println("Ronin Thinking: $scratchpad")
                }
                
                return when (type) {
                    "question", "explanation" -> {
                        ParseResult(content, scratchpad = scratchpad)
                    }
                    "task_complete" -> {
                        sessionService.clearSession()
                        ParseResult("✅ **TASK COMPLETED**\n\n$content", scratchpad = scratchpad)
                    }
                    "command" -> {
                         // Extract command
                         val rawCmd = step["command"] ?: step["cmd"] ?: step["execute"]
                         val args = step["args"]
                         val commandStr = if (rawCmd != null && args is List<*>) {
                             "$rawCmd " + args.joinToString(" ")
                         } else {
                             rawCmd?.toString()
                         }
                         ParseResult(content, commandStr, scratchpad = scratchpad)
                    }
                    "read_code" -> {
                        val path = step["path"] as? String
                        val startLine = (step["startLine"] as? Number)?.toInt() ?: 1
                        val endLine = (step["endLine"] as? Number)?.toInt() ?: -1
                        
                        if (path != null) {
                            val editService = project.service<EditService>()
                            try {
                                val vFile = editService.findFile(path)
                                if (vFile != null) {
                                    val lines = String(vFile.contentsToByteArray()).lines()
                                    val startIdx = (startLine - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                                    val endIdx = if (endLine == -1) (startIdx + 500).coerceAtMost(lines.size) else endLine.coerceAtMost(lines.size)
                                    
                                    val selectedLines = lines.subList(startIdx, endIdx)
                                    val fileContent = selectedLines.joinToString("\n")
                                    val isTruncated = endIdx < lines.size || startIdx > 0
                                    
                                    val output = "**Reading File:** `$path` (Lines $startLine-$endIdx of ${lines.size})\n" +
                                               "```\n$fileContent\n```" +
                                               (if (isTruncated) "\n...(truncated)" else "")
                                    
                                    ParseResult(content, scratchpad = scratchpad, requiresFollowUp = true, toolOutput = output)
                                } else {
                                    ParseResult("Error: File not found at $path (Tried relative and absolute resolution)", scratchpad = scratchpad, requiresFollowUp = true)
                                }
                            } catch(e: Exception) {
                                ParseResult("Error reading file: ${e.message}", scratchpad = scratchpad, requiresFollowUp = true)
                            }
                        } else {
                            ParseResult("Error: No path provided for read_code", scratchpad = scratchpad)
                        }
                    }
                    "write_code" -> {
                        val path = step["path"] as? String
                        val search = step["code_search"] as? String
                        val replace = step["code_replace"] as? String
                        
                        if (path != null && replace != null) {
                            val editService = project.service<EditService>()
                            // Use search/replace if search is provided, else overwrite? Schema says search is required.
                            // If search is empty/null, maybe append or overwrite? Let's assume replaceFileContent for now if search missing?
                            // But Schema says 'code_search' description "Exact code to search for".
                            val result = if (search.isNullOrBlank()) {
                                editService.replaceFileContent(path, replace)
                            } else {
                                val results = editService.applyEdits(listOf(EditService.EditOperation(path, search, replace)))
                                if (results.isNotEmpty()) "Applied 1 edit." else "Failed to apply edit (search string not found)."
                            }
                            val output = "**Edit Applied to `$path`**: $result"
                            ParseResult(content, scratchpad = scratchpad, requiresFollowUp = true, toolOutput = output)
                        } else {
                            ParseResult("Error: Missing path, search, or replace for write_code", scratchpad = scratchpad, requiresFollowUp = true)
                        }
                    }
                    else -> ParseResult(response) // Unknown type fallback
                }
            } else {
                 // Fallback for non-step JSON or partial hallucination
                 // Try to handle legacy hallucination of "commands" array just in case
                 if (step != null && step.containsKey("commands")) {
                     // ... logic from before ...
                     // Actually, if we are in Execution mode, we shouldn't really fall back to legacy unless the model failed completely.
                     // But let's keep it safe.
                 }
            }
        } catch (e: Exception) {
            // Not JSON
        }
        
        // Fallback: If we have a Plan but received non-JSON text, just return it.
        // This might happen if the model refuses to output JSON.
        return ParseResult(response)
    }
}
