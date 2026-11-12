package com.ourchat.mobile

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ChatApi(context: Context) {
    private val preferences = OurChatPreferences(context.applicationContext)

    fun normalizedBaseUrl(): String {
        val entered = preferences.serverUrl.trim().trimEnd('/')
        require(entered.startsWith("https://")) {
            "Use the secure HTTPS address of your OUR CHAT server."
        }
        return when {
            entered.endsWith("/api/our-chat") -> entered
            entered.endsWith("/api") -> "$entered/our-chat"
            else -> "$entered/api/our-chat"
        }
    }

    fun socketOrigin(): String {
        val uri = Uri.parse(normalizedBaseUrl())
        return "${uri.scheme}://${uri.encodedAuthority}"
    }

    suspend fun setupStatus(): AccountSetup {
        return AccountSetup.fromJson(request("GET", "/setup/status", authenticated = false))
    }

    suspend fun setupAccounts(adminUsername: String, guestUsername: String): AccountSetup {
        val result = request(
            "POST",
            "/setup",
            JSONObject()
                .put("adminUsername", adminUsername)
                .put("guestUsername", guestUsername),
            authenticated = false,
        )
        return AccountSetup.fromJson(result)
    }

    suspend fun login(username: String): JSONObject {
        val response = request(
            "POST",
            "/login",
            JSONObject().put("username", username),
            authenticated = false,
        )
        preferences.token = response.optString("token")
        preferences.role = response.optString("role")
        preferences.username = username
        return response
    }

    suspend fun loadState(): JSONObject = request("GET", "/state")

    suspend fun loadMessages(): List<ChatMessage> {
        val rows = request("GET", "/messages").optJSONArray("messages") ?: JSONArray()
        return (0 until rows.length()).mapNotNull { index ->
            rows.optJSONObject(index)?.let(ChatMessage::fromJson)
        }
    }

    suspend fun sendMessage(body: String, clientId: String): ChatMessage {
        val result = request(
            "POST",
            "/messages",
            JSONObject().put("body", body).put("clientId", clientId),
        )
        return ChatMessage.fromJson(result.getJSONObject("message"))
    }

    suspend fun markRead(id: Long) {
        request("POST", "/messages/$id/read", JSONObject())
    }

    suspend fun renameGuest(username: String): JSONObject {
        return request(
            "PUT",
            "/admin/guest-username",
            JSONObject().put("guestUsername", username),
        )
    }

    suspend fun setStatusButton(visible: Boolean): JSONObject {
        return request(
            "PUT",
            "/admin/status-button",
            JSONObject().put("visible", visible),
        )
    }

    suspend fun dashboard(): JSONObject = request("GET", "/admin/dashboard")

    suspend fun setConsent(active: Boolean): JSONObject {
        return request(
            "POST",
            "/monitoring/consent",
            JSONObject().put("active", active),
        )
    }

    suspend fun heartbeat(
        internetStatus: String,
        networkType: String,
        messengerForeground: Boolean,
        serviceActive: Boolean,
        permissions: JSONObject,
    ): JSONObject {
        return request(
            "POST",
            "/monitoring/heartbeat",
            JSONObject()
                .put("sharingActive", true)
                .put("internetStatus", internetStatus)
                .put("networkType", networkType)
                .put("messengerForeground", messengerForeground)
                .put("serviceActive", serviceActive)
                .put("permissions", permissions),
        )
    }

    suspend fun reportEvent(type: String, detail: JSONObject = JSONObject()) {
        request(
            "POST",
            "/monitoring/event",
            JSONObject().put("type", type).put("detail", detail),
        )
    }

    private suspend fun request(
        method: String,
        path: String,
        body: JSONObject? = null,
        authenticated: Boolean = true,
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(normalizedBaseUrl() + path).openConnection() as HttpURLConnection)
        try {
            connection.requestMethod = method
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = preferences.token
                check(token.isNotBlank()) { "Sign in before using OUR CHAT." }
                connection.setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { output ->
                    output.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = if (response.isBlank()) JSONObject() else JSONObject(response)
            if (code !in 200..299) {
                throw IllegalStateException(json.optString("error", "The server returned HTTP $code."))
            }
            json
        } finally {
            connection.disconnect()
        }
    }
}
