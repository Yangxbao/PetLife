package com.petlife.app.ui.pet.character

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 图片立绘渲染：加载透明 PNG 底图，施加整体动效（呼吸/摇摆/跳跃/转身），
 * 并在面部锚点区域"贴脸"绘制 10 种参数化表情（肤色补丁盖住原图表情 + 程序化五官）。
 */
fun DrawScope.drawImageMaid(
    base: ImageBitmapLike,
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

    // 布局：立绘高度占画布 92%，底部对齐地面
    val drawH = h * 0.92f * (anchor.stageScale[stage] ?: 1f)
    val drawW = drawH * (anchor.spriteW.toFloat() / anchor.spriteH.toFloat())
    val left = (w - drawW) / 2f
    val top = h * 0.90f - drawH
    val cx = w / 2f + pose.bodyDx * w * 0.20f
    val pivot = Offset(cx, top + drawH) // 底部中心为呼吸/旋转轴

    val breathe = 1f + pose.breathe * 0.012f
    val turnScaleX = if (pose.turn > 0f) {
        val c = kotlin.math.cos(PI * pose.turn).toFloat()
        if (abs(c) < 0.15f) 0.15f * (if (c >= 0f) 1f else -1f) else c
    } else 1f

    // 地面阴影
    drawOval(
        brush = Brush.radialGradient(
            listOf(Color(0x404A3728), Color.Transparent),
            center = Offset(cx, h * 0.905f),
            radius = drawW * 0.55f * (1f + pose.jump * 1.4f),
        ),
        topLeft = Offset(cx - drawW * 0.55f, h * 0.875f),
        size = sz(drawW * 1.1f, h * 0.05f),
    )

    // 进化辉光
    val glowA = sp.glow + pose.glowBoost * 0.5f
    if (glowA > 0.02f) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFE9B0).copy(alpha = 0.35f * glowA), Color.Transparent),
                center = Offset(cx, top + drawH * 0.35f),
                radius = drawW * 0.8f,
            ),
            radius = drawW * 0.8f,
            center = Offset(cx, top + drawH * 0.35f),
        )
    }

    val jumpPx = pose.jump * h
    val tx = cx - drawW * breathe * turnScaleX / 2f
    val ty = top + jumpPx
    val pivotX = drawW / 2f
    val pivotY = drawH
    val scaleX = breathe * turnScaleX

    withTransform({
        translate(left = tx, top = ty)
        scale(scaleX, breathe, pivot = Offset(pivotX, pivotY))
        rotate(pose.bodyTilt * 0.7f, pivot = Offset(pivotX, pivotY))
        rotate(pose.headTilt * 0.5f, pivot = Offset(pivotX, pivotY * 0.2f))
    }) {
        drawImage(base, dstOffset = androidx.compose.ui.unit.IntOffset.Zero, dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt().coerceAtLeast(1), drawH.toInt().coerceAtLeast(1)))
    }

    // ---- 表情贴脸（锚点坐标为立绘比例 → 经动效变换映射到画布坐标） ----
    val sx = { fx: Float -> tx + pivotX + (fx * drawW - pivotX) * scaleX }
    val sy = { fy: Float -> ty + pivotY + (fy * drawH - pivotY) * breathe }

    // 1) 肤色补丁盖住原图表情（眉毛到嘴以下）
    val pcx = sx(anchor.patchCx)
    val pcy = sy(anchor.patchCy)
    val prx = anchor.patchRx * drawW
    val pry = anchor.patchRy * drawH
    val skinColor = Color(anchor.skin.r, anchor.skin.g, anchor.skin.b)
    rotate(pose.headTilt * 0.5f, pivot = Offset(pcx, pcy)) {
        drawOval(skinColor, topLeft = Offset(pcx - prx, pcy - pry), size = sz(prx * 2f, pry * 2f))
        // 轻微立体感：下部淡阴影
        drawOval(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x18C9A98E)),
                startY = pcy - pry, endY = pcy + pry,
            ),
            topLeft = Offset(pcx - prx, pcy - pry), size = sz(prx * 2f, pry * 2f),
        )
    }

    // 2) 参数化五官（复用 Faces 表情参数）
    drawImageFace(expression, anchor, pcx, pcy, prx, pry, w, pose, timeMs, annoyed)

    // 3) 情绪标记
    drawMarks(expression, pcx, pcy, prx, pry, w, pose, timeMs)
}


