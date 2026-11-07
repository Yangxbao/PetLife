package com.petlife.app.usage

import android.app.Activity
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.util.Log
import com.petlife.app.core.DateUtils
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 应用使用时长读取器（官方 UsageStatsManager）。
 *
 * v1.0.1 变更：
 *  - 全链路诊断日志（tag = "PetLifeStats"）：权限模式、事件数量、区间数、桶回退、最终分钟数
 *  - 查询结果对象 [QueryResult]，把"为什么是 0"明确暴露给上层
 *  - 事件流与桶查询交叉合并（取每包较大值），事件缺失的 ROM 也能拿到数据
 *  - 授权跳转支持 Activity 上下文（同任务栈打开，返回键可回到应用）
 */
class UsageStatsReader(private val context: Context) {

    private val usm: UsageStatsManager? =
        context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager

    private val tag = "PetLifeStats"

    data class PermissionState(val granted: Boolean, val mode: Int, val modeName: String)

    fun permissionState(): PermissionState {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
        if (appOps == null) {
            Log.w(tag, "权限检测失败：AppOpsManager 不可用")
            return PermissionState(false, AppOpsManager.MODE_ERRORED, "ERRORED(服务不可用)")
        }
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        val granted = mode == AppOpsManager.MODE_ALLOWED
        val name = when (mode) {
            AppOpsManager.MODE_ALLOWED -> "ALLOWED"
            AppOpsManager.MODE_IGNORED -> "IGNORED"
            AppOpsManager.MODE_ERRORED -> "ERRORED"
            AppOpsManager.MODE_DEFAULT -> "DEFAULT"
            else -> "MODE_$mode"
        }
        Log.i(tag, "权限检测：mode=$mode($name) granted=$granted")
        return PermissionState(granted, mode, name)
    }

    fun hasPermission(): Boolean = permissionState().granted

    /**
     * 跳转系统"使用情况访问"设置。
     * 传入 Activity 时使用同任务栈启动（返回键回到应用）；否则回退到 NEW_TASK。
     */
    fun openUsageAccessSettings(fromActivity: Activity? = null) {
        runCatching {
            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
            if (fromActivity != null) {
                fromActivity.startActivity(intent)
                Log.i(tag, "已从 Activity 上下文打开使用情况设置（同任务栈）")
            } else {
                context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                Log.i(tag, "已从应用上下文打开使用情况设置（新任务栈）")
            }
        }.onFailure { Log.w(tag, "打开使用情况设置失败：${it.message}") }
    }

    data class Interval(val packageName: String, val start: Long, val end: Long)

    /** 一次查询的完整结果（供诊断展示） */
    data class QueryResult(
        val byDay: Map<String, Map<String, Long>>,
        val eventCount: Int,
        val intervalsCount: Int,
        val fallbackUsed: Boolean,
        val crossFillUsed: Boolean,
        val error: String?,
        val elapsedMs: Long,
    )

    companion object {
        /**
         * 纯函数：把使用区间拆分到本地日历日，返回 每日 → 每包分钟数。
         * 日界按本地时区 00:00 计算。
         */
        fun accumulateByDay(intervals: List<Interval>): Map<String, MutableMap<String, Long>> {
            val result = linkedMapOf<String, MutableMap<String, Long>>()
            for (iv in intervals) {
                var start = iv.start
                val end = iv.end
                if (end <= start) continue
                while (start < end) {
                    val dayStart = localDayStartOf(start)
                    val nextDayStart = dayStart + 86_400_000L
                    val segEnd = minOf(end, nextDayStart)
                    val minutes = ((segEnd - start + 30_000) / 60_000).coerceAtLeast(0)
                    if (minutes > 0) {
                        val dateStr = millisToDateStrStatic(start)
                        val bucket = result.getOrPut(dateStr) { mutableMapOf() }
                        bucket[iv.packageName] = (bucket[iv.packageName] ?: 0L) + minutes
                    }
                    start = segEnd
                }
            }
            return result
        }

        /** 纯函数：事件流结果与桶结果按 (日, 包) 取较大值合并（可单测） */
        fun mergeByDay(
            events: Map<String, Map<String, Long>>,
            buckets: Map<String, Map<String, Long>>,
        ): Map<String, Map<String, Long>> {
            if (buckets.isEmpty()) return events
            if (events.isEmpty()) return buckets
            val days = events.keys + buckets.keys
            val out = linkedMapOf<String, Map<String, Long>>()
            for (day in days) {
                val e = events[day].orEmpty()
                val b = buckets[day].orEmpty()
                val pkgs = e.keys + b.keys
                val merged = linkedMapOf<String, Long>()
                for (pkg in pkgs) {
                    merged[pkg] = maxOf(e[pkg] ?: 0L, b[pkg] ?: 0L)
                }
                out[day] = merged
            }
            return out
        }

        /** 纯函数：桶查询结果 → 每日每包分钟（桶归属日 = 桶起始时间的本地日期） */
        fun bucketsToDayMap(buckets: List<android.app.usage.UsageStats>): Map<String, Map<String, Long>> {
            val out = linkedMapOf<String, MutableMap<String, Long>>()
            for (b in buckets) {
                val fg = b.totalTimeInForeground
                if (fg <= 0) continue
                val dateStr = DateUtils.dateStr(
                    Instant.ofEpochMilli(b.firstTimeStamp).atZone(ZoneId.systemDefault()).toLocalDate(),
                )
                val bucket = out.getOrPut(dateStr) { mutableMapOf() }
                bucket[b.packageName] = (bucket[b.packageName] ?: 0L) + fg / 60_000
            }
            return out
        }

        private fun localDayStartOf(millis: Long): Long =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
                .toLocalDate().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

        private fun millisToDateStrStatic(millis: Long): String =
            DateUtils.dateStr(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate())
    }

