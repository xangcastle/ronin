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
        val activeStanceName = settings.activeStance
        val stance = settings.stances.find { it.name == activeStanceName } 
            ?: throw IllegalStateException("Active stance '$activeStanceName' not found in configuration.")
            
        val systemPrompt = stance.systemPrompt.trimIndent()

        if (stance.provider == "OpenAI") {
            var apiKey = com.ronin.settings.CredentialHelper.getApiKey(stance.credentialId)
            
            if (!stance.encryptedKey.isNullOrBlank()) {
                try {
                    val decoded = String(java.util.Base64.getDecoder().decode(stance.encryptedKey))
                    if (decoded.isNotBlank()) apiKey = decoded
                } catch (e: Exception) {
                    println("Ronin: Failed to decode encrypted key for stance ${stance.name}")
                }
            }
            
            if (apiKey.isNullOrBlank()) return "Error: No API Key found for credential ID '${stance.credentialId}'. Please configure it in Settings."
            
            return sendOpenAIRequest(systemPrompt, history, stance.model, apiKey, false)
        }
        return "Error: Provider '${stance.provider}' not supported yet."
    }

    private fun sendOpenAIRequest(systemPrompt: String, history: List<Map<String, String>>, model: String, apiKey: String, enforceJson: Boolean): String {
        
        val prunedHistory = pruneHistory(history, 20)
        
        val jsonBody = createOpenAIRequestBody(model, systemPrompt, prunedHistory, enforceJson)

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
            requestBody["temperature"] = 0.2
        } else {
            requestBody["messages"] = messages
            requestBody["temperature"] = 0.1
        }
        
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
            val tempMap = com.google.gson.Gson().fromJson(json, Map::class.java) as Map<*, *>
            val data = tempMap["data"] as? List<*> ?: emptyList<Any>()
            
            val models = mutableListOf<String>()
            
            for (modelItem in data) {
                val modelObj = modelItem as? Map<*, *> ?: continue
                val id = modelObj["id"] as? String ?: continue
                
                val capabilities = (modelObj["capabilities"] as? Map<*, *>) 
                    ?: (modelObj["features"] as? List<*>)?.associate { it.toString() to true } 
                    ?: emptyMap<Any, Any>()
                    
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
        // Return static list supported by this version of Ronin
        return getAvailableModels(provider)
    }
}
