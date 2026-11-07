package com.petlife.app.ui.pet.character

import com.petlife.app.data.model.PetStage

/**
 * 「星伴」角色 v4：二次元 Q 版鲸鱼女仆少女（原创程序化分层立绘）。
 * 设计语言来自用户参考图：蓝色渐变长卷发、鲸鱼鳍状侧发、白色褶边女仆头饰、
 * 深蓝女仆裙+白围裙（鲸鱼刺绣）、鲸鱼尾、大蓝眼、傲娇气质。仅借鉴风格，完全原创绘制。
 */
object MaidPalette {
    val Skin = 0xFFFFEDE0.toInt()
    val SkinShade = 0xFFF6D9C8.toInt()
    val Blush = 0xFFFFB3AD.toInt()
    val BlushShy = 0xFFFF9E9E.toInt()

    // 头发：深靛蓝 → 亮蓝发梢
    val HairTop = 0xFF3B5BA5.toInt()
    val HairMid = 0xFF4770BF.toInt()
    val HairTip = 0xFF54AEE8.toInt()
    val HairTipLight = 0xFF7FC8F2.toInt()
    val HairShade = 0xFF2E4680.toInt()
    val HairLine = 0xFF27407A.toInt()

    // 眼睛
    val EyeTop = 0xFF1E3F8F.toInt()
    val EyeBottom = 0xFF58B8F0.toInt()
    val EyeOutline = 0xFF20283C.toInt()
    val EyeWhite = 0xFFFDFDFF.toInt()

    // 女仆装
    val DressNavy = 0xFF3A4A73.toInt()
    val DressNavyDark = 0xFF2C3857.toInt()
    val DressNavyLight = 0xFF46598A.toInt()
    val White = 0xFFFFFFFF.toInt()
    val WhiteShade = 0xFFE6ECF6.toInt()
    val Lace = 0xFFF6F9FF.toInt()
    val Gold = 0xFFE8C46B.toInt()

    // 鲸鱼元素
    val WhaleTop = 0xFF33406B.toInt()
    val WhaleBelly = 0xFF4A6AA8.toInt()
    val WhaleLight = 0xFF6FA8DC.toInt()
    val BowBlue = 0xFF5B9FE0.toInt()
    val Jewel = 0xFF4FA8E8.toInt()

    val Lash = 0xFF232A40.toInt()
    val Brow = 0xFF3A4A73.toInt()
    val MouthInner = 0xFF8A4A52.toInt()
    val Tongue = 0xFFFF9E9E.toInt()
    val Tear = 0xFF8FC8F0.toInt()
    val Sweat = 0xFFA8D8F0.toInt()
}

/** 一个成长阶段的完整外观参数（阶段内线性插值） */
data class MaidStageParams(
    val backHairLen: Float,      // 后发长度 0..1（胸→过臀）
    val sideHairLen: Float,      // 侧发髻长度
    val finScale: Float,         // 鲸鱼鳍状侧发 0..1
    val tailScale: Float,        // 鲸鱼尾 0..1（0=无尾）
    val headdress: Float,        // 女仆头饰褶边 0..1
    val sideBow: Float,          // 侧发蝴蝶结 0..1
    val brooch: Float,           // 领口宝石 0..1
    val laceLevel: Float,        // 围裙蕾丝 0..1
    val whaleEmbroidery: Float,  // 围裙鲸鱼刺绣 0..1
    val starPin: Float,          // 星星发饰 0..1
    val bodyScale: Float,        // 整体缩放
    val glow: Float,             // 进化期微光 0..1
)

object MaidStageVisuals {

    private data class Range(val s: MaidStageParams, val e: MaidStageParams)

    private fun p(
        backHair: Float, sideHair: Float, fin: Float, tail: Float, head: Float,
        bow: Float, brooch: Float, lace: Float, whale: Float, star: Float, body: Float, glow: Float,
    ) = MaidStageParams(backHair, sideHair, fin, tail, head, bow, brooch, lace, whale, star, body, glow)

