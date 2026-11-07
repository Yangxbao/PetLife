package com.petlife.app.data.model

/** 应用分类：学习 / 娱乐（由用户自行分类，不做自动判断） */
enum class AppCategory(val label: String, val emoji: String) {
    LEARN("学习", "📚"),
    FUN("娱乐", "🎮");

    companion object {
        fun from(s: String): AppCategory = entries.firstOrNull { it.name == s } ?: LEARN
    }
}

/** 宠物成长阶段 */
enum class PetStage(val label: String, val desc: String) {
    BABY("幼年期", "软软的小毛球，刚刚来到这个世界"),
    GROWING("成长期", "长出了小耳朵，开始好奇地探索"),
    ADULT("成年期", "头顶长出小叶子，稳重又可靠"),
    EVOLVED("进化期", "闪闪发光的伙伴，最忠实的陪伴");

    companion object {
        fun from(s: String): PetStage = entries.firstOrNull { it.name == s } ?: BABY
    }
}

enum class FoodRarity(val label: String) {
    COMMON("普通"),
    RARE("稀有"),
    SPECIAL("特殊");
}

/** 宠物情绪表现状态（用于绘制表情） */
enum class PetMoodState(val label: String) {
    HAPPY("开心"), CALM("平静"), SAD("失落"), TIRED("疲惫"), SICK("没精神");

    companion object {
        fun of(mood: Int, health: Int): PetMoodState = when {
            health < 35 -> SICK
            health < 55 -> TIRED
            mood >= 70 -> HAPPY
            mood >= 35 -> CALM
            else -> SAD
        }
    }
}

/** 经验来源 */
enum class ExpSource(val label: String) {
    FEED("喂食"), CHECKIN("签到"), CLAIM("任务奖励"), EVOLVE("进化"), SETTLE("每日结算")
}

/** 每日自律扣分/加分明细 */
data class DisciplineReason(
    val label: String,
    val delta: Int,        // 负数=扣分 正数=加分
)

data class OverApp(
    val packageName: String,
    val appName: String,
    val limitMinutes: Int,
    val usedMinutes: Long,
    val overMinutes: Long,
)

data class TaskStatus(
    val packageName: String,
    val appName: String,
    val targetMinutes: Int,
    val usedMinutes: Long,
    val completed: Boolean,
    val claimed: Boolean,
)

/** 每日结算结果（也用于"今日实时预览"） */
data class DayEvaluation(
    val date: String,
    val studyMinutes: Long,
    val funMinutes: Long,
    val tasksCompleted: Int,
    val tasksTotal: Int,
    val overApps: List<OverApp>,
    val overTotalMinutes: Long,
    val disciplineScore: Int,
    val reasons: List<DisciplineReason>,
    val growthCoefficient: Float,
    val expGained: Int,
    val foodsFed: Int,
)
