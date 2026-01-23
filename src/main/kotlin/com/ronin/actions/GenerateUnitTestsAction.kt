package com.ronin.actions

class GenerateUnitTestsAction : BaseRoninAction() {
    override fun getPrompt(code: String): String {
        return "Generate unit tests for this code:\n```\n$code\n```"
    }
}
