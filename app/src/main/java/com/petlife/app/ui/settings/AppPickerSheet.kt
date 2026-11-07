package com.petlife.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import com.petlife.app.ServiceLocator
import com.petlife.app.data.model.AppCategory
import com.petlife.app.data.repo.PetLifeRepository
import com.petlife.app.ui.theme.Cream100
import com.petlife.app.ui.theme.Cream50
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary

/**
 * 应用选择器（v1.1.0 重写为全屏浮层）。
 *
 * 卡顿根治说明：
 *  - 旧实现把可滚动列表放进 ModalBottomSheet，M3 Sheet 的嵌套滚动连接会拦截
 *    LazyColumn 的滑动手势（尤其向上快速滑动时），表现为"断触/滑到一半停住"。
 *  - 新实现使用全屏 Dialog 浮层：列表滚动完全不经过 Sheet 手势系统；
 *    应用列表与图标在全进程只扫描/渲染一次（AppCatalog 缓存）。
 */
@Composable
fun AppPickerSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onPicked: (packageName: String, appName: String, category: AppCategory, minutes: Int) -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 4 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 4 }),
    ) {
        val repo = ServiceLocator.repository
        var apps by remember { mutableStateOf<List<PetLifeRepository.InstalledApp>?>(AppCatalog.cachedOrNull()) }
        var query by rememberSaveable { mutableStateOf("") }
        var selected by remember { mutableStateOf<PetLifeRepository.InstalledApp?>(null) }
        var category by remember { mutableStateOf(AppCategory.LEARN) }
        var minutesText by rememberSaveable { mutableStateOf("30") }

        LaunchedEffect(visible) {
            if (visible && apps == null) apps = AppCatalog.getOrLoad(repo)
        }

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(Cream50)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding(),
            ) {
                // 顶栏
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("添加监控应用", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Ink)
                        Text(
                            "选择分类并设定每日目标（首次设置不消耗修改次数）",
                            fontSize = 12.sp, color = InkSecondary,
                        )
                    }
                    TextButton(onClick = onDismiss) { Text("关闭", color = InkSecondary) }
                }

                if (selected == null) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("搜索应用…") },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    val list = apps
                    when {
                        list == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                        else -> {
                            val filtered = remember(query, list) {
                                if (query.isBlank()) list else list.filter {
                                    it.label.contains(query, ignoreCase = true) ||
                                        it.packageName.contains(query, ignoreCase = true)
                                }
                            }
                            LazyColumn(
                                Modifier.fillMaxSize().padding(horizontal = 12.dp),
                                // 关键：让容器消费滚动，不再有 Sheet 抢手势
                            ) {
                                items(filtered, key = { it.packageName }, contentType = { "app" }) { app ->
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .clickable { selected = app }
                                            .padding(horizontal = 10.dp, vertical = 9.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        PickerIcon(app.iconBitmap, 40.dp, app.label)
                                        Column {
                                            Text(app.label, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Ink, maxLines = 1)
                                            Text(app.packageName, fontSize = 10.sp, color = InkSecondary, maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 选择分类 + 目标
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp),
                    ) {
                        Spacer(Modifier.height(10.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            PickerIcon(selected!!.iconBitmap, 48.dp, selected!!.label)
                            Text(selected!!.label, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Ink)
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AppCategory.entries.forEach { c ->
                                FilterChip(
                                    selected = category == c,
                                    onClick = {
                                        category = c
                                        minutesText = when (c) {
                                            AppCategory.LEARN -> "60"
                                            AppCategory.FUN -> "30"
                                        }
                                    },
                                    label = { Text("${c.emoji} ${c.label}") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Cream100,
                                        selectedLabelColor = Ink,
                                    ),
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        OutlinedTextField(
                            value = minutesText,
                            onValueChange = { s -> minutesText = s.filter { it.isDigit() }.take(4) },
                            label = { Text(if (category == AppCategory.LEARN) "每日学习目标（分钟）" else "每日娱乐限额（分钟）") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { selected = null }) { Text("← 重新选择", color = InkSecondary) }
                            Button(
                                onClick = {
                                    val m = minutesText.toIntOrNull() ?: 0
                                    if (m in 1..1440 && selected != null) {
                                        onPicked(selected!!.packageName, selected!!.label, category, m)
                                    }
                                },
                                shape = RoundedCornerShape(50),
                                enabled = (minutesText.toIntOrNull() ?: 0) in 1..1440,
                            ) {
                                Text("添加", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerIcon(bitmap: Bitmap?, sizeDp: androidx.compose.ui.unit.Dp, label: String) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = label,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(sizeDp).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier.size(sizeDp).clip(RoundedCornerShape(10.dp)).background(Cream100),
            contentAlignment = Alignment.Center,
        ) { Text("📱", fontSize = 16.sp) }
    }
}
