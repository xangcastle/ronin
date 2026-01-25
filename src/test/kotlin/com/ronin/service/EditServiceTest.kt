package com.ronin.service

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service

class EditServiceTest : BasePlatformTestCase() {

    fun testCreateNewFile() {
        val service = project.service<EditService>()
        val tempDir = java.nio.file.Files.createTempDirectory("ronin_test").toFile()
        val path = "${tempDir.absolutePath}/newFile.txt"
        
        val ops = listOf(EditService.EditOperation(path, null, "Hello World"))
        val results = service.applyEdits(ops)
        
        // Cleanup
        tempDir.deleteOnExit()
        
        if (results.isEmpty()) {
            fail("Results were empty. file exists? ${java.io.File(path).exists()}")
        }
        
        assertTrue(results.size >= 1)
        assertTrue(results.any { it.contains("Created") })
        
        val file = service.findFile(path)
        assertNotNull(file)
        assertEquals("Hello World", String(file!!.contentsToByteArray()))
    }

    fun testSurgicalEdit() {
        // Setup existing file
        val service = project.service<EditService>()
        val tempDir = java.nio.file.Files.createTempDirectory("ronin_test_surgical").toFile()
        val path = "${tempDir.absolutePath}/code.kt"
        
        WriteCommandAction.runWriteCommandAction(project) {
            val file = service.createFile(path)
            file!!.setBinaryContent("""
                fun hello() {
                    println("Hello")
                }
                
                fun world() {
                    println("World")
                }
            """.trimIndent().toByteArray())
        }

        // Apply surgical edit to "Hello" -> "Hi"
        val ops = listOf(EditService.EditOperation(
            path, 
            "println(\"Hello\")", 
            "println(\"Hi\")"
        ))
        
        val results = service.applyEdits(ops)
        
        // Cleanup
        tempDir.deleteOnExit()
        
        assertTrue(results.isNotEmpty())
        assertTrue(results[0].contains("Updated"))
        
        val file = service.findFile(path)
        val content = String(file!!.contentsToByteArray())
        
        assertTrue(content.contains("println(\"Hi\")"))
        assertTrue(content.contains("fun world()")) // Ensure other content remains
    }
    
    fun testOverwriteIfSearchNull() {
         // Setup existing file
        val service = project.service<EditService>()
        val tempDir = java.nio.file.Files.createTempDirectory("ronin_test_overwrite").toFile()
        val path = "${tempDir.absolutePath}/overwrite.txt"
        
        WriteCommandAction.runWriteCommandAction(project) {
            val file = service.createFile(path)
            file!!.setBinaryContent("Old Content".toByteArray())
        }
        
        // Overwrite
        val ops = listOf(EditService.EditOperation(path, null, "New Content"))
        service.applyEdits(ops)
        
        val file = service.findFile(path)
        assertEquals("New Content", String(file!!.contentsToByteArray()))
    }
}
