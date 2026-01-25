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
        val sessionService = project.service<AgentSessionService>()
        
        val currentPlan = sessionService.currentPlan
        val isPlanningMode = currentPlan == null
        
        val systemPrompt = if (isPlanningMode) {
             """
            You are a SENIOR DEVELOPER.
            Your task is to analyze the user request and create a detailed IMPLEMENTATION PLAN.
            
            **INSTRUCTIONS:**
            - Return ONLY the plan in PLAIN TEXT.
            - Break the task into logical steps.
            - Define clear Acceptance Criteria.
            - Do not execute code yet. Just plan.
            
            User Request: $prompt
            Context: $context
            """.trimIndent()
        } else {
             """
            You are an AGENT executing a plan.
            
            **CURRENT PLAN:**
            $currentPlan
            
            **INSTRUCTIONS:**
            - You are in a recursive execution loop. 
            - Review the history carefully. If you see a 'Command Output', that means the previous step is DONE. 
            - Move to the NEXT step in the plan. Do NOT repeat finished steps.
            - Be DECISIVE. Do not explain your thoughts in plain text unless you are asking a 'question'.
            - Respond in strictly valid JSON matching the schema.
            
            **SCHEMA:**
            {"type": "command|read_code|write_code|question|explanation|task_complete", "content": "reasoning", "command": "...", "path": "...", "code_search": "...", "code_replace": "..."}
            
            User Request: $prompt
            Context: $context
            """.trimIndent()
        }

        if (settings.provider == "OpenAI") {
            return sendOpenAIRequest(systemPrompt, history, settings, !isPlanningMode)
        }
        return "Error: Only OpenAI supported for v2 Architecture currently."
    }

    private fun sendOpenAIRequest(systemPrompt: String, history: List<Map<String, String>>, settings: com.ronin.settings.RoninSettingsState, enforceJson: Boolean): String {
        val apiKey = com.ronin.settings.CredentialHelper.getApiKey("openaiApiKey")
            ?: System.getenv("OPENAI_API_KEY")
        
        if (apiKey.isNullOrBlank()) return "Error: OpenAI API Key not found."

        val model = settings.model.ifBlank { "gpt-4o" }
        val jsonBody = createOpenAIRequestBody(model, systemPrompt, history, enforceJson)

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

        try {
            val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 200) {
                return extractContentFromResponse(response.body())
            } else {
                return "Error: ${response.statusCode()} - ${response.body()}"
            }
        } catch (e: Exception) {
            return "Error sending request: ${e.message}"
        }
    }

    internal fun createOpenAIRequestBody(model: String, systemPrompt: String, history: List<Map<String, String>>, enforceJson: Boolean): String {
        val messages = mutableListOf<Map<String, String>>()
        messages.add(mapOf("role" to "system", "content" to systemPrompt))
        
        // Add only relevant history (maybe filtered? for now all)
        messages.addAll(history.map { 
             mapOf("role" to (it["role"] ?: "user"), "content" to (it["content"] ?: ""))
        })
        
        val isReasoningModel = model.startsWith("o1") || model.startsWith("gpt-5") || model.contains("reasoning")
        val isResponsesApi = model.contains("codex") || model.contains("gpt-5.1")
        
        val requestBody = mutableMapOf<String, Any>(
            "model" to model
        )
        
        if (isResponsesApi) {
            requestBody["input"] = messages
        } else {
            requestBody["messages"] = messages
        }
        
        if (enforceJson) {
            val commonSchemaProps = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "type" to mapOf("type" to "string", "enum" to listOf("question", "explanation", "command", "read_code", "write_code", "task_complete")),
                    "content" to mapOf("type" to "string", "description" to "Primary text content or reasoning."),
                    "command" to mapOf("type" to "string", "description" to "Command to run (if type=command)."),
                    "path" to mapOf("type" to "string", "description" to "File path (if type=read_code|write_code)."),
                    "code_search" to mapOf("type" to "string", "description" to "Exact code to search for (if type=write_code)."),
                    "code_replace" to mapOf("type" to "string", "description" to "New code (if type=write_code).")
                ),
                "required" to listOf("type", "content", "command", "path", "code_search", "code_replace"),
                "additionalProperties" to false
            )

            if (isResponsesApi) {
                 // Flattened structure for Responses API
                 // format: { type: "json_schema", name: "...", strict: true, schema: ... }
                 val flattenedSchema = mapOf(
                    "type" to "json_schema",
                    "name" to "RoninStep",
                    "strict" to true,
                    "schema" to commonSchemaProps
                 )
                 
                 requestBody["text"] = mapOf("format" to flattenedSchema)
                 requestBody["temperature"] = 1.0 
            } else {
                 // Standard Chat Completions API
                 val jsonSchemaWrapper = mapOf(
                    "type" to "json_schema",
                    "json_schema" to mapOf(
                        "name" to "RoninStep",
                        "strict" to true,
                        "schema" to commonSchemaProps
                    )
                 )
                 requestBody["response_format"] = jsonSchemaWrapper
                 requestBody["temperature"] = if (isReasoningModel) 1.0 else 0.1
            }
        } else {
             // Planning Phase
             requestBody["temperature"] = if (isReasoningModel) 1.0 else 0.7
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
