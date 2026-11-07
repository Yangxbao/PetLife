package com.petlife.app.engine

import com.petlife.app.core.DateUtils
import com.petlife.app.data.model.FoodRarity
import java.time.LocalDate

/**
 * 签到引擎：连续签到、奖励发放。
 * 漏签只清零连击，绝不惩罚宠物。
 */
object CheckinEngine {

    data class Grant(val foodId: String, val count: Int, val why: String)

    /** 计算本次签到后的连续天数（需在上次签到数据之上） */
    fun streakFor(lastCheckinDate: String?, lastStreak: Int, today: LocalDate = DateUtils.today()): Int {
        if (lastCheckinDate == null) return 1
        val last = runCatching { DateUtils.parseDate(lastCheckinDate) }.getOrNull() ?: return 1
        return when {
            last == today -> lastStreak                      // 今天已签（不应走到这里，防御）
            last == today.minusDays(1) -> lastStreak + 1     // 连续
            last < today.minusDays(1) -> 1                   // 断签重来
            else -> 1                                        // 时间回拨等异常
        }
    }

    /** 本次签到奖励：基础小苹果×1；连击 3 的倍数送热牛奶；7 的倍数加送草莓蛋糕 */
    fun rewardsFor(streak: Int): List<Grant> {
        val grants = mutableListOf(Grant("apple", 1, "每日签到"))
        if (streak % 3 == 0) grants += Grant("milk", 1, "连续签到 $streak 天")
        if (streak % 7 == 0) grants += Grant("cake", 1, "连续签到 $streak 天")
        return grants
    }

    fun rewardsText(grants: List<Grant>): String =
        grants.joinToString("、") { g ->
            val def = FoodCatalog.get(g.foodId)
            if (g.count > 1) "${def.name}×${g.count}" else def.name
        }
}
