package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AgentSessionService(private val project: Project) {
    
    // The history of the current conversation/session.
    private val history = mutableListOf<Map<String, String>>()
    
    fun getHistory(): List<Map<String, String>> = history
    
    fun addMessage(role: String, content: String) {
        history.add(mapOf("role" to role, "content" to content))
    }
    
    fun clearSession() {
        history.clear()
    }
}
