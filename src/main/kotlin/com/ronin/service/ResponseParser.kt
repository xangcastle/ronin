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
        
        // 1. Extract <analysis> (Thinking)
        val analysisRegex = "<analysis>([\\s\\S]*?)</analysis>".toRegex()
        val analysisMatch = analysisRegex.find(response)
        val scratchpad = analysisMatch?.groupValues?.get(1)?.trim()
        
        if (!scratchpad.isNullOrEmpty()) {
            println("Ronin Thinking (Protocol v3): $scratchpad")
        }

        // 2. Extract <execute> (Action)
        val executeRegex = "<execute>([\\s\\S]*?)</execute>".toRegex()
        val executeMatch = executeRegex.find(response)
        
        if (executeMatch == null) {
            // Conversational response (no action)
            var cleanText = response.replace(analysisRegex, "").trim()
            if (cleanText.isEmpty() && !scratchpad.isNullOrEmpty()) {
                // Fallback: If no text but we have thinking, show the thinking to avoid "silent death"
                cleanText = "🤔 **Thinking Process:**\n\n$scratchpad"
            }
            return ParseResult(cleanText, scratchpad = scratchpad)
        }

        val executeContent = executeMatch.groupValues[1].trim()
        
        // 3. Parse <command> inside <execute>
        // Format: <command name="..."> ... </command>
        val commandRegex = "<command\\s+name=\"([^\"]+)\">([\\s\\S]*?)</command>".toRegex()
        val commandMatch = commandRegex.find(executeContent)
        
        if (commandMatch != null) {
            val commandName = commandMatch.groupValues[1]
            val commandBody = commandMatch.groupValues[2]
            
            // Extract Arguments
            val args = parseArguments(commandBody)
            val contentArg = args["content"] ?: "" // Default message content if any
            
            return when (commandName) {
                "task_complete" -> {
                    sessionService.clearSession()
                    ParseResult("✅ **TASK COMPLETED**\n\n$contentArg", scratchpad = scratchpad)
                }
                "read_code" -> {
                    val path = args["path"]
                    val startLine = args["start_line"]?.toIntOrNull() ?: 1
                    val endLine = args["end_line"]?.toIntOrNull() ?: -1
                    
                    if (path != null) {
                        val editService = project.service<EditService>()
                        try {
                            var output: String? = null
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                                val vFile = editService.findFile(path)
                                if (vFile != null) {
                                    val lines = String(vFile.contentsToByteArray()).lines()
                                    val startIdx = (startLine - 1).coerceAtLeast(0).coerceAtMost(lines.size)
                                    val endIdx = if (endLine == -1) (startIdx + 500).coerceAtMost(lines.size) else endLine.coerceAtMost(lines.size)
                                    
                                    val selectedLines = lines.subList(startIdx, endIdx)
                                    val fileContent = selectedLines.joinToString("\n")
                                    val isTruncated = endIdx < lines.size || startIdx > 0
                                    
                                    output = "**Reading File:** `$path` (Lines $startLine-$endIdx of ${lines.size})\n" +
                                               "```\n$fileContent\n```" +
                                               (if (isTruncated) "\n...(truncated)" else "")
                                }
                            }
                            
                            if (output != null) {
                                ParseResult("Reading $path...", scratchpad = scratchpad, requiresFollowUp = true, toolOutput = output)
                            } else {
                                ParseResult("Error: File not found at $path", scratchpad = scratchpad, requiresFollowUp = true)
                            }
                        } catch(e: Exception) {
                             ParseResult("Error reading file: ${e.message}", scratchpad = scratchpad, requiresFollowUp = true)
                        }
                    } else {
                         ParseResult("Error: Missing 'path' argument for read_code", scratchpad = scratchpad)
                    }
                }
                "write_code" -> {
                    val path = args["path"]
                    // content tag (CDATAs) are handled by parseArguments specialized logic or basic regex below if needed
                    // But our parseArguments handles <arg> tags. 
                    // Special handling for code content usually in <content> or <arg name="code_replace">
                    
                    // Let's assume the prompt instructs: <arg name="code_replace"><![CDATA[...]]></arg>
                    // OR <content><![CDATA[...]]></content> if we defined that.
                    // Implementation Plan said: <content><![CDATA[...]]></content>
                    
                    // Let's refine parseArguments to capture <content> as a key "content"
                    
                    val replace = args["content"] // From <content> tag
                    val search = args["code_search"] // from <arg name="code_search">
                    val startLine = args["start_line"]?.toIntOrNull()
                    val endLine = args["end_line"]?.toIntOrNull()
                    
                    if (path != null && replace != null) {
                         val editService = project.service<EditService>()
                         var resultStr = "No result returned."
                         
                         com.intellij.openapi.application.ApplicationManager.getApplication().invokeAndWait {
                             val result = if (startLine != null && endLine != null) {
                                    val results = editService.applyEdits(listOf(EditService.EditOperation(path, null, replace, startLine = startLine, endLine = endLine)))
                                    if (results.isNotEmpty()) results.first() else "No result returned."
                             } else if (search.isNullOrBlank()) {
                                    editService.replaceFileContent(path, replace)
                             } else {
                                    val results = editService.applyEdits(listOf(EditService.EditOperation(path, search, replace)))
                                    if (results.isNotEmpty()) "Applied 1 edit." else "Failed to apply edit (search string not found)."
                             }
                             resultStr = result
                             
                             // Auto-Validation
                             com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
                         }
                         
                         val validationService = project.service<ValidationService>()
                         val validation = validationService.validateFile(path)
                         val validationMsg = if (validation.isValid) "✅ Syntax Valid" else "⚠️ **SYNTAX ERRORS DETECTED**: ${validation.error}"
                         
                         val output = "**Edit Applied to `$path`**: $resultStr\n**Validation**: $validationMsg"
                         ParseResult("Editing $path...", scratchpad = scratchpad, requiresFollowUp = true, toolOutput = output)
                    } else {
                        ParseResult("Error: Missing 'path' or 'content' for write_code", scratchpad = scratchpad, requiresFollowUp = true)
                    }
                }
                "run_command" -> {
                     val cmd = args["command"]
                     if (cmd != null) {
                         ParseResult("Executing shell command...", commandToRun = cmd, scratchpad = scratchpad)
                     } else {
                         ParseResult("Error: Missing 'command' argument", scratchpad = scratchpad)
                     }
                }
                else -> ParseResult("Unknown command: $commandName", scratchpad = scratchpad)
            }
        }
        
        return ParseResult(response.replace(analysisRegex, "").replace(executeRegex,"").trim(), scratchpad = scratchpad)
    }

    private fun parseArguments(xmlBody: String): Map<String, String> {
        val args = mutableMapOf<String, String>()
        
        // Match <arg name="...">value</arg>
        val argRegex = "<arg\\s+name=\"([^\"]+)\">([\\s\\S]*?)</arg>".toRegex()
        argRegex.findAll(xmlBody).forEach { match ->
            args[match.groupValues[1]] = match.groupValues[2].trim()
        }
        
        // Match <content>...</content> (Special case for large code blocks)
        val contentRegex = "<content>([\\s\\S]*?)</content>".toRegex()
        val contentMatch = contentRegex.find(xmlBody)
        if (contentMatch != null) {
            val raw = contentMatch.groupValues[1].trim()
            // Strip CDATA if present
            val clean = if (raw.startsWith("<![CDATA[") && raw.endsWith("]]>")) {
                raw.substring(9, raw.length - 3)
            } else {
                raw
            }
            args["content"] = clean
        }
        
        return args
    }
}
