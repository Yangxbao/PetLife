package com.petlife.app.pet.weather

import android.content.Context
import android.util.Log
import com.petlife.app.data.repo.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 天气存储与调度：
 *  - Open-Meteo 免费无密钥；只上传所选城市的经纬度
 *  - 缓存 30 分钟；失败静默（返回旧缓存或 null），不影响核心养成功能
 *  - 可在设置页整体关闭
 */
class WeatherStore(context: Context, private val service: WeatherService = WeatherService()) {

    private val prefs = Prefs(context)
    private val tag = "PetLifeWeather"

    companion object {
        const val CACHE_TTL_MS = 30 * 60_000L
    }

    val enabled: Flow<Boolean> = prefs.weatherEnabled
    val city: Flow<Prefs.WeatherCity?> = prefs.weatherCity

    private val _weather = MutableStateFlow<WeatherInfo?>(null)
    val weather: Flow<WeatherInfo?> = _weather

    /** 应用启动时调用：恢复缓存 + 按需刷新 */
    suspend fun restoreAndRefresh() {
        val enabled = prefs.weatherEnabled.first()
        if (!enabled) return
        // 1. 先用缓存恢复（UI 立即有值）
        val cache = prefs.weatherCache.first()
        if (cache != null) {
            fromJson(cache.json)?.let { _weather.value = it.copy(fetchedAtMillis = cache.cachedAt) }
        }
        // 2. 过期则刷新
        refreshIfNeeded()
    }

    /** 按需刷新：缓存超过 TTL 才真正请求网络 */
    suspend fun refreshIfNeeded(force: Boolean = false): WeatherInfo? {
        val enabled = prefs.weatherEnabled.first()
        if (!enabled) return null
        val city = prefs.weatherCity.first() ?: return null
        val cache = prefs.weatherCache.first()
        if (!force && cache != null && System.currentTimeMillis() - cache.cachedAt < CACHE_TTL_MS) {
            return fromJson(cache.json)?.copy(fetchedAtMillis = cache.cachedAt)
        }
        return withContext(Dispatchers.IO) {
            runCatching { service.fetchCurrent(city.lat, city.lon, city.name, System.currentTimeMillis()) }
                .onFailure { Log.w(tag, "天气获取失败（静默降级）：${it.message}") }
                .getOrNull()
        }?.also { info ->
            _weather.value = info
            prefs.setWeatherCache(toJson(info), info.fetchedAtMillis)
        }
    }

    suspend fun searchCity(query: String): List<CityResult> = withContext(Dispatchers.IO) {
        runCatching { service.geocode(query) }
            .onFailure { Log.w(tag, "城市搜索失败：${it.message}") }
            .getOrDefault(emptyList())
    }

    /**
     * 一键 GPS 定位（v1.3.0）：LocationManager 单次取位（无 GMS 依赖），
     * 拿到经纬度后直接反查城市名（Android Geocoder，失败则用"我的位置"）。
     */
    suspend fun locate(context: Context): Prefs.WeatherCity? = withContext(Dispatchers.IO) {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? android.location.LocationManager
            ?: return@withContext null
        var loc: android.location.Location? = null
        for (p in listOf(android.location.LocationManager.NETWORK_PROVIDER, android.location.LocationManager.GPS_PROVIDER, android.location.LocationManager.PASSIVE_PROVIDER)) {
            runCatching { lm.getLastKnownLocation(p) }.getOrNull()?.let {
                if (loc == null || it.accuracy < loc!!.accuracy) loc = it
            }
        }
        if (loc == null) {
            val latch = java.util.concurrent.CountDownLatch(1)
            val listener = android.location.LocationListener { l ->
                if (loc == null) { loc = l; latch.countDown() }
            }
            runCatching {
                for (p in lm.allProviders) {
                    if (lm.isProviderEnabled(p)) {
                        lm.requestLocationUpdates(p, 0L, 0f, listener, android.os.Looper.getMainLooper())
                    }
                }
                latch.await(8, java.util.concurrent.TimeUnit.SECONDS)
            }
            runCatching { lm.removeUpdates(listener) }
        }
        val location = loc ?: run { Log.w(tag, "定位失败：无可用位置"); return@withContext null }
        Log.i(tag, "定位成功：${location.latitude},${location.longitude}")
        val displayName = runCatching {
            android.location.Geocoder(context, java.util.Locale.getDefault())
                .getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()?.locality
        }.getOrNull() ?: "我的位置"
        Prefs.WeatherCity(displayName.ifBlank { "我的位置" }, location.latitude, location.longitude)
    }

    suspend fun setCity(city: Prefs.WeatherCity?) {
        prefs.setWeatherCity(city)
        _weather.value = null
        if (city != null) refreshIfNeeded(force = true)
    }

    suspend fun setEnabled(v: Boolean) {
        prefs.setWeatherEnabled(v)
        if (v) refreshIfNeeded(force = true)
    }

    // ---- WeatherInfo ↔ JSON（缓存用） ----

    fun toJson(info: WeatherInfo): String = JSONObject().apply {
        put("t", info.temperatureC)
        put("a", info.apparentC)
        put("h", info.humidity)
        put("w", info.windKmh)
        put("c", info.code)
        put("d", if (info.isDay) 1 else 0)
        put("city", info.cityName)
    }.toString()

    fun fromJson(json: String): WeatherInfo? = runCatching {
        val o = JSONObject(json)
        WeatherInfo(
            temperatureC = o.getDouble("t"),
            apparentC = o.getDouble("a"),
            humidity = o.getInt("h"),
            windKmh = o.getDouble("w"),
            code = o.getInt("c"),
            isDay = o.optInt("d", 1) == 1,
            fetchedAtMillis = 0L,
            cityName = o.optString("city", ""),
        )
    }.getOrNull()
}
