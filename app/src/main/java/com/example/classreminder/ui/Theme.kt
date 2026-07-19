package com.example.classreminder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 主题模式 ────────────────────────────────────────────────────

enum class ThemeMode(val label: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

// ── 配色 ─────────────────────────────────────────────────────────

private val LightColors = lightColors(
    primary = Color(0xFF1565C0),           // 深蓝 — 主色
    primaryVariant = Color(0xFF0D47A1),    // 更深蓝
    secondary = Color(0xFFF9A825),         // 暖黄 — 强调色
    secondaryVariant = Color(0xFFF57F17),  // 深黄
    background = Color(0xFFF8F9FA),        // 浅灰白 — 背景
    surface = Color.White,
    error = Color(0xFFD32F2F),
    onPrimary = Color.White,
    onSecondary = Color(0xFF1A1A1A),
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
    onError = Color.White
)

private val DarkColors = darkColors(
    primary = Color(0xFF90CAF9),           // 浅蓝 — 主色（深色背景上高对比）
    primaryVariant = Color(0xFF42A5F5),
    secondary = Color(0xFFFFD54F),         // 暖黄 — 强调色
    secondaryVariant = Color(0xFFFFB300),
    background = Color(0xFF121212),        // Material Design 深色背景标准
    surface = Color(0xFF1E1E1E),           // 卡片/表面
    error = Color(0xFFEF5350),
    onPrimary = Color(0xFF0D1B2A),
    onSecondary = Color(0xFF1A1A1A),
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onError = Color(0xFF1A1A1A)
)

// ── Theme Composable ─────────────────────────────────────────────

@Composable
fun ClassReminderTheme(
    themeMode: ThemeMode = ThemeMode.FOLLOW_SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        ThemeMode.FOLLOW_SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    MaterialTheme(
        colors = if (isDark) DarkColors else LightColors,
        content = content
    )
}
