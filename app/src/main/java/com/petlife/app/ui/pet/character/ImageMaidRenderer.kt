package com.petlife.app.ui.pet.character

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 图片立绘渲染 v2 —— 「保留原图表情 + 加法式情绪叠加」。
 *
 * 不用肤色补丁覆盖原图表情（会变形），改为在角色眼睛/脸颊区域
 * 叠加半透明情绪效果层。原图二次元脸保持完美。
 */
fun DrawScope.drawImageMaid(
    base: ImageBitmap,
    anchor: FaceAnchor,
    stage: com.petlife.app.data.model.PetStage,
    growthT: Float,
    expression: Expression,
    pose: MaidPose,
    timeMs: Long,
    annoyed: Boolean,
) {
    val w = size.width
    val h = size.height
    val sp = MaidStageVisuals.forStage(stage, growthT)

    val drawH = h * 0.92f * (anchor.stageScale[stage] ?: 1f)
    val drawW = drawH * (anchor.spriteW.toFloat() / anchor.spriteH.toFloat())
    val cx = w / 2f + pose.bodyDx * w * 0.20f
    val top = h * 0.90f - drawH
    val pivot = Offset(cx, top + drawH)

    val breathe = 1f + pose.breathe * 0.012f
    val turnScaleX = if (pose.turn > 0f) {
        val c = kotlin.math.cos(PI * pose.turn).toFloat()
        if (abs(c) < 0.15f) 0.15f * (if (c >= 0f) 1f else -1f) else c
    } else 1f

    val jumpPx = pose.jump * h
    val tx = cx - drawW * breathe * turnScaleX / 2f
    val ty = top + jumpPx
    val pivotX = drawW / 2f
    val pivotY = drawH
    val scaleX = breathe * turnScaleX

    // ---- 地面阴影 ----
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x404A3728), Color.Transparent),
            center = Offset(cx, h * 0.905f),
            radius = drawW * 0.55f * (1f + pose.jump * 1.4f),
        ),
        topLeft = Offset(cx - drawW * 0.55f, h * 0.875f),
        size = Size(kotlin.math.max(1f, drawW * 1.1f), kotlin.math.max(1f, h * 0.05f)),
    )

    // ---- 进化辉光 ----
    val glowA = sp.glow + pose.glowBoost * 0.5f
    if (glowA > 0.02f) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFE9B0).copy(alpha = 0.30f * glowA), Color.Transparent),
                center = Offset(cx, top + drawH * 0.35f),
                radius = drawW * 0.75f,
            ),
            radius = drawW * 0.75f,
            center = Offset(cx, top + drawH * 0.35f),
        )
    }

    // ---- 立绘本体 ----
    withTransform({
        translate(left = tx, top = ty)
        scale(scaleX, breathe, pivot = Offset(pivotX, pivotY))
        rotate(pose.bodyTilt, pivot = Offset(pivotX, pivotY))
        rotate(pose.headTilt * 0.4f, pivot = Offset(pivotX, pivotY * 0.2f))
    }) {
        drawImage(base, dstOffset = androidx.compose.ui.unit.IntOffset.Zero, dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)))
    }

    // ---- 锚点映射 ----
    val sx = { fx: Float -> tx + pivotX + (fx * drawW - pivotX) * scaleX }
    val sy = { fy: Float -> ty + pivotY + (fy * drawH - pivotY) * breathe }
    val eyeLX = sx(anchor.eyeLX)
    val eyeLY = sy(anchor.eyeLY)
    val eyeRX = sx(anchor.eyeRX)
    val eyeRY = sy(anchor.eyeRY)
    val faceCX = sx(anchor.faceCx)
    val faceCY = sy(anchor.faceCy)
    val faceR = anchor.eyeDx * drawW * 2.8f

    // ---- 情绪叠加（加法式） ----
    drawMoodOverlay(expression, annoyed, faceCX, faceCY, eyeLX, eyeLY, eyeRX, eyeRY, faceR, w, pose, timeMs)
}

private fun sz(w: Float, h: Float): Size = Size(
    kotlin.math.max(0.5f, if (w.isNaN()) 0.5f else w),
    kotlin.math.max(0.5f, if (h.isNaN()) 0.5f else h),
)

