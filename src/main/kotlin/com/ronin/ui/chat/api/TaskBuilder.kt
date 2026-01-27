package com.ronin.ui.chat.api

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

/**
 * Builds LLM tasks with appropriate context without the UI needing to know the details
 * 
 * THREADING: This class is designed to be called from background threads.
 * The buildContext() method uses ReadAction.compute() to safely access project data.
 */
class TaskBuilder(private val project: Project) {
    
    /**
     * Builds a task for a user message with full project context
     */
    fun buildUserMessageTask(
        message: String,
        history: List<Map<String, String>>
    ): LLMTask {
        val context = buildContext()
        return LLMTask(
            message = message,
            context = context,
            history = history
        )
    }
    
    /**
     * Builds a task for a follow-up message (e.g., after command execution)
     */
    fun buildFollowUpTask(
        prompt: String,
        history: List<Map<String, String>>
    ): LLMTask {
        val context = buildContext()
        return LLMTask(
            message = prompt,
            context = context,
            history = history
        )
    }
    
    /**
     * Builds the context string including active file and project structure
     * 
     * THREADING: Uses ReadAction.compute() to safely access file content and project structure
     * from a background thread. This is called from executeOnPooledThread in TaskExecutor.
     */
    private fun buildContext(): String {
        return ReadAction.compute<String, Throwable> {
            val contextBuilder = StringBuilder()

            val activeFileContent = getActiveFileContent()
            if (activeFileContent != null) {
                contextBuilder.append("Active File Content:\n```\n$activeFileContent\n```\n\n")
            }

            val projectStructure = getProjectStructure()
            contextBuilder.append(projectStructure)
            
            contextBuilder.toString()
        }
    }
    
    /**
     * Gets the content of the currently active file in the editor
     * MUST be called inside ReadAction
     */
    private fun getActiveFileContent(): String? {
        val fileEditorManager = FileEditorManager.getInstance(project)
        val selectedFiles = fileEditorManager.selectedFiles
        
        if (selectedFiles.isEmpty()) {
            return null
        }
        
        val activeFile = selectedFiles[0]
        val document = FileDocumentManager.getInstance().getDocument(activeFile) ?: return null
        
        val fileName = activeFile.name
        val filePath = activeFile.path
        
        return """
            File: $fileName
            Path: $filePath
            
            ${document.text}
        """.trimIndent()
    }
    
    /**
     * Gets the project structure as a string representation
     * MUST be called inside ReadAction
     */
    private fun getProjectStructure(): String {
        val builder = StringBuilder()
        builder.append("Project Structure:\n\n")
        
        val basePath = project.basePath
        if (basePath != null) {
            builder.append("Base Path: $basePath\n\n")
        }
        
        val projectRootManager = ProjectRootManager.getInstance(project)
        val contentRoots = projectRootManager.contentRoots
        
        if (contentRoots.isNotEmpty()) {
            builder.append("Content Roots:\n")
            contentRoots.forEach { root ->
                builder.append("- ${root.path}\n")
                appendFileTree(root, builder, 1, maxDepth = 2)
            }
        }
        
        return builder.toString()
    }
    
    /**
     * Recursively appends file tree structure
     * MUST be called inside ReadAction
     */
    private fun appendFileTree(
        file: VirtualFile,
        builder: StringBuilder,
        depth: Int,
        maxDepth: Int
    ) {
        if (depth > maxDepth) return
        
        val indent = "  ".repeat(depth)
        val children = file.children ?: return
        
        for (child in children) {
            if (child.isDirectory && child.name in listOf(".git", ".idea", "node_modules", "build", ".gradle", "out")) {
                continue
            }
            
            if (child.isDirectory) {
                builder.append("$indent├── ${child.name}/\n")
                appendFileTree(child, builder, depth + 1, maxDepth)
            } else {
                builder.append("$indent├── ${child.name}\n")
            }
        }
    }
}
