package com.petlife.app.core

import java.util.Locale

object FormatUtils {

    /**
     * 时长统一展示为纯分钟数字（用户明确要求全站一致，不使用"X小时X分"或"HH:MM"）。
     * 例：0 → "0分钟"，45 → "45分钟"，204 → "204分钟"
     */
    fun minutesHuman(minutes: Long): String = "${minutes.coerceAtLeast(0)}分钟"

    /** 兼容旧调用：统一走向纯分钟格式 */
    fun minutesClock(minutes: Long): String = minutesHuman(minutes)

    fun percent(v: Float): String = "${(v * 100).toInt()}%"

    fun signedDelta(v: Int): String = if (v >= 0) "+$v" else "$v"
}
