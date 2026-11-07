package com.petlife.app.core

import java.util.Locale

object FormatUtils {

    /** 分钟数 → "1小时23分" / "45分钟" / "0分钟" */
    fun minutesHuman(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        return if (m >= 60) {
            val h = m / 60
            val mm = m % 60
            if (mm == 0L) "${h}小时" else "${h}小时${mm}分"
        } else {
            "${m}分钟"
        }
    }

    /** 分钟数 → "02:15" */
    fun minutesClock(minutes: Long): String {
        val m = minutes.coerceAtLeast(0)
        return "%02d:%02d".format(Locale.US, m / 60, m % 60)
    }

    fun percent(v: Float): String = "${(v * 100).toInt()}%"

    fun signedDelta(v: Int): String = if (v >= 0) "+$v" else "$v"
}
