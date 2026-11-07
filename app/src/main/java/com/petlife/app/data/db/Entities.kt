package com.petlife.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 宠物（单只，id 固定为 1） */
@Entity(tableName = "pet")
data class PetEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "皮可",
    val level: Int = 1,
    val exp: Int = 0,
    val mood: Int = 80,
    val health: Int = 90,
    val birthEpochDay: Long,
    val lastFedAtMillis: Long = 0L,
    val lastInteractedMillis: Long = 0L,
    val evolutionCount: Int = 0,
)

/** 每日签到记录 */
@Entity(tableName = "checkins", indices = [Index(value = ["date"], unique = true)])
data class CheckinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,            // yyyy-MM-dd（本地时间）
    val streak: Int,
    val rewardsText: String,
    val millis: Long,
)

/** 食物库存 */
@Entity(tableName = "inventory")
data class InventoryEntity(
    @PrimaryKey val foodId: String,
    val quantity: Int,
)

/** 被监控的应用（用户自行分类与设定目标） */
@Entity(tableName = "monitored_apps")
data class MonitoredAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val category: String,          // LEARN | FUN
    val dailyTargetMinutes: Int,   // 学习=每日目标下限；娱乐=每日限额上限
    val createdAtEpochDay: Long,
    val lastModifiedEpochDay: Long = 0,
    val lastModifiedWeekKey: String = "",
)

/** 每日每应用前台使用时长缓存（分钟） */
@Entity(tableName = "usage_days", primaryKeys = ["date", "packageName"])
data class AppUsageDayEntity(
    val date: String,
    val packageName: String,
    val minutes: Long,
)

/** 学习任务领取记录（每应用每天一次） */
@Entity(tableName = "task_claims", primaryKeys = ["date", "packageName"])
data class TaskClaimEntity(
    val date: String,
    val packageName: String,
    val claimedAtMillis: Long,
    val rewardText: String,
)

/** 目标修改历史（SET=添加时首次设置，不占额度；MODIFY=修改，占额度） */
@Entity(tableName = "goal_changes")
data class GoalChangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val weekKey: String,
    val packageName: String,
    val appName: String,
    val changeType: String,        // SET | MODIFY
    val field: String,             // TARGET(学习) | LIMIT(娱乐)
    val oldValueMinutes: Int,
    val newValueMinutes: Int,
)

/** 每日结算记录（历史 + 统计页数据源；每日唯一，保证幂等） */
@Entity(tableName = "daily_records", indices = [Index(value = ["date"], unique = true)])
data class DailyRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val studyMinutes: Long,
    val funMinutes: Long,
    val tasksCompleted: Int,
    val tasksTotal: Int,
    val funOverCount: Int,
    val overMinutes: Long,
    val disciplineScore: Int,
    val reasonsJson: String,
    val growthCoefficient: Float,
    val expGained: Int,
    val foodsFed: Int,
    val moodAfter: Int,
    val healthAfter: Int,
    val settledAtMillis: Long,
)

/** 经验/心情流水（喂食、签到、领取、进化） */
@Entity(tableName = "exp_events")
data class ExpEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val millis: Long,
    val source: String,      // FEED | CHECKIN | CLAIM | EVOLVE | SETTLE
    val foodId: String?,
    val exp: Int,
    val moodDelta: Int,
)
