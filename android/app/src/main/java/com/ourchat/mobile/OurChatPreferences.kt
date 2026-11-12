package com.ourchat.mobile

import android.content.Context

class OurChatPreferences(context: Context) {
    private val values = context.getSharedPreferences("our_chat", Context.MODE_PRIVATE)

    var serverUrl: String
        get() = values.getString(KEY_SERVER, "") ?: ""
        set(value) = values.edit().putString(KEY_SERVER, value).apply()

    var token: String
        get() = values.getString(KEY_TOKEN, "") ?: ""
        set(value) = values.edit().putString(KEY_TOKEN, value).apply()

    var role: String
        get() = values.getString(KEY_ROLE, "") ?: ""
        set(value) = values.edit().putString(KEY_ROLE, value).apply()

    var username: String
        get() = values.getString(KEY_USERNAME, "") ?: ""
        set(value) = values.edit().putString(KEY_USERNAME, value).apply()

    var sharingActive: Boolean
        get() = values.getBoolean(KEY_SHARING, false)
        set(value) = values.edit().putBoolean(KEY_SHARING, value).apply()

    var pendingRevocation: Boolean
        get() = values.getBoolean(KEY_PENDING_REVOCATION, false)
        set(value) = values.edit().putBoolean(KEY_PENDING_REVOCATION, value).apply()

    var serverUnavailableSince: String
        get() = values.getString(KEY_SERVER_UNAVAILABLE, "") ?: ""
        set(value) = values.edit().putString(KEY_SERVER_UNAVAILABLE, value).apply()

    var messengerForeground: Boolean
        get() = values.getBoolean(KEY_FOREGROUND, false)
        set(value) = values.edit().putBoolean(KEY_FOREGROUND, value).apply()

    fun clearSession() {
        values.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_ROLE)
            .remove(KEY_USERNAME)
            .apply()
    }

    companion object {
        private const val KEY_SERVER = "server_url"
        private const val KEY_TOKEN = "session_token"
        private const val KEY_ROLE = "account_role"
        private const val KEY_USERNAME = "username"
        private const val KEY_SHARING = "sharing_active"
        private const val KEY_PENDING_REVOCATION = "pending_revocation"
        private const val KEY_SERVER_UNAVAILABLE = "server_unavailable_since"
        private const val KEY_FOREGROUND = "messenger_foreground"
    }
}
