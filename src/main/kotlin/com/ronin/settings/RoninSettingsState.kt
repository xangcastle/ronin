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
    var ollamaBaseUrl: String = "http://localhost:11434"
    
    var model: String = "gpt-4o"
    var provider: String = "OpenAI"
    var allowedTools: String = "git, podman, kubectl, argocd, aws, bazel"

    // Legacy fields for migration
    var openaiApiKey: String? = null
    var anthropicApiKey: String? = null
    var googleApiKey: String? = null
    var kimiApiKey: String? = null
    var minimaxApiKey: String? = null

    override fun getState(): RoninSettingsState {
        return this
    }

    override fun loadState(state: RoninSettingsState) {
        this.ollamaBaseUrl = state.ollamaBaseUrl
        this.model = state.model
        this.provider = state.provider
        this.allowedTools = state.allowedTools

        // Migrate keys to PasswordSafe if found in XML
        migrateKey("openaiApiKey", state.openaiApiKey)
        migrateKey("anthropicApiKey", state.anthropicApiKey)
        migrateKey("googleApiKey", state.googleApiKey)
        migrateKey("kimiApiKey", state.kimiApiKey)
        migrateKey("minimaxApiKey", state.minimaxApiKey)
    }

    private fun migrateKey(keyName: String, value: String?) {
        if (!value.isNullOrBlank()) {
            CredentialHelper.setApiKey(keyName, value)
        }
    }

    companion object {
        val instance: RoninSettingsState
            get() = service()
    }
}
