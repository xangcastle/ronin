package com.ronin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JLabel

class RoninSettingsConfigurable : Configurable {
    private var settingsPanel: JPanel? = null
    private val openaiApiKeyField = JBPasswordField()
    private val anthropicApiKeyField = JBPasswordField()
    private val googleApiKeyField = JBPasswordField()
    private val kimiApiKeyField = JBPasswordField()
    private val minimaxApiKeyField = JBPasswordField()
    private val ollamaBaseUrlField = JBTextField()
    
    private val providerComboBox = ComboBox(arrayOf("OpenAI", "Anthropic", "Google", "Kimi", "Minimax", "Ollama"))

    override fun getDisplayName(): String = "Ronin"

    override fun createComponent(): JComponent? {
        settingsPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JLabel("Provider:"), providerComboBox)
            .addSeparator()
            .addLabeledComponent(JLabel("OpenAI API Key:"), openaiApiKeyField)
            .addLabeledComponent(JLabel("Anthropic API Key:"), anthropicApiKeyField)
            .addLabeledComponent(JLabel("Google API Key:"), googleApiKeyField)
            .addLabeledComponent(JLabel("Kimi API Key:"), kimiApiKeyField)
            .addLabeledComponent(JLabel("Minimax API Key:"), minimaxApiKeyField)
            .addSeparator()
            .addLabeledComponent(JLabel("Ollama Base URL:"), ollamaBaseUrlField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        return settingsPanel
    }

    override fun isModified(): Boolean {
        val settings = RoninSettingsState.instance
        return String(openaiApiKeyField.password) != settings.openaiApiKey ||
                String(anthropicApiKeyField.password) != settings.anthropicApiKey ||
                String(googleApiKeyField.password) != settings.googleApiKey ||
                String(kimiApiKeyField.password) != settings.kimiApiKey ||
                String(minimaxApiKeyField.password) != settings.minimaxApiKey ||
                ollamaBaseUrlField.text != settings.ollamaBaseUrl ||
                providerComboBox.selectedItem != settings.provider
    }

    override fun apply() {
        val settings = RoninSettingsState.instance
        settings.openaiApiKey = String(openaiApiKeyField.password)
        settings.anthropicApiKey = String(anthropicApiKeyField.password)
        settings.googleApiKey = String(googleApiKeyField.password)
        settings.kimiApiKey = String(kimiApiKeyField.password)
        settings.minimaxApiKey = String(minimaxApiKeyField.password)
        settings.ollamaBaseUrl = ollamaBaseUrlField.text
        settings.provider = providerComboBox.selectedItem as String ?: "OpenAI"
    }

    override fun reset() {
        val settings = RoninSettingsState.instance
        openaiApiKeyField.text = settings.openaiApiKey
        anthropicApiKeyField.text = settings.anthropicApiKey
        googleApiKeyField.text = settings.googleApiKey
        kimiApiKeyField.text = settings.kimiApiKey
        minimaxApiKeyField.text = settings.minimaxApiKey
        ollamaBaseUrlField.text = settings.ollamaBaseUrl
        providerComboBox.selectedItem = settings.provider
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
