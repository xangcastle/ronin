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

    fun replaceFileContent(virtualFile: VirtualFile, newContent: String): Boolean {
        var success = false
        WriteCommandAction.runWriteCommandAction(project) {
            try {
                val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                if (document != null) {
                    document.setText(newContent)
                    success = true
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return success
    }

    fun findFile(path: String): VirtualFile? {
        val file = File(path)
        return if (file.isAbsolute) {
            LocalFileSystem.getInstance().findFileByIoFile(file)
        } else {
            project.basePath?.let { basePath ->
                LocalFileSystem.getInstance().findFileByPath("$basePath/$path")
            }
        }
    }
}
