package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
class AgentSessionService(private val project: Project) {
    
    // The current active plan text. If null, we are in "Planning Mode".
    // If not null, we are in "Execution Mode".
    var currentPlan: String? = null
    
    fun hasPlan(): Boolean {
        return !currentPlan.isNullOrBlank()
    }
    
    fun updatePlan(newPlan: String) {
        currentPlan = newPlan
    }
    
    fun clearPlan() {
        currentPlan = null
    }
}
