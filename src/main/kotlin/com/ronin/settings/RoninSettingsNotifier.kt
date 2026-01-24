package com.ronin.settings

import com.intellij.util.messages.Topic

interface RoninSettingsNotifier {
    companion object {
        val TOPIC = Topic.create("Ronin Settings Changed", RoninSettingsNotifier::class.java)
    }

    fun settingsChanged(settings: RoninSettingsState)
}
