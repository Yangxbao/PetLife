package com.petlife.app.ui.bag

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.petlife.app.ServiceLocator
import com.petlife.app.core.MessageBus
import com.petlife.app.data.model.FoodRarity
import com.petlife.app.data.model.PetMoodState
import com.petlife.app.data.model.PetStage
import com.petlife.app.engine.FoodCatalog
import com.petlife.app.engine.GrowthEngine
import com.petlife.app.ui.components.ConfirmDialog
import com.petlife.app.ui.components.EmptyHint
import com.petlife.app.ui.components.PetCard
import com.petlife.app.ui.components.SectionTitle
import com.petlife.app.ui.components.StatChip
import com.petlife.app.ui.pet.PetAction
import com.petlife.app.ui.pet.PetView
import com.petlife.app.ui.pet.PetVisualConfig
import com.petlife.app.ui.theme.Cream100
import com.petlife.app.ui.theme.Coral
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary
import com.petlife.app.ui.theme.InkTertiary
import com.petlife.app.ui.theme.Lavender
import com.petlife.app.ui.theme.Mint
import com.petlife.app.ui.theme.Sun
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BagViewModel(private val repo: com.petlife.app.data.repo.PetLifeRepository) : ViewModel() {

    val inventory = repo.inventory.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val pet = repo.pet.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val message = MessageBus()
    val todayEval = MutableStateFlow<com.petlife.app.data.model.DayEvaluation?>(null)

    private fun line(event: String, petName: String): String = runCatching {
        ServiceLocator.dialogueEngine.pick(
            com.petlife.app.pet.dialogue.DialogueRequest(
                event = event, mood = PetMoodState.HAPPY,
                hour = java.time.LocalTime.now().hour, petName = petName,
            ),
        )?.text
    }.getOrNull() ?: ""

    init {
        viewModelScope.launch { todayEval.value = runCatching { repo.todayCoefficientPreview() }.getOrNull() }
    }

    /** 喂食；返回进化信息供 UI 播放庆祝动画 */
    fun feed(foodId: String, onStageChanged: (PetStage) -> Unit) {
        viewModelScope.launch {
            val r = repo.feed(foodId)
            if (r.success) {
                val food = FoodCatalog.get(foodId)
                val petName = pet.value?.name ?: "皮可"
                val line = line("FEED", petName)
                message.post(if (line.isBlank()) "${food.emoji} ${food.name}：+${r.expApplied} 经验" else line)
                if (r.stageChanged && r.newStage != null) {
                    onStageChanged(r.newStage)
                } else if (r.levelsGained > 0) {
                    message.post("⬆️ 升级啦！")
                }
            } else {
                message.post(r.message)
            }
            todayEval.value = runCatching { repo.todayCoefficientPreview() }.getOrNull()
        }
    }
}

