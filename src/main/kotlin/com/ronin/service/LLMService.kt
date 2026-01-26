package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

interface LLMService {
    fun sendMessage(prompt: String, context: String? = null, history: List<Map<String, String>> = emptyList(), images: List<String> = emptyList()): String
    fun getAvailableModels(provider: String): List<String>
    fun fetchAvailableModels(provider: String): List<String>
}

class LLMServiceImpl(private val project: Project) : LLMService {
    private val client = java.net.http.HttpClient.newHttpClient()

    override fun sendMessage(prompt: String, context: String?, history: List<Map<String, String>>, images: List<String>): String {
        val settings = com.ronin.settings.RoninSettingsState.instance
        val configService = project.service<RoninConfigService>()
        val projectContext = configService.getProjectStructure()
        val projectRules = configService.getProjectRules()
                val systemPrompt = """
            You are Ronin, an autonomous agentic developer assistant.
            
            **ENVIRONMENT:**
            - You are working in a Bazel-based monorepo.
            - $projectContext
            - Allowed Tools: ${settings.allowedTools}
            
            ${if (!projectRules.isNullOrBlank()) "**PROJECT RULES:**\n$projectRules\n" else ""}
            
            **CORE PROTOCOL (Thought-Action):**
            You must always "think" before you act. Your response must follow this strict XML format:
            
            <analysis>
            1. Analyze the user request and file context.
            2. Plan your specific edits or actions.
            3. Verify your plan against project rules.
            </analysis>
            
            <execute>
                <!-- YOU MUST PROVIDE EXACTLY ONE COMMAND HERE. DO NOT LEAVE EMPTY. -->
                <command name="COMMAND_NAME">
                    <arg name="ARG_NAME">ARG_VALUE</arg>
                </command>
            </execute>
            
            **CRITICAL RULES:** 
            1. **MANDATORY EXECUTION**: You MUST output an `<execute>` block in every single turn.
            2. **NO OPEN LOOPS**: If you are just replying to the user (no code action), you MUST use `task_complete` with your message as `content`.
            3. **ANTI-STALLING**: Do NOT stop at `<analysis>`. If you stop, the system hangs. You must proceed to `<execute>`.
            4. **AUTOMATION FAILURE**: If you output only analysis, the automation fails.
            
            **AVAILABLE COMMANDS:**
            
            1. `read_code`: Inspect files.
               <command name="read_code">
                   <arg name="path">libs/core/utils.py</arg>
                   <arg name="start_line">1</arg> <!-- Optional, default 1 -->
                   <arg name="end_line">100</arg> <!-- Optional, default 500 lines -->
               </command>
            
            2. `write_code`: Modify files. Use CDATA for content to avoid escaping issues.
               <command name="write_code">
                   <arg name="path">libs/core/utils.py</arg>
                   <!-- OPTION A: Line-Based Replacement (Preferred) -->
                   <arg name="start_line">10</arg>
                   <arg name="end_line">15</arg>
                   <content><![CDATA[
            def new_function():
                return True
            ]]></content>
               </command>
               
               OR
               
               <command name="write_code">
                   <arg name="path">...</arg>
                   <!-- OPTION B: Search & Replace (Fuzzy) -->
                   <arg name="code_search"><![CDATA[def old_function():]]></arg>
                   <content><![CDATA[def new_function():]]></content>
               </command>

            3. `run_command`: Execute shell commands.
               <command name="run_command">
                   <arg name="command">./gradlew build</arg>
               </command>

            4. `task_complete`: Signal completion.
               <command name="task_complete">
                   <arg name="content">I have finished the task.</arg>
               </command>

            **INSTRUCTIONS:**
            - **MODE: VIBE CODING (Architect/Editor)**:
                - **Self-Healing**: If you make a mistake, analyze it in <analysis> and fix it in the next turn.
                - **Context Echo**: After editing, the tool returns the result. READ IT.
            - **SAFE EDITING**: 
                - Use `write_code` with `start_line`/`end_line` whenever possible.
            - **STRICT ANTI-REPETITION**: If a command fails, do not retry blindly. Read the file, understand the state, then fix.
            
            User Request: $prompt
            
            (REMINDER: You MUST end your response with an <execute> block containing a command. Do not just analyze.)
            Context: $context
        """.trimIndent()

        if (settings.provider == "OpenAI") {
            // enforceJson = false for v3 Protocol (XML)
            return sendOpenAIRequest(systemPrompt, history, settings, false)
        }
        return "Error: Only OpenAI supported for v2 Architecture currently."
    }

