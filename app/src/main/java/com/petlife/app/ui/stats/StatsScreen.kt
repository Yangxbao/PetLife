package com.petlife.app.ui.stats

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.petlife.app.data.db.DailyRecordEntity
import com.petlife.app.data.model.DayEvaluation
import com.petlife.app.engine.DisciplineEngine
import com.petlife.app.engine.GrowthEngine
import com.petlife.app.engine.SettlementEngine
import com.petlife.app.ui.components.BarChart
import com.petlife.app.ui.components.ChartSeries
import com.petlife.app.ui.components.EmptyHint
import com.petlife.app.ui.components.LineChart
import com.petlife.app.ui.components.PetCard
import com.petlife.app.ui.components.SectionTitle
import com.petlife.app.ui.theme.Coral
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary
import com.petlife.app.ui.theme.InkTertiary
import com.petlife.app.ui.theme.Lavender
import com.petlife.app.ui.theme.Mint
import com.petlife.app.ui.theme.SkyBlue
import com.petlife.app.ui.theme.WarnRed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class StatsViewModel(private val repo: com.petlife.app.data.repo.PetLifeRepository) : ViewModel() {

    val records = repo.recentRecords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val todayEval = MutableStateFlow<DayEvaluation?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            todayEval.value = runCatching { repo.todayCoefficientPreview() }.getOrNull()
        }
    }
}

