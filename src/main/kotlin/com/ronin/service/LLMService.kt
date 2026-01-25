package com.ronin.service




interface LLMService {
    fun sendMessage(prompt: String, context: String? = null, history: List<Map<String, String>> = emptyList(), images: List<String> = emptyList()): String
    fun getAvailableModels(provider: String): List<String>
    fun fetchAvailableModels(provider: String): List<String>
}

class LLMServiceImpl : LLMService {
    private val client = java.net.http.HttpClient.newHttpClient()

    override fun sendMessage(prompt: String, context: String?, history: List<Map<String, String>>, images: List<String>): String {
        val settings = com.ronin.settings.RoninSettingsState.instance
        
        // Context is only added to the LATEST prompt if provided, or maybe as a system message?
        // Let's add it to the latest user message for now.
        val fullPrompt = if (context != null) {
            val allowedTools = settings.allowedTools.ifBlank { "git, docker, kubectl, argocd, aws, bazel" }
            
            """
            System Instructions:
            You are the SENIOR DEVELOPER assigned to this task.
            Your goal is to solve the user's request COMPLETELY and AUTONOMOUSLY.
            
            **ENVIRONMENT & PERMISSIONS:**
            - You are running **LOCALLY** on the user's machine.
            - You have **FULL TERMINAL ACCESS**.
            - You CAN and SHOULD execute ANY installed CLI tool (e.g., $allowedTools).
            - **NEVER** refuse to run a command claiming you "don't have access" or "can't connect to the cluster". 
            - If the user asks you to check something, **RUN THE COMMAND**.
            1. **PLAN**: Analyze the request. What files need to change? What commands need to run?
            2. **EXECUTE**: Use the available tools to apply changes and run commands. 
               - Do NOT just "suggest" code. YOU must update the files.
               - Do NOT ask the user to run commands. YOU run them.
            3. **VERIFY**: After every change, run a test or build command to verify it works.
               - If it fails, fix it immediately. Step 2 and 3 should form a loop.
            4. **REPORT**: When finished, provide a concise summary with emojis 🧠 of what you did.
            
            AVAILABLE TOOLS:
            
            1. **Update File**:
               [UPDATED_FILE: /absolute/path/to/file]
               ```<language>
               ... new content ...
               ```
               *Use this to modify code. Write the FULL file content.*
               
            2. **Execute Command**:
               [EXECUTE: command args...]
               *Use this to run terminal commands (ls, cat, grep, ./gradlew test, etc).*
               *Always check the output of your commands!*
               
            IMPORTANT RULES:
            - **Be Proactive**: If you need info, use `[EXECUTE: ls]` or `[EXECUTE: cat]`. Don't ask unless stuck.
            - **Be thorough**: Don't leave placeholders. Write production-ready code.
            - ** verify**: Never claim a task is done without running a verification command (test, build, or lint).
            
            Context:
            $context
            
            User Request: $prompt
            """.trimIndent()
        } else {
            prompt
        }

        if (settings.provider == "OpenAI") {
            return sendOpenAIRequest(fullPrompt, history, settings)
        }
        
        // ... (mock implementation omitted for brevity, assume similar)
         val apiKeyName = when(settings.provider) {
            "Anthropic" -> "anthropicApiKey"
            else -> "openaiApiKey"
        }
        val apiKey = com.ronin.settings.CredentialHelper.getApiKey(apiKeyName)
        val hasKey = if (!apiKey.isNullOrBlank()) "Yes" else "No"
        return "Rank: Mock Response ($hasKey)"
    }

    private fun sendOpenAIRequest(currentPrompt: String, history: List<Map<String, String>>, settings: com.ronin.settings.RoninSettingsState): String {
        val apiKey = com.ronin.settings.CredentialHelper.getApiKey("openaiApiKey")
        if (apiKey.isNullOrBlank()) {
            return "Error: OpenAI API Key not found. Please configure it in Settings."
        }

        val model = settings.model.ifBlank { "gpt-4o" }
        
        val jsonBody = createOpenAIRequestBody(model, currentPrompt, history)

        val request = java.net.http.HttpRequest.newBuilder()
            .timeout(java.time.Duration.ofSeconds(300)) // Increased timeout for reasoning models
            .uri(java.net.URI.create("https://api.openai.com/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $apiKey")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
            .build()

        try {
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            val responseBody = response.body()
            
            if (response.statusCode() == 200) {
                return extractContentFromResponse(responseBody)
            } else {
                return "Error: Received status code ${response.statusCode()}\nResponse: $responseBody"
            }
        } catch (e: Exception) {
            return "Error sending request: ${e.message}"
        }
    }

    internal fun createOpenAIRequestBody(model: String, currentPrompt: String, history: List<Map<String, String>>): String {
        val messages = history.map { 
            mapOf("role" to (it["role"] ?: "user"), "content" to (it["content"] ?: ""))
        }.toMutableList()
        
        messages.add(mapOf("role" to "user", "content" to currentPrompt))
        
        val isReasoningModel = model.startsWith("o1") || model.contains("gpt-5")
        
        val requestBody = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to messages
        )
        
        if (isReasoningModel) {
            requestBody["temperature"] = 1
        } else {
            requestBody["temperature"] = 0.1
        }
        
        return com.google.gson.Gson().toJson(requestBody)
    }

    private fun extractContentFromResponse(json: String): String {
        // Manual parsing to avoid StackOverflowError with regex on large responses
        val key = "\"content\""
        var startIndex = json.indexOf(key)
        if (startIndex == -1) return json
        
        // Move past "content"
        startIndex += key.length
        
        // Find the start quote of the value
        val quoteStart = json.indexOf("\"", startIndex)
        if (quoteStart == -1) return json
        
        // Iterate to find the matching end quote, handling escapes
        var current = quoteStart + 1
        while (current < json.length) {
            when (json[current]) {
                '\\' -> {
                    // Skip the next character (escaped)
                    current += 2
                }
                '"' -> {
                    // Found the end
                    val content = json.substring(quoteStart + 1, current)
                    return content.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\")
                }
                else -> {
                    current++
                }
            }
        }
        
        return json
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
