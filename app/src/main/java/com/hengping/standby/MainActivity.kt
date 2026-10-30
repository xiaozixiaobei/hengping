package com.hengping.standby

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.view.View
import android.widget.Button
import android.widget.Switch
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val prefs by lazy { Prefs(this) }
    private var pendingAutoStart = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = getColor(R.color.surface)
        window.navigationBarColor = getColor(R.color.surface)

        findViewById<Switch>(R.id.show_seconds).apply {
            isChecked = prefs.showSeconds
            setOnCheckedChangeListener { _, checked -> prefs.showSeconds = checked }
        }
        findViewById<Switch>(R.id.dim_at_night).apply {
            isChecked = prefs.dimAtNight
            setOnCheckedChangeListener { _, checked -> prefs.dimAtNight = checked }
        }
        findViewById<RadioGroup>(R.id.calendar_background).apply {
            check(when (prefs.calendarBackground) {
                CalendarBackground.NONE -> R.id.calendar_background_none
                CalendarBackground.FROSTED -> R.id.calendar_background_frosted
                CalendarBackground.LIQUID -> R.id.calendar_background_liquid
            })
            setOnCheckedChangeListener { _, checkedId ->
                prefs.calendarBackground = when (checkedId) {
                    R.id.calendar_background_frosted -> CalendarBackground.FROSTED
                    R.id.calendar_background_liquid -> CalendarBackground.LIQUID
                    else -> CalendarBackground.NONE
                }
            }
        }
        findViewById<Button>(R.id.choose_background).setOnClickListener {
            startActivityForResult(
                Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    type = "image/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                PICK_BACKGROUND,
            )
        }
        findViewById<Button>(R.id.clear_background).setOnClickListener {
            prefs.backgroundUri = null
            renderBackgroundPreview()
        }
        findViewById<View>(R.id.background_preview).setOnClickListener {
            findViewById<Button>(R.id.choose_background).performClick()
        }
        findViewById<Button>(R.id.open_standby).setOnClickListener {
            startActivity(Intent(this, StandbyActivity::class.java))
        }
        findViewById<Button>(R.id.dream_settings).setOnClickListener {
            openDreamSettings()
        }
        findViewById<Switch>(R.id.auto_start).apply {
            isChecked = prefs.autoStart
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    enableAutoStart()
                } else {
                    prefs.autoStart = false
                    stopService(Intent(this@MainActivity, ChargingMonitorService::class.java))
                }
            }
        }
        findViewById<Button>(R.id.background_settings).setOnClickListener {
            Toast.makeText(this, R.string.background_permission_hint, Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName")))
        }
    }

    override fun onResume() {
        super.onResume()
        findViewById<Switch>(R.id.show_seconds).isChecked = prefs.showSeconds
        findViewById<RadioGroup>(R.id.calendar_background).check(
            when (prefs.calendarBackground) {
                CalendarBackground.NONE -> R.id.calendar_background_none
                CalendarBackground.FROSTED -> R.id.calendar_background_frosted
                CalendarBackground.LIQUID -> R.id.calendar_background_liquid
            },
        )
        renderBackgroundPreview()
        val autoStart = findViewById<Switch>(R.id.auto_start)
        if (pendingAutoStart) {
            pendingAutoStart = false
            if (Settings.canDrawOverlays(this)) autoStart.isChecked = true
        }
        if (prefs.autoStart && !Settings.canDrawOverlays(this)) prefs.autoStart = false
        autoStart.isChecked = prefs.autoStart
        if (prefs.autoStart) {
            startForegroundService(Intent(this, ChargingMonitorService::class.java))
        }
    }

    private fun enableAutoStart() {
        if (!Settings.canDrawOverlays(this)) {
            pendingAutoStart = true
            findViewById<Switch>(R.id.auto_start).isChecked = false
            Toast.makeText(this, R.string.overlay_permission_hint, Toast.LENGTH_LONG).show()
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (_: android.content.ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 42)
        }
        prefs.autoStart = true
        startForegroundService(Intent(this, ChargingMonitorService::class.java)
            .setAction(ChargingMonitorService.ACTION_ENABLE))
    }

    @Deprecated("Activity result is enough for this single image picker.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_BACKGROUND && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                try {
                    contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                } catch (_: SecurityException) {
                    // Some document providers grant access without supporting persistence.
                }
                prefs.backgroundUri = uri.toString()
                renderBackgroundPreview()
                Toast.makeText(this, R.string.background_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openDreamSettings() {
        val component = ComponentName(this, StandbyDream::class.java)
        val intent = Intent(Settings.ACTION_DREAM_SETTINGS).apply {
            putExtra("android.settings.DREAM_COMPONENT", component)
        }
        try {
            startActivity(intent)
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
    }

    private fun renderBackgroundPreview() {
        val uriString = prefs.backgroundUri
        val image = findViewById<ImageView>(R.id.background_thumbnail)
        val placeholder = findViewById<View>(R.id.background_placeholder)
        val status = findViewById<TextView>(R.id.background_status)
        image.setImageDrawable(null)
        image.visibility = View.GONE
        placeholder.visibility = View.VISIBLE
        status.setText(R.string.photo_empty)
        if (uriString == null) return
        try {
            val uri = Uri.parse(uriString)
            val bitmap = if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, uri)) { decoder, info, _ ->
                    val sample = (maxOf(info.size.width, info.size.height) + 1279) / 1280
                    if (sample > 1) decoder.setTargetSampleSize(sample)
                }
            } else {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                options.inSampleSize = 1
                while (options.outWidth / options.inSampleSize > 1280 || options.outHeight / options.inSampleSize > 1280) {
                    options.inSampleSize *= 2
                }
                options.inJustDecodeBounds = false
                contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            }
            if (bitmap != null) {
                image.setImageBitmap(bitmap)
                image.visibility = View.VISIBLE
                placeholder.visibility = View.GONE
                status.setText(R.string.photo_selected)
            } else {
                status.setText(R.string.photo_unavailable)
            }
        } catch (_: Exception) {
            status.setText(R.string.photo_unavailable)
        }
    }

    companion object {
        private const val PICK_BACKGROUND = 41
    }
}
