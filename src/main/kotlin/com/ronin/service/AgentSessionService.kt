package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AgentSessionService(private val project: Project) {
    
    // Delegate to persistent storage
    private val storage: ChatStorageService
        get() = project.service<ChatStorageService>()
    
    fun getHistory(): List<Map<String, String>> = storage.getHistory()
    
    fun addMessage(role: String, content: String) {
        storage.addMessage(role, content)
    }
    
    fun clearSession() {
        storage.clearHistory()
    }
}
