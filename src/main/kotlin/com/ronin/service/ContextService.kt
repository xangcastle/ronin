package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile

@Service(Service.Level.PROJECT)
class ContextService(private val project: Project) {

    fun getActiveFileContent(): String? {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return null
        return editor.document.text
    }

    fun getProjectStructure(): String {
        val sb = StringBuilder()
        sb.append("Project Structure (Truncated):\n")
        
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
}
