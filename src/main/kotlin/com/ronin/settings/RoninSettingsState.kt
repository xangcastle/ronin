package com.ronin.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@State(
    name = "RoninSettingsState",
    storages = [Storage("ronin_settings.xml")]
)
class RoninSettingsState : PersistentStateComponent<RoninSettingsState> {
    
    data class Profile(
        val settingsEditable: Boolean = true,
        val stances: List<Stance> = emptyList()
    )

    data class Stance(
        var id: String = java.util.UUID.randomUUID().toString(),
        var name: String = "",
        var description: String = "",
        var systemPrompt: String = "",
        var provider: String = "OpenAI",
        var model: String = "gpt-4o-mini",
        var credentialId: String = "",
        var encryptedKey: String? = null
    )

    var stances: MutableList<Stance> = mutableListOf()
    var activeStance: String = "Hi (Django REST)"
    var settingsEditable: Boolean = true
    
    var ollamaBaseUrl: String = "http://localhost:11434"

    init {
        loadStancesFromResources()
    }

    private fun loadStancesFromResources() {
        if (stances.isNotEmpty()) return

        try {
            val url = RoninSettingsState::class.java.getResource("/stances") ?: return
            
            if (url.protocol == "file") {
                val dir = java.io.File(url.toURI())
                val files = dir.listFiles { _, name -> name.endsWith(".md") } ?: return
                
                for (file in files) {
                    val content = file.readText()
                    val stance = parseStanceMarkdown(file.nameWithoutExtension, content)
                    if (stance != null) {
                        stances.add(stance)
                    }
                }
            } else if (url.protocol == "jar") {
                val connection = url.openConnection() as java.net.JarURLConnection
                val jarFile = connection.jarFile
                val entries = jarFile.entries()
                
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val name = entry.name
                    // Check if it is within our target directory and is a markdown file
                    if (name.startsWith("stances/") && name.endsWith(".md") && !entry.isDirectory) {
                         val filename = name.substringAfterLast('/')
                         val id = filename.substringBeforeLast('.')
                         
                         val inputStream = jarFile.getInputStream(entry)
                         val content = inputStream.bufferedReader().use { it.readText() }
                         val stance = parseStanceMarkdown(id, content)
                         if (stance != null) {
                             stances.add(stance)
                         }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun parseStanceMarkdown(filenameId: String, content: String): Stance? {
        try {
            val parts = content.split("---", limit = 3)
            if (parts.size < 3) return null // Invalid format
            
            val frontmatter = parts[1]
            val systemPrompt = parts[2].trim()
            
            val stance = Stance(id = filenameId)
            stance.systemPrompt = systemPrompt
            
            // Parse Frontmatter (Simple Key-Value)
            frontmatter.lines().forEach { line ->
                val trimmed = line.trim()
                if (trimmed.isNotBlank() && trimmed.contains(":")) {
                    val split = trimmed.split(":", limit = 2)
                    val key = split[0].trim()
                    val value = split[1].trim()
                    
                    when (key) {
                        "name" -> stance.name = value
                        "description" -> stance.description = value
                        "provider" -> stance.provider = value
                        "model" -> stance.model = value
                        "credentialId" -> stance.credentialId = value
                        "encryptedKey" -> stance.encryptedKey = value
                    }
                }
            }
            return stance
        } catch (e: Exception) {
            println("Ronin: Failed to parse stance file $filenameId: ${e.message}")
            return null
        }
    }
    


    override fun getState(): RoninSettingsState {
        return this
    }

    override fun loadState(state: RoninSettingsState) {
        this.ollamaBaseUrl = state.ollamaBaseUrl
        // allowedTools moved to Stance
        this.activeStance = state.activeStance
        this.settingsEditable = state.settingsEditable
        
        if (state.stances.isNotEmpty()) {
            this.stances.clear()
            this.stances.addAll(state.stances)
        }
    }

    companion object {
        val instance: RoninSettingsState
            get() = service()
    }
}
