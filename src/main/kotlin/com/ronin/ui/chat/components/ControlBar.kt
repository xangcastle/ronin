package com.ronin.ui.chat.components

import com.intellij.icons.AllIcons
import com.intellij.openapi.ui.ComboBox
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.DefaultComboBoxModel

/**
 * Control bar with model selector and action buttons
 */
class ControlBar(
    private val onActionButtonClick: () -> Unit,
    private val onAttachClick: () -> Unit,
    private val onModelChange: (String) -> Unit
) : JPanel(GridBagLayout()) {
    
    val modelComboBox = ComboBox<String>()
    private val actionButton = JButton(AllIcons.Actions.Refresh)
    private val attachButton = JButton(AllIcons.FileTypes.Any_type)
    
    private var isGenerating = false
    
    init {
        border = JBUI.Borders.emptyBottom(5)
        createControls()
    }
    
    private fun createControls() {
        modelComboBox.isOpaque = false
        modelComboBox.addActionListener {
            val selected = modelComboBox.selectedItem as? String
            if (selected != null) {
                onModelChange(selected)
            }
        }
        val gbc = GridBagConstraints()

        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.anchor = GridBagConstraints.WEST
        add(modelComboBox, gbc)

        val rightControls = JPanel()
        rightControls.layout = BoxLayout(rightControls, BoxLayout.X_AXIS)

        actionButton.toolTipText = "Reset Chat"
        actionButton.addActionListener { onActionButtonClick() }

        attachButton.toolTipText = "Attach Image"
        attachButton.addActionListener { onAttachClick() }

        rightControls.add(actionButton)
        rightControls.add(Box.createHorizontalStrut(5))
        rightControls.add(attachButton)

        gbc.gridx = 1
        gbc.gridy = 0
        gbc.weightx = 0.0
        gbc.fill = GridBagConstraints.NONE
        gbc.anchor = GridBagConstraints.EAST
        gbc.insets = JBUI.insetsLeft(10)

        add(rightControls, gbc)
    }
    
    /**
     * Updates the model list
     */
    fun updateModels(models: Array<String>, selectedModel: String?) {
        val model = DefaultComboBoxModel(models)
        modelComboBox.model = model
        
        if (selectedModel != null && models.contains(selectedModel)) {
            modelComboBox.selectedItem = selectedModel
        } else if (models.isNotEmpty()) {
            modelComboBox.selectedItem = models[0]
        }
    }
    
    /**
     * Sets whether models are being loaded
     */
    fun setModelsLoading(loading: Boolean) {
        if (loading) {
            modelComboBox.model = DefaultComboBoxModel(arrayOf("Loading..."))
            modelComboBox.isEnabled = false
        } else {
            modelComboBox.isEnabled = true
        }
    }
    
    /**
     * Updates the state of the action button based on generation status
     */
    fun setGenerating(generating: Boolean) {
        isGenerating = generating
        if (generating) {
            actionButton.icon = AllIcons.Actions.Suspend
            actionButton.toolTipText = "Stop Generation"
        } else {
            actionButton.icon = AllIcons.Actions.Refresh
            actionButton.toolTipText = "Reset Chat (Keep Settings)"
        }
    }
}
