package com.petlife.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val PetColorScheme = lightColorScheme(
    primary = Coral,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = CoralSoft,
    onPrimaryContainer = Ink,
    secondary = Mint,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = MintSoft,
    onSecondaryContainer = Ink,
    tertiary = Lavender,
    tertiaryContainer = LavenderSoft,
    onTertiaryContainer = Ink,
    background = Cream50,
    onBackground = Ink,
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = Ink,
    surfaceVariant = Cream100,
    onSurfaceVariant = InkSecondary,
    outline = InkTertiary,
    error = WarnRed,
)

@Composable
fun PetLifeTheme(content: @Composable () -> Unit) {
    // 产品定位为"温暖治愈系"，固定浅色（夜间模式留作后续扩展）
    MaterialTheme(
        colorScheme = PetColorScheme,
        typography = PetTypography,
        shapes = PetShapes,
        content = content,
    )
}
