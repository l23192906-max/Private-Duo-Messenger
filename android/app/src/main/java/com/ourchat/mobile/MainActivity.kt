package com.ourchat.mobile

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import io.socket.client.IO
import io.socket.client.Socket
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        OurChatPreferences(this).messengerForeground = true
    }

    override fun onStop() {
        OurChatPreferences(this).messengerForeground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.createChannels(this)
        setContent { OurChatApp() }
    }
}

@androidx.compose.runtime.Composable
private fun OurChatApp() {
    val context = LocalContext.current
    val activity = context as Activity
    val preferences = remember { OurChatPreferences(context) }
    val api = remember { ChatApi(context) }
    val scope = rememberCoroutineScope()

    var serverUrl by remember { mutableStateOf(preferences.serverUrl) }
    var setup by remember { mutableStateOf<AccountSetup?>(null) }
    var role by remember { mutableStateOf(preferences.role) }
    var username by remember { mutableStateOf(preferences.username) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var guestStatus by remember { mutableStateOf(GuestStatus()) }
    var dashboard by remember { mutableStateOf<JSONObject?>(null) }
    var page by remember { mutableStateOf("chat") }
    var peerOnline by remember { mutableStateOf(false) }
    var showStatusButton by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf("") }
    var refreshKey by remember { mutableIntStateOf(0) }
    var consentDialog by remember { mutableStateOf(false) }
    var notificationForConsent by remember { mutableStateOf(false) }
    var notificationForAlerts by remember { mutableStateOf(false) }
    var permissionMessage by remember { mutableStateOf("") }

    val startSharing: () -> Unit = {
        scope.launch {
            if (!NotificationHelper.canShowForegroundNotice(context)) {
                errorText = "Enable OUR CHAT notifications first so Android can display the required monitoring notice."
                return@launch
            }
            busy = true
            try {
                api.setConsent(true)
                preferences.sharingActive = true
                preferences.pendingRevocation = false
                MonitoringService.start(context)
                errorText = ""
                refreshKey++
            } catch (error: Exception) {
                preferences.sharingActive = false
                preferences.pendingRevocation = true
                runCatching {
                    api.setConsent(false)
                    preferences.pendingRevocation = false
                }
                errorText = error.message ?: "Could not start connection sharing."
            } finally {
                busy = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        when {
            notificationForConsent -> {
                notificationForConsent = false
                if (granted) {
                    startSharing()
                } else {
                    errorText = "Connection sharing needs notification permission so Android can show its required ongoing notice."
                }
            }
            notificationForAlerts -> {
                notificationForAlerts = false
                permissionMessage = if (granted) {
                    "Event alerts are enabled on this device while OUR CHAT can connect."
                } else {
                    "Notifications are off. You can still review the saved event history."
                }
            }
        }
    }

    suspend fun refreshData() {
        if (preferences.token.isBlank()) return
        val state = api.loadState()
        role = state.optString("role", preferences.role)
        val accountState = state.optJSONObject("accounts")
        if (accountState != null) {
            setup = AccountSetup.fromJson(accountState)
            username = if (role == "ADMIN") {
                setup?.adminUsername.orEmpty()
            } else {
                setup?.guestUsername.orEmpty()
            }
            preferences.username = username
        }
        peerOnline = state.optBoolean("peerOnline")
        showStatusButton = state.optBoolean("showStatusButton")
        guestStatus = GuestStatus.fromJson(state)
        messages = api.loadMessages()
        if (role == "ADMIN" && page == "status") {
            dashboard = api.dashboard()
        }
    }

    LaunchedEffect(serverUrl) {
        if (serverUrl.isNotBlank() && preferences.token.isNotBlank()) {
            try {
                refreshData()
            } catch (_: Exception) {
                // The chat surface keeps the last known state while the server is unreachable.
            }
        }
    }

    LaunchedEffect(Unit) {
        if (serverUrl.isNotBlank()) {
            try {
                preferences.serverUrl = serverUrl
                setup = api.setupStatus()
            } catch (_: Exception) {
                // Connection errors are shown when the user taps Connect.
            }
        }
    }

    LaunchedEffect(preferences.token) {
        while (true) {
            delay(15_000)
            refreshKey++
            if (preferences.pendingRevocation && preferences.token.isNotBlank()) {
                try {
                    api.setConsent(false)
                    preferences.pendingRevocation = false
                } catch (_: Exception) {
                    // Keep the local stop in effect and retry on the next app refresh.
                }
            }
        }
    }

    LaunchedEffect(preferences.token, refreshKey, page) {
        if (preferences.token.isBlank()) return@LaunchedEffect
        try {
            refreshData()
            errorText = ""
        } catch (error: Exception) {
            if (errorText.isBlank()) {
                errorText = error.message ?: "The server could not be reached. Last known data is still shown."
            }
        }
    }

    DisposableEffect(preferences.token, serverUrl) {
        val token = preferences.token
        if (token.isBlank() || serverUrl.isBlank()) {
            onDispose { }
        } else {
            val socket = try {
                val options = IO.Options().apply {
                    path = "/api/socket.io"
                    auth = mapOf("token" to token)
                    reconnection = true
                    reconnectionAttempts = Int.MAX_VALUE
                    reconnectionDelay = 1000
                    reconnectionDelayMax = 10_000
                    timeout = 15_000
                }
                IO.socket(api.socketOrigin(), options)
            } catch (_: Exception) {
                null
            }

            if (socket != null) {
                val onChange = { _: Array<out Any> -> refreshKey++ }
                socket.on(Socket.EVENT_CONNECT, onChange)
                socket.on("message:new", onChange)
                socket.on("message:updated", onChange)
                socket.on("state:update", onChange)
                socket.on("control:update", onChange)
                socket.on("monitoring:update", onChange)
                socket.on("event:new") { args ->
                    val event = args.firstOrNull() as? JSONObject
                    if (preferences.role == "ADMIN" && event != null) {
                        val type = event.optString("type")
                        val title = eventTitle(type)
                        if (title.isNotBlank()) {
                            NotificationHelper.showAdminEvent(
                                context,
                                title,
                                eventDescription(type, event.optJSONObject("detail")),
                            )
                        }
                    }
                    refreshKey++
                }
                socket.on("presence") { args ->
                    val presence = args.firstOrNull() as? JSONObject ?: return@on
                    peerOnline = presence.optBoolean(
                        if (preferences.role == "ADMIN") "GUEST" else "ADMIN",
                    )
                }
                socket.on(Socket.EVENT_CONNECT_ERROR) {
                    if (errorText.isBlank()) {
                        errorText = "Live connection unavailable. OUR CHAT will keep retrying."
                    }
                }
                socket.connect()
                onDispose {
                    socket.disconnect()
                    socket.off()
                    socket.close()
                }
            } else {
                onDispose { }
            }
        }
    }

    OurChatTheme {
        if (preferences.token.isBlank()) {
            LoginScreen(
                serverUrl = serverUrl,
                onServerUrlChange = {
                    serverUrl = it
                    preferences.serverUrl = it
                    setup = null
                },
                setup = setup,
                busy = busy,
                errorText = errorText,
                onConnect = {
                    scope.launch {
                        busy = true
                        errorText = ""
                        preferences.serverUrl = serverUrl
                        try {
                            setup = api.setupStatus()
                        } catch (error: Exception) {
                            errorText = error.message ?: "Could not connect to this server."
                        } finally {
                            busy = false
                        }
                    }
                },
                onSetup = { adminName, guestName ->
                    scope.launch {
                        busy = true
                        errorText = ""
                        try {
                            setup = api.setupAccounts(adminName, guestName)
                            username = adminName
                            val result = api.login(adminName)
                            role = result.optString("role")
                            setup = AccountSetup.fromJson(result)
                            refreshKey++
                        } catch (error: Exception) {
                            errorText = error.message ?: "Initial setup could not be completed."
                        } finally {
                            busy = false
                        }
                    }
                },
                onLogin = { enteredUsername ->
                    scope.launch {
                        busy = true
                        errorText = ""
                        try {
                            val result = api.login(enteredUsername)
                            username = enteredUsername
                            role = result.optString("role")
                            setup = AccountSetup.fromJson(result)
                            refreshKey++
                        } catch (error: Exception) {
                            errorText = error.message ?: "Could not sign in."
                        } finally {
                            busy = false
                        }
                    }
                },
            )
        } else {
            OurChatMainScreen(
                role = role.ifBlank { preferences.role },
                username = username.ifBlank { preferences.username },
                setup = setup,
                messages = messages,
                peerOnline = peerOnline,
                guestStatus = guestStatus,
                showStatusButton = showStatusButton,
                dashboard = dashboard,
                page = page,
                busy = busy,
                errorText = errorText,
                permissionMessage = permissionMessage,
                onPageChange = {
                    page = it
                    refreshKey++
                },
                onSend = { text ->
                    scope.launch {
                        busy = true
                        try {
                            api.sendMessage(text, "${System.currentTimeMillis()}-${text.hashCode()}")
                            refreshKey++
                            errorText = ""
                        } catch (error: Exception) {
                            errorText = error.message ?: "Message could not be sent."
                        } finally {
                            busy = false
                        }
                    }
                },
                onRead = { id ->
                    scope.launch {
                        runCatching { api.markRead(id) }
                    }
                },
                onShowStatus = { consentDialog = true },
                onOpenBatterySettings = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                        )
                    }.onFailure {
                        errorText = "Battery settings could not be opened on this device."
                    }
                },
                onSetStatusButton = { visible ->
                    scope.launch {
                        busy = true
                        try {
                            api.setStatusButton(visible)
                            refreshKey++
                        } catch (error: Exception) {
                            errorText = error.message ?: "Could not update the guest control."
                        } finally {
                            busy = false
                        }
                    }
                },
                onRenameGuest = { guestName ->
                    scope.launch {
                        busy = true
                        try {
                            api.renameGuest(guestName)
                            refreshKey++
                        } catch (error: Exception) {
                            errorText = error.message ?: "Could not change the GUEST username."
                        } finally {
                            busy = false
                        }
                    }
                },
                onRevokeSharing = {
                    preferences.sharingActive = false
                    preferences.pendingRevocation = true
                    MonitoringService.stop(context)
                    scope.launch {
                        try {
                            api.setConsent(false)
                            preferences.pendingRevocation = false
                            errorText = "Connection sharing is off."
                            refreshKey++
                        } catch (_: Exception) {
                            errorText = "Sharing stopped on this device. The server will show STALE until it reconnects."
                        }
                    }
                },
                onEnableAlerts = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationForAlerts = true
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        permissionMessage = "Event notifications are available on this device while OUR CHAT is connected."
                    }
                },
            )
        }
    }

    if (consentDialog) {
        ConsentDialog(
            onDismiss = { consentDialog = false },
            onAgree = {
                consentDialog = false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    NotificationHelper.createChannels(context)
                    notificationForConsent = true
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    startSharing()
                }
            },
        )
    }
}

