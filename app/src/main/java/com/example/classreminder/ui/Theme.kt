package com.example.classreminder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

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
// 深色下的主色**也用 #1A73E8**（和浅色同一个 Google Blue 600），不再走 Google 官方深色那套
// 提亮版 #8AB4F8 —— 深浅两套用同一个品牌蓝，高亮色才不会「一进深色就换了支蓝」。
//
// 代价要清楚：#1A73E8 对 #121212 是 4.2:1、对卡片 #1E1E1E 是 3.8:1，
// **小字号正文达不到 AA 的 4.5:1**（大标题 / 图标 / 色块仍达标）。
// 所以主色只承担「高亮块、图标、按钮底、色条」这类角色，不用来写正文。
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
    primary = Color(0xFF1A73E8),              // Google Blue 600 — 与浅色同色
    onPrimary = Color.White,                  // 主色变深后，压在它上面的字/图标必须是白的
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

// ── 便签调色盘 ───────────────────────────────────────────────────
//
// 8 种可选颜色，按「色相环均匀铺开」排序：蓝 → 青 → 绿 → 黄 → 橙 → 红 → 紫 → 灰。
// 第 0 格是**主题主色**（Google Blue），也就是便签竖条的原本颜色 ——
// 这样调色盘里天然包含了「当前颜色」，用户想改回来有据可依。
//
// 浅色 / 深色各一套色值：同一个色相在深浅底上要分别调明度，
// 浅色底用 600 档（够深、在白色卡片上看得清），深色底用 200 档（够亮、在深卡片上看得清）。
// 这是 Material 3 的标准做法，也是 Google 各应用的颜色选择器的实际取法。

/** 调色盘色号 → 浅色主题下的颜色 */
private val NotePaletteLight = listOf(
    Color(0xFF1A73E8),   // 0 Google Blue  600（= 主题主色，即原有竖条颜色）
    Color(0xFF00838F),   // 1 Cyan         700
    Color(0xFF188038),   // 2 Google Green 600
    Color(0xFFF9AB00),   // 3 Google Yellow 600
    Color(0xFFE8710A),   // 4 Orange       600
    Color(0xFFD93025),   // 5 Google Red   600
    Color(0xFF8430CE),   // 6 Purple       600
    Color(0xFF5F6368)    // 7 Google Gray  700
)

/** 调色盘色号 → 深色主题下的颜色 */
private val NotePaletteDark = listOf(
    Color(0xFF8AB4F8),   // 0 Google Blue  200（调色盘统一走 200 档，深底上才够亮）
    Color(0xFF4DD0E1),   // 1 Cyan         300
    Color(0xFF81C995),   // 2 Google Green 200
    Color(0xFFFDD663),   // 3 Google Yellow 200
    Color(0xFFFFB74D),   // 4 Orange       300
    Color(0xFFF28B82),   // 5 Google Red   200
    Color(0xFFD7AEFB),   // 6 Purple       200
    Color(0xFF9AA0A6)    // 7 Google Gray  500
)

/**
 * 深色下的「浅色 Google Blue」（提亮版）。
 *
 * 深色主色是 `#1A73E8`（与浅色同色），但它对深色卡片只有 3.8:1 —— 做**小字号文字**
 * 会发闷，纯白又太素（和正文同色，时间就不再像「时间」了）。
 * 这个提亮版专供深色下的小字：保住品牌蓝，亮度也够。
 * 浅色主题不用它（浅底上用主色 `#1A73E8` 就对了）。
 */
val GoogleBlueLight = Color(0xFF8AB4F8)

/**
 * 取便签调色盘里的颜色。[index] 越界时收敛到 0，避免脏数据把 UI 打挂
 * （颜色可能来自旧库或手改的备份文件）。
 */
@Composable
fun notePaletteColor(index: Int): Color {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = if (isDark) NotePaletteDark else NotePaletteLight
    return palette[index.coerceIn(0, palette.lastIndex)]
}

/** 整个调色盘。调色盘 UI 用它铺格子，顺序即色号 */
@Composable
fun notePalette(): List<Color> {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return if (isDark) NotePaletteDark else NotePaletteLight
}

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
