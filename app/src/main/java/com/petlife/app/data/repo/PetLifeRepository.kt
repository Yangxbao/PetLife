package com.petlife.app.data.repo

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.petlife.app.core.DateUtils
import com.petlife.app.data.db.AppUsageDayEntity
import com.petlife.app.data.db.CheckinEntity
import com.petlife.app.data.db.DailyRecordEntity
import com.petlife.app.data.db.ExpEventEntity
import com.petlife.app.data.db.GoalChangeEntity
import com.petlife.app.data.db.MonitoredAppEntity
import com.petlife.app.data.db.PetLifeDatabase
import com.petlife.app.data.db.PetEntity
import com.petlife.app.data.db.TaskClaimEntity
import com.petlife.app.data.model.AppCategory
import com.petlife.app.data.model.DayEvaluation
import com.petlife.app.data.model.PetStage
import com.petlife.app.engine.CheckinEngine
import com.petlife.app.engine.FoodCatalog
import com.petlife.app.engine.GrowthEngine
import com.petlife.app.engine.SettlementEngine
import com.petlife.app.engine.VitalityEngine
import com.petlife.app.engine.WeekBudget
import com.petlife.app.usage.UsageStatsReader
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * 统一数据门面：宠物、签到、喂食、监控应用、使用时长、领取、结算、统计。
 * 应用完全本地运行，无网络权限。
 */
