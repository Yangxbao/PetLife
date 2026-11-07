package com.petlife.app.engine

import com.petlife.app.core.DateUtils
import java.time.LocalDate

/**
 * 每周目标修改额度：每周最多修改 2 次（学习目标与娱乐限额合计）。
 *
 *  - 第一次给新添加的应用设置目标【不】消耗额度（changeType = SET）
 *  - 修改（MODIFY）消耗额度
 *  - 每周一凌晨 00:00 随 ISO 周键切换自动恢复
 *  - 防重复重置：系统时间回拨时沿用上一次记录的周键，不会重开新周
 */
object WeekBudget {

    const val WEEKLY_LIMIT = 2

    data class Budget(val weekKey: String, val used: Int) {
        val remaining: Int get() = (WEEKLY_LIMIT - used).coerceAtLeast(0)
        val canModify: Boolean get() = remaining > 0
    }

    fun compute(currentWeekKey: String, modifyCountThisWeek: Int): Budget =
        Budget(currentWeekKey, modifyCountThisWeek.coerceAtLeast(0))

    /**
     * 判定一次目标写入是否需要消耗额度。
     * @param isNewApp 该应用是否为本次流程中新添加的（添加时首次设置目标不消耗）
     */
    fun requiresQuota(isNewApp: Boolean): Boolean = !isNewApp

    fun weekKeyForDate(d: LocalDate = java.time.LocalDate.now()): String = DateUtils.weekKeyOf(d)
}
