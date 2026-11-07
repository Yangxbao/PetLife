package com.petlife.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.petlife.app.data.repo.PetLifeRepository
import com.petlife.app.ui.bag.BagScreen
import com.petlife.app.ui.home.HomeScreen
import com.petlife.app.ui.settings.SettingsScreen
import com.petlife.app.ui.stats.StatsScreen
import com.petlife.app.ui.tasks.TasksScreen
import com.petlife.app.ui.theme.Coral
import com.petlife.app.ui.theme.Cream50
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary
import com.petlife.app.ui.theme.InkTertiary
import com.petlife.app.ui.theme.Mint
import com.petlife.app.ui.theme.PetLifeTheme
import com.petlife.app.work.DailyWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val repo: PetLifeRepository) : ViewModel() {

    /**
     * v1.0.1 修复 Bug 2：三态引导状态。
     * null = DataStore 尚未加载完成（此时绝不弹窗，避免每次冷启动闪现引导）；
     * false = 未完成引导；true = 已完成。
     */
    val onboarded = repo.onboardingDone
        .map { it as Boolean? }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch { repo.onAppOpened() }
    }

    fun markOnboarded() {
        viewModelScope.launch { repo.markOnboarded() }
    }
}

@Composable
fun MainActivityContent() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val vm: MainViewModel = viewModel(key = "main") { MainViewModel(ServiceLocator.repository) }
    val onboarded by vm.onboarded.collectAsState()
    var showOnboarding by remember { mutableStateOf(false) }

    LaunchedEffect(onboarded) {
        // 仅在"确实未完成引导"时弹出；null（加载中）不弹
        if (onboarded == false && !showOnboarding) showOnboarding = true
    }

    // 回到前台时刷新（补算结算 + 今日使用时长）
    val activity = androidx.compose.ui.platform.LocalContext.current as? android.app.Activity
    androidx.compose.runtime.DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
                    runCatching { ServiceLocator.repository.onAppOpened() }
                }
            }
        }
        (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.addObserver(observer)
        onDispose { (activity as? androidx.lifecycle.LifecycleOwner)?.lifecycle?.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        activity?.let {
            DailyWorker.schedule(it)
            com.petlife.app.work.CompanionWorker.schedule(it)
        }
    }

    fun toast(msg: String) {
        scope.launch { snackbarHostState.showSnackbar(msg, withDismissAction = false) }
    }

    Scaffold(
        containerColor = Cream50,
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    data,
                    containerColor = Ink.copy(alpha = 0.92f),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(16.dp),
                )
            }
        },
        bottomBar = { PetBottomBar(navController) },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.fillMaxSize().padding(padding),
            // v1.0.1：更快的 Tab 过渡，减少"上一个页面内容串台"的观感
            enterTransition = { fadeIn(tween(140)) },
            exitTransition = { fadeOut(tween(90)) },
            popEnterTransition = { fadeIn(tween(140)) },
            popExitTransition = { fadeOut(tween(90)) },
        ) {
            composable("home") { HomeScreen(onNavigateBag = { navController.navigate("bag") }, onToast = ::toast) }
            composable("tasks") { TasksScreen(onToast = ::toast) }
            composable("stats") { StatsScreen() }
            composable("bag") { BagScreen(onToast = ::toast) }
            composable("settings") { SettingsScreen(onToast = ::toast) }
        }
    }

    if (showOnboarding) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("欢迎来到皮可的世界 🌟", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("皮可是一只靠你的真实努力成长的小毛球：", fontSize = 14.sp, color = Ink)
                    Text("📚 认真学习 → 获得食物 → 喂养宠物", fontSize = 14.sp, color = Mint)
                    Text("🎮 控制娱乐 → 宠物开心 → 成长更快", fontSize = 14.sp, color = Mint)
                    Text(
                        "为了让皮可成长记了解你的学习情况，需要「使用情况访问权限」（系统级特殊权限，仅用于统计时长，数据全部保存在本地、绝不联网）。",
                        fontSize = 13.sp, color = InkSecondary,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.markOnboarded()
                    showOnboarding = false
                    // v1.0.1 修复 Bug 3：从 Activity 上下文、同任务栈打开系统设置，返回键回到应用
                    ServiceLocator.repository.openUsageAccessSettings(activity)
                }) { Text("去授权，开始陪伴", fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { vm.markOnboarded(); showOnboarding = false }) {
                    Text("稍后再说", color = InkTertiary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(24.dp),
        )
    }
}

private data class TabSpec(val route: String, val label: String, val emoji: String)

private val TABS = listOf(
    TabSpec("home", "皮可", "🐹"),
    TabSpec("tasks", "任务", "📚"),
    TabSpec("stats", "统计", "📊"),
    TabSpec("bag", "背包", "🍱"),
    TabSpec("settings", "设置", "⚙️"),
)

@Composable
private fun PetBottomBar(navController: NavHostController) {
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Box(
        Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TABS.forEach { tab ->
                val selected = currentRoute == tab.route
                val scale by animateFloatAsState(
                    if (selected) 1.12f else 1f,
                    animationSpec = tween(180),
                    label = "tabScale",
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        tab.emoji,
                        fontSize = 22.sp,
                        modifier = Modifier.scale(scale).alpha(if (selected) 1f else 0.55f),
                    )
                    Text(
                        tab.label,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) Coral else InkTertiary,
                    )
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PetLifeTheme {
                MainActivityContent()
            }
        }
    }
}
