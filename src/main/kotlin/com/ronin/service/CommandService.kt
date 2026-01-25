package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import com.intellij.openapi.components.service

sealed class CommandResult {
    data class PromptInjection(val prompt: String) : CommandResult()
    data class Action(val message: String) : CommandResult()
    data class Error(val message: String) : CommandResult()
}

@Service(Service.Level.PROJECT)
class CommandService(private val project: Project) {

    fun executeCommand(commandLine: String): CommandResult {
        val parts = commandLine.trim().split("\\s+".toRegex())
        val name = parts[0].removePrefix("/")
        
        return when (name) {
            "init" -> handleInit()
            else -> handleCustomCommand(name)
        }
    }
    
    // Autocomplete helper
    fun getAvailableCommands(): List<String> {
        val commands = mutableListOf("/init")
        
        val basePath = project.basePath ?: return commands
        val customDir = File("$basePath/ronin/commands")
        if (customDir.exists() && customDir.isDirectory) {
            customDir.listFiles { _, fileName -> fileName.endsWith(".md") }?.forEach {
                commands.add("/" + it.nameWithoutExtension)
            }
        }
        return commands
    }

    private fun handleInit(): CommandResult {
        val terminalService = project.service<TerminalService>()
        val basePath = project.basePath ?: return CommandResult.Error("Project base path not found.")
        
        try {
            // 1. Find all OCI images
            val imagesRaw = terminalService.runCommand("bazel query \"kind(oci_image, //...)\" 2>/dev/null")
            val images = imagesRaw.lines().map { it.trim() }.filter { it.startsWith("//") }
            
            if (images.isEmpty()) {
                return CommandResult.Action("No `oci_image` targets found in the monorepo.")
            }
            
            val yamlBuilder = StringBuilder("targets:\n")
            
            for (image in images) {
                yamlBuilder.append("  $image:\n")
                yamlBuilder.append("    kind: oci_image\n")
                
                // 2. Base Image
                val buildOutput = terminalService.runCommand("bazel query --output=build \"$image\" 2>/dev/null")
                val baseLine = buildOutput.lines().find { it.contains("base =") }
                val base = baseLine?.split("\"")?.getOrNull(1) ?: "unknown"
                yamlBuilder.append("    base: \"$base\"\n")
                
                // 3. Infer Language from deps
                val depsRaw = terminalService.runCommand("bazel query \"deps($image, 1)\" 2>/dev/null")
                val lang = when {
                    depsRaw.contains("java_binary") -> "java"
                    depsRaw.contains("kt_jvm_binary") -> "kotlin"
                    depsRaw.contains("go_binary") -> "go"
                    depsRaw.contains("py_binary") -> "python"
                    depsRaw.contains("js_binary") || depsRaw.contains("ts_library") -> "typescript"
                    else -> "unknown"
                }
                yamlBuilder.append("    lang: $lang\n")
                
                // 4. Port (Heuristic)
                val portLine = buildOutput.lines().find { it.contains("port:") }
                val port = portLine?.substringAfter("port:")?.substringBefore("\"")?.trim() ?: "8080"
                yamlBuilder.append("    port: $port\n")
                
                // 5. Internal Deps
                yamlBuilder.append("    internal_deps:\n")
                val internalDepsRaw = terminalService.runCommand("bazel query \"kind('.*library', deps($image)) intersect //...\" 2>/dev/null")
                internalDepsRaw.lines()
                    .map { it.trim() }
                    .filter { it.startsWith("//") && it != image }
                    .take(20)
                    .forEach { yamlBuilder.append("      - \"$it\"\n") }
                
                // 6. External Deps
                yamlBuilder.append("    deps:\n")
                val externalDepsRaw = terminalService.runCommand("bazel query \"deps($image) except //...\" 2>/dev/null")
                externalDepsRaw.lines()
                    .map { it.trim() }
                    .filter { it.startsWith("@") && !it.contains("bazel_tools") && !it.contains("@local_config") }
                    .distinct()
                    .take(15)
                    .forEach { yamlBuilder.append("      - \"$it\"\n") }
                    
                yamlBuilder.append("\n")
            }
            
            // Write to file
            val roninFile = File("$basePath/ronin.yaml")
            roninFile.writeText(yamlBuilder.toString())
            
            return CommandResult.Action("✅ `ronin.yaml` generated successfully exploring ${images.size} images.")
        } catch (e: Exception) {
            return CommandResult.Error("Failed to generate ronin.yaml: ${e.message}")
        }
    }

    private fun handleCustomCommand(name: String): CommandResult {
        val basePath = project.basePath ?: return CommandResult.Error("Project base path not found.")
        val commandFile = File("$basePath/ronin/commands/$name.md")
        
        if (commandFile.exists()) {
             return try {
                 val content = commandFile.readText()
                 CommandResult.PromptInjection(content)
             } catch (e: Exception) {
                 CommandResult.Error("Failed to read command file: ${e.message}")
             }
        }
        return CommandResult.Error("Unknown command: /$name")
    }
}
