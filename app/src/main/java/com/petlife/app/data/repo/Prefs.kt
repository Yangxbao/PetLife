package com.petlife.app.data.repo

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "petlife_prefs")

/**
 * 轻量键值状态：
 *  - 结算游标 / 周重置防回拨守卫 / 每日撸宠计数 / 引导标记
 *  - v1.1.0：天气配置与缓存、主动交流冷却、每日通知开关
 */
class Prefs(private val context: Context) {

    private object Keys {
        val LAST_SETTLED_DATE = stringPreferencesKey("last_settled_date")
        val GUARD_LAST_DATE = stringPreferencesKey("guard_last_date")
        val GUARD_WEEK_KEY = stringPreferencesKey("guard_week_key")
        val TAP_DATE = stringPreferencesKey("tap_date")
        val TAP_COUNT = intPreferencesKey("tap_count")
        val ONBOARDED = longPreferencesKey("onboarded")

        // ---- v1.1.0 天气 ----
        val WEATHER_ENABLED = intPreferencesKey("weather_enabled")
        val WEATHER_CITY = stringPreferencesKey("weather_city")
        val WEATHER_COORDS = stringPreferencesKey("weather_coords") // "lat,lon"
        val WEATHER_CACHE = stringPreferencesKey("weather_cache")   // WeatherInfo JSON
        val WEATHER_CACHED_AT = longPreferencesKey("weather_cached_at")

        // ---- v1.1.0 主动交流 / 通知 ----
        val PROACTIVE_LAST_AT = longPreferencesKey("proactive_last_at")
        val NOTIFY_ENABLED = intPreferencesKey("notify_enabled")
        val LAST_OPEN_AT = longPreferencesKey("last_open_at")
    }

    val lastSettledDate: Flow<String?> = context.dataStore.data.map { it[Keys.LAST_SETTLED_DATE] }

    suspend fun lastSettledDateOnce(): String? = context.dataStore.data.first()[Keys.LAST_SETTLED_DATE]

    suspend fun setLastSettledDate(date: String) {
        context.dataStore.edit { it[Keys.LAST_SETTLED_DATE] = date }
    }

    suspend fun guardState(): Pair<String?, String?> {
        val p = context.dataStore.data.first()
        return p[Keys.GUARD_LAST_DATE] to p[Keys.GUARD_WEEK_KEY]
    }

    suspend fun updateGuard(date: String, weekKey: String) {
        context.dataStore.edit {
            it[Keys.GUARD_LAST_DATE] = date
            it[Keys.GUARD_WEEK_KEY] = weekKey
        }
    }

    /** 今日撸宠次数（跨天自动清零） */
    suspend fun registerTap(today: String, limit: Int): Boolean {
        var allowed = false
        context.dataStore.edit { p ->
            val d = p[Keys.TAP_DATE]
            val c = if (d == today) p[Keys.TAP_COUNT] ?: 0 else 0
            if (c < limit) {
                p[Keys.TAP_DATE] = today
                p[Keys.TAP_COUNT] = c + 1
                allowed = true
            }
        }
        return allowed
    }

    val onboarded: Flow<Boolean> = context.dataStore.data.map { (it[Keys.ONBOARDED] ?: 0L) > 0 }

    suspend fun markOnboarded() {
        context.dataStore.edit { it[Keys.ONBOARDED] = System.currentTimeMillis() }
    }

    // ---------- 天气 ----------

    val weatherEnabled: Flow<Boolean> = context.dataStore.data.map { (it[Keys.WEATHER_ENABLED] ?: 1) == 1 }

    suspend fun setWeatherEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_ENABLED] = if (v) 1 else 0 }
    }

    data class WeatherCity(val name: String, val lat: Double, val lon: Double)

    val weatherCity: Flow<WeatherCity?> = context.dataStore.data.map { p ->
        val name = p[Keys.WEATHER_CITY] ?: return@map null
        val coords = p[Keys.WEATHER_COORDS]?.split(",") ?: return@map null
        val lat = coords.getOrNull(0)?.toDoubleOrNull() ?: return@map null
        val lon = coords.getOrNull(1)?.toDoubleOrNull() ?: return@map null
        WeatherCity(name, lat, lon)
    }

    suspend fun setWeatherCity(city: WeatherCity?) {
        context.dataStore.edit { p ->
            if (city == null) {
                p.remove(Keys.WEATHER_CITY); p.remove(Keys.WEATHER_COORDS)
                p.remove(Keys.WEATHER_CACHE); p.remove(Keys.WEATHER_CACHED_AT)
            } else {
                p[Keys.WEATHER_CITY] = city.name
                p[Keys.WEATHER_COORDS] = "${city.lat},${city.lon}"
            }
        }
    }

    data class WeatherCache(val json: String, val cachedAt: Long)

    val weatherCache: Flow<WeatherCache?> = context.dataStore.data.map { p ->
        val json = p[Keys.WEATHER_CACHE] ?: return@map null
        WeatherCache(json, p[Keys.WEATHER_CACHED_AT] ?: 0L)
    }

    suspend fun setWeatherCache(json: String, at: Long) {
        context.dataStore.edit {
            it[Keys.WEATHER_CACHE] = json
            it[Keys.WEATHER_CACHED_AT] = at
        }
    }

    // ---------- 主动交流 ----------

    val proactiveLastAt: Flow<Long> = context.dataStore.data.map { it[Keys.PROACTIVE_LAST_AT] ?: 0L }

    /** 尝试占用一次主动交流机会；距上次不足 [minIntervalMs] 时返回 false */
    suspend fun tryProactive(minIntervalMs: Long, now: Long = System.currentTimeMillis()): Boolean {
        var ok = false
        context.dataStore.edit { p ->
            val last = p[Keys.PROACTIVE_LAST_AT] ?: 0L
            if (now - last >= minIntervalMs) {
                p[Keys.PROACTIVE_LAST_AT] = now
                ok = true
            }
        }
        return ok
    }

    /** 上次打开应用的时间（用于"久未打开"迎接台词） */
    suspend fun lastOpenAtOnce(): Long = context.dataStore.data.first()[Keys.LAST_OPEN_AT] ?: 0L

    suspend fun markOpened(now: Long = System.currentTimeMillis()) {
        context.dataStore.edit { it[Keys.LAST_OPEN_AT] = now }
    }

    // ---------- 通知 ----------

    val notifyEnabled: Flow<Boolean> = context.dataStore.data.map { (it[Keys.NOTIFY_ENABLED] ?: 0) == 1 }

    suspend fun setNotifyEnabled(v: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFY_ENABLED] = if (v) 1 else 0 }
    }
}
