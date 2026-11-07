package com.petlife.app

import com.petlife.app.usage.UsageStatsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 使用时长跨午夜拆分逻辑（纯函数）测试。
 * 注意：毫秒→日期的换算依赖系统默认时区；测试用本地时区构造时间戳，断言也基于本地时区。
 */
class UsageSplitTest {

    private val zone = ZoneId.systemDefault()

    private fun millis(d: LocalDateTime): Long = d.atZone(zone).toInstant().toEpochMilli()

    private fun dateStr(d: LocalDate): String = d.toString()

    @Test
    fun `single full-day interval stays in one day`() {
        val day = LocalDate.of(2026, 11, 6)
        val intervals = listOf(
            UsageStatsReader.Interval("a", millis(day.atTime(9, 0)), millis(day.atTime(10, 30))),
        )
        val byDay = UsageStatsReader.accumulateByDay(intervals)
        assertEquals(90L, byDay[dateStr(day)]!!["a"])
    }

    @Test
    fun `interval crossing midnight splits into two days`() {
        val day = LocalDate.of(2026, 11, 6)
        val next = day.plusDays(1)
        // 23:50 → 00:10 = 20 分钟，各 10 分钟
        val intervals = listOf(
            UsageStatsReader.Interval("a", millis(day.atTime(23, 50)), millis(next.atTime(0, 10))),
        )
        val byDay = UsageStatsReader.accumulateByDay(intervals)
        assertEquals(10L, byDay[dateStr(day)]!!["a"])
        assertEquals(10L, byDay[dateStr(next)]!!["a"])
    }

    @Test
    fun `very short interval contributes zero minutes`() {
        val day = LocalDate.of(2026, 11, 6)
        val intervals = listOf(
            UsageStatsReader.Interval("b", millis(day.atTime(12, 0)), millis(day.atTime(12, 0).plusSeconds(10))),
        )
        val byDay = UsageStatsReader.accumulateByDay(intervals)
        // 10 秒 < 30 秒 → 四舍五入后 0 分钟（不夸大统计）
        assertEquals(0, (byDay[dateStr(day)]?.get("b") ?: 0L))
    }

    @Test
    fun `empty and zero-length intervals ignored`() {
        val day = LocalDate.of(2026, 11, 6)
        val byDay = UsageStatsReader.accumulateByDay(
            listOf(
                UsageStatsReader.Interval("c", millis(day.atTime(1, 0)), millis(day.atTime(1, 0))),
            ),
        )
        assertTrue(byDay.isEmpty())
    }

    @Test
    fun `multi-day interval accumulates per day`() {
        val day = LocalDate.of(2026, 11, 5)
        // 11-05 22:00 → 11-07 04:00：跨 3 个日历日
        val intervals = listOf(
            UsageStatsReader.Interval("d", millis(day.atTime(22, 0)), millis(day.plusDays(2).atTime(4, 0))),
        )
        val byDay = UsageStatsReader.accumulateByDay(intervals)
        assertEquals(120L, byDay[dateStr(day)]!!["d"])           // 22:00-24:00
        assertEquals(1440L, byDay[dateStr(day.plusDays(1))]!!["d"])
        assertEquals(240L, byDay[dateStr(day.plusDays(2))]!!["d"]) // 00:00-04:00
    }
}
