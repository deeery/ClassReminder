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

// ── 配色：Google 标准蓝（Material 3 基准色板） ──────────────────
//
// 这一版把主色从原来的 #1565C0 换成 Google 官方应用的基准蓝 #1A73E8
// （Calendar / Gmail / Drive 都在用的那一支），并补齐 M3 的全部语义角色。
//
// 深色下的主色不用 #1A73E8 原色 —— 它对 #121212 只有 3.1:1，达不到 AA 的白字要求。
// Google 官方深色用的是提亮版 #8AB4F8（对比度 7.4:1），这里照搬。
//
// 中性色走 Google 的 Gray 灰阶（#F8F9FA / #5F6368 这一族），
// 比 Material 默认的紫灰更冷、更接近 Google 应用的实际观感。

private val LightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),              // Google Blue 600
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E3FD),     // 选中态底 / 导航胶囊
    onPrimaryContainer = Color(0xFF0B3D91),
    secondary = Color(0xFFF9AB00),            // Google Yellow 600 — 临时提醒
    onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFFFEEFC3),   // 临时提醒底
    onSecondaryContainer = Color(0xFF7A5C00),
    tertiary = Color(0xFF188038),             // Google Green 600 — 服务运行中
    onTertiary = Color.White,
    background = Color(0xFFF8F9FA),           // Google Gray 50
    onBackground = Color(0xFF1F1F1F),
    surface = Color.White,
    onSurface = Color(0xFF1F1F1F),            // Google Gray 900（不是纯黑，更柔和）
    surfaceVariant = Color(0xFFF1F3F4),       // Google Gray 100
    onSurfaceVariant = Color(0xFF5F6368),     // Google Gray 700
    // 灰阶拉开三档：outline 能在白卡上看见（1.76:1），
    // outlineVariant 只当分隔线（1.24:1），surfaceVariant 是最淡的填充
    outline = Color(0xFF80868B),              // Google Gray 600
    outlineVariant = Color(0xFFDADCE0),       // Google Gray 300
    error = Color(0xFFD93025),                // Google Red 600
    onError = Color.White,
    errorContainer = Color(0xFFFCE8E6),
    onErrorContainer = Color(0xFF8C1D18)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),              // Google Blue 200 — 深色专用的提亮版
    onPrimary = Color(0xFF0D2D62),
    primaryContainer = Color(0xFF1A3D7C),     // 深色下的选中底
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFFDD663),            // Google Yellow 200
    onSecondary = Color(0xFF1F1F1F),
    secondaryContainer = Color(0xFF5C4614),
    onSecondaryContainer = Color(0xFFFEEFC3),
    tertiary = Color(0xFF81C995),             // Google Green 200
    onTertiary = Color(0xFF0D2D1A),
    background = Color(0xFF121212),           // Material Design 深色标准底
    onBackground = Color(0xFFE8EAED),         // Google Gray 200
    surface = Color(0xFF1E1E1E),              // Material 深色表面
    onSurface = Color(0xFFE8EAED),
    surfaceVariant = Color(0xFF2A2B2E),
    onSurfaceVariant = Color(0xFF9AA0A6),     // Google Gray 500
    // 深色下灰阶同样拉三档：outline 对 surface 1.78:1（能当描边）
    outline = Color(0xFF5F6368),              // Google Gray 700
    outlineVariant = Color(0xFF3C4043),       // Google Gray 800
    error = Color(0xFFF28B82),                // Google Red 200
    onError = Color(0xFF1F1F1F),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFCE8E6)
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
