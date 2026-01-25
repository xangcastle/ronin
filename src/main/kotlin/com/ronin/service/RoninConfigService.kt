package com.ronin.service

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

@Service(Service.Level.PROJECT)
class RoninConfigService(private val project: Project) {

    fun getProjectContext(): String {
        val basePath = project.basePath ?: return "Standard Project"
        val roninYaml = File(basePath, "ronin.yaml")
        
        if (roninYaml.exists()) {
            return try {
                val content = roninYaml.readText()
                "Project Context (ronin.yaml):\n$content"
            } catch (e: Exception) {
                "Standard Project (Error reading ronin.yaml: ${e.message})"
            }
        }
        
        return "Standard Bazel Monorepo (ronin.yaml not found, assuming defaults)"
    }
}
