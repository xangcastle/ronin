package com.ronin.actions

class FixCodeAction : BaseRoninAction() {
    override fun getPrompt(code: String): String {
        return "Fix this code:\n```\n$code\n```"
    }
}
