package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.fasterxml.jackson.databind.DeserializationFeature

@Service(Service.Level.PROJECT)
class ContextService(private val project: Project) {

    fun getActiveFileContent(): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        return editor.document.text
    }

    fun getProjectRules(): String? {
        val basePath = project.basePath ?: return null
        val roninRules = java.io.File("$basePath/.roninrules")
        if (roninRules.exists()) return roninRules.readText()
        
        return null
    }

    private fun parseRoninConfig(): com.ronin.model.RoninMonorepo? {
        val basePath = project.basePath ?: return null
        val configFile = java.io.File("$basePath/ronin.yaml")
        
        if (!configFile.exists()) return null
        
        return try {
            val mapper = com.fasterxml.jackson.databind.ObjectMapper(com.fasterxml.jackson.dataformat.yaml.YAMLFactory())
            mapper.registerModule(com.fasterxml.jackson.module.kotlin.KotlinModule.Builder().build())
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            mapper.readValue(configFile, com.ronin.model.RoninMonorepo::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getProjectStructure(): String {
        val repoConfig = parseRoninConfig()
        
        if (repoConfig != null) {
            // Smart Context Resolution
            val activeTarget = resolveActiveTarget(repoConfig)
            
            val sb = StringBuilder()
            if (activeTarget != null) {
                val (targetName, targetConfig) = activeTarget
                sb.append("[BAZEL MONOREPO CONTEXT]\n")
                sb.append("Active Target: $targetName\n")
                sb.append("Kind: ${targetConfig.kind}\n")
                sb.append("Language: ${targetConfig.lang}\n")
                if (targetConfig.base != null) sb.append("Base Image: ${targetConfig.base}\n")
                
                sb.append("Internal Dependencies (Modules):\n")
                targetConfig.internal_deps.forEach { sb.append(" - $it\n") }
                sb.append("External Dependencies:\n")
                targetConfig.deps.forEach { sb.append(" - $it\n") }
            } else {
                 sb.append("[BAZEL MONOREPO CONTEXT]\n")
                 sb.append("Note: Active file does not match any specific target in ronin.yaml.\n")
                 sb.append("Available Targets: ${repoConfig.targets.keys.joinToString(", ")}\n")
            }
            return sb.toString()
        }

        // Fallback to legacy scanning
        val sb = StringBuilder()
        sb.append("Project Structure (Legacy Scan):\n")
        
        val ignoredDirs = setOf("node_modules", "target", "build", "dist", ".git", ".idea", ".gradle", "bazel-bin", "bazel-out", "bazel-testlogs", "bazel-ronin")
        var count = 0
        val maxItems = 1000
        
        val basePath = project.basePath ?: return ""

        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (count >= maxItems) return@iterateContent false
            
            // Basic filtering
            if (file.name.startsWith(".")) return@iterateContent true
            
            // Check if any parent part is in ignoredDirs
            // This is a bit expensive for every file, so we rely on checks relative to base
            val relPath = file.path.removePrefix(basePath)
            if (relPath.split("/").any { it in ignoredDirs }) return@iterateContent true

            if (file.isDirectory) {
                if (file.name in ignoredDirs) return@iterateContent true
                
                val depth = relPath.count { it == '/' }
                if (depth > 5) return@iterateContent true // Limit depth
                
                sb.append("  ".repeat(depth)).append(file.name).append("/\n")
                count++
            } else {
                 val depth = relPath.count { it == '/' }
                 if (depth > 5) return@iterateContent true
                 
                 // Extension filter? Maybe too aggressive. Let's stick to count limit + ignores.
                 sb.append("  ".repeat(depth)).append(file.name).append("\n")
                 count++
            }
            true
        }
        
        if (count >= maxItems) {
            sb.append("\n... (And more files. Context limited to $maxItems items.)")
        }
        
        return sb.toString()
    }

    private fun resolveActiveTarget(repo: com.ronin.model.RoninMonorepo): Pair<String, com.ronin.model.RoninTarget>? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        val file = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getFile(editor.document) ?: return null
        val basePath = project.basePath ?: return null
        
        val relPath = file.path.removePrefix(basePath).removePrefix("/")
        
        // Find the target key (e.g. "//apps/payment") that matches the start of relPath
        // Key "//apps/payment" -> check if relPath starts with "apps/payment"
        
        // Sort by length descending to match most specific path first (e.g. //apps/payment/v2 vs //apps/payment)
        val sortedKeys = repo.targets.keys.sortedByDescending { it.length }
        
        for (key in sortedKeys) {
            val cleanKey = key.replace("//", "").trim()
            if (relPath.startsWith(cleanKey)) {
                return key to (repo.targets[key] ?: continue)
            }
        }
        return null
    }
}
