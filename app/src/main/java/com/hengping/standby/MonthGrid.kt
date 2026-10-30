package com.hengping.standby

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class DayCell(
    val date: LocalDate,
    val inMonth: Boolean,
    val isToday: Boolean,
)

object MonthGrid {
    fun create(month: YearMonth, today: LocalDate): List<DayCell> {
        val first = month.atDay(1)
        val startOffset = (first.dayOfWeek.value - DayOfWeek.SUNDAY.value + 7) % 7
        val start = first.minusDays(startOffset.toLong())
        val itemCount = ((startOffset + month.lengthOfMonth() + 6) / 7) * 7

        return (0 until itemCount).map { offset ->
            val date = start.plusDays(offset.toLong())
            DayCell(
                date = date,
                inMonth = YearMonth.from(date) == month,
                isToday = date == today && YearMonth.from(date) == month,
            )
        }
    }
}
