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
        sb.append("Project Structure:\n")
        
        ProjectFileIndex.getInstance(project).iterateContent { file ->
            if (file.isDirectory) {
                // Determine depth for indentation based on path
                // This is a simplified tree view
                val path = file.path.replace(project.basePath ?: "", "")
                if (path.isNotEmpty()) {
                    val depth = path.count { it == '/' }
                    sb.append("  ".repeat(depth)).append(file.name).append("/\n")
                }
            } else {
                 val path = file.path.replace(project.basePath ?: "", "")
                 val depth = path.count { it == '/' }
                 // Limit file listing to avoid huge contexts, or maybe just list important extensions?
                 // For now, list everything but skip hidden or build dirs if not handled by iterateContent
                 if (!file.name.startsWith(".")) {
                     sb.append("  ".repeat(depth)).append(file.name).append("\n")
                 }
            }
            true
        }
        return sb.toString()
    }
}
