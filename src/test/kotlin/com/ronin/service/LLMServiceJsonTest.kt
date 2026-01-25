package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class LLMServiceJsonTest : BasePlatformTestCase() {

    fun testJsonEscapingParams() {
        val service = LLMServiceImpl()
        val prompt = """
            Path: C:\Windows\System32
            Tab: 	Indent
            Crlf: 
            Line
        """.trimIndent()
        
        val json = service.createOpenAIRequestBody("gpt-4o", prompt, emptyList())
        
        // The backslash in C:\Windows must be escaped to C:\\Windows in JSON
        // Current implementation likely fails this check
        assertTrue("Backslashes should be escaped", json.contains("""C:\\Windows\\System32"""))
        
        // Tabs should be escaped as \t
        assertTrue("Tabs should be escaped", json.contains("""\tIndent"""))
    }
}
