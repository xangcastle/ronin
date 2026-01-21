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
    var openaiApiKey: String = ""
    var anthropicApiKey: String = ""
    var googleApiKey: String = ""
    var kimiApiKey: String = ""
    var minimaxApiKey: String = ""
    var ollamaBaseUrl: String = "http://localhost:11434/"
    
    var model: String = "gpt-4o"
    var provider: String = "OpenAI"

    override fun getState(): RoninSettingsState {
        return this
    }

    override fun loadState(state: RoninSettingsState) {
        this.openaiApiKey = state.openaiApiKey
        this.anthropicApiKey = state.anthropicApiKey
        this.googleApiKey = state.googleApiKey
        this.kimiApiKey = state.kimiApiKey
        this.minimaxApiKey = state.minimaxApiKey
        this.ollamaBaseUrl = state.ollamaBaseUrl
        
        this.model = state.model
        this.provider = state.provider
    }

    companion object {
        val instance: RoninSettingsState
            get() = service()
    }
}
