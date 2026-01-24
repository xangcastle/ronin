package com.ronin.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RoninSettingsStateTest : BasePlatformTestCase() {
    
    fun testMigration() {
        val state = RoninSettingsState()
        val loadedState = RoninSettingsState()
        
        // Simulate loading from XML with legacy fields
        loadedState.openaiApiKey = "test-secret-key"
        
        // This should trigger migration
        state.loadState(loadedState)
        
        // Check if state is clear (fields remain null)
        assertNull(state.openaiApiKey)
        
        // Verify key was moved to CredentialHelper
        // Note: usage of PasswordSafe in test environment might rely on in-memory impl or fail
        // If it throws, we know we need to adjust test or environment.
        val storedKey = CredentialHelper.getApiKey("openaiApiKey")
        assertEquals("test-secret-key", storedKey)
    }
}
