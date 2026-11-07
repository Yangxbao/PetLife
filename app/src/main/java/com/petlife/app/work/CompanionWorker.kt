package com.petlife.app.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.petlife.app.ServiceLocator
import com.petlife.app.data.model.PetMoodState
import com.petlife.app.pet.dialogue.DialogueRequest
import com.petlife.app.ui.pet.PetAction
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * 每日问候通知（可选，设置页开关；未授权通知权限时静默跳过）。
 * 宠物用一句话在固定时间问候用户；通知内容由话术引擎按时间段生成。
 */
class CompanionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val context = applicationContext
        val notifyEnabled = runCatching {
            com.petlife.app.data.repo.Prefs(context).notifyEnabled.first()
        }.getOrDefault(false)
        if (!notifyEnabled) return Result.success()

        // Android 13+ 需要运行时通知权限
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }

        val hour = LocalTime.now().hour
        val event = if (hour in 5..17) "GREET" else "SLEEP"
        val petName = runCatching {
            ServiceLocator.repository.pet.first()?.name ?: "皮可"
        }.getOrDefault("皮可")
        val line = runCatching {
            ServiceLocator.dialogueEngine.pick(
                DialogueRequest(event = event, hour = hour, petName = petName),
            )?.text
        }.getOrNull() ?: "想你了，回来看看我吧～"

        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setContentTitle(petName)
            .setContentText(line)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(NOTIFY_ID, notification)
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "petlife_companion"
        const val NOTIFY_ID = 1001
        private const val WORK_NAME = "petlife_companion_daily"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= 26) {
                val channel = NotificationChannel(
                    CHANNEL_ID, "伙伴问候", NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = "宠物在固定时间的温柔问候" }
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.createNotificationChannel(channel)
            }
        }

        /** 每天 21:30 左右问候一次（对齐下一次触发点） */
        fun schedule(context: Context) {
            ensureChannel(context)
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(21, 30)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next)
            val request = PeriodicWorkRequestBuilder<CompanionWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