private fun DrawScope.drawMoodOverlay(
    expression: Expression,
    annoyed: Boolean,
    faceCX: Float, faceCY: Float,
    eyeLX: Float, eyeLY: Float,
    eyeRX: Float, eyeRY: Float,
    faceR: Float,
    w: Float,
    pose: MaidPose,
    timeMs: Long,
) {
    val f = Faces.of(expression)
    val bob = sin(timeMs / 500.0 * PI).toFloat() * 0.5f + 0.5f
    val pulse = sin(timeMs / 300.0 * PI).toFloat() * 0.5f + 0.5f

    // 1. 情绪色调
    val tintColor = when {
        annoyed || expression == Expression.ANGRY -> Color(0x12E57373)
        expression == Expression.UPSET -> Color(0x128FC8F0)
        expression == Expression.SHY -> Color(0x14FFB3AD)
        expression == Expression.HAPPY -> Color(0x0AFFE9B0)
        else -> Color.Transparent
    }
    if (tintColor != Color.Transparent) {
        drawOval(tintColor, topLeft = Offset(faceCX - faceR, faceCY - faceR * 0.8f), size = Size(faceR * 2f, faceR * 1.6f))
    }

    // 2. 腮红
    val blushAlpha = f.blush.coerceIn(0f, 1f) * 0.55f
    if (blushAlpha > 0.05f) {
        for (ex in listOf(eyeLX, eyeRX)) {
            val bx = ex + (if (ex < faceCX) -faceR * 0.26f else faceR * 0.26f)
            drawOval(
                Brush.radialGradient(listOf(Color(0xFFFFB3AD).copy(alpha = 0.7f * blushAlpha), Color.Transparent)),
                topLeft = Offset(bx - faceR * 0.12f, eyeLY + faceR * 0.16f),
                size = Size(faceR * 0.24f, faceR * 0.12f),
            )
            if (f.blushLines && blushAlpha > 0.4f) {
                for (i in 0..2) {
                    drawLine(
                        Color(0xFFFF9E9E).copy(alpha = 0.7f * blushAlpha),
                        start = Offset(bx - faceR * 0.06f + i * faceR * 0.05f, eyeLY + faceR * 0.14f),
                        end = Offset(bx - faceR * 0.08f + i * faceR * 0.05f, eyeLY + faceR * 0.22f),
                        strokeWidth = w * 0.004f, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }

    // 3. 泪滴
    if (f.tears) {
        val dropT = sin(timeMs / 700.0 * PI).toFloat() * 0.5f + 0.5f
        for (ex in listOf(eyeLX, eyeRX)) {
            drawCircle(Color(0xFF8FC8F0).copy(alpha = 0.75f), faceR * 0.035f, Offset(ex, eyeLY + faceR * (0.12f + dropT * 0.08f)))
        }
    }

    // 4. 心形
    if (expression == Expression.SPOILED || expression == Expression.HAPPY) {
        drawHeart(faceCX + faceR * 0.3f, eyeLY - faceR * (0.25f + pulse * 0.06f), faceR * 0.06f, Color(0xFFFF8FA3).copy(alpha = 0.7f * pulse))
    }

    // 5. 星光
    if (expression == Expression.SMUG || pose.glowBoost > 0.1f) {
        for (i in 0..2) {
            val ang = (-100 + i * 40) * PI / 180.0
            val r = faceR * (0.9f + 0.1f * pulse)
            drawStar(faceCX + kotlin.math.cos(ang).toFloat() * r, faceCY + kotlin.math.sin(ang).toFloat() * r - faceR * 0.5f, faceR * (0.03f + 0.015f * pulse), Color(0xFFFFE08A).copy(alpha = 0.8f))
        }
    }

    // 6. 怒纹
    if (f.angerMark) {
        val mx = faceCX + faceR * 0.65f
        val my = faceCY - faceR * 0.72f
        val s = faceR * 0.035f
        drawLine(Color(0xFFE57373).copy(alpha = 0.9f), Offset(mx - s, my), Offset(mx + s, my + s * 2f), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
        drawLine(Color(0xFFE57373).copy(alpha = 0.9f), Offset(mx + s, my), Offset(mx - s, my + s * 2f), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
    }

    // 7. 汗滴
    if (f.sweat) {
        drawCircle(Color(0xFFA8D8F0).copy(alpha = 0.9f), faceR * 0.045f, Offset(faceCX + faceR * 0.72f, faceCY - faceR * (0.4f + bob * 0.15f)))
    }

    // 8. 惊讶线
    if (f.shockLines) {
        for (i in 0..2) {
            val ang = (-70 + i * 35) * PI / 180.0
            val r1 = faceR * 0.75f
            val r2 = faceR * 0.92f
            drawLine(
                Color(0xFF6B7A99).copy(alpha = 0.7f),
                start = Offset(faceCX + kotlin.math.cos(ang).toFloat() * r1, faceCY + kotlin.math.sin(ang).toFloat() * r1 - faceR * 0.55f),
                end = Offset(faceCX + kotlin.math.cos(ang).toFloat() * r2, faceCY + kotlin.math.sin(ang).toFloat() * r2 - faceR * 0.55f),
                strokeWidth = w * 0.005f, cap = StrokeCap.Round,
            )
        }
    }

    // 9. Zzz
    if (expression == Expression.SLEEPY || pose.slump > 0.5f) {
        val zPaint = android.graphics.Paint().apply {
            textSize = faceR * 0.16f
            isAntiAlias = true
            color = android.graphics.Color.argb(160, 138, 116, 98)
        }
        drawContext.canvas.nativeCanvas.drawText("z", faceCX + faceR * 0.55f, faceCY - faceR * (0.35f + bob * 0.1f), zPaint)
        drawContext.canvas.nativeCanvas.drawText("Z", faceCX + faceR * 0.70f, faceCY - faceR * (0.53f + bob * 0.1f), zPaint)
    }
}

private fun DrawScope.drawHeart(x: Float, y: Float, r: Float, color: Color) {
    val path = Path().apply {
        moveTo(x, y + r * 0.6f)
        cubicTo(x - r * 1.4f, y - r * 0.4f, x - r * 0.5f, y - r * 1.2f, x, y - r * 0.3f)
        cubicTo(x + r * 0.5f, y - r * 1.2f, x + r * 1.4f, y - r * 0.4f, x, y + r * 0.6f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawStar(x: Float, y: Float, r: Float, color: Color) {
    val path = Path()
    for (i in 0 until 10) {
        val rad = (-90 + i * 36) * PI / 180.0
        val rr = if (i % 2 == 0) r else r * 0.45f
        val px = kotlin.math.cos(rad).toFloat() * rr
        val py = kotlin.math.sin(rad).toFloat() * rr
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    drawPath(path, color)
}
