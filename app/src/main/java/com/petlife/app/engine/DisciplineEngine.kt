package com.petlife.app.engine

import com.petlife.app.data.model.DisciplineReason
import com.petlife.app.data.model.OverApp
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 每日自律评分（0~100）与扣分明细。
 *
 * 规则（温和、透明）：
 *  - 每个超标的娱乐应用：-min(25, 10 + 超标分钟/限额 × 15)
 *  - 当日未签到：-5
 *  - 学习任务未全部完成：-(1-完成率)×15（最多 -15）
 *  - 全部完成且无超标：+5 鼓励
 *
 * 扣分原因逐条记录，用户在统计页能看到"为什么被扣、扣了多少"。
 */
object DisciplineEngine {

    data class Result(val score: Int, val reasons: List<DisciplineReason>)

    fun compute(
        overs: List<OverApp>,
        checkedIn: Boolean,
        tasksCompleted: Int,
        tasksTotal: Int,
    ): Result {
        val reasons = mutableListOf<DisciplineReason>()
        var score = 100

        for (over in overs.sortedByDescending { it.overMinutes }) {
            val ratio = if (over.limitMinutes > 0) over.overMinutes.toDouble() / over.limitMinutes else 1.0
            val penalty = min(25.0, 10.0 + ratio * 15.0).roundToInt()
            if (penalty > 0) {
                reasons += DisciplineReason(
                    label = "${over.appName} 超出限额 ${over.overMinutes} 分钟",
                    delta = -penalty,
                )
                score -= penalty
            }
        }

        if (!checkedIn) {
            reasons += DisciplineReason("今天没有签到", -5)
            score -= 5
        }

        if (tasksTotal > 0) {
            val rate = tasksCompleted.toDouble() / tasksTotal
            if (rate < 1.0) {
                val penalty = ((1 - rate) * 15).roundToInt()
                if (penalty > 0) {
                    reasons += DisciplineReason(
                        "学习任务完成 ${tasksCompleted}/${tasksTotal}",
                        -penalty,
                    )
                    score -= penalty
                }
            } else {
                reasons += DisciplineReason("学习任务全部完成", +5)
                score += 5
            }
        }

        return Result(score.coerceIn(0, 100), reasons)
    }

    fun scoreLabel(score: Int): String = when {
        score >= 90 -> "非常自律 ✨"
        score >= 75 -> "表现不错 👍"
        score >= 55 -> "还有空间 🙂"
        score >= 35 -> "需要努力 💪"
        else -> "别灰心，明天重新开始 🌈"
    }
}
