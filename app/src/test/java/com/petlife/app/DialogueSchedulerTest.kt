package com.petlife.app

import com.petlife.app.pet.dialogue.DialogueEngine
import com.petlife.app.pet.dialogue.DialogueParser
import com.petlife.app.pet.dialogue.DialogueScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 对话调度器测试：点击≠说话、全局/事件冷却、优先级仲裁、防重复。
 * 使用可控时钟与固定随机源，结果完全确定。
 */
class DialogueSchedulerTest {

    private class Clock(var now: Long) {
        fun elapse(ms: Long) { now += ms }
    }

    private fun loadEngine(): DialogueEngine {
        val dir = listOf(File("src/main/assets/dialogues"), File("app/src/main/assets/dialogues"))
            .first { it.isDirectory }
        val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        return DialogueEngine(files.flatMap { DialogueParser.parse(it.readText()) })
    }

    private fun scheduler(clock: Clock, seed: Int = 42): DialogueScheduler =
        DialogueScheduler(engineProvider = { loadEngine() }, nowMs = { clock.now }, random = Random(seed))

    @Test
    fun `点击以概率触发台词 - 概率为零时永不说话`() {
        val clock = Clock(1_000_000L)
        val s = scheduler(clock)
        // 概率 0 → 永不触发（动作仍会由 UI 播放，这里只验证对话被克制）
        repeat(30) {
            assertNull(s.tryTap(null, "皮可", probability = 0f))
        }
    }

    @Test
    fun `点击概率为一时首次说话 - 但气泡期间与冷却内不再说话`() {
        val clock = Clock(1_000_000L)
        val s = scheduler(clock)
        val first = s.tryTap(null, "皮可", probability = 1f)
        assertNotNull("概率 1.0 首次点击应说话", first)
        // 气泡展示中：再次点击（即使概率 1.0）也不说话
        clock.elapse(100)
        assertNull(s.tryTap(null, "皮可", probability = 1f))
        // 气泡结束后但在 TAP 冷却内：仍不说话
        clock.elapse(DialogueScheduler.BUBBLE_MS)
        s.onBubbleDismissed()
        assertNull(s.tryTap(null, "皮可", probability = 1f))
        // 冷却结束后：可再次说话
        clock.elapse(12_000L)
        assertNotNull(s.tryTap(null, "皮可", probability = 1f))
    }

    @Test
    fun `全局最小间隔生效`() {
        val clock = Clock(2_000_000L)
        val s = scheduler(clock)
        assertNotNull(s.trySpeak("FEED", petName = "皮可"))
        clock.elapse(500) // 全局间隔 3.6s 未到
        assertNull(s.trySpeak("CHECKIN", petName = "皮可"))
        clock.elapse(DialogueScheduler.GLOBAL_MIN_GAP_MS)
        assertNotNull(s.trySpeak("CHECKIN", petName = "皮可"))
    }

    @Test
    fun `事件独立冷却生效`() {
        val clock = Clock(3_000_000L)
        val s = scheduler(clock)
        assertNotNull(s.trySpeak("FEED", petName = "皮可"))
        clock.elapse(DialogueScheduler.GLOBAL_MIN_GAP_MS + 10)
        // FEED 冷却 8s 未到（已过 3.6s）
        assertNull(s.trySpeak("FEED", petName = "皮可"))
        // 但另一事件 CHECKIN 可说
        assertNotNull(s.trySpeak("CHECKIN", petName = "皮可"))
    }

    @Test
    fun `高优先级可打断普通气泡 - 低优先级不能打断重大事件`() {
        val clock = Clock(4_000_000L)
        val s = scheduler(clock)
        // 先来一条普通闲聊（优先级 1）
        assertNotNull(s.trySpeak("CHAT", petName = "皮可"))
        clock.elapse(DialogueScheduler.GLOBAL_MIN_GAP_MS)
        // 升级（优先级 4）可接管；其全局间隔减半为 1800ms
        val levelup = s.trySpeak("LEVELUP", petName = "皮可")
        assertNotNull("升级应接管", levelup)
        // 快进 2000ms：升级气泡（3.2s）仍在展示，但已超过其减半间隔
        clock.elapse(2_000L)
        // 气泡期间，闲聊（优先级 1）不能打断升级
        assertNull(s.trySpeak("CHAT", petName = "皮可"))
        // 喂食（优先级 3 < 4）也不行
        assertNull(s.trySpeak("FEED", petName = "皮可"))
    }

    @Test
    fun `同一条台词短时间不重复`() {
        val clock = Clock(5_000_000L)
        val s = scheduler(clock)
        val seen = mutableSetOf<String>()
        var last: String? = null
        repeat(10) {
            // 每次等足全局间隔与 TAP 冷却，模拟连续触发 TAP
            clock.elapse(12_500L)
            val line = s.tryTap(null, "皮可", probability = 1f)
            if (line != null) {
                assertTrue("台词「$line」在最近窗口内重复", line !in seen || seen.size >= 12)
                seen.add(line)
                last = line
                clock.elapse(DialogueScheduler.BUBBLE_MS) // 等气泡结束
            }
        }
        assertNotNull("概率 1.0 下十轮至少应说过几条", last)
        assertTrue("十轮内说过的话术应多于 2 条", seen.size >= 3)
    }

    @Test
    fun `打开应用只产出一条问候`() {
        val clock = Clock(6_000_000L)
        val s = scheduler(clock)
        val greeting = s.tryOpeningGreeting(null, "皮可", weatherKey = null, hoursSinceLastOpen = 1f)
        assertNotNull(greeting)
        // 问候气泡期间，时段问候/天气/闲聊都不应再产出台词
        assertNull(s.tryOpeningGreeting(null, "皮可", weatherKey = "RAIN", hoursSinceLastOpen = 1f))
        assertNull(s.trySpeak("CHAT", petName = "皮可"))
    }

    @Test
    fun `久未打开优先迎接台词`() {
        val clock = Clock(7_000_000L)
        val s = scheduler(clock)
        val greeting = s.tryOpeningGreeting(null, "皮可", weatherKey = null, hoursSinceLastOpen = 30f)
        assertNotNull(greeting)
    }

    @Test
    fun `台词引擎真实资产可用于全部事件`() {
        val clock = Clock(8_000_000L)
        val s = scheduler(clock)
        var spoke = 0
        for (event in listOf("TAP", "CHAT", "FEED", "CHECKIN", "STUDY", "OVERLIMIT", "LEVELUP", "GREET", "LONELY", "HAPPY")) {
            val r = s.trySpeak(event, petName = "皮可")
            if (r != null) spoke++
            // 每个事件间给足全局间隔（不同事件各自首条不受事件冷却限制）
            clock.now += DialogueScheduler.GLOBAL_MIN_GAP_MS + 1
            s.onBubbleDismissed()
        }
        assertTrue("10 个事件中仅 $spoke 个能说话", spoke >= 9)
    }

    @Test
    fun `实际资产仍然超过1000条`() {
        val dir = listOf(File("src/main/assets/dialogues"), File("app/src/main/assets/dialogues"))
            .first { it.isDirectory }
        val total = dir.listFiles { f -> f.extension == "json" }!!
            .flatMap { DialogueParser.parse(it.readText()) }
        assertTrue("话术总数 ${total.size} 应 ≥1000", total.size >= 1000)
        assertEquals("话术应零重复", total.size, total.map { it.text }.toSet().size)
    }
}
