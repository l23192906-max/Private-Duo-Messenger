package com.ourchat.mobile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val preferences = OurChatPreferences(context)
        if (!preferences.sharingActive || preferences.token.isBlank()) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching { ChatApi(context).reportEvent("device_restarted") }
                try {
                    MonitoringService.start(context, recovery = true)
                } catch (_: Exception) {
                    runCatching {
                        ChatApi(context).reportEvent("monitoring_service_stopped")
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