    private val baby = Range(
        p(0.38f, 0.35f, 0.18f, 0f, 0.25f, 0f, 0f, 0.25f, 0f, 0f, 0.94f, 0f),
        p(0.48f, 0.45f, 0.30f, 0f, 0.25f, 0f, 0f, 0.35f, 0f, 0f, 0.96f, 0f),
    )
    private val growing = Range(
        p(0.55f, 0.55f, 0.45f, 0.30f, 0.45f, 0.60f, 0f, 0.55f, 0f, 0f, 0.98f, 0f),
        p(0.68f, 0.68f, 0.62f, 0.50f, 0.45f, 0.80f, 0f, 0.70f, 0f, 0f, 1.00f, 0f),
    )
    private val adult = Range(
        p(0.75f, 0.75f, 0.70f, 0.60f, 0.85f, 0.85f, 0.70f, 0.80f, 0f, 0f, 1.00f, 0f),
        p(0.88f, 0.88f, 0.85f, 0.80f, 1.00f, 1.00f, 0.90f, 0.95f, 0f, 0f, 1.02f, 0f),
    )
    private val evolved = Range(
        p(0.92f, 0.92f, 0.90f, 0.85f, 1.00f, 1.00f, 1.00f, 1.00f, 0.55f, 0f, 1.02f, 0.25f),
        p(1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.00f, 1.04f, 0.6f),
    )

    private fun rangeOf(stage: PetStage) = when (stage) {
        PetStage.BABY -> baby
        PetStage.GROWING -> growing
        PetStage.ADULT -> adult
        PetStage.EVOLVED -> evolved
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    /** 阶段内成长进度（与旧系统同一等级曲线） */
    fun growthTOf(level: Int, stage: PetStage): Float {
        val (start, endExclusive) = when (stage) {
            PetStage.BABY -> 1 to 10
            PetStage.GROWING -> 10 to 20
            PetStage.ADULT -> 20 to 30
            PetStage.EVOLVED -> 30 to 41
        }
        if (level >= endExclusive) return 1f
        return ((level - start).toFloat() / (endExclusive - start)).coerceIn(0f, 1f)
    }

    fun forStage(stage: PetStage, growthT: Float): MaidStageParams {
        val r = rangeOf(stage)
        val t = growthT.coerceIn(0f, 1f)
        fun mix(get: (MaidStageParams) -> Float) = lerp(get(r.s), get(r.e), t)
        return MaidStageParams(
            backHairLen = mix { it.backHairLen },
            sideHairLen = mix { it.sideHairLen },
            finScale = mix { it.finScale },
            tailScale = mix { it.tailScale },
            headdress = mix { it.headdress },
            sideBow = mix { it.sideBow },
            brooch = mix { it.brooch },
            laceLevel = mix { it.laceLevel },
            whaleEmbroidery = mix { it.whaleEmbroidery },
            starPin = mix { it.starPin },
            bodyScale = mix { it.bodyScale },
            glow = mix { it.glow },
        )
    }
}

// ==================== 表情系统 ====================

/** 10 种可区分表情 */
enum class Expression {
    NORMAL,   // 普通
    HAPPY,    // 开心
    ANGRY,    // 生气
    SHY,      // 害羞
    UPSET,    // 委屈
    SLEEPY,   // 困倦
    SURPRISED,// 惊讶
    SMUG,     // 得意
    SPEECHLESS, // 无语
    SPOILED,  // 撒娇
}

/** 嘴型 */
enum class MouthStyle { SMILE, OPEN_SMILE, OPEN_SHOUT, POUT, FLAT, WAVE, O_SMALL, SMIRK, WOBBLE }

/** 面部参数（各部位独立变化，表情=参数组合而非贴图切换） */
data class FaceStyle(
    val eyeOpenL: Float,      // 0..1（1=全睁，0=闭）
    val eyeOpenR: Float,
    val irisScale: Float,     // 瞳孔缩放（惊讶变小、撒娇变大）
    val gazeX: Float,         // -1..1 视线偏移
    val gazeY: Float,
    val browAngle: Float,     // 眉毛角度（度，正=内端下垂=生气）
    val browLift: Float,      // 眉毛抬高
    val mouth: MouthStyle,
    val mouthScale: Float,
    val blush: Float,         // 腮红强度
    val blushLines: Boolean,  // 害羞斜线
    val tears: Boolean,       // 委屈泪水
    val sweat: Boolean,       // 无语汗滴
    val angerMark: Boolean,   // 怒纹
    val sparkleInEye: Boolean,// 眼内星星（撒娇/得意）
    val shockLines: Boolean,  // 惊讶线
)

object Faces {

