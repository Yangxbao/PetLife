package com.petlife.app.ui.pet.character

import android.content.Context
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.petlife.app.data.model.PetMoodState
import com.petlife.app.data.model.PetStage
import org.json.JSONObject

/**
 * 图片立绘模式（v1.3.0）：
 * 加载 assets/character/ 下的透明立绘 PNG + 面部锚点 JSON，
 * 运行时在锚点区域"贴脸"绘制参数化表情（10 种），并施加呼吸/摇摆/跳跃等整体动效。
 *
 * 素材管线：tools/prepare_character.py（GrabCut 抠图 + 虹膜/口部锚点检测）
 * 缺少素材时自动回退到程序化 MaidCharacter 渲染。
 */
data class FaceAnchor(
    val sprite: String,
    val spriteW: Int,
    val spriteH: Int,
    val eyeLX: Float, val eyeLY: Float,
    val eyeRX: Float, val eyeRY: Float,
    val mouthX: Float, val mouthY: Float,
    val faceCx: Float, val faceCy: Float,
    val eyeDx: Float,
    val eyeW: Float,
    val eyeH: Float,
    val patchCx: Float, val patchCy: Float, val patchRx: Float, val patchRy: Float,
    val blushDx: Float,
    val skin: Color3,
    val stageScale: Map<PetStage, Float>,
) {
    data class Color3(val r: Int, val g: Int, val b: Int)
}

object MaidAsset {

    @Volatile
    private var cachedAnchor: FaceAnchor? = null

    @Volatile
    private var cachedSprite: ImageBitmap? = null

    fun loadAnchor(context: Context): FaceAnchor? = cachedAnchor ?: runCatching {
        context.assets.open("character/face_anchor.json").bufferedReader().use { reader ->
            val o = JSONObject(reader.readText())
            fun pt(name: String) = o.getJSONObject(name)
            val skinArr = o.getJSONArray("skin")
            val scaleMuts = o.getJSONObject("stage_scale")
            FaceAnchor(
                sprite = o.optString("sprite", "maid_base.png"),
                spriteW = o.getInt("sprite_w"),
                spriteH = o.getInt("sprite_h"),
                eyeLX = pt("eye_l").getDouble("x").toFloat(), eyeLY = pt("eye_l").getDouble("y").toFloat(),
                eyeRX = pt("eye_r").getDouble("x").toFloat(), eyeRY = pt("eye_r").getDouble("y").toFloat(),
                mouthX = pt("mouth").getDouble("x").toFloat(), mouthY = pt("mouth").getDouble("y").toFloat(),
                faceCx = o.getDouble("face_cx").toFloat(),
                faceCy = o.getDouble("face_cy").toFloat(),
                eyeDx = o.getDouble("eye_dx").toFloat(),
                eyeW = o.getDouble("eye_w").toFloat(),
                eyeH = o.getDouble("eye_h").toFloat(),
                patchCx = pt("patch").getDouble("cx").toFloat(),
                patchCy = pt("patch").getDouble("cy").toFloat(),
                patchRx = pt("patch").getDouble("rx").toFloat(),
                patchRy = pt("patch").getDouble("ry").toFloat(),
                blushDx = o.getDouble("blush_dx").toFloat(),
                skin = FaceAnchor.Color3(skinArr.getInt(0), skinArr.getInt(1), skinArr.getInt(2)),
                stageScale = PetStage.entries.associateWith { scaleMuts.getDouble(it.name).toFloat() },
            )
        }
    }.getOrNull()?.also { cachedAnchor = it }

    fun loadSprite(context: Context): ImageBitmap? = cachedSprite ?: runCatching {
        val anchor = loadAnchor(context) ?: return null
        context.assets.open("character/${anchor.sprite}").use { stream ->
            android.graphics.BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()?.also { cachedSprite = it }

    /** 渲染模式：有素材 → 图片立绘；无 → 程序化立绘 */
    fun mode(context: Context): Mode =
        if (loadAnchor(context) != null && loadSprite(context) != null) Mode.IMAGE else Mode.PROCEDURAL

    enum class Mode { IMAGE, PROCEDURAL }
}
