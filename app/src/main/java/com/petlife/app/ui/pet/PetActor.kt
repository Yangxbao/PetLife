package com.petlife.app.ui.pet

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 动作定义：每种动作带【时长】与【优先级】。
 * 优先级：IDLE(0) < 微互动(1) < 互动(2) < 事件(3) < 重大(4)。
 * 高优先级可打断低优先级；同级仅在当前动作播过大半时允许接管；播完自动回 IDLE。
 */
enum class PetAction(val durationMs: Long, val priority: Int) {
    IDLE(0, 0),

    // ---- 微互动（待机随机小动作） ----
    WINK(700, 1),
    HEAD_TILT(1000, 1),
    LOOK_AROUND(1600, 1),
    HAIR_TUCK(1500, 1),
    TAIL_WAG(1500, 1),
    NOD(800, 1),

    // ---- 点击互动 ----
    SURPRISED(1000, 2),
    HAPPY_BOUNCE(1100, 2),
    HAPPY_SHAKE(1500, 2),
    SHY_COVER(2200, 2),
    PUFF(1800, 2),
    UPSET_DOWN(2000, 2),
    SPOILED(1800, 2),
    SMUG_AKIMBO(2200, 2),
    YAWN(2000, 2),
    STRETCH(2200, 2),
    TURN(1500, 2),
    JUMP(900, 2),
    SPIN(1500, 2),
    SLEEP(2400, 2),

    // ---- 事件 ----
    EAT(2800, 3),
    CELEBRATE(2400, 3),
    CHEER(2000, 3),
    PUFF_AKIMBO(2200, 3),
    WELCOME_BACK(2400, 3),

    // ---- 重大 ----
    LEVELUP(3000, 4),
    EVOLVE(3200, 4),
    GROWTH_POSE(3000, 4),
}

/**
 * 统一动作状态机：
 *  - 同一时刻只有一个动作，防止部位/动作冲突
 *  - play() 按优先级决定接受与否（低打断高会被拒绝；重大事件可用 force）
 *  - 播完自动回 IDLE；进度由 Animatable 驱动（Canvas 直接读取其 State）
 */
class PetActor(private val scope: CoroutineScope) {

    var action by mutableStateOf(PetAction.IDLE)
        private set

    private val anim = Animatable(1f)
    private var job: Job? = null

    val busy: Boolean get() = action != PetAction.IDLE

    /** Canvas 每帧读取的当前动作进度 0..1 */
    fun progress(): Float = anim.value

    fun play(target: PetAction, force: Boolean = false): Boolean {
        if (target == PetAction.IDLE) return false
        val cur = action
        if (!force && cur != PetAction.IDLE) {
            val curP = cur.priority
            val newP = target.priority
            val sameLevelTakeover = curP == newP && anim.value >= 0.7f
            if (newP < curP || (newP == curP && !sameLevelTakeover)) return false
        }
        job?.cancel()
        action = target
        job = scope.launch {
            runCatching {
                anim.snapTo(0f)
                anim.animateTo(1f, tween(target.durationMs.toInt().coerceAtLeast(200), easing = LinearEasing))
            }.onFailure { android.util.Log.e("PetLifeCrash", "动作动画异常（已忽略）", it) }
            if (action == target) action = PetAction.IDLE
        }
        return true
    }

    /** 立即回到待机（页面销毁等场景） */
    fun reset() {
        job?.cancel()
        action = PetAction.IDLE
        scope.launch { anim.snapTo(1f) }
    }
}

@Composable
fun rememberPetActor(): PetActor {
    val scope = rememberCoroutineScope()
    return remember { PetActor(scope) }
}