    fun of(e: Expression): FaceStyle = when (e) {
        Expression.NORMAL -> FaceStyle(1f, 1f, 1f, 0f, 0f, 0f, 0f, MouthStyle.SMILE, 1f, 0.35f, false, false, false, false, false, false)
        Expression.HAPPY -> FaceStyle(0f, 0f, 1f, 0f, 0f, -8f, 0.1f, MouthStyle.OPEN_SMILE, 1.15f, 0.6f, false, false, false, false, false, false)
        Expression.ANGRY -> FaceStyle(0.9f, 0.9f, 0.92f, 0f, 0f, 22f, -0.05f, MouthStyle.WAVE, 0.9f, 0.55f, false, false, false, true, false, false)
        Expression.SHY -> FaceStyle(0.75f, 0.75f, 1.05f, -0.35f, 0.1f, -4f, 0.05f, MouthStyle.WAVE, 0.85f, 0.95f, true, false, false, false, false, false)
        Expression.UPSET -> FaceStyle(0.85f, 0.85f, 1.08f, 0f, 0.12f, -10f, 0.08f, MouthStyle.WOBBLE, 0.95f, 0.5f, false, true, false, false, false, false)
        Expression.SLEEPY -> FaceStyle(0.32f, 0.32f, 1f, 0.1f, 0.05f, 6f, -0.02f, MouthStyle.O_SMALL, 0.7f, 0.25f, false, false, false, false, false, false)
        Expression.SURPRISED -> FaceStyle(1f, 1f, 0.7f, 0f, -0.1f, -14f, 0.18f, MouthStyle.O_SMALL, 1.25f, 0.3f, false, false, false, false, false, true)
        Expression.SMUG -> FaceStyle(0f, 0f, 1f, 0.15f, 0f, -6f, 0.12f, MouthStyle.SMIRK, 1.05f, 0.4f, false, false, false, false, true, false)
        Expression.SPEECHLESS -> FaceStyle(0.42f, 0.42f, 0.95f, 0.25f, 0.06f, 2f, 0f, MouthStyle.FLAT, 0.9f, 0.15f, false, false, true, false, false, false)
        Expression.SPOILED -> FaceStyle(1f, 1f, 1.15f, 0f, 0.05f, -12f, 0.12f, MouthStyle.POUT, 1.1f, 0.8f, false, true, false, false, true, false)
    }

    /** 情绪 → 常驻表情 */
    fun fromMood(mood: com.petlife.app.data.model.PetMoodState, annoyed: Boolean): Expression = when {
        annoyed -> Expression.ANGRY
        mood == com.petlife.app.data.model.PetMoodState.HAPPY -> Expression.HAPPY
        mood == com.petlife.app.data.model.PetMoodState.SAD -> Expression.UPSET
        mood == com.petlife.app.data.model.PetMoodState.SICK -> Expression.UPSET
        mood == com.petlife.app.data.model.PetMoodState.TIRED -> Expression.SLEEPY
        else -> Expression.NORMAL
    }

    /** 动作 → 专属表情（覆盖常驻表情） */
    fun fromAction(action: com.petlife.app.ui.pet.PetAction): Expression? = when (action) {
        com.petlife.app.ui.pet.PetAction.SURPRISED, com.petlife.app.ui.pet.PetAction.WELCOME_BACK -> Expression.SURPRISED
        com.petlife.app.ui.pet.PetAction.SHY_COVER -> Expression.SHY
        com.petlife.app.ui.pet.PetAction.PUFF, com.petlife.app.ui.pet.PetAction.PUFF_AKIMBO -> Expression.ANGRY
        com.petlife.app.ui.pet.PetAction.UPSET_DOWN -> Expression.UPSET
        com.petlife.app.ui.pet.PetAction.YAWN, com.petlife.app.ui.pet.PetAction.STRETCH -> Expression.SLEEPY
        com.petlife.app.ui.pet.PetAction.SMUG_AKIMBO -> Expression.SMUG
        com.petlife.app.ui.pet.PetAction.SPOILED -> Expression.SPOILED
        com.petlife.app.ui.pet.PetAction.HAPPY_BOUNCE, com.petlife.app.ui.pet.PetAction.HAPPY_SHAKE,
        com.petlife.app.ui.pet.PetAction.CELEBRATE, com.petlife.app.ui.pet.PetAction.CHEER,
        com.petlife.app.ui.pet.PetAction.EAT, com.petlife.app.ui.pet.PetAction.LEVELUP,
        com.petlife.app.ui.pet.PetAction.EVOLVE, com.petlife.app.ui.pet.PetAction.GROWTH_POSE -> Expression.HAPPY
        else -> null
    }
}
