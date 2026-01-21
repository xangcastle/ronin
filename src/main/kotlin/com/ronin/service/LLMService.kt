package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

interface LLMService {
    fun sendMessage(prompt: String, images: List<String> = emptyList()): String
}

@Service(Service.Level.PROJECT)
class LLMServiceImpl(private val project: Project) : LLMService {
    override fun sendMessage(prompt: String, images: List<String>): String {
        // Placeholder for actual implementation connecting to API
        // This will eventually use the settings to decide which provider to use
        return "Echo: $prompt"
    }
}
