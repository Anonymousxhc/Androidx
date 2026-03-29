package com.akatsuki.trading.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.akatsuki.trading.data.model.KotakCredentials
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "akatsuki_credentials",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(credentials: KotakCredentials) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, credentials.accessToken)
            .putString(KEY_MOBILE, credentials.mobileNumber)
            .putString(KEY_MPIN, credentials.mpin)
            .putString(KEY_UCC, credentials.ucc)
            .apply()
    }

    fun load(): KotakCredentials? {
        val token = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val mobile = prefs.getString(KEY_MOBILE, null) ?: return null
        val mpin = prefs.getString(KEY_MPIN, null) ?: return null
        val ucc = prefs.getString(KEY_UCC, null) ?: return null
        return KotakCredentials(token, mobile, mpin, ucc)
    }

    fun hasCredentials(): Boolean = prefs.contains(KEY_ACCESS_TOKEN)

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_MOBILE = "mobile_number"
        private const val KEY_MPIN = "mpin"
        private const val KEY_UCC = "ucc"
    }
}