private fun sz(w: Float, h: Float): Size = Size(
    kotlin.math.max(0.5f, if (w.isNaN()) 0.5f else w),
    kotlin.math.max(0.5f, if (h.isNaN()) 0.5f else h),
)

private fun DrawScope.drawMarks(
    expression: Expression,
    pcx: Float, pcy: Float, prx: Float, pry: Float,
    w: Float,
    pose: MaidPose,
    timeMs: Long,
) {
    val f = Faces.of(expression)
    if (f.angerMark) {
        val mx = pcx + prx * 0.85f
        val my = pcy - pry * 1.15f
        val s = prx * 0.10f
        drawLine(Color(0xFFE57373), Offset(mx - s, my), Offset(mx + s, my + s * 2f), strokeWidth = w * 0.008f, cap = StrokeCap.Round)
        drawLine(Color(0xFFE57373), Offset(mx + s, my), Offset(mx - s, my + s * 2f), strokeWidth = w * 0.008f, cap = StrokeCap.Round)
    }
    if (f.sweat) {
        val bob = sin(timeMs / 500.0 * PI).toFloat() * 0.5f + 0.5f
        drawCircle(Color(0xFFA8D8F0).copy(alpha = 0.9f), prx * 0.14f, Offset(pcx + prx * 1.15f, pcy - pry * (0.6f + bob * 0.3f)))
    }
    if (f.shockLines) {
        for (i in 0..2) {
            val ang = (-70 + i * 35) * PI / 180.0
            val r1 = pry * 1.5f
            val r2 = pry * 1.8f
            drawLine(
                Color(0xFF6B7A99).copy(alpha = 0.7f),
                start = Offset(pcx + kotlin.math.cos(ang).toFloat() * r1, pcy + kotlin.math.sin(ang).toFloat() * r1 - pry * 0.8f),
                end = Offset(pcx + kotlin.math.cos(ang).toFloat() * r2, pcy + kotlin.math.sin(ang).toFloat() * r2 - pry * 0.8f),
                strokeWidth = w * 0.007f, cap = StrokeCap.Round,
            )
        }
    }
}

