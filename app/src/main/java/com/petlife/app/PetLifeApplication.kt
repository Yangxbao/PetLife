package com.petlife.app

import android.app.Application
import androidx.work.Configuration
import com.petlife.app.data.repo.PetLifeRepository
import com.petlife.app.pet.dialogue.DialogueEngine
import com.petlife.app.pet.dialogue.DialogueParser
import com.petlife.app.pet.weather.WeatherStore
import com.petlife.app.usage.UsageStatsReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** 手写服务定位器（无第三方 DI，保持构建简单可靠） */
object ServiceLocator {
    lateinit var repository: PetLifeRepository
        private set

    lateinit var prefs: com.petlife.app.data.repo.Prefs
        private set

    lateinit var weatherStore: WeatherStore
        private set

    /** 话术引擎：assets 异步加载完成后替换（加载完成前 pick 返回 null，UI 静默降级） */
    @Volatile
    var dialogueEngine: DialogueEngine = DialogueEngine(emptyList())
        private set

    /** 对话调度器：全局冷却 + 事件冷却 + 优先级仲裁 + 防重复 */
    lateinit var dialogueScheduler: com.petlife.app.pet.dialogue.DialogueScheduler
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val dialogueReady: Boolean get() = dialogueEngine.totalLines > 0

    fun init(app: Application) {
        com.petlife.app.core.CrashGuard.install(app)
        val reader = UsageStatsReader(app)
        prefs = com.petlife.app.data.repo.Prefs(app)
        repository = PetLifeRepository(app, reader)
        weatherStore = WeatherStore(app)
        dialogueScheduler = com.petlife.app.pet.dialogue.DialogueScheduler(
            engineProvider = { dialogueEngine.takeIf { dialogueReady } },
        )
        appScope.launch {
            val lines = DialogueParser.loadFromAssets(app)
            dialogueEngine = DialogueEngine(lines)
            android.util.Log.i("PetLifeStats", "话术库加载完成：${lines.size} 条")
        }
        appScope.launch {
            runCatching { weatherStore.restoreAndRefresh() }
        }
    }
}

class PetLifeApplication : Application(), Configuration.Provider {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()
}
