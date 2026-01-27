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
        var scope: String = "General",
        var credentialId: String = "",
        var executionCommand: String = "bazel run //project:app.binary",
        var encryptedKey: String? = null
    )

    var stances: MutableList<Stance> = mutableListOf()
    var activeStance: String = "Hi (Django REST)"
    var settingsEditable: Boolean = true
    
    var ollamaBaseUrl: String = "http://localhost:11434"
    var allowedTools: String = "git, podman, kubectl, argocd, aws, bazel"
    var coreWorkflow: String = """
        1. **PLAN**: Analyze request.
        2. **EXECUTE**: Return the JSON with commands and edits.
        3. **VERIFY**: Check if the goal is achieved. Only run verification commands (test/build) if necessary to validate code changes. Do NOT verify simple info queries (e.g. pwd, ls).
    """.trimIndent()

    init {
        val profileResource = RoninSettingsState::class.java.getResource("/ronin-profile.json")
        if (stances.isEmpty() && profileResource != null) {
            try {
                val content = profileResource.readText()
                val profile = com.google.gson.Gson().fromJson(content, Profile::class.java)
                
                this.settingsEditable = profile.settingsEditable
                
                if (profile.stances.isNotEmpty()) {
                    stances.addAll(profile.stances)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    


    override fun getState(): RoninSettingsState {
        return this
    }

    override fun loadState(state: RoninSettingsState) {
        this.ollamaBaseUrl = state.ollamaBaseUrl
        this.allowedTools = state.allowedTools
        this.coreWorkflow = state.coreWorkflow
        
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
