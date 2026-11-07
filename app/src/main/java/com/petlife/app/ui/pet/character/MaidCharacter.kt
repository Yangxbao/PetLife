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
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 分层立绘绘制引擎（全部程序化矢量，无外部素材）。
 *
 * 图层顺序（后→前）：
 * 进化辉光 → 鲸鱼尾 → 后发主 masses → 双腿 → 裙 → 上身/围裙/领结 → 手臂 →
 * 头部 → 表情(眉/眼/腮/嘴) → 刘海+天使环 → 侧发髻+鳍状侧发 → 呆毛 → 头饰/发饰 → 情绪标记
 */
class MaidPose {
    var jump = 0f             // 像素由外部换算：系数(0..1 of H)
    var bodyTilt = 0f         // 身体倾斜（度）
    var bodyDx = 0f           // 身体水平偏移（unit 倍数）
    var headDx = 0f           // 头水平（unit 倍数）
    var headDy = 0f
    var headTilt = 0f
    var breathe = 0f          // -1..1
    var blink = 0f            // 0 开 → 1 闭
    var gazeX = 0f            // -1..1
    var gazeY = 0f
    var armPose = ArmPose.DOWN
    var mouthOpen = 0f        // 0..1（吃东西/哈欠）
    var eyesClosedStyle = false // 闭眼样式用开心弧
    var hairSway = 1f         // 头发摆幅系数
    var tailWave = 1f
    var legLift = 0f
    var turn = 0f             // 0..1 转身（cos 翻面）
    var lean = 0f             // 蹭手倾斜 -1..1
    var slump = 0f            // 委屈/困 耷拉
    var glowBoost = 0f
    var stretch = 0f          // 伸懒腰 0..1
}

enum class ArmPose { DOWN, AKIMBO, COVER, REACH, UP, CHEER }

/** 动作 → 姿态（t = 动作进度 0..1） */
fun applyMaidAction(pose: MaidPose, action: com.petlife.app.ui.pet.PetAction, t: Float) {
    val s = sin(PI * t).toFloat()
    when (action) {
        com.petlife.app.ui.pet.PetAction.HAPPY_BOUNCE -> {
            pose.jump = -abs(sin(PI * t * 2).toFloat()) * 0.05f
            pose.legLift = abs(sin(PI * t * 2).toFloat()) * 0.5f
            pose.hairSway = 1.5f
        }
        com.petlife.app.ui.pet.PetAction.JUMP -> {
            pose.jump = -sin(PI * t).toFloat() * 0.09f
            pose.legLift = sin(PI * t).toFloat() * 0.7f
        }
        com.petlife.app.ui.pet.PetAction.SPIN, com.petlife.app.ui.pet.PetAction.TURN -> {
            pose.turn = t
            pose.jump = -abs(sin(PI * t * 2).toFloat()) * 0.03f
            pose.hairSway = 1.8f
        }
        com.petlife.app.ui.pet.PetAction.SHY_COVER -> {
            pose.armPose = ArmPose.COVER
            pose.headTilt = s * 8f
            pose.headDx = -0.04f * s
            pose.lean = -0.3f * s
            pose.hairSway = 0.5f
        }
        com.petlife.app.ui.pet.PetAction.PUFF, com.petlife.app.ui.pet.PetAction.PUFF_AKIMBO -> {
            pose.armPose = ArmPose.AKIMBO
            pose.headTilt = -s * 4f
            pose.bodyTilt = sin(PI * t * 3).toFloat() * 1.5f
        }
        com.petlife.app.ui.pet.PetAction.SMUG_AKIMBO -> {
            pose.armPose = ArmPose.AKIMBO
            pose.headTilt = s * 6f
        }
        com.petlife.app.ui.pet.PetAction.UPSET_DOWN -> {
            pose.headDy = 0.05f * s.coerceAtLeast(0.3f)
            pose.headTilt = 6f
            pose.slump = 0.6f * s
            pose.hairSway = 0.3f
        }
        com.petlife.app.ui.pet.PetAction.SPOILED -> {
            pose.headTilt = sin(PI * t * 2).toFloat() * 7f
            pose.headDx = 0.05f
            pose.lean = 0.35f * s
            pose.armPose = ArmPose.DOWN
        }
        com.petlife.app.ui.pet.PetAction.YAWN -> {
            pose.mouthOpen = s
            pose.headDy = -0.015f * s
            pose.headTilt = -s * 5f
        }
        com.petlife.app.ui.pet.PetAction.STRETCH -> {
            pose.stretch = s
            pose.armPose = ArmPose.UP
            pose.mouthOpen = s * 0.5f
        }
        com.petlife.app.ui.pet.PetAction.EAT -> {
            val chew = abs(sin(PI * t * 6).toFloat())
            pose.headDy = 0.06f + chew * 0.012f
            pose.headTilt = 10f
            pose.mouthOpen = chew
            pose.armPose = ArmPose.REACH
        }
        com.petlife.app.ui.pet.PetAction.CELEBRATE, com.petlife.app.ui.pet.PetAction.CHEER -> {
            pose.armPose = ArmPose.CHEER
            pose.jump = -abs(sin(PI * t * 3).toFloat()) * 0.06f
            pose.legLift = abs(sin(PI * t * 3).toFloat()) * 0.4f
            pose.hairSway = 1.8f
        }
        com.petlife.app.ui.pet.PetAction.LEVELUP, com.petlife.app.ui.pet.PetAction.EVOLVE, com.petlife.app.ui.pet.PetAction.GROWTH_POSE -> {
            pose.armPose = ArmPose.UP
            pose.jump = -sin(PI * minOf(t * 1.4f, 1f)).toFloat() * 0.06f
            pose.glowBoost = s
            pose.hairSway = 1.5f
        }
        com.petlife.app.ui.pet.PetAction.WELCOME_BACK -> {
            pose.armPose = ArmPose.CHEER
            pose.headTilt = sin(PI * t * 2).toFloat() * 5f
        }
        com.petlife.app.ui.pet.PetAction.HAPPY_BOUNCE, com.petlife.app.ui.pet.PetAction.HAPPY_SHAKE -> {
            pose.bodyTilt = sin(PI * t * 3).toFloat() * 4f
            pose.headTilt = sin(PI * t * 3 + 0.6f).toFloat() * 5f
            pose.hairSway = 1.7f
            pose.jump = -abs(sin(PI * t * 2).toFloat()) * 0.025f
        }
        com.petlife.app.ui.pet.PetAction.HEAD_TILT -> pose.headTilt = s * 10f
        com.petlife.app.ui.pet.PetAction.LOOK_AROUND -> {
            pose.headDx = sin(PI * t * 2).toFloat() * 0.06f
            pose.gazeX = sin(PI * t * 2).toFloat() * 0.8f
        }
        com.petlife.app.ui.pet.PetAction.HAIR_TUCK -> {
            pose.armPose = ArmPose.COVER
            pose.headTilt = -s * 6f
        }
        com.petlife.app.ui.pet.PetAction.WINK -> pose.headTilt = s * 4f
        com.petlife.app.ui.pet.PetAction.TAIL_WAG -> pose.tailWave = 2.2f * s.coerceAtLeast(0.5f)
        com.petlife.app.ui.pet.PetAction.NOD -> pose.headDy = abs(sin(PI * t * 2).toFloat()) * 0.03f
        com.petlife.app.ui.pet.PetAction.SURPRISED -> pose.headDy = -0.02f * s
        com.petlife.app.ui.pet.PetAction.SLEEP -> {
            pose.headTilt = 10f * t.coerceAtLeast(0.2f)
            pose.headDy = 0.06f * t
            pose.slump = 0.5f * t
        }
        else -> {}
    }
}

