package com.petlife.app.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petlife.app.ServiceLocator
import com.petlife.app.core.DateUtils
import com.petlife.app.core.FormatUtils
import com.petlife.app.core.MessageBus
import com.petlife.app.data.db.MonitoredAppEntity
import com.petlife.app.data.db.PetEntity
import com.petlife.app.data.model.AppCategory
import com.petlife.app.data.model.DayEvaluation
import com.petlife.app.data.model.PetMoodState
import com.petlife.app.data.model.PetStage
import com.petlife.app.data.repo.PetLifeRepository
import com.petlife.app.engine.GrowthEngine
import com.petlife.app.pet.dialogue.DialogueScheduler
import com.petlife.app.ui.components.GoalProgress
import com.petlife.app.ui.components.PetCard
import com.petlife.app.ui.components.StatChip
import com.petlife.app.ui.pet.HomeStage
import com.petlife.app.ui.pet.PetAction
import com.petlife.app.ui.pet.PetView
import com.petlife.app.ui.pet.PetVisualConfig
import com.petlife.app.ui.pet.character.MaidStageVisuals
import com.petlife.app.ui.pet.rememberPetActor
import com.petlife.app.ui.theme.Coral
import com.petlife.app.ui.theme.CoralSoft
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary
import com.petlife.app.ui.theme.InkTertiary
import com.petlife.app.ui.theme.Lavender
import com.petlife.app.ui.theme.Mint
import com.petlife.app.ui.theme.SkyBlue
import com.petlife.app.ui.theme.Sun
import com.petlife.app.ui.theme.WarnRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.random.Random

class HomeViewModel(private val repo: PetLifeRepository) : ViewModel() {

    val pet = repo.pet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val apps = repo.monitoredApps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val usageToday = repo.usageToday
        .map { list -> list.associate { it.packageName to it.minutes } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())
    val checkins = repo.recentCheckins.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayEval = MutableStateFlow<DayEvaluation?>(null)
    val bubble = MutableStateFlow<String?>(null)
    val toastBus = MessageBus()

    private var bubbleJob: kotlinx.coroutines.Job? = null
    private val scheduler: DialogueScheduler get() = ServiceLocator.dialogueScheduler
    private var greetedThisSession = false
    private var lonelyThisSession = false
    private var overWarnedSession = false

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            todayEval.value = runCatching { repo.todayCoefficientPreview() }.getOrNull()
        }
    }

    fun showBubble(text: String) {
        bubbleJob?.cancel()
        bubble.value = text
        bubbleJob = viewModelScope.launch {
            runCatching {
                delay(DialogueScheduler.BUBBLE_MS)
                bubble.value = null
                scheduler.onBubbleDismissed()
            }.onFailure { android.util.Log.e("PetLifeCrash", "气泡收起异常（已忽略）", it) }
        }
    }

    // ================= 点击：动作必触发，对话低概率 =================

    /** 点击宠物：始终返回一个互动动作；台词只以小概率出现（调度器裁决） */
    fun onTapPet(mood: PetMoodState, petName: String, firstTapOfSession: Boolean): Pair<PetAction, String?> {
        val action = when (mood) {
            PetMoodState.HAPPY -> listOf(
                PetAction.HAPPY_BOUNCE, PetAction.HAPPY_SHAKE, PetAction.SPIN, PetAction.JUMP,
                PetAction.WINK, PetAction.SPOILED,
            ).random()
            PetMoodState.CALM -> listOf(
                PetAction.HEAD_TILT, PetAction.NOD, PetAction.WINK, PetAction.SPIN,
                PetAction.HAPPY_BOUNCE, PetAction.SURPRISED,
            ).random()
            PetMoodState.SAD, PetMoodState.SICK -> listOf(
                PetAction.UPSET_DOWN, PetAction.HEAD_TILT, PetAction.SHY_COVER,
            ).random()
            PetMoodState.TIRED -> listOf(PetAction.YAWN, PetAction.STRETCH, PetAction.HEAD_TILT).random()
        }
        val probability = if (firstTapOfSession) 0.9f else DialogueScheduler.TAP_PROBABILITY
        val line = scheduler.tryTap(mood, petName, probability)
        return action to line
    }

    // ================= 事件（高优先级，总是产生台词，调度器负责仲裁） =================

    fun checkin(mood: PetMoodState, petName: String): Pair<PetAction, String?> {
        viewModelScope.launch {
            val r = repo.checkinToday()
            if (r.alreadyDone) {
                toastBus.post("今天已经签过到啦，明天再来～")
            } else {
                val line = scheduler.trySpeak("CHECKIN", mood = mood, petName = petName)
                    ?: "签到成功！连续 ${r.streak} 天！"
                showBubble(line)
                toastBus.post("签到成功！连续 ${r.streak} 天，获得：${r.rewardsText}")
            }
            refresh()
        }
        return PetAction.CELEBRATE to null
    }

    fun feedMessage(petName: String): String? = scheduler.trySpeak("FEED", mood = PetMoodState.HAPPY, petName = petName)

    fun onLevelUp(level: Int, petName: String): String =
        scheduler.trySpeak("LEVELUP", mood = PetMoodState.HAPPY, petName = petName)
            ?: "升级啦！现在是 Lv.$level！"

    fun onEvolve(stageLabel: String, petName: String): String =
        scheduler.trySpeak("EVOLVE", mood = PetMoodState.HAPPY, petName = petName)
            ?: "进化到「$stageLabel」啦！✨"

    fun onOverLimit(petName: String): String? =
        scheduler.trySpeak("OVERLIMIT", mood = null, petName = petName)

    // ================= 主动交流（单气泡，调度器仲裁） =================

    /** 打开应用：久未打开迎接 > 天气 > 时段问候，只出一条（话术引擎未就绪时自动重试） */
    fun greetOnOpen(mood: PetMoodState, petName: String, weatherKey: String?) {
        if (greetedThisSession) return
        greetedThisSession = true
        viewModelScope.launch {
            val hours = runCatching { repo.hoursSinceLastOpen() }.getOrDefault(-1f)
            var line: String? = null
            repeat(3) {
                line = scheduler.tryOpeningGreeting(mood, petName, weatherKey, hours)
                if (line != null) return@repeat
                delay(1200) // 等待话术引擎资产加载完成
            }
            line?.let { showBubble(it) }
        }
    }

    /** 停留期间随机闲聊（调度器内部 120s 冷却 + 全局间隔） */
    fun idleChat(mood: PetMoodState, petName: String) {
        if (bubble.value != null) return
        viewModelScope.launch {
            scheduler.trySpeak("CHAT", mood = mood, petName = petName)?.let { showBubble(it) }
        }
    }

    /** 天气首次到达反应（每会话一次） */
    fun weatherGreet(mood: PetMoodState, petName: String, weatherKey: String) {
        if (weatherKey == "ANY") return
        viewModelScope.launch {
            scheduler.trySpeak("WEATHER", mood = mood, weatherKey = weatherKey, petName = petName)
                ?.let { showBubble(it) }
        }
    }
}

