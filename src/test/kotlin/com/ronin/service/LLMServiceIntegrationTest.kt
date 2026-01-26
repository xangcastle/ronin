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
        
        // 1. Get the Settings Instance
        // We rely on the real service instance for integration tests
        val settings = RoninSettingsState.instance
        
        // 2. Select a Stance to modify (e.g., "The Daimyo" which is OpenAI)
        val stanceName = "The Daimyo"
        val stance = settings.stances.find { it.name == stanceName }
            ?: throw IllegalStateException("Default stance '$stanceName' not found for testing.")
            
        // 3. Backup original state
        val originalModel = stance.model
        val originalActive = settings.activeStance
        
        try {
            // 4. Configure Stance for Test
            stance.model = modelName
            settings.activeStance = stanceName
            
            // Ensure Credential is set (Integration test assumes Env Var or Keychain is ready)
            // But since we are in Strict Mode, we MUST ensure the credentialID resolves.
            // If realApiKey is set (from setUp), we should inject it into PasswordSafe for the test?
            // Or just assume the user has "openai_main" set up? 
            // The test checks `if (realApiKey.isNullOrBlank()) return`, so we have an Env Var.
            // We should Set the API Key for the stance's credentialId to this Env Var value to ensure it passes Strict Mode.
            com.ronin.settings.CredentialHelper.setApiKey(stance.credentialId, realApiKey)
            
            // 5. Service creation & Execution
            val service = LLMServiceImpl(project)
            val response = service.sendMessage("Say 'Test'", history = emptyList())
            
            println("Response from $modelName: $response")
            
            assertFalse("Response should not be an error: $response", response.startsWith("Error:"))
            assertTrue("Response should contain text", response.isNotBlank())
            
        } finally {
            // Restore state
            stance.model = originalModel
            settings.activeStance = originalActive
        }
    }
}
