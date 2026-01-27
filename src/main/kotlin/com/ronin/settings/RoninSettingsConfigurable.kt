package com.ronin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import javax.swing.*
import java.util.UUID

import com.ronin.MyBundle
import com.ronin.settings.RoninSettingsState.Stance

class RoninSettingsConfigurable : Configurable {
    private var mainPanel: JPanel? = null
    
    // Stance Management
    private val stanceSelector = ComboBox<String>()
    private val addStanceButton = JButton("Add Stance")
    private val removeStanceButton = JButton("Remove Stance")
    
    // Stance Fields
    private val nameField = JBTextField()
    private val descriptionField = JBTextField()
    private val providerComboBox = ComboBox(arrayOf("OpenAI", "Anthropic", "Google", "Kimi", "Minimax", "Ollama"))
    private val modelField = JBTextField()
    private val scopeField = JBTextField()
    private val credentialIdField = JBTextField()
    private val apiKeyField = JBPasswordField() // Used to update key
    private val executionCommandField = JBTextField()
    private val systemPromptField = JBTextArea(5, 40)
    
    // Global Fields
    private val ollamaBaseUrlField = JBTextField()
    private val allowedToolsField = JBTextField()
    private val coreWorkflowField = JBTextArea(5, 40)

    // Local State
    private var localStances = mutableListOf<Stance>()
    private var currentStanceIndex = -1
    private var isUpdating = false

    override fun getDisplayName(): String = "Ronin"

