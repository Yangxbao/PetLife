package com.petlife.app.engine

import com.petlife.app.data.db.PetEntity
import com.petlife.app.data.model.PetStage
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 成长引擎：等级曲线、阶段判定、成长系数、经验结算。
 */
object GrowthEngine {

    /** 升到下一级所需经验：随等级缓慢增长，前期升级快、给足正反馈。 */
    fun expToNext(level: Int): Int = 60 + (level - 1) * 25

    fun stageOf(level: Int): PetStage = when {
        level >= 30 -> PetStage.EVOLVED
        level >= 20 -> PetStage.ADULT
        level >= 10 -> PetStage.GROWING
        else -> PetStage.BABY
    }

    data class ExpResult(val pet: PetEntity, val expApplied: Int, val levelsGained: Int, val stageChanged: Boolean)

    /**
     * 喂食获得经验 = 食物经验 × 当日成长系数（向下取整，至少 1）。
     * 连续升级自动结算；阶段变化时记录 evolutionCount。
     */
    fun applyExp(pet: PetEntity, rawExp: Int, coefficient: Float, nowMillis: Long): ExpResult {
        val applied = (rawExp * coefficient).toInt().coerceAtLeast(1)
        var level = pet.level
        var exp = pet.exp + applied
        var gained = 0
        while (exp >= expToNext(level) && level < 60) {
            exp -= expToNext(level)
            level += 1
            gained += 1
        }
        val oldStage = stageOf(pet.level)
        val newStage = stageOf(level)
        return ExpResult(
            pet = pet.copy(
                level = level,
                exp = exp,
                lastFedAtMillis = nowMillis,
                evolutionCount = pet.evolutionCount + if (newStage != oldStage) 1 else 0,
            ),
            expApplied = applied,
            levelsGained = gained,
            stageChanged = newStage != oldStage,
        )
    }

    /**
     * 成长系数（0.70 ~ 1.40），决定喂食经验的放大倍率。
     *
     *  = 1.00 基础
     *  + 0.30 × 学习任务完成率（今日已完成学习任务 / 学习任务总数；无任务时按 0.5 计）
     *  + 0.10 今日已签到
     *  + 0.02 × min(连续签到天数, 10)
     *  - 0.05 × 超标娱乐应用数
     *  - 0.10 当娱乐总超标时间超过 60 分钟（严重超标）
     *
     * 任何时候都不会低于 0.70 —— 表现不好只会慢一点，永远可以恢复。
     */
    fun growthCoefficient(
        tasksCompleted: Int,
        tasksTotal: Int,
        checkedInToday: Boolean,
        streak: Int,
        overCount: Int,
        severeOver: Boolean,
    ): Float {
        val rate = if (tasksTotal > 0) tasksCompleted.toDouble() / tasksTotal else 0.5
        var c = 1.0
        c += 0.30 * rate
        if (checkedInToday) c += 0.10
        c += 0.02 * min(streak, 10)
        c -= 0.05 * overCount
        if (severeOver) c -= 0.10
        return c.toFloat().coerceIn(0.70f, 1.40f)
    }

    /** 展示用：系数描述 */
    fun coefficientLabel(c: Float): String = when {
        c >= 1.25f -> "状态极佳 🚀"
        c >= 1.05f -> "稳步成长 🌱"
        c >= 0.90f -> "平平常常 😌"
        else -> "有点松懈 💤"
    }
}

/** 情绪/健康的增量规则（温和、可恢复，绝不惩罚致死） */
object VitalityEngine {

    const val MOOD_MIN = 0
    const val MOOD_MAX = 100
    const val HEALTH_MIN = 10
    const val HEALTH_MAX = 100

    /** 超标娱乐对情绪的打击：单应用最多 -20，轻度超标也只轻微影响 */
    fun moodPenaltyForOver(overMinutes: Long): Int =
        -(min(20.0, 8.0 + overMinutes / 10.0).roundToInt())

    /** 点击互动：小心情加成（每次+1，有冷却，每日有上限，防刷） */
    const val TAP_MOOD_GAIN = 1
    const val TAP_COOLDOWN_MS = 60_000L
    const val TAP_DAILY_LIMIT = 5

    /** 喂食对健康与情绪的回复 */
    const val FEED_HEALTH_GAIN = 6
}
