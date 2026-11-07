package com.petlife.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petlife.app.ServiceLocator
import com.petlife.app.core.DateUtils
import com.petlife.app.core.FormatUtils
import com.petlife.app.data.db.GoalChangeEntity
import com.petlife.app.data.db.MonitoredAppEntity
import com.petlife.app.data.model.AppCategory
import com.petlife.app.data.repo.PetLifeRepository
import com.petlife.app.engine.WeekBudget
import com.petlife.app.ui.components.ConfirmDialog
import com.petlife.app.ui.components.EmptyHint
import com.petlife.app.ui.components.NumberInputDialog
import com.petlife.app.ui.components.PetCard
import com.petlife.app.ui.components.SectionTitle
import com.petlife.app.ui.theme.Coral
import com.petlife.app.ui.theme.Cream100
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary
import com.petlife.app.ui.theme.InkTertiary
import com.petlife.app.ui.theme.Mint
import com.petlife.app.ui.theme.SkyBlue
import com.petlife.app.ui.theme.WarnRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repo: PetLifeRepository) : ViewModel() {

    val apps = repo.monitoredApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goalChanges = repo.recentGoalChanges.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pet = repo.pet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val budget = MutableStateFlow<PetLifeRepository.WeekBudgetState?>(null)
    val hasUsagePermission = MutableStateFlow(false)
    val permissionMode = MutableStateFlow("")
    val usageReport = repo.lastUsageReport
    val message = com.petlife.app.core.MessageBus()

    // ---- v1.1.0 天气 ----
    private val weatherStore get() = com.petlife.app.ServiceLocator.weatherStore
    val weatherEnabled = weatherStore.enabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val weatherCity = weatherStore.city.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val weather = weatherStore.weather.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val cityResults = MutableStateFlow<List<com.petlife.app.pet.weather.CityResult>>(emptyList())
    val citySearching = MutableStateFlow(false)

    // ---- v1.1.0 通知 / 桌面 ----
    private val prefs = com.petlife.app.ServiceLocator.prefs
    val notifyEnabled = prefs.notifyEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            hasUsagePermission.value = runCatching { repo.hasUsagePermission() }.getOrDefault(false)
            permissionMode.value = runCatching { repo.permissionModeName() }.getOrDefault("?")
            budget.value = runCatching { repo.weekBudget() }.getOrNull()
        }
    }

    // ---------- v1.1.0 天气 ----------

    fun searchCity(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            citySearching.value = true
            cityResults.value = runCatching { weatherStore.searchCity(query) }.getOrDefault(emptyList())
            citySearching.value = false
        }
    }

    fun setWeatherEnabled(v: Boolean) {
        viewModelScope.launch { weatherStore.setEnabled(v) }
    }

    /** 选择城市：显示名优先用用户输入的中文（英文库记录名可能是 "Changsha"） */
    fun setCity(c: com.petlife.app.pet.weather.CityResult, userInput: String) {
        viewModelScope.launch {
            val isAscii = c.name.all { it.code < 0x2E80 }
            val displayName = if (isAscii && userInput.isNotBlank()) userInput.trim() else c.name
            weatherStore.setCity(com.petlife.app.data.repo.Prefs.WeatherCity(displayName, c.latitude, c.longitude))
            cityResults.value = emptyList()
            message.post("已选择城市：$displayName，正在获取天气…")
        }
    }

    // ---------- v1.1.0 通知 ----------

    fun setNotifyEnabled(context: android.content.Context, v: Boolean) {
        viewModelScope.launch {
            prefs.setNotifyEnabled(v)
            if (v) com.petlife.app.work.CompanionWorker.schedule(context) else com.petlife.app.work.CompanionWorker.cancel(context)
            message.post(if (v) "每日 21:30 左右会收到宠物的问候通知" else "已关闭问候通知")
        }
    }

    // ---------- v1.1.0 桌面自定义 ----------

    fun applyLauncherPreset(context: android.content.Context, key: String) {
        val ok = com.petlife.app.core.LauncherUtil.applyPreset(context, key)
        message.post(if (ok) "桌面入口已切换，桌面图标可能延迟 1~2 秒刷新" else "切换失败，请重试")
    }

    fun pinCustomShortcut(context: android.content.Context, name: String, bitmap: android.graphics.Bitmap?) {
        val ok = com.petlife.app.core.LauncherUtil.pinCustomShortcut(context, name, bitmap)
        message.post(
            if (ok) "已请求创建桌面快捷方式，请在弹窗中确认"
            else "当前桌面不支持钉选快捷方式（可长按应用图标创建）",
        )
    }

    fun openUsageSettings(activity: android.app.Activity?) {
        repo.openUsageAccessSettings(activity)
    }

    fun refreshUsageNow() {
        viewModelScope.launch {
            val r = runCatching { repo.refreshUsage() }.getOrNull() ?: return@launch
            message.post(
                if (!r.hasPermission) "权限未授予，无法统计"
                else "统计完成：${r.packagesCount} 个应用共 ${FormatUtils.minutesHuman(r.totalMinutes)}（事件 ${r.eventCount} 条，耗时 ${r.elapsedMs}ms）",
            )
            refresh()
        }
    }

    /** 包名 → 应用名（诊断卡展示） */
    fun labelFor(pkg: String): String = runCatching { repo.labelFor(pkg) }.getOrDefault(pkg)

    fun removeApp(pkg: String) {
        viewModelScope.launch {
            repo.removeMonitoredApp(pkg)
            message.post("已移除监控（历史数据保留）")
            refresh()
        }
    }

    fun modifyTarget(pkg: String, minutes: Int) {
        viewModelScope.launch {
            val r = repo.modifyTarget(pkg, minutes)
            when (r) {
                is PetLifeRepository.AddOrEditResult.Success -> message.post(r.message)
                is PetLifeRepository.AddOrEditResult.QuotaExhausted -> message.post(r.message)
            }
            refresh()
        }
    }

    fun renamePet(name: String, onDone: () -> Unit) {
        viewModelScope.launch {
            val ok = repo.renamePet(name)
            val current = runCatching { repo.pet.first() }.getOrNull()?.name ?: "?"
            message.post(if (ok) "已改名为「$current」" else "改名失败：宠物数据尚未初始化，请回首页后重试")
            onDone()
        }
    }

    fun resetAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.resetAllData()
            message.post("已重置全部数据，宠物重新出生了")
            onDone()
        }
    }
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(key = "settings") { SettingsViewModel(ServiceLocator.repository) },
    onToast: (String) -> Unit,
) {
    val apps by vm.apps.collectAsState()
    val changes by vm.goalChanges.collectAsState()
    val budget by vm.budget.collectAsState()
    val hasPermission by vm.hasUsagePermission.collectAsState()
    val permissionMode by vm.permissionMode.collectAsState()
    val report by vm.usageReport.collectAsState()
    val petName by vm.pet.collectAsState()
    val currentName = petName?.name ?: "皮可"

    var editApp by remember { mutableStateOf<MonitoredAppEntity?>(null) }
    var deleteApp by remember { mutableStateOf<MonitoredAppEntity?>(null) }
    var showRename by remember { mutableStateOf(false) }
    var confirmResetStep by remember { mutableStateOf(0) }
    var showHistory by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity

    // v1.1.0：天气 / 通知 / 桌面自定义状态
    val weatherEnabled by vm.weatherEnabled.collectAsState()
    val weatherCity by vm.weatherCity.collectAsState()
    val weatherInfo by vm.weather.collectAsState()
    val cityResults by vm.cityResults.collectAsState()
    val citySearching by vm.citySearching.collectAsState()
    val notifyEnabled by vm.notifyEnabled.collectAsState()

    // 进入 + 回前台刷新（从系统设置授权回来后状态及时更新）
    val lifecycleOwner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        vm.message.events.collect { onToast(it) }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Column {
                Text("设置", style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text("皮可成长记 · 权限、监控与宠物管理", fontSize = 13.sp, color = InkSecondary)
            }
        }

        // ===== 权限 =====
        item { SectionTitle("🔐 应用监控权限") }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("使用情况访问权限", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                        Text(
                            if (hasPermission) "已授权（系统状态：$permissionMode）"
                            else "未授权：无法统计应用时长，任务页数字会全部为 0",
                            fontSize = 12.sp, color = if (hasPermission) Mint else WarnRed,
                        )
                    }
                    Switch(
                        checked = hasPermission,
                        onCheckedChange = { if (!it) vm.openUsageSettings(activity) },
                        colors = SwitchDefaults.colors(
                            checkedTrackColor = Mint,
                            uncheckedTrackColor = Cream100,
                        ),
                    )
                }
            }
        }

        // ===== 诊断（Bug 1 配套） =====
        item { SectionTitle("🩺 使用时长诊断", trailing = {
            TextButton(onClick = { vm.refreshUsageNow() }) { Text("立即重新统计", fontSize = 12.sp, color = Coral) }
        }) }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                val r = report
                if (r == null) {
                    Text("还没有统计记录：回到首页/任务页会自动统计一次", fontSize = 13.sp, color = InkTertiary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        DiagRow("权限", if (r.hasPermission) "已授权（${r.permissionMode}）" else "未授予 ⚠️")
                        DiagRow("统计范围", "${r.from} ~ ${r.to}")
                        DiagRow("事件数量", "${r.eventCount} 条（前台区间 ${r.intervalsCount} 段）")
                        DiagRow("应用数量", "${r.packagesCount} 个，共 ${FormatUtils.minutesHuman(r.totalMinutes)}")
                        DiagRow("兜底策略", if (r.fallbackUsed) "事件缺失，使用系统桶数据" else if (r.crossFillUsed) "桶数据交叉补齐" else "事件流正常")
                        if (r.error != null) DiagRow("异常", r.error)
                        if (r.topEntries.isNotEmpty()) {
                            DiagRow("时长 Top3", r.topEntries.take(3).joinToString("、") { (pkg, m) -> "${vm.labelFor(pkg)} ${m}分" })
                        }
                        Text(
                            "排查提示：若权限已授权但应用数为 0，多为系统数据延迟，可稍后点「立即重新统计」；logcat 过滤 PetLifeStats 可查看完整日志。",
                            fontSize = 11.sp, color = InkTertiary,
                        )
                        // 崩溃日志（点宠物闪退等问题的自查入口）
                        var crashText by remember { mutableStateOf<String?>(null) }
                        val crashCtx = LocalContext.current
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            TextButton(onClick = { crashText = com.petlife.app.core.CrashGuard.lastTrace(crashCtx) }) {
                                Text("查看崩溃日志", fontSize = 12.sp, color = Coral)
                            }
                            if (crashText != null) {
                                TextButton(onClick = { com.petlife.app.core.CrashGuard.clear(crashCtx); crashText = null }) {
                                    Text("清除", fontSize = 12.sp, color = InkTertiary)
                                }
                            }
                        }
                        if (crashText != null) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .background(Cream100, RoundedCornerShape(12.dp))
                                    .padding(8.dp)
                                    .height(160.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(crashText ?: "", fontSize = 10.sp, color = Ink, lineHeight = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        // ===== 每周修改额度 =====
        item { SectionTitle("🎯 本周目标修改额度", trailing = {
            TextButton(onClick = { showHistory = !showHistory }) {
                Text(if (showHistory) "收起记录" else "查看记录", fontSize = 12.sp, color = Coral)
            }
        }) }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("剩余修改次数", fontSize = 13.sp, color = InkSecondary)
                        Text(
                            "${budget?.remaining ?: WeekBudget.WEEKLY_LIMIT} / ${WeekBudget.WEEKLY_LIMIT} 次",
                            fontSize = 24.sp, fontWeight = FontWeight.Black, color = Ink,
                        )
                        Text(
                            "规则：添加新应用并首次设置目标不消耗次数；修改学习目标/娱乐限额每次消耗 1 次；每周一 00:00 自动恢复",
                            fontSize = 11.sp, color = InkTertiary,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        repeat(WeekBudget.WEEKLY_LIMIT) { i ->
                            val used = i < (budget?.used ?: 0)
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(
                                        color = if (used) WarnRed else Mint,
                                        shape = CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
        }
        if (showHistory) {
            item {
                PetCard(Modifier.fillMaxWidth()) {
                    if (changes.isEmpty()) {
                        Text("暂无修改记录", fontSize = 13.sp, color = InkTertiary)
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            changes.take(20).forEach { c ->
                                GoalChangeRow(c)
                            }
                        }
                    }
                }
            }
        }

        // ===== 监控应用 =====
        item { SectionTitle("📱 监控应用管理") }
        if (apps.isEmpty()) {
            item { EmptyHint("还没有监控应用\n去「任务」页添加吧") }
        } else {
            items(apps.size) { idx ->
                val app = apps[idx]
                PetCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(AppCategory.from(app.category).emoji, fontSize = 14.sp)
                                Spacer(Modifier.size(6.dp))
                                Text(app.appName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink, maxLines = 1)
                            }
                            Text(
                                "${if (AppCategory.from(app.category) == AppCategory.LEARN) "学习目标" else "娱乐限额"}：${app.dailyTargetMinutes} 分钟/天",
                                fontSize = 12.sp, color = InkSecondary,
                            )
                        }
                        IconButton(onClick = { editApp = app }) {
                            Icon(Icons.Rounded.Edit, contentDescription = "修改", tint = Coral)
                        }
                        IconButton(onClick = { deleteApp = app }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = WarnRed)
                        }
                    }
                }
            }
        }

        // ===== 宠物 =====
        item { SectionTitle("🐾 宠物管理") }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐶", fontSize = 22.sp)
                    Spacer(Modifier.size(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("给宠物改名", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                        Text("当前：$currentName（应用品牌名「皮可成长记」不受影响）", fontSize = 12.sp, color = InkSecondary)
                    }
                    TextButton(onClick = { showRename = true }) { Text("改名", color = Coral) }
                }
            }
        }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("重置全部数据", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = WarnRed)
                        Text("宠物、库存、记录全部清空且不可恢复", fontSize = 12.sp, color = InkSecondary)
                    }
                    TextButton(onClick = { confirmResetStep = 1 }) { Text("重置", color = WarnRed, fontWeight = FontWeight.Bold) }
                }
            }
        }

        // ===== 天气（v1.1.0） =====
        item { SectionTitle("🌤 天气感知", trailing = {
            TextButton(onClick = { vm.setWeatherEnabled(!weatherEnabled) }) {
                Text(if (weatherEnabled) "关闭" else "开启", fontSize = 12.sp, color = Coral)
            }
        }) }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        if (weatherEnabled) "已开启 · 使用 Open-Meteo 免费服务（无需账号）"
                        else "开启后，宠物会根据天气和你聊天（下雨提醒带伞、高温提醒补水…）",
                        fontSize = 12.sp, color = if (weatherEnabled) Mint else InkSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    val currentCity = weatherCity
                    val currentWeather = weatherInfo
                    if (currentCity != null) {
                        Text(
                            "当前城市：${currentCity.name}" + (currentWeather?.let { "  ${it.emoji()} ${it.temperatureC.toInt()}°C ${it.description()}" } ?: ""),
                            fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink,
                        )
                    } else {
                        Text("还没有选择城市", fontSize = 14.sp, color = InkTertiary)
                    }
                    Spacer(Modifier.height(10.dp))
                    var cityQuery by remember { mutableStateOf("") }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        androidx.compose.material3.OutlinedTextField(
                            value = cityQuery,
                            onValueChange = { cityQuery = it.take(20) },
                            placeholder = { Text("输入城市名，如：杭州") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = { vm.searchCity(cityQuery) },
                            shape = RoundedCornerShape(50),
                            enabled = cityQuery.isNotBlank() && !citySearching,
                        ) { Text("搜索") }
                    }
                    if (cityResults.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Column {
                            cityResults.take(5).forEach { c ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { vm.setCity(c, cityQuery) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                ) {
                                    val pop = if (c.population > 0) " · ${(c.population / 10000).coerceAtLeast(1)}万人" else ""
                                    Text("${c.name} · ${c.admin1} ${c.country}$pop", fontSize = 14.sp, color = Ink)
                                }
                            }
                        }
                    }
                    Text(
                        "隐私说明：仅向 Open-Meteo 发送所选城市的经纬度；不上传任何使用记录或行为数据；可随时关闭。",
                        fontSize = 10.sp, color = InkTertiary,
                    )
                }
            }
        }

        // ===== 每日问候通知（v1.1.0） =====
        item { SectionTitle("🔔 每日问候通知") }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                ) { granted -> if (granted) vm.setNotifyEnabled(context, true) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("宠物每日问候", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                        Text(
                            "每天 21:30 左右，宠物会用一句话跟你说晚安。可随时关闭。",
                            fontSize = 12.sp, color = InkSecondary,
                        )
                    }
                    Switch(
                        checked = notifyEnabled,
                        onCheckedChange = { want ->
                            if (want && android.os.Build.VERSION.SDK_INT >= 33 &&
                                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                                android.content.pm.PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                vm.setNotifyEnabled(context, want)
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Mint, uncheckedTrackColor = Cream100),
                    )
                }
            }
        }

        // ===== 桌面名称与图标（v1.1.0） =====
        item { SectionTitle("🏠 桌面名称与图标") }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                Column {
                    Text("预设图标组合", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.petlife.app.core.LauncherUtil.PRESETS.forEach { preset ->
                            FilterChip(
                                selected = com.petlife.app.core.LauncherUtil.currentPreset(context) == preset.key,
                                onClick = { vm.applyLauncherPreset(context, preset.key) },
                                label = { Text("${preset.label} · ${preset.description.substringBefore(" ·")}") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Cream100, selectedLabelColor = Ink,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("自定义桌面快捷方式", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
                    Text(
                        "在桌面创建一个显示自定义名称/图标的快捷入口（应用本体的系统名称无法被应用修改，这是 Android 的限制）。",
                        fontSize = 11.sp, color = InkTertiary,
                    )
                    Spacer(Modifier.height(8.dp))
                    var customName by remember { mutableStateOf("") }
                    var pickedIcon by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                    val photoPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(),
                    ) { uri ->
                        if (uri != null) {
                            pickedIcon = com.petlife.app.core.LauncherUtil.decodeSquareIcon(context, uri)
                        }
                    }
                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it.take(12) },
                        placeholder = { Text("快捷方式名称，如：小白的家") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ))
                        }) { Text(if (pickedIcon == null) "选择图片" else "已选择图片 ✓", color = Coral) }
                        Spacer(Modifier.weight(1f))
                        Button(
                            onClick = { vm.pinCustomShortcut(context, customName.ifBlank { "星伴" }, pickedIcon) },
                            shape = RoundedCornerShape(50),
                        ) { Text("钉到桌面", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }

        // ===== 关于 =====
        item { SectionTitle("ℹ️ 关于") }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("星伴（PetLife）v1.1.0", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                    Text(
                        "和你的专属伙伴一起成长。养成核心完全本地运行；天气为唯一可选联网功能，可在上方关闭。",
                        fontSize = 12.sp, color = InkSecondary,
                    )
                    Text(
                        "宠物不会死亡：即使几天不照顾，它只会难过，永远等你回来。",
                        fontSize = 12.sp, color = Mint,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }

    // ===== 对话框们 =====
    editApp?.let { app ->
        NumberInputDialog(
            title = "修改${if (AppCategory.from(app.category) == AppCategory.LEARN) "学习目标" else "娱乐限额"}",
            initial = app.dailyTargetMinutes,
            hint = "修改将消耗本周 1 次修改机会（剩余 ${budget?.remaining ?: 0} 次）",
            onConfirm = {
                vm.modifyTarget(app.packageName, it)
                editApp = null
            },
            onDismiss = { editApp = null },
        )
    }
    deleteApp?.let { app ->
        ConfirmDialog(
            title = "移除「${app.appName}」？",
            body = "移除后不再监控该应用的使用时长，已有的历史记录保留。",
            confirmText = "移除",
            danger = true,
            onConfirm = {
                vm.removeApp(app.packageName)
                deleteApp = null
            },
            onDismiss = { deleteApp = null },
        )
    }
    if (showRename) {
        RenameDialog(
            current = currentName,
            onConfirm = { vm.renamePet(it) { showRename = false } },
            onDismiss = { showRename = false },
        )
    }
    when (confirmResetStep) {
        1 -> ConfirmDialog(
            title = "确认重置？",
            body = "所有成长、记录将清空，宠物回到出生状态。此操作不可撤销。",
            confirmText = "继续",
            danger = true,
            onConfirm = { confirmResetStep = 2 },
            onDismiss = { confirmResetStep = 0 },
        )
        2 -> ConfirmDialog(
            title = "最后确认",
            body = "真的要和现在的宠物告别吗？它会从头开始，但所有回忆都会消失。",
            confirmText = "狠心重置",
            danger = true,
            onConfirm = {
                confirmResetStep = 0
                vm.resetAll { }
            },
            onDismiss = { confirmResetStep = 0 },
        )
    }
}

@Composable
private fun DiagRow(label: String, value: String) {
    Row {
        Text("$label：", fontSize = 12.sp, color = InkTertiary)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Ink, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GoalChangeRow(c: GoalChangeEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (c.changeType) {
                "SET" -> "🆕"
                else -> "✏️"
            },
            fontSize = 14.sp,
        )
        Spacer(Modifier.size(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "${c.appName} ${if (c.changeType == "SET") "首次设置" else "修改"}${if (c.field == "TARGET") "学习目标" else "娱乐限额"}",
                fontSize = 13.sp, color = Ink, maxLines = 1,
            )
            Text(
                "${c.date}（${c.weekKey}）" + if (c.changeType == "MODIFY") "：${c.oldValueMinutes} → ${c.newValueMinutes} 分钟" else "：${c.newValueMinutes} 分钟",
                fontSize = 11.sp, color = InkTertiary,
            )
        }
        if (c.changeType == "MODIFY") {
            Text("消耗 1 次", fontSize = 11.sp, color = WarnRed)
        } else {
            Text("免费", fontSize = 11.sp, color = Mint)
        }
    }
}

@Composable
private fun RenameDialog(current: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("给宠物改名", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.imePadding()) {
                Text("1~12 个字符，所有页面会同步显示新名字", fontSize = 12.sp, color = InkSecondary)
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(12) },
                    placeholder = { Text(current) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
            ) { Text("确定", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) } },
        containerColor = androidx.compose.ui.graphics.Color.White,
        shape = RoundedCornerShape(22.dp),
    )
}