@Composable
fun BagScreen(
    vm: BagViewModel = viewModel(key = "bag") { BagViewModel(ServiceLocator.repository) },
    onToast: (String) -> Unit,
) {
    val inventory by vm.inventory.collectAsState()
    val pet by vm.pet.collectAsState()
    val eval by vm.todayEval.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var confirmFood by remember { mutableStateOf<String?>(null) }
    var celebrateStage by remember { mutableStateOf<PetStage?>(null) }
    val petName = pet?.name ?: "皮可"

    LaunchedEffect(Unit) {
        vm.message.events.collect { onToast(it) }
    }

    val counts = inventory.associate { it.foodId to it.quantity }
    val owned = FoodCatalog.FOODS.values.filter { (counts[it.id] ?: 0) > 0 }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Column {
            Text("背包", style = MaterialTheme.typography.headlineSmall, color = Ink)
            Text("给宠物「$petName」喂食，陪它一起长大", fontSize = 13.sp, color = InkSecondary)
        }
        Spacer(Modifier.height(10.dp))

        // 成长系数卡
        PetCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("今日成长系数 ${String.format("%.2f", eval?.growthCoefficient ?: 1.0f)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Ink)
                    Text(
                        GrowthEngine.coefficientLabel(eval?.growthCoefficient ?: 1f) + " · 喂食经验 = 食物经验 × 系数",
                        fontSize = 12.sp, color = InkTertiary,
                    )
                }
                StatChip("任务", "${eval?.tasksCompleted ?: 0}/${eval?.tasksTotal ?: 0}", color = Mint)
            }
        }

        SectionTitle("🍱 我的食物")
        if (owned.isEmpty()) {
            EmptyHint("背包空空的～\n签到、完成学习任务都可以获得食物")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(owned, key = { it.id }) { food ->
                    val count = counts[food.id] ?: 0
                    FoodCell(food, count) { confirmFood = food.id }
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    // 食物详情
    selected?.let { id ->
        val food = FoodCatalog.get(id)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text("${food.emoji} ${food.name}", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(food.desc, fontSize = 14.sp, color = InkSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("成长经验：+${food.exp}", fontSize = 14.sp, color = Ink)
                    Text("心情恢复：+${food.mood}", fontSize = 14.sp, color = Ink)
                    Text("库存：${counts[id] ?: 0} 个", fontSize = 14.sp, color = Ink)
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = { selected = null; confirmFood = id },
                    enabled = (counts[id] ?: 0) > 0,
                ) { Text("喂食", fontWeight = FontWeight.Bold, color = Coral) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { selected = null }) { Text("关闭", color = InkSecondary) }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(22.dp),
        )
    }

    // 喂食确认
    confirmFood?.let { id ->
        val food = FoodCatalog.get(id)
        ConfirmDialog(
            title = "喂 ${food.emoji} ${food.name}？",
            body = "$petName 获得约 +${(food.exp * (eval?.growthCoefficient ?: 1f)).toInt().coerceAtLeast(1)} 经验（按当前系数 ${String.format("%.2f", eval?.growthCoefficient ?: 1f)}），心情 +${food.mood}。",
            confirmText = "喂给它",
            onConfirm = {
                confirmFood = null
                vm.feed(id) { stage -> celebrateStage = stage }
            },
            onDismiss = { confirmFood = null },
        )
    }

    // ===== 进化庆祝（全屏，播 EVOLVE 动画）=====
    celebrateStage?.let { stage ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { celebrateStage = null },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { celebrateStage = null }) {
                    Text("太棒了！", fontWeight = FontWeight.Bold, color = Coral)
                }
            },
            title = { Text("🎉 进化啦！", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val evolveActor = com.petlife.app.ui.pet.rememberPetActor()
                    LaunchedEffect(Unit) { evolveActor.play(PetAction.EVOLVE, force = true) }
                    Box(Modifier.size(220.dp)) {
                        PetView(
                            config = PetVisualConfig(
                                stage = stage,
                                mood = com.petlife.app.data.model.PetMoodState.HAPPY,
                                growthT = com.petlife.app.ui.pet.character.MaidStageVisuals.growthTOf(pet?.level ?: 1, stage),
                                name = petName,
                            ),
                            actor = evolveActor,
                            enableIdleMicro = false,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("$petName 进化到「${stage.label}」啦！", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Ink)
                    Text(stage.desc, fontSize = 13.sp, color = InkSecondary)
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(26.dp),
        )
    }
}

@Composable
private fun FoodCell(
    food: com.petlife.app.engine.FoodDef,
    count: Int,
    onClick: () -> Unit,
) {
    val rarityColor = when (food.rarity) {
        FoodRarity.COMMON -> Mint
        FoodRarity.RARE -> Lavender
        FoodRarity.SPECIAL -> Sun
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Cream100),
                contentAlignment = Alignment.Center,
            ) {
                Text(food.emoji, fontSize = 24.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(food.name, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = Ink)
            Text("经验+${food.exp}", fontSize = 10.sp, color = InkTertiary)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(rarityColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("×$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = rarityColor)
            }
        }
    }
}
