package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class TerminalService(private val project: Project) {

    fun runCommand(command: String, onOutput: (String) -> Unit = {}): String {
        val basePath = project.basePath ?: return "Error: No project base path found."
        
        // Detect shell
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val shell = if (isWindows) listOf("cmd.exe", "/c") else listOf("/bin/sh", "-c")
        
        try {
            val processBuilder = ProcessBuilder(shell + command)
            processBuilder.directory(File(basePath))
            processBuilder.redirectErrorStream(true) // Merge stdout and stderr
            
            val process = processBuilder.start()
            
            val output = StringBuilder()
            val reader = process.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val text = line + "\n"
                onOutput(text)
                output.append(text)
            }
            
            // Wait with timeout (increased to 60 minutes)
            val completed = process.waitFor(60, TimeUnit.MINUTES)
            if (!completed) {
                process.destroy()
                return output.toString() + "\nError: Command timed out after 60 minutes."
            }
            
            return output.toString()
            
        } catch (e: Exception) {
            return "Error executing command: ${e.message}"
        }
    }
}