fun DrawScope.drawMaidCharacter(
    stage: com.petlife.app.data.model.PetStage,
    growthT: Float,
    expression: Expression,
    pose: MaidPose,
    timeMs: Long,
) {
    val w = size.width
    val h = size.height
    val sp = MaidStageVisuals.forStage(stage, growthT)
    val c = MaidPalette

    val cxBase = w / 2f + pose.bodyDx * w * 0.30f
    val groundY = h * 0.90f
    val unit = w * 0.195f * sp.bodyScale
    val headRx = unit * 1.00f
    val headRy = unit * 0.92f
    val headCY = h * 0.315f + pose.headDy * unit - pose.jump * h + pose.slump * unit * 0.12f
    val cx = cxBase + pose.lean * unit * 0.10f

    val neckY = headCY + headRy * 0.92f
    val waistY = neckY + h * 0.095f
    val hemY = groundY - h * 0.115f + pose.slump * unit * 0.05f
    val shoulderY = neckY + h * 0.018f
    val shoulderDx = unit * 0.62f

    // ---- 进化辉光（最底层） ----
    val glowA = sp.glow + pose.glowBoost * 0.5f
    if (glowA > 0.02f) {
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFFFFE9B0).copy(alpha = 0.35f * glowA), Color.Transparent),
                center = Offset(cx, headCY),
                radius = unit * 2.6f,
            ),
            radius = unit * 2.6f,
            center = Offset(cx, headCY),
        )
    }

    val turnScaleX = if (pose.turn > 0f) {
        val c = kotlin.math.cos(PI * pose.turn).toFloat()
        if (abs(c) < 0.12f) 0.12f * (if (c >= 0f) 1f else -1f) else c
    } else 1f

    // ================= 鲸鱼尾（最后方） =================
    if (sp.tailScale > 0.02f) {
        drawWhaleTail(cx, hemY, unit, sp.tailScale, pose.tailWave, timeMs)
    }

    // ================= 后发主 masses =================
    drawBackHair(cx, headCY, headRx, headRy, unit, sp, pose, w, h, timeMs)

    scale(turnScaleX, 1f, pivot = Offset(cx, headCY)) {
        // ================= 双腿 =================
        drawLegs(cx, hemY, groundY, unit, pose.legLift, timeMs)

        // ================= 裙子 =================
        drawSkirt(cx, waistY, hemY, unit, sp.laceLevel, pose.bodyTilt, timeMs)

        // ================= 上身 + 围裙 =================
        drawBody(cx, neckY, waistY, hemY, unit, sp, pose.breathe)

        // ================= 手臂 =================
        drawArms(cx, shoulderY, shoulderDx, waistY, unit, pose, timeMs)
    }

    // ================= 头部（不参与转身翻面，独立轻微跟随） =================
    val headCX = cx + pose.headDx * unit * turnScaleX
    translate(headCX, headCY) {
        rotate(pose.headTilt) {
            drawHeadFront(headRx, headRy, unit)
            // 表情（在刘海之下画眼睛，刘海之后覆盖额头）
            drawFace(expression, headRx, headRy, unit, pose, timeMs)
            // 刘海 + 天使环
            drawBangs(headRx, headRy, unit, pose, timeMs)
            // 侧发髻 + 鳍状侧发（头两侧，压在刘海之上）
            drawSideHair(headRx, headRy, unit, sp, pose, timeMs)
            // 呆毛
            drawAhoge(headRx, headRy, unit, pose, timeMs)
            // 头饰
            if (sp.headdress > 0.02f) drawHeaddress(headRx, headRy, unit, sp.headdress)
            if (sp.sideBow > 0.02f) drawSideBow(headRx, headRy, unit, sp.sideBow, headCX, headCY, timeMs)
            if (sp.starPin > 0.02f) drawStarPin(headRx, headRy, unit, sp.starPin, timeMs)
        }
    }

    // ---- 情绪标记（怒纹/汗滴/惊讶线，画在最上层） ----
    drawMarks(expression, headCX, headCY, headRx, headRy, unit, pose, timeMs)
}

private fun sign(v: Float): Float = if (v >= 0f) 1f else -1f


/** 防非法尺寸：任何负数/NaN 都会被钳制，杜绝绘制层崩溃 */
private fun sz(w: Float, h: Float): Size = Size(
    kotlin.math.max(0.5f, if (w.isNaN()) 0.5f else w),
    kotlin.math.max(0.5f, if (h.isNaN()) 0.5f else h),
)

