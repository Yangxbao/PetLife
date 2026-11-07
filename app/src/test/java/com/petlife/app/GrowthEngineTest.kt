package com.petlife.app

import com.petlife.app.engine.GrowthEngine
import com.petlife.app.data.db.PetEntity
import com.petlife.app.data.model.PetStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrowthEngineTest {

    private fun pet(level: Int = 1, exp: Int = 0) = PetEntity(
        id = 1, level = level, exp = exp, birthEpochDay = 0, lastFedAtMillis = 0,
    )

    @Test
    fun `exp curve is monotonic increasing`() {
        for (level in 1..40) {
            assertTrue(GrowthEngine.expToNext(level + 1) > GrowthEngine.expToNext(level))
        }
        assertEquals(60, GrowthEngine.expToNext(1))
    }

    @Test
    fun `stage thresholds`() {
        assertEquals(PetStage.BABY, GrowthEngine.stageOf(1))
        assertEquals(PetStage.BABY, GrowthEngine.stageOf(9))
        assertEquals(PetStage.GROWING, GrowthEngine.stageOf(10))
        assertEquals(PetStage.GROWING, GrowthEngine.stageOf(19))
        assertEquals(PetStage.ADULT, GrowthEngine.stageOf(20))
        assertEquals(PetStage.EVOLVED, GrowthEngine.stageOf(30))
        assertEquals(PetStage.EVOLVED, GrowthEngine.stageOf(60))
    }

    @Test
    fun `applyExp applies coefficient and at least 1`() {
        val r = GrowthEngine.applyExp(pet(), rawExp = 10, coefficient = 1.0f, nowMillis = 100)
        assertEquals(10, r.expApplied)
        // 系数 0.7 → 10*0.7 = 7
        val r2 = GrowthEngine.applyExp(pet(), 10, 0.7f, 100)
        assertEquals(7, r2.expApplied)
        // 极低系数也不得为 0
        val r3 = GrowthEngine.applyExp(pet(), 1, 0.01f, 100)
        assertEquals(1, r3.expApplied)
    }

    @Test
    fun `applyExp levels up and carries remainder`() {
        // expToNext(1) = 60；喂 70 经验 → 升 1 级余 10
        val r = GrowthEngine.applyExp(pet(level = 1, exp = 0), rawExp = 70, coefficient = 1.0f, nowMillis = 1)
        assertEquals(1, r.levelsGained)
        assertEquals(2, r.pet.level)
        assertEquals(10, r.pet.exp)
    }

    @Test
    fun `applyExp multi-level jump`() {
        val r = GrowthEngine.applyExp(pet(level = 1, exp = 0), rawExp = 2000, coefficient = 1.0f, nowMillis = 1)
        assertTrue(r.levelsGained >= 4)
        assertTrue(r.pet.exp < GrowthEngine.expToNext(r.pet.level))
    }

    @Test
    fun `stage change detected on crossing level 10`() {
        val r = GrowthEngine.applyExp(pet(level = 9, exp = GrowthEngine.expToNext(9) - 1), rawExp = 50, coefficient = 1.0f, nowMillis = 1)
        assertTrue(r.stageChanged)
        assertEquals(PetStage.GROWING, GrowthEngine.stageOf(r.pet.level))
        assertEquals(1, r.pet.evolutionCount)
    }

    @Test
    fun `growth coefficient bounds and logic`() {
        // 全勤最优（有任务且全完成、已签、连击 10、无超标）
        val best = GrowthEngine.growthCoefficient(3, 3, true, 10, 0, false)
        assertTrue("best=$best", best >= 1.4f) // clamp 到上限
        // 极端躺平：无任务(0.5)、未签、连击0、8个超标+严重 → 触底 0.70
        val worst = GrowthEngine.growthCoefficient(0, 0, false, 0, 8, true)
        assertEquals(0.70f, worst)
        // 轻度松懈（2 个轻度超标 + 严重超标）尚未触底
        val mid = GrowthEngine.growthCoefficient(0, 0, false, 0, 2, true)
        assertEquals(0.95f, mid, 0.001f)
        // 基准：无任务、未签、无连击、无超标
        val base = GrowthEngine.growthCoefficient(0, 0, false, 0, 0, false)
        assertEquals(1.15f, base, 0.001f) // 1.0 + 0.30*0.5
        // 系数永不低于 0.7（可恢复性）
        val floor = GrowthEngine.growthCoefficient(0, 0, false, 0, 5, true)
        assertTrue(floor >= 0.70f)
        // 有任务但全没完成 → 完成率 0
        val zero = GrowthEngine.growthCoefficient(0, 3, false, 0, 0, false)
        assertEquals(1.00f, zero, 0.001f)
    }

    @Test
    fun `coefficient label is non-empty`() {
        assertFalse(GrowthEngine.coefficientLabel(1.3f).isBlank())
    }
}
