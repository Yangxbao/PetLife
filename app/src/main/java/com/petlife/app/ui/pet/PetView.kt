package com.petlife.app.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import com.petlife.app.data.model.PetMoodState
import com.petlife.app.data.model.PetStage
import com.petlife.app.ui.pet.character.Expression
import com.petlife.app.ui.pet.character.Faces
import com.petlife.app.ui.pet.character.MaidPose
import com.petlife.app.ui.pet.character.MaidStageVisuals
import com.petlife.app.ui.pet.character.applyMaidAction
import com.petlife.app.ui.pet.character.drawMaidCharacter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * 宠物视图 v4：分层立绘 + 动作状态机 + 待机微动作 + 视线追踪 + 粒子。
 * 角色本体绘制在 character/MaidCharacter.kt（纯程序化矢量，无位图资源）。
 */
data class PetVisualConfig(
    val stage: PetStage,
    val mood: PetMoodState,
    val growthT: Float,
    val annoyed: Boolean = false,
    val name: String = "皮可",
)

private data class Particle(
    val startX: Float, val startY: Float, val drift: Float,
    val glyph: String, val tint: Color, val delayMs: Long, val sizeScale: Float,
)

@Composable
fun PetView(
    config: PetVisualConfig,
    actor: PetActor,
    modifier: Modifier = Modifier,
    onTap: ((Offset) -> Unit)? = null,
    enableIdleMicro: Boolean = true,
) {
    val breath = rememberInfiniteTransition(label = "breath")
    val breathValue by breath.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(1700), RepeatMode.Reverse), label = "breath",
    )

    // 眨眼
    val blink = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(Random.nextLong(2200, 5000))
            blink.animateTo(1f, tween(80, easing = LinearEasing))
            blink.animateTo(0f, tween(110, easing = LinearEasing))
        }
    }

    // 视线追踪：点击后看向点击处，1.4s 后回到中心
    val gazeX = remember { Animatable(0f) }
    val gazeY = remember { Animatable(0f) }
    // ★ 必须用带 MonotonicFrameClock 的组合作用域启动 animateTo，
    //   Dispatchers.Main 裸作用域会导致 IllegalStateException（真机闪退根因）
    val gazeScope = rememberCoroutineScope()

    // 待机微动作调度：4~9 秒随机一个小动作（不与对话耦合）
    LaunchedEffect(enableIdleMicro) {
        if (!enableIdleMicro) return@LaunchedEffect
        while (true) {
            kotlinx.coroutines.delay(Random.nextLong(4000, 9000))
            if (!actor.busy) {
                val micro = listOf(
                    PetAction.WINK, PetAction.HEAD_TILT, PetAction.LOOK_AROUND,
                    PetAction.TAIL_WAG, PetAction.HAIR_TUCK, PetAction.NOD,
                ).random()
                actor.play(micro)
            }
        }
    }

    // 粒子
    var particles by remember { mutableStateOf(emptyList<Particle>()) }
    val particleProgress = remember { Animatable(1f) }
    LaunchedEffect(actor.action) {
        runCatching {
            val a = actor.action
            if (a == PetAction.IDLE) return@LaunchedEffect
            particles = buildParticles(a)
            if (particles.isNotEmpty()) {
                particleProgress.snapTo(0f)
                particleProgress.animateTo(1f, tween(1500, easing = LinearEasing))
            }
        }.onFailure { android.util.Log.e("PetLifeCrash", "粒子动画异常（已忽略）", it) }
    }

    val tapMod = if (onTap != null) {
        Modifier.pointerInput(Unit) {
            detectTapGestures { offset ->
                // 视线看向点击位置（相对画布中心归一化）；整个点击链路兜底，不允许异常杀死进程
                runCatching {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    gazeScope.launch {
                        runCatching {
                            val nx = ((offset.x - w / 2f) / (w * 0.5f)).coerceIn(-1f, 1f)
                            val ny = ((offset.y - h * 0.35f) / (h * 0.5f)).coerceIn(-1f, 1f)
                            launch {
                                gazeX.animateTo(nx.coerceIn(-0.9f, 0.9f), tween(160))
                                gazeX.animateTo(0f, tween(1200))
                            }
                            launch {
                                gazeY.animateTo(ny.coerceIn(-0.5f, 0.6f), tween(160))
                                gazeY.animateTo(0f, tween(1200))
                            }
                        }.onFailure { android.util.Log.e("PetLifeCrash", "视线动画异常（已忽略）", it) }
                    }
                    onTap(offset)
                }.onFailure { android.util.Log.e("PetLifeCrash", "点击处理异常（已忽略）", it) }
            }
        }
    } else Modifier

    Box(modifier = modifier.then(tapMod)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 绘制层兜底：任何一帧异常都不允许杀死进程（记录日志并跳过该帧）
            val drew = runCatching {
                drawPetContent(config, actor, breathValue, blink.value, gazeX.value, gazeY.value, particles, particleProgress.value)
            }
            drew.onFailure {
                android.util.Log.e("PetLifeCrash", "宠物绘制异常（已跳过该帧）", it)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPetContent(
    config: PetVisualConfig,
    actor: PetActor,
    breathValue: Float,
    blink: Float,
    gazeX: Float,
    gazeY: Float,
    particles: List<Particle>,
    particleT: Float,
) {
    run {
            val t = actor.progress()
            val pose = MaidPose()
            // 常驻呼吸与轻微体态
            pose.breathe = breathValue
            pose.blink = blink
            pose.gazeX = gazeX
            pose.gazeY = gazeY
            pose.headTilt = sin(System.currentTimeMillis() / 2600.0 * PI).toFloat() * 1.2f // 极轻的头部自然晃动
            if (actor.action != PetAction.IDLE) {
                applyMaidAction(pose, actor.action, t)
            } else {
                // 待机时的随机重心微移
                pose.bodyDx = sin(System.currentTimeMillis() / 3400.0 * PI).toFloat() * 0.012f
                if (config.mood == PetMoodState.TIRED) {
                    pose.slump = 0.25f
                    pose.headTilt += 3f
                }
            }

            drawMaidCharacter(
                stage = config.stage,
                growthT = config.growthT,
                expression = Faces.fromAction(actor.action) ?: Faces.fromMood(config.mood, config.annoyed),
                pose = pose,
                timeMs = System.currentTimeMillis(),
            )

            // 吃东西：嘴边食物缩小
            if (actor.action == PetAction.EAT && t < 0.85f) {
                val foodR = size.width * 0.035f * (1f - t)
                drawCircle(Color(0xFFF6C872), foodR, Offset(size.width * 0.36f, size.height * 0.50f))
                drawCircle(Color(0xFFE8A15C), foodR * 0.55f, Offset(size.width * 0.36f, size.height * 0.505f))
            }

            // 粒子
            val pt = particleT
            if (pt < 1f && particles.isNotEmpty()) {
                for (p in particles) {
                    val local = ((pt * 1500 - p.delayMs) / 1200f).coerceIn(0f, 1f)
                    if (local <= 0f) continue
                    drawGlyph(
                        p.glyph,
                        size.width * (p.startX + p.drift * local),
                        size.height * (p.startY - local * 0.36f),
                        (1f - local).coerceIn(0f, 1f),
                        size.minDimension * 0.05f * p.sizeScale,
                        p.tint,
                    )
                }
            }

            // 困倦 Zzz
            val showZzz = actor.action == PetAction.SLEEP ||
                (actor.action == PetAction.IDLE && config.mood == PetMoodState.TIRED)
            if (showZzz) {
                val bob = sin(System.currentTimeMillis() / 640.0 * PI).toFloat() * 0.5f + 0.5f
                val paint = android.graphics.Paint().apply {
                    textSize = size.width * 0.05f
                    isAntiAlias = true
                    color = android.graphics.Color.argb(150, 138, 116, 98)
                }
                drawContext.canvas.nativeCanvas.drawText("z", size.width * 0.72f, size.height * (0.22f - bob * 0.02f), paint)
                drawContext.canvas.nativeCanvas.drawText("Z", size.width * 0.78f, size.height * (0.16f - bob * 0.025f), paint)
            }
    }
}

private fun buildParticles(action: PetAction): List<Particle> = when (action) {
    PetAction.EAT -> List(5) {
        Particle(0.55f, 0.52f, (Random.nextFloat() - 0.5f) * 0.1f,
            if (Random.nextBoolean()) "•" else "*", Color(0xFFE8A15C), Random.nextLong(0, 300), 0.6f)
    }
    PetAction.CELEBRATE, PetAction.CHEER -> List(9) {
        Particle(0.2f + Random.nextFloat() * 0.6f, 0.35f + Random.nextFloat() * 0.25f, (Random.nextFloat() - 0.5f) * 0.3f,
            listOf("✦", "☆", "♥").random(), listOf(Color(0xFFFFC94D), Color(0xFF63BF96), Color(0xFFFF8FA3)).random(),
            Random.nextLong(0, 400), 1.0f)
    }
    PetAction.LEVELUP, PetAction.EVOLVE, PetAction.GROWTH_POSE -> List(10) {
        Particle(0.2f + Random.nextFloat() * 0.6f, 0.28f + Random.nextFloat() * 0.3f, (Random.nextFloat() - 0.5f) * 0.3f,
            if (Random.nextBoolean()) "✦" else "☆", Color(0xFFFFC94D), Random.nextLong(0, 500), 1.15f)
    }
    PetAction.HAPPY_SHAKE, PetAction.SPOILED, PetAction.HAPPY_BOUNCE -> List(5) {
        Particle(0.42f + Random.nextFloat() * 0.2f, 0.40f + Random.nextFloat() * 0.1f, (Random.nextFloat() - 0.5f) * 0.16f,
            "♥", Color(0xFFFF8FA3), Random.nextLong(0, 260), 0.9f)
    }
    PetAction.SPIN -> List(6) {
        Particle(0.3f + Random.nextFloat() * 0.4f, 0.38f + Random.nextFloat() * 0.15f, (Random.nextFloat() - 0.5f) * 0.3f,
            if (Random.nextBoolean()) "♪" else "✦", Color(0xFF9C8FD8), Random.nextLong(0, 260), 0.9f)
    }
    else -> emptyList()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGlyph(
    glyph: String, x: Float, y: Float, alpha: Float, fontSize: Float, tint: Color,
) {
    val paint = android.graphics.Paint().apply {
        textSize = fontSize
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
        color = android.graphics.Color.argb(
            (alpha.coerceIn(0f, 1f) * 255).toInt(),
            (tint.red * 255).toInt(), (tint.green * 255).toInt(), (tint.blue * 255).toInt(),
        )
    }
    drawContext.canvas.nativeCanvas.drawText(glyph, x, y, paint)
}
