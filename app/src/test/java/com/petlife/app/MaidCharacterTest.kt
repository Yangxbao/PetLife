package com.petlife.app

import com.petlife.app.data.model.PetStage
import com.petlife.app.ui.pet.PetAction
import com.petlife.app.ui.pet.character.Expression
import com.petlife.app.ui.pet.character.Faces
import com.petlife.app.ui.pet.character.MaidStageVisuals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 角色阶段视觉参数测试（同一角色、阶段间差异、渐变成长） */
class MaidStageVisualsTest {

    @Test
    fun `growthT covers each stage span`() {
        assertEquals(0f, MaidStageVisuals.growthTOf(1, PetStage.BABY), 0.001f)
        assertEquals(1f, MaidStageVisuals.growthTOf(10, PetStage.BABY), 0.001f)
        assertEquals(0f, MaidStageVisuals.growthTOf(10, PetStage.GROWING), 0.001f)
        assertEquals(0f, MaidStageVisuals.growthTOf(20, PetStage.ADULT), 0.001f)
        assertEquals(0f, MaidStageVisuals.growthTOf(30, PetStage.EVOLVED), 0.001f)
        assertEquals(1f, MaidStageVisuals.growthTOf(41, PetStage.EVOLVED), 0.001f)
    }

    @Test
    fun `baby has no whale tail - it grows with stages`() {
        assertEquals(0f, MaidStageVisuals.forStage(PetStage.BABY, 0.5f).tailScale, 0.001f)
        assertTrue(MaidStageVisuals.forStage(PetStage.GROWING, 0.5f).tailScale > 0f)
        assertTrue(MaidStageVisuals.forStage(PetStage.EVOLVED, 0.5f).tailScale > 0.8f)
    }

    @Test
    fun `stages differ visibly but remain the same character`() {
        // 发长、尾巴、装饰随阶段递增
        val params = PetStage.entries.map { MaidStageVisuals.forStage(it, 0.8f) }
        assertTrue(params[0].backHairLen < params[1].backHairLen)
        assertTrue(params[1].backHairLen < params[2].backHairLen)
        assertTrue(params[2].backHairLen < params[3].backHairLen)
        assertTrue("女仆头饰应随阶段出现", params[3].headdress >= params[0].headdress)
        assertTrue("进化期应有鲸鱼刺绣", params[3].whaleEmbroidery > params[0].whaleEmbroidery)
        assertTrue("进化期应有微光", params[3].glow > 0f)
    }

    @Test
    fun `hair grows gradually within stage`() {
        val start = MaidStageVisuals.forStage(PetStage.GROWING, 0f)
        val end = MaidStageVisuals.forStage(PetStage.GROWING, 1f)
        assertTrue(end.backHairLen > start.backHairLen)
        assertTrue(end.sideHairLen > start.sideHairLen)
    }

    @Test
    fun `out of range growthT clamps safely`() {
        val lo = MaidStageVisuals.forStage(PetStage.ADULT, -3f)
        val hi = MaidStageVisuals.forStage(PetStage.ADULT, 9f)
        assertEquals(MaidStageVisuals.forStage(PetStage.ADULT, 0f).bodyScale, lo.bodyScale, 0.0001f)
        assertEquals(MaidStageVisuals.forStage(PetStage.ADULT, 1f).bodyScale, hi.bodyScale, 0.0001f)
    }
}

/** 10 种表情：参数互不相同、关键属性生效 */
class ExpressionTest {

    @Test
    fun `all ten expressions exist and differ`() {
        val expressions = Expression.entries
        assertEquals(10, expressions.size)
        val styles = expressions.map { Faces.of(it) }
        // 两两不同（结构性相等比较）
        for (i in styles.indices) {
            for (j in i + 1 until styles.size) {
                assertNotEquals("表情 ${expressions[i]} 与 ${expressions[j]} 参数相同", styles[i], styles[j])
            }
        }
    }

    @Test
    fun `angry has anger mark - shy has blush lines - upset has tears`() {
        assertTrue(Faces.of(Expression.ANGRY).angerMark)
        assertTrue(Faces.of(Expression.SHY).blushLines)
        assertTrue(Faces.of(Expression.UPSET).tears)
        assertTrue(Faces.of(Expression.SPEECHLESS).sweat)
        assertTrue(Faces.of(Expression.SURPRISED).shockLines)
        assertTrue(Faces.of(Expression.SPOILED).sparkleInEye)
    }

    @Test
    fun `action maps to dedicated expression`() {
        assertEquals(Expression.SURPRISED, Faces.fromAction(PetAction.SURPRISED))
        assertEquals(Expression.SHY, Faces.fromAction(PetAction.SHY_COVER))
        assertEquals(Expression.ANGRY, Faces.fromAction(PetAction.PUFF))
        assertEquals(Expression.SMUG, Faces.fromAction(PetAction.SMUG_AKIMBO))
        assertEquals(Expression.SLEEPY, Faces.fromAction(PetAction.YAWN))
        assertEquals(null, Faces.fromAction(PetAction.IDLE))
    }
}
