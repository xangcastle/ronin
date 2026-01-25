package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiManager
import java.io.File

@Service(Service.Level.PROJECT)
class ValidationService(private val project: Project) {

    data class ValidationResult(val isValid: Boolean, val error: String?)

    fun validateFile(path: String): ValidationResult {
        val cleanPath = path.replace("\\", "/").removePrefix("./")
        val file = if (File(cleanPath).isAbsolute) {
            File(cleanPath)
        } else {
             // Resolve relative to project base path
             val basePath = project.basePath ?: return ValidationResult(false, "Project base path not found")
             File("$basePath/$cleanPath")
        }

        if (!file.exists()) return ValidationResult(false, "File not found: ${file.path}")

        val virtualFile = LocalFileSystem.getInstance().findFileByIoFile(file) 
            ?: return ValidationResult(false, "Could not find VirtualFile: $path")

        // Ensure PSI is in sync with Document (needs to be done on EDT/Write safe context, NOT inside ReadAction)
        ApplicationManager.getApplication().invokeAndWait {
             com.intellij.psi.PsiDocumentManager.getInstance(project).commitAllDocuments()
        }

        return ReadAction.compute<ValidationResult, Throwable> {
            
            val psiFile = PsiManager.getInstance(project).findFile(virtualFile)
            if (psiFile == null) {
                // If PSI file is null, it might be a binary or unsupported file type, essentially "valid" or at least "not syntax-checkable"
                return@compute ValidationResult(true, null)
            }

            var firstError: String? = null
            
            psiFile.accept(object : PsiRecursiveElementVisitor() {
                override fun visitErrorElement(element: PsiErrorElement) {
                    if (firstError == null) {
                        val line = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(virtualFile)?.getLineNumber(element.textOffset)?.plus(1) ?: "?"
                        firstError = "Line $line: ${element.errorDescription}"
                    }
                }
            })

            if (firstError != null) {
                ValidationResult(false, firstError)
            } else {
                ValidationResult(true, null)
            }
        }
    }
}
