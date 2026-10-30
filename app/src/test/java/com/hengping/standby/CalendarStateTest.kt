package com.hengping.standby

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CalendarStateTest {
    @Test
    fun restoringBrowsedMonthRetainsItAfterDateRefresh() {
        val state = CalendarState(LocalDate.of(2026, 10, 30), YearMonth.of(2027, 1))
        state.updateToday(LocalDate.of(2026, 10, 31))
        assertEquals(YearMonth.of(2027, 1), state.month)
        assertEquals(0, state.cells.count { it.isToday })
    }

    @Test
    fun movingForwardAndBackCrossesYearBoundary() {
        val state = CalendarState(LocalDate.of(2026, 12, 30))
        state.moveMonth(1)
        assertEquals(YearMonth.of(2027, 1), state.month)
        assertEquals(31, state.cells.count { it.inMonth })
        assertEquals(0, state.cells.count { it.isToday })
        state.moveMonth(-1)
        assertEquals(YearMonth.of(2026, 12), state.month)
        assertEquals(LocalDate.of(2026, 12, 30), state.cells.single { it.isToday }.date)
    }

    @Test
    fun clockUpdatesDoNotResetTheBrowsedMonth() {
        val state = CalendarState(LocalDate.of(2026, 10, 30))
        state.moveMonth(1)
        state.updateToday(LocalDate.of(2026, 10, 30))
        state.updateToday(LocalDate.of(2026, 10, 31))
        assertEquals(YearMonth.of(2026, 11), state.month)
    }

    @Test
    fun midnightFollowsTheNewMonthWhenShowingToday() {
        val state = CalendarState(LocalDate.of(2026, 10, 31))
        state.updateToday(LocalDate.of(2026, 11, 1))
        assertEquals(YearMonth.of(2026, 11), state.month)
        assertEquals(LocalDate.of(2026, 11, 1), state.cells.single { it.isToday }.date)
    }

    @Test
    fun returningToTodayRestoresHighlightAndSixWeekLayout() {
        val state = CalendarState(LocalDate.of(2026, 8, 31))
        state.moveMonth(-2)
        state.returnToToday()
        assertEquals(YearMonth.of(2026, 8), state.month)
        assertEquals(42, state.cells.size)
        assertEquals(LocalDate.of(2026, 8, 31), state.cells.single { it.isToday }.date)
    }
}
