package com.petlife.app

import com.petlife.app.data.model.PetMoodState
import com.petlife.app.pet.dialogue.DialogueEngine
import com.petlife.app.pet.dialogue.DialogueParser
import com.petlife.app.pet.dialogue.DialogueRequest
import com.petlife.app.pet.weather.WeatherInfo
import com.petlife.app.pet.weather.WeatherJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * 直接加载项目真实 assets 的对话资产测试：
 * 校验总量、去重、分类覆盖、格式合法（这些断言约束话术库质量）。
 */
class DialogueAssetTest {

    private fun loadAssets(): List<com.petlife.app.pet.dialogue.DialogueLine> {
        val candidates = listOf(
            File("src/main/assets/dialogues"),
            File("app/src/main/assets/dialogues"),
        )
        val dir = candidates.firstOrNull { it.isDirectory }
            ?: throw IllegalStateException("找不到 assets/dialogues 目录")
        val files = dir.listFiles { f -> f.extension == "json" }!!.sortedBy { it.name }
        return files.flatMap { DialogueParser.parse(it.readText()) }
    }

    private val lines by lazy { loadAssets() }

    @Test
    fun `话术总量不少于1000条`() {
        assertTrue("当前仅 ${lines.size} 条，要求 ≥1000", lines.size >= 1000)
    }

    @Test
    fun `无空文本`() {
        assertTrue(lines.all { it.text.isNotBlank() })
    }

    @Test
    fun `同一事件内无重复文本`() {
        val dupes = lines.groupBy { it.event }
            .mapValues { (_, v) -> v.groupBy { it.text }.filter { it.value.size > 1 }.keys }
            .filterValues { it.isNotEmpty() }
        assertTrue("发现重复话术: $dupes", dupes.isEmpty())
    }

    @Test
    fun `核心事件分类齐备`() {
        val required = listOf("TAP", "GREET", "SLEEP", "STUDY", "OVERLIMIT", "FEED", "CHECKIN", "LEVELUP", "EVOLVE", "WEATHER", "CHAT")
        val present = lines.map { it.event }.toSet()
        val missing = required - present
        assertTrue("缺少分类: $missing", missing.isEmpty())
    }

    @Test
    fun `情绪时段天气字段合法`() {
        val moods = setOf("ANY") + PetMoodState.entries.map { it.name }
        val times = setOf("ANY", "MORNING", "NOON", "AFTERNOON", "EVENING", "NIGHT")
        val weathers = setOf("ANY", "RAIN", "HOT", "COLD", "WIND", "NICE", "NIGHT_COLD")
        for (l in lines) {
            assertTrue("非法情绪 ${l.mood}", l.mood in moods)
            assertTrue("非法时段 ${l.time}", l.time in times)
            assertTrue("非法天气 ${l.weather}", l.weather in weathers)
        }
    }

    @Test
    fun `引擎过滤-雨天时段命中天气话术`() {
        val engine = DialogueEngine(lines, Random(7))
        val r = engine.pick(
            DialogueRequest(event = "WEATHER", mood = PetMoodState.CALM, hour = 15, weatherKey = "RAIN", petName = "小白"),
        )
        assertNotNull(r)
        assertTrue(r!!.text.isNotBlank())
    }

    @Test
    fun `引擎过滤-深夜命中NIGHT时段问候`() {
        val engine = DialogueEngine(lines, Random(7))
        val r = engine.pick(DialogueRequest(event = "GREET", hour = 23, petName = "皮可"))
        assertNotNull(r)
    }

    @Test
    fun `宠物名占位符替换`() {
        val engine = DialogueEngine(lines, Random(7))
        repeat(80) {
            val r = engine.pick(DialogueRequest(event = "CHAT", petName = "小毛豆")) ?: return@repeat
            assertTrue(!r.text.contains("{name}"))
        }
    }

    @Test
    fun `同一事件近期不重复`() {
        val engine = DialogueEngine(lines, Random(7))
        val seen = mutableSetOf<String>()
        repeat(12) {
            val r = engine.pick(DialogueRequest(event = "TAP", mood = PetMoodState.HAPPY, petName = "皮可"))!!
            assertTrue("连续出现重复台词: ${r.text}", r.text !in seen || seen.size >= 8)
            seen.add(r.text)
        }
    }
}

class WeatherJsonTest {

    private val geocodeJson = """{"results":[{"id":1816670,"name":"北京","latitude":39.9075,"longitude":116.39723,
        "country":"中国","admin1":"北京市","country_code":"CN","timezone":"Asia/Shanghai"}]}"""

    private fun forecastJson(code: Int, temp: Double, wind: Double, isDay: Int) = """{"current":{
        "time":"2026-11-07T01:45","interval":900,"temperature_2m":$temp,"relative_humidity_2m":59,
        "apparent_temperature":${temp - 3.0},"is_day":$isDay,"weather_code":$code,"wind_speed_10m":$wind}}"""

    @Test
    fun `解析地理编码结果`() {
        val cities = WeatherJson.parseGeocode(geocodeJson)
        assertEquals(1, cities.size)
        assertEquals("北京", cities[0].name)
        assertEquals(39.9075, cities[0].latitude, 0.0001)
        assertEquals("中国", cities[0].country)
    }

    @Test
    fun `解析当前天气`() {
        val info = WeatherJson.parseForecast(forecastJson(61, 18.0, 12.0, 1), "北京", 123L)!!
        assertEquals(18.0, info.temperatureC, 0.001)
        assertEquals(61, info.code)
        assertEquals(true, info.isDay)
        assertEquals("北京", info.cityName)
    }

    @Test
    fun `天气键映射`() {
        val rain = WeatherJson.parseForecast(forecastJson(61, 18.0, 5.0, 1), "x", 0L)!!
        assertEquals("RAIN", rain.weatherKey())
        val hot = WeatherJson.parseForecast(forecastJson(0, 34.0, 5.0, 1), "x", 0L)!!
        assertEquals("HOT", hot.weatherKey())
        val cold = WeatherJson.parseForecast(forecastJson(0, 1.0, 5.0, 1), "x", 0L)!!
        assertEquals("COLD", cold.weatherKey())
        val wind = WeatherJson.parseForecast(forecastJson(0, 20.0, 55.0, 1), "x", 0L)!!
        assertEquals("WIND", wind.weatherKey())
        val nice = WeatherJson.parseForecast(forecastJson(0, 22.0, 8.0, 1), "x", 0L)!!
        assertEquals("NICE", nice.weatherKey())
        val nightCold = WeatherJson.parseForecast(forecastJson(0, 8.0, 5.0, 0), "x", 0L)!!
        assertEquals("NIGHT_COLD", nightCold.weatherKey())
    }

    @Test
    fun `天气展示与emoji非空`() {
        val info: WeatherInfo = WeatherJson.parseForecast(forecastJson(0, 22.0, 8.0, 1), "x", 0L)!!
        assertTrue(info.description().isNotBlank())
        assertTrue(info.emoji().isNotBlank())
    }
}
