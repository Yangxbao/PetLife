package com.petlife.app.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.petlife.app.ui.theme.Cream100
import com.petlife.app.ui.theme.Ink
import com.petlife.app.ui.theme.InkSecondary
import com.petlife.app.ui.theme.Mint
import com.petlife.app.ui.theme.WarnRed

/** 圆角白卡片：整个应用的容器基调 */
@Composable
fun PetCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box(Modifier.padding(16.dp)) { content() }
    }
}

/** 区块标题（左侧圆点装饰） */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.size(8.dp))
        Text(text, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

/** 小状态胶囊 */
@Composable
fun StatChip(
    label: String,
    value: String,
    color: Color = Mint,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, fontSize = 12.sp, color = InkSecondary)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Ink)
    }
}

/**
 * 通用进度条（带目标刻度颜色渐变）。
 * ratio: 0..1+；over=超出时变红
 */
@Composable
fun GoalProgress(
    ratio: Float,
    over: Boolean,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    val clamped = ratio.coerceIn(0f, 1f)
    val trackColor = if (over) WarnRed.copy(alpha = 0.16f) else Cream100
    val barColor = when {
        over -> WarnRed
        ratio >= 1f -> Mint
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(50))
            .background(trackColor),
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(height)
                .clip(RoundedCornerShape(50))
                .background(barColor),
        )
    }
}

/** 应用图标（Drawable → ImageBitmap） */
@Composable
fun rememberAppIcon(drawable: Drawable?, sizePx: Int = 96): androidx.compose.ui.graphics.ImageBitmap? {
    return remember(drawable, sizePx) {
        drawable?.let {
            runCatching {
                val w = if (it.intrinsicWidth > 0) it.intrinsicWidth.coerceAtMost(256) else sizePx
                val h = if (it.intrinsicHeight > 0) it.intrinsicHeight.coerceAtMost(256) else sizePx
                val bmp = Bitmap.createBitmap(w.coerceAtLeast(1), h.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                val canvas = AndroidCanvas(bmp)
                it.setBounds(0, 0, w, h)
                it.draw(canvas)
                bmp.asImageBitmap()
            }.getOrNull()
        }
    }
}

@Composable
fun AppIcon(drawable: Drawable?, size: Dp = 40.dp) {
    val bmp = rememberAppIcon(drawable)
    if (bmp != null) {
        Image(
            bitmap = bmp,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(10.dp)),
        )
    } else {
        Box(
            Modifier.size(size).clip(RoundedCornerShape(10.dp)).background(Cream100),
            contentAlignment = Alignment.Center,
        ) {
            Text("📱", fontSize = 18.sp)
        }
    }
}

/** 通用确认对话框 */
@Composable
fun ConfirmDialog(
    title: String,
    body: String,
    confirmText: String = "确定",
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.imePadding()) {
                Text(body, color = InkSecondary, lineHeight = 22.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (danger) WarnRed else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
    )
}

/** 数字输入对话框（用于设置目标分钟） */
@Composable
fun NumberInputDialog(
    title: String,
    initial: Int,
    hint: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var text = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(initial.toString()) }
    val value = text.value.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.imePadding()) {
                Text(hint, style = MaterialTheme.typography.bodyMedium, color = InkSecondary)
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = text.value,
                    onValueChange = { s -> text.value = s.filter { it.isDigit() }.take(4) },
                    suffix = { Text("分钟") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = value != null && value > 0 && value <= 1440,
            ) { Text("确定", fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消", color = InkSecondary) } },
        containerColor = Color.White,
        shape = RoundedCornerShape(22.dp),
    )
}

/** 空状态提示 */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🐾", fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = InkSecondary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
    }
}