    private fun sendOpenAIRequest(systemPrompt: String, history: List<Map<String, String>>, settings: com.ronin.settings.RoninSettingsState, enforceJson: Boolean): String {
        val apiKey = com.ronin.settings.CredentialHelper.getApiKey("openaiApiKey")
            ?: System.getenv("OPENAI_API_KEY")
        
        if (apiKey.isNullOrBlank()) return "Error: OpenAI API Key not found."

        val model = settings.model.ifBlank { "gpt-4o" }
        
        // Prune history to manage token limits (approx heuristic)
        val prunedHistory = pruneHistory(history, 20)
        
        val jsonBody = createOpenAIRequestBody(model, systemPrompt, prunedHistory, enforceJson)

        // Custom endpoint handling for specific internal/preview models
        val endpoint = if (model.contains("codex") || model.contains("gpt-5.1")) {
            "https://api.openai.com/v1/responses"
        } else {
            "https://api.openai.com/v1/chat/completions"
        }

        val request = java.net.http.HttpRequest.newBuilder()
            .timeout(java.time.Duration.ofSeconds(300))
            .uri(java.net.URI.create(endpoint))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        var attempt = 0
        val maxRetries = 3
        
        while (attempt < maxRetries) {
            try {
                attempt++
                val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                val statusCode = response.statusCode()
                
                if (statusCode == 200) {
                    return extractContentFromResponse(response.body())
                } else if ((statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504) && attempt < maxRetries) {
                     // Retryable error
                     try { Thread.sleep(1000L * attempt) } catch (e: InterruptedException) { Thread.currentThread().interrupt(); return "Error: Interrupted" }
                     continue
                } else {
                    return "Error: $statusCode - ${response.body()}"
                }
            } catch (e: Exception) {
                if (attempt < maxRetries) {
                    try { Thread.sleep(1000L * attempt) } catch (ie: InterruptedException) { Thread.currentThread().interrupt(); return "Error: Interrupted" }
                    continue
                }
                return "Error sending request: ${e.message}"
            }
        }
        return "Error: Failed after $maxRetries attempts."
    }

    private fun pruneHistory(history: List<Map<String, String>>, maxMessages: Int): List<Map<String, String>> {
        if (history.size <= maxMessages) return history
        return history.takeLast(maxMessages)
    }

    internal fun createOpenAIRequestBody(model: String, systemPrompt: String, history: List<Map<String, String>>, enforceJson: Boolean): String {
        val messages = mutableListOf<Map<String, String>>()
        messages.add(mapOf("role" to "system", "content" to systemPrompt))
        
        // Add only relevant history (maybe filtered? for now all)
        messages.addAll(history.map { 
             mapOf("role" to (it["role"] ?: "user"), "content" to (it["content"] ?: ""))
        })
        
        val isResponsesApi = model.contains("codex") || model.contains("gpt-5.1")
        
        val requestBody = mutableMapOf<String, Any>(
            "model" to model
        )
        
        if (isResponsesApi) {
            requestBody["input"] = messages
            // For v3 XML protocol, we effectively disable strict schema and rely on prompt
            requestBody["temperature"] = 0.2 // Low temp for code/XML
        } else {
            requestBody["messages"] = messages
            requestBody["temperature"] = 0.1 // Low temp for code/XML
        }
        
        // NOTE: JSON Schema enforcement removed for v3. XML is guided by prompt.
        
        return com.google.gson.Gson().toJson(requestBody)
    }

