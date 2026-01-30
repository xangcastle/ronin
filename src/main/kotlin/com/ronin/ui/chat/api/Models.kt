package com.ronin.ui.chat.api

/**
 * Represents a task to be sent to the LLM
 */
data class LLMTask(
    val message: String,
    val context: String,
    val history: List<Map<String, String>>
)

/**
 * Represents the response from the LLM after processing
 */
data class LLMResponse(
    val text: String,
    val scratchpad: String? = null,
    val toolOutput: String? = null,
    val commandToRun: String? = null,
    val requiresFollowUp: Boolean = false
)

/**
 * Types of messages in the chat
 */
enum class MessageType {
    USER,
    ASSISTANT,
    SYSTEM,
    THINKING
}

/**
 * Represents a chat message
 */
data class ChatMessage(
    val role: String,
    val content: String,
    val type: MessageType
)