    /**
     * 查询 [from, to]（含两端）每日每应用前台使用分钟（含诊断）。
     */
    fun queryDailyUsageDetailed(from: LocalDate, to: LocalDate): QueryResult {
        val started = System.currentTimeMillis()
        val usm = this.usm
        if (usm == null) {
            Log.w(tag, "UsageStatsManager 不可用（getSystemService 返回 null）")
            return QueryResult(emptyMap(), 0, 0, false, false, "USM 不可用", 0)
        }
        val rangeStart = DateUtils.startOfDayMillis(from)
        val rangeEnd = DateUtils.endOfDayMillis(to)
        Log.i(tag, "查询使用时长：$from ~ $to（${DateUtils.dateStr(from)} 00:00 → ${DateUtils.dateStr(to)} 23:59 本地时区）")

        // ---- 1. 事件流 ----
        val raw: MutableList<Triple<Long, String, Int>> = mutableListOf()
        var eventError: String? = null
        runCatching {
            val ev = usm.queryEvents(rangeStart, rangeEnd + 1)
            collectEvents(ev, raw)
        }.onFailure {
            eventError = "queryEvents 异常：${it.javaClass.simpleName}: ${it.message}"
            Log.w(tag, eventError!!)
        }
        val typeCounts = raw.groupingBy { it.third }.eachCount()
        Log.i(tag, "事件流：共 ${raw.size} 条，类型分布 $typeCounts")

        raw.sortWith(compareBy({ it.first }, { it.third }))
        val intervals = mutableListOf<Interval>()
        val open = HashMap<String, Long>()
        for ((ts, pkg, type) in raw) {
            when (type) {
                UsageEvents.Event.ACTIVITY_RESUMED -> open[pkg] = ts
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val start = open.remove(pkg)
                    if (start != null) {
                        val s = maxOf(start, rangeStart)
                        val e = minOf(ts, rangeEnd)
                        if (e > s) intervals += Interval(pkg, s, e)
                    }
                }
            }
        }
        val now = System.currentTimeMillis()
        for ((pkg, start) in open) {
            val s = maxOf(start, rangeStart)
            val e = minOf(now, rangeEnd)
            if (e > s) intervals += Interval(pkg, s, e)
        }
        Log.i(tag, "前台区间：${intervals.size} 段（未闭合会话 ${open.size} 个按当前时刻闭合）")

        val eventByDay = accumulateByDay(intervals)
        val eventTotal = eventByDay.values.sumOf { it.values.sum() }
        Log.i(tag, "事件流统计：${eventByDay.size} 天，总计 $eventTotal 分钟")

        // ---- 2. 桶查询 ----
        var bucketError: String? = null
        val bucketByDay = linkedMapOf<String, Map<String, Long>>()
        runCatching {
            val buckets = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, rangeStart, rangeEnd + 1)
            bucketByDay.putAll(bucketsToDayMap(buckets))
        }.onFailure {
            bucketError = "queryUsageStats 异常：${it.javaClass.simpleName}: ${it.message}"
            Log.w(tag, bucketError!!)
        }
        val bucketTotal = bucketByDay.values.sumOf { it.values.sum() }
        Log.i(tag, "桶查询统计：${bucketByDay.size} 天，总计 $bucketTotal 分钟")

        // ---- 3. 合并（取较大值：事件缺失的 ROM 由桶兜底） ----
        val merged = mergeByDay(eventByDay, bucketByDay)
        val crossFillUsed = merged != eventByDay
        val fallbackUsed = eventByDay.isEmpty() && bucketByDay.isNotEmpty()
        val mergedTotal = merged.values.sumOf { it.values.sum() }
        val elapsed = System.currentTimeMillis() - started
        Log.i(
            tag,
            "最终结果：${merged.size} 天，$mergedTotal 分钟，" +
                "top3=" + merged.entries.flatMap { it.value.entries }.sortedByDescending { it.value }.take(3) +
                "，合并修正=$crossFillUsed，纯桶回退=$fallbackUsed，耗时 ${elapsed}ms" +
                (eventError?.let { "，事件错误=$it" } ?: "") +
                (bucketError?.let { "，桶错误=$it" } ?: ""),
        )

        return QueryResult(merged, raw.size, intervals.size, fallbackUsed, crossFillUsed, eventError ?: bucketError, elapsed)
    }

    /** 兼容旧调用：只要结果数据 */
    fun queryDailyUsage(from: LocalDate, to: LocalDate): Map<String, Map<String, Long>> =
        queryDailyUsageDetailed(from, to).byDay

    private fun collectEvents(events: UsageEvents, out: MutableList<Triple<Long, String, Int>>) {
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            out += Triple(event.timeStamp, event.packageName, event.eventType)
        }
    }

    /** 今日实时使用（含当前仍在前台的时间），供 UI 平滑刷新 */
    fun queryTodayUsage(): Map<String, Long> =
        queryDailyUsage(DateUtils.today(), DateUtils.today())[DateUtils.todayStr()] ?: emptyMap()
}
