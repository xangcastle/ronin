package com.ronin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JLabel

import com.ronin.MyBundle

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
            .addLabeledComponent(JLabel(MyBundle.message("settings.provider")), providerComboBox)
            .addSeparator()
            .addLabeledComponent(JLabel(MyBundle.message("settings.openai_key")), openaiApiKeyField)
            .addLabeledComponent(JLabel(MyBundle.message("settings.anthropic_key")), anthropicApiKeyField)
            .addLabeledComponent(JLabel(MyBundle.message("settings.google_key")), googleApiKeyField)
            .addLabeledComponent(JLabel(MyBundle.message("settings.kimi_key")), kimiApiKeyField)
            .addLabeledComponent(JLabel(MyBundle.message("settings.minimax_key")), minimaxApiKeyField)
            .addSeparator()
            .addLabeledComponent(JLabel(MyBundle.message("settings.ollama_url")), ollamaBaseUrlField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
        
        return settingsPanel
    }

    override fun isModified(): Boolean {
        val settings = RoninSettingsState.instance
        return String(openaiApiKeyField.password) != (CredentialHelper.getApiKey("openaiApiKey") ?: "") ||
                String(anthropicApiKeyField.password) != (CredentialHelper.getApiKey("anthropicApiKey") ?: "") ||
                String(googleApiKeyField.password) != (CredentialHelper.getApiKey("googleApiKey") ?: "") ||
                String(kimiApiKeyField.password) != (CredentialHelper.getApiKey("kimiApiKey") ?: "") ||
                String(minimaxApiKeyField.password) != (CredentialHelper.getApiKey("minimaxApiKey") ?: "") ||
                ollamaBaseUrlField.text != settings.ollamaBaseUrl ||
                (providerComboBox.selectedItem as? String ?: "OpenAI") != settings.provider
    }

    override fun apply() {
        val settings = RoninSettingsState.instance
        CredentialHelper.setApiKey("openaiApiKey", String(openaiApiKeyField.password))
        CredentialHelper.setApiKey("anthropicApiKey", String(anthropicApiKeyField.password))
        CredentialHelper.setApiKey("googleApiKey", String(googleApiKeyField.password))
        CredentialHelper.setApiKey("kimiApiKey", String(kimiApiKeyField.password))
        CredentialHelper.setApiKey("minimaxApiKey", String(minimaxApiKeyField.password))
        settings.ollamaBaseUrl = ollamaBaseUrlField.text
        settings.provider = providerComboBox.selectedItem as? String ?: "OpenAI"
        val messageBus = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus
        messageBus.syncPublisher(RoninSettingsNotifier.TOPIC).settingsChanged(settings)
    }

    override fun reset() {
        val settings = RoninSettingsState.instance
        openaiApiKeyField.text = CredentialHelper.getApiKey("openaiApiKey") ?: ""
        anthropicApiKeyField.text = CredentialHelper.getApiKey("anthropicApiKey") ?: ""
        googleApiKeyField.text = CredentialHelper.getApiKey("googleApiKey") ?: ""
        kimiApiKeyField.text = CredentialHelper.getApiKey("kimiApiKey") ?: ""
        minimaxApiKeyField.text = CredentialHelper.getApiKey("minimaxApiKey") ?: ""
        ollamaBaseUrlField.text = settings.ollamaBaseUrl
        providerComboBox.selectedItem = settings.provider
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }
}