// ---------------- 鲸鱼尾 ----------------
private fun DrawScope.drawWhaleTail(cx: Float, hemY: Float, unit: Float, scale: Float, wave: Float, timeMs: Long) {
    val waveOff = sin(timeMs / 420.0 * PI).toFloat() * unit * 0.06f * wave
    val baseX = cx - unit * 0.72f
    val baseY = hemY - unit * 0.10f
    val midX = baseX - unit * 0.42f * scale
    val midY = baseY + unit * 0.16f + waveOff * 0.6f
    val tipX = baseX - unit * 0.72f * scale
    val tipY = baseY - unit * 0.30f * scale + waveOff

    val tail = Path().apply {
        moveTo(baseX + unit * 0.10f, baseY - unit * 0.05f)
        cubicTo(
            baseX - unit * 0.25f, baseY + unit * 0.14f,
            midX, midY - unit * 0.16f,
            tipX + unit * 0.06f, tipY + unit * 0.05f,
        )
        // 尾鳍上叶
        quadraticBezierTo(tipX - unit * 0.18f * scale, tipY - unit * 0.22f, tipX - unit * 0.30f * scale, tipY - unit * 0.10f)
        // 中凹
        quadraticBezierTo(tipX - unit * 0.10f, tipY + unit * 0.02f, tipX - unit * 0.02f, tipY + unit * 0.02f)
        // 尾鳍下叶
        quadraticBezierTo(tipX + unit * 0.10f, tipY + unit * 0.16f, tipX + unit * 0.16f * scale, tipY + unit * 0.22f)
        quadraticBezierTo(midX + unit * 0.10f, midY + unit * 0.16f, baseX + unit * 0.10f, baseY + unit * 0.14f)
        close()
    }
    drawPath(tail, Brush.linearGradient(listOf(Color(MaidPalette.WhaleTop), Color(MaidPalette.WhaleBelly)), start = Offset(baseX, baseY - unit * 0.2f), end = Offset(tipX, tipY)))
    drawPath(tail, Color(MaidPalette.HairLine).copy(alpha = 0.5f), style = Stroke(width = unit * 0.02f))
    // 尾鳍浅色边
    drawLine(
        Color(MaidPalette.WhaleLight).copy(alpha = 0.7f),
        start = Offset(tipX - unit * 0.26f * scale, tipY - unit * 0.08f),
        end = Offset(tipX + unit * 0.10f, tipY + unit * 0.10f),
        strokeWidth = unit * 0.035f, cap = StrokeCap.Round,
    )
}

// ---------------- 后发 ----------------
private fun DrawScope.drawBackHair(
    cx: Float, headCY: Float, headRx: Float, headRy: Float, unit: Float,
    sp: MaidStageParams, pose: MaidPose, w: Float, h: Float, timeMs: Long,
) {
    val sway = sin(timeMs / 900.0 * PI).toFloat() * unit * 0.018f * pose.hairSway
    val topY = headCY - headRy * 1.04f
    val bottomY = headCY + headRy * 0.90f + sp.backHairLen * h * 0.30f
    val halfW = headRx * (1.00f + sp.backHairLen * 0.35f)

    val path = Path().apply {
        moveTo(cx - halfW * 0.55f, topY)
        // 左侧外轮廓（含鳍状外扩）
        cubicTo(
            cx - halfW * 1.25f, topY + headRy * 0.5f,
            cx - halfW * 1.30f + sway, bottomY - h * 0.10f,
            cx - halfW * 1.05f + sway, bottomY,
        )
        // 底部波浪
        val seg = (halfW * 2.1f) / 5f
        var x = cx - halfW * 1.05f + sway
        for (i in 0 until 5) {
            val dip = if (i % 2 == 0) unit * 0.10f else unit * 0.02f
            quadraticBezierTo(x + seg / 2, bottomY + dip, x + seg, bottomY + dip * 0.35f)
            x += seg
        }
        // 右侧上行
        cubicTo(
            cx + halfW * 1.30f + sway, bottomY - h * 0.10f,
            cx + halfW * 1.25f, topY + headRy * 0.5f,
            cx + halfW * 0.55f, topY,
        )
        // 顶部沿头型闭合
        quadraticBezierTo(cx, topY - headRy * 0.25f, cx - halfW * 0.55f, topY)
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Color(MaidPalette.HairTop), Color(MaidPalette.HairMid), Color(MaidPalette.HairTip)),
            startY = topY, endY = bottomY + unit * 0.2f,
        ),
    )
    drawPath(path, Color(MaidPalette.HairLine).copy(alpha = 0.45f), style = Stroke(width = unit * 0.018f))
    // 发丝高光条
    for (dx in listOf(-0.45f, -0.1f, 0.3f)) {
        drawLine(
            Color(MaidPalette.HairTipLight).copy(alpha = 0.28f),
            start = Offset(cx + halfW * dx, headCY + headRy * 0.6f),
            end = Offset(cx + halfW * dx * 1.15f + sway, bottomY - unit * 0.15f),
            strokeWidth = unit * 0.045f, cap = StrokeCap.Round,
        )
    }
}

// ---------------- 双腿 ----------------
private fun DrawScope.drawLegs(cx: Float, hemY: Float, groundY: Float, unit: Float, legLift: Float, timeMs: Long) {
    val legGap = unit * 0.16f
    val legW = unit * 0.14f
    val shoeH = unit * 0.09f
    val phase = timeMs / 200.0
    for ((i, side) in listOf(-1f, 1f).withIndex()) {
        val lift = if (legLift > 0.05f) abs(sin(phase + i * PI).toFloat()) * legLift * unit * 0.07f else 0f
        val legX = cx + side * legGap
        val topY = hemY - unit * 0.04f
        val bottomY = groundY - shoeH - lift
        // 白袜
        drawRoundRect(
            Color(MaidPalette.White),
            topLeft = Offset(legX - legW / 2, topY),
            size = sz(legW, (bottomY - topY).coerceAtLeast(unit * 0.05f)),
            cornerRadius = CornerRadius(legW / 2, legW / 2),
        )
        drawRoundRect(
            Color(MaidPalette.WhiteShade).copy(alpha = 0.5f),
            topLeft = Offset(legX - legW / 2 + legW * 0.45f, topY),
            size = sz(legW * 0.55f, (bottomY - topY).coerceAtLeast(unit * 0.05f)),
            cornerRadius = CornerRadius(legW / 2, legW / 2),
        )
        // 袜口褶边
        drawRoundRect(
            Color(MaidPalette.Lace),
            topLeft = Offset(legX - legW * 0.62f, topY - unit * 0.02f),
            size = sz(legW * 1.24f, unit * 0.06f),
            cornerRadius = CornerRadius(unit * 0.03f, unit * 0.03f),
        )
        // 鞋
        drawRoundRect(
            Color(MaidPalette.DressNavy),
            topLeft = Offset(legX - legW * 0.72f, bottomY - unit * 0.02f),
            size = sz(legW * 1.7f, shoeH),
            cornerRadius = CornerRadius(unit * 0.05f, unit * 0.05f),
        )
        // 鞋带扣
        drawCircle(Color(MaidPalette.Gold), unit * 0.018f, Offset(legX - side * legW * 0.35f, bottomY + unit * 0.005f))
    }
}

