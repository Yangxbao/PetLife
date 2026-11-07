package com.petlife.app

import com.petlife.app.core.DateUtils
import com.petlife.app.engine.SettlementEngine
import com.petlife.app.data.db.MonitoredAppEntity
import com.petlife.app.data.model.AppCategory
import com.petlife.app.engine.WeekBudget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeekBudgetTest {

    @Test
    fun `new app first target does not consume quota`() {
        assertFalse(WeekBudget.requiresQuota(isNewApp = true))
        assertTrue(WeekBudget.requiresQuota(isNewApp = false))
    }

    @Test
    fun `budget remaining decrements`() {
        val b = WeekBudget.compute("2026-W45", 1)
        assertEquals(1, b.remaining)
        assertFalse(b.canModify == false)
    }

    @Test
    fun `used more than limit clamps remaining to 0`() {
        val b = WeekBudget.compute("2026-W45", 5)
        assertEquals(0, b.remaining)
        assertFalse(b.canModify)
    }

    @Test
    fun `week key starts on monday`() {
        // 2026-11-02 是周一；2026-11-06 是周五 → 同一周
        val monday = LocalDate.of(2026, 11, 2)
        val friday = LocalDate.of(2026, 11, 6)
        val sunday = LocalDate.of(2026, 11, 8)
        val nextMonday = LocalDate.of(2026, 11, 9)
        assertEquals(DateUtils.weekKeyOf(monday), DateUtils.weekKeyOf(friday))
        assertEquals(DateUtils.weekKeyOf(monday), DateUtils.weekKeyOf(sunday))
        assertTrue(DateUtils.weekKeyOf(monday) != DateUtils.weekKeyOf(nextMonday))
        // 周日 23:59 属于上一周，周一 00:00 属于新一周（周一凌晨恢复额度）
        assertEquals(monday, DateUtils.mondayOfWeek(friday))
        assertEquals(nextMonday, DateUtils.mondayOfWeek(nextMonday))
    }

    @Test
    fun `clock rollback guard keeps previous week key`() {
        // 锚点设为远未来日期，保证无论真实"今天"是哪天，now < last 恒成立（回拨分支）
        val (key, recorded) = DateUtils.effectiveWeekKeyWithGuard("2099-12-31", "2099-W53")
        // 时间回拨 → 沿用旧周键，防止重复重置
        assertEquals("2099-W53", key)
        assertEquals("2099-12-31", recorded)
    }

    @Test
    fun `normal time advance opens new week`() {
        val (key, recorded) = DateUtils.effectiveWeekKeyWithGuard("2026-11-06", "2026-W45")
        assertEquals(DateUtils.weekKeyOf(DateUtils.today()), key)
        assertEquals(DateUtils.dateStr(DateUtils.today()), recorded)
    }
}

class SettlementEvaluateTest {

    private fun app(
        pkg: String,
        name: String,
        cat: AppCategory,
        target: Int,
    ) = MonitoredAppEntity(
        packageName = pkg, appName = name, category = cat.name,
        dailyTargetMinutes = target, createdAtEpochDay = 0,
    )

    @Test
    fun `evaluate counts study tasks and overs`() {
        val apps = listOf(
            app("bilibili", "B站", AppCategory.LEARN, 120),
            app("baicizhan", "百词斩", AppCategory.LEARN, 30),
            app("douyin", "抖音", AppCategory.FUN, 30),
            app("wzry", "王者荣耀", AppCategory.FUN, 60),
        )
        val usage = mapOf(
            "bilibili" to 130L,   // 达标
            "baicizhan" to 20L,   // 未达标
            "douyin" to 45L,      // 超标15
            "wzry" to 90L,        // 超标30
        )
        val eval = SettlementEngine.evaluate(
            date = "2026-11-06",
            usage = usage,
            apps = apps,
            expGained = 45,
            foodsFed = 3,
            checkedIn = true,
        )
        assertEquals(150L, eval.studyMinutes)
        assertEquals(135L, eval.funMinutes)
        assertEquals(1, eval.tasksCompleted)
        assertEquals(2, eval.tasksTotal)
        assertEquals(2, eval.overApps.size)
        assertEquals(45L, eval.overTotalMinutes)
        assertTrue(eval.disciplineScore in 1..99)
        assertTrue(eval.reasons.isNotEmpty())
    }

    @Test
    fun `no apps means no tasks and no overs`() {
        val eval = SettlementEngine.evaluate("2026-11-06", emptyMap(), emptyList(), 0, 0, false)
        assertEquals(0, eval.tasksTotal)
        assertEquals(0L, eval.studyMinutes)
        assertEquals(0, eval.overApps.size)
        // 未签到扣 5
        assertEquals(95, eval.disciplineScore)
    }

    @Test
    fun `severe over flagged beyond 60 minutes`() {
        val apps = listOf(app("game", "某游戏", AppCategory.FUN, 30))
        val eval = SettlementEngine.evaluate("2026-11-06", mapOf("game" to 130L), apps, 0, 0, true)
        assertEquals(100L, eval.overTotalMinutes)
        // 严重超标（>60min）+ 1 个超标应用会显著拉低系数；无任务时按 0.5 完成率计
        // 1.0 + 0.30*0.5 + 0.10(签到) - 0.05*1 - 0.10 = 1.10
        assertEquals(1.10f, eval.growthCoefficient, 0.01f)
        // 永远不会低于 0.70 的恢复下限
        assertTrue(eval.growthCoefficient >= 0.70f)
    }
}