private fun eventTitle(type: String): String = when (type) {
    "internet_connected" -> "Guest internet connected"
    "internet_disconnected" -> "Guest internet disconnected"
    "internet_restored" -> "Guest internet restored"
    "network_type_changed" -> "Guest network changed"
    "messenger_opened" -> "OUR CHAT opened on GUEST"
    "messenger_backgrounded" -> "OUR CHAT moved to background"
    "monitoring_service_started" -> "Connection sharing service started"
    "monitoring_service_stopped" -> "Connection sharing service stopped"
    "monitoring_service_recovered" -> "Connection sharing service recovered"
    "sharing_activated" -> "GUEST started sharing"
    "sharing_revoked" -> "GUEST stopped sharing"
    "permissions_revoked" -> "A monitoring permission changed"
    "server_connection_lost" -> "Guest server connection lost"
    "server_connection_restored" -> "Guest server connection restored"
    "device_restarted" -> "Guest device restarted"
    "battery_restriction_detected" -> "Guest battery restriction detected"
    else -> ""
}

private fun eventDescription(type: String, detail: JSONObject?): String = when (type) {
    "network_type_changed" ->
        "Network changed from ${detail?.optString("from", "unknown")} to ${detail?.optString("to", "unknown")}."
    "permissions_revoked" ->
        "The ${detail?.optString("permission", "required")} permission is no longer available."
    "battery_restriction_detected" ->
        "Android reports a background restriction or battery limit."
    else -> eventTitle(type)
}
