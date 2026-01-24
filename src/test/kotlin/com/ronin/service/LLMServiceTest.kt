package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LLMServiceTest : BasePlatformTestCase() {

    fun testGetAvailableModelsOpenAI() {
        val service = LLMServiceImpl()
        val models = service.getAvailableModels("OpenAI")
        assertContainsElements(models, "gpt-4o", "gpt-4-turbo")
    }

    fun testGetAvailableModelsAnthropic() {
        val service = LLMServiceImpl()
        val models = service.getAvailableModels("Anthropic")
        assertContainsElements(models, "claude-3-opus-20240229")
    }
    
    fun testGetAvailableModelsUnknown() {
        val service = LLMServiceImpl()
        val models = service.getAvailableModels("UnknownProvider")
        // Default to OpenAI models as per implementation
        assertContainsElements(models, "gpt-4o")
    }
}
