package com.hengping.standby

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import java.time.LocalDateTime
import kotlin.math.max

class StandbyController(
    private val activity: Context,
    private val root: View,
    private val window: Window,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val prefs = Prefs(activity)
    private lateinit var face: StandbyFaceView
    private lateinit var controls: LinearLayout
    private var dimmed: Boolean? = null

    private val isActivity = activity is android.app.Activity
    private val backdropPreDraw = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            root.viewTreeObserver.removeOnPreDrawListener(this)
            updateCalendarBackdrop(root.findViewById(R.id.background_image))
            return true
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            renderFace()
            handler.postDelayed(this, 1000L - System.currentTimeMillis() % 1000L)
        }
    }

    fun bind() {
        face = root.findViewById(R.id.standby_face)
        controls = root.findViewById(R.id.controls)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        face.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 28) {
                val cutout = insets.displayCutout
                view.setPadding(
                    max(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0),
                    max(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0),
                    max(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0),
                    max(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0),
                )
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
        face.requestApplyInsets()

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        root.setOnClickListener { showControls(true) }
        root.findViewById<Button>(R.id.finish_preview).setOnClickListener {
            (activity as? android.app.Activity)?.finish()
        }
        root.findViewById<Button>(R.id.finish_preview).visibility =
            if (isActivity) View.VISIBLE else View.GONE
        root.findViewById<Button>(R.id.edit_settings).setOnClickListener {
            activity.startActivity(
                Intent(activity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        root.findViewById<Button>(R.id.toggle_seconds).setOnClickListener {
            prefs.showSeconds = !prefs.showSeconds
            renderFace()
            showControls(true)
        }
        refresh()
        handler.post(ticker)
    }

    fun refresh() {
        renderFace()
        renderBackground()
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        root.viewTreeObserver.removeOnPreDrawListener(backdropPreDraw)
    }

    private fun renderFace() {
        val now = LocalDateTime.now()
        face.update(
            now.toLocalTime(),
            now.toLocalDate(),
            prefs.showSeconds,
            prefs.calendarBackground,
        )
        val dim = prefs.dimAtNight && (now.hour >= 23 || now.hour < 7)
        if (dimmed != dim) {
            dimmed = dim
            window.attributes = window.attributes.apply { screenBrightness = if (dim) 0.18f else 0.75f }
        }
    }

    private fun renderBackground() {
        root.viewTreeObserver.removeOnPreDrawListener(backdropPreDraw)
        val image = root.findViewById<ImageView>(R.id.background_image)
        val uri = prefs.backgroundUri
        if (uri == null) {
            image.visibility = View.GONE
            face.updateCalendarBackdrop(null)
            return
        }
        try {
            image.setImageURI(Uri.parse(uri))
            image.visibility = if (image.drawable == null) View.GONE else View.VISIBLE
            root.viewTreeObserver.addOnPreDrawListener(backdropPreDraw)
        } catch (_: Exception) {
            image.visibility = View.GONE
            face.updateCalendarBackdrop(null)
        }
    }

    private fun updateCalendarBackdrop(image: ImageView) {
        val source = image.drawable as? BitmapDrawable ?: run {
            face.updateCalendarBackdrop(null)
            return
        }
        if (face.contentScale() <= 0f) {
            return
        }
        val bitmap = source.bitmap
        if (bitmap.width == 0 || bitmap.height == 0 || image.width == 0 || image.height == 0) {
            face.updateCalendarBackdrop(null)
            return
        }
        val scale = CalendarContrast.centeredCropScale(
            bitmap.width, bitmap.height, image.width, image.height,
        )
        val sourceCenterX = bitmap.width / 2f
        val sourceCenterY = bitmap.height / 2f
        val panel = face.calendarPanelBounds
        val faceScale = face.contentScale()
        val faceLeft = face.contentLeft()
        val faceTop = face.contentTop()
        val visibleCenterX = faceLeft + panel.centerX() * faceScale
        val visibleCenterY = faceTop + panel.centerY() * faceScale
        val sourceX = sourceCenterX + (visibleCenterX - image.width / 2f) / scale
        val sourceY = sourceCenterY + (visibleCenterY - image.height / 2f) / scale
        val halfWidth = (panel.width() * faceScale / scale / 2f).coerceAtLeast(1f)
        val halfHeight = (panel.height() * faceScale / scale / 2f).coerceAtLeast(1f)
        val crop = RectF(
            (sourceX - halfWidth).coerceIn(0f, bitmap.width.toFloat()),
            (sourceY - halfHeight).coerceIn(0f, bitmap.height.toFloat()),
            (sourceX + halfWidth).coerceIn(0f, bitmap.width.toFloat()),
            (sourceY + halfHeight).coerceIn(0f, bitmap.height.toFloat()),
        )
        if (crop.width() < 1f || crop.height() < 1f) {
            face.updateCalendarBackdrop(null)
            return
        }
        val mode = prefs.calendarBackground
        val sampleWidth = if (mode == CalendarBackground.LIQUID) 192 else 128
        val sampleHeight = (sampleWidth * panel.height() / panel.width()).toInt()
        val sample = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(
            bitmap,
            android.graphics.Rect(
                crop.left.toInt(),
                crop.top.toInt(),
                crop.right.toInt(),
                crop.bottom.toInt(),
            ),
            RectF(0f, 0f, sampleWidth.toFloat(), sampleHeight.toFloat()),
            android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG),
        )
        val pixels = IntArray(sampleWidth * sampleHeight)
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
        var sum = 0f
        for (color in pixels) {
            sum += CalendarContrast.luminance(Color.red(color), Color.green(color), Color.blue(color))
        }
        val luminance = sum / pixels.size
        if (mode == CalendarBackground.NONE) {
            sample.recycle()
            face.updateCalendarBackdrop(luminance)
        } else {
            val blurred = CalendarGlass.blur(pixels, sampleWidth, sampleHeight,
                if (mode == CalendarBackground.FROSTED) 4 else 1)
            sample.setPixels(blurred, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
            face.updateCalendarBackdrop(luminance, sample)
        }
    }

    private fun showControls(visible: Boolean) {
        controls.visibility = if (visible) View.VISIBLE else View.GONE
        handler.removeCallbacks(hideControls)
        if (visible) handler.postDelayed(hideControls, 4500L)
    }

    private val hideControls = Runnable { showControls(false) }
}
