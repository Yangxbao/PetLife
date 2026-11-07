package com.petlife.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PetDao {
    @Query("SELECT * FROM pet WHERE id = 1")
    fun observe(): Flow<PetEntity?>

    @Query("SELECT * FROM pet WHERE id = 1")
    suspend fun get(): PetEntity?

    @Upsert
    suspend fun upsert(pet: PetEntity)

    @Query("DELETE FROM pet")
    suspend fun clear()
}

@Dao
interface CheckinDao {
    @Query("SELECT * FROM checkins WHERE date = :date LIMIT 1")
    suspend fun get(date: String): CheckinEntity?

    @Query("SELECT * FROM checkins ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<CheckinEntity>>

    @Query("SELECT * FROM checkins ORDER BY date DESC LIMIT 1")
    suspend fun latest(): CheckinEntity?

    @Insert
    suspend fun insert(e: CheckinEntity)

    @Query("DELETE FROM checkins")
    suspend fun clear()
}

@Dao
interface InventoryDao {
    @Query("SELECT * FROM inventory ORDER BY foodId")
    fun observeAll(): Flow<List<InventoryEntity>>

    @Query("SELECT * FROM inventory WHERE foodId = :foodId LIMIT 1")
    suspend fun get(foodId: String): InventoryEntity?

    @Upsert
    suspend fun upsert(e: InventoryEntity)

    @Query("UPDATE inventory SET quantity = quantity + :delta WHERE foodId = :foodId")
    suspend fun add(foodId: String, delta: Int)

    @Query("DELETE FROM inventory")
    suspend fun clear()

    @Transaction
    suspend fun grant(foodId: String, count: Int) {
        add(foodId, count)
    }

    @Transaction
    suspend fun consume(foodId: String, count: Int) {
        val cur = get(foodId)?.quantity ?: 0
        if (cur - count <= 0) {
            upsert(InventoryEntity(foodId, 0))
        } else {
            add(foodId, -count)
        }
    }
}

@Dao
interface MonitoredAppDao {
    @Query("SELECT * FROM monitored_apps ORDER BY category, appName")
    fun observeAll(): Flow<List<MonitoredAppEntity>>

    @Query("SELECT * FROM monitored_apps")
    suspend fun getAll(): List<MonitoredAppEntity>

    @Query("SELECT * FROM monitored_apps WHERE packageName = :pkg LIMIT 1")
    suspend fun get(pkg: String): MonitoredAppEntity?

    @Upsert
    suspend fun upsert(e: MonitoredAppEntity)

    @Query("DELETE FROM monitored_apps WHERE packageName = :pkg")
    suspend fun delete(pkg: String)

    @Query("DELETE FROM monitored_apps")
    suspend fun clear()
}

@Dao
interface UsageDao {
    @Query("SELECT * FROM usage_days WHERE date = :date")
    fun observeDay(date: String): Flow<List<AppUsageDayEntity>>

    @Query("SELECT * FROM usage_days WHERE date = :date")
    suspend fun day(date: String): List<AppUsageDayEntity>

    @Query("SELECT * FROM usage_days WHERE date BETWEEN :from AND :to")
    suspend fun range(from: String, to: String): List<AppUsageDayEntity>

    @Query("DELETE FROM usage_days WHERE date = :date")
    suspend fun clearDate(date: String)

    @Upsert
    suspend fun upsertAll(rows: List<AppUsageDayEntity>)

    @Query("DELETE FROM usage_days")
    suspend fun clear()
}

@Dao
interface TaskClaimDao {
    @Query("SELECT * FROM task_claims WHERE date = :date AND packageName = :pkg LIMIT 1")
    suspend fun get(date: String, pkg: String): TaskClaimEntity?

    @Query("SELECT * FROM task_claims WHERE date = :date")
    fun observeDate(date: String): Flow<List<TaskClaimEntity>>

    @Query("SELECT * FROM task_claims WHERE date = :date")
    suspend fun forDate(date: String): List<TaskClaimEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(e: TaskClaimEntity)

    @Query("DELETE FROM task_claims")
    suspend fun clear()
}

@Dao
interface GoalChangeDao {
    @Query("SELECT COUNT(*) FROM goal_changes WHERE weekKey = :weekKey AND changeType = 'MODIFY'")
    suspend fun modifyCount(weekKey: String): Int

    @Query("SELECT * FROM goal_changes ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<GoalChangeEntity>>

    @Insert
    suspend fun insert(e: GoalChangeEntity)

    @Query("DELETE FROM goal_changes")
    suspend fun clear()
}

@Dao
interface DailyRecordDao {
    @Query("SELECT * FROM daily_records WHERE date = :date LIMIT 1")
    suspend fun get(date: String): DailyRecordEntity?

    @Query("SELECT * FROM daily_records ORDER BY date DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<DailyRecordEntity>>

    @Query("SELECT * FROM daily_records WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun rangeAsc(from: String, to: String): List<DailyRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(e: DailyRecordEntity)

    @Query("DELETE FROM daily_records")
    suspend fun clear()
}

@Dao
interface ExpEventDao {
    @Insert
    suspend fun insert(e: ExpEventEntity)

    @Query("SELECT COALESCE(SUM(exp), 0) FROM exp_events WHERE date = :date")
    suspend fun sumExpForDate(date: String): Int

    @Query("SELECT COUNT(*) FROM exp_events WHERE date = :date AND source = 'FEED'")
    suspend fun feedCountForDate(date: String): Int

    @Query("SELECT * FROM exp_events ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<ExpEventEntity>>

    @Query("DELETE FROM exp_events")
    suspend fun clear()
}

// （数据重置由 PetLifeRepository.resetAllData 逐表执行，无需独立 MaintenanceDao）
