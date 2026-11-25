package com.maya.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val ObsidianBg = Color(0xFF040711)
val NavySurface = Color(0xFF0B1221)
val NeonCyan = Color(0xFF38BDF8)
val ElectricBlue = Color(0xFF0EA5E9)
val DeepCobalt = Color(0xFF1D4ED8)
val SlateText = Color(0xFFE2E8F0)
val MutedText = Color(0xFF94A3B8)

private val MayaColorScheme = darkColorScheme(
    primary = ElectricBlue,
    secondary = NeonCyan,
    background = ObsidianBg,
    surface = NavySurface,
    onPrimary = Color.White,
    onBackground = SlateText,
    onSurface = SlateText
)

@Composable
fun MayaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MayaColorScheme,
        content = content
    )
}
