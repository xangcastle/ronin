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
        sessionService.updatePlan("Step 1: Save the world.")
        
        // Assert State is "Dirty"
        assertEquals(2, historyService.getHistory().size)
        assertTrue(sessionService.hasPlan())
        assertNotNull(sessionService.currentPlan)
        
        // 2. Execute Reset Logic (mimicking ChatToolWindowFactory.clearChat)
        historyService.clearHistory()
        sessionService.clearPlan()
        
        // 3. Assert State is "Clean"
        assertEquals("History should be empty", 0, historyService.getHistory().size)
        assertFalse("Plan should be cleared", sessionService.hasPlan())
        assertNull("Current plan should be null", sessionService.currentPlan)
    }
}
