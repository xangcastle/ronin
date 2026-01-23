package com.ronin.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.ide.passwordSafe.PasswordSafe

object CredentialHelper {
    private const val SERVICE_NAME = "Ronin"

    fun getApiKey(keyName: String): String? {
        val attributes = createCredentialAttributes(keyName)
        return PasswordSafe.instance.getPassword(attributes)
    }

    fun setApiKey(keyName: String, apiKey: String?) {
        val attributes = createCredentialAttributes(keyName)
        val credentials = apiKey?.let { Credentials(null, it) }
        PasswordSafe.instance.set(attributes, credentials)
    }

    private fun createCredentialAttributes(keyName: String): CredentialAttributes {
        return CredentialAttributes(SERVICE_NAME, keyName)
    }
}
