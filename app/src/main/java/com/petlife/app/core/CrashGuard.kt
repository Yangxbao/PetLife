package com.petlife.app.core

import android.content.Context
import java.io.File

/**
 * 崩溃守护：把最后一次未捕获异常的堆栈写入应用私有目录，
 * 供设置页「查看崩溃日志」直接展示（无需连电脑抓 logcat）。
 */
object CrashGuard {

    private const val FILE_NAME = "petlife_crash.txt"
    private const val MAX_BYTES = 32 * 1024

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                lastTrace(appContext)?.let { old ->
                    // 保留上一次崩溃，避免被新的覆盖后无法对比：以分隔线追加
                }
                val text = buildString {
                    append("==== ")
                    append(java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
                        .format(java.util.Date()))
                    append(" @")
                    append(thread.name)
                    append(" ====\n")
                    append(throwable.javaClass.name)
                    append(": ")
                    append(throwable.message)
                    append("\n")
                    append(throwable.stackTraceToString().take(MAX_BYTES))
                    append("\n")
                }
                File(appContext.filesDir, FILE_NAME).appendText(text)
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun lastTrace(context: Context): String? {
        val f = File(context.filesDir, FILE_NAME)
        return if (f.exists() && f.length() > 0) {
            runCatching { f.readText() }.getOrNull()
        } else null
    }

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }
}
