package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class TerminalService(private val project: Project) {

    fun runCommand(command: String, onOutput: (String) -> Unit = {}): String {
        val basePath = project.basePath ?: return "Error: No project base path found."
        
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        
        val userShell = System.getenv("SHELL")
        val shellCmd = if (isWindows) {
             listOf("cmd.exe", "/c") 
        } else {
             val shell = when {
                 !userShell.isNullOrBlank() -> userShell
                 File("/bin/zsh").exists() -> "/bin/zsh"
                 File("/bin/bash").exists() -> "/bin/bash"
                 else -> "/bin/sh"
             }
             listOf(shell, "-i", "-l", "-c")
        }
        
        var process: Process? = null
        try {
            onOutput("Ronin Shell: Using ${shellCmd.first()} (Interactive/Login) to execute: $command\n")
            
            val processBuilder = ProcessBuilder(shellCmd + command)
            processBuilder.directory(File(basePath))
            processBuilder.redirectErrorStream(true) // Merge stdout and stderr
            processBuilder.environment().putAll(System.getenv())
            
            process = processBuilder.start()
            val currentProcess = process
            
            val output = StringBuilder()
            val reader = currentProcess.inputStream.bufferedReader()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val text = line + "\n"
                onOutput(text)
                output.append(text)
                if (Thread.interrupted()) throw InterruptedException()
            }
            
            val completed = currentProcess.waitFor(60, TimeUnit.MINUTES)
            if (!completed) {
                currentProcess.destroyForcibly()
                return output.toString() + "\nError: Command timed out after 60 minutes."
            }
            
            return output.toString()
            
        } catch (e: InterruptedException) {
            process?.destroyForcibly()
            return "Command cancelled by user."
        } catch (e: Exception) {
            process?.destroyForcibly()
            return "Error executing command: ${e.message}"
        }
    }
}
