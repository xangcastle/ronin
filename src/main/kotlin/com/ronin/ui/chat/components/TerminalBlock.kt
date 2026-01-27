package com.ronin.ui.chat.components

import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Terminal block component for displaying command output
 */
class TerminalBlock(private val command: String) : JPanel(FlowLayout(FlowLayout.LEFT)) {
    
    private val termPanel = JPanel(BorderLayout())
    private val outputArea = JTextArea()
    
    init {
        isOpaque = false
        createTerminalPanel()
    }
    
    private fun createTerminalPanel() {
        termPanel.background = Color(30, 30, 30)
        termPanel.border = BorderFactory.createLineBorder(Color.GRAY)
        
        // Header with command
        val header = JLabel(" \$ $command")
        header.foreground = Color.GREEN
        header.font = Font("JetBrains Mono", Font.BOLD, 12)
        header.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        termPanel.add(header, BorderLayout.NORTH)
        
        // Output area
        outputArea.background = Color(30, 30, 30)
        outputArea.foreground = Color.LIGHT_GRAY
        outputArea.font = Font("JetBrains Mono", Font.PLAIN, 12)
        outputArea.isEditable = false
        outputArea.columns = 50
        outputArea.rows = 5
        termPanel.add(outputArea, BorderLayout.CENTER)
        
        add(termPanel)
    }
    
    /**
     * Appends text to the output area
     */
    fun appendOutput(text: String) {
        outputArea.append(text)
        outputArea.caretPosition = outputArea.document.length
    }
    
    /**
     * Marks the command as finished
     */
    fun markFinished() {
        appendOutput("\n[Finished]")
    }
    
    /**
     * Gets the current output text
     */
    fun getOutput(): String = outputArea.text
}
