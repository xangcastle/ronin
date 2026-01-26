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
    
    // Stance Definition
    data class Stance(
        var name: String = "",
        var systemPrompt: String = "",
        var provider: String = "OpenAI",
        var model: String = "gpt-4o",
        var scope: String = "General",
        var credentialId: String = ""
    )

    // State Fields
    var stances: MutableList<Stance> = mutableListOf()
    var activeStance: String = "The Daimyo"
    
    var ollamaBaseUrl: String = "http://localhost:11434"
    var allowedTools: String = "git, podman, kubectl, argocd, aws, bazel"
    var coreWorkflow: String = """
        1. **PLAN**: Analyze request.
        2. **EXECUTE**: Return the JSON with commands and edits.
        3. **VERIFY**: Check if the goal is achieved. Only run verification commands (test/build) if necessary to validate code changes. Do NOT verify simple info queries (e.g. pwd, ls).
    """.trimIndent()

    init {
        // Initialize Default Samurai Personas if empty
        if (stances.isEmpty()) {
            val corporateProfile = RoninSettingsState::class.java.getResource("/default_stances.json")
            if (corporateProfile != null) {
                try {
                    val content = corporateProfile.readText()
                    val type = object : com.google.gson.reflect.TypeToken<List<Stance>>() {}.type
                    val defaults: List<Stance> = com.google.gson.Gson().fromJson(content, type)
                    stances.addAll(defaults)
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Fallback to hardcoded defaults on error
                    addHardcodedDefaults()
                }
            } else {
                addHardcodedDefaults()
            }
        }
    }
    
    private fun addHardcodedDefaults() {
        stances.add(Stance(
            name = "The Daimyo",
            systemPrompt = "You are The Daimyo, a strategic software architect. You focus on high-level design, system patterns, and maintainability. You prefer correctness over speed.",
            provider = "OpenAI",
            model = "gpt-4o",
            scope = "Full Project",
            credentialId = "openai_main"
        ))
        stances.add(Stance(
            name = "The Shinobi",
            systemPrompt = "You are The Shinobi, a swift code editor. Your goal is to make surgical edits with extreme speed and precision. Do not lecture; just execute.",
            provider = "OpenAI",
            model = "gpt-4o-mini", // Fast model
            scope = "Current File",
            credentialId = "openai_main"
        ))
        stances.add(Stance(
            name = "The Ronin",
            systemPrompt = "You are The Ronin, a master of the Frontend. You specialize in React, CSS, and UI interactions. You ignore backend logic unless strictly necessary.",
            provider = "OpenAI",
            model = "gpt-4o",
            scope = "Frontend",
            credentialId = "openai_main"
        ))
    }

    override fun getState(): RoninSettingsState {
        return this
    }

    override fun loadState(state: RoninSettingsState) {
        this.ollamaBaseUrl = state.ollamaBaseUrl
        this.allowedTools = state.allowedTools
        this.coreWorkflow = state.coreWorkflow
        
        this.activeStance = state.activeStance
        
        // Preserve default stances if loading empty state, otherwise load user state
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
