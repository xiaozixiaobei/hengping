package com.hengping.standby

import java.time.LocalDate
import java.time.YearMonth

class CalendarState(today: LocalDate, initialMonth: YearMonth = YearMonth.from(today)) {
    private var today = today
    var month: YearMonth = initialMonth
        private set
    var cells: List<DayCell> = MonthGrid.create(month, today)
        private set

    fun copy(): CalendarState = CalendarState(today, month)

    fun moveMonth(delta: Int) {
        month = month.plusMonths(delta.toLong())
        cells = MonthGrid.create(month, today)
    }

    fun returnToToday() {
        month = YearMonth.from(today)
        cells = MonthGrid.create(month, today)
    }

    fun updateToday(today: LocalDate) {
        if (today == this.today) return
        val followingToday = month == YearMonth.from(this.today)
        this.today = today
        if (followingToday) month = YearMonth.from(today)
        cells = MonthGrid.create(month, today)
    }
}
