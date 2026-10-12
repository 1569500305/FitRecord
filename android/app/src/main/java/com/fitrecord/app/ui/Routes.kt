package com.fitrecord.app.ui

import java.time.LocalDate

/** 路由表（规格 1.2）。日期用 epochDay 传参，避免 URI 里出现斜杠。 */
object Routes {
    const val CALENDAR = "calendar"
    const val STATS = "stats"
    const val PROFILE = "profile"
    const val BODY = "body"
    const val TYPE_LIST = "types"
    const val BACKUP = "backup"
    const val APPEARANCE = "appearance"

    const val ARG_DATE = "date"
    const val ARG_ID = "id"

    /** 三个一级 Tab；不在表内的路由不显示底部导航 */
    val tabs = setOf(CALENDAR, STATS, PROFILE)

    const val DAY = "day/{$ARG_DATE}"
    const val RECORD = "record?{$ARG_DATE}&{$ARG_ID}"
    const val TYPE_EDIT = "type/edit/{$ARG_ID}"

    fun day(date: LocalDate): String = "day/${date.toEpochDay()}"

    fun record(date: LocalDate, id: Long = -1L): String = "record?date=${date.toEpochDay()}&id=$id"

    fun typeEdit(id: Long): String = "type/edit/$id"

    fun epochDayOf(raw: String?): LocalDate =
        LocalDate.ofEpochDay(raw?.toLongOrNull() ?: LocalDate.now().toEpochDay())
}
