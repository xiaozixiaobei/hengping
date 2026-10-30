package com.hengping.standby

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

class MonitorRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED)) return
        if (Prefs(context).autoStart && Settings.canDrawOverlays(context)) {
            try {
                context.startForegroundService(Intent(context, ChargingMonitorService::class.java))
            } catch (error: RuntimeException) {
                Log.w("ChargingStandby", "Open the app to resume charging monitoring", error)
            }
        }
    }
}
