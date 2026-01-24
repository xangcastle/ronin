package com.ronin.actions

class ImproveCodeAction : BaseRoninAction() {
    override fun getPrompt(code: String): String {
        return "Improve this code:\n```\n$code\n```"
    }
}
