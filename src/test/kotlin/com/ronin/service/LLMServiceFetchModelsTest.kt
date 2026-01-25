package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.google.gson.Gson


class LLMServiceFetchModelsTest : BasePlatformTestCase() {

    fun testFetchModelsFiltering() {
        val service = LLMServiceImpl()
        
        // Mock response data
        val mockResponse = mapOf(
            "object" to "list",
            "data" to listOf(
                mapOf("id" to "gpt-4o", "object" to "model", "created" to 1234567890, "owned_by" to "openai"),
                mapOf("id" to "gpt-3.5-turbo-instruct", "object" to "model", "created" to 1234567890, "owned_by" to "openai"), // Should be excluded (heuristic)
                mapOf("id" to "dall-e-3", "object" to "model", "created" to 1234567890, "owned_by" to "openai"), // Should be excluded
                mapOf(
                    "id" to "custom-model-chat", 
                    "object" to "model", 
                    "created" to 1234567890, 
                    "owned_by" to "openai",
                    "features" to listOf("chat-completion") // Explicit capability
                ),
                mapOf(
                    "id" to "gpt-5.2-pro", 
                    "object" to "model", 
                    "created" to 1234567890, 
                    "owned_by" to "openai"
                    // No capabilities, fits ID heuristic "gpt-..." -> Included by default unless specific exclude
                ),
                mapOf(
                    "id" to "legacy-completion-only",
                    "object" to "model",
                    "features" to listOf("completion") // Explicit capture
                )
            )
        )
        val json = Gson().toJson(mockResponse)

        // Reflection or refactoring would be needed to test the private client call.
        // However, since we extracted the JSON parsing logic (conceptually), we can verify the LOGIC.
        // I will extract the parsing logic to a public/internal method to test it directly without network.
        
        val models = service.parseModelsJson(json)
        
        assertTrue("Contains gpt-4o", models.contains("gpt-4o"))
        assertFalse("Excludes instruct", models.contains("gpt-3.5-turbo-instruct"))
        assertFalse("Excludes dall-e", models.contains("dall-e-3"))
        assertTrue("Includes explicit chat feature", models.contains("custom-model-chat"))
        assertFalse("Excludes explicit completion feature", models.contains("legacy-completion-only"))
        
        // gpt-5.2-pro should be excluded as known completion model
        assertFalse("Excludes gpt-5.2-pro", models.contains("gpt-5.2-pro"))
    }
}