@Composable
fun StatsScreen(vm: StatsViewModel = viewModel(key = "stats") { StatsViewModel(ServiceLocator.repository) }) {
    val records by vm.records.collectAsState()
    val today by vm.todayEval.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }
        item {
            Column {
                Text("成长统计", style = MaterialTheme.typography.headlineSmall, color = Ink)
                Text("看见自己的每一点改变", fontSize = 13.sp, color = InkSecondary)
            }
        }

        item { SectionTitle("☀️ 今日速览（实时）") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell("学习", FormatUtils.minutesHuman(today?.studyMinutes ?: 0), Mint, Modifier.weight(1f))
                StatCell("娱乐", FormatUtils.minutesHuman(today?.funMinutes ?: 0), SkyBlue, Modifier.weight(1f))
                StatCell("获得经验", "+${today?.expGained ?: 0}", Coral, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCell("任务", "${today?.tasksCompleted ?: 0}/${today?.tasksTotal ?: 0}", Mint, Modifier.weight(1f))
                StatCell(
                    "超标",
                    if ((today?.overTotalMinutes ?: 0) > 0) FormatUtils.minutesHuman(today?.overTotalMinutes ?: 0) else "无",
                    if ((today?.overTotalMinutes ?: 0) > 0) WarnRed else Mint,
                    Modifier.weight(1f),
                )
                StatCell(
                    "自律",
                    "${if (today != null) today!!.disciplineScore else "--"}分",
                    if ((today?.disciplineScore ?: 100) >= 75) Mint else Coral,
                    Modifier.weight(1f),
                )
            }
        }
        item {
            PetCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("今日成长系数", fontSize = 13.sp, color = InkSecondary)
                        Text(
                            String.format("%.2f", today?.growthCoefficient ?: 1.0f),
                            fontSize = 26.sp, fontWeight = FontWeight.Black, color = Ink,
                        )
                        Text(
                            GrowthEngine.coefficientLabel(today?.growthCoefficient ?: 1f) +
                                " · 影响每次喂食获得的经验倍率",
                            fontSize = 12.sp, color = InkTertiary,
                        )
                    }
                    Text("🌱", fontSize = 40.sp)
                }
            }
        }

        item { SectionTitle("📈 最近 7 天趋势") }
        item {
            val days = recentDays(7)
            val map = records.associateBy { it.date }
            PetCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("学习 / 娱乐 时长（分钟）", fontSize = 12.sp, color = InkSecondary)
                    LineChart(
                        labels = days.map { it.first },
                        series = listOf(
                            ChartSeries("学习", Mint, days.map { (map[it.second]?.studyMinutes ?: 0).toFloat() }),
                            ChartSeries("娱乐", SkyBlue, days.map { (map[it.second]?.funMinutes ?: 0).toFloat() }),
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Legend(Mint, "学习时长")
                        Legend(SkyBlue, "娱乐时长")
                    }
                }
            }
        }
        item {
            val days = recentDays(7)
            val map = records.associateBy { it.date }
            PetCard(Modifier.fillMaxWidth()) {
                Column {
                    Text("每日自律评分", fontSize = 12.sp, color = InkSecondary)
                    BarChart(
                        labels = days.map { it.first },
                        values = days.map { (map[it.second]?.disciplineScore ?: 0).toFloat() },
                        barColor = Coral,
                    )
                    Text(
                        "评分由每日结算生成：超标娱乐、未签到、未完成学习任务都会扣分（今日评分为实时预估）",
                        fontSize = 11.sp, color = InkTertiary,
                    )
                }
            }
        }

        item { SectionTitle("🗓 每日结算记录") }
        val sorted = records.sortedByDescending { it.date }
        if (sorted.isEmpty()) {
            item { EmptyHint("暂无结算记录\n每天结束后自动生成（今日的会实时显示在上面）") }
        } else {
            items(sorted.size) { idx ->
                RecordCard(sorted[idx])
            }
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

private data class DayLabel(val first: String, val second: String)

private fun recentDays(n: Int): List<DayLabel> {
    val fmt = DateTimeFormatter.ofPattern("d日")
    return (n - 1 downTo 0).map { off ->
        val d = LocalDate.now().minusDays(off.toLong())
        DayLabel(d.format(fmt), DateUtils.dateStr(d))
    }
}

@Composable
private fun StatCell(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    PetCard(modifier) {
        Column {
            Text(title, fontSize = 12.sp, color = InkSecondary)
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

@Composable
private fun Legend(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(color, shape = CircleShape),
        )
        Text(text, fontSize = 12.sp, color = InkSecondary)
    }
}

@Composable
private fun RecordCard(record: DailyRecordEntity) {
    var expanded by remember { mutableStateOf(false) }
    val reasons = SettlementEngine.reasonsFromJson(record.reasonsJson)
    val scoreColor = if (record.disciplineScore >= 75) Mint else if (record.disciplineScore >= 50) Coral else WarnRed
    PetCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        DateUtils.friendlyDate(DateUtils.parseDate(record.date)),
                        fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Ink,
                    )
                    Text(
                        "学习 ${FormatUtils.minutesHuman(record.studyMinutes)} · 娱乐 ${FormatUtils.minutesHuman(record.funMinutes)} · 任务 ${record.tasksCompleted}/${record.tasksTotal}",
                        fontSize = 12.sp, color = InkSecondary,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "${record.disciplineScore}分",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        color = scoreColor,
                    )
                    Text("系数 ${String.format("%.2f", record.growthCoefficient)}", fontSize = 11.sp, color = InkTertiary)
                }
                Icon(
                    if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = InkTertiary,
                )
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("评分明细（${DisciplineEngine.scoreLabel(record.disciplineScore)}）", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Ink)
                    reasons.forEach { r ->
                        Row {
                            Text(
                                (if (r.delta >= 0) "+" else "") + "${r.delta}分",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (r.delta >= 0) Mint else WarnRed,
                            )
                            Spacer(Modifier.size(8.dp))
                            Text(r.label, fontSize = 13.sp, color = InkSecondary)
                        }
                    }
                    if (reasons.isEmpty()) Text("无加减分记录", fontSize = 13.sp, color = InkTertiary)
                    Text(
                        "当日获得经验 +${record.expGained} · 喂食 ${record.foodsFed} 次 · 结算后情绪 ${record.moodAfter}/健康 ${record.healthAfter}",
                        fontSize = 12.sp, color = InkTertiary, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
