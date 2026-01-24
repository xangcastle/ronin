package com.ronin.service

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.fileEditor.FileDocumentManager
import java.io.File

@Service(Service.Level.PROJECT)
class EditService(private val project: Project) {

    fun replaceFileContent(path: String, newContent: String): String {
        var result = "Unknown error"
        
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                var virtualFile = findFile(path)
                
                if (virtualFile == null) {
                    // Try to create the file
                    val success = createFile(path)
                    if (success != null) {
                        virtualFile = success
                        result = "Created new file: $path"
                    } else {
                        result = "Error: File not found and could not be created: $path"
                        return@runWriteCommandAction
                    }
                }
                
                if (virtualFile != null) {
                    val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                    if (document != null) {
                        document.setText(newContent)
                        FileDocumentManager.getInstance().saveDocument(document)
                        if (result == "Unknown error") result = "Updated file: $path"
                    } else {
                        // Binary file or not text?
                        // Try VFS direct write
                        virtualFile.setBinaryContent(newContent.toByteArray())
                        if (result == "Unknown error") result = "Updated file (binary/VFS): $path"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                result = "Error updating file: ${e.message}"
            }
        }
        return result
    }

    fun findFile(path: String): VirtualFile? {
        // Handle standard paths
        val cleanPath = path.replace("\\", "/").removePrefix("./")
        
        // Try absolute first
        val file = File(cleanPath)
        if (file.isAbsolute && file.exists()) {
             return LocalFileSystem.getInstance().findFileByIoFile(file)
        }
        
        // Try relative to project base
        project.basePath?.let { basePath ->
            val relativeFile = LocalFileSystem.getInstance().findFileByPath("$basePath/$cleanPath")
            if (relativeFile != null) return relativeFile
        }
        
        return null
    }
    
    private fun createFile(path: String): VirtualFile? {
        val cleanPath = path.replace("\\", "/").removePrefix("./")
        val basePath = project.basePath ?: return null
        val fullPath = if (File(cleanPath).isAbsolute) cleanPath else "$basePath/$cleanPath"
        
        try {
            val file = File(fullPath)
            if (!file.parentFile.exists()) {
                file.parentFile.mkdirs()
            }
            if (!file.exists()) {
                file.createNewFile()
            }
            return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }
}
