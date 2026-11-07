package com.petlife.app.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields

/**
 * 日期工具。全部以手机本地时区为准（需求：按手机本地时间计算）。
 */
object DateUtils {

    private val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()

    fun yesterday(): LocalDate = LocalDate.now().minusDays(1)

    fun dateStr(d: LocalDate): String = d.format(ISO_DATE)

    fun parseDate(s: String): LocalDate = LocalDate.parse(s, ISO_DATE)

    fun todayStr(): String = dateStr(today())

    fun startOfDayMillis(d: LocalDate): Long =
        d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    fun endOfDayMillis(d: LocalDate): Long =
        d.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1

    fun epochDay(d: LocalDate): Long = d.toEpochDay()

    /** 周一凌晨 00:00 为一周的开始（ISO 周）。 */
    fun mondayOfWeek(d: LocalDate): LocalDate =
        d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /**
     * 周键，例如 "2026-W45"。ISO 周从周一开始，天然满足"周一凌晨恢复"的规则。
     */
    fun weekKeyOf(d: LocalDate): String {
        val wf: WeekFields = WeekFields.ISO
        val week = d.get(wf.weekOfWeekBasedYear())
        val year = d.get(wf.weekBasedYear())
        return "%04d-W%02d".format(year, week)
    }

    /**
     * 防止"系统时间回拨导致重复重置"：
     * 若当前日期早于上次见过的日期（用户手动调回时间），则沿用上次记录的周键，不重新开周。
     * 返回 (生效周键, 应记录的最后日期)。
     */
    fun effectiveWeekKeyWithGuard(lastSeenDateStr: String?, lastSeenWeekKey: String?): Pair<String, String> {
        val now = today()
        if (lastSeenDateStr == null) {
            val key = weekKeyOf(now)
            return key to dateStr(now)
        }
        val last = runCatching { parseDate(lastSeenDateStr) }.getOrElse { now }
        return if (!now.isBefore(last)) {
            weekKeyOf(now) to dateStr(now)
        } else {
            (lastSeenWeekKey ?: weekKeyOf(last)) to dateStr(last)
        }
    }

    /** 用于"连续签到/断签"判断：上次签到是否为昨天 */
    fun isYesterday(dateStr: String?, reference: LocalDate = today()): Boolean {
        if (dateStr.isNullOrBlank()) return false
        return runCatching { parseDate(dateStr) == reference.minusDays(1) }.getOrDefault(false)
    }

    fun weekdayShortCN(d: LocalDate): String =
        when (d.dayOfWeek) {
            DayOfWeek.MONDAY -> "周一"
            DayOfWeek.TUESDAY -> "周二"
            DayOfWeek.WEDNESDAY -> "周三"
            DayOfWeek.THURSDAY -> "周四"
            DayOfWeek.FRIDAY -> "周五"
            DayOfWeek.SATURDAY -> "周六"
            DayOfWeek.SUNDAY -> "周日"
        }

    fun friendlyDate(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日 ${weekdayShortCN(d)}"

    fun millisToHourMinute(millis: Long): String {
        val dt = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        return "%02d:%02d".format(dt.hour, dt.minute)
    }

    fun currentLocalTime(): LocalTime = LocalTime.now()
}
