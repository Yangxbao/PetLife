package com.petlife.app

import com.petlife.app.data.model.OverApp
import com.petlife.app.engine.DisciplineEngine
import com.petlife.app.engine.CheckinEngine
import com.petlife.app.engine.SettlementEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DisciplineEngineTest {

    @Test
    fun `perfect day scores 105 clamped to 100 with bonus reason`() {
        val r = DisciplineEngine.compute(overs = emptyList(), checkedIn = true, tasksCompleted = 3, tasksTotal = 3)
        assertEquals(100, r.score)
        assertTrue(r.reasons.any { it.delta > 0 })
    }

    @Test
    fun `no overs but nothing done`() {
        // 未签到 -5，任务 0/3 → -(1-0)*15 = -15
        val r = DisciplineEngine.compute(emptyList(), checkedIn = false, tasksCompleted = 0, tasksTotal = 3)
        assertEquals(80, r.score)
        assertEquals(2, r.reasons.size)
    }

    @Test
    fun `over-limit app gets penalty proportional and capped at 25`() {
        val over = OverApp("pkg", "抖音", limitMinutes = 30, usedMinutes = 60, overMinutes = 30)
        val r = DisciplineEngine.compute(listOf(over), checkedIn = true, tasksCompleted = 0, tasksTotal = 0)
        // 10 + (30/30)*15 = 25
        assertEquals(75, r.score)
        assertEquals("抖音 超出限额 30 分钟", r.reasons.first().label)
        assertEquals(-25, r.reasons.first().delta)
    }

    @Test
    fun `mild over gets small penalty`() {
        val over = OverApp("pkg", "抖音", limitMinutes = 60, usedMinutes = 65, overMinutes = 5)
        val r = DisciplineEngine.compute(listOf(over), checkedIn = true, tasksCompleted = 0, tasksTotal = 0)
        // 10 + (5/60)*15 = 11.25 → 11
        assertEquals(89, r.score)
    }

    @Test
    fun `score never below 0`() {
        val overs = (1..10).map { OverApp("p$it", "A$it", 10, 999, 989) }
        val r = DisciplineEngine.compute(overs, checkedIn = false, tasksCompleted = 0, tasksTotal = 5)
        assertEquals(0, r.score)
    }

    @Test
    fun `reasons json roundtrip`() {
        val r = DisciplineEngine.compute(emptyList(), checkedIn = false, tasksCompleted = 1, tasksTotal = 2)
        val json = SettlementEngine.reasonsToJson(r.reasons)
        val back = SettlementEngine.reasonsFromJson(json)
        assertEquals(r.reasons, back)
    }
}

class CheckinEngineTest {

    @Test
    fun `first checkin streak 1`() {
        assertEquals(1, CheckinEngine.streakFor(null, 0, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `consecutive checkin increments`() {
        assertEquals(
            6,
            CheckinEngine.streakFor("2026-11-05", 5, LocalDate.of(2026, 11, 6)),
        )
    }

    @Test
    fun `broken streak resets to 1 without punishing`() {
        assertEquals(
            1,
            CheckinEngine.streakFor("2026-11-03", 5, LocalDate.of(2026, 11, 6)),
        )
    }

    @Test
    fun `same day keeps streak defensive`() {
        assertEquals(5, CheckinEngine.streakFor("2026-11-06", 5, LocalDate.of(2026, 11, 6)))
    }

    @Test
    fun `rewards base and milestones`() {
        val base = CheckinEngine.rewardsFor(1)
        assertEquals(1, base.size)
        assertEquals("apple", base[0].foodId)

        val d3 = CheckinEngine.rewardsFor(3)
        assertEquals(2, d3.size)
        assertTrue(d3.any { it.foodId == "milk" })

        val d7 = CheckinEngine.rewardsFor(7)
        assertTrue(d7.any { it.foodId == "cake" })
        // 7 不被 3 整除，所以没有牛奶
        assertTrue(d7.none { it.foodId == "milk" })
    }

    @Test
    fun `rewards text readable`() {
        val text = CheckinEngine.rewardsText(CheckinEngine.rewardsFor(3))
        assertTrue(text.contains("小苹果"))
        assertTrue(text.contains("热牛奶"))
    }
}
