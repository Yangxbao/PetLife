package com.petlife.app.ui.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin

/**
 * 首页宠物舞台环境：按时段渐变天空 + 日/月/星 + 飘云 + 地面平台。
 * 所有元素纯 Canvas 绘制，零资源、零分配热点。
 */
@Composable
fun HomeStage(hour: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        // 舞台背景绘制兜底：异常只跳过该帧
        runCatching {
            val t = System.currentTimeMillis() / 1000f
            drawSky(hour)
            if (hour in 20..23 || hour < 6) drawStars(hour, t)
            drawSunMoon(hour)
            drawClouds(t)
            drawGround(hour)
        }.onFailure { android.util.Log.e("PetLifeCrash", "舞台绘制异常（已跳过该帧）", it) }
    }
}

private data class SkyColors(val top: Color, val mid: Color, val bottom: Color, val ground: Color, val isNight: Boolean)

private fun skyFor(hour: Int): SkyColors = when (hour) {
    in 5..7 -> SkyColors(Color(0xFFFFD9E8), Color(0xFFFFECD2), Color(0xFFFDF6E3), Color(0xFFBFE0C0), false) // 晨
    in 8..15 -> SkyColors(Color(0xFFBFE3FF), Color(0xFFE3F4FF), Color(0xFFFFF6E8), Color(0xFFB7DFB9), false) // 昼
    in 16..18 -> SkyColors(Color(0xFFFFC4A3), Color(0xFFFFE0C2), Color(0xFFFFF3D9), Color(0xFFC4DFA8), false) // 黄昏
    in 19..21 -> SkyColors(Color(0xFF5C6B9E), Color(0xFF8B93C4), Color(0xFFC9B7D8), Color(0xFF6E8F79), true)  // 入夜
    else -> SkyColors(Color(0xFF232A4D), Color(0xFF3A4370), Color(0xFF565C8F), Color(0xFF3F5D51), true)       // 深夜
}

private fun DrawScope.drawSky(hour: Int) {
    val sky = skyFor(hour)
    drawRect(
        Brush.verticalGradient(listOf(sky.top, sky.mid, sky.bottom)),
        size = size,
    )
}

private fun DrawScope.drawStars(hour: Int, t: Float) {
    val sky = skyFor(hour)
    val stars = listOf(
        0.10f to 0.12f, 0.22f to 0.07f, 0.35f to 0.16f, 0.48f to 0.06f, 0.62f to 0.13f,
        0.75f to 0.08f, 0.88f to 0.15f, 0.15f to 0.24f, 0.55f to 0.22f, 0.82f to 0.26f,
        0.30f to 0.30f, 0.68f to 0.33f,
    )
    stars.forEachIndexed { i, (fx, fy) ->
        val tw = 0.5f + 0.5f * sin(t * (1.2f + i * 0.23f) + i * 1.7f)
        drawCircle(
            Color.White.copy(alpha = (if (sky.isNight) 0.7f else 0.25f) * (0.4f + 0.6f * tw)),
            size.minDimension * 0.005f * (1f + 0.3f * tw),
            Offset(size.width * fx, size.height * fy),
        )
    }
}

private fun DrawScope.drawSunMoon(hour: Int) {
    val w = size.width
    val h = size.height
    val r = w * 0.055f
    if (hour in 6..18) {
        // 太阳：6 点在左地平线 → 12 点正上方 → 18 点右侧落下
        val progress = ((hour - 6) / 12f).coerceIn(0f, 1f)
        val cx = w * (0.15f + 0.7f * progress)
        val cy = h * 0.20f - sin(PI * progress).toFloat() * h * 0.10f
        drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.35f), r * 1.9f, Offset(cx, cy))
        drawCircle(Color(0xFFFFD873), r, Offset(cx, cy))
        drawCircle(Color(0xFFFFF3C9), r * 0.62f, Offset(cx, cy))
    } else {
        // 月亮：夜半偏右
        val cx = w * 0.76f
        val cy = h * 0.14f
        drawCircle(Color(0xFFF4F1DE).copy(alpha = 0.25f), r * 1.7f, Offset(cx, cy))
        drawCircle(Color(0xFFF7F3DF), r * 0.85f, Offset(cx, cy))
        drawCircle(skyFor(hour).top.copy(alpha = 0.92f), r * 0.72f, Offset(cx + r * 0.42f, cy - r * 0.28f))
    }
}

private fun DrawScope.drawClouds(t: Float) {
    val w = size.width
    val h = size.height
    val clouds = listOf(
        Triple(0.0f, 0.16f, 1.0f),
        Triple(0.45f, 0.09f, 0.7f),
        Triple(0.72f, 0.24f, 0.85f),
    )
    clouds.forEachIndexed { i, (speed, fy, scale) ->
        val drift = ((t * speed * 8f + i * 400f) % (w + w * 0.4f)) - w * 0.2f
        val cy = h * fy
        val cw = w * 0.16f * scale
        val cloud = Color.White.copy(alpha = 0.55f)
        drawCircle(cloud, cw * 0.34f, Offset(drift, cy))
        drawCircle(cloud, cw * 0.26f, Offset(drift + cw * 0.3f, cy + cw * 0.06f))
        drawCircle(cloud, cw * 0.22f, Offset(drift - cw * 0.32f, cy + cw * 0.08f))
        drawCircle(cloud, cw * 0.24f, Offset(drift + cw * 0.12f, cy - cw * 0.16f))
    }
}

private fun DrawScope.drawGround(hour: Int) {
    val sky = skyFor(hour)
    val groundTop = size.height * 0.80f
    // 远景地面
    drawRect(
        Brush.verticalGradient(listOf(sky.ground, sky.ground.copy(alpha = 0.75f))),
        topLeft = Offset(0f, groundTop),
        size = Size(size.width, size.height - groundTop),
    )
    // 平台
    drawRoundRect(
        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0.08f))),
        topLeft = Offset(size.width * 0.10f, groundTop + size.height * 0.015f),
        size = Size(size.width * 0.80f, size.height * 0.075f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.width * 0.06f, size.width * 0.06f),
    )
}
