package com.nofence.browser.privacy

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class ProxyCredentials(
    val username: String,
    val password: String
)

/**
 * Encrypts proxy credentials (username/password) and Tor ControlPort authentication cookies/passwords
 * at rest using Android Keystore AES256-GCM MasterKey + EncryptedSharedPreferences.
 */
class EncryptedCredentialStore(context: Context) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context.applicationContext,
            "nofence_encrypted_credentials",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveProxyCredentials(profileId: Long, username: String, password: String) {
        prefs.edit()
            .putString("proxy_user_$profileId", username)
            .putString("proxy_pass_$profileId", password)
            .apply()
    }

    fun getProxyCredentials(profileId: Long): ProxyCredentials? {
        val username = prefs.getString("proxy_user_$profileId", null) ?: return null
        val password = prefs.getString("proxy_pass_$profileId", "") ?: ""
        if (username.isBlank()) return null
        return ProxyCredentials(username = username, password = password)
    }

    fun deleteProxyCredentials(profileId: Long) {
        prefs.edit()
            .remove("proxy_user_$profileId")
            .remove("proxy_pass_$profileId")
            .apply()
    }

    fun saveTorControlPassword(password: String) {
        prefs.edit().putString("tor_control_password", password).apply()
    }

    fun getTorControlPassword(): String {
        return prefs.getString("tor_control_password", "") ?: ""
    }

    fun clearAllCredentials() {
        prefs.edit().clear().apply()
    }
}