@Composable
fun HomeScreen(
    vm: HomeViewModel = viewModel(key = "home") { HomeViewModel(ServiceLocator.repository) },
    onNavigateBag: () -> Unit,
    onToast: (String) -> Unit = {},
) {
    val pet by vm.pet.collectAsState()
    val apps by vm.apps.collectAsState()
    val usage by vm.usageToday.collectAsState()
    val eval by vm.todayEval.collectAsState()
    val checkins by vm.checkins.collectAsState()
    val bubble by vm.bubble.collectAsState()
    val weather by ServiceLocator.weatherStore.weather.collectAsState(initial = null)
    val weatherEnabled by ServiceLocator.weatherStore.enabled.collectAsState(initial = false)
    val city by ServiceLocator.weatherStore.city.collectAsState(initial = null)

    val actor = rememberPetActor()
    var firstTapOfSession by remember { mutableStateOf(true) }
    var overWarned by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.toastBus.events.collect { onToast(it) }
    }

    val hour = remember { LocalTime.now().hour }
    val moodState = pet?.let { PetMoodState.of(it.mood, it.health) } ?: PetMoodState.CALM
    val petName = pet?.name ?: "皮可"

    // 打开应用：单一问候（久未打开/天气/时段）
    LaunchedEffect(pet?.id) {
        pet?.let {
            val weatherKey = weather?.takeIf { weatherEnabled }?.weatherKey()
            vm.greetOnOpen(moodState, petName, weatherKey)
        }
    }
    // 天气首次到达（会话内一次，非 greetings）
    var weatherGreeted by remember { mutableStateOf(false) }
    LaunchedEffect(weather?.fetchedAtMillis) {
        val w = weather ?: return@LaunchedEffect
        if (weatherEnabled && !weatherGreeted && pet != null) {
            weatherGreeted = true
            vm.weatherGreet(moodState, petName, w.weatherKey())
        }
    }
    // 停留闲聊（调度器冷却）
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            vm.idleChat(moodState, petName)
        }
    }
    LaunchedEffect(Unit) { vm.refresh() }

    // 等级/阶段变化 → 重大动作 + 台词（覆盖普通气泡）
    var lastStage by remember { mutableStateOf<PetStage?>(null) }
    var lastLevel by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(pet?.level) {
        val level = pet?.level ?: return@LaunchedEffect
        val stage = GrowthEngine.stageOf(level)
        val prevStage = lastStage
        val prevLevel = lastLevel
        if (prevStage != null && prevLevel != null && level > prevLevel) {
            if (stage != prevStage) {
                actor.play(PetAction.EVOLVE, force = true)
                vm.showBubble(vm.onEvolve(stage.label, petName))
                onToast("🎉 $petName 进化到「${stage.label}」啦！")
            } else {
                actor.play(PetAction.LEVELUP, force = true)
                vm.showBubble(vm.onLevelUp(level, petName))
                onToast("⬆️ $petName 升到 Lv.$level！")
            }
        }
        lastStage = stage
        lastLevel = level
    }

    // 今日超标 → 不满表情 + 提醒（会话一次）
    LaunchedEffect(eval?.overTotalMinutes) {
        val over = eval?.overTotalMinutes ?: 0L
        if (over > 0 && !overWarned) {
            overWarned = true
            vm.onOverLimit(petName)?.let { vm.showBubble(it) }
        }
    }

    val checkedInToday = checkins.firstOrNull()?.date == DateUtils.todayStr()
    val streak = if (checkedInToday) (checkins.firstOrNull()?.streak ?: 0) else 0

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(6.dp))
        HeaderRow(petName, pet, eval, weather?.takeIf { weatherEnabled }, city?.name)
        Spacer(Modifier.height(2.dp))

        // ===== 台词气泡（固定在舞台上方的独立槽位，绝不会被角色遮挡） =====
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(
                visible = bubble != null,
                enter = fadeIn() + scaleIn(initialScale = 0.85f),
                exit = fadeOut(),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(bubble ?: "", fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                }
            }
        }

        // ===== 宠物舞台（含环境） =====
        Box(
            Modifier
                .fillMaxWidth()
                .height(330.dp)
                .clip(RoundedCornerShape(26.dp)),
        ) {
            HomeStage(hour = hour, modifier = Modifier.fillMaxSize())
            PetView(
                config = PetVisualConfig(
                    stage = GrowthEngine.stageOf(pet?.level ?: 1),
                    mood = moodState,
                    growthT = MaidStageVisuals.growthTOf(pet?.level ?: 1, GrowthEngine.stageOf(pet?.level ?: 1)),
                    annoyed = (eval?.overApps?.isNotEmpty() == true),
                    name = petName,
                ),
                actor = actor,
                onTap = { _ ->
                    runCatching {
                        val (action, line) = vm.onTapPet(moodState, petName, firstTapOfSession)
                        firstTapOfSession = false
                        actor.play(action)
                        line?.let { vm.showBubble(it) }
                    }.onFailure {
                        android.util.Log.e("PetLifeCrash", "点击宠物处理异常（已忽略）", it)
                        onToast("哎呀，出了点小状况，再试一次～")
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 状态胶囊行
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatChip("情绪", "${pet?.mood ?: 0}", color = Coral)
            StatChip("健康", "${pet?.health ?: 0}", color = Mint)
            StatChip("成长系数", String.format("%.2f", eval?.growthCoefficient ?: 1f), color = Lavender)
            Spacer(Modifier.weight(1f))
            Text("摸摸她 ❤", fontSize = 12.sp, color = InkTertiary)
        }

        // 经验条
        val level = pet?.level ?: 1
        val exp = pet?.exp ?: 0
        val expToNext = GrowthEngine.expToNext(level)
        val expRatio = (exp.toFloat() / expToNext).coerceIn(0f, 1f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Lv.$level", fontWeight = FontWeight.Black, fontSize = 20.sp, color = Ink)
            Spacer(Modifier.size(10.dp))
            Box(Modifier.weight(1f)) {
                LinearProgressIndicator(
                    progress = { expRatio },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(50)),
                    color = Coral,
                    trackColor = CoralSoft.copy(alpha = 0.4f),
                )
            }
            Spacer(Modifier.size(10.dp))
            Text("$exp/$expToNext", fontSize = 12.sp, color = InkSecondary)
        }
        val stage = GrowthEngine.stageOf(level)
        Text("${stage.label} · ${stage.desc}", fontSize = 12.sp, color = InkTertiary)

        // ===== 签到 =====
        Button(
            onClick = {
                val (action, _) = vm.checkin(moodState, petName)
                actor.play(action)
            },
            enabled = !checkedInToday,
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = Sun,
                contentColor = Ink,
                disabledContainerColor = Sun.copy(alpha = 0.35f),
                disabledContentColor = Ink.copy(alpha = 0.5f),
            ),
            modifier = Modifier.fillMaxWidth().height(52.dp).animateContentSize(),
        ) {
            Icon(
                if (checkedInToday) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                if (checkedInToday) "今日已签到 · 连续 $streak 天" else "每日签到 · 已连续 $streak 天",
                fontWeight = FontWeight.Bold, fontSize = 16.sp,
            )
        }

        // ===== 今日学习 / 娱乐 =====
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TodayCard(
                title = "今日学习",
                emoji = "📚",
                accent = Mint,
                modifier = Modifier.weight(1f),
                mainText = FormatUtils.minutesHuman(eval?.studyMinutes ?: 0),
                subText = "任务 ${eval?.tasksCompleted ?: 0}/${eval?.tasksTotal ?: 0} 完成",
                progress = if ((eval?.tasksTotal ?: 0) > 0) {
                    (eval!!.tasksCompleted.toFloat() / eval!!.tasksTotal)
                } else 0f,
            )
            val funMinutes = eval?.funMinutes ?: 0
            val overMinutes = eval?.overTotalMinutes ?: 0
            TodayCard(
                title = "今日娱乐",
                emoji = "🎮",
                accent = if (overMinutes > 0) WarnRed else SkyBlue,
                modifier = Modifier.weight(1f),
                mainText = FormatUtils.minutesHuman(funMinutes),
                subText = if (overMinutes > 0) "已超标 ${FormatUtils.minutesHuman(overMinutes)}" else "控制在限额内，棒！",
                progress = if (funMinutes > 0) {
                    val limits = apps.filter { AppCategory.from(it.category) == AppCategory.FUN }.sumOf { it.dailyTargetMinutes }
                    if (limits > 0) (funMinutes.toFloat() / limits) else 0f
                } else 0f,
            )
        }

        // 各应用速览
        PetCard {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("应用速览", style = MaterialTheme.typography.titleMedium, color = Ink)
                    Spacer(Modifier.weight(1f))
                    Text("任务页可领取奖励 →", fontSize = 12.sp, color = InkTertiary)
                }
                Spacer(Modifier.height(8.dp))
                val monitored = apps.take(4)
                if (monitored.isEmpty()) {
                    Text("还没有监控的应用，去「任务」页添加吧", fontSize = 13.sp, color = InkTertiary)
                } else {
                    monitored.forEach { app ->
                        AppMiniRow(app, usage[app.packageName] ?: 0L)
                    }
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

@Composable
private fun HeaderRow(
    petName: String,
    pet: PetEntity?,
    eval: DayEvaluation?,
    weather: com.petlife.app.pet.weather.WeatherInfo?,
    cityName: String?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    DateUtils.friendlyDate(DateUtils.today()),
                    style = MaterialTheme.typography.titleLarge,
                    color = Ink,
                )
                if (weather != null) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(50))
                            .background(Color.White)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            "${weather.emoji()} ${weather.temperatureC.toInt()}° ${cityName ?: ""}",
                            fontSize = 12.sp, color = InkSecondary, fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Text(
                "伙伴「$petName」· ${GrowthEngine.stageOf(pet?.level ?: 1).label}",
                fontSize = 13.sp, color = InkSecondary,
            )
        }
        Box(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(Brush.horizontalGradient(listOf(Coral, CoralSoft)))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "Lv.${pet?.level ?: 1}",
                color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun TodayCard(
    title: String,
    emoji: String,
    accent: Color,
    mainText: String,
    subText: String,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    PetCard(modifier) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 16.sp)
                Spacer(Modifier.size(6.dp))
                Text(title, fontSize = 13.sp, color = InkSecondary)
                Spacer(Modifier.weight(1f))
            }
            Text(mainText, fontSize = 22.sp, fontWeight = FontWeight.Black, color = Ink, modifier = Modifier.padding(top = 4.dp))
            Text(subText, fontSize = 12.sp, color = if (subText.startsWith("已超标")) WarnRed else InkTertiary)
            Spacer(Modifier.height(8.dp))
            GoalProgress(ratio = progress, over = subText.startsWith("已超标"))
        }
    }
}

@Composable
private fun AppMiniRow(app: MonitoredAppEntity, usedMinutes: Long) {
    val cat = AppCategory.from(app.category)
    val isLearn = cat == AppCategory.LEARN
    val target = app.dailyTargetMinutes
    val ratio = if (target > 0) usedMinutes.toFloat() / target else 0f
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(cat.emoji, fontSize = 16.sp)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text(app.appName, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1)
            GoalProgress(ratio = ratio, over = !isLearn && usedMinutes > target, height = 7.dp)
        }
        Spacer(Modifier.size(10.dp))
        Text(
            if (isLearn) "${FormatUtils.minutesClock(usedMinutes)}/${target}min"
            else if (usedMinutes > target) "超标${usedMinutes - target}分"
            else "剩${(target - usedMinutes).coerceAtLeast(0)}分",
            fontSize = 12.sp,
            color = if (!isLearn && usedMinutes > target) WarnRed else InkSecondary,
        )
    }
}