// ---------------- 裙子 ----------------
private fun DrawScope.drawSkirt(cx: Float, waistY: Float, hemY: Float, unit: Float, lace: Float, tilt: Float, timeMs: Long) {
    val sway = sin(timeMs.toDouble() * 2.2).toFloat() * unit * 0.02f
    val waistHalf = unit * 0.52f
    val hemHalf = unit * 1.02f + sway
    val skirt = Path().apply {
        moveTo(cx - waistHalf, waistY)
        cubicTo(
            cx - waistHalf * 1.1f, waistY + (hemY - waistY) * 0.4f,
            cx - hemHalf, hemY - unit * 0.18f,
            cx - hemHalf, hemY,
        )
        // 裙摆褶（4 个圆弧）
        val seg = hemHalf * 2f / 4f
        var x = cx - hemHalf
        for (i in 0 until 4) {
            quadraticBezierTo(x + seg / 2, hemY + unit * (0.06f + 0.02f * ((i + 1) % 2)), x + seg, hemY - unit * 0.015f)
            x += seg
        }
        cubicTo(
            cx + hemHalf, hemY - unit * 0.18f,
            cx + waistHalf * 1.1f, waistY + (hemY - waistY) * 0.4f,
            cx + waistHalf, waistY,
        )
        close()
    }
    rotate(tilt, pivot = Offset(cx, waistY)) {
        drawPath(skirt, Brush.verticalGradient(listOf(Color(MaidPalette.DressNavyLight), Color(MaidPalette.DressNavy), Color(MaidPalette.DressNavyDark)), startY = waistY, endY = hemY + unit * 0.1f))
        drawPath(skirt, Color(MaidPalette.HairLine).copy(alpha = 0.4f), style = Stroke(width = unit * 0.016f))
        // 白色蕾丝裙边
        if (lace > 0.05f) {
            val laceY = hemY - unit * 0.02f
            val half = hemHalf * (0.96f + 0.02f * lace)
            drawRoundRect(
                Color(MaidPalette.White).copy(alpha = 0.95f),
                topLeft = Offset(cx - half, laceY),
                size = sz(half * 2, unit * 0.075f + unit * 0.03f * lace),
                cornerRadius = CornerRadius(unit * 0.04f, unit * 0.04f),
            )
            // 扇贝
            val n = 6
            val seg = half * 2 / n
            for (i in 0 until n) {
                drawCircle(
                    Color(MaidPalette.White).copy(alpha = 0.95f),
                    unit * (0.035f + 0.012f * lace),
                    Offset(cx - half + seg * (i + 0.5f), laceY + unit * 0.075f + unit * 0.02f * lace),
                )
            }
        }
    }
}

// ---------------- 上身 + 围裙 ----------------
private fun DrawScope.drawBody(cx: Float, neckY: Float, waistY: Float, hemY: Float, unit: Float, sp: MaidStageParams, breathe: Float) {
    val bodyW = unit * 0.72f
    val breatheOff = breathe * unit * 0.012f
    // 上身（深蓝）
    drawRoundRect(
        Brush.verticalGradient(listOf(Color(MaidPalette.DressNavyLight), Color(MaidPalette.DressNavy))),
        topLeft = Offset(cx - bodyW / 2, neckY - unit * 0.02f + breatheOff),
        size = sz(bodyW, (waistY - neckY) + unit * 0.06f),
        cornerRadius = CornerRadius(bodyW * 0.35f, bodyW * 0.35f),
    )
    // 白领
    drawOval(
        Color(MaidPalette.White),
        topLeft = Offset(cx - bodyW * 0.42f, neckY - unit * 0.06f + breatheOff),
        size = sz(bodyW * 0.84f, unit * 0.16f),
    )
    // 宝石胸针
    if (sp.brooch > 0.05f) {
        val by = neckY + unit * 0.10f
        drawCircle(Color(MaidPalette.Gold), unit * 0.055f * sp.brooch, Offset(cx, by))
        drawCircle(Color(MaidPalette.Jewel), unit * 0.038f * sp.brooch, Offset(cx, by))
        drawCircle(Color.White.copy(alpha = 0.8f), unit * 0.014f * sp.brooch, Offset(cx - unit * 0.012f, by - unit * 0.012f))
    }
    // 金色扣子
    for (i in 0..1) {
        drawCircle(Color(MaidPalette.Gold), unit * 0.020f, Offset(cx, neckY + unit * (0.20f + i * 0.09f)))
    }
    // 白围裙（胸前+裙前）
    val apronTop = neckY + unit * 0.13f
    val apronBottom = hemY - unit * 0.06f
    drawRoundRect(
        Color(MaidPalette.White),
        topLeft = Offset(cx - bodyW * 0.36f, apronTop),
        size = sz(bodyW * 0.72f, (apronBottom - apronTop)),
        cornerRadius = CornerRadius(bodyW * 0.22f, bodyW * 0.30f),
    )
    drawRoundRect(
        Color(MaidPalette.WhiteShade).copy(alpha = 0.6f),
        topLeft = Offset(cx + bodyW * 0.14f, apronTop),
        size = sz(bodyW * 0.22f, (apronBottom - apronTop)),
        cornerRadius = CornerRadius(bodyW * 0.18f, bodyW * 0.26f),
    )
    // 肩带
    drawLine(Color(MaidPalette.White), Offset(cx - bodyW * 0.30f, apronTop + unit * 0.02f), Offset(cx - bodyW * 0.16f, neckY + unit * 0.02f), strokeWidth = unit * 0.045f, cap = StrokeCap.Round)
    drawLine(Color(MaidPalette.White), Offset(cx + bodyW * 0.30f, apronTop + unit * 0.02f), Offset(cx + bodyW * 0.16f, neckY + unit * 0.02f), strokeWidth = unit * 0.045f, cap = StrokeCap.Round)
    // 腰部蝴蝶结（左右）
    for (side in listOf(-1f, 1f)) {
        val bx = cx + side * bodyW * 0.42f
        val by = waistY - unit * 0.02f
        drawCircle(Color(MaidPalette.BowBlue), unit * 0.035f, Offset(bx, by))
        drawCircle(Color(MaidPalette.DressNavyDark), unit * 0.016f, Offset(bx, by))
    }
    // 围裙鲸鱼刺绣（进化期）
    if (sp.whaleEmbroidery > 0.05f) {
        val ex = cx
        val ey = (apronTop + apronBottom) / 2 - unit * 0.03f
        val es = unit * 0.09f * sp.whaleEmbroidery
        val whale = Path().apply {
            moveTo(ex - es * 1.2f, ey)
            cubicTo(ex - es * 0.7f, ey - es * 0.9f, ex + es * 0.7f, ey - es * 0.9f, ex + es * 1.1f, ey - es * 0.1f)
            quadraticBezierTo(ex + es * 1.35f, ey - es * 0.35f, ex + es * 1.45f, ey - es * 0.05f)
            quadraticBezierTo(ex + es * 0.7f, ey + es * 0.75f, ex - es * 1.2f, ey)
            close()
        }
        drawPath(whale, Color(MaidPalette.WhaleLight).copy(alpha = 0.9f))
        drawCircle(Color(MaidPalette.WhaleTop), es * 0.06f, Offset(ex + es * 0.75f, ey - es * 0.25f))
        // 水花
        drawCircle(Color(MaidPalette.WhaleLight).copy(alpha = 0.6f), es * 0.08f, Offset(ex - es * 1.5f, ey - es * 0.3f))
    }
}

