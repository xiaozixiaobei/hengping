package com.hengping.standby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log

class ChargingMonitorService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { Prefs(this) }
    private val power by lazy { getSystemService(PowerManager::class.java) }
    private val launchWakeLock by lazy {
        power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "hengping:charging-launch").apply {
            setReferenceCounted(false)
        }
    }
    private val openStandby = Runnable {
        try {
            if (canOpenStandby()) {
                startActivity(Intent(this, StandbyActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(StandbyActivity.EXTRA_AUTOMATIC, true))
            }
        } catch (error: RuntimeException) {
            Log.w("ChargingStandby", "Background display blocked; use the notification", error)
        } finally {
            if (launchWakeLock.isHeld) launchWakeLock.release()
        }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            handler.removeCallbacks(openStandby)
            if (launchWakeLock.isHeld) launchWakeLock.release()
            if (intent.action != Intent.ACTION_POWER_DISCONNECTED) scheduleStandby()
        }
    }

    override fun onCreate() {
        super.onCreate()
        val notifications = getSystemService(NotificationManager::class.java)
        notifications.createNotificationChannel(NotificationChannel(
            CHANNEL, getString(R.string.charging_display), NotificationManager.IMPORTANCE_LOW,
        ))
        val open = PendingIntent.getActivity(this, 0,
            Intent(this, StandbyActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, ChargingMonitorService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        startForeground(1, Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_standby)
            .setContentTitle(getString(R.string.monitor_title))
            .setContentText(getString(R.string.monitor_hint))
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop_auto_start), stop).build())
            .build())
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || !prefs.autoStart) {
            prefs.autoStart = false
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_ENABLE || intent == null || !power.isInteractive ||
            getSystemService(KeyguardManager::class.java).isKeyguardLocked
        ) {
            scheduleStandby()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (launchWakeLock.isHeld) launchWakeLock.release()
        unregisterReceiver(receiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun canOpenStandby(): Boolean {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return prefs.autoStart && Settings.canDrawOverlays(this) &&
            (battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
    }

    private fun scheduleStandby() {
        handler.removeCallbacks(openStandby)
        if (launchWakeLock.isHeld) launchWakeLock.release()
        if (!canOpenStandby()) return
        launchWakeLock.acquire(5000L)
        handler.postDelayed(openStandby, 700L)
    }

    companion object {
        const val ACTION_ENABLE = "com.hengping.standby.ENABLE_MONITOR"
        private const val ACTION_STOP = "com.hengping.standby.STOP_MONITOR"
        private const val CHANNEL = "charging_standby"
    }
}
