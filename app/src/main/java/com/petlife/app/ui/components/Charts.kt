package com.petlife.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.petlife.app.ui.theme.Cream100
import com.petlife.app.ui.theme.InkSecondary

data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
)

/** 折线图（最多 2 组数据 + 渐变填充 + 顶部圆点 + x 轴标签） */
@Composable
fun LineChart(
    labels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yMaxOverride: Float? = null,
) {
    Canvas(modifier = modifier.fillMaxWidth().height(170.dp)) {
        val w = size.width
        val h = size.height
        val labelH = 34f
        val plotH = h - labelH
        val n = labels.size
        if (n == 0) return@Canvas

        val yMax = yMaxOverride ?: series.maxOfOrNull { s -> (s.values.maxOrNull() ?: 0f) } ?: 1f
        val yMaxSafe = (yMax.coerceAtLeast(10f)) * 1.15f

        // 横向网格
        val gridPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(50, 138, 116, 98)
            strokeWidth = 1.5f
        }
        for (i in 0..3) {
            val y = plotH * i / 3f
            drawContext.canvas.nativeCanvas.drawLine(0f, y, w, y, gridPaint)
        }

        // x 轴标签
        val labelPaint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.argb(200, 138, 116, 98)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        labels.forEachIndexed { i, label ->
            val x = if (n == 1) w / 2f else w * i / (n - 1)
            drawContext.canvas.nativeCanvas.drawText(label, x.coerceIn(20f, w - 20f), h - 8f, labelPaint)
        }

        fun px(i: Int, v: Float): Offset {
            val x = if (n == 1) w / 2f else w * i / (n - 1)
            val y = plotH - (v / yMaxSafe) * plotH * 0.92f
            return Offset(x, y)
        }

        for (s in series) {
            if (s.values.isEmpty()) continue
            val pts = s.values.indices.map { px(it, s.values[it]) }
            // 填充
            val fill = Path().apply {
                moveTo(pts.first().x, plotH)
                pts.forEach { lineTo(it.x, it.y) }
                lineTo(pts.last().x, plotH)
                close()
            }
            drawPath(
                fill,
                Brush.verticalGradient(
                    listOf(s.color.copy(alpha = 0.28f), s.color.copy(alpha = 0.02f)),
                    startY = 0f, endY = plotH,
                ),
            )
            // 线
            val line = Path().apply {
                pts.forEachIndexed { idx, p -> if (idx == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
            }
            drawPath(line, s.color, style = Stroke(width = 5f, cap = StrokeCap.Round))
            // 点
            pts.forEach { drawCircle(s.color, 7f, it); drawCircle(Color.White, 3.2f, it) }
        }
    }
}

/** 柱状图（自律评分等），支持目标线 */
@Composable
fun BarChart(
    labels: List<String>,
    values: List<Float>,
    barColor: Color,
    modifier: Modifier = Modifier,
    valueSuffix: String = "",
) {
    Canvas(modifier = modifier.fillMaxWidth().height(170.dp)) {
        val w = size.width
        val h = size.height
        val labelH = 34f
        val plotH = h - labelH
        val n = values.size
        if (n == 0) return@Canvas

        val yMax = (values.maxOrNull() ?: 100f).coerceAtLeast(100f) * 1.05f
        val slot = w / n
        val barW = slot * 0.52f

        val labelPaint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.argb(200, 138, 116, 98)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val valuePaint = android.graphics.Paint().apply {
            textSize = 24f
            color = android.graphics.Color.argb(230, 74, 55, 40)
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        labels.forEachIndexed { i, label ->
            val cx = slot * i + slot / 2
            val v = values[i].coerceAtLeast(0f)
            val barH = (v / yMax) * plotH
            val topLeft = Offset(cx - barW / 2, plotH - barH)
            drawRoundRect(
                brush = Brush.verticalGradient(
                    listOf(barColor.copy(alpha = 0.9f), barColor.copy(alpha = 0.55f)),
                    startY = topLeft.y, endY = plotH,
                ),
                topLeft = topLeft,
                size = Size(barW, barH.coerceAtLeast(4f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 3f, barW / 3f),
            )
            if (valueSuffix.isNotEmpty() && v > 0f) {
                drawContext.canvas.nativeCanvas.drawText(
                    "${v.toInt()}$valueSuffix", cx, (topLeft.y - 8f).coerceAtLeast(18f), valuePaint,
                )
            }
            drawContext.canvas.nativeCanvas.drawText(label, cx, h - 8f, labelPaint)
        }
    }
}