// ---------------- 手臂 ----------------
private fun DrawScope.drawArms(cx: Float, shoulderY: Float, shoulderDx: Float, waistY: Float, unit: Float, pose: MaidPose, timeMs: Long) {
    val swing = sin(timeMs / 800.0 * PI).toFloat() * unit * 0.012f * pose.hairSway
    for (side in listOf(-1f, 1f)) {
        val sx = cx + side * shoulderDx
        val sy = shoulderY + unit * 0.01f
        when (pose.armPose) {
            ArmPose.AKIMBO -> {
                // 肩 → 肘（外） → 手（腰）
                val elbowX = sx + side * unit * 0.30f
                val elbowY = sy + unit * 0.16f
                val handX = cx + side * unit * 0.40f
                val handY = waistY - unit * 0.04f
                drawLimb(sx, sy, elbowX, elbowY, unit)
                drawLimb(elbowX, elbowY, handX, handY, unit)
                drawCircle(Color(MaidPalette.Skin), unit * 0.055f, Offset(handX, handY))
            }
            ArmPose.COVER -> {
                val handX = cx + side * unit * 0.16f
                val handY = shoulderY - unit * 0.32f
                drawLimb(sx, sy, handX - side * unit * 0.05f, handY + unit * 0.10f, unit)
                drawCircle(Color(MaidPalette.Skin), unit * 0.06f, Offset(handX, handY))
            }
            ArmPose.REACH -> {
                if (side < 0) {
                    val handX = cx - unit * 0.42f
                    val handY = shoulderY + unit * 0.34f
                    drawLimb(sx, sy, handX, handY, unit)
                    drawCircle(Color(MaidPalette.Skin), unit * 0.06f, Offset(handX, handY))
                } else {
                    drawDown(sx, sy, side, unit, swing)
                }
            }
            ArmPose.UP, ArmPose.CHEER -> {
                val handX = sx + side * unit * 0.16f
                val handY = shoulderY - unit * (0.42f + if (pose.armPose == ArmPose.CHEER) abs(sin(timeMs / 160.0 * PI).toFloat()) * 0.05f else 0f)
                drawLimb(sx, sy, handX, handY, unit)
                drawCircle(Color(MaidPalette.Skin), unit * 0.058f, Offset(handX, handY))
            }
            ArmPose.DOWN -> drawDown(sx, sy + side * swing * 0.3f, side, unit, swing)
        }
    }
}

private fun DrawScope.drawDown(sx: Float, sy: Float, side: Float, unit: Float, swing: Float) {
    val handX = sx + side * unit * 0.06f
    val handY = sy + unit * 0.40f + swing
    drawLimb(sx, sy, handX, handY, unit)
    drawCircle(Color(MaidPalette.Skin), unit * 0.055f, Offset(handX, handY))
}

private fun DrawScope.drawLimb(x1: Float, y1: Float, x2: Float, y2: Float, unit: Float) {
    // 藏蓝袖 + 白袖口
    drawLine(Color(MaidPalette.DressNavy), Offset(x1, y1), Offset(x2, y2), strokeWidth = unit * 0.115f, cap = StrokeCap.Round)
    val cuffX = x1 + (x2 - x1) * 0.72f
    val cuffY = y1 + (y2 - y1) * 0.72f
    drawLine(Color(MaidPalette.White), Offset(cuffX, cuffY), Offset(x1 + (x2 - x1) * 0.86f, y1 + (y2 - y1) * 0.86f), strokeWidth = unit * 0.12f, cap = StrokeCap.Round)
    drawCircle(Color(MaidPalette.Gold), unit * 0.022f, Offset(cuffX, cuffY))
}

// ---------------- 头 ----------------
private fun DrawScope.drawHeadFront(headRx: Float, headRy: Float, unit: Float) {
    // 耳朵（被头发遮，仅轮廓光影略过）
    // 脸
    drawOval(
        Brush.verticalGradient(listOf(Color(MaidPalette.Skin), Color(MaidPalette.SkinShade).copy(alpha = 0.55f)), startY = -headRy, endY = headRy * 1.2f),
        topLeft = Offset(-headRx, -headRy),
        size = sz(headRx * 2f, headRy * 2f),
    )
    drawOval(
        Color(MaidPalette.HairLine).copy(alpha = 0.25f),
        topLeft = Offset(-headRx, -headRy),
        size = sz(headRx * 2f, headRy * 2f),
        style = Stroke(width = unit * 0.012f),
    )
}

