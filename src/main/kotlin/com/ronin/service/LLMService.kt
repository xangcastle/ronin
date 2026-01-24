package com.ronin.service




interface LLMService {
    fun sendMessage(prompt: String, images: List<String> = emptyList()): String
    fun getAvailableModels(provider: String): List<String>
}

class LLMServiceImpl : LLMService {
    private val client = java.net.http.HttpClient.newHttpClient()

    override fun sendMessage(prompt: String, images: List<String>): String {
        val settings = com.ronin.settings.RoninSettingsState.instance
        
        if (settings.provider == "OpenAI") {
            return sendOpenAIRequest(prompt, settings)
        }

        // Keep mock behavior for other providers for now
        // Simulate network delay to test UI responsiveness
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        
        val apiKeyName = when(settings.provider) {
            "Anthropic" -> "anthropicApiKey"
            "Google" -> "googleApiKey"
            "Kimi" -> "kimiApiKey"
            "Minimax" -> "minimaxApiKey"
            else -> "openaiApiKey"
        }
        val apiKey = com.ronin.settings.CredentialHelper.getApiKey(apiKeyName)
        val hasKey = if (!apiKey.isNullOrBlank()) "Yes" else "No"
        
        return "Rank: Mock Response\nProvider: ${settings.provider}\nModel: ${settings.model}\nAPI Key Present: $hasKey\n\nEcho: $prompt"
    }

    private fun sendOpenAIRequest(prompt: String, settings: com.ronin.settings.RoninSettingsState): String {
        val apiKey = com.ronin.settings.CredentialHelper.getApiKey("openaiApiKey")
        if (apiKey.isNullOrBlank()) {
            return "Error: OpenAI API Key not found. Please configure it in Settings."
        }

        val model = settings.model.ifBlank { "gpt-4o" }
        val escapedPrompt = prompt.replace("\"", "\\\"").replace("\n", "\\n")
        
        val jsonBody = """
            {
                "model": "$model",
                "messages": [
                    {"role": "user", "content": "$escapedPrompt"}
                ]
            }
        """.trimIndent()

        val request = java.net.http.HttpRequest.newBuilder()
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
        // Simple regex extraction to avoid external JSON dependency issues
        val contentPattern = "\"content\"\\s*:\\s*\"(.*?)\"".toRegex(RegexOption.DOT_MATCHES_ALL)
        val match = contentPattern.find(json)
        return match?.groups?.get(1)?.value?.replace("\\n", "\n")?.replace("\\\"", "\"") ?: json
    }

    override fun getAvailableModels(provider: String): List<String> {
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
}
