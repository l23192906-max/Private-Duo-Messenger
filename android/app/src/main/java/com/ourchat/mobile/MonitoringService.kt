package com.ourchat.mobile

import android.app.ActivityManager
import android.app.NotificationManager
import android.app.PowerManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class MonitoringService : Service() {
    private lateinit var preferences: OurChatPreferences
    private lateinit var api: ChatApi
    private lateinit var connectivity: ConnectivityManager
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var serverWasUnavailable = false
    private var reportRecoveryAfterHeartbeat = false

    override fun onCreate() {
        super.onCreate()
        preferences = OurChatPreferences(this)
        api = ChatApi(this)
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!preferences.sharingActive || preferences.token.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        NotificationHelper.createChannels(this)
        if (!NotificationHelper.canShowForegroundNotice(this)) {
            serviceScope.launch {
                runCatching {
                    api.reportEvent(
                        "permissions_revoked",
                        JSONObject().put("permission", "notifications"),
                    )
                }
            }
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(
            NotificationHelper.SERVICE_NOTIFICATION_ID,
            NotificationHelper.monitoringNotification(this),
        )
        reportRecoveryAfterHeartbeat =
            intent?.action == ACTION_RECOVERY || intent == null
        registerNetworkChanges()
        serviceScope.launch {
            while (isActive) {
                sendHeartbeat()
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
        return START_STICKY
    }

    private fun registerNetworkChanges() {
        if (networkCallback != null) return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                sendImmediateHeartbeat()
            }

            override fun onLost(network: Network) {
                sendImmediateHeartbeat()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities,
            ) {
                sendImmediateHeartbeat()
            }
        }
        networkCallback = callback
        runCatching { connectivity.registerDefaultNetworkCallback(callback) }
            .onFailure {
                serviceScope.launch {
                    runCatching {
                        api.reportEvent(
                            "permissions_revoked",
                            JSONObject().put("permission", "network_state"),
                        )
                    }
                }
            }
    }

    private fun sendImmediateHeartbeat() {
        serviceScope.launch { sendHeartbeat() }
    }

    private suspend fun sendHeartbeat() {
        if (!preferences.sharingActive) return
        try {
            if (!NotificationHelper.canShowForegroundNotice(this)) {
                api.reportEvent(
                    "permissions_revoked",
                    JSONObject().put("permission", "notifications"),
                )
                stopSelf()
                return
            }
            val (internetStatus, networkType) = currentNetwork()
            val permissions = diagnostics()
            api.heartbeat(
                internetStatus = internetStatus,
                networkType = networkType,
                messengerForeground = preferences.messengerForeground,
                serviceActive = true,
                permissions = permissions,
            )
            if (serverWasUnavailable) {
                val unavailableSince = preferences.serverUnavailableSince
                if (unavailableSince.isNotBlank()) {
                    api.reportEvent(
                        "server_connection_lost",
                        JSONObject().put("startedAt", unavailableSince),
                    )
                }
                api.reportEvent("server_connection_restored")
                preferences.serverUnavailableSince = ""
            }
            if (reportRecoveryAfterHeartbeat) {
                api.reportEvent("monitoring_service_recovered")
                reportRecoveryAfterHeartbeat = false
            }
            serverWasUnavailable = false
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            serverWasUnavailable = true
            if (preferences.serverUnavailableSince.isBlank()) {
                preferences.serverUnavailableSince = java.time.Instant.now().toString()
            }
        }
    }

    private fun currentNetwork(): Pair<String, String> {
        val active = connectivity.activeNetwork ?: return "disconnected" to "none"
        val caps = connectivity.getNetworkCapabilities(active)
            ?: return "unknown" to "unknown"
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
        val internet = when {
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
                "disconnected"
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
                "connected"
            else -> "unknown"
        }
        return internet to type
    }

    private fun diagnostics(): JSONObject {
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        val activity = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val notificationsAllowed = NotificationHelper.canShowForegroundNotice(this)
        return JSONObject()
            .put("notificationsAllowed", notificationsAllowed)
            .put("batteryOptimizationExempt", power.isIgnoringBatteryOptimizations(packageName))
            .put(
                "backgroundRestricted",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activity.isBackgroundRestricted,
            )
            .put("networkStatePermission", true)
            .put("androidVersion", Build.VERSION.SDK_INT)
            .put("deviceManufacturer", Build.MANUFACTURER)
            .put("deviceModel", Build.MODEL)
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            runCatching { connectivity.unregisterNetworkCallback(callback) }
        }
        if (preferences.sharingActive) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    api.reportEvent("monitoring_service_stopped")
                }
            }
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 15_000L
        private const val ACTION_RECOVERY = "com.ourchat.mobile.MONITORING_RECOVERY"

        fun start(context: Context, recovery: Boolean = false) {
            val intent = Intent(context, MonitoringService::class.java).apply {
                if (recovery) action = ACTION_RECOVERY
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringService::class.java))
        }
    }
}
