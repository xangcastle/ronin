package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.ronin.settings.RoninSettingsState
import org.junit.Test
import org.mockito.Mockito

class LLMServiceIntegrationTest : BasePlatformTestCase() {

    private val realApiKey = System.getenv("OPENAI_API_KEY")

    override fun setUp() {
        super.setUp()
        if (realApiKey.isNullOrBlank()) {
            System.err.println("WARNING: OPENAI_API_KEY not set. Skipping integration tests.")
            return
        }
        // Mock the credential helper or settings to return the real key
        // Since CredentialHelper is static, we might need to mock the static method or
        // relying on the fact that LLMService calls CredentialHelper.
        // Actually, LLMServiceImpl calls CredentialHelper.getApiKey("openaiApiKey").
        // We can't easily mock static methods with standard Mockito.
        // Plan B: specific test subclass or refactor LLMService to accept key provider.
        // For now, let's assume we can set it via some mechanism or we just use reflection/power-mock if available?
        // Simpler: We'll modify LLMService temporarily or rely on CredentialHelper to look at env var?
        // Unlikely CredentialHelper looks at env var.
        
        // Let's refactor LLMService to allow passing API Key explicitly for testing?
        // Or better, let's just make a "TestableLLMService" subclass if the method was open? It's not.
        
        // Hack: We will rely on RoninSettingsState holding the model, but the API KEY is the hard part.
        // Let's look at CredentialHelper. If it uses PasswordSafe, we can't easily mock it in headless test without full setup.
        
        // Alternative: We manually invoke the `sendOpenAIRequest` method via Reflection to bypass credential check?
        // `sendOpenAIRequest` calls CredentialHelper inside.
        
        // Best approach given constraints:
        // Update LLMService to check for System.getenv("OPENAI_API_KEY") as a fallback if CredentialHelper returns null!
        // This is good practice for development anyway.
    }

    @Test
    fun testRealApiCall_GPT4o() {
        if (realApiKey.isNullOrBlank()) return
        runTestForModel("gpt-4o")
    }

    @Test
    fun testRealApiCall_GPT51CodexMini() {
        if (realApiKey.isNullOrBlank()) return
        runTestForModel("gpt-5.1-codex-mini")
    }

    @Test
    fun testRealApiCall_O1Preview() {
        if (realApiKey.isNullOrBlank()) return
        runTestForModel("o1-preview")
    }

    private fun runTestForModel(modelName: String) {
        println("Testing Model: $modelName")
        
        // 1. Setup Settings Mock
        val mockSettings = Mockito.mock(RoninSettingsState::class.java)
        Mockito.`when`(mockSettings.provider).thenReturn("OpenAI")
        Mockito.`when`(mockSettings.model).thenReturn(modelName)
        
        // 2. We need to inject these settings into LLMService. 
        // LLMService calls RoninSettingsState.instance. 
        // We can try to set the static instance if it's mutable?
        // RoninSettingsState seems to be a service.
        // ApplicationManager.getApplication().replaceService?
        
        // Let's try to bypass the service structure and use reflection to invoke `sendOpenAIRequest`
        // but passing our OWN settings object/key logic if possible?
        // No, `sendOpenAIRequest` is private.
        
        // Real logic: We will assume we added the Env Var Fallback to LLMService.kt
        
        // Service creation
        val service = LLMServiceImpl(project)
        
        // We need to Force the Model.
        // Since we can't mock the static RoninSettingsState.instance easily without registering a service in the test container:
        // plugin.xml declares it as applicationService.
        // We can replace it.
        
        /*
        val application = com.intellij.openapi.application.ApplicationManager.getApplication()
        application.replaceService(RoninSettingsState::class.java, mockSettings, testRootDisposable)
        */
        // But RoninSettingsState.instance is a static property that calls getService.
        // Replacing the service should work.
        
        // However, checking if RoninSettingsState is an interface or class. If class, check if open.
        // If final class, Mockito won't mock it unless inline-mock-maker is on.
        
        // Simpler Path:
        // Use a custom version of `LLMServiceImpl` that overrides `sendMessage` to use a local settings object?
        
        // Let's rely on the user having `OPENAI_API_KEY` set and us Modifying LLMService.kt to use it.
        // And we will set the global settings object real fields if possible.
        val settings = RoninSettingsState.instance
        val originalModel = settings.model
        try {
            settings.model = modelName
            
            // Execute
            val response = service.sendMessage("Say 'Test'", history = emptyList())
            println("Response from $modelName: $response")
            
            assertFalse("Response should not be an error", response.startsWith("Error:"))
            assertTrue("Response should contain text", response.isNotBlank())
            
        } finally {
            settings.model = originalModel
        }
    }
}
