package com.ronin.service

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.annotations.XCollection
import java.io.Serializable

@Service(Service.Level.PROJECT)
@State(
    name = "RoninChatHistory",
    storages = [Storage("ronin_chat_history.xml")]
)
class ChatStorageService : PersistentStateComponent<ChatStorageService.State> {

    data class ChatMessage(
        var role: String = "",
        var content: String = "",
        var timestamp: Long = System.currentTimeMillis()
    ) : Serializable

    class State {
        @XCollection(style = XCollection.Style.v2)
        var messages: MutableList<ChatMessage> = mutableListOf()
    }

    private var myState = State()

    override fun getState(): State {
        return myState
    }

    override fun loadState(state: State) {
        myState = state
    }

    fun addMessage(role: String, content: String) {
        myState.messages.add(ChatMessage(role, content))
    }

    fun getHistory(): List<Map<String, String>> {
        return myState.messages.map { mapOf("role" to it.role, "content" to it.content) }
    }

    fun clearHistory() {
        myState.messages.clear()
    }
}