    private fun extractContentFromResponse(json: String): String {
        val contentKey = "\"content\":"
        val contentStartIdx = json.indexOf(contentKey)
        if (contentStartIdx == -1) return json
        
        val valueStartIdx = json.indexOf("\"", contentStartIdx + contentKey.length)
        if (valueStartIdx == -1) return json
        
        val sb = StringBuilder()
        var escaped = false
        for (i in valueStartIdx + 1 until json.length) {
            val c = json[i]
            if (escaped) {
                when (c) {
                    'n' -> sb.append('\n')
                    'r' -> sb.append('\r')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '\"' -> sb.append('\"')
                    else -> sb.append(c)
                }
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == '\"') {
                break 
            } else {
                sb.append(c)
            }
        }
        return sb.toString()
    }

    override fun getAvailableModels(provider: String): List<String> {
        return when (provider) {
            "OpenAI" -> listOf("gpt-4o", "o1-preview", "o1-mini", "gpt-4-turbo", "gpt-3.5-turbo")
            "Anthropic" -> listOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
            "Google" -> listOf("gemini-1.5-pro", "gemini-1.0-pro")
            "Kimi" -> listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
            "Minimax" -> listOf("abab6.5-chat", "abab6-chat")
            "Ollama" -> listOf("llama3", "mistral", "gemma:7b", "codellama")
            else -> listOf("gpt-4o")
        }
    }
    
    internal fun parseModelsJson(json: String): List<String> {
        return try {
            val tempMap = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<String, Any>
            val data = tempMap["data"] as? List<Map<String, Any>> ?: emptyList()
            
            val models = mutableListOf<String>()
            
            for (modelObj in data) {
                val id = modelObj["id"] as? String ?: continue
                
                val capabilities = (modelObj["capabilities"] as? Map<String, Any>) 
                    ?: (modelObj["features"] as? List<String>)?.associate { it to true } 
                    ?: emptyMap()
                    
                val isChatExplicit = capabilities.keys.any { k -> k.toString().contains("chat") }
                val isCompletionOnly = capabilities.keys.any { k -> (k.toString() == "completion" || k.toString().contains("text-completion")) } && !isChatExplicit
                
                val isGpt = id.startsWith("gpt") || id.startsWith("o1") || id.startsWith("chatgpt")
                val isInstruct = id.contains("instruct")
                val isAudio = id.contains("audio") || id.contains("realtime") || id.contains("tts") || id.contains("whisper")
                val isDallE = id.contains("dall-e")
                // Explicitly exclude known completion-only models that heuristics might miss
                val isKnownCompletion = id.contains("gpt-5.2-pro") || id.contains("davinci") || id.contains("babbage")
                
                var shouldInclude = false
                
                if (isChatExplicit) {
                     shouldInclude = true
                } else if (isCompletionOnly) {
                     shouldInclude = false
                } else {
                    if (isGpt && !isInstruct && !isAudio && !isDallE && !isKnownCompletion) {
                        shouldInclude = true
                    }
                }
                
                if (shouldInclude) {
                    models.add(id)
                }
            }
            models.sorted().reversed()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override fun fetchAvailableModels(provider: String): List<String> {
        if (provider != "OpenAI") return getAvailableModels(provider)

        val apiKey = com.ronin.settings.CredentialHelper.getApiKey("openaiApiKey")
        if (apiKey.isNullOrBlank()) return getAvailableModels(provider)

        val request = java.net.http.HttpRequest.newBuilder()
            .timeout(java.time.Duration.ofSeconds(10))
            .uri(java.net.URI.create("https://api.openai.com/v1/models"))
            .header("Authorization", "Bearer $apiKey")
            .GET()
            .build()
            
        try {
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                return parseModelsJson(response.body())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getAvailableModels(provider)
    }
}
