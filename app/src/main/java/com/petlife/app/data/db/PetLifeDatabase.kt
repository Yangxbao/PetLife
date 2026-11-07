package com.petlife.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PetEntity::class,
        CheckinEntity::class,
        InventoryEntity::class,
        MonitoredAppEntity::class,
        AppUsageDayEntity::class,
        TaskClaimEntity::class,
        GoalChangeEntity::class,
        DailyRecordEntity::class,
        ExpEventEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class PetLifeDatabase : RoomDatabase() {

    abstract fun petDao(): PetDao
    abstract fun checkinDao(): CheckinDao
    abstract fun inventoryDao(): InventoryDao
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun usageDao(): UsageDao
    abstract fun taskClaimDao(): TaskClaimDao
    abstract fun goalChangeDao(): GoalChangeDao
    abstract fun dailyRecordDao(): DailyRecordDao
    abstract fun expEventDao(): ExpEventDao

    companion object {
        @Volatile
        private var instance: PetLifeDatabase? = null

        fun get(context: Context): PetLifeDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    PetLifeDatabase::class.java,
                    "petlife.db",
                )
                    .fallbackToDestructiveMigrationOnDowngrade()
                    .build()
                    .also { instance = it }
            }
    }
}
