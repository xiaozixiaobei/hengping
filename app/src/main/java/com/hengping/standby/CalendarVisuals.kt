package com.hengping.standby

enum class CalendarBackground {
    NONE,
    FROSTED,
    LIQUID;

    companion object {
        fun from(value: String?): CalendarBackground =
            values().firstOrNull { it.name == value } ?: NONE
    }
}

object CalendarContrast {
    private const val DARK_TEXT_LUMINANCE_THRESHOLD = 0.55f

    fun shouldUseDarkText(averageLuminance: Float): Boolean =
        averageLuminance > DARK_TEXT_LUMINANCE_THRESHOLD

    fun luminance(red: Int, green: Int, blue: Int): Float =
        (0.2126f * red + 0.7152f * green + 0.0722f * blue) / 255f

    fun centeredCropScale(
        imageWidth: Int,
        imageHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
    ): Float = maxOf(
        viewWidth.toFloat() / imageWidth,
        viewHeight.toFloat() / imageHeight,
    )
}

object CalendarGlass {
    fun blur(pixels: IntArray, width: Int, height: Int, radius: Int): IntArray {
        var source = pixels.copyOf()
        var target = IntArray(pixels.size)
        val count = radius * 2 + 1
        repeat(3) {
            for (horizontal in listOf(true, false)) {
                for (y in 0 until height) {
                    for (x in 0 until width) {
                        var red = 0
                        var green = 0
                        var blue = 0
                        for (offset in -radius..radius) {
                            val sx = if (horizontal) (x + offset).coerceIn(0, width - 1) else x
                            val sy = if (horizontal) y else (y + offset).coerceIn(0, height - 1)
                            val color = source[sy * width + sx]
                            red += color ushr 16 and 255
                            green += color ushr 8 and 255
                            blue += color and 255
                        }
                        target[y * width + x] = (255 shl 24) or
                            ((red / count) shl 16) or ((green / count) shl 8) or (blue / count)
                    }
                }
                val previous = source
                source = target
                target = previous
            }
        }
        return source
    }
}

object CalendarPageMotion {
    data class Flip(val visible: Boolean, val angle: Float)

    fun flip(direction: Int, progress: Float, incoming: Boolean = false): Flip {
        val fraction = progress.coerceIn(0f, 1f)
        val sign = if (direction < 0) -1f else 1f
        return if (incoming) {
            Flip(visible = fraction > 0.5f, angle = sign * (-90f + (fraction - 0.5f) * 180f))
        } else {
            Flip(visible = fraction <= 0.5f, angle = sign * (fraction * 180f))
        }
    }
}
