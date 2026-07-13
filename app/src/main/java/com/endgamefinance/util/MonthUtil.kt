package com.endgamefinance.util

import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Bridges the schema's 'YYYY-MM' month keys and java.time, in the device zone. */
object MonthUtil {

    private val labelFormat = DateTimeFormatter.ofPattern("MMMM yyyy")

    fun key(ym: YearMonth): String = "%04d-%02d".format(ym.year, ym.monthValue)

    fun parse(key: String): YearMonth {
        val (y, m) = key.split("-")
        return YearMonth.of(y.toInt(), m.toInt())
    }

    fun startMs(ym: YearMonth): Long =
        ym.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun endMs(ym: YearMonth): Long = startMs(ym.plusMonths(1))

    fun label(ym: YearMonth): String = ym.format(labelFormat)
}
