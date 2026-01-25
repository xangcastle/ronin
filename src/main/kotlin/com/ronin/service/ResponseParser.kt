package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

data class ParseResult(val text: String, val commandToRun: String? = null)

object ResponseParser {

    fun parseAndApply(response: String, project: Project): ParseResult {
        val sessionService = project.service<AgentSessionService>()
        
        // PHASE 1: PLANNING
        // If we don't have a plan yet, the LLM response IS the plan (Plain Text).
        if (!sessionService.hasPlan()) {
            val cleanPlan = response.trim()
            if (cleanPlan.isNotEmpty()) {
                sessionService.updatePlan(cleanPlan)
                return ParseResult("**PLAN CAPTURED**\n\n$cleanPlan\n\n*(Type 'Proceed' to start execution)*")
            }
        }
        
        // PHASE 2: EXECUTION (Step Loop)
        // If we have a plan, we expect strict JSON "Step".
        
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
            val step = gson.fromJson(jsonToParse, Map::class.java) as? Map<String, Any>
            
            if (step != null && step.containsKey("type")) {
                val type = step["type"] as? String
                val content = step["content"] as? String ?: ""
                
                return when (type) {
                    "question", "explanation" -> {
                        ParseResult(content)
                    }
                    "task_complete" -> {
                        sessionService.clearPlan()
                        ParseResult("✅ **TASK COMPLETED**\n\n$content")
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
                         ParseResult(content, commandStr)
                    }
                    "read_code" -> {
                        val path = step["path"] as? String
                        if (path != null) {
                            // TODO: Implement ReadFile logic here or use a service
                            // For now, let's look for a simple read implementation or defer to user?
                            // User workflow expects us to read it.
                            try {
                                val vFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(path)
                                if (vFile != null) {
                                    val fileContent = String(vFile.contentsToByteArray())
                                    val truncated = fileContent.take(2000) + (if(fileContent.length > 2000) "\n...(truncated)" else "")
                                    ParseResult("$content\n\n**Reading File:** `$path`\n```\n$truncated\n```")
                                    // Note: This result is displayed to the user. 
                                    // The ChatToolWindowFactory needs to ensure this "output" is fed back to the LLM for the NEXT turn.
                                } else {
                                    ParseResult("Error: File not found at $path")
                                }
                            } catch(e: Exception) {
                                ParseResult("Error reading file: ${e.message}")
                            }
                        } else {
                            ParseResult("Error: No path provided for read_code")
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
                            ParseResult("$content\n\n**Edit Applied:** $result")
                        } else {
                            ParseResult("Error: Missing path, search, or replace for write_code")
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
