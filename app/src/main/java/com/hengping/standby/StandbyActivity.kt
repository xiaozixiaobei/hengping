package com.hengping.standby

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager

class StandbyActivity : Activity() {
    private var controller: StandbyController? = null
    private var watchingPower = false
    private val unplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_DISCONNECTED) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val automatic = intent.getBooleanExtra(EXTRA_AUTOMATIC, false)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(automatic)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
            if (automatic) window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        if (automatic) {
            val filter = IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
            if (Build.VERSION.SDK_INT >= 33) {
                registerReceiver(unplugReceiver, filter, RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(unplugReceiver, filter)
            }
            watchingPower = true
        }
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        setContentView(R.layout.activity_standby)
        controller = StandbyController(this, findViewById(R.id.standby_root), window).also { it.bind() }
    }

    override fun onResume() {
        super.onResume()
        if (watchingPower) {
            val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if ((battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) == 0) {
                finish()
                return
            }
        }
        controller?.refresh()
    }

    override fun onDestroy() {
        if (watchingPower) unregisterReceiver(unplugReceiver)
        controller?.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_AUTOMATIC = "automaticChargingStandby"
    }
}