/** 图片模式专用五官绘制（以锚点为基准，风格与程序化模式一致） */
private fun DrawScope.drawImageFace(
    expression: Expression,
    anchor: FaceAnchor,
    pcx: Float, pcy: Float, prx: Float, pry: Float,
    w: Float,
    pose: MaidPose,
    timeMs: Long,
    annoyed: Boolean,
) {
    val f = Faces.of(expression)
    val eyeW = anchor.eyeW * prx * 2f * 0.62f
    val eyeH = anchor.eyeH * pry * 2f * 1.35f
    val eyeDX = anchor.eyeDx * prx * 2f
    val eyeY = anchor.eyeLY * pry * 2f + (pcy - anchor.patchCy * pry * 2f) // 相对补丁中心
    val eyeLX = anchor.eyeLX * prx * 2f + (pcx - anchor.faceCx * prx * 2f)
    val gazeX = (f.gazeX + pose.gazeX).coerceIn(-1f, 1f) * eyeW * 0.18f
    val gazeY = (f.gazeY + pose.gazeY).coerceIn(-1f, 1f) * eyeH * 0.15f
    val blinkY = 1f - sin(PI * pose.blink).toFloat()
    val downcast = expression == Expression.UPSET || expression == Expression.NORMAL || expression == Expression.SHY

    // 眉毛
    val browY = eyeY - eyeH * 0.85f - f.browLift * pry * 2f * 0.06f
    for (side in listOf(-1f, 1f)) {
        val bx = pcx + side * eyeDX
        rotate(f.browAngle * side, pivot = Offset(bx, browY)) {
            drawLine(
                Color(0xFF3A4A73).copy(alpha = 0.9f),
                start = Offset(bx - eyeW * 0.44f, browY + eyeH * 0.10f),
                end = Offset(bx + eyeW * 0.44f, browY - eyeH * 0.04f),
                strokeWidth = w * 0.0075f, cap = StrokeCap.Round,
            )
        }
    }

    // 眼睛
    for ((idx, side) in listOf(-1f, 1f).withIndex()) {
        val ex = pcx + side * eyeDX
        val ey = eyeY
        val openness = ((if (side < 0) f.eyeOpenL else f.eyeOpenR) * (1f - pose.blink)).coerceIn(0f, 1f)
        val closedHappy = expression == Expression.HAPPY || expression == Expression.SMUG
        when {
            openness < 0.14f && !false -> {
                drawArc(
                    Color(0xFF232A40),
                    startAngle = if (closedHappy) 180f else 0f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(ex - eyeW * 0.60f, ey - eyeH * (if (closedHappy) 0.10f else 0.30f)),
                    size = Size(eyeW * 1.2f, eyeH * 0.75f),
                    style = Stroke(width = w * 0.0075f, cap = StrokeCap.Round),
                )
            }
            else -> {
                val eh = eyeH * openness.coerceAtLeast(0.12f)
                // 眼白
                drawOval(Color(0xFFFDFDFF), Offset(ex - eyeW / 2, ey - eh / 2), Size(eyeW, eh))
                // 虹膜（上深下亮）
                val irisW = eyeW * 0.82f * f.irisScale
                val irisH = eh * 0.98f * f.irisScale
                drawOval(
                    Brush.verticalGradient(listOf(Color(0xFF1E3F8F), Color(0xFF58B8F0)), startY = ey - eh / 2, endY = ey + eh / 2),
                    topLeft = Offset(ex + gazeX * 0.5f - irisW / 2, ey + gazeY * 0.5f - irisH / 2),
                    size = Size(irisW, irisH),
                )
                // 瞳孔
                drawOval(
                    Color(0xFF20283C).copy(alpha = 0.8f),
                    topLeft = Offset(ex + gazeX * 0.5f - irisW * 0.16f, ey + gazeY * 0.5f - irisH * 0.24f),
                    size = Size(irisW * 0.32f, irisH * 0.46f),
                )
                // 上睫毛线
                drawArc(
                    Color(0xFF232A40),
                    startAngle = 185f, sweepAngle = 160f, useCenter = false,
                    topLeft = Offset(ex - eyeW * 0.64f, ey - eh * 0.72f),
                    size = Size(eyeW * 1.28f, eh * 1.1f),
                    style = Stroke(width = w * 0.009f, cap = StrokeCap.Round),
                )
                // 高光
                drawCircle(Color.White.copy(alpha = 0.95f), eyeW * 0.14f, Offset(ex + gazeX * 0.5f - irisW * 0.22f, ey + gazeY * 0.5f - irisH * 0.24f))
                drawCircle(Color.White.copy(alpha = 0.6f), eyeW * 0.08f, Offset(ex + gazeX * 0.5f + irisW * 0.20f, ey + gazeY * 0.5f + irisH * 0.16f))
                if (f.sparkleInEye) {
                    val sp2 = 0.5f + 0.5f * sin(timeMs / 240.0 * PI).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.9f * sp2), eyeW * 0.07f, Offset(ex + gazeX * 0.5f, ey + gazeY * 0.5f))
                }
                if (f.tears) {
                    drawOval(Color(0xFF8FC8F0).copy(alpha = 0.55f), Offset(ex - eyeW * 0.5f, ey + eh * 0.26f), Size(eyeW, eh * 0.2f))
                    val dropT = sin(timeMs / 700.0 * PI).toFloat() * 0.5f + 0.5f
                    drawCircle(Color(0xFF8FC8F0).copy(alpha = 0.8f), eyeW * 0.10f, Offset(ex, ey + eh * 0.62f + dropT * eyeH * 0.5f))
                }
            }
        }
    }

    // 腮红
    val blushA = f.blush.coerceIn(0f, 1f)
    if (blushA > 0.03f) {
        for (side in listOf(-1f, 1f)) {
            val bx = pcx + side * anchor.blushDx * prx * 2f
            val by = pcy + pry * 0.62f
            drawOval(
                Brush.radialGradient(listOf(Color(0xFFFFB3AD).copy(alpha = 0.8f * blushA), Color.Transparent)),
                topLeft = Offset(bx - prx * 0.18f, by - pry * 0.10f),
                size = Size(prx * 0.36f, pry * 0.20f),
            )
            if (f.blushLines) {
                for (i in 0..2) {
                    drawLine(
                        Color(0xFFFF9E9E).copy(alpha = 0.85f * blushA),
                        start = Offset(bx - prx * 0.12f + i * prx * 0.09f, by - pry * 0.06f),
                        end = Offset(bx - prx * 0.16f + i * prx * 0.09f, by + pry * 0.06f),
                        strokeWidth = w * 0.005f, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }

    // 嘴
    val mx = pcx
    val my = pcy + pry * 0.78f
    val stroke = Stroke(width = w * 0.0065f, cap = StrokeCap.Round)
    val ms = f.mouthScale
    when {
        pose.mouthOpen > 0.12f -> {
            drawOval(Color(0xFF8A4A52), Offset(mx - prx * 0.085f, my - pry * 0.03f), Size(prx * 0.17f, pry * (0.10f + 0.08f * pose.mouthOpen)))
            drawOval(Color(0xFFFF9E9E), Offset(mx - prx * 0.05f, my + pry * 0.02f), Size(prx * 0.10f, pry * 0.04f))
        }
        else -> when (f.mouth) {
            com.petlife.app.ui.pet.character.MouthStyle.SMILE ->
                drawArc(Color(0xFF8A4A52), 20f, 140f, false, Offset(mx - prx * 0.12f * ms, my - pry * 0.05f), Size(prx * 0.24f * ms, pry * 0.14f), style = stroke)
            com.petlife.app.ui.pet.character.MouthStyle.OPEN_SMILE -> {
                drawArc(Color(0xFF8A4A52), 5f, 170f, true, Offset(mx - prx * 0.14f * ms, my - pry * 0.07f), Size(prx * 0.28f * ms, pry * 0.20f))
                drawArc(Color(0xFFFF9E9E), 15f, 150f, true, Offset(mx - prx * 0.09f * ms, my + pry * 0.005f), Size(prx * 0.18f * ms, pry * 0.10f))
            }
            com.petlife.app.ui.pet.character.MouthStyle.OPEN_SHOUT ->
                drawOval(Color(0xFF8A4A52), Offset(mx - prx * 0.09f * ms, my - pry * 0.06f), Size(prx * 0.18f * ms, pry * 0.14f))
            com.petlife.app.ui.pet.character.MouthStyle.POUT -> {
                drawArc(Color(0xFF8A4A52), 200f, 140f, false, Offset(mx - prx * 0.08f * ms, my + pry * 0.02f), Size(prx * 0.16f * ms, pry * 0.10f), style = stroke)
            }
            com.petlife.app.ui.pet.character.MouthStyle.FLAT ->
                drawLine(Color(0xFF8A4A52), Offset(mx - prx * 0.07f, my), Offset(mx + prx * 0.07f, my), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
            com.petlife.app.ui.pet.character.MouthStyle.WAVE ->
                drawArc(Color(0xFF8A4A52), 200f, 160f, false, Offset(mx - prx * 0.09f * ms, my + pry * 0.03f), Size(prx * 0.18f * ms, prx * 0.10f), style = stroke)
            com.petlife.app.ui.pet.character.MouthStyle.O_SMALL ->
                drawOval(Color(0xFF8A4A52), Offset(mx - prx * 0.045f * ms, my - pry * 0.03f), Size(prx * 0.09f * ms, pry * 0.07f))
            com.petlife.app.ui.pet.character.MouthStyle.SMIRK ->
                drawArc(Color(0xFF8A4A52), 40f, 130f, false, Offset(mx - prx * 0.02f * ms, my - pry * 0.05f), Size(prx * 0.16f * ms, prx * 0.12f), style = stroke)
            com.petlife.app.ui.pet.character.MouthStyle.WOBBLE -> {
                val y0 = my
                drawLine(Color(0xFF8A4A52), Offset(mx - prx * 0.09f, y0), Offset(mx - prx * 0.03f, y0 - pry * 0.025f), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
                drawLine(Color(0xFF8A4A52), Offset(mx - prx * 0.03f, y0 - pry * 0.025f), Offset(mx + prx * 0.03f, y0), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
                drawLine(Color(0xFF8A4A52), Offset(mx + prx * 0.03f, y0), Offset(mx + prx * 0.09f, y0 - pry * 0.025f), strokeWidth = w * 0.006f, cap = StrokeCap.Round)
            }
        }
    }
}

typealias ImageBitmapLike = androidx.compose.ui.graphics.ImageBitmap
