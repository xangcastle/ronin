package com.ronin.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.ronin.ui.chat.ChatToolWindow

class ChatToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val chatToolWindow = ChatToolWindow(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(chatToolWindow.getContent(), "", false)
        toolWindow.contentManager.addContent(content)
        
        // Add toolbar actions
        setupToolbarActions(toolWindow, chatToolWindow)
    }

    private fun setupToolbarActions(toolWindow: ToolWindow, chatToolWindow: ChatToolWindow) {
        val actionGroup = DefaultActionGroup()

        actionGroup.add(object : AnAction(
            "Export Conversation...",
            "Save chat history to JSON",
            com.intellij.icons.AllIcons.Actions.Download
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                chatToolWindow.exportConversation()
            }
        })

        actionGroup.add(object : AnAction(
            "Clear Chat",
            "Reset conversation",
            com.intellij.icons.AllIcons.Actions.GC
        ) {
            override fun actionPerformed(e: AnActionEvent) {
                chatToolWindow.clearChat()
            }
        })
        
        toolWindow.setTitleActions(listOf(actionGroup))
    }
}