    override fun createComponent(): JComponent? {
        systemPromptField.lineWrap = true
        systemPromptField.wrapStyleWord = true
        coreWorkflowField.lineWrap = true
        coreWorkflowField.wrapStyleWord = true

        // Top Bar: Selector + Buttons
        val topPanel = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT))
        topPanel.add(JLabel("Select Stance:"))
        stanceSelector.prototypeDisplayValue = "The Daimyo (General Architect) - Extra Width"
        topPanel.add(stanceSelector)
        topPanel.add(addStanceButton)
        topPanel.add(removeStanceButton)

        // Stance Form
        val stanceForm = FormBuilder.createFormBuilder()
            .addLabeledComponent("Name:", nameField)
            .addLabeledComponent("Description:", descriptionField)
            .addLabeledComponent("Provider:", providerComboBox)
            .addLabeledComponent("Model:", modelField)
            .addLabeledComponent("Scope (Tip: Use bazel targets like //core/...):", scopeField)
            .addLabeledComponent("Credential ID:", credentialIdField)
            .addLabeledComponent("Update API Key (Leave empty to keep):", apiKeyField)
            .addLabeledComponent("Execution Command:", executionCommandField)
            .addLabeledComponent("System Prompt:", JBScrollPane(systemPromptField))
            .addSeparator()
            .panel

        // Global Form
        val globalForm = FormBuilder.createFormBuilder()
            .addSeparator()
            .addSeparator()
            .addLabeledComponent(MyBundle.message("settings.ollama_url"), ollamaBaseUrlField)
            .addLabeledComponent(MyBundle.message("settings.allowed_tools"), allowedToolsField)
            .addLabeledComponent(MyBundle.message("settings.core_workflow"), JBScrollPane(coreWorkflowField))
            .addComponentFillVertically(JPanel(), 0)
            .panel
            
        mainPanel = JPanel(BorderLayout())
        mainPanel!!.add(topPanel, BorderLayout.NORTH)
        
        val centerPanel = JPanel()
        centerPanel.layout = BoxLayout(centerPanel, BoxLayout.Y_AXIS)
        centerPanel.add(stanceForm)
        centerPanel.add(globalForm)
        
        mainPanel!!.add(JBScrollPane(centerPanel), BorderLayout.CENTER)

        setupListeners()
        return mainPanel
    }
    
    private fun setupListeners() {
        stanceSelector.addActionListener {
            if (isUpdating) return@addActionListener
            if (currentStanceIndex >= 0 && currentStanceIndex < localStances.size) {
                saveFormToStance(localStances[currentStanceIndex])
            }
            val idx = stanceSelector.selectedIndex
            if (idx >= 0 && idx < localStances.size) {
                currentStanceIndex = idx
                loadStanceToForm(localStances[idx])
            } else {
                currentStanceIndex = -1
                clearForm()
            }
        }
        
        addStanceButton.addActionListener {
            val newStance = Stance(name = "New Stance", credentialId = "key_" + UUID.randomUUID().toString().take(8))
            localStances.add(newStance)
            refreshSelector()
            stanceSelector.selectedIndex = localStances.size - 1
        }
        
        removeStanceButton.addActionListener {
            val idx = stanceSelector.selectedIndex
            if (idx >= 0) {
                localStances.removeAt(idx)
                refreshSelector()
                if (localStances.isNotEmpty()) {
                    stanceSelector.selectedIndex = (idx - 1).coerceAtLeast(0)
                } else {
                    clearForm()
                    currentStanceIndex = -1
                }
            }
        }
    }
    
    private fun refreshSelector() {
        isUpdating = true
        try {
            val oldSelection = stanceSelector.selectedIndex
            stanceSelector.removeAllItems()
            for (s in localStances) {
                stanceSelector.addItem(s.name)
            }
            if (oldSelection >= 0 && oldSelection < localStances.size) {
                stanceSelector.selectedIndex = oldSelection
            } else if (localStances.isNotEmpty()) {
                stanceSelector.selectedIndex = 0
            }
        } finally {
            isUpdating = false
        }
    }
    
    private fun loadStanceToForm(s: Stance) {
        nameField.text = s.name
        descriptionField.text = s.description
        providerComboBox.selectedItem = s.provider
        modelField.text = s.model
        scopeField.text = s.scope
        credentialIdField.text = s.credentialId
        executionCommandField.text = s.executionCommand
        apiKeyField.text = "" // Always clear password field on load
        systemPromptField.text = s.systemPrompt
    }
    
    private fun saveFormToStance(s: Stance) {
        s.name = nameField.text
        s.description = descriptionField.text
        s.provider = providerComboBox.selectedItem as? String ?: "OpenAI"
        s.model = modelField.text
        s.scope = scopeField.text
        s.credentialId = credentialIdField.text
        s.executionCommand = executionCommandField.text
        s.systemPrompt = systemPromptField.text
        
        val newKey = String(apiKeyField.password)
        if (newKey.isNotEmpty()) {
            tempKeyUpdates[s.credentialId] = newKey
        }
    }
    
    private fun clearForm() {
        nameField.text = ""
        descriptionField.text = ""
        modelField.text = ""
        scopeField.text = ""
        credentialIdField.text = ""
        executionCommandField.text = ""
        apiKeyField.text = ""
        systemPromptField.text = ""
    }

    // Temporary storage for key updates (CredID -> NewKey)
    private val tempKeyUpdates = mutableMapOf<String, String>()

    override fun isModified(): Boolean {
        val settings = RoninSettingsState.instance
        if (currentStanceIndex >= 0 && currentStanceIndex < localStances.size) {
            saveFormToStance(localStances[currentStanceIndex])
        }
        
        if (ollamaBaseUrlField.text != settings.ollamaBaseUrl) return true
        if (allowedToolsField.text != settings.allowedTools) return true
        if (coreWorkflowField.text != settings.coreWorkflow) return true
        if (localStances != settings.stances) return true
        if (tempKeyUpdates.isNotEmpty()) return true
        
        return false
    }

    override fun apply() {
        if (currentStanceIndex >= 0 && currentStanceIndex < localStances.size) {
            saveFormToStance(localStances[currentStanceIndex])
        }
        
        val settings = RoninSettingsState.instance
        settings.ollamaBaseUrl = ollamaBaseUrlField.text
        settings.allowedTools = allowedToolsField.text
        settings.coreWorkflow = coreWorkflowField.text
        
        val activeStanceId = settings.stances.find { it.name == settings.activeStance }?.id
        
        settings.stances.clear()
        for (s in localStances) {
             settings.stances.add(s.copy()) // Copy to detach from UI
        }
        
        var activeRestored = false
        if (activeStanceId != null) {
            val newName = settings.stances.find { it.id == activeStanceId }?.name
            if (newName != null) {
                settings.activeStance = newName
                activeRestored = true
            }
        }
        
        if (!activeRestored && settings.stances.none { it.name == settings.activeStance } && settings.stances.isNotEmpty()) {
            settings.activeStance = settings.stances[0].name
        }
        
        for ((id, key) in tempKeyUpdates) {
             CredentialHelper.setApiKey(id, key)
        }
        tempKeyUpdates.clear()
        
        val messageBus = com.intellij.openapi.application.ApplicationManager.getApplication().messageBus
        messageBus.syncPublisher(RoninSettingsNotifier.TOPIC).settingsChanged(settings)
    }

    override fun reset() {
        val settings = RoninSettingsState.instance
        ollamaBaseUrlField.text = settings.ollamaBaseUrl
        allowedToolsField.text = settings.allowedTools
        coreWorkflowField.text = settings.coreWorkflow
        
        localStances.clear()
        for (s in settings.stances) {
            localStances.add(s.copy())
        }
        
        tempKeyUpdates.clear()
        refreshSelector()
        
        // Try to select active stance
        val activeIdx = localStances.indexOfFirst { it.name == settings.activeStance }
        
        if (activeIdx >= 0) {
            currentStanceIndex = activeIdx
            isUpdating = true
            stanceSelector.selectedIndex = activeIdx
            isUpdating = false
            loadStanceToForm(localStances[activeIdx])
        } else if (localStances.isNotEmpty()) {
            currentStanceIndex = 0
            isUpdating = true
            stanceSelector.selectedIndex = 0
            isUpdating = false
            loadStanceToForm(localStances[0])
        } else {
             currentStanceIndex = -1
             clearForm()
        }
    }

    override fun disposeUIResources() {
        mainPanel = null
    }
}
