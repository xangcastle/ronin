package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import org.mockito.Mockito
import com.intellij.openapi.project.Project

class ResetLogicTest : BasePlatformTestCase() {

    fun testResetLogicClearsEverything() {
        // Setup Services
        val historyService = ChatStorageService()
        val sessionService = AgentSessionService(project)
        
        // 1. Simulate Active State
        historyService.addMessage("user", "Hello")
        historyService.addMessage("assistant", "Hi")
        sessionService.addMessage("user", "Hello")
        sessionService.addMessage("assistant", "Hi")
        
        // Assert State is "Dirty"
        assertEquals(2, historyService.getHistory().size)
        assertEquals(2, sessionService.getHistory().size)
        
        // 2. Execute Reset Logic (mimicking ChatToolWindowFactory.clearChat)
        historyService.clearHistory()
        sessionService.clearSession()
        
        // 3. Assert State is "Clean"
        assertEquals("History should be empty", 0, historyService.getHistory().size)
        assertEquals("Session history should be cleared", 0, sessionService.getHistory().size)
    }
}
