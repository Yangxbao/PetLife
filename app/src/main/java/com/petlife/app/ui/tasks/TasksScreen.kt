package com.petlife.app.ui.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.petlife.app.core.FormatUtils
import com.petlife.app.core.MessageBus
import com.petlife.app.data.db.MonitoredAppEntity
import com.petlife.app.data.model.AppCategory
import com.petlife.app.data.repo.PetLifeRepository
import com.petlife.app.ui.components.EmptyHint
import com.petlife.app.ui.components.GoalProgress
import com.petlife.app.ui.components.PetCard
import com.petlife.app.ui.components.SectionTitle
import com.petlife.app.ui.settings.AppPickerSheet
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TasksViewModel(private val repo: PetLifeRepository) : ViewModel() {

    val apps = repo.monitoredApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val usageToday = repo.usageToday
        .map { list -> list.associate { it.packageName to it.minutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val claims = repo.recentClaims.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val hasPermission = MutableStateFlow(false)

    val refreshing = MutableStateFlow(false)
    val message = MessageBus()
    val weekBudget = MutableStateFlow<PetLifeRepository.WeekBudgetState?>(null)

    private fun claimLine(appName: String): String = runCatching {
        ServiceLocator.dialogueEngine.pick(
            com.petlife.app.pet.dialogue.DialogueRequest(
                event = "STUDY", mood = com.petlife.app.data.model.PetMoodState.HAPPY,
                hour = java.time.LocalTime.now().hour, petName = "皮可",
            ),
        )?.text
    }.getOrNull() ?: "学霸本霸！"

    init {
        refreshUsage()
    }

    /** 回前台/进入页面时调用：刷新权限状态 + 时长缓存 + 额度 */
    fun onScreenVisible() {
        viewModelScope.launch { hasPermission.value = runCatching { repo.hasUsagePermission() }.getOrDefault(false) }
        refreshUsage()
    }

    fun refreshUsage() {
        viewModelScope.launch {
            refreshing.value = true
            runCatching { repo.refreshUsage() }
            refreshing.value = false
            weekBudget.value = runCatching { repo.weekBudget() }.getOrNull()
        }
    }

    fun claim(pkg: String, appName: String, targetMinutes: Int) {
        viewModelScope.launch {
            when (val r = repo.claimTask(pkg)) {
                is PetLifeRepository.ClaimResult.Success -> {
                    message.post("🎉 领取成功：${r.rewardText}")
                    message.post(claimLine(appName))
                }
                is PetLifeRepository.ClaimResult.NotReached ->
                    message.post("还差 ${FormatUtils.minutesHuman(r.need)} 达到 ${targetMinutes} 分钟，继续加油！")
                PetLifeRepository.ClaimResult.AlreadyClaimed ->
                    message.post("这个任务今天已经领过啦，明天再来")
                PetLifeRepository.ClaimResult.Failed ->
                    message.post("领取失败，请稍后再试")
            }
        }
    }

    fun addApp(pkg: String, name: String, cat: AppCategory, minutes: Int) {
        viewModelScope.launch {
            val r = repo.addMonitoredApp(pkg, name, cat, minutes)
            when (r) {
                is PetLifeRepository.AddOrEditResult.Success -> message.post(r.message)
                is PetLifeRepository.AddOrEditResult.QuotaExhausted -> message.post(r.message)
            }
            weekBudget.value = runCatching { repo.weekBudget() }.getOrNull()
        }
    }
}

@Composable
fun TasksScreen(
    vm: TasksViewModel = viewModel(key = "tasks") { TasksViewModel(ServiceLocator.repository) },
    onToast: (String) -> Unit,
) {
    val apps by vm.apps.collectAsState()
    val usage by vm.usageToday.collectAsState()
    val claims by vm.claims.collectAsState()
    val refreshing by vm.refreshing.collectAsState()
    val budget by vm.weekBudget.collectAsState()
    val hasPermission by vm.hasPermission.collectAsState()

    var showPicker by remember { mutableStateOf(false) }
    val activity = LocalContext.current as? android.app.Activity

    // 进入页面 + 回前台时刷新（权限状态在系统设置里改变后能及时更新）
    val lifecycleOwner = LocalContext.current as? androidx.lifecycle.LifecycleOwner
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.onScreenVisible()
        }
        lifecycleOwner?.lifecycle?.addObserver(observer)
        onDispose { lifecycleOwner?.lifecycle?.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        vm.message.events.collect { onToast(it) }
    }

    val learnApps = apps.filter { AppCategory.from(it.category) == AppCategory.LEARN }
    val funApps = apps.filter { AppCategory.from(it.category) == AppCategory.FUN }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { Spacer(Modifier.height(8.dp)) }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("任务中心", style = MaterialTheme.typography.headlineSmall, color = Ink)
                        Text("学习达标领食物，娱乐控制别超标", fontSize = 13.sp, color = InkSecondary)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { vm.refreshUsage() }) {
                        if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Refresh, contentDescription = "刷新", tint = InkSecondary)
                    }
                }
            }

            // v1.0.1（Bug 1）：权限未授予时明确告知，而不是让用户以为"显示 0 是没使用软件"
            if (!hasPermission) {
                item {
                    PetCard(Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚠️", fontSize = 22.sp)
                            Spacer(Modifier.size(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("还没有「使用情况访问」权限", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink)
                                Text(
                                    "没有该权限无法统计应用时长（所有数字会显示 0）。点击右侧按钮去系统设置授权，回来后自动刷新。",
                                    fontSize = 12.sp, color = InkSecondary,
                                )
                            }
                            Spacer(Modifier.size(8.dp))
                            Button(
                                onClick = { ServiceLocator.repository.openUsageAccessSettings(activity) },
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = androidx.compose.ui.graphics.Color.White),
                            ) { Text("去授权", fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.AssistChip(
                        onClick = { showPicker = true },
                        label = { Text("＋ 添加应用", fontWeight = FontWeight.Bold) },
                        shape = RoundedCornerShape(50),
                    )
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        label = { Text("本周可改目标：${budget?.remaining ?: 2}/2 次") },
                        shape = RoundedCornerShape(50),
                    )
                }
            }

            item { SectionTitle("📚 学习任务（达标可领取食物奖励）") }
            if (learnApps.isEmpty()) {
                item { EmptyHint("还没有学习任务，点击「添加应用」开始\n例如：B站、百词斩、微信读书…") }
            } else {
                items(learnApps, key = { it.packageName }) { app ->
                    LearnTaskCard(app, usage[app.packageName] ?: 0L, claimed = claims.any { it.packageName == app.packageName }) {
                        vm.claim(app.packageName, app.appName, app.dailyTargetMinutes)
                    }
                }
            }

            item { SectionTitle("🎮 娱乐限额（超了皮可会难过）") }
            if (funApps.isEmpty()) {
                item { EmptyHint("还没有娱乐应用\n添加抖音、王者荣耀等，帮皮可监督你") }
            } else {
                items(funApps, key = { it.packageName }) { app ->
                    FunLimitCard(app, usage[app.packageName] ?: 0L)
                }
            }
            item { Spacer(Modifier.height(96.dp)) }
        }

        // 添加应用入口（右下浮动）
        Button(
            onClick = { showPicker = true },
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = androidx.compose.ui.graphics.Color.White),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .height(52.dp),
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Spacer(Modifier.size(4.dp))
            Text("添加应用", fontWeight = FontWeight.Bold)
        }
    }

    AppPickerSheet(
        visible = showPicker,
        onDismiss = { showPicker = false },
        onPicked = { pkg, name, cat, minutes ->
            showPicker = false
            vm.addApp(pkg, name, cat, minutes)
        },
    )
}

