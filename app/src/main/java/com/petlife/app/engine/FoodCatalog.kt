package com.petlife.app.engine

import com.petlife.app.data.model.FoodRarity

data class FoodDef(
    val id: String,
    val name: String,
    val emoji: String,
    val exp: Int,
    val mood: Int,
    val rarity: FoodRarity,
    val desc: String,
)

/**
 * 食物图鉴（全静态目录）。
 * 普通：日常签到/小任务可得；稀有：大任务奖励；特殊：里程碑/连续签到/进化奖励。
 */
object FoodCatalog {

    val FOODS: Map<String, FoodDef> = listOf(
        FoodDef("apple", "小苹果", "🍎", 12, 4, FoodRarity.COMMON, "脆脆甜甜，皮可的最爱"),
        FoodDef("carrot", "胡萝卜", "🥕", 14, 3, FoodRarity.COMMON, "咔嚓咔嚓，健康满满"),
        FoodDef("milk", "热牛奶", "🥛", 10, 6, FoodRarity.COMMON, "暖暖的，睡个好觉"),
        FoodDef("riceball", "饭团", "🍙", 15, 2, FoodRarity.COMMON, "简单扎实的一餐"),
        FoodDef("cake", "草莓蛋糕", "🍰", 25, 10, FoodRarity.RARE, "奖励自己的甜点"),
        FoodDef("sushi", "寿司拼盘", "🍣", 30, 8, FoodRarity.RARE, "认真学习的犒劳"),
        FoodDef("honey", "蜂蜜罐", "🍯", 20, 12, FoodRarity.RARE, "甜到心里，情绪 +MAX"),
        FoodDef("star", "星光饼干", "🌟", 40, 15, FoodRarity.SPECIAL, "据说吃了会变聪明"),
        FoodDef("rainbow", "彩虹软糖", "🌈", 35, 20, FoodRarity.SPECIAL, "七彩的快乐"),
        FoodDef("party", "庆祝蛋糕", "🎂", 50, 18, FoodRarity.SPECIAL, "值得纪念的日子"),
    ).associateBy { it.id }

    fun get(id: String): FoodDef = FOODS[id] ?: FOODS.getValue("apple")
}
