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
            """
            System Instructions:
            You are Ronin, an intelligent coding agent.
            You have access to the user's codebase.
            To UPDATE a file, you MUST use the following format:
            [UPDATED_FILE: <path_to_file>]
            ```<language>
            ... full content of the file ...
            ```
            
            To EXECUTE a terminal command, use:
            [EXECUTE: <command>]
            (e.g., [EXECUTE: ls -la] or [EXECUTE: bazel test //...])
            
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
        
        // Build messages JSON array
        // History contains {"role":.., "content":..} maps
        // We need to escape them properly
        
        val messagesBuilder = StringBuilder()
        
        // Add history
        for (msg in history) {
            val role = msg["role"] ?: "user"
            val content = msg["content"] ?: ""
            val escapedContent = content.replace("\"", "\\\"").replace("\n", "\\n")
            messagesBuilder.append("""{"role": "$role", "content": "$escapedContent"},""")
        }
        
        // Add current message
        val escapedCurrent = currentPrompt.replace("\"", "\\\"").replace("\n", "\\n")
        messagesBuilder.append("""{"role": "user", "content": "$escapedCurrent"}""")
        
        val jsonBody = """
            {
                "model": "$model",
                "max_tokens": 4096,
                "temperature": 0.1,
                "messages": [
                    $messagesBuilder
                ]
            }
        """.trimIndent()

        val request = java.net.http.HttpRequest.newBuilder()
            .timeout(java.time.Duration.ofSeconds(60))
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
        // This is now a simple getter, but typically the UI should call fetchAvailableModels() 
        // which might cache the result. For now, let's keep the hardcoded list as a fallback
        // until the async fetch completes.
        return when (provider) {
            "OpenAI" -> listOf("gpt-4o", "gpt-4-turbo", "gpt-3.5-turbo")
            "Anthropic" -> listOf("claude-3-opus-20240229", "claude-3-sonnet-20240229", "claude-3-haiku-20240307")
            "Google" -> listOf("gemini-1.5-pro", "gemini-1.0-pro")
            "Kimi" -> listOf("moonshot-v1-8k", "moonshot-v1-32k", "moonshot-v1-128k")
            "Minimax" -> listOf("abab6.5-chat", "abab6-chat")
            "Ollama" -> listOf("llama3", "mistral", "gemma:7b", "codellama")
            else -> listOf("gpt-4o")
        }
    }
    
    // Validating against the interface logic which we will update next, but technically 
    // we need to add this method to the interface first or simultaneously. 
    // Since I can only edit one file block efficiently, I'll add the implementation here 
    // and assume I'll update the interface header in a separate tool call if needed
    // OR I can use multi-replace if I knew the line numbers perfectly.
    // Let's add the implementation as a new method.
    
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
                // Parse JSON manually to find "id"
                val json = response.body()
                val models = mutableListOf<String>()
                val pattern = "\"id\"\\s*:\\s*\"([^\"]+)\"".toRegex()
                pattern.findAll(json).forEach { match ->
                    val id = match.groupValues[1]
                    // Filter for chat models to avoid clutter
                    if (id.startsWith("gpt") || id.startsWith("o1")) {
                        models.add(id)
                    }
                }
                return models.sorted().reversed()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return getAvailableModels(provider)
    }
}
