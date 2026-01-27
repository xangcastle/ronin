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

    // Removed createBackup as per user request (Step 326)

    data class EditOperation(
        val path: String, 
        val search: String?, 
        val replace: String, 
        val startLine: Int? = null, 
        val endLine: Int? = null,
        val isRegex: Boolean = false
    )

    fun applyEdits(operations: List<EditOperation>): List<String> {
        val results = mutableListOf<String>()
        val commandProcessor = com.intellij.openapi.command.CommandProcessor.getInstance()
        
        // Wrap entire batch in one Command for atomic Undo
        commandProcessor.executeCommand(project, {
            WriteCommandAction.runWriteCommandAction(project) {
                for (op in operations) {
                    try {
                        var virtualFile = findFile(op.path)
                        
                        if (virtualFile == null) {
                            val created = createFile(op.path)
                            if (created != null) {
                                virtualFile = created
                                results.add("Created new file: ${op.path}")
                            } else {
                                results.add("Error: Could not create file: ${op.path}")
                                continue
                            }
                        }
                        
                        // Ensure file is writable (as per Strategy Doc)
                        if (!com.intellij.openapi.vfs.ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(listOf(virtualFile!!)).hasReadonlyFiles()) {
                            val document = FileDocumentManager.getInstance().getDocument(virtualFile)
                            if (document != null) {
                                val text = document.text
                            
                                if (op.startLine != null && op.endLine != null) {
                                    // LINE-BASED REPLACEMENT (Refined)
                                    val startLineIdx = (op.startLine - 1).coerceAtLeast(0)
                                    val endLineIdx = (op.endLine - 1).coerceAtMost(document.lineCount - 1)
                                    
                                    if (startLineIdx <= endLineIdx) {
                                        val startOffset = document.getLineStartOffset(startLineIdx)
                                        val endLineContentOffset = document.getLineEndOffset(endLineIdx)
                                        val separatorLength = document.getLineSeparatorLength(endLineIdx)
                                        val endOffset = (endLineContentOffset + separatorLength).coerceAtMost(document.textLength)
                                        
                                        document.replaceString(startOffset, endOffset, op.replace + if (op.replace.endsWith("\n") || endOffset == document.textLength) "" else "\n") 
                                        
                                        // Context Echo
                                        val newStartLine = startLineIdx
                                        val newEndLine = (startLineIdx + op.replace.lines().size + 2).coerceAtMost(document.lineCount - 1)
                                        val contextStart = (newStartLine - 3).coerceAtLeast(0)
                                        val contextEnd = (newEndLine + 3).coerceAtMost(document.lineCount - 1)
                                        
                                        val contextText = document.charsSequence.subSequence(
                                            document.getLineStartOffset(contextStart), 
                                            document.getLineEndOffset(contextEnd)
                                        ).toString()
                                        
                                        results.add("Replaced lines ${op.startLine}-${op.endLine}. \n**Current Context (Lines ${contextStart+1}-${contextEnd+1}):**\n```\n$contextText\n```")
                                    } else {
                                        results.add("Error: Invalid line range ${op.startLine}-${op.endLine}")
                                    }
                                } else if (op.search.isNullOrEmpty()) {
                                    // OVERWRITE MODE
                                    document.setText(op.replace)
                                    results.add("Overwrote file: ${op.path}")
                                    
                                } else {
                                    // SURGICAL SEARCH & REPLACE (With Fuzzy Fallback)
                                    var idx = -1
                                    var matchLength = 0
                                    
                                    if (op.isRegex) {
                                        val match = try { op.search.toRegex().find(text) } catch(e: Exception) { null }
                                        if (match != null) {
                                            idx = match.range.first
                                            matchLength = match.range.count()
                                        }
                                    } else {
                                        // 1. Exact Match
                                        idx = text.indexOf(op.search)
                                        matchLength = op.search.length
                                        
                                        // 2. Fuzzy Match (Whitespace Insensitive)
                                        if (idx == -1) {
                                            // Heuristic: Try to find start by matching first meaningful line
                                            val searchLines = op.search.lines().map { it.trim() }.filter { it.isNotEmpty() }
                                            if (searchLines.isNotEmpty()) {
                                                val firstLine = searchLines[0]
                                                var searchOffset = 0
                                                while (searchOffset < text.length) {
                                                    val candidateIdx = text.indexOf(firstLine, searchOffset)
                                                    if (candidateIdx == -1) break
                                                    
                                                    // Found a candidate anchor. 
                                                    // In a real fuzzy system we would check subsequent lines.
                                                    // For now, if we match the first unique line, we assume it's the block.
                                                    // But searching just one line is risky.
                                                    // Let's rely on exact match or report failure with helpful message.
                                                    // Implementing true fuzzy patch application is complex.
                                                    // Strategy: Fail, but suggest Line-Based editing.
                                                    idx = -1 
                                                    break 
                                                }
                                            }
                                        }
                                    }

                                    if (idx != -1) {
                                        document.replaceString(idx, idx + matchLength, op.replace)
                                        results.add("Updated file: ${op.path} (Match found at index $idx)")
                                    } else {
                                        results.add("Error: Exact match not found. **RECOMMENDATION**: Use `write_code` with `startLine` and `endLine` for robust editing.")
                                    }
                                }
                                
                                // FORCE PSI COMMIT
                                com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(document)
                            }
                        } else {
                            results.add("Error: File is read-only: ${op.path}")
                        }
                    } catch (e: Exception) {
                        results.add("Exception applying edit to ${op.path}: ${e.message}")
                        e.printStackTrace()
                    }
                }
            }
        }, "Ronin Edit", "Ronin")
        
        return results
    }

    // Legacy method for full replace (keep for now or delegate)
    fun replaceFileContent(path: String, newContent: String): String {
        return applyEdits(listOf(EditOperation(path, null, newContent))).firstOrNull() ?: "Error"
    }

    fun findFile(path: String): VirtualFile? {
        val cleanPath = path.replace("\\", "/").removePrefix("./") // Keep original first char if not . or /
        
        var ioFile = File(cleanPath)
        
        // If relative, resolve against project base path
        if (!ioFile.isAbsolute) {
             val basePath = project.basePath
             if (basePath != null) {
                 ioFile = File(basePath, cleanPath)
             }
        }

        // Use refreshAndFindFileByIoFile to ensure we find it even if VFS is stale 
        // or if it's a hidden folder (like .k8s) that might be ignored by index.
        if (ioFile.exists()) {
            return LocalFileSystem.getInstance().refreshAndFindFileByIoFile(ioFile)
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
