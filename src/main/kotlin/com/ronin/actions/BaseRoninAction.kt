package com.ronin.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.components.service
import com.intellij.openapi.wm.ToolWindowManager
import com.ronin.service.LLMService
import com.ronin.ui.ChatToolWindowFactory
import com.ronin.MyBundle


abstract class BaseRoninAction : AnAction() {

    abstract fun getPrompt(code: String): String

    override fun update(e: AnActionEvent) {
        // Enable only if text is selected
        val editor = e.getData(CommonDataKeys.EDITOR)
        e.presentation.isEnabledAndVisible = editor != null && editor.selectionModel.hasSelection()
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val selectedText = editor.selectionModel.selectedText ?: return

        val prompt = getPrompt(selectedText)

        // 1. Open the Tool Window if not open
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Ronin Chat")
        toolWindow?.show {
            // 2. Append User's "pseudo-command" to chat
            val chatWindow = ChatToolWindowFactory.ChatToolWindow.getInstance(project)
            chatWindow?.appendMessage(MyBundle.message("toolwindow.you"), prompt)

            // 3. Call Service
            com.intellij.openapi.application.ApplicationManager.getApplication().executeOnPooledThread {
                val llmService = project.service<LLMService>()
                val response = llmService.sendMessage(prompt)
                
                // 4. Append Response
                chatWindow?.appendMessage(MyBundle.message("toolwindow.ronin"), response)
            }
        }
    }
}
