package com.petlife.app.engine

import com.petlife.app.core.DateUtils
import com.petlife.app.data.db.DailyRecordEntity
import com.petlife.app.data.db.MonitoredAppEntity
import com.petlife.app.data.db.PetEntity
import com.petlife.app.data.model.AppCategory
import com.petlife.app.data.model.DayEvaluation
import com.petlife.app.data.model.OverApp
import org.json.JSONArray
import org.json.JSONObject

/**
 * 每日结算引擎。
 *
 * 对"已结束的一天 D"结算：
 *  1. 学习任务完成数（实际使用 ≥ 目标）
 *  2. 娱乐超标应用与超标分钟
 *  3. 自律评分 + 扣分明细（透明）
 *  4. 情绪/健康按温和规则变化（永不致死）
 *  5. 成长系数快照（解释今天为什么成长快/慢）
 *
 * 结算幂等：daily_records 按日期唯一，重复结算会覆盖同一天的记录，宠物状态扣减不会叠加。
 */
object SettlementEngine {

    data class PetDelta(val moodDelta: Int, val healthDelta: Int)

    fun reasonsToJson(reasons: List<com.petlife.app.data.model.DisciplineReason>): String {
        val arr = JSONArray()
        for (r in reasons) {
            arr.put(JSONObject().put("label", r.label).put("delta", r.delta))
        }
        return arr.toString()
    }

    fun reasonsFromJson(json: String): List<com.petlife.app.data.model.DisciplineReason> =
        runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                com.petlife.app.data.model.DisciplineReason(o.getString("label"), o.getInt("delta"))
            }
        }.getOrDefault(emptyList())

    /** 只评估不落库（用于今日实时预览与统计页） */
    fun evaluate(
        date: String,
        usage: Map<String, Long>,
        apps: List<MonitoredAppEntity>,
        expGained: Int,
        foodsFed: Int,
        checkedIn: Boolean,
    ): com.petlife.app.data.model.DayEvaluation {
        var study = 0L
        var fun_ = 0L
        var completed = 0
        var total = 0
        val overs = mutableListOf<OverApp>()

        for (app in apps) {
            val used = usage[app.packageName] ?: 0L
            when (AppCategory.from(app.category)) {
                AppCategory.LEARN -> {
                    total += 1
                    study += used
                    if (app.dailyTargetMinutes > 0 && used >= app.dailyTargetMinutes) completed += 1
                }
                AppCategory.FUN -> {
                    fun_ += used
                    val limit = app.dailyTargetMinutes
                    if (limit > 0 && used > limit) {
                        overs += OverApp(app.packageName, app.appName, limit, used, used - limit)
                    }
                }
            }
        }

        val disc = DisciplineEngine.compute(overs, checkedIn, completed, total)
        val overTotal = overs.sumOf { it.overMinutes }
        val coeff = GrowthEngine.growthCoefficient(
            tasksCompleted = completed,
            tasksTotal = total,
            checkedInToday = checkedIn,
            streak = 0, // 结算快照不含连击（连击影响实时系数），此处保守
            overCount = overs.size,
            severeOver = overTotal > 60,
        )
        return com.petlife.app.data.model.DayEvaluation(
            date = date,
            studyMinutes = study,
            funMinutes = fun_,
            tasksCompleted = completed,
            tasksTotal = total,
            overApps = overs,
            overTotalMinutes = overTotal,
            disciplineScore = disc.score,
            reasons = disc.reasons,
            growthCoefficient = coeff,
            expGained = expGained,
            foodsFed = foodsFed,
        )
    }

    /** 宠物在结算日 D 的状态变化（温和） */
    fun petDeltaFor(eval: DayEvaluation, pet: PetEntity, settledDayEndMillis: Long): PetDelta {
        var mood = 0
        var health = 0
        for (over in eval.overApps) {
            mood += VitalityEngine.moodPenaltyForOver(over.overMinutes)
        }
        // 一整天没有互动 → 轻微低落（-5），鼓励回来看看，但不惩罚
        if (pet.lastInteractedMillis in 1 until DateUtils.startOfDayMillis(DateUtils.parseDate(eval.date))) {
            mood -= 5
        }
        // 超过 20 小时没喂食 → 健康 -10；否则 +2 恢复
        val twentyHours = 20 * 3600_000L
        if (pet.lastFedAtMillis in 1 until (settledDayEndMillis - twentyHours)) {
            health -= 10
        } else if (pet.lastFedAtMillis > 0) {
            health += 2
        }
        return PetDelta(mood, health)
    }

    fun clampMood(v: Int) = v.coerceIn(VitalityEngine.MOOD_MIN, VitalityEngine.MOOD_MAX)
    fun clampHealth(v: Int) = v.coerceIn(VitalityEngine.HEALTH_MIN, VitalityEngine.HEALTH_MAX)

    fun buildRecord(eval: DayEvaluation, moodAfter: Int, healthAfter: Int, now: Long): DailyRecordEntity =
        DailyRecordEntity(
            date = eval.date,
            studyMinutes = eval.studyMinutes,
            funMinutes = eval.funMinutes,
            tasksCompleted = eval.tasksCompleted,
            tasksTotal = eval.tasksTotal,
            funOverCount = eval.overApps.size,
            overMinutes = eval.overTotalMinutes,
            disciplineScore = eval.disciplineScore,
            reasonsJson = reasonsToJson(eval.reasons),
            growthCoefficient = eval.growthCoefficient,
            expGained = eval.expGained,
            foodsFed = eval.foodsFed,
            moodAfter = moodAfter,
            healthAfter = healthAfter,
            settledAtMillis = now,
        )
}
