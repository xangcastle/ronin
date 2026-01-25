package com.ronin.service

import com.intellij.openapi.components.service
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test
import java.io.File

class PathResolutionTest : BasePlatformTestCase() {

    fun testRelativePathResolution() {
        val editService = project.service<EditService>()
        
        // Create a dummy file in the project
        val dummyPath = "test-dir/dummy.txt"
        val vFile = editService.createFile(dummyPath)
        assertNotNull("File should be created", vFile)
        
        // Test resolution with various prefixes
        assertNotNull("Should resolve standard relative path", editService.findFile(dummyPath))
        assertNotNull("Should resolve path with ./", editService.findFile("./$dummyPath"))
        assertNotNull("Should resolve path with /", editService.findFile("/$dummyPath"))
        assertNotNull("Should resolve path with backslashes", editService.findFile("test-dir\\dummy.txt"))
    }

    fun testAbsolutePathResolution() {
        val editService = project.service<EditService>()
        val tempFile = File.createTempFile("ronin-test", ".txt")
        try {
            assertNotNull("Should resolve absolute path", editService.findFile(tempFile.absolutePath))
        } finally {
            tempFile.delete()
        }
    }
}
