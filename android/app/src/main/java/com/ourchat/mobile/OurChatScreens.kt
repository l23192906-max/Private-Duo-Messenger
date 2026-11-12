package com.ourchat.mobile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun LoginScreen(
    serverUrl: String,
    onServerUrlChange: (String) -> Unit,
    setup: AccountSetup?,
    busy: Boolean,
    errorText: String,
    onConnect: () -> Unit,
    onSetup: (String, String) -> Unit,
    onLogin: (String) -> Unit,
) {
    var adminName by remember { mutableStateOf("") }
    var guestName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "OUR CHAT",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "A private two-account chat. No public sign-up.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(22.dp),
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "Server address",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    OutlinedTextField(
                        value = serverUrl,
                        onValueChange = onServerUrlChange,
                        label = { Text("HTTPS server URL") },
                        placeholder = { Text("https://your-server.example") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = onConnect,
                        enabled = !busy && serverUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (busy) "Connecting…" else "Connect")
                    }

                    when {
                        setup == null -> {
                            Text(
                                "Connect to the server to continue.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        !setup.configured -> {
                            HorizontalDivider()
                            Text(
                                "First-time setup",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Choose the only two usernames. The first person to finish setup claims both account names, so initialize the server privately before sharing its address.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = adminName,
                                onValueChange = { adminName = it },
                                label = { Text("ADMIN username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = guestName,
                                onValueChange = { guestName = it },
                                label = { Text("GUEST username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { onSetup(adminName.trim(), guestName.trim()) },
                                enabled = !busy && adminName.isNotBlank() && guestName.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Create the two accounts")
                            }
                        }
                        else -> {
                            HorizontalDivider()
                            Text(
                                "Sign in",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Enter the username assigned to you. There are no passwords, codes, or additional accounts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedTextField(
                                value = username,
                                onValueChange = { username = it },
                                label = { Text("Username") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Button(
                                onClick = { onLogin(username.trim()) },
                                enabled = !busy && username.isNotBlank(),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (busy) "Signing in…" else "Continue")
                            }
                        }
                    }
                    if (errorText.isNotBlank()) {
                        Text(
                            errorText,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        "Security note: username-only sign-in does not prove identity. Anyone who knows a configured username may impersonate that account.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OurChatMainScreen(
    role: String,
    username: String,
    setup: AccountSetup?,
    messages: List<ChatMessage>,
    peerOnline: Boolean,
    guestStatus: GuestStatus,
    showStatusButton: Boolean,
    dashboard: JSONObject?,
    page: String,
    busy: Boolean,
    errorText: String,
    permissionMessage: String,
    onPageChange: (String) -> Unit,
    onSend: (String) -> Unit,
    onRead: (Long) -> Unit,
    onShowStatus: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onSetStatusButton: (Boolean) -> Unit,
    onRenameGuest: (String) -> Unit,
    onRevokeSharing: () -> Unit,
    onEnableAlerts: () -> Unit,
) {
    val isAdmin = role == "ADMIN"
    val peerName = if (isAdmin) setup?.guestUsername.orEmpty() else setup?.adminUsername.orEmpty()
    var renameDialog by remember { mutableStateOf(false) }
    var draftGuestName by remember(setup?.guestUsername) {
        mutableStateOf(setup?.guestUsername.orEmpty())
    }

    LaunchedEffect(messages, page, role) {
        if (page == "chat") {
            messages
                .filter { it.sender != role && it.readAt == null }
                .forEach { onRead(it.id) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (page == "status" && isAdmin) "Connection status" else peerName.ifBlank { "OUR CHAT" },
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (page == "chat") {
                            Text(
                                if (peerOnline) "Online" else "Offline",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peerOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                actions = {
                    Text(
                        username,
                        modifier = Modifier.padding(end = 16.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        },
        bottomBar = {
            if (isAdmin) {
                NavigationBar {
                    NavigationBarItem(
                        selected = page == "chat",
                        onClick = { onPageChange("chat") },
                        icon = { Text("C") },
                        label = { Text("Chat") },
                    )
                    NavigationBarItem(
                        selected = page == "status",
                        onClick = { onPageChange("status") },
                        icon = { Text("S") },
                        label = { Text("Status") },
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
        ) {
            if (errorText.isNotBlank()) {
                Text(
                    errorText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF1E8))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color(0xFF8A3E1C),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (permissionMessage.isNotBlank() && page == "status") {
                Text(
                    permissionMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when {
                page == "status" && isAdmin -> AdminDashboard(
                    dashboard = dashboard,
                    setup = setup,
                    buttonVisible = showStatusButton,
                    busy = busy,
                    onStatusButtonChange = onSetStatusButton,
                    onRenameClick = {
                        draftGuestName = setup?.guestUsername.orEmpty()
                        renameDialog = true
                    },
                    onEnableAlerts = onEnableAlerts,
                )
                else -> ChatView(
                    role = role,
                    messages = messages,
                    guestStatus = guestStatus,
                    showStatusButton = showStatusButton,
                    busy = busy,
                    onSend = onSend,
                    onShowStatus = onShowStatus,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onRevokeSharing = onRevokeSharing,
                )
            }
        }
    }

    if (renameDialog) {
        AlertDialog(
            onDismissRequest = { renameDialog = false },
            title = { Text("Change GUEST username") },
            text = {
                OutlinedTextField(
                    value = draftGuestName,
                    onValueChange = { draftGuestName = it },
                    label = { Text("New username") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = draftGuestName.isNotBlank() && !busy,
                    onClick = {
                        onRenameGuest(draftGuestName.trim())
                        renameDialog = false
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun ChatView(
    role: String,
    messages: List<ChatMessage>,
    guestStatus: GuestStatus,
    showStatusButton: Boolean,
    busy: Boolean,
    onSend: (String) -> Unit,
    onShowStatus: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onRevokeSharing: () -> Unit,
) {
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val isGuest = role == "GUEST"

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isGuest && guestStatus.sharingActive) {
            SharingNotice(
                status = guestStatus,
                onRevoke = onRevokeSharing,
                onBatterySettings = onOpenBatterySettings,
            )
        } else if (isGuest && showStatusButton) {
            Button(
                onClick = onShowStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                shape = RoundedCornerShape(15.dp),
            ) {
                Text("SHOW CONNECTION STATUS")
            }
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("No messages yet", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Send a message to start your conversation.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(messages, key = { it.id }) { message ->
                MessageBubble(message = message, ownMessage = message.sender == role)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .imePadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it.take(4000) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message") },
                maxLines = 5,
                shape = RoundedCornerShape(22.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = draft.isNotBlank() && !busy,
                onClick = {
                    val text = draft.trim()
                    if (text.isNotEmpty()) {
                        draft = ""
                        onSend(text)
                    }
                },
                shape = CircleShape,
                modifier = Modifier.size(54.dp),
            ) {
                Text("Send", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, ownMessage: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (ownMessage) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.84f)
                .clip(
                    RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (ownMessage) 18.dp else 5.dp,
                        bottomEnd = if (ownMessage) 5.dp else 18.dp,
                    ),
                )
                .background(
                    if (ownMessage) Color(0xFFD9F3EC) else MaterialTheme.colorScheme.surface,
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(message.body, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    formatTimestamp(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (ownMessage) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        when {
                            message.readAt != null -> "✓✓"
                            message.deliveredAt != null -> "✓✓"
                            else -> "✓"
                        },
                        color = if (message.readAt != null) Color(0xFF2588A4) else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharingNotice(
    status: GuestStatus,
    onRevoke: () -> Unit,
    onBatterySettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2F3ED)),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Connection sharing is active", fontWeight = FontWeight.SemiBold)
            Text(
                "Shared with ADMIN: ${status.networkType}, connection state, OUR CHAT foreground/background, service status, and update times.",
                style = MaterialTheme.typography.bodySmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Android’s ongoing notification stays visible.", style = MaterialTheme.typography.labelSmall)
                TextButton(onClick = onRevoke) { Text("Stop sharing") }
            }
            TextButton(onClick = onBatterySettings) {
                Text("Review battery settings")
            }
        }
    }
}

@Composable
fun ConsentDialog(onDismiss: () -> Unit, onAgree: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share connection status?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "If you agree, ADMIN can see whether this device reports an internet connection, Wi-Fi/mobile network type, whether OUR CHAT is foreground or background, monitoring-service health, server reachability, and timestamps.",
                )
                Text(
                    "Connection changes and app activity are saved as a timestamped history. No message text, location, contacts, or network passwords are collected.",
                )
                Text(
                    "Android will request notification permission so the required ongoing monitoring notice can be shown. You can stop sharing in the chat at any time. Android may still pause background work.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            Button(onClick = onAgree) { Text("Agree and continue") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Not now") }
        },
    )
}

@Composable
private fun AdminDashboard(
    dashboard: JSONObject?,
    setup: AccountSetup?,
    buttonVisible: Boolean,
    busy: Boolean,
    onStatusButtonChange: (Boolean) -> Unit,
    onRenameClick: () -> Unit,
    onEnableAlerts: () -> Unit,
) {
    val status = remember(dashboard) {
        dashboard?.let(GuestStatus::fromJson) ?: GuestStatus()
    }
    val daily = dashboard?.optJSONObject("connection")?.optJSONObject("day")
    val weekly = dashboard?.optJSONObject("connection")?.optJSONObject("week")

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(statusColor(status.state)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            status.state.replace('_', ' '),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = statusColor(status.state),
                        )
                    }
                    StatusLine("Network", status.networkType.uppercase())
                    StatusLine(
                        "Internet",
                        when (status.internetStatus) {
                            "connected" -> "Connected"
                            "disconnected" -> "Disconnected (reported)"
                            else -> "Unknown"
                        },
                    )
                    StatusLine(
                        "Server connectivity",
                        when (status.serverConnectivity) {
                            "connected" -> "Connected at last update"
                            "stale" -> "No recent server update"
                            else -> "Unknown"
                        },
                    )
                    StatusLine(
                        "OUR CHAT",
                        when (status.messengerForeground) {
                            true -> "Foreground"
                            false -> "Background"
                            null -> "Unknown"
                        },
                    )
                    StatusLine("Monitoring service", if (status.serviceActive) "Running" else "Not confirmed running")
                    StatusLine("Last update", status.lastUpdate?.let(::formatTimestamp) ?: "No update received")
                    if (status.state == "STALE") {
                        Text(
                            "No recent heartbeat. This is STALE, not proof that the device lost internet.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (status.state == "UNKNOWN") {
                        Text(
                            "No confirmed status has been received yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text("Guest controls", fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show status-sharing button")
                            Text(
                                "Hiding it does not end active sharing.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = buttonVisible,
                            onCheckedChange = onStatusButtonChange,
                            enabled = !busy,
                        )
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("GUEST username: ${setup?.guestUsername.orEmpty()}")
                        TextButton(onClick = onRenameClick) { Text("Change") }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard(
                    title = "Today online",
                    value = formatDuration(daily?.optLong("totalSeconds") ?: 0),
                    detail = "${daily?.optInt("interruptions") ?: 0} interruptions",
                    modifier = Modifier.weight(1f),
                )
                MetricCard(
                    title = "This week",
                    value = formatDuration(weekly?.optLong("totalSeconds") ?: 0),
                    detail = "${weekly?.optInt("interruptions") ?: 0} interruptions",
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Session summary", fontWeight = FontWeight.SemiBold)
                    Text("Today average: ${formatDuration(daily?.optLong("averageSessionSeconds") ?: 0)}")
                    Text("Today longest: ${formatDuration(daily?.optLong("longestSessionSeconds") ?: 0)}")
                    Text("Week average: ${formatDuration(weekly?.optLong("averageSessionSeconds") ?: 0)}")
                    Text("Week longest: ${formatDuration(weekly?.optLong("longestSessionSeconds") ?: 0)}")
                }
            }
        }

        item { SectionTitle("Connection history") }
        val connections = dashboard?.optJSONArray("connectionHistory") ?: JSONArray()
        if (connections.length() == 0) {
            item { EmptyHistory("No confirmed connection sessions yet.") }
        } else {
            items(connections.length()) { index ->
                val session = connections.optJSONObject(index) ?: JSONObject()
                HistoryCard(
                    title = if (session.optBoolean("ongoing")) "Current reported session" else "Connection session",
                    start = session.optString("startedAt"),
                    end = session.optString("endedAt").takeIf { it.isNotBlank() && it != "null" },
                    duration = session.optLong("durationSeconds", -1),
                )
            }
        }

        item { SectionTitle("OUR CHAT activity") }
        val activities = dashboard?.optJSONArray("activityHistory") ?: JSONArray()
        if (activities.length() == 0) {
            item { EmptyHistory("No foreground/background history yet.") }
        } else {
            items(activities.length()) { index ->
                val session = activities.optJSONObject(index) ?: JSONObject()
                HistoryCard(
                    title = if (session.optString("endedAt") == "null") "OUR CHAT currently foreground" else "OUR CHAT foreground session",
                    start = session.optString("startedAt"),
                    end = session.optString("endedAt").takeIf { it.isNotBlank() && it != "null" },
                    duration = -1,
                )
            }
        }

        item { SectionTitle("Device diagnostics and permissions") }
        item {
            DiagnosticsCard(status.permissions)
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Event alerts", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Local Android notifications work while the app can receive server events. They are not guaranteed if Android stops the app or the server is offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onEnableAlerts) { Text("Enable") }
            }
        }

        item { SectionTitle("Recent events") }
        val events = dashboard?.optJSONArray("events") ?: JSONArray()
        if (events.length() == 0) {
            item { EmptyHistory("No connection or sharing events have been recorded.") }
        } else {
            items(events.length()) { index ->
                val event = events.optJSONObject(index) ?: JSONObject()
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(eventLabel(event.optString("type")), fontWeight = FontWeight.Medium)
                        Text(
                            formatTimestamp(event.optString("createdAt")),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val detail = event.optJSONObject("detail")
                        if (detail != null && detail.length() > 0) {
                            Text(
                                detail.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

@Composable
private fun MetricCard(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F3F0)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        modifier = Modifier.padding(top = 8.dp, bottom = 1.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun EmptyHistory(message: String) {
    Text(
        message,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun HistoryCard(title: String, start: String, end: String?, duration: Long) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(
                "Started ${formatTimestamp(start)}",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                if (end == null) "End not confirmed" else "Ended ${formatTimestamp(end)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (duration >= 0) {
                Text(
                    "Observed duration ${formatDuration(duration)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(permissions: JSONObject) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            if (permissions.length() == 0) {
                Text(
                    "Waiting for the GUEST device to send diagnostics.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                val keys = listOf(
                    "notificationsAllowed",
                    "batteryOptimizationExempt",
                    "backgroundRestricted",
                    "networkStatePermission",
                    "androidVersion",
                    "deviceManufacturer",
                    "deviceModel",
                )
                keys.forEach { key ->
                    if (!permissions.isNull(key)) {
                        StatusLine(
                            diagnosticLabel(key),
                            permissions.opt(key)?.toString() ?: "Unknown",
                        )
                    }
                }
            }
        }
    }
}

private fun diagnosticLabel(key: String) = when (key) {
    "notificationsAllowed" -> "Notifications allowed"
    "batteryOptimizationExempt" -> "Battery optimization exempt"
    "backgroundRestricted" -> "Background restricted"
    "networkStatePermission" -> "Network state available"
    "androidVersion" -> "Android API"
    "deviceManufacturer" -> "Manufacturer"
    "deviceModel" -> "Device model"
    else -> key
}

private fun statusColor(state: String) = when (state) {
    "ONLINE" -> Color(0xFF17834A)
    "OFFLINE_CONFIRMED" -> Color(0xFFB42318)
    "STALE" -> Color(0xFFB76E00)
    else -> Color(0xFF687975)
}

private fun eventLabel(type: String) = when (type) {
    "internet_connected" -> "Internet connection detected"
    "internet_disconnected" -> "Internet disconnection reported"
    "internet_restored" -> "Internet connection restored"
    "network_type_changed" -> "Network type changed"
    "messenger_opened" -> "OUR CHAT opened"
    "messenger_backgrounded" -> "OUR CHAT moved to background"
    "monitoring_service_started" -> "Monitoring service started"
    "monitoring_service_stopped" -> "Monitoring service stopped"
    "monitoring_service_recovered" -> "Monitoring service recovered"
    "sharing_activated" -> "GUEST activated sharing"
    "sharing_revoked" -> "GUEST revoked sharing"
    "permissions_revoked" -> "Monitoring permission changed"
    "server_connection_lost" -> "Server connection lost"
    "server_connection_restored" -> "Server connection restored"
    "device_restarted" -> "Device restart detected"
    "battery_restriction_detected" -> "Battery restriction detected"
    "guest_username_changed" -> "GUEST username changed"
    "status_button_changed" -> "Status button visibility changed"
    "accounts_initialized" -> "OUR CHAT accounts initialized"
    else -> type.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

private fun formatTimestamp(value: String): String {
    if (value.isBlank() || value == "null") return "Unknown"
    return try {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, HH:mm"))
    } catch (_: Exception) {
        value
    }
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        safe >= 60 -> "${minutes}m"
        else -> "${safe}s"
    }
}
