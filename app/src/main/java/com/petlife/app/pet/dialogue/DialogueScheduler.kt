package com.petlife.app.pet.dialogue

import com.petlife.app.data.model.PetMoodState
import kotlin.random.Random

/**
 * 对话调度器 v2 —— 「动作可以频繁，对话必须克制」。
 *
 * 规则：
 *  1. 全局最小间隔：任意两条对话之间至少 GLOBAL_MIN_GAP_MS。
 *  2. 事件独立冷却：每个事件类型有自己的冷却时间。
 *  3. 优先级仲裁：气泡正在显示时，低/同优先级事件直接丢弃，
 *     高优先级（升级/进化等）可以打断覆盖。
 *  4. 防重复：引擎内每事件 LRU 之外，这里再维护全局最近台词窗口，
 *     抽到重复台词时重抽一次，仍重复则放弃本次。
 *  5. 点击≠说话：TAP 事件由 [tryTap] 以概率触发（默认 22%），
 *     且气泡显示中永不触发。
 */
class DialogueScheduler(
    private val engineProvider: () -> DialogueEngine?,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val random: Random = Random.Default,
) {

    data class Rule(val priority: Int, val cooldownMs: Long)

    companion object {
        // 气泡展示时长（与 HomeScreen 的 3.2s 对齐）
        const val BUBBLE_MS = 3_200L
        const val GLOBAL_MIN_GAP_MS = 3_600L

        val RULES: Map<String, Rule> = mapOf(
            "TAP" to Rule(1, 12_000),
            "CHAT" to Rule(1, IDLE_CHAT_GAP_MS),
            "HAPPY" to Rule(1, 90_000),
            "LONELY" to Rule(2, 0),
            "GREET" to Rule(2, 30_000),
            "WEATHER" to Rule(2, 20 * 60_000L),
            "OVERLIMIT" to Rule(2, 90_000),
            "FEED" to Rule(3, 8_000),
            "CHECKIN" to Rule(3, 8_000),
            "STUDY" to Rule(3, 12_000),
            "HUNGRY" to Rule(2, 60_000),
            "LEVELUP" to Rule(4, 0),
            "EVOLVE" to Rule(4, 0),
        )

        // v1.3.0：主动性提升（用户反馈"不点就像哑巴"）
        const val TAP_PROBABILITY = 0.42f
        const val IDLE_CHAT_GAP_MS = 75_000L
        /** 前台闲置多久后触发一次想念/闲聊 */
        const val SESSION_IDLE_MS = 15 * 60_000L
    }

    private var lastAnyAt = 0L
    private val lastPerEvent = HashMap<String, Long>()
    private val recent = ArrayDeque<String>()
    private var currentPriority = 0
    private var currentUntil = 0L

    /** 气泡消失时由 UI 回调，释放优先级占用 */
    fun onBubbleDismissed() {
        currentPriority = 0
        currentUntil = 0
    }

    /**
     * 通用请求。
     * @return 台词文本；被冷却/优先级/防重复拒绝时返回 null
     */
    fun trySpeak(
        event: String,
        mood: PetMoodState? = null,
        hour: Int? = null,
        weatherKey: String? = null,
        stageName: String? = null,
        petName: String = "皮可",
    ): String? {
        val now = nowMs()
        val rule = RULES[event] ?: return null

        // 气泡占用中：低/同优先级放弃，高优先级覆盖
        if (now < currentUntil) {
            if (rule.priority <= currentPriority) return null
        }
        // 全局最小间隔（高优先级 4 可豁免一半间隔）
        val gap = if (rule.priority >= 4) GLOBAL_MIN_GAP_MS / 2 else GLOBAL_MIN_GAP_MS
        if (now - lastAnyAt < gap) return null
        // 事件独立冷却
        val last = lastPerEvent[event] ?: 0L
        if (now - last < rule.cooldownMs) return null

        val engine = engineProvider() ?: return null
        val hourResolved = hour ?: java.time.LocalTime.now().hour
        val stage = stageName?.let { runCatching { com.petlife.app.data.model.PetStage.valueOf(it) }.getOrNull() }
        fun request() = DialogueRequest(
            event = event, mood = mood, hour = hourResolved,
            weatherKey = weatherKey, stage = stage, petName = petName,
        )
        var text = engine.pick(request())?.text ?: return null
        // 全局防重复：抽到最近说过的，重抽一次
        if (text in recent) {
            text = engine.pick(request())?.text ?: return null
            if (text in recent) return null
        }

        // 记录状态
        lastAnyAt = now
        lastPerEvent[event] = now
        recent.addLast(text)
        while (recent.size > 12) recent.removeFirst()
        currentPriority = rule.priority
        currentUntil = now + BUBBLE_MS
        return text
    }

    /**
     * 点击宠物：只以概率说话（动作在 UI 层总是触发）。
     */
    fun tryTap(
        mood: PetMoodState?,
        petName: String,
        probability: Float = TAP_PROBABILITY,
    ): String? {
        if (random.nextFloat() > probability) return null
        return trySpeak("TAP", mood = mood, petName = petName)
    }

    /**
     * 打开应用时的单一问候：从"久未打开 > 天气 > 时段问候"中只挑一条。
     * @param hoursSinceLastOpen 距上次打开应用的小时数（<0 表示未知）
     */
    fun tryOpeningGreeting(
        mood: PetMoodState?,
        petName: String,
        weatherKey: String?,
        hoursSinceLastOpen: Float,
    ): String? {
        return when {
            hoursSinceLastOpen >= 8f -> trySpeak("LONELY", mood = mood, petName = petName)
                ?: trySpeak("GREET", mood = mood, hour = null, petName = petName)
            weatherKey != null && weatherKey != "ANY" ->
                trySpeak("WEATHER", mood = mood, weatherKey = weatherKey, petName = petName)
                    ?: trySpeak("GREET", mood = mood, petName = petName)
            else -> trySpeak("GREET", mood = mood, petName = petName)
        }
    }
}