@Composable
private fun LearnTaskCard(app: MonitoredAppEntity, usedMinutes: Long, claimed: Boolean, onClaim: () -> Unit) {
    val target = app.dailyTargetMinutes
    val reached = usedMinutes >= target
    PetCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.appName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                Text(
                    "${FormatUtils.minutesClock(usedMinutes)} / $target 分钟",
                    fontSize = 13.sp, color = InkSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GoalProgress(ratio = if (target > 0) usedMinutes.toFloat() / target else 0f, over = false)
            }
            Spacer(Modifier.size(12.dp))
            Button(
                onClick = onClaim,
                enabled = reached && !claimed,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Mint,
                    contentColor = androidx.compose.ui.graphics.Color.White,
                    disabledContainerColor = Cream100,
                    disabledContentColor = InkTertiary,
                ),
            ) {
                Text(
                    when {
                        claimed -> "已领取"
                        reached -> "领取 🎁"
                        else -> "未达标"
                    },
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                )
            }
        }
        if (reached && !claimed) {
            Text(
                "✅ 已达标！奖励：小苹果×1${if (target >= 60) " + 热牛奶×1" else ""}${if (target >= 120) " + 寿司拼盘×1" else ""}",
                fontSize = 12.sp, color = Mint, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun FunLimitCard(app: MonitoredAppEntity, usedMinutes: Long) {
    val limit = app.dailyTargetMinutes
    val over = usedMinutes > limit
    val remaining = (limit - usedMinutes).coerceAtLeast(0)
    PetCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(app.appName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Ink, maxLines = 1)
                Text(
                    "${FormatUtils.minutesClock(usedMinutes)} / 限额 $limit 分钟",
                    fontSize = 13.sp, color = InkSecondary,
                )
                Spacer(Modifier.height(8.dp))
                GoalProgress(ratio = if (limit > 0) usedMinutes.toFloat() / limit else 0f, over = over)
            }
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    over -> "超标 ${FormatUtils.minutesHuman(usedMinutes - limit)}"
                    remaining <= 5 -> "只剩 ${remaining} 分钟！"
                    else -> "剩余 ${FormatUtils.minutesHuman(remaining)}"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (over) WarnRed else SkyBlue,
            )
        }
        if (over) {
            Text(
                "😿 超标会影响今天的自律评分和皮可的情绪，明天注意休息哦",
                fontSize = 12.sp, color = WarnRed, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
