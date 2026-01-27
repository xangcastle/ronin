package com.ronin.ui.chat.components

import com.intellij.util.ui.UIUtil
import com.ronin.MyBundle
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JComponent

/**
 * Represents different types of chat messages
 */
enum class BubbleType {
    USER,
    ASSISTANT,
    SYSTEM,
    THINKING
}

/**
 * A message bubble component for the chat interface
 */
class MessageBubble private constructor(
    private val type: BubbleType,
    private val message: String,
    private val maxWidth: Int
) : JPanel(GridBagLayout()) {
    
    init {
        isOpaque = false
        createBubble()
    }
    
    private fun createBubble() {
        val c = GridBagConstraints()
        c.gridx = 0
        c.gridy = 0
        c.weightx = 1.0
        c.fill = GridBagConstraints.HORIZONTAL
        
        when (type) {
            BubbleType.USER -> {
                c.anchor = GridBagConstraints.EAST
                c.insets = Insets(0, 50, 0, 0)
            }
            else -> {
                c.anchor = GridBagConstraints.WEST
                c.insets = Insets(0, 0, 0, 50)
            }
        }
        
        val textArea = createTextArea()
        val bubbleWrapper = JPanel(BorderLayout())
        bubbleWrapper.isOpaque = false
        bubbleWrapper.add(textArea, BorderLayout.CENTER)
        
        add(bubbleWrapper, c)
    }
    
    private fun createTextArea(): JTextArea {
        val textArea = DynamicTextArea(message, maxWidth)
        textArea.lineWrap = true
        textArea.wrapStyleWord = true
        textArea.isEditable = false
        textArea.isOpaque = true
        
        when (type) {
            BubbleType.USER -> {
                textArea.background = Color(0, 122, 255)
                textArea.foreground = Color.WHITE
            }
            BubbleType.SYSTEM -> {
                textArea.background = Color(40, 44, 52)
                textArea.foreground = Color(171, 178, 191)
                textArea.font = Font("JetBrains Mono", Font.PLAIN, 12)
            }
            BubbleType.THINKING -> {
                textArea.background = UIUtil.getPanelBackground()
                textArea.foreground = Color(150, 150, 150)
                textArea.font = Font(textArea.font.name, Font.ITALIC, 11)
                textArea.border = BorderFactory.createLineBorder(Color(200, 200, 200, 50), 1)
            }
            BubbleType.ASSISTANT -> {
                textArea.background = UIUtil.getPanelBackground()
                textArea.foreground = UIUtil.getLabelForeground()
                textArea.border = BorderFactory.createLineBorder(Color.GRAY, 1)
            }
        }
        
        textArea.border = BorderFactory.createCompoundBorder(
            textArea.border,
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        )
        
        return textArea
    }
    
    /**
     * Text area that dynamically adjusts its width
     */
    private class DynamicTextArea(text: String, private val maxWidth: Int) : JTextArea(text) {
        override fun getPreferredSize(): java.awt.Dimension {
            val d = super.getPreferredSize()
            val effectiveMaxWidth = (maxWidth * 0.85).toInt()
            if (effectiveMaxWidth > 100 && d.width > effectiveMaxWidth) {
                return java.awt.Dimension(effectiveMaxWidth, d.height)
            }
            return d
        }
        
        override fun getScrollableTracksViewportWidth(): Boolean = true
        
        override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
            var w = width
            val effectiveMaxWidth = (maxWidth * 0.85).toInt()
            if (w > effectiveMaxWidth) {
                w = effectiveMaxWidth
            }
            super.setBounds(x, y, w, height)
        }
    }
    
    companion object {
        /**
         * Creates a user message bubble
         */
        fun createUserMessage(message: String, maxWidth: Int = 600): JComponent {
            return MessageBubble(BubbleType.USER, message, maxWidth)
        }
        
        /**
         * Creates an assistant message bubble
         */
        fun createAssistantMessage(message: String, maxWidth: Int = 600): JComponent {
            return MessageBubble(BubbleType.ASSISTANT, message, maxWidth)
        }
        
        /**
         * Creates a system message bubble
         */
        fun createSystemMessage(message: String, maxWidth: Int = 600): JComponent {
            return MessageBubble(BubbleType.SYSTEM, message, maxWidth)
        }
        
        /**
         * Creates a thinking message bubble
         */
        fun createThinkingMessage(message: String, maxWidth: Int = 600): JComponent {
            return MessageBubble(BubbleType.THINKING, message, maxWidth)
        }
    }
}
