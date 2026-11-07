package com.petlife.app.pet.weather

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 天气数据模型与 Open-Meteo 接入（免费、无需 API Key）。
 *
 * 数据来源：https://open-meteo.com/（CC BY 4.0，非商业使用需注明来源）
 * 只发送城市经纬度以查询天气；不上传任何用户行为数据。
 */
data class CityResult(
    val name: String,
    val admin1: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val population: Long = 0L,
)

data class WeatherInfo(
    val temperatureC: Double,
    val apparentC: Double,
    val humidity: Int,
    val windKmh: Double,
    val code: Int,
    val isDay: Boolean,
    val fetchedAtMillis: Long,
    val cityName: String,
) {
    /** WMO 天气代码 → 展示 */
    fun description(): String = when (code) {
        0 -> "晴"
        1, 2 -> "多云转晴"
        3 -> "阴"
        45, 48 -> "雾"
        in 51..57 -> "毛毛雨"
        in 61..67 -> "雨"
        in 71..77 -> "雪"
        in 80..82 -> "阵雨"
        in 85..86 -> "阵雪"
        in 95..99 -> "雷雨"
        else -> "未知"
    }

    fun emoji(): String = when (code) {
        0 -> if (isDay) "☀️" else "🌙"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..67, in 80..82 -> "🌧️"
        in 71..77, in 85..86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    /**
     * 映射为话术系统的天气键。
     * 优先级：雨/雪 > 高温 > 低温 > 大风 > 夜间降温 > 舒适
     */
    fun weatherKey(): String = when {
        code in 51..67 || code in 80..82 || code in 95..99 -> "RAIN"
        code in 71..77 || code in 85..86 -> "COLD"
        temperatureC >= 32 -> "HOT"
        temperatureC <= 2 -> "COLD"
        windKmh >= 40 -> "WIND"
        !isDay && temperatureC <= 10 -> "NIGHT_COLD"
        temperatureC in 12.0..28.0 && code <= 3 -> "NICE"
        else -> "ANY"
    }
}

object WeatherJson {

    /** 解析 Open-Meteo 地理编码结果（含人口数，用于把大城市排前） */
    fun parseGeocode(jsonText: String): List<CityResult> {
        val out = mutableListOf<CityResult>()
        runCatching {
            val arr = JSONObject(jsonText).optJSONArray("results") ?: return out
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                out += CityResult(
                    name = o.optString("name"),
                    admin1 = o.optString("admin1", ""),
                    country = o.optString("country", ""),
                    latitude = o.optDouble("latitude", 0.0),
                    longitude = o.optDouble("longitude", 0.0),
                    population = o.optLong("population", 0L),
                )
            }
        }
        return out
    }

    /** 解析 Open-Meteo current 天气 */
    fun parseForecast(jsonText: String, cityName: String, nowMillis: Long): WeatherInfo? {
        return runCatching {
            val current = JSONObject(jsonText).optJSONObject("current") ?: return null
            WeatherInfo(
                temperatureC = current.optDouble("temperature_2m", Double.NaN),
                apparentC = current.optDouble("apparent_temperature", Double.NaN),
                humidity = current.optInt("relative_humidity_2m", 0),
                windKmh = current.optDouble("wind_speed_10m", 0.0),
                code = current.optInt("weather_code", -1),
                isDay = current.optInt("is_day", 1) == 1,
                fetchedAtMillis = nowMillis,
                cityName = cityName,
            ).takeIf { !it.temperatureC.isNaN() }
        }.getOrNull()
    }
}

/**
 * Open-Meteo HTTP 客户端（HttpURLConnection，无第三方依赖，全部在 IO 线程调用）。
 */
class WeatherService(private val connectTimeoutMs: Int = 6000, private val readTimeoutMs: Int = 8000) {

    fun geocode(query: String, count: Int = 10): List<CityResult> {
        val base = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=${URLEncoder.encode(query, "UTF-8")}&count=$count&format=json"
        // 三路查询合并：中文原名 + 中文"市"后缀（中文库缺大城市记录时的补救）+ 英文
        val zh = runCatching { WeatherJson.parseGeocode(httpGet("$base&language=zh")) }.getOrDefault(emptyList())
        val zhCity = if (query.endsWith("市") || query.endsWith("省") || query.endsWith("县") || query.endsWith("区")) {
            emptyList()
        } else {
            runCatching {
                WeatherJson.parseGeocode(httpGet("$base&language=zh&name=${URLEncoder.encode(query + "市", "UTF-8")}"))
            }.getOrDefault(emptyList())
        }
        val en = runCatching { WeatherJson.parseGeocode(httpGet(base)) }.getOrDefault(emptyList())
        val merged = linkedMapOf<String, CityResult>()
        for (r in zh + zhCity + en) {
            val key = "${r.name}|${"%.3f".format(r.latitude)}|${"%.3f".format(r.longitude)}"
            if (key !in merged || (merged[key]!!.population < r.population)) merged[key] = r
        }
        // 人口多的排前（把真正的城市排到村镇前面）
        return merged.values.sortedByDescending { it.population }
    }

    fun fetchCurrent(latitude: Double, longitude: Double, cityName: String, nowMillis: Long): WeatherInfo? {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$latitude&longitude=$longitude" +
            "&current=temperature_2m,relative_humidity_2m,apparent_temperature,is_day,weather_code,wind_speed_10m" +
            "&timezone=auto"
        return WeatherJson.parseForecast(httpGet(url), cityName, nowMillis)
    }

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = connectTimeoutMs
        conn.readTimeout = readTimeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
