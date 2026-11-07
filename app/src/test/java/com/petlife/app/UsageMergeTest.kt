package com.petlife.app

import com.petlife.app.usage.UsageStatsReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageMergeTest {

    private fun mapOfDays(vararg pairs: Pair<String, Map<String, Long>>): Map<String, Map<String, Long>> =
        pairs.toMap()

    @Test
    fun `empty buckets returns events unchanged`() {
        val events = mapOfDays("2026-11-07" to mapOf("a" to 10L))
        assertEquals(events, UsageStatsReader.mergeByDay(events, emptyMap()))
    }

    @Test
    fun `empty events returns buckets`() {
        val buckets = mapOfDays("2026-11-07" to mapOf("a" to 30L))
        assertEquals(buckets, UsageStatsReader.mergeByDay(emptyMap(), buckets))
    }

    @Test
    fun `merge takes the larger estimate per package`() {
        val events = mapOfDays("2026-11-07" to mapOf("a" to 10L, "b" to 5L))
        val buckets = mapOfDays("2026-11-07" to mapOf("a" to 32L, "c" to 7L))
        val merged = UsageStatsReader.mergeByDay(events, buckets)
        assertEquals(32L, merged["2026-11-07"]!!["a"])
        assertEquals(5L, merged["2026-11-07"]!!["b"])   // 桶缺失该包 → 事件值
        assertEquals(7L, merged["2026-11-07"]!!["c"])   // 事件缺失该包 → 桶值
    }

    @Test
    fun `merge keeps all days`() {
        val events = mapOfDays("2026-11-06" to mapOf("a" to 5L))
        val buckets = mapOfDays("2026-11-07" to mapOf("a" to 9L))
        val merged = UsageStatsReader.mergeByDay(events, buckets)
        assertEquals(2, merged.size)
        assertTrue(merged.containsKey("2026-11-06"))
        assertTrue(merged.containsKey("2026-11-07"))
    }
}
