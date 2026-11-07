package com.petlife.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.petlife.app.ServiceLocator
import java.util.concurrent.TimeUnit

/**
 * 定期后台任务：每日结算 + 使用时长缓存刷新。
 * 即使长期不打开应用，跨午夜后也会在下次执行时正确补算（幂等，最多回溯 14 天）。
 */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            ServiceLocator.repository.onAppOpened()
            Result.success()
        } catch (t: Throwable) {
            Result.retry()
        }
    }

    companion object {
        private const val NAME = "petlife_daily_worker"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DailyWorker>(20, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}
