package com.petlife.app.core

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 桌面名称/图标自定义工具（不依赖 Root）。
 *
 * Android 真实限制（务必向用户说明）：
 *  1. 系统设置"应用列表"里的原始名称来自安装包清单，普通应用无法动态修改；
 *     唯一无 Root 的标准做法是【预设 activity-alias 切换】——只能在我们预先打包的几个
 *     名称/图标组合之间切换（本应用提供 3 组预设）。
 *  2. 完全自定义的名称/图标可以通过【钉选快捷方式】实现：长按桌面的那个新图标
 *     显示用户自定义的名称与图片；但它是快捷方式而非应用入口本体，
 *     各家桌面（launcher）对"请求钉选"的支持略有差异（ColorOS/realme UI 支持弹窗确认）。
 */
object LauncherUtil {

    /** 预设桌面方案（activity-alias） */
    data class Preset(val key: String, val label: String, val description: String)

    val PRESETS = listOf(
        Preset("Default", "星伴", "默认图标 · 应用名「星伴」"),
        Preset("PetName", "皮可", "默认图标 · 应用名「皮可」"),
        Preset("Mint", "星伴", "薄荷色宠物图标 · 应用名「星伴」"),
    )

    private fun aliasComponent(context: Context, key: String) =
        ComponentName(context, "com.petlife.app.Launcher$key")

    /**
     * 切换桌面入口预设：启用所选 alias，禁用其余。
     * 注意：切换瞬间桌面图标可能延迟 1~2 秒刷新，部分桌面需要手动滑动刷新。
     */
    fun applyPreset(context: Context, key: String): Boolean {
        if (PRESETS.none { it.key == key }) return false
        val pm = context.packageManager
        return runCatching {
            for (preset in PRESETS) {
                val component = aliasComponent(context, preset.key)
                val state = if (preset.key == key) {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(
                    component,
                    state,
                    android.content.pm.PackageManager.DONT_KILL_APP,
                )
            }
            true
        }.getOrDefault(false)
    }

    /** 当前启用的预设 */
    fun currentPreset(context: Context): String? {
        val pm = context.packageManager
        return PRESETS.firstOrNull {
            pm.getComponentEnabledSetting(aliasComponent(context, it.key)) ==
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }?.key
    }

    /** 是否支持请求钉选快捷方式（由当前桌面决定） */
    fun canPinShortcut(context: Context): Boolean =
        ShortcutManagerCompat.isRequestPinShortcutSupported(context)

    /**
     * 请求把一个【自定义名称 + 自定义图标】的快捷方式钉到桌面。
     * @param name 桌面上显示的名称（1~12 字）
     * @param iconBitmap 用户选择的图片（可为 null → 使用应用图标）
     * @return 是否成功发起（最终显示由桌面确认弹窗决定）
     */
    fun pinCustomShortcut(context: Context, name: String, iconBitmap: Bitmap?): Boolean {
        if (name.isBlank()) return false
        if (!canPinShortcut(context)) return false
        return runCatching {
            val icon = when {
                iconBitmap != null -> IconCompat.createWithBitmap(iconBitmap)
                else -> IconCompat.createWithResource(context, android.R.drawable.sym_def_app_icon)
            }
            val intent = Intent(context, Class.forName("com.petlife.app.MainActivity"))
                .setAction(Intent.ACTION_VIEW)
            val shortcut = ShortcutInfoCompat.Builder(context, "petlife_custom_${System.currentTimeMillis()}")
                .setShortLabel(name.trim().take(12))
                .setLongLabel(name.trim().take(12))
                .setIcon(icon)
                .setIntent(intent)
                .build()
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }

    /** 把用户选择的 Uri 解码为方形图标位图（在 IO 线程调用；Photo Picker 无需存储权限） */
    fun decodeSquareIcon(context: Context, uri: android.net.Uri, sizePx: Int = 192): Bitmap? = runCatching {
        val source = android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(context.contentResolver, uri),
        ) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            decoder.isMutableRequired = true
        }
        val size = minOf(source.width, source.height).coerceAtLeast(1)
        val square = Bitmap.createBitmap(source, (source.width - size) / 2, (source.height - size) / 2, size, size)
        Bitmap.createScaledBitmap(square, sizePx, sizePx, true)
    }.getOrNull()

    fun appIconBitmap(context: Context, sizePx: Int = 192): Bitmap? = runCatching {
        val d: Drawable = context.packageManager.getApplicationIcon(context.packageName)
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, sizePx, sizePx)
        d.draw(c)
        bmp
    }.getOrNull()

    fun formatTime(millis: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
}
