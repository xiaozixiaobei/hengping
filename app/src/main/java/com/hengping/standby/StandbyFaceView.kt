package com.hengping.standby

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Camera
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.View
import android.view.MotionEvent
import android.view.ViewConfiguration
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sin

class StandbyFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val calendarTypeface = Typeface.create("sans-serif-black", Typeface.NORMAL)
    val calendarPanelBounds = RectF(404f, -24f, 832f, 384f)
    private var calendarBackdrop: Bitmap? = null
    private var time = LocalTime.now()
    private var date = LocalDate.now()
    private var calendar = CalendarState(date)
    private var previousCalendar: CalendarState? = null
    private var calendarBackground = CalendarBackground.NONE
    private var calendarDarkText = false
    private var calendarAnimator: ValueAnimator? = null
    private var calendarPageProgress = 1f
    private var calendarPageDirection = 1
    private val calendarCamera = Camera()
    private var contentScale = 1f
    private var contentLeft = 0f
    private var contentTop = 0f
    private var touchX = 0f
    private var touchY = 0f
    private var showSeconds = true
    private val white = Color.rgb(245, 245, 247)
    private val gray = Color.rgb(142, 142, 147)
    private val red = Color.rgb(255, 59, 65)
    private val orange = Color.rgb(255, 159, 10)

    init {
        isClickable = true
    }

    fun update(
        time: LocalTime,
        date: LocalDate,
        showSeconds: Boolean,
        background: CalendarBackground = calendarBackground,
    ) {
        this.time = time
        this.showSeconds = showSeconds
        this.calendarBackground = background
        if (this.date != date) {
            this.date = date
            calendar.updateToday(date)
        }
        updateDescription()
        invalidate()
    }

    fun updateCalendarBackdrop(luminance: Float?, bitmap: Bitmap? = null) {
        calendarBackdrop?.recycle()
        calendarBackdrop = bitmap
        calendarDarkText = luminance != null && CalendarContrast.shouldUseDarkText(luminance)
        invalidate()
    }

    fun contentScale(): Float {
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        val scale = min(availableWidth * 0.84f / 800f, availableHeight * 0.82f / 370f)
        contentScale = scale
        contentLeft = paddingLeft + (availableWidth - 800f * scale) / 2f
        contentTop = paddingTop + (availableHeight - 370f * scale) / 2f
        return scale
    }

    fun contentLeft(): Float = contentLeft

    fun contentTop(): Float = contentTop

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scale = contentScale()
        if (scale <= 0f) return
        canvas.save()
        canvas.translate(contentLeft, contentTop)
        canvas.scale(scale, scale)
        drawClock(canvas)
        drawCalendar(canvas)
        canvas.restore()
    }

    private fun drawClock(canvas: Canvas) {
        canvas.save()
        canvas.translate(180f, 185f)
        paint.strokeCap = Paint.Cap.ROUND
        for (mark in 0 until 60) {
            canvas.save()
            canvas.rotate(mark * 6f)
            paint.color = if (mark % 5 == 0) white else Color.rgb(91, 91, 94)
            paint.strokeWidth = 3.8f
            canvas.drawLine(0f, -174f, 0f, if (mark % 5 == 0) -163f else -165f, paint)
            canvas.restore()
        }
        for (hour in 1..12) {
            val angle = Math.toRadians(hour * 30.0 - 90.0)
            drawText(canvas, hour.toString(), (cos(angle) * 136).toFloat(),
                (sin(angle) * 136).toFloat(), 42f, white)
        }
        val minute = time.minute + time.second / 60f
        val hour = time.hour % 12 + minute / 60f
        drawHand(canvas, hour * 30f, 94f, 11f)
        drawHand(canvas, minute * 6f, 157f, 10f)
        paint.color = white
        canvas.drawCircle(0f, 0f, 10f, paint)
        if (showSeconds) {
            drawHand(canvas, time.second * 6f, 174f, 2.4f, orange, 28f)
            paint.color = orange
            canvas.drawCircle(0f, 0f, 6.5f, paint)
        }
        paint.color = Color.BLACK
        canvas.drawCircle(0f, 0f, 3.5f, paint)
        canvas.restore()
    }

    private fun drawHand(
        canvas: Canvas,
        angle: Float,
        length: Float,
        width: Float,
        color: Int = white,
        tail: Float = 0f,
    ) {
        canvas.save()
        canvas.rotate(angle)
        paint.color = color
        paint.strokeWidth = width
        canvas.drawLine(0f, tail, 0f, -length, paint)
        canvas.restore()
    }

    private fun drawCalendar(canvas: Canvas) {
        if (calendarBackground != CalendarBackground.NONE) drawCalendarPanel(canvas)
        val oldCalendar = previousCalendar
        if (oldCalendar == null || calendarPageProgress >= 1f) {
            drawCalendarPage(canvas, calendar)
            return
        }
        val oldPage = CalendarPageMotion.flip(calendarPageDirection, calendarPageProgress)
        val newPage = CalendarPageMotion.flip(calendarPageDirection, calendarPageProgress, incoming = true)
        if (oldPage.visible) {
            drawCalendarPage(canvas, oldCalendar, oldPage.angle, 1f - calendarPageProgress * 1.4f)
        } else if (newPage.visible) {
            drawCalendarPage(canvas, calendar, newPage.angle, (calendarPageProgress - 0.5f) * 2f)
        }
    }

    private fun drawCalendarPanel(canvas: Canvas) {
        val bounds = calendarPanelBounds
        val liquid = calendarBackground == CalendarBackground.LIQUID
        val topCorner = if (liquid) 36f else 28f
        val bottomCorner = if (liquid) 48f else 44f
        val path = calendarPanelPath(bounds, topCorner, bottomCorner)
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL
        glassPaint.color = Color.WHITE
        canvas.save()
        canvas.clipPath(path)
        calendarBackdrop?.let { backdrop ->
            if (liquid) {
                val lens = RectF(bounds).apply { inset(-10f, -10f) }
                canvas.drawBitmap(backdrop, null, lens, glassPaint)
                canvas.save()
                val inner = RectF(bounds).apply { inset(7f, 7f) }
                val innerPath = calendarPanelPath(inner, topCorner - 7f, bottomCorner - 7f)
                canvas.clipPath(innerPath)
                canvas.drawBitmap(backdrop, null, bounds, glassPaint)
                canvas.restore()
            } else {
                canvas.drawBitmap(backdrop, null, bounds, glassPaint)
            }
        }
        val tint = when {
            liquid -> intArrayOf(0x30FFFFFF, 0x08FFFFFF, 0x22DDEAFF)
            calendarDarkText -> intArrayOf(0x78FFFFFF, 0x50F4F7FC)
            else -> intArrayOf(0x5C555E6B, 0x70323944)
        }
        glassPaint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            tint, null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, glassPaint)
        if (liquid) {
            glassPaint.shader = RadialGradient(bounds.left + 70f, bounds.top - 40f, 300f,
                0x38FFFFFF, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawPath(path, glassPaint)
        }
        canvas.restore()
        glassPaint.style = Paint.Style.STROKE
        glassPaint.strokeWidth = if (liquid) 1.8f else 0.8f
        glassPaint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            if (liquid) intArrayOf(0xDDFFFFFF.toInt(), 0x20FFFFFF, 0x08FFFFFF, 0xA0FFFFFF.toInt())
            else intArrayOf(0x60FFFFFF, 0x12FFFFFF), null, Shader.TileMode.CLAMP)
        canvas.drawPath(path, glassPaint)
        if (liquid) {
            val inner = RectF(bounds).apply { inset(2.5f, 2.5f) }
            val innerPath = calendarPanelPath(inner, topCorner - 2.5f, bottomCorner - 2.5f)
            glassPaint.strokeWidth = 0.75f
            glassPaint.shader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                0x18000000, 0x45FFFFFF, Shader.TileMode.CLAMP)
            canvas.drawPath(innerPath, glassPaint)
        }
        glassPaint.shader = null
        glassPaint.style = Paint.Style.FILL
    }

    private fun calendarPanelPath(bounds: RectF, topCorner: Float, bottomCorner: Float): Path =
        Path().apply {
            addRoundRect(
                bounds,
                floatArrayOf(
                    topCorner, topCorner,
                    topCorner, topCorner,
                    bottomCorner, bottomCorner,
                    bottomCorner, bottomCorner,
                ),
                Path.Direction.CW,
            )
        }

    private fun drawCalendarPage(
        canvas: Canvas,
        page: CalendarState,
        rotationY: Float = 0f,
        alpha: Float = 1f,
    ) {
        canvas.save()
        canvas.clipRect(calendarPanelBounds)
        if (rotationY != 0f) {
            calendarCamera.save()
            calendarCamera.rotateY(rotationY)
            canvas.translate(615f, 189f)
            calendarCamera.applyToCanvas(canvas)
            canvas.translate(-615f, -189f)
            calendarCamera.restore()
        }
        val pageAlpha = (alpha.coerceIn(0f, 1f) * 255).toInt()
        val month = "${page.month.year}年 ${page.month.monthValue}月"
        drawText(canvas, month, 440f, 20f, 28f, withAlpha(red, pageAlpha),
            Paint.Align.LEFT, true)
        val labels = listOf("日", "一", "二", "三", "四", "五", "六")
        val columnWidth = 360f / 7f
        labels.forEachIndexed { index, label ->
            drawText(canvas, label, 440f + (index + 0.5f) * columnWidth, 86f, 25f,
                withAlpha(if (index == 0 || index == 6) mutedInk() else ink(), pageAlpha), bold = true)
        }
        val rowHeight = 254f / (page.cells.size / 7)
        page.cells.forEachIndexed { index, cell ->
            if (!cell.inMonth) return@forEachIndexed
            val column = index % 7
            val x = 440f + (column + 0.5f) * columnWidth
            val y = 112f + (index / 7 + 0.5f) * rowHeight
            if (cell.isToday) {
                paint.color = withAlpha(red, pageAlpha)
                canvas.drawCircle(x, y, min(22f, rowHeight * 0.44f), paint)
            }
            drawText(canvas, cell.date.dayOfMonth.toString(), x, y, 27f,
                withAlpha(if (cell.isToday) white else if (column in 1..5) ink() else mutedInk(), pageAlpha),
                bold = true)
        }
        canvas.restore()
    }

    private fun ink(): Int = if (calendarDarkText) Color.rgb(28, 28, 30) else white

    private fun mutedInk(): Int = if (calendarDarkText) Color.rgb(97, 97, 102) else gray

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    private fun animateCalendar(direction: Int) {
        calendarAnimator?.cancel()
        previousCalendar = calendar.copy()
        calendar.moveMonth(direction)
        calendarPageDirection = direction
        calendarPageProgress = 0f
        calendarAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            addUpdateListener {
                calendarPageProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    previousCalendar = null
                    calendarPageProgress = 1f
                    invalidate()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchX = event.x
                touchY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = event.x - touchX
                val dy = event.y - touchY
                val x = (touchX - contentLeft) / contentScale
                val y = (touchY - contentTop) / contentScale
                val inCalendar = calendarPanelBounds.contains(x, y)
                val slop = ViewConfiguration.get(context).scaledTouchSlop
                if (inCalendar && abs(dx) > 40 * resources.displayMetrics.density && abs(dx) > abs(dy) * 1.5f) {
                    animateCalendar(if (dx < 0) 1 else -1)
                    updateDescription()
                } else if (abs(dx) < slop && abs(dy) < slop) {
                    if (inCalendar && y < 45f) {
                        calendarAnimator?.cancel()
                        previousCalendar = null
                        calendar.returnToToday()
                        updateDescription()
                        invalidate()
                    } else {
                        performClick()
                    }
                }
                return true
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        calendarAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    override fun performClick(): Boolean {
        super.performClick()
        (parent as? View)?.performClick()
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        return Bundle().apply {
            putParcelable("super", super.onSaveInstanceState())
            putString("month", calendar.month.toString())
        }
    }

    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.getParcelable<Parcelable>("super"))
        state.getString("month")?.let { calendar = CalendarState(date, YearMonth.parse(it)) }
        updateDescription()
        invalidate()
    }

    private fun updateDescription() {
        val clock = time.format(DateTimeFormatter.ofPattern(if (showSeconds) "HH:mm:ss" else "HH:mm"))
        contentDescription = "月历：${calendar.month.year}年${calendar.month.monthValue}月；" +
            "星期：日、一、二、三、四、五、六；今天：${date}；$clock"
    }

    private fun drawText(
        canvas: Canvas,
        text: String,
        x: Float,
        y: Float,
        size: Float,
        color: Int,
        align: Paint.Align = Paint.Align.CENTER,
        bold: Boolean = false,
    ) {
        paint.color = color
        paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) calendarTypeface else Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText(text, x, y - (paint.ascent() + paint.descent()) / 2f, paint)
    }
}
