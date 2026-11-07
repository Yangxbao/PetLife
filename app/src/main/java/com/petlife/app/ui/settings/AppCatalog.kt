package com.petlife.app.ui.settings

import android.graphics.Bitmap
import com.petlife.app.data.repo.PetLifeRepository

/**
 * 全进程应用目录缓存：
 *  - 扫描 + 图标预渲染只执行一次（后台线程），之后所有打开选择器都是即时显示
 *  - 监控应用变动后可调用 refresh() 手动失效
 */
object AppCatalog {

    @Volatile
    private var cached: List<PetLifeRepository.InstalledApp>? = null

    fun cachedOrNull(): List<PetLifeRepository.InstalledApp>? = cached

    suspend fun getOrLoad(repo: PetLifeRepository, iconSizePx: Int = 96): List<PetLifeRepository.InstalledApp> {
        cached?.let { return it }
        val loaded = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            repo.installedLaunchableApps(iconSizePx)
        }
        cached = loaded
        return loaded
    }

    fun invalidate() {
        cached = null
    }
}
