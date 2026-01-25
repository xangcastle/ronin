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

    data class EditOperation(val path: String, val search: String?, val replace: String)

    fun applyEdits(operations: List<EditOperation>): List<String> {
        val results = mutableListOf<String>()
        
        WriteCommandAction.runWriteCommandAction(project) {
            for (op in operations) {
                try {
                    var virtualFile = findFile(op.path)
                    
                    if (virtualFile == null) {
                        // Create file if it doesn't exist
                        val created = createFile(op.path)
                        if (created != null) {
                            virtualFile = created
                            results.add("Created new file: ${op.path}")
                        } else {
                            results.add("Error: Could not create file: ${op.path}")
                            continue
                        }
                    }
                    
                    if (virtualFile != null) {
                        val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                        if (document != null) {
                            val text = document.text
                            
                            if (op.search.isNullOrEmpty()) {
                                // Full replacement or rewrite (if search is missing)
                                // Or purely appending? For safety, let's treat null search as "Overwrite/Set Content" 
                                // if the file is new, or if explicitly desired. 
                                // But usually surgical edits imply search. 
                                // If search is null, we assume we overwrite the whole file content.
                                document.setText(op.replace)
                                results.add("Overwrote file: ${op.path}")
                            } else {
                                // Surgical Search & Replace
                                // Normalize line endings for comparison?
                                val idx = text.indexOf(op.search)
                                if (idx != -1) {
                                    document.replaceString(idx, idx + op.search.length, op.replace)
                                    results.add("Updated file: ${op.path}")
                                } else {
                                    // Try fuzzy match or reporting error?
                                    // For now, robust error behavior.
                                    results.add("Error: Could not find search block in ${op.path}")
                                }
                            }
                            FileDocumentManager.getInstance().saveDocument(document)
                        } else {
                             // Binary or not loaded
                             virtualFile.setBinaryContent(op.replace.toByteArray())
                             results.add("Updated binary/VFS file: ${op.path}")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    results.add("Exception updating ${op.path}: ${e.message}")
                }
            }
        }
        return results
    }

    // Legacy method for full replace (keep for now or delegate)
    fun replaceFileContent(path: String, newContent: String): String {
        return applyEdits(listOf(EditOperation(path, null, newContent))).firstOrNull() ?: "Error"
    }

    fun findFile(path: String): VirtualFile? {
        // Handle standard paths
        val cleanPath = path.replace("\\", "/").removePrefix("./").removePrefix("/")
        
        // 1. Try relative to project base (highest priority for agent-provided paths)
        project.basePath?.let { basePath ->
            val relativeFile = LocalFileSystem.getInstance().findFileByPath("$basePath/$cleanPath")
            if (relativeFile != null) return relativeFile
        }

        // 2. Try absolute path
        val file = File(path) // Use original path for absolute check
        if (file.isAbsolute && file.exists()) {
             return LocalFileSystem.getInstance().findFileByIoFile(file)
        }
        
        return null
    }
    
    internal fun createFile(path: String): VirtualFile? {
        val cleanPath = path.replace("\\", "/").removePrefix("./")
        val isAbs = File(cleanPath).isAbsolute
        val basePath = project.basePath
        
        if (!isAbs && basePath == null) return null
        
        val fullPath = if (isAbs) cleanPath else "$basePath/$cleanPath"
        
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
