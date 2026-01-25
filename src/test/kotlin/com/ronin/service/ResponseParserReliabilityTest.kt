package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class ResponseParserReliabilityTest : BasePlatformTestCase() {

    fun testParseWithScratchpad() {
        val jsonResponse = """
            {
              "scratchpad": "I need to check the pods for nexus.",
              "type": "command",
              "content": "Running kubectl get po",
              "command": "kubectl get po -l app=nexus",
              "path": "",
              "code_search": "",
              "code_replace": ""
            }
        """.trimIndent()
        
        val result = ResponseParser.parseAndApply(jsonResponse, project)
        
        assertEquals("Running kubectl get po", result.text)
        assertEquals("kubectl get po -l app=nexus", result.commandToRun)
        assertFalse("Commands should not require manual follow-up flag as they use executeCommandChain", result.requiresFollowUp)
    }

    fun testReadCodeRequiresFollowUp() {
        val jsonResponse = """
            {
              "scratchpad": "Reading a file.",
              "type": "read_code",
              "content": "Reading...",
              "command": "",
              "path": "test.txt",
              "code_search": "",
              "code_replace": ""
            }
        """.trimIndent()
        
        val result = ResponseParser.parseAndApply(jsonResponse, project)
        assertTrue("read_code should require follow-up", result.requiresFollowUp)
    }

    fun testReadCodeWithToolOutput() {
        val jsonResponse = """
            {
              "scratchpad": "Reading some code.",
              "type": "read_code",
              "content": "I will read the file now.",
              "command": "",
              "path": "test.txt",
              "code_search": "",
              "code_replace": ""
            }
        """.trimIndent()
        
        val result = ResponseParser.parseAndApply(jsonResponse, project)
        assertEquals("I will read the file now.", result.text)
        assertNotNull(result.toolOutput)
        assertTrue(result.toolOutput!!.contains("**Reading File:**"))
        assertTrue(result.requiresFollowUp)
    }

    fun testParseWithMarkdownJsonAndScratchpad() {
        val response = "Here is the next step:\n" +
                "```json\n" +
                "{\n" +
                "  \"scratchpad\": \"Switching to the new branch.\",\n" +
                "  \"type\": \"command\",\n" +
                "  \"content\": \"Creating branch...\",\n" +
                "  \"command\": \"git checkout -b new-feat\",\n" +
                "  \"path\": \"\",\n" +
                "  \"code_search\": \"\",\n" +
                "  \"code_replace\": \"\"\n" +
                "}\n" +
                "```"
        
        val result = ResponseParser.parseAndApply(response, project)
        
        assertEquals("Creating branch...", result.text)
        assertEquals("git checkout -b new-feat", result.commandToRun)
    }
}
