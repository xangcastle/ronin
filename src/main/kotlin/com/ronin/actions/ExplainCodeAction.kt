package com.ronin.actions

class ExplainCodeAction : BaseRoninAction() {
    override fun getPrompt(code: String): String {
        return "Explain this code:\n```\n$code\n```"
    }
}
