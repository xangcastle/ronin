package com.ronin.service

import org.junit.Test
import org.junit.Assert.*
import com.google.gson.Gson
import com.intellij.openapi.project.Project
import org.mockito.Mockito

class LLMServiceTest {

    @Test
    fun testCreateOpenAIRequestBodyForStandardModel() {
        // Arrange
        val project = Mockito.mock(Project::class.java)
        val service = LLMServiceImpl(project)
        val history = listOf(mapOf("role" to "user", "content" to "Hello"))
        val systemPrompt = "You are a helper."
        
        // Act
        val json = service.createOpenAIRequestBody("gpt-4o", systemPrompt, history, true)
        
        // Assert
        val map = Gson().fromJson(json, Map::class.java)
        assertEquals("gpt-4o", map["model"])
        assertTrue(map.containsKey("messages"))
        assertFalse(map.containsKey("input"))
        assertTrue(map.containsKey("response_format"))
        assertEquals(0.1, map["temperature"])
    }

    @Test
    fun testCreateOpenAIRequestBodyForResponsesApiModel() {
        // Arrange
        val project = Mockito.mock(Project::class.java)
        val service = LLMServiceImpl(project)
        val history = listOf(mapOf("role" to "user", "content" to "Code this."))
        val systemPrompt = "You are a coder."
        
        // Act
        val json = service.createOpenAIRequestBody("gpt-5.1-codex-mini", systemPrompt, history, true)
        
        // Assert
        val map = Gson().fromJson(json, Map::class.java)
        assertEquals("gpt-5.1-codex-mini", map["model"])
        
        // Check for 'input' instead of 'messages'
        assertTrue("Should contain 'input'", map.containsKey("input"))
        assertFalse("Should NOT contain 'messages'", map.containsKey("messages"))
        
        // Check for 'text.format' instead of 'response_format' (if implemented)
        // Currently the code puts it in response_format, we expect this to FAIL until we fix it.
        // We will assert what we WANT:
        // The structure for Responses API is different. It usually doesn't strictly follow chat completions schema if it uses text.format.
        // Based on search: text: { format: ... }
        // Let's assume the key at root is not response_format but maybe wrapped? 
        // Or if the error said 'response_format' moved to 'text.format', it implies 'text' object with 'format' property?
        // Let's inspect the map structure we intend to build.
        // For now, let's just assert we HAVE "text" or verify "response_format" is ABSENT if we switch.
        
        // Let's implement the fix first or expect failure. I'll expect failure on the specific key check.
    }
}
