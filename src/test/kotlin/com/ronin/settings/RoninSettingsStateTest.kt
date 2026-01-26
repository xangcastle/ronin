package com.ronin.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RoninSettingsStateTest : BasePlatformTestCase() {
    
    fun testDefaultStancesInitialization() {
        val state = RoninSettingsState()
        
        // The init block should trigger when accessing stances if empty logic is in constructor or init
        // Actually logic is in init block checking stances.isEmpty()
        
        // Verify Defaults
        assertEquals(3, state.stances.size)
        
        val daimyo = state.stances.find { it.name == "The Daimyo" }
        assertNotNull(daimyo)
        assertEquals("gpt-4o", daimyo?.model)
        assertEquals("OpenAI", daimyo?.provider)
        
        val shinobi = state.stances.find { it.name == "The Shinobi" }
        assertNotNull(shinobi)
        assertEquals("gpt-4o-mini", shinobi?.model)
        
        assertEquals("The Daimyo", state.activeStance)
    }
}
