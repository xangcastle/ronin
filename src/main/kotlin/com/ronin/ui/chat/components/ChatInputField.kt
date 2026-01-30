package com.ronin.ui.chat.components

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CustomShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorTextField
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.event.KeyEvent
import javax.swing.KeyStroke
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities

class ChatInputField(
    project: Project,
    private val onSendMessage: (String) -> Unit
) : EditorTextField(project, com.intellij.openapi.fileTypes.PlainTextFileType.INSTANCE) {

    private val MAX_VISIBLE_LINES = 15
    private val MIN_HEIGHT = 100

    init {
        setOneLineMode(false)
        setPlaceholder("Type a message... (Enter to send, Shift+Enter for newline)")

        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBColor.border(), 1),
            JBUI.Borders.empty(4)
        )

        addSettingsProvider { editor ->
            editor.settings.apply {
                isUseSoftWraps = true
                isLineNumbersShown = false
                isFoldingOutlineShown = false
                isRightMarginShown = false
                isVirtualSpace = false
                isAdditionalPageAtBottom = false
            }
            editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            editor.scrollPane.border = JBUI.Borders.empty()
            editor.backgroundColor = JBColor.namedColor("Editor.background", JBColor.WHITE)
        }

        addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                SwingUtilities.invokeLater {
                    revalidate()
                    repaint()
                }
            }
        })

        val sendAction = object : DumbAwareAction() {
            override fun actionPerformed(e: AnActionEvent) {
                sendMessage()
            }
        }
        sendAction.registerCustomShortcutSet(
            CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)),
            this
        )
    }

    override fun getPreferredSize(): Dimension {
        return ApplicationManager.getApplication().runWriteIntentReadAction<Dimension, RuntimeException> {
            val d = super.getPreferredSize()

            val editor = this.editor ?: return@runWriteIntentReadAction d

            val lineHeight = editor.lineHeight
            val lineCount = document.lineCount.coerceAtLeast(1)

            val linesToShow = lineCount.coerceAtMost(MAX_VISIBLE_LINES)

            val insets = insets
            val contentHeight = (linesToShow * lineHeight) + insets.top + insets.bottom + 4

            Dimension(d.width, contentHeight.coerceAtLeast(MIN_HEIGHT))
        }
    }

    private fun sendMessage() {
        val message = text.trim()
        if (message.isNotBlank()) {
            onSendMessage(message)
            ApplicationManager.getApplication().invokeLater {
                text = ""
                revalidate()
                repaint()
            }
        }
    }
}