package com.hengping.standby

import android.content.Context

class Prefs(context: Context) {
    private val values = context.getSharedPreferences("standby", Context.MODE_PRIVATE)

    var autoStart: Boolean
        get() = values.getBoolean("autoStart", false)
        set(value) = values.edit().putBoolean("autoStart", value).apply()

    var showSeconds: Boolean
        get() = values.getBoolean("showSeconds", true)
        set(value) = values.edit().putBoolean("showSeconds", value).apply()

    var backgroundUri: String?
        get() = values.getString("backgroundUri", null)
        set(value) = values.edit().putString("backgroundUri", value).apply()

    var dimAtNight: Boolean
        get() = values.getBoolean("dimAtNight", true)
        set(value) = values.edit().putBoolean("dimAtNight", value).apply()

    var calendarBackground: CalendarBackground
        get() = CalendarBackground.from(values.getString("calendarBackground", null))
        set(value) = values.edit().putString("calendarBackground", value.name).apply()
}
