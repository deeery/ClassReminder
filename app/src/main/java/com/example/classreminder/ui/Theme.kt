package com.example.classreminder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── 主题模式 ────────────────────────────────────────────────────

enum class ThemeMode(val label: String) {
    FOLLOW_SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色")
}

// ── 配色 ─────────────────────────────────────────────────────────
// Material 3 的色彩角色比 M2 多，这里把原来的蓝+琥珀配色原样映射过来，
// 只补上 M3 组件会用到的那几个角色（container / surfaceVariant / outline）。

private val LightColors = lightColorScheme(
    primary = Color(0xFF1565C0),              // 深蓝 — 主色
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),     // 浅蓝 — 选中态底
    onPrimaryContainer = Color(0xFF0D47A1),
    secondary = Color(0xFFF9A825),            // 暖黄 — 强调色
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFFFFF1CC),   // 浅黄 — 临时提醒底
    onSecondaryContainer = Color(0xFF6B4E00),
    background = Color(0xFFF8F9FA),           // 浅灰白 — 背景
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEDEFF3),
    onSurfaceVariant = Color(0xFF464C55),
    outline = Color(0xFFB4BBC5),
    outlineVariant = Color(0xFFE2E6EC),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF8C1D18)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),              // 浅蓝 — 深色背景上高对比
    onPrimary = Color(0xFF0D1B2A),
    primaryContainer = Color(0xFF16406B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFFFFD54F),
    onSecondary = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF5A4410),
    onSecondaryContainer = Color(0xFFFFE9B0),
    background = Color(0xFF121212),           // Material Design 深色背景标准
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1E1E1E),              // 卡片/表面
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF2A2E34),
    onSurfaceVariant = Color(0xFFC2C7CF),
    outline = Color(0xFF5A6169),
    outlineVariant = Color(0xFF33383F),
    error = Color(0xFFEF5350),
    onError = Color(0xFF1A1A1A),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFFDAD6)
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
        colorScheme = if (isDark) DarkColors else LightColors,
        content = content
    )
}
