package com.akatsuki.trading.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.akatsuki.trading.data.model.KotakSession
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "akatsuki_session",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(session: KotakSession) {
        prefs.edit()
            .putString(KEY_TOKEN, session.sessionToken)
            .putString(KEY_SID, session.sessionSid)
            .putString(KEY_BASE_URL, session.baseUrl)
            .putString(KEY_DC, session.dataCenter)
            .putString(KEY_ORDER_URL, session.orderUrl)
            .putString(KEY_GREETING, session.greetingName)
            .putLong(KEY_TIME, session.loginTime)
            .apply()
    }

    fun load(): KotakSession? {
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        val sid = prefs.getString(KEY_SID, null) ?: return null
        val baseUrl = prefs.getString(KEY_BASE_URL, null) ?: return null
        if (token.isEmpty() || sid.isEmpty() || baseUrl.isEmpty()) return null
        return KotakSession(
            sessionToken = token,
            sessionSid = sid,
            baseUrl = baseUrl,
            dataCenter = prefs.getString(KEY_DC, "") ?: "",
            orderUrl = prefs.getString(KEY_ORDER_URL, "") ?: "",
            greetingName = prefs.getString(KEY_GREETING, "") ?: "",
            loginTime = prefs.getLong(KEY_TIME, 0L)
        )
    }

    fun isValid(): Boolean {
        val token = prefs.getString(KEY_TOKEN, null) ?: return false
        val loginTime = prefs.getLong(KEY_TIME, 0L)
        val sessionAgeHours = (System.currentTimeMillis() - loginTime) / 3_600_000L
        return token.isNotEmpty() && sessionAgeHours < 8
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_TOKEN = "session_token"
        private const val KEY_SID = "session_sid"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_DC = "data_center"
        private const val KEY_ORDER_URL = "order_url"
        private const val KEY_GREETING = "greeting_name"
        private const val KEY_TIME = "login_time"
    }
}
