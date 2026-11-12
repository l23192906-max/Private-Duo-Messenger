package com.ourchat.mobile

import org.json.JSONObject

data class ChatMessage(
    val id: Long,
    val sender: String,
    val body: String,
    val createdAt: String,
    val deliveredAt: String?,
    val readAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject) = ChatMessage(
            id = json.optLong("id"),
            sender = json.optString("sender"),
            body = json.optString("body"),
            createdAt = json.optString("createdAt"),
            deliveredAt = json.optString("deliveredAt").takeIf { it.isNotBlank() && it != "null" },
            readAt = json.optString("readAt").takeIf { it.isNotBlank() && it != "null" },
        )
    }
}

data class AccountSetup(
    val configured: Boolean,
    val adminUsername: String,
    val guestUsername: String,
    val showStatusButton: Boolean,
) {
    companion object {
        fun fromJson(json: JSONObject) = AccountSetup(
            configured = json.optBoolean("configured"),
            adminUsername = json.optString("adminUsername"),
            guestUsername = json.optString("guestUsername"),
            showStatusButton = json.optBoolean("showStatusButton"),
        )
    }
}

data class GuestStatus(
    val state: String = "UNKNOWN",
    val internetStatus: String = "unknown",
    val serverConnectivity: String = "unknown",
    val networkType: String = "unknown",
    val messengerForeground: Boolean? = null,
    val serviceActive: Boolean = false,
    val sharingActive: Boolean = false,
    val lastUpdate: String? = null,
    val permissions: JSONObject = JSONObject(),
) {
    companion object {
        fun fromJson(json: JSONObject): GuestStatus {
            val snapshot = json.optJSONObject("guestStatus") ?: json
            return GuestStatus(
                state = snapshot.optString("state", "UNKNOWN"),
                internetStatus = snapshot.optString("internetStatus", "unknown"),
                serverConnectivity = snapshot.optString("serverConnectivity", "unknown"),
                networkType = snapshot.optString("networkType", "unknown"),
                messengerForeground = if (snapshot.isNull("messengerForeground")) {
                    null
                } else {
                    snapshot.optBoolean("messengerForeground")
                },
                serviceActive = snapshot.optBoolean("serviceActive"),
                sharingActive = snapshot.optBoolean("sharingActive"),
                lastUpdate = snapshot.optString("lastUpdate").takeIf { it.isNotBlank() && it != "null" },
                permissions = snapshot.optJSONObject("permissions") ?: JSONObject(),
            )
        }
    }
}
