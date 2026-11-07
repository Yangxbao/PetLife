package com.petlife.app.pet.dialogue

import android.content.Context
import com.petlife.app.data.model.PetMoodState
import com.petlife.app.data.model.PetStage
import kotlin.random.Random

/** 单条话术（紧凑 JSON 解析而来） */
data class DialogueLine(
    val event: String,
    val mood: String,        // ANY | PetMoodState.name
    val time: String,        // ANY | TimeSlot.name
    val weather: String,     // ANY | WeatherKey
    val stage: String,       // ANY | PetStage.name
    val action: String,      // 建议动作
    val text: String,
)

object TimeSlot {
    const val MORNING = "MORNING"    // 5-10
    const val NOON = "NOON"          // 11-13
    const val AFTERNOON = "AFTERNOON"// 14-17
    const val EVENING = "EVENING"    // 18-22
    const val NIGHT = "NIGHT"        // 23-4

    fun fromHour(hour: Int): String = when (hour) {
        in 5..10 -> MORNING
        in 11..13 -> NOON
        in 14..17 -> AFTERNOON
        in 18..22 -> EVENING
        else -> NIGHT
    }
}

object DialogueParser {

    /** 解析一段 JSON 数组文本；字段缺失/非法时使用默认值，不抛出 */
    fun parse(jsonText: String): List<DialogueLine> {
        val out = mutableListOf<DialogueLine>()
        runCatching {
            val arr = org.json.JSONArray(jsonText)
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString("x").trim()
                if (text.isEmpty()) continue
                out += DialogueLine(
                    event = o.optString("e", "CHAT").uppercase(),
                    mood = o.optString("m", "ANY").uppercase(),
                    time = o.optString("t", "ANY").uppercase(),
                    weather = o.optString("w", "ANY").uppercase(),
                    stage = o.optString("s", "ANY").uppercase(),
                    action = o.optString("a", "BOUNCE").uppercase(),
                    text = text,
                )
            }
        }
        return out
    }

    /** 从 assets/dialogues/ 下全部 JSON 文件加载（在 IO 线程调用） */
    fun loadFromAssets(context: Context): List<DialogueLine> {
        val lines = mutableListOf<DialogueLine>()
        runCatching {
            val am = context.assets
            val files = am.list("dialogues").orEmpty().filter { it.endsWith(".json") }.sorted()
            for (f in files) {
                am.open("dialogues/$f").bufferedReader().use { r ->
                    lines += parse(r.readText())
                }
            }
        }
        return lines
    }
}

data class DialogueRequest(
    val event: String,
    val mood: PetMoodState? = null,
    val hour: Int? = null,
    val weatherKey: String? = null,
    val stage: PetStage? = null,
    val petName: String = "皮可",
)

data class DialogueResult(val text: String, val action: String)

/**
 * 数据驱动的话术引擎：
 *  - 按事件 + 情绪 + 时段 + 天气 + 阶段过滤，精确匹配优先于 ANY
 *  - 同一事件近期不重复（LRU 记录），候选耗尽后自动解锁
 *  - 文本中的 {name} 占位符替换为当前宠物名
 */
class DialogueEngine(
    lines: List<DialogueLine>,
    private val random: Random = Random.Default,
) {
    private val byEvent: Map<String, List<DialogueLine>> = lines.groupBy { it.event }
    private val recent = HashMap<String, ArrayDeque<String>>()

    val totalLines: Int get() = byEvent.values.sumOf { it.size }
    val events: Set<String> get() = byEvent.keys

    fun candidates(req: DialogueRequest): List<Pair<DialogueLine, Int>> {
        val pool = byEvent[req.event.uppercase()].orEmpty()
        val timeSlot = req.hour?.let { TimeSlot.fromHour(it) }
        val out = mutableListOf<Pair<DialogueLine, Int>>()
        for (l in pool) {
            if (l.mood != "ANY" && req.mood?.name != l.mood) continue
            if (l.time != "ANY" && timeSlot != l.time) continue
            if (l.weather != "ANY" && (req.weatherKey ?: "ANY") != l.weather) continue
            if (l.stage != "ANY" && req.stage?.name != l.stage) continue
            // 匹配精度分：精确命中的维度越多越优先
            var score = 0
            if (l.mood != "ANY") score += 8
            if (l.weather != "ANY") score += 6
            if (l.time != "ANY") score += 4
            if (l.stage != "ANY") score += 2
            out += l to score
        }
        return out
    }

    /** 挑选一条话术；无候选返回 null */
    fun pick(req: DialogueRequest): DialogueResult? {
        val cands = candidates(req)
        if (cands.isEmpty()) return null
        val maxScore = cands.maxOf { it.second }
        val best = cands.filter { it.second == maxScore }.map { it.first }
        val seen = recent.getOrPut(req.event.uppercase()) { ArrayDeque() }
        var fresh = best.filter { it.text !in seen }
        if (fresh.isEmpty()) { seen.clear(); fresh = best }
        val chosen = fresh[random.nextInt(fresh.size)]
        seen.addLast(chosen.text)
        while (seen.size > RECENT_LIMIT) seen.removeFirst()
        return DialogueResult(chosen.text.replace("{name}", req.petName), chosen.action)
    }

    companion object {
        private const val RECENT_LIMIT = 8
    }
}
