package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

interface LLMService {
    fun sendMessage(prompt: String, images: List<String> = emptyList()): String
    fun getAvailableModels(provider: String): List<String>
}

@Service(Service.Level.PROJECT)
class LLMServiceImpl(private val project: Project) : LLMService {
    override fun sendMessage(prompt: String, images: List<String>): String {
        // Simulate network delay to test UI responsiveness
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        
        val settings = com.ronin.settings.RoninSettingsState.instance
        return "Rank: Mock Response\nProvider: ${settings.provider}\nModel: ${settings.model}\n\nEcho: $prompt"
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
