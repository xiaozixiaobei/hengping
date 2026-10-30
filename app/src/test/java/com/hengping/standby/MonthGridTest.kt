package com.hengping.standby

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class MonthGridTest {
    @Test
    fun februaryStartsOnThursdayAndUsesSundayFirst() {
        val grid = MonthGrid.create(YearMonth.of(2024, 2), LocalDate.of(2024, 2, 14))

        assertEquals(35, grid.size)
        assertEquals(LocalDate.of(2024, 2, 1), grid.first { it.inMonth }.date)
        assertEquals(4, grid.indexOfFirst { it.date == LocalDate.of(2024, 2, 1) })
        assertEquals(true, grid.single { it.date == LocalDate.of(2024, 2, 14) }.isToday)
    }

    @Test
    fun february2026StartsWithSundayWithoutLosingWeekBoundary() {
        val grid = MonthGrid.create(YearMonth.of(2026, 2), LocalDate.of(2026, 2, 1))

        assertEquals(28, grid.size)
        assertEquals(0, grid.indexOfFirst { it.date == LocalDate.of(2026, 2, 1) })
    }

    @Test
    fun aprilMatchesReferenceWithWednesdayInFourthColumn() {
        val grid = MonthGrid.create(YearMonth.of(2026, 4), LocalDate.of(2026, 4, 1))

        assertEquals(35, grid.size)
        assertEquals(3, grid.indexOfFirst { it.date == LocalDate.of(2026, 4, 1) })
        assertEquals(LocalDate.of(2026, 4, 1), grid.single { it.isToday }.date)
    }

    @Test
    fun sixWeekMonthKeepsEveryDayInTheCorrectColumn() {
        val grid = MonthGrid.create(YearMonth.of(2026, 8), LocalDate.of(2026, 8, 31))

        assertEquals(42, grid.size)
        assertEquals(6, grid.indexOfFirst { it.date == LocalDate.of(2026, 8, 1) })
        assertEquals(31, grid.count { it.inMonth })
        assertEquals(36, grid.indexOfFirst { it.isToday })
    }

    @Test
    fun todayOutsideDisplayedMonthIsNotHighlighted() {
        val grid = MonthGrid.create(YearMonth.of(2026, 10), LocalDate.of(2026, 9, 30))

        assertEquals(0, grid.count { it.isToday })
    }

    @Test
    fun datesOutsideDisplayedMonthAreNotMarkedCurrentMonth() {
        val grid = MonthGrid.create(YearMonth.of(2026, 10), LocalDate.of(2026, 10, 30))

        assertEquals(false, grid.first().inMonth)
        assertEquals(true, grid.first { it.date == LocalDate.of(2026, 10, 1) }.inMonth)
    }
}
