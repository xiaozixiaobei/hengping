package com.hengping.standby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarVisualsTest {
    @Test
    fun glassSoftensBothSidesOfAHighContrastEdgeWithoutChangingThePhoto() {
        val pixels = IntArray(25) { if (it % 5 < 2) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val blurred = CalendarGlass.blur(pixels, 5, 5, 1)

        assertTrue((blurred[11] and 255) in 1..254)
        assertTrue((blurred[12] and 255) in 1..254)
        assertEquals(0xFF000000.toInt(), pixels[11])
        assertEquals(0xFFFFFFFF.toInt(), pixels[12])
    }

    @Test
    fun glassAlsoSoftensHorizontalEdgesInNarrowPhotos() {
        val pixels = intArrayOf(0xFF000000.toInt(), 0xFF000000.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt())
        val blurred = CalendarGlass.blur(pixels, 1, 5, 1)

        assertTrue((blurred[1] and 255) in 1..254)
        assertTrue((blurred[2] and 255) in 1..254)
    }

    @Test
    fun glassPreservesSolidColorAndDoesNotWrapOppositeEdges() {
        val solid = IntArray(25) { 0xFF3A729C.toInt() }
        assertArrayEquals(solid, CalendarGlass.blur(solid, 5, 5, 4))

        val edge = IntArray(17) { if (it == 0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt() }
        assertEquals(0xFF000000.toInt(), CalendarGlass.blur(edge, 17, 1, 1).last())
    }

    @Test
    fun unknownSavedBackgroundFallsBackToTransparent() {
        assertEquals(CalendarBackground.NONE, CalendarBackground.from("future-mode"))
    }

    @Test
    fun lightPhotoUsesDarkCalendarText() {
        assertTrue(CalendarContrast.shouldUseDarkText(0.78f))
    }

    @Test
    fun darkPhotoKeepsLightCalendarText() {
        assertFalse(CalendarContrast.shouldUseDarkText(0.3f))
    }

    @Test
    fun perceivedLuminanceUsesWeightedChannels() {
        assertEquals(1f, CalendarContrast.luminance(255, 255, 255), 0.001f)
        assertEquals(0f, CalendarContrast.luminance(0, 0, 0), 0.001f)
        assertEquals(0.2126f, CalendarContrast.luminance(255, 0, 0), 0.001f)
    }

    @Test
    fun centeredCropScaleFillsBothViewDimensions() {
        assertEquals(2f, CalendarContrast.centeredCropScale(500, 250, 1000, 400), 0.001f)
        assertEquals(4f, CalendarContrast.centeredCropScale(500, 250, 1000, 1000), 0.001f)
    }

    @Test
    fun pageFlipOnlyShowsTheOldPageBeforeMidpoint() {
        val oldPage = CalendarPageMotion.flip(direction = 1, progress = 0.25f)
        val newPage = CalendarPageMotion.flip(direction = 1, progress = 0.25f, incoming = true)

        assertTrue(oldPage.visible)
        assertFalse(newPage.visible)
    }

    @Test
    fun pageFlipOnlyShowsTheNewPageAfterMidpoint() {
        val oldPage = CalendarPageMotion.flip(direction = -1, progress = 0.75f)
        val newPage = CalendarPageMotion.flip(direction = -1, progress = 0.75f, incoming = true)

        assertFalse(oldPage.visible)
        assertTrue(newPage.visible)
    }

    @Test
    fun pageFlipStaysWithinRightAngleAndNeverMovesOutsideTheFrame() {
        val quarter = CalendarPageMotion.flip(direction = 1, progress = 0.25f)
        val halfway = CalendarPageMotion.flip(direction = 1, progress = 0.5f)
        val incoming = CalendarPageMotion.flip(direction = 1, progress = 0.75f, incoming = true)

        assertTrue(quarter.angle in 0f..90f)
        assertEquals(90f, halfway.angle, 0.001f)
        assertTrue(incoming.angle in -90f..0f)
    }
}