class PetLifeRepository(
    context: Context,
    private val usageReader: UsageStatsReader,
) {
    private val db = PetLifeDatabase.get(context)
    private val prefs = Prefs(context)
    private val appContext = context.applicationContext

    // ---------- 观察流 ----------

    val onboardingDone: Flow<Boolean> = prefs.onboarded

    suspend fun markOnboarded() = prefs.markOnboarded()

    /**
     * "今天"观察键：进程跨午夜保持打开时，回前台（onAppOpened）刷新为新的本地日期，
     * 使用时长与领取记录的查询随之切换到新的一天。
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val observeDateKey = kotlinx.coroutines.flow.MutableStateFlow(DateUtils.todayStr())

    val pet: Flow<PetEntity?> = db.petDao().observe()
    val inventory = db.inventoryDao().observeAll()
    val monitoredApps = db.monitoredAppDao().observeAll()
    val recentCheckins = db.checkinDao().observeRecent(30)
    val recentGoalChanges = db.goalChangeDao().observeRecent(50)
    val recentRecords = db.dailyRecordDao().observeRecent(14)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val recentClaims: Flow<List<TaskClaimEntity>> =
        observeDateKey.flatMapLatest { db.taskClaimDao().observeDate(it) }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val usageToday: Flow<List<AppUsageDayEntity>> =
        observeDateKey.flatMapLatest { db.usageDao().observeDay(it) }

    fun usageDayFlow(date: String): Flow<List<AppUsageDayEntity>> = db.usageDao().observeDay(date)

    suspend fun dailyRecordsRange(from: LocalDate, to: LocalDate): List<DailyRecordEntity> =
        db.dailyRecordDao().rangeAsc(DateUtils.dateStr(from), DateUtils.dateStr(to))

    // ---------- 初始化 ----------

    /** 首次启动：创建宠物并发放新手礼包 */
    suspend fun ensurePetCreated() {
        if (db.petDao().get() == null) {
            db.petDao().upsert(
                PetEntity(birthEpochDay = DateUtils.epochDay(DateUtils.today()), lastFedAtMillis = System.currentTimeMillis()),
            )
            db.inventoryDao().grant("apple", 3)
            db.inventoryDao().grant("milk", 1)
            db.inventoryDao().grant("rainbow", 1)
        }
        if (prefs.lastSettledDateOnce() == null) {
            prefs.setLastSettledDate(DateUtils.dateStr(DateUtils.yesterday()))
        }
    }

    // ---------- 签到 ----------

    data class CheckinResult(val streak: Int, val rewardsText: String, val alreadyDone: Boolean)

    suspend fun checkinToday(): CheckinResult {
        val today = DateUtils.todayStr()
        val existing = db.checkinDao().get(today)
        if (existing != null) {
            return CheckinResult(existing.streak, existing.rewardsText, alreadyDone = true)
        }
        val last = db.checkinDao().latest()
        val streak = CheckinEngine.streakFor(last?.date, last?.streak ?: 0)
        val grants = CheckinEngine.rewardsFor(streak)
        val text = CheckinEngine.rewardsText(grants)
        db.checkinDao().insert(
            CheckinEntity(date = today, streak = streak, rewardsText = text, millis = System.currentTimeMillis()),
        )
        for (g in grants) db.inventoryDao().grant(g.foodId, g.count)
        db.expEventDao().insert(
            ExpEventEntity(date = today, millis = System.currentTimeMillis(), source = "CHECKIN", foodId = null, exp = 0, moodDelta = 2),
        )
        // 签到给宠物一点点心情
        db.petDao().get()?.let { p ->
            db.petDao().upsert(p.copy(mood = SettlementEngine.clampMood(p.mood + 2)))
        }
        return CheckinResult(streak, text, alreadyDone = false)
    }

    // ---------- 喂食 ----------

    data class FeedResult(
        val success: Boolean,
        val message: String,
        val expApplied: Int = 0,
        val levelsGained: Int = 0,
        val stageChanged: Boolean = false,
        val newStage: PetStage? = null,
    )

    suspend fun feed(foodId: String): FeedResult {
        val item = db.inventoryDao().get(foodId)
        if (item == null || item.quantity <= 0) return FeedResult(false, "背包里没有这个食物啦")
        val pet = db.petDao().get() ?: return FeedResult(false, "宠物不见了…")
        val def = FoodCatalog.get(foodId)
        val coeff = todayCoefficientPreview().growthCoefficient

        db.inventoryDao().consume(foodId, 1)
        val now = System.currentTimeMillis()
        val result = GrowthEngine.applyExp(pet, def.exp, coeff, now)
        val newPet = result.pet.copy(
            mood = SettlementEngine.clampMood(pet.mood + def.mood),
            health = SettlementEngine.clampHealth(pet.health + VitalityEngine.FEED_HEALTH_GAIN),
            lastInteractedMillis = now,
        )
        db.petDao().upsert(newPet)
        db.expEventDao().insert(
            ExpEventEntity(date = DateUtils.todayStr(), millis = now, source = "FEED", foodId = foodId, exp = result.expApplied, moodDelta = def.mood),
        )

        val stageMsg = when {
            result.stageChanged -> " 🎉 进化到「${GrowthEngine.stageOf(newPet.level).label}」啦！"
            result.levelsGained > 0 -> " ⬆️ 升到 Lv.${newPet.level}！"
            else -> ""
        }
        return FeedResult(
            success = true,
            message = "${def.emoji} ${def.name}：+${result.expApplied} 经验$stageMsg",
            expApplied = result.expApplied,
            levelsGained = result.levelsGained,
            stageChanged = result.stageChanged,
            newStage = if (result.stageChanged) GrowthEngine.stageOf(newPet.level) else null,
        )
    }

    /** 点击撸宠：小心情加成（有冷却与每日上限） */
    suspend fun tapPet(): Boolean {
        val pet = db.petDao().get() ?: return false
        val now = System.currentTimeMillis()
        if (now - pet.lastInteractedMillis < VitalityEngine.TAP_COOLDOWN_MS) return false
        val allowed = prefs.registerTap(DateUtils.todayStr(), VitalityEngine.TAP_DAILY_LIMIT)
        if (!allowed) return false
        db.petDao().upsert(
            pet.copy(
                mood = SettlementEngine.clampMood(pet.mood + VitalityEngine.TAP_MOOD_GAIN),
                lastInteractedMillis = now,
            ),
        )
        return true
    }

    /** 改名；返回 false 表示宠物行不存在（不应发生——onAppOpened 已兜底创建） */
    suspend fun renamePet(name: String): Boolean {
        val pet = db.petDao().get() ?: return false
        val trimmed = name.trim().take(12)
        if (trimmed.isEmpty()) return false
        db.petDao().upsert(pet.copy(name = trimmed))
        return true
    }

    /** 主动交流频率控制：距上次不足间隔则拒绝（v1.1.0） */
    suspend fun tryProactive(minIntervalMs: Long): Boolean = prefs.tryProactive(minIntervalMs)

    // ---------- 监控应用管理（含每周修改额度） ----------

    data class InstalledApp(val packageName: String, val label: String, val iconBitmap: android.graphics.Bitmap?)

    /**
     * 列出可启动的应用（在后台线程调用）。
     * v1.0.1：图标在此预渲染为 Bitmap，避免选择列表滚动时逐项重绘自适应图标造成卡顿。
     */
    fun installedLaunchableApps(iconSizePx: Int = 96): List<InstalledApp> {
        val pm = appContext.packageManager
        val result = mutableListOf<InstalledApp>()
        val self = appContext.packageName
        for (app in pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (app.packageName == self) continue
            if (pm.getLaunchIntentForPackage(app.packageName) == null) continue
            val label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(app.packageName)
            val drawable = runCatching { pm.getApplicationIcon(app.packageName) }.getOrNull()
            val bitmap = drawable?.let { d ->
                runCatching {
                    val w = if (d.intrinsicWidth > 0) d.intrinsicWidth.coerceAtMost(iconSizePx) else iconSizePx
                    val h = if (d.intrinsicHeight > 0) d.intrinsicHeight.coerceAtMost(iconSizePx) else iconSizePx
                    val bmp = android.graphics.Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    d.setBounds(0, 0, w, h)
                    d.draw(canvas)
                    bmp
                }.getOrNull()
            }
            result += InstalledApp(app.packageName, label, bitmap)
        }
        return result.sortedBy { it.label }
    }

    fun appIcon(pkg: String): Drawable? = runCatching {
        appContext.packageManager.getApplicationIcon(pkg)
    }.getOrNull()

    /** 包名 → 中文应用名（诊断卡展示用；解析失败回退包名尾段） */
    fun labelFor(pkg: String): String = try {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (t: Throwable) {
        pkg.substringAfterLast('.')
    }

    data class WeekBudgetState(val weekKey: String, val used: Int, val remaining: Int)

    suspend fun weekBudget(): WeekBudgetState {
        val (lastDate, lastWeekKey) = prefs.guardState()
        val (key, currentDate) = DateUtils.effectiveWeekKeyWithGuard(lastDate, lastWeekKey)
        prefs.updateGuard(currentDate, key)
        val used = db.goalChangeDao().modifyCount(key)
        return WeekBudgetState(key, used, WeekBudget.compute(key, used).remaining)
    }

    sealed class AddOrEditResult {
        data class Success(val message: String) : AddOrEditResult()
        data class QuotaExhausted(val message: String) : AddOrEditResult()
    }

    /**
     * 添加应用（首次设置目标不消耗周额度）。
     */
    suspend fun addMonitoredApp(pkg: String, appName: String, category: AppCategory, targetMinutes: Int): AddOrEditResult {
        if (db.monitoredAppDao().get(pkg) != null) {
            return AddOrEditResult.Success("该应用已在监控列表中")
        }
        val (weekKey, guardDate) = DateUtils.effectiveWeekKeyWithGuard(guardLastDate(), guardLastWeekKey())
        val today = DateUtils.epochDay(DateUtils.today())
        db.monitoredAppDao().upsert(
            MonitoredAppEntity(
                packageName = pkg,
                appName = appName,
                category = category.name,
                dailyTargetMinutes = targetMinutes,
                createdAtEpochDay = today,
                lastModifiedEpochDay = today,
                lastModifiedWeekKey = weekKey,
            ),
        )
        db.goalChangeDao().insert(
            GoalChangeEntity(
                date = DateUtils.dateStr(DateUtils.today()),
                weekKey = weekKey,
                packageName = pkg,
                appName = appName,
                changeType = "SET",
                field = if (category == AppCategory.LEARN) "TARGET" else "LIMIT",
                oldValueMinutes = 0,
                newValueMinutes = targetMinutes,
            ),
        )
        prefs.updateGuard(guardDate, weekKey)
        return AddOrEditResult.Success("已添加 ${appName.take(8)}：${category.label}目标 ${targetMinutes} 分钟/天")
    }

    /** 修改目标/限额（消耗每周修改额度；额度用尽则拒绝） */
    suspend fun modifyTarget(pkg: String, newMinutes: Int): AddOrEditResult {
        val app = db.monitoredAppDao().get(pkg) ?: return AddOrEditResult.Success("应用不存在")
        val budget = weekBudget()
        if (budget.remaining <= 0) {
            return AddOrEditResult.QuotaExhausted("本周 2 次修改机会已用完，下周一 00:00 自动恢复")
        }
        val (weekKey, guardDate) = DateUtils.effectiveWeekKeyWithGuard(guardLastDate(), guardLastWeekKey())
        val today = DateUtils.epochDay(DateUtils.today())
        db.monitoredAppDao().upsert(
            app.copy(
                dailyTargetMinutes = newMinutes,
                lastModifiedEpochDay = today,
                lastModifiedWeekKey = weekKey,
            ),
        )
        db.goalChangeDao().insert(
            GoalChangeEntity(
                date = DateUtils.dateStr(DateUtils.today()),
                weekKey = weekKey,
                packageName = pkg,
                appName = app.appName,
                changeType = "MODIFY",
                field = if (AppCategory.from(app.category) == AppCategory.LEARN) "TARGET" else "LIMIT",
                oldValueMinutes = app.dailyTargetMinutes,
                newValueMinutes = newMinutes,
            ),
        )
        prefs.updateGuard(guardDate, weekKey)
        return AddOrEditResult.Success(
            "已修改：${app.appName.take(8)} ${app.dailyTargetMinutes} → ${newMinutes} 分钟（本周剩余 ${budget.remaining - 1} 次）",
        )
    }

    suspend fun removeMonitoredApp(pkg: String) {
        db.monitoredAppDao().delete(pkg)
    }

    private suspend fun guardLastDate(): String? = prefs.guardState().first
    private suspend fun guardLastWeekKey(): String? = prefs.guardState().second

    // ---------- 使用时长 ----------

    /** 最近一次使用时长刷新的诊断报告（设置页可视化展示） */
    data class UsageRefreshReport(
        val atMillis: Long,
        val hasPermission: Boolean,
        val permissionMode: String,
        val from: String,
        val to: String,
        val eventCount: Int,
        val intervalsCount: Int,
        val fallbackUsed: Boolean,
        val crossFillUsed: Boolean,
        val packagesCount: Int,
        val totalMinutes: Long,
        val topEntries: List<Pair<String, Long>>,
        val error: String?,
        val elapsedMs: Long,
    )

    private val _lastUsageReport = kotlinx.coroutines.flow.MutableStateFlow<UsageRefreshReport?>(null)
    val lastUsageReport: kotlinx.coroutines.flow.StateFlow<UsageRefreshReport?> = _lastUsageReport

    fun hasUsagePermission(): Boolean = usageReader.hasPermission()

    fun permissionModeName(): String = usageReader.permissionState().modeName

    fun openUsageAccessSettings(fromActivity: android.app.Activity? = null) =
        usageReader.openUsageAccessSettings(fromActivity)

    /**
     * 刷新指定日期区间的使用时长缓存（默认今天）。
     * v1.0.1：不再静默失败——返回诊断报告并记录日志；查询在后台线程执行（不阻塞主线程）。
     */
    suspend fun refreshUsage(from: LocalDate = DateUtils.today(), to: LocalDate = DateUtils.today()): UsageRefreshReport {
        val startedAt = System.currentTimeMillis()
        val permission = usageReader.permissionState()
        if (!permission.granted) {
            android.util.Log.w("PetLifeStats", "刷新使用时长被跳过：使用情况访问权限未授予（mode=${permission.modeName}）")
            val report = UsageRefreshReport(
                atMillis = startedAt, hasPermission = false, permissionMode = permission.modeName,
                from = DateUtils.dateStr(from), to = DateUtils.dateStr(to),
                eventCount = 0, intervalsCount = 0, fallbackUsed = false, crossFillUsed = false,
                packagesCount = 0, totalMinutes = 0, topEntries = emptyList(),
                error = "权限未授予", elapsedMs = 0,
            )
            _lastUsageReport.value = report
            return report
        }
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val result = usageReader.queryDailyUsageDetailed(from, to)
            // ★ 数据保护：查询出错且拿不到任何数据时，绝不 clear 旧缓存（防止瞬时故障抹掉历史记录）
            if (result.error != null && result.byDay.isEmpty()) {
                android.util.Log.w("PetLifeStats", "使用时长查询失败且无数据，本次跳过写库以保护已有记录：${result.error}")
                val report = UsageRefreshReport(
                    atMillis = System.currentTimeMillis(),
                    hasPermission = true,
                    permissionMode = permission.modeName,
                    from = DateUtils.dateStr(from),
                    to = DateUtils.dateStr(to),
                    eventCount = result.eventCount,
                    intervalsCount = result.intervalsCount,
                    fallbackUsed = result.fallbackUsed,
                    crossFillUsed = result.crossFillUsed,
                    packagesCount = 0,
                    totalMinutes = 0,
                    topEntries = emptyList(),
                    error = result.error,
                    elapsedMs = result.elapsedMs,
                )
                _lastUsageReport.value = report
                return@withContext report
            }
            val rows = mutableListOf<AppUsageDayEntity>()
            var d = from
            while (!d.isAfter(to)) {
                val key = DateUtils.dateStr(d)
                db.usageDao().clearDate(key)
                result.byDay[key]?.forEach { (pkg, minutes) ->
                    if (minutes > 0) rows += AppUsageDayEntity(key, pkg, minutes)
                }
                d = d.plusDays(1)
            }
            if (rows.isNotEmpty()) db.usageDao().upsertAll(rows)
            val top = result.byDay.values
                .flatMap { it.entries }
                .groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }
                .entries
                .sortedByDescending { it.value }
                .take(5)
                .map { it.key to it.value }
            val report = UsageRefreshReport(
                atMillis = System.currentTimeMillis(),
                hasPermission = true,
                permissionMode = permission.modeName,
                from = DateUtils.dateStr(from),
                to = DateUtils.dateStr(to),
                eventCount = result.eventCount,
                intervalsCount = result.intervalsCount,
                fallbackUsed = result.fallbackUsed,
                crossFillUsed = result.crossFillUsed,
                packagesCount = rows.size,
                totalMinutes = rows.sumOf { it.minutes },
                topEntries = top,
                error = result.error,
                elapsedMs = result.elapsedMs,
            )
            _lastUsageReport.value = report
            report
        }
    }

    suspend fun usageMapFor(date: String): Map<String, Long> =
        db.usageDao().day(date).associate { it.packageName to it.minutes }

    // ---------- 学习任务领取 ----------

    sealed class ClaimResult {
        data class Success(val rewardText: String, val moodGain: Int) : ClaimResult()
        data class NotReached(val need: Long) : ClaimResult()
        object AlreadyClaimed : ClaimResult()
        object Failed : ClaimResult()
    }

    /** 学习任务达标后领取食物奖励（每应用每天一次，防止重复领取） */
    suspend fun claimTask(pkg: String): ClaimResult {
        val today = DateUtils.todayStr()
        if (db.taskClaimDao().get(today, pkg) != null) return ClaimResult.AlreadyClaimed
        val app = db.monitoredAppDao().get(pkg) ?: return ClaimResult.Failed
        val used = usageMapFor(today)[pkg] ?: 0L
        if (used < app.dailyTargetMinutes) {
            return ClaimResult.NotReached(app.dailyTargetMinutes - used)
        }
        // 奖励按目标时长分档，透明可预期
        val grants = mutableListOf(CheckinEngine.Grant("apple", 1, "学习任务"))
        if (app.dailyTargetMinutes >= 60) grants += CheckinEngine.Grant("milk", 1, "学习任务")
        if (app.dailyTargetMinutes >= 120) grants += CheckinEngine.Grant("sushi", 1, "学习任务")
        if (app.dailyTargetMinutes >= 180) grants += CheckinEngine.Grant("cake", 1, "学习任务")
        val text = CheckinEngine.rewardsText(grants)
        db.taskClaimDao().insert(
            TaskClaimEntity(date = today, packageName = pkg, claimedAtMillis = System.currentTimeMillis(), rewardText = text),
        )
        for (g in grants) db.inventoryDao().grant(g.foodId, g.count)
        db.expEventDao().insert(
            ExpEventEntity(date = today, millis = System.currentTimeMillis(), source = "CLAIM", foodId = null, exp = 0, moodDelta = 8),
        )
        db.petDao().get()?.let { p ->
            db.petDao().upsert(p.copy(mood = SettlementEngine.clampMood(p.mood + 8), lastInteractedMillis = System.currentTimeMillis()))
        }
        return ClaimResult.Success(text, 8)
    }

    // ---------- 今日实时预览（首页展示） ----------

    suspend fun todayCoefficientPreview(): DayEvaluation {
        val today = DateUtils.todayStr()
        val usage = usageMapFor(today)
        val apps = db.monitoredAppDao().getAll()
        val checkedIn = db.checkinDao().get(today) != null
        val last = db.checkinDao().latest()
        val streak = if (checkedIn) (last?.streak ?: 1) else CheckinEngine.streakFor(last?.date, last?.streak ?: 0)
        val expGained = db.expEventDao().sumExpForDate(today)
        val foodsFed = db.expEventDao().feedCountForDate(today)
        val eval = SettlementEngine.evaluate(today, usage, apps, expGained, foodsFed, checkedIn)
        // 实时系数考虑连击加成
        val withStreak = eval.copy(
            growthCoefficient = GrowthEngine.growthCoefficient(
                eval.tasksCompleted, eval.tasksTotal, checkedIn, streak,
                eval.overApps.size, eval.overTotalMinutes > 60,
            ),
        )
        return withStreak
    }

    // ---------- 每日结算 ----------

    /** 补算所有未结算的过去日期（最多回溯 14 天），幂等 */
    suspend fun settlePendingDays() {
        val last = prefs.lastSettledDateOnce() ?: DateUtils.dateStr(DateUtils.yesterday())
        val lastSettled = runCatching { DateUtils.parseDate(last) }.getOrElse { return }
        val today = DateUtils.today()
        if (!today.isAfter(lastSettled)) return  // 今天还没结束 / 时间回拨保护

        // 刷新结算区间（最多 14 天）的使用缓存
        val from = maxOf(lastSettled.plusDays(1), today.minusDays(14))
        refreshUsage(from, DateUtils.yesterday())

        var d = from
        while (d.isBefore(today)) {
            settleOneDay(d)
            prefs.setLastSettledDate(DateUtils.dateStr(d))
            d = d.plusDays(1)
        }
    }

    /** 结算具体一天（幂等：同日重复结算只覆盖记录，宠物扣减不叠加） */
    suspend fun settleOneDay(day: LocalDate) {
        val dateStr = DateUtils.dateStr(day)
        val usage = usageMapFor(dateStr)
        val apps = db.monitoredAppDao().getAll().filter { it.createdAtEpochDay <= DateUtils.epochDay(day) }
        val expGained = db.expEventDao().sumExpForDate(dateStr)
        val foodsFed = db.expEventDao().feedCountForDate(dateStr)
        val checkedIn = db.checkinDao().get(dateStr) != null

        val eval = SettlementEngine.evaluate(dateStr, usage, apps, expGained, foodsFed, checkedIn)

        // 宠物状态：仅当日尚未结算过时应用一次
        val already = db.dailyRecordDao().get(dateStr) != null
        if (!already) {
            db.petDao().get()?.let { pet ->
                val delta = SettlementEngine.petDeltaFor(eval, pet, DateUtils.endOfDayMillis(day))
                db.petDao().upsert(
                    pet.copy(
                        mood = SettlementEngine.clampMood(pet.mood + delta.moodDelta),
                        health = SettlementEngine.clampHealth(pet.health + delta.healthDelta),
                    ),
                )
            }
        }
        val petNow = db.petDao().get()
        db.dailyRecordDao().upsert(
            SettlementEngine.buildRecord(eval, petNow?.mood ?: 0, petNow?.health ?: 0, System.currentTimeMillis()),
        )
    }

    /** 冷启动/回前台时调用：确保宠物存在 → 补算 → 刷新今日缓存 → 记录打开时间（全部在后台线程） */
    suspend fun onAppOpened() {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            // v1.0.1 修复：宠物行创建此前没有任何调用方，导致经验显示 null、改名静默失败
            runCatching { ensurePetCreated() }
                .onFailure { android.util.Log.w("PetLifeStats", "ensurePetCreated 失败：${it.message}") }
            observeDateKey.value = DateUtils.todayStr()
            runCatching { settlePendingDays() }
            runCatching { refreshUsage() }
            runCatching { prefs.markOpened() }
        }
    }

    /** 距上次打开应用的小时数（<0 表示无记录） */
    suspend fun hoursSinceLastOpen(): Float {
        val last = prefs.lastOpenAtOnce()
        if (last <= 0L) return -1f
        return (System.currentTimeMillis() - last) / 3_600_000f
    }

    // ---------- 危险操作 ----------

    suspend fun resetAllData() {
        db.petDao().clear()
        db.checkinDao().clear()
        db.inventoryDao().clear()
        db.monitoredAppDao().clear()
        db.usageDao().clear()
        db.taskClaimDao().clear()
        db.goalChangeDao().clear()
        db.dailyRecordDao().clear()
        db.expEventDao().clear()
        prefs.setLastSettledDate(DateUtils.dateStr(DateUtils.yesterday()))
        ensurePetCreated()
    }
}