// ---------------- 表情 ----------------
private fun DrawScope.drawFace(
    expression: Expression,
    headRx: Float,
    headRy: Float,
    unit: Float,
    pose: MaidPose,
    timeMs: Long,
) {
    val f = Faces.of(expression)
    val eyeY = headRy * 0.06f
    val eyeDx = headRx * 0.34f
    val eyeW = headRx * 0.34f
    val eyeH = headRy * 0.46f
    val gazeX = (f.gazeX + pose.gazeX).coerceIn(-1f, 1f)
    val gazeY = (f.gazeY + pose.gazeY).coerceIn(-1f, 1f)
    val gazeOff = Offset(gazeX * eyeW * 0.16f, gazeY * eyeH * 0.14f)
    val bob = sin(timeMs / 620.0 * PI).toFloat() * headRy * 0.008f

    // ---- 眉毛 ----
    val browY = eyeY - eyeH * 0.78f - f.browLift * headRy * 0.08f
    for (side in listOf(-1f, 1f)) {
        val bx = side * eyeDx
        rotate(f.browAngle * side, pivot = Offset(bx, browY)) {
            drawLine(
                Color(MaidPalette.Brow).copy(alpha = 0.9f),
                start = Offset(bx - eyeW * 0.42f, browY + eyeH * 0.06f),
                end = Offset(bx + eyeW * 0.42f, browY - eyeH * 0.02f),
                strokeWidth = unit * 0.022f, cap = StrokeCap.Round,
            )
        }
    }

    // ---- 眼睛 ----
    for (side in listOf(-1f, 1f)) {
        val ex = side * eyeDx
        val openness = (if (side < 0) f.eyeOpenL else f.eyeOpenR) * (1f - pose.blink)
        translate(ex, eyeY + bob) {
            if (openness < 0.14f) {
                // 闭眼：HAPPY/SMUG 用 ∩ 弧，其余平弧
                val happyClosed = expression == Expression.HAPPY || expression == Expression.SMUG
                drawArc(
                    Color(MaidPalette.Lash),
                    startAngle = if (happyClosed) 180f else 0f,
                    sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(-eyeW * 0.55f, -eyeH * (if (happyClosed) 0.05f else 0.25f)),
                    size = sz(eyeW * 1.1f, eyeH * 0.6f),
                    style = Stroke(width = unit * 0.028f, cap = StrokeCap.Round),
                )
            } else {
                val eh = eyeH * openness
                // 眼白
                drawOval(Color(MaidPalette.EyeWhite), Offset(-eyeW / 2, -eh / 2), sz(eyeW, eh))
                // 虹膜（上深下亮渐变）
                val irisW = eyeW * 0.78f * f.irisScale
                val irisH = eh * 0.96f * f.irisScale
                drawOval(
                    Brush.verticalGradient(listOf(Color(MaidPalette.EyeTop), Color(MaidPalette.EyeBottom)), startY = -eh / 2, endY = eh / 2),
                    topLeft = Offset(gazeOff.x - irisW / 2, gazeOff.y - irisH / 2),
                    size = sz(irisW, irisH),
                )
                // 瞳孔
                drawOval(
                    Color(MaidPalette.EyeOutline).copy(alpha = 0.85f),
                    topLeft = Offset(gazeOff.x - irisW * 0.16f, gazeOff.y - irisH * 0.22f),
                    size = sz(irisW * 0.32f, irisH * 0.44f),
                )
                // 上睫毛线（粗）
                drawArc(
                    Color(MaidPalette.Lash),
                    startAngle = 185f, sweepAngle = 160f, useCenter = false,
                    topLeft = Offset(-eyeW * 0.62f, -eh * 0.66f),
                    size = sz(eyeW * 1.24f, eh * 1.05f),
                    style = Stroke(width = unit * 0.035f, cap = StrokeCap.Round),
                )
                // 外眼角睫毛
                drawLine(
                    Color(MaidPalette.Lash),
                    start = Offset(side * eyeW * 0.60f, -eh * 0.38f),
                    end = Offset(side * eyeW * 0.74f, -eh * 0.55f),
                    strokeWidth = unit * 0.024f, cap = StrokeCap.Round,
                )
                // 高光
                drawCircle(Color.White.copy(alpha = 0.95f), eyeW * 0.14f, Offset(gazeOff.x - irisW * 0.22f, gazeOff.y - irisH * 0.22f))
                drawCircle(Color.White.copy(alpha = 0.65f), eyeW * 0.08f, Offset(gazeOff.x + irisW * 0.20f, gazeOff.y + irisH * 0.18f))
                if (f.sparkleInEye) {
                    val sp2 = 0.5f + 0.5f * sin(timeMs / 240.0 * PI).toFloat()
                    drawCircle(Color.White.copy(alpha = 0.9f * sp2), eyeW * 0.07f, Offset(gazeOff.x + irisW * 0.1f, gazeOff.y - irisH * 0.05f))
                }
                // 委屈泪水积聚
                if (f.tears) {
                    drawOval(
                        Color(MaidPalette.Tear).copy(alpha = 0.55f),
                        topLeft = Offset(-eyeW * 0.5f, eh * 0.30f),
                        size = sz(eyeW, eh * 0.22f),
                    )
                    val dropT = sin(timeMs / 700.0 * PI).toFloat() * 0.5f + 0.5f
                    drawCircle(Color(MaidPalette.Tear).copy(alpha = 0.8f), eyeW * 0.10f, Offset(side * eyeW * 0.1f, eh * 0.62f + dropT * eyeH * 0.5f))
                }
            }
        }
    }

    // ---- 腮红 ----
    val blushA = (f.blush).coerceIn(0f, 1f)
    if (blushA > 0.03f) {
        for (side in listOf(-1f, 1f)) {
            val bx = side * headRx * 0.62f
            val by = eyeY + headRy * 0.30f
            drawOval(
                Brush.radialGradient(listOf(Color(MaidPalette.Blush).copy(alpha = 0.75f * blushA), Color.Transparent)),
                topLeft = Offset(bx - headRx * 0.15f, by - headRy * 0.08f),
                size = sz(headRx * 0.30f, headRy * 0.16f),
            )
            if (f.blushLines) {
                for (i in 0..2) {
                    drawLine(
                        Color(MaidPalette.BlushShy).copy(alpha = 0.8f * blushA),
                        start = Offset(bx - headRx * 0.10f + i * headRx * 0.075f, by - headRy * 0.05f),
                        end = Offset(bx - headRx * 0.135f + i * headRx * 0.075f, by + headRy * 0.05f),
                        strokeWidth = unit * 0.014f, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }

    // ---- 嘴 ----
    val mouthY = eyeY + headRy * 0.44f
    val ms = f.mouthScale
    val stroke = Stroke(width = unit * 0.022f, cap = StrokeCap.Round)
    val open = pose.mouthOpen
    when {
        open > 0.12f -> {
            drawOval(
                Color(MaidPalette.MouthInner),
                Offset(-headRx * 0.085f * ms, mouthY - headRy * 0.05f * open),
                sz(headRx * 0.17f * ms, headRy * (0.09f + 0.08f * open)),
            )
            drawOval(
                Color(MaidPalette.Tongue),
                Offset(-headRx * 0.05f * ms, mouthY + headRy * 0.015f),
                sz(headRx * 0.10f * ms, headRy * 0.05f),
            )
        }
        else -> when (f.mouth) {
            MouthStyle.SMILE -> drawArc(Color(MaidPalette.MouthInner), 20f, 140f, false, Offset(-headRx * 0.11f * ms, mouthY - headRy * 0.05f), sz(headRx * 0.22f * ms, headRy * 0.14f), style = stroke)
            MouthStyle.OPEN_SMILE -> {
                drawArc(Color(MaidPalette.MouthInner), 5f, 170f, true, Offset(-headRx * 0.14f * ms, mouthY - headRy * 0.07f), sz(headRx * 0.28f * ms, headRy * 0.20f))
                drawArc(Color(MaidPalette.Tongue), 15f, 150f, true, Offset(-headRx * 0.09f * ms, mouthY + headRy * 0.01f), sz(headRx * 0.18f * ms, headRy * 0.10f))
            }
            MouthStyle.OPEN_SHOUT -> drawOval(Color(MaidPalette.MouthInner), Offset(-headRx * 0.09f * ms, mouthY - headRy * 0.06f), sz(headRx * 0.18f * ms, headRy * 0.14f))
            MouthStyle.POUT -> {
                drawArc(Color(MaidPalette.MouthInner), 200f, 140f, false, Offset(-headRx * 0.08f * ms, mouthY + headRy * 0.02f), sz(headRx * 0.16f * ms, headRy * 0.10f), style = stroke)
                drawCircle(Color(MaidPalette.BlushShy).copy(alpha = 0.4f), unit * 0.012f, Offset(0f, mouthY - headRy * 0.01f))
            }
            MouthStyle.FLAT -> drawLine(Color(MaidPalette.MouthInner), Offset(-headRx * 0.07f, mouthY), Offset(headRx * 0.07f, mouthY), strokeWidth = unit * 0.02f, cap = StrokeCap.Round)
            MouthStyle.WAVE -> drawArc(Color(MaidPalette.MouthInner), 200f, 160f, false, Offset(-headRx * 0.09f * ms, mouthY + headRy * 0.03f), sz(headRx * 0.18f * ms, headRy * 0.10f), style = stroke)
            MouthStyle.O_SMALL -> drawOval(Color(MaidPalette.MouthInner), Offset(-headRx * 0.045f * ms, mouthY - headRy * 0.03f), sz(headRx * 0.09f * ms, headRy * 0.07f))
            MouthStyle.SMIRK -> drawArc(Color(MaidPalette.MouthInner), 40f, 130f, false, Offset(-headRx * 0.02f * ms, mouthY - headRy * 0.05f), sz(headRx * 0.16f * ms, headRy * 0.12f), style = stroke)
            MouthStyle.WOBBLE -> {
                val y0 = mouthY
                drawLine(Color(MaidPalette.MouthInner), Offset(-headRx * 0.09f, y0), Offset(-headRx * 0.03f, y0 - headRy * 0.025f), strokeWidth = unit * 0.018f, cap = StrokeCap.Round)
                drawLine(Color(MaidPalette.MouthInner), Offset(-headRx * 0.03f, y0 - headRy * 0.025f), Offset(headRx * 0.03f, y0), strokeWidth = unit * 0.018f, cap = StrokeCap.Round)
                drawLine(Color(MaidPalette.MouthInner), Offset(headRx * 0.03f, y0), Offset(headRx * 0.09f, y0 - headRy * 0.025f), strokeWidth = unit * 0.018f, cap = StrokeCap.Round)
            }
        }
    }
}

// ---------------- 刘海 + 天使环 ----------------
private fun DrawScope.drawBangs(headRx: Float, headRy: Float, unit: Float, pose: MaidPose, timeMs: Long) {
    val sway = sin(timeMs / 1000.0 * PI).toFloat() * unit * 0.008f * pose.hairSway
    val topY = -headRy * 1.02f
    val path = Path().apply {
        moveTo(-headRx * 1.00f, topY + headRy * 0.55f)
        // 左侧发际下滑
        quadraticBezierTo(-headRx * 1.02f + sway, -headRy * 0.1f, -headRx * 0.80f, headRy * 0.28f)
        // 五个 M 形尖
        val tips = listOf(
            -headRx * 0.80f to headRy * 0.28f,
            -headRx * 0.50f to headRy * 0.02f,
            -headRx * 0.22f to headRy * 0.34f,
            headRx * 0.08f to headRy * 0.05f,
            headRx * 0.38f to headRy * 0.30f,
            headRx * 0.66f to headRy * 0.02f,
            headRx * 1.00f to headRy * 0.50f,
        )
        for (i in 0 until tips.size - 1) {
            val (x1, y1) = tips[i]
            val (x2, y2) = tips[i + 1]
            quadraticBezierTo((x1 + x2) / 2 + sway * 0.4f, minOf(y1, y2) - headRy * 0.16f, x2, y2)
        }
        // 顶部沿头盖骨
        quadraticBezierTo(0f, topY - headRy * 0.18f, -headRx * 1.00f, topY + headRy * 0.55f)
        close()
    }
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(Color(MaidPalette.HairTop), Color(MaidPalette.HairMid), Color(MaidPalette.HairTip).copy(alpha = 0.9f)),
            startY = topY, endY = headRy * 0.4f,
        ),
    )
    drawPath(path, Color(MaidPalette.HairLine).copy(alpha = 0.5f), style = Stroke(width = unit * 0.014f))
    // 天使环高光
    drawArc(
        Color(MaidPalette.HairTipLight).copy(alpha = 0.55f),
        startAngle = 200f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(-headRx * 0.72f, topY + headRy * 0.02f),
        size = sz(headRx * 1.44f, headRy * 0.72f),
        style = Stroke(width = unit * 0.05f, cap = StrokeCap.Round),
    )
}

// ---------------- 侧发髻 + 鳍状侧发 ----------------
private fun DrawScope.drawSideHair(headRx: Float, headRy: Float, unit: Float, sp: MaidStageParams, pose: MaidPose, timeMs: Long) {
    val sway = sin(timeMs / 760.0 * PI).toFloat() * unit * 0.022f * pose.hairSway
    for (side in listOf(-1f, 1f)) {
        val sx = side * headRx * 0.98f
        val len = headRy * (0.45f + sp.sideHairLen * 0.95f)
        // 鳍状侧发（眼侧外扩尖）
        if (sp.finScale > 0.05f) {
            val fin = Path().apply {
                moveTo(sx - side * headRx * 0.05f, -headRy * 0.15f)
                quadraticBezierTo(
                    sx + side * headRx * (0.42f + 0.25f * sp.finScale), -headRy * (0.05f - 0.2f * sp.finScale),
                    sx + side * headRx * (0.30f + 0.2f * sp.finScale) + sway * side, headRy * (0.28f + 0.15f * sp.finScale),
                )
                quadraticBezierTo(sx + side * headRx * 0.18f, headRy * 0.12f, sx - side * headRx * 0.02f, headRy * 0.05f)
                close()
            }
            drawPath(
                fin,
                Brush.linearGradient(listOf(Color(MaidPalette.HairMid), Color(MaidPalette.HairTip)), start = Offset(sx, -headRy * 0.2f), end = Offset(sx + side * headRx * 0.4f, headRy * 0.3f)),
            )
            drawPath(fin, Color(MaidPalette.HairLine).copy(alpha = 0.4f), style = Stroke(width = unit * 0.012f))
        }
        // 侧长卷发髻（波浪轮廓，垂到 sp.sideHairLen）
        val endYpx = headRy * 0.55f + len
        val strand = Path().apply {
            moveTo(sx - side * headRx * 0.10f, -headRy * 0.25f)
            cubicTo(
                sx + side * headRx * (0.24f), -headRy * 0.1f,
                sx + side * headRx * (0.20f) - sway * side, endYpx - headRy * 0.35f,
                sx + side * headRx * (0.13f) - sway * side, endYpx,
            )
            // 卷曲尖端
            quadraticBezierTo(sx + side * headRx * 0.12f, endYpx + headRy * 0.16f, sx - side * headRx * 0.06f - sway * side * 0.5f, endYpx - headRy * 0.10f)
            cubicTo(
                sx + side * headRx * 0.02f, endYpx - headRy * 0.4f,
                sx - side * headRx * 0.12f, headRy * 0.3f,
                sx - side * headRx * 0.10f, -headRy * 0.25f,
            )
            close()
        }
        drawPath(
            strand,
            Brush.verticalGradient(
                listOf(Color(MaidPalette.HairTop), Color(MaidPalette.HairMid), Color(MaidPalette.HairTip)),
                startY = -headRy * 0.3f, endY = endYpx,
            ),
        )
        drawPath(strand, Color(MaidPalette.HairLine).copy(alpha = 0.45f), style = Stroke(width = unit * 0.013f))
    }
}

// ---------------- 呆毛 ----------------
private fun DrawScope.drawAhoge(headRx: Float, headRy: Float, unit: Float, pose: MaidPose, timeMs: Long) {
    val spring = sin(timeMs / 500.0 * PI).toFloat() * unit * 0.02f * pose.hairSway
    val path = Path().apply {
        moveTo(-unit * 0.02f, -headRy * 1.02f)
        cubicTo(
            unit * 0.02f, -headRy * 1.30f,
            unit * 0.16f + spring, -headRy * 1.34f,
            unit * 0.20f + spring, -headRy * 1.18f,
        )
        quadraticBezierTo(unit * 0.10f + spring, -headRy * 1.24f, unit * 0.03f, -headRy * 1.06f)
        close()
    }
    drawPath(path, Color(MaidPalette.HairMid))
    drawPath(path, Color(MaidPalette.HairLine).copy(alpha = 0.4f), style = Stroke(width = unit * 0.010f))
}

// ---------------- 女仆头饰 ----------------
private fun DrawScope.drawHeaddress(headRx: Float, headRy: Float, unit: Float, scale: Float) {
    val bandY = -headRy * 1.02f
    rotate(-4f, pivot = Offset(0f, bandY)) {
        // 白色缎带底
        drawArc(
            Color(MaidPalette.White),
            startAngle = 195f, sweepAngle = 150f, useCenter = false,
            topLeft = Offset(-headRx * 0.86f, bandY - headRy * 0.30f),
            size = sz(headRx * 1.72f, headRy * 0.72f),
            style = Stroke(width = unit * 0.085f * scale, cap = StrokeCap.Round),
        )
        // 褶边扇贝
        val n = 7
        for (i in 0..n) {
            val ang = (195f + 150f * i / n)
            val rad = (ang * PI / 180.0)
            val px = (headRx * 0.86f * kotlin.math.cos(rad).toFloat()).let { -it } // 镜像 x
            val py = bandY - headRy * 0.30f + headRy * 0.36f * kotlin.math.sin(rad).toFloat() * 0.5f + unit * 0.045f * scale
            drawCircle(Color(MaidPalette.Lace), unit * 0.042f * scale, Offset(px, py))
        }
    }
}

// ---------------- 侧发蝴蝶结 ----------------
private fun DrawScope.drawSideBow(headRx: Float, headRy: Float, unit: Float, scale: Float, headCX: Float, headCY: Float, timeMs: Long) {
    val bob = sin(timeMs / 640.0 * PI).toFloat() * unit * 0.006f
    translate(headRx * 0.78f, -headRy * 0.62f + bob) {
        rotate(18f) {
            val s = unit * 0.085f * scale
            for (side in listOf(-1f, 1f)) {
                val wing = Path().apply {
                    moveTo(0f, 0f)
                    quadraticBezierTo(side * s * 1.5f, -s * 0.9f, side * s * 1.7f, 0f)
                    quadraticBezierTo(side * s * 1.5f, s * 0.9f, 0f, 0f)
                    close()
                }
                drawPath(wing, Color(MaidPalette.BowBlue))
                drawPath(wing, Color(MaidPalette.HairLine).copy(alpha = 0.35f), style = Stroke(width = unit * 0.008f))
            }
            drawCircle(Color(MaidPalette.BowBlue), s * 0.34f, Offset(0f, 0f))
        }
    }
}

// ---------------- 星星发饰（进化期） ----------------
private fun DrawScope.drawStarPin(headRx: Float, headRy: Float, unit: Float, scale: Float, timeMs: Long) {
    val tw = 0.7f + 0.3f * sin(timeMs / 300.0 * PI).toFloat()
    translate(-headRx * 0.72f, -headRy * 0.78f) {
        drawStar(Color(0xFFFFE08A).copy(alpha = 0.95f * tw), unit * 0.075f * scale)
    }
    translate(headRx * 0.45f, -headRy * 1.02f) {
        drawStar(Color(0xFFFFF3C9).copy(alpha = 0.9f * tw), unit * 0.05f * scale)
    }
}

private fun DrawScope.drawStar(color: Color, r: Float) {
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

// ---------------- 情绪标记 ----------------
private fun DrawScope.drawMarks(
    expression: Expression,
    headCX: Float,
    headCY: Float,
    headRx: Float,
    headRy: Float,
    unit: Float,
    pose: MaidPose,
    timeMs: Long,
) {
    val f = Faces.of(expression)
    if (f.angerMark) {
        val mx = headCX + headRx * 0.72f
        val my = headCY - headRy * 0.95f
        val s = unit * 0.035f
        drawLine(Color(0xFFE57373), Offset(mx - s, my), Offset(mx + s, my + s * 2f), strokeWidth = unit * 0.022f, cap = StrokeCap.Round)
        drawLine(Color(0xFFE57373), Offset(mx + s, my), Offset(mx - s, my + s * 2f), strokeWidth = unit * 0.022f, cap = StrokeCap.Round)
    }
    if (f.sweat) {
        val bob = sin(timeMs / 500.0 * PI).toFloat() * 0.5f + 0.5f
        drawCircle(Color(MaidPalette.Sweat).copy(alpha = 0.9f), unit * 0.045f, Offset(headCX + headRx * 0.95f, headCY - headRy * (0.5f + bob * 0.15f)))
    }
    if (f.shockLines) {
        for (i in 0..2) {
            val ang = (-60 + i * 30) * PI / 180.0
            val r1 = headRy * 1.12f
            val r2 = headRy * 1.30f
            drawLine(
                Color(0xFF6B7A99).copy(alpha = 0.7f),
                start = Offset(headCX + kotlin.math.cos(ang).toFloat() * r1, headCY + kotlin.math.sin(ang).toFloat() * r1 - headRy * 0.3f),
                end = Offset(headCX + kotlin.math.cos(ang).toFloat() * r2, headCY + kotlin.math.sin(ang).toFloat() * r2 - headRy * 0.3f),
                strokeWidth = unit * 0.020f, cap = StrokeCap.Round,
            )
        }
    }
}
