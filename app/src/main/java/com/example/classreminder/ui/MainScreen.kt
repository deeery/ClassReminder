package com.example.classreminder.ui

import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp as lerpColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp as lerpDp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp as lerp
import androidx.compose.ui.zIndex
import com.example.classreminder.ClassReminderService
import com.example.classreminder.R
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.MainViewModel
import com.example.classreminder.data.NoteEntity
import com.example.classreminder.data.TimeAxis
import com.example.classreminder.data.TodaySchedule
import com.example.classreminder.data.WeekSchedule
import com.example.classreminder.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val dayLabel = mapOf(
    "Monday" to "周一", "Tuesday" to "周二", "Wednesday" to "周三",
    "Thursday" to "周四", "Friday" to "周五", "Saturday" to "周六", "Sunday" to "周日"
)

// ── 统一的圆角/描边，避免每个组件各写各的 ─────────────────────────
// 这几个是跨文件共享的（TodayScreen.kt 也用同一套），所以是 internal 而不是 private ——
// Kotlin 的 `private` 在文件作用域是「本文件可见」，别的文件拿不到。
/** 卡片圆角半径。单独抽成 Dp 是因为自绘描边要拿它当 `CornerRadius` 用 */
internal val SHAPE_CARD_RADIUS = 14.dp
internal val SHAPE_CARD = RoundedCornerShape(SHAPE_CARD_RADIUS)
internal val SHAPE_SMALL_RADIUS = 8.dp
internal val SHAPE_SMALL = RoundedCornerShape(SHAPE_SMALL_RADIUS)
private val SHAPE_CHIP = RoundedCornerShape(50)

/** 课表列表卡片：竖条占位宽度 */
private val CARD_BAR_GAP = 8.dp

/** 课表列表卡片：内容区的垂直内边距。自绘竖条要用它算上下内缩 */
private val CARD_V_PADDING = 11.dp

/** 普通卡片用一条极淡的描边代替阴影，深色浅色都干净 */
@Composable
private fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

// ── 统一动效 ────────────────────────────────────────────────────
// 全应用的过渡时长都收在这里，风格才一致：入场稍慢、出场更快（避免两层长时间同时可见）。
// tween 的默认缓动就是 FastOutSlowInEasing，所以这里不再逐个指定。

/** 入场时长：淡入 / 展开 / 位移。跨文件共享，见上方 SHAPE_CARD 的说明 */
internal const val ENTER_MS = 240

/** 出场时长：比入场快一档 */
private const val EXIT_MS = 160

/**
 * 高亮「亮起来」的时长。比一般的入场稍慢一点，因为高亮要同时带动底色、描边、
 * 竖条、光晕好几样属性，太快会像闪光，慢一点才看得出「层次」。
 */
private const val SELECT_ENTER_MS = 280

/** 高亮「收回去」的时长。刻意比亮起快 —— 取消选中是「撤销」，不该拖泥带水 */
private const val SELECT_EXIT_MS = 180

/** 悬停反馈时长：只要「跟手」，不要「表演」，所以取最短的一档 */
private const val HOVER_MS = 120

/** 点按时整块缩到的比例 */
private const val PRESS_SCALE = 0.97f

/**
 * 选中项「独立扩大」的倍数。
 * 用 `graphicsLayer` 缩放实现 —— 它不参与布局，所以是**原地长大、不挤开邻居**，
 * 比改尺寸（会推着别人动、还要重排文字）便宜得多。
 *
 * 1.05 是「不被列表左右内边距裁掉」的上限：便签行左右各留 12dp（行宽约 336dp），
 * 课表卡片左右各留 10dp（卡宽约 340dp），再多一点卡片就会被裁边。
 */
private const val SELECTED_SCALE = 1.05f

/**
 * 选中态统一用**一条** 0 → 1 的进度驱动。
 *
 * 底色、色条、文字色、描边颜色与粗细、阴影、放大倍数全部由它 `lerp` 出来。
 * 好处有两条：
 *  - 一个条目只跑一条动画。原来每个属性各跑一条 `animate*AsState`，六条动画各自独立地
 *    逐帧失效重组，条目一多帧数就顶不住
 *  - 各属性天然同步 —— 原来六条动画时长一样但各自启动，观感上会有细微的「错拍」
 *
 * 进 / 出**刻意用不同时长**：亮起来稍慢（让人看清是哪一条），收回去更快（不拖泥带水）。
 * 这正是 Material 里 enter / exit 分开设的用意，比两个方向都用一个时长更「跟手」。
 */
@Composable
private fun rememberSelectionProgress(selected: Boolean): Float {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        // 用 lambda 按目标值取时长，切换方向时才不用重建动画
        animationSpec = tween(delayMillis = 0, durationMillis = if (selected) SELECT_ENTER_MS else SELECT_EXIT_MS),
        label = "selection"
    )
    return progress
}

/**
 * 悬停进度。桌面 / 触控板上鼠标移到某一条上时，提前把「点下去会选中谁」画出来，
 * 点之前就有反馈。移动端没有悬停，这条进度恒为 0，不产生任何开销。
 */
@Composable
private fun rememberHoverProgress(interaction: MutableInteractionSource): Float {
    val hovering by interaction.collectIsHoveredAsState()
    return animateFloatAsState(
        targetValue = if (hovering) 1f else 0f,
        animationSpec = tween(HOVER_MS),
        label = "hover"
    ).value
}

/** 按下反馈的规格。用短 tween 而不是弹簧：弹簧会来回震荡好几帧，帧数不好预测 */
private fun pressSpec() = tween<Float>(120)

// ── 高亮线条规范 ────────────────────────────────────────────────
//
// 高亮态原本是「一条 5dp 直角色块 + 一条 1dp 极淡描边」，问题出在两处：
//  1. 色块是 `drawRect` 的直角矩形，压在 14dp 圆角的卡片里，四个角被切出豁口 —— 一眼「糙」
//  2. 描边用 `BorderStroke` 画在卡片边界上，和内层内容的圆角不是同一份几何，
//     加上纯色平涂、没有光感，高亮时卡片轮廓并没有真正「立」起来
//
// 所以这里把「高亮线条」统一成一个只画**几何**的函数：竖条、描边、外光晕都由它一笔画出，
// 全部走 `drawRoundRect`（圆角天然抗锯齿），并且四边内缩、和卡片圆角同心。
// 颜色 / 宽度 / 透明度则由 [HighlightSpec] 算好后传进来。

/** 竖条满宽（=「选中」那一级的宽度）。配合圆角和内缩才显得「精致」而不是「一根粗杠」 */
private val HL_BAR_WIDTH = 4.dp

/** 竖条两端圆角半径（= 宽度的一半，画出来是颗胶囊） */
private val HL_BAR_RADIUS = 2.dp

/**
 * 竖条到卡片边缘的内缩。
 * 必须大于卡片的圆角半径与描边宽度之和，竖条才会完全落在圆角之外 —— 否则仍会被圆角切到。
 * 14dp 圆角的卡片里，6dp 已经能保证竖条的圆角完整可见。
 */
private val HL_INSET = 6.dp

/** 选中描边宽度。1.6dp 在 xhdpi 以上是整数像素，渲染出来是「细而实」的一条，不会发虚 */
private val HL_BORDER_WIDTH = 1.6.dp

/** 外光晕的宽度（向外扩出的距离） */
private val HL_GLOW_WIDTH = 3.dp

/**
 * 一条高亮所画的三样东西。
 *
 * 竖条 / 描边 / 光晕**共用同一份圆角几何**，所以永远同心、不会有错位感。
 * 调用方只管把颜色与宽度算好（一般来自 [HighlightSpec.of]）。
 */
private data class HighlightLines(
    /** 左侧竖条的填充。用 `Brush` 而不是 `Color` 是为了能喂渐变 */
    val barBrush: Brush,
    /** 竖条宽度。降到 0 就整条不画 */
    val barWidth: Dp,
    /** 竖条两端圆角半径，一般取宽度的一半（画出来是颗胶囊） */
    val barRadius: Dp = HL_BAR_RADIUS,
    /** 描边颜色。透明度极低时不画 */
    val borderColor: Color = Color.Transparent,
    /** 描边宽度。1.6dp 在 xhdpi 以上是整数像素，渲染出来「细而实」，不会发虚 */
    val borderWidth: Dp = 0.dp,
    /** 外光晕颜色。向外扩一圈低透明主色，相当于给卡片打了一层柔光 */
    val glowColor: Color = Color.Transparent,
    /** 外光晕向外扩出的距离。只被 [glowColor] 用到 */
    val glowWidth: Dp = 3.dp
)

/**
 * 画一组高亮线条。竖条 / 描边 / 光晕共用同一份圆角几何。
 *
 * [barTop] / [barHeight]：竖条的上下端点**由调用方按卡片实际高度算好**。
 * 这里不用 `size.height`，因为卡片外层的 `padding` 会让 DrawScope 比卡片本身高，
 * 直接用 `size` 会把竖条画到卡片外面去。
 *
 * 宽度或透明度降到 0 的那几样直接跳过，省掉无意义的绘制。
 */
private fun DrawScope.drawHighlightLines(
    cornerRadius: Dp,
    lines: HighlightLines,
    barTop: Float,
    barHeight: Float
) {
    val corner = cornerRadius.toPx()
    val glow = lines.glowWidth.toPx()
    val border = lines.borderWidth.toPx()
    val inset = HL_INSET.toPx()
    val barW = lines.barWidth.toPx()
    val barR = lines.barRadius.toPx()

    // 光晕：在卡片外侧再画一圈同圆角的描边。线宽居中在路径上，所以左偏移正好是 glow
    if (glow > 0f && lines.glowColor.alpha > 0.01f) {
        drawRoundRect(
            color = lines.glowColor,
            topLeft = Offset(-glow, -glow),
            size = Size(size.width + glow * 2f, size.height + glow * 2f),
            cornerRadius = CornerRadius(corner + glow),
            style = Stroke(width = glow * 2f)
        )
    }

    // 描边：不传 size，默认就是当前 DrawScope 的尺寸，正好压在卡片边界上
    if (border > 0f && lines.borderColor.alpha > 0.01f) {
        drawRoundRect(
            color = lines.borderColor,
            cornerRadius = CornerRadius(corner),
            style = Stroke(width = border)
        )
    }

    // 竖条：圆角 + 内缩，避免被卡片圆角切出直角豁口
    if (barHeight > 0f && barW > 0f) {
        drawRoundRect(
            brush = lines.barBrush,
            topLeft = Offset(inset, barTop),
            size = Size(barW, barHeight),
            cornerRadius = CornerRadius(barR)
        )
    }
}

// ── 高亮强度分级 ────────────────────────────────────────────────
//
// 一屏里可能同时出现好几种「亮着」：
//  - 今天那一列（与手动选择无关，只是提示「这是今天」）
//  - 鼠标悬停到的那一条（预览「点下去会选中谁」）
//  - 手指按住的那一条（正在发生的动作）
//  - 手动选中的那一条（当前焦点）
//  - 正在上课的那一条（时间维度上的「现在」）
//
// 如果都画成一个样子，重点就散了。所以按 **弱 → 中 → 强** 分三级，
// 每一级都给出固定的几何与透明度：弱级只有一条细竖条，中级加描边，强级才有光晕 + 抬升。
// 这样「谁更重要」是**一眼可见的，而不是要靠试**。

/** 竖条宽度：未高亮 → 弱 → 强。选中时反而更细（配合内缩 + 圆角才显精致，而不是「一根粗杠」） */
private const val BAR_W_IDLE = 0.30f
private const val BAR_W_WEAK = 0.56f
private const val BAR_W_STRONG = 1.00f

/** 描边宽度系数：未高亮只用一根极细的灰线，弱级略粗，强级实线 */
private const val BORDER_W_IDLE = 0.40f
private const val BORDER_W_WEAK = 0.62f
private const val BORDER_W_STRONG = 1.00f

/** 强级专属的外光晕透明度（浅色 / 深色各标定一次，见 [HighlightSpec]） */
private const val GLOW_A_LIGHT = 0.17f
private const val GLOW_A_DARK = 0.30f

/** 强级底色：主色容器与卡面混合的比例。深色下调高，否则「亮起来」在深背景上几乎看不出来 */
private const val TINT_MIX_LIGHT = 0.72f
private const val TINT_MIX_DARK = 0.88f

/** 强级标题色：向 `onPrimaryContainer` 靠拢的比例。深色下反而要小一点 —— 见下方注释 */
private const val TITLE_TINT_LIGHT = 0.85f
private const val TITLE_TINT_DARK = 0.55f

/**
 * 把「当前处于哪一级高亮」翻译成一组具体的几何与颜色。
 *
 * **一个条目只算一次**：四级强度先收敛成一个 0 → 1 的进度 [level]，再由它 `lerp` 出所有属性。
 * 好处是各属性天然同步（不会出现「底色到了、描边还在路上」的错拍），
 * 也避免了每个属性各跑一条 `animate*AsState` 导致逐帧重复失效重组。
 *
 * 下面所有颜色都按**当前主题**取值，并且透明度是**逐主题标定**的 —— 同一个 α 在浅色和深色
 * 底上的观感完全不同，写死一个值必然有一边看不清。
 */
private data class HighlightSpec(
    val barBrush: Brush,
    val barWidth: Dp,
    val borderColor: Color,
    val borderWidth: Dp,
    val glowColor: Color,
    val containerColor: Color,
    val titleColor: Color
) {
    companion object {
        /**
         * @param baseContainer 未高亮时的底色（临时课是暖黄、长期课是 surface）
         * @param baseTitle 未高亮时的标题色
         * @param selection 手动选中进度 0 → 1
         * @param accent 弱高亮（今天那一列）开关
         * @param isToday 这一项是不是「今天」。**决定选中色条走蓝还是走灰**：
         *   今日 = 主色（强调，对应用户说的「当前颜色」），非今日 = 中性灰。
         *   浏览多日课表时，一眼就能分出「这是今天选中的」和「这是别的天选中的」。
         * @param pressed 正被按住
         * @param hovering 正被悬停 / 聚焦
         */
        @Composable
        fun of(
            baseContainer: Color,
            baseTitle: Color,
            selection: Float,
            accent: Float = 0f,
            isToday: Boolean = true,
            pressed: Boolean = false,
            hovering: Boolean = false
        ): HighlightSpec {
            val scheme = MaterialTheme.colorScheme
            val primary = scheme.primary
            val isDark = scheme.surface.luminance() < 0.5f

            // ── 选中态的主色：今日 = 蓝（primary），非今日 = 灰 ──
            // 灰不能用 onSurface 调 α：深色下 surface 与 onSurface 只差 1.78:1，等于没铺。
            // 浅色走 outlineVariant 压深、深色走 surfaceVariant —— 都是「比背景深一档」的中性色，
            // 目的是让色条**可见但不抢眼**，把注意力留给真正今天的那个蓝条目。
            val selectColor = if (isToday) primary else {
                if (isDark) scheme.surfaceVariant else scheme.outlineVariant
            }

            // ── 四级强度收敛成一条 0 → 1 的顺序刻度 ──
            // 弱高亮(1) < 悬停(2) < 按下(3) < 选中(4)。
            // 用 maxOf 而不是相加：同时成立时取最强的那一级，不会出现「叠出第五种颜色」
            val weak = accent.coerceIn(0f, 1f)
            val hover = if (hovering) 1f else 0f
            val down = if (pressed) 1f else 0f
            val level = maxOf(weak * 0.30f, hover * 0.46f, down * 0.62f, selection)

            val strong = selection

            // ── 几何：按 level 在「未高亮 → 强」之间插值 ──
            // 竖条宽度还要再走一档：弱高亮（今天那一列）固定取「弱」的宽度，
            // 而不是跟着 level 滑动 —— 否则今天列会随着悬停 / 按下一起变宽，看起来像选中了
            val idleBar = HL_BAR_WIDTH * BAR_W_IDLE
            val strongBar = HL_BAR_WIDTH * BAR_W_STRONG
            val weakBar = HL_BAR_WIDTH * BAR_W_WEAK
            val barWidth = if (weak > 0.5f && selection < 0.01f) weakBar
            else lerpDp(idleBar, strongBar, level)
            val borderWidth = lerpDp(1.dp * BORDER_W_IDLE, HL_BORDER_WIDTH, level)

            // ── 描边色：浅色下走中性灰（主题的 outline，对白卡片 1.76:1；
            //    原来用 α0.07 的 onSurface 只有 1.05:1，等于没有）。
            //    深色下不能走灰 —— onSurface 是浅灰、surface 是深灰，两者只差 1.78:1，
            //    在深底上「悄悄出现一条灰线」根本看不见，所以改走主色，靠透明度给强度。
            //    注意终点用 selectColor：选中态下今日描蓝边、非今日描灰边，与竖条同色系 ──
            val borderColor = if (isDark) {
                lerpColor(
                    scheme.outline.copy(alpha = 1f),
                    selectColor.copy(alpha = lerp(0.30f, 0.72f, level)),
                    level
                )
            } else {
                lerpColor(scheme.outline.copy(alpha = 0.78f), selectColor, level)
            }

            // ── 竖条：未高亮只有在弱高亮时才显形；选中后换成纵向渐变（有光感、有方向）。
            //    颜色跟随 selectColor —— 今日走蓝、非今日走灰 ──
            val barBrush: Brush = when {
                level > 0.01f -> Brush.verticalGradient(
                    colors = listOf(
                        selectColor.copy(alpha = lerp(0.55f, 1.00f, level)),
                        selectColor.copy(alpha = lerp(0.35f, 0.48f, level))
                    )
                )
                weak > 0f -> SolidColor(selectColor.copy(alpha = 0.30f))
                else -> SolidColor(selectColor.copy(alpha = 0.16f))
            }

            // ── 底色：选中时向 primaryContainer 混合。深色下的 primaryContainer(0xFF16406B)
            //    本身太暗，混 72% 也只比 surface 亮一点点，卡片「亮起来」几乎不可见 —— 所以调高比例。
            //    非今日不走 primaryContainer（那是蓝的），改用 selectColor 弱混 ——
            //    灰条目的底只轻微加深一点，把「蓝 = 今天」这个信号独占给今日项 ──
            val containerColor = if (isToday) {
                lerpColor(
                    baseContainer,
                    primaryContainerFor(scheme, isDark),
                    selection * if (isDark) TINT_MIX_DARK else TINT_MIX_LIGHT
                )
            } else {
                lerpColor(
                    baseContainer,
                    selectColor.copy(alpha = 1f),
                    selection * if (isDark) 0.22f else 0.30f
                )
            }

            // ── 标题色：浅色下加深（onPrimaryContainer 是深蓝，和浅蓝底对比 4.5:1 以上）；
            //    深色下如果让它去靠近 onPrimaryContainer(0xFFD6E4FF) 反而会**变亮**、和底色的
            //    对比不升反降，所以深色只轻微靠拢，靠底色变深来拉开对比。
            //    非今日不染蓝：保持原本的主文字色，靠色条和描边表达「选中」即可 ──
            val titleColor = if (isToday) {
                lerpColor(
                    baseTitle,
                    scheme.onPrimaryContainer,
                    selection * if (isDark) TITLE_TINT_DARK else TITLE_TINT_LIGHT
                )
            } else {
                baseTitle
            }

            return HighlightSpec(
                barBrush = barBrush,
                barWidth = barWidth,
                borderColor = borderColor,
                borderWidth = borderWidth,
                // 光晕跟竖条同色：非今日是灰条目，发蓝光会显得「两张卡不是一套」。
                // α 比主色光晕略收 —— 灰本身不抢眼，光晕太亮反而突兀
                glowColor = selectColor.copy(
                    alpha = (if (isDark) GLOW_A_DARK else GLOW_A_LIGHT) * strong *
                        (if (isToday) 1f else 0.7f)
                ),
                containerColor = containerColor,
                titleColor = titleColor
            )
        }

        /** 深色主题下容器色要单独抬一档亮度，否则「选中」在深背景上读不出来 */
        private fun primaryContainerFor(
            scheme: androidx.compose.material3.ColorScheme,
            isDark: Boolean
        ): Color = if (isDark) scheme.primaryContainer.copy(alpha = 1f) else scheme.primaryContainer
    }
}

/** 亮度（0=黑 1=白）。用来判断当前是浅色还是深色主题，比传一个 Boolean 下去更难用错 */
private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/**
 * 把「按下 → 缩小、松手弹回」单独抽出来。
 * 自带 interactionSource 的组件（FAB 等）可以直接把它们的 source 传进来复用同一套手感。
 */
@Composable
private fun rememberPressScale(
    interaction: MutableInteractionSource,
    pressedScale: Float = PRESS_SCALE
): Float {
    val pressed by interaction.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = pressSpec(),
        label = "pressScale"
    ).value
}

/**
 * 高亮交互统一走 [clickable] + `MutableInteractionSource`（见 [HighlightSpec]）：
 * 同一个 source 同时喂「悬停」和「按下」，两档反馈来自同一处状态，
 * 不会出现「悬停亮了、按下反而灭掉」这种自相矛盾。
 * 缩放由 [rememberPressScale] 单独提供，只给真正需要「压下去」的大块元素用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportTimetable: () -> Unit
) {
    val ctx = LocalContext.current
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    val notes by viewModel.notes.collectAsState(initial = emptyList())
    val canUndo by viewModel.canUndo.collectAsState()
    val serviceRunning by ClassReminderService.running.collectAsState()
    var editing by remember { mutableStateOf<ClassEntity?>(null) }
    var deleting by remember { mutableStateOf<ClassEntity?>(null) }
    var addingKind by remember { mutableStateOf<AddKind?>(null) }
    // 便签：编辑中的那条 + 是否正在新增 + 当前高亮选中的那条
    var editingNote by remember { mutableStateOf<NoteEntity?>(null) }
    var addingNote by remember { mutableStateOf(false) }
    var selectedNoteId by remember { mutableStateOf<Int?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 0=今天, 1=课表, 2=便签, 3=设置；启动时接着上次停留的非设置页
    var selectedTab by remember { mutableStateOf(Prefs.getLastTab(ctx)) }
    // 「今天」页的日期副标题要跟着走字，所以这里也留一个每 30 秒刷新一次的「现在」。
    // 和 TodayScreen 内部那份是分开的：顶栏属于 Scaffold，没法读 TodayScreen 的局部状态。
    var clockNow by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(selectedTab) {
        // 只在今天页走时钟，别的页不需要每 30 秒重组一次顶栏
        while (selectedTab == 0) {
            delay(30_000L)
            clockNow = System.currentTimeMillis()
        }
    }
    // 课表的显示模式：既要在切 Tab 时不丢，也要在下次打开时沿用
    var weekMode by remember { mutableStateOf(if (Prefs.isWeekGrid(ctx)) WeekMode.GRID else WeekMode.LIST) }
    // 实验性：时间轴网格课表。关掉后课表换成 v2.0 那版「按天分组、可折叠」的列表渲染。
    // 放在这里（而不是只读 Prefs）是为了让设置页一拨开关课表立刻跟着换。
    var experimentalGrid by remember { mutableStateOf(Prefs.isExperimentalGrid(ctx)) }

    // 周次校准：存的是「第 1 周的周一」，改了它课表和提醒都会跟着变
    var week1Monday by remember { mutableStateOf(Prefs.getWeek1Monday(ctx)) }
    var showCalibrate by remember { mutableStateOf(false) }
    val currentWeek = remember(week1Monday) {
        if (week1Monday == 0L) null else WeekSchedule.weekNumber(week1Monday, System.currentTimeMillis())
    }
    // 课表正在浏览第几周：也提到这里，切 Tab 回来还停在原来那周（重新校准会跟着回到本周）
    var shownWeek by remember(currentWeek) { mutableStateOf(currentWeek ?: 1) }

    // 搜索过滤（便签页按便签内容匹配）
    val filteredNotes = remember(searchQuery, notes) {
        if (searchQuery.isBlank()) notes
        else notes.filter { it.text.contains(searchQuery, ignoreCase = true) }
    }

    // 选中的便签。被删掉或搜索过滤掉时它会自动变回 null，左下角操作区随之消失
    val selectedNote = remember(selectedNoteId, notes) {
        selectedNoteId?.let { id -> notes.firstOrNull { it.id == id } }
    }

    // 切页或改搜索词时清掉选中，免得左下角操作区对着一条已经看不见的便签
    LaunchedEffect(selectedTab, searchQuery) { selectedNoteId = null }

    Scaffold(
        topBar = {
            // 顶栏在「今天」和「便签」两页存在。这里**故意不给它做高度动画** ——
            // Scaffold 的 contentPadding.top 直接取顶栏的测量高度（见 Scaffold.kt：
            // `if (topBarPlaceables.isEmpty()) insets.calculateTopPadding() else topBarHeight`），
            // 高度一动，整屏每帧都要换一套约束：课表网格每帧重组、每个课程块每帧重新排版
            // （文字布局缓存的 key 里带 constraints，高度变了就不复用），切页帧率就是这么掉的。
            // 改成随内容一起切换，代价只是没有折叠动画。
            if (selectedTab == 0) {
                // ── 今天页：大标题 + 日期副标题 ──
                // 副标题承载「9 月 29 日 周二 · 第 5 周」这类信息，
                // 顶栏有一行小字，正文就不用再重复一遍日期。
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .statusBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 10.dp)
                ) {
                    Text(
                        text = "今天",
                        fontSize = 26.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = todaySubtitle(currentWeek, clockNow),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (selectedTab == 2) {
                TopAppBar(
                    title = {
                        // 标题 ↔ 搜索框：淡入淡出 + 从左侧轻微放大，锚点放在左端
                        AnimatedContent(
                            targetState = isSearchOpen,
                            transitionSpec = {
                                (fadeIn(tween(ENTER_MS)) +
                                    scaleIn(tween(ENTER_MS), initialScale = 0.94f, transformOrigin = TransformOrigin(0f, 0.5f))) togetherWith
                                    (fadeOut(tween(EXIT_MS)) +
                                        scaleOut(tween(EXIT_MS), targetScale = 0.94f, transformOrigin = TransformOrigin(0f, 0.5f)))
                            },
                            label = "searchToggle"
                        ) { open ->
                            if (open) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("搜索便签...", fontSize = 13.sp) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 50.dp)
                                        .border(
                                            width = 1.5.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                                            shape = RoundedCornerShape(4.dp)
                                        ),
                                    shape = RoundedCornerShape(4.dp),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = 13.sp
                                    )
                                )
                            } else {
                                Text("快速便签", modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        // 放大镜 ↔ 关闭：交叉淡入 + 缩放，图标不是「啪」地换掉
                        AnimatedContent(
                            targetState = isSearchOpen,
                            transitionSpec = {
                                (fadeIn(tween(ENTER_MS)) + scaleIn(tween(ENTER_MS), initialScale = 0.6f)) togetherWith
                                    (fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = 0.6f))
                            },
                            contentAlignment = Alignment.Center,
                            label = "searchAction"
                        ) { open ->
                            if (open) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    isSearchOpen = false
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                                }
                            } else {
                                IconButton(onClick = { isSearchOpen = true }) {
                                    Icon(Icons.Default.Search, contentDescription = "搜索")
                                }
                            }
                        }
                    }
                )
            }
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                isRunning = serviceRunning,
                currentWeek = currentWeek,
                onTabSelected = { tab ->
                    selectedTab = tab
                    // 离开课表页就把展开的添加菜单收起来，免得切回来还敞着
                    if (tab != 1) fabExpanded = false
                    // 只记非设置页（守卫在 Prefs 里）
                    Prefs.setLastTab(ctx, tab)
                }
            )
        },
        floatingActionButton = {
            // 设置页没有悬浮按钮。切 Tab 时它瞬时出现 / 消失，不做缩放淡入 ——
            // 和页面一样，切页那一帧已经很挤了，再叠过渡只会更卡。
            if (selectedTab != 3) {
                // 加号是点得最多的按钮，按下反馈单独给它一份 interactionSource
                val fabInteraction = remember { MutableInteractionSource() }
                val fabScale = rememberPressScale(fabInteraction, pressedScale = 0.94f)
                Column(horizontalAlignment = Alignment.End) {
                    // ── 加号正上方那个槽位：便签页放「回撤」，课表页让给展开菜单，今天页空着 ──
                    if (selectedTab == 2) {
                        // 回撤只在真的有可撤销的操作时出现
                        AnimatedVisibility(visible = canUndo) {
                            Column(horizontalAlignment = Alignment.End) {
                                UndoButton(onClick = { viewModel.undoNote() })
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.End) {
                            Spacer(Modifier.height(12.dp))
                        }
                        // ── 课表页：加号展开的两个方块，从加号那一侧向上长出来，收起时缩回去 ──
                        AnimatedVisibility(
                            visible = fabExpanded,
                            enter = fadeIn(tween(ENTER_MS)) +
                                expandVertically(tween(ENTER_MS), expandFrom = Alignment.Bottom),
                            exit = fadeOut(tween(EXIT_MS)) +
                                shrinkVertically(tween(EXIT_MS), shrinkTowards = Alignment.Bottom)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                FabTile("临时提醒", Icons.Default.DateRange) {
                                    fabExpanded = false
                                    addingKind = AddKind.ONE_OFF
                                }
                                Spacer(Modifier.height(10.dp))
                                FabTile("长期提醒", Icons.AutoMirrored.Filled.List) {
                                    fabExpanded = false
                                    addingKind = AddKind.LONG_TERM
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                        }
                    }
                    FloatingActionButton(
                        onClick = {
                            // 便签页 = 添加便签；课表页 = 展开 / 收起添加菜单；
                            // 今天页 = 直接进「添加课程 / 提醒」对话框（这页没有可展开的二级项）
                            when (selectedTab) {
                                1 -> fabExpanded = !fabExpanded
                                2 -> addingNote = true
                                else -> addingKind = AddKind.LONG_TERM
                            }
                        },
                        modifier = Modifier.graphicsLayer {
                            scaleX = fabScale
                            scaleY = fabScale
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        interactionSource = fabInteraction
                    ) {
                        // 加号 ↔ 关闭：交叉淡入 + 缩放。两个图标同尺寸，切换时不会跳
                        AnimatedContent(
                            targetState = selectedTab == 1 && fabExpanded,
                            transitionSpec = {
                                (fadeIn(tween(ENTER_MS)) + scaleIn(tween(ENTER_MS), initialScale = 0.6f)) togetherWith
                                    (fadeOut(tween(EXIT_MS)) + scaleOut(tween(EXIT_MS), targetScale = 0.6f))
                            },
                            contentAlignment = Alignment.Center,
                            label = "fabIcon"
                        ) { open ->
                            Icon(
                                if (open) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = when {
                                    open -> "收起添加菜单"
                                    selectedTab == 2 -> "添加便签"
                                    selectedTab == 1 -> "添加提醒"
                                    else -> "添加课程"
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        // 用 Box 而不是 Column：左下角操作区要浮在内容之上，得靠 align 定位
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 四个页面之间**瞬时切换**，不做过渡动画：
            // 切页本身就要组合出新的一屏（课表那屏很重），再叠加过渡只会让这一帧更挤。
            when (selectedTab) {
                0 -> TodayScreen(
                    classes = classes,
                    notes = notes,
                    currentWeek = currentWeek,
                    onOpenClass = { editing = it },
                    onOpenNotes = {
                        selectedTab = 2
                        Prefs.setLastTab(ctx, 2)
                    },
                    modifier = Modifier.fillMaxSize()
                )
                1 -> WeekView(
                    classes = classes,
                    currentWeek = currentWeek,
                    week1Monday = week1Monday,
                    mode = weekMode,
                    onModeChange = {
                        weekMode = it
                        Prefs.setWeekGrid(ctx, it == WeekMode.GRID)
                    },
                    shownWeek = shownWeek,
                    onShownWeekChange = { shownWeek = it },
                    onCalibrate = { showCalibrate = true },
                    onEdit = { editing = it }
                )
                2 -> NoteListView(
                    notes = filteredNotes,
                    searching = searchQuery.isNotBlank(),
                    selectedId = selectedNote?.id,
                    onSelect = { note ->
                        // 再点一次同一条就取消选中
                        selectedNoteId = if (selectedNoteId == note.id) null else note.id
                    },
                    onDeselect = { selectedNoteId = null },
                    onEdit = { editingNote = it },
                    onDelete = { note ->
                        viewModel.deleteNote(note.id)
                        if (selectedNoteId == note.id) selectedNoteId = null
                    },
                    onMove = { from, to -> viewModel.moveNote(from, to) },
                    onAdd = { addingNote = true }
                )
                else -> SettingsPage(
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenSettings = onOpenSettings,
                    onImportTimetable = onImportTimetable
                )
            }

            // 选中便签时，左下角浮出「编辑 / 删除」，尺寸和右下角的按钮一致。
            // 用 AnimatedContent 以「选中的那条便签」为目标：退场动画期间它仍拿得到那一条，不会读到 null
            AnimatedContent(
                targetState = if (selectedTab == 2) selectedNote else null,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp),
                transitionSpec = {
                    (fadeIn(tween(ENTER_MS)) +
                        scaleIn(tween(ENTER_MS), initialScale = 0.8f, transformOrigin = TransformOrigin(0f, 1f)) +
                        slideInVertically(tween(ENTER_MS)) { it / 5 }) togetherWith
                        (fadeOut(tween(EXIT_MS)) +
                            scaleOut(tween(EXIT_MS), targetScale = 0.8f, transformOrigin = TransformOrigin(0f, 1f)) +
                            slideOutVertically(tween(EXIT_MS)) { it / 5 })
                },
                contentAlignment = Alignment.BottomStart,
                label = "noteActions"
            ) { note ->
                if (note != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NoteActionButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "编辑便签",
                            tint = MaterialTheme.colorScheme.primary
                        ) { editingNote = note }
                        NoteActionButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "删除便签",
                            tint = MaterialTheme.colorScheme.error
                        ) {
                            viewModel.deleteNote(note.id)
                            selectedNoteId = null
                        }
                    }
                }
            }
        }
    }

    addingKind?.let { kind ->
        AddEditDialog(
            initial = null,
            oneOff = kind == AddKind.ONE_OFF,
            onSave = { new ->
                viewModel.save(new)
                addingKind = null
            },
            onDismiss = { addingKind = null }
        )
    }

    editing?.let { target ->
        AddEditDialog(
            initial = target,
            // 已有日期的就是临时提醒
            oneOff = !target.date.isNullOrEmpty(),
            onSave = { updated ->
                viewModel.save(updated)
                editing = null
            },
            onDelete = {
                deleting = target
                editing = null
            },
            onDismiss = { editing = null }
        )
    }

    deleting?.let { target ->
        DeleteConfirmDialog(
            target = target,
            onConfirm = {
                viewModel.delete(target)
                deleting = null
            },
            onDismiss = { deleting = null }
        )
    }

    // ── 便签的添加 / 编辑 ──
    if (addingNote) {
        NoteEditDialog(
            initial = null,
            onSave = { text ->
                // 有高亮选中的便签就插到它上方，否则照旧置顶
                viewModel.addNote(text, aboveNoteId = selectedNote?.id)
                addingNote = false
            },
            onDismiss = { addingNote = false }
        )
    }

    editingNote?.let { target ->
        NoteEditDialog(
            initial = target,
            onSave = { text ->
                viewModel.updateNote(target.id, text)
                editingNote = null
            },
            onDismiss = { editingNote = null }
        )
    }

    if (showCalibrate) {
        CalibrateWeekDialog(
            currentWeek = currentWeek,
            onConfirm = { week ->
                val monday = WeekSchedule.mondayOf(System.currentTimeMillis()) -
                    (week - 1).toLong() * 7L * 24 * 60 * 60 * 1000
                Prefs.setWeek1Monday(ctx, monday)
                week1Monday = monday
                showCalibrate = false
            },
            onDismiss = { showCalibrate = false }
        )
    }
}

// ── 周次校准对话框 ──────────────────────────────────────────────

@Composable
fun CalibrateWeekDialog(currentWeek: Int?, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(currentWeek?.toString() ?: "1") }
    val week = text.trim().toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("校准周数") },
        text = {
            Column {
                Text(
                    "填「本周是第几周」，应用就能算出整学期的周次，并按周次显示课表和发提醒。",
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("本周是第几周") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (week == null) {
                    Spacer(Modifier.height(6.dp))
                    Text("请填 1~30 之间的整数", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { week?.let(onConfirm) },
                enabled = week != null && week in 1..30
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 添加方式 ────────────────────────────────────────────────────

/** 长期提醒 = 每周重复的课；临时提醒 = 指定日期、只生效一次 */
enum class AddKind { LONG_TERM, ONE_OFF }

/** FAB 展开后的方块按钮 */
@Composable
private fun FabTile(label: String, icon: ImageVector, onClick: () -> Unit) {
    // 按下反馈要挂在外层 Surface 上：挂到里面的 Column 上只会缩图标和文字，卡片底不动
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    val hover = rememberHoverProgress(interaction)
    // 悬停时描边染上主色，和列表卡片的「选中」用同一种语言（只是弱很多）
    val borderColor by animateColorAsState(
        targetValue = if (hover > 0.5f) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f),
        animationSpec = tween(HOVER_MS),
        label = "tileBorder"
    )
    Surface(
        shape = SHAPE_CARD,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 6.dp,
        modifier = Modifier
            .size(84.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = interaction,
                    indication = LocalIndication.current,
                    onClick = onClick
                )
                .padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
    }
}

// ── 删除确认 ────────────────────────────────────────────────────

@Composable
fun DeleteConfirmDialog(target: ClassEntity, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("删除课程") },
        text = {
            Column {
                Text("「${target.title}」将被删除，无法撤销。", fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${dayLabel[target.dayOfWeek] ?: target.dayOfWeek} ${target.startTime} - ${target.endTime}" +
                        if (target.room.isNotBlank()) " · ${target.room}" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ── 底部导航栏 ──────────────────────────────────────────────────

data class BottomNavItem(val label: String, val icon: ImageVector)

@Composable
fun BottomNavigationBar(
    selectedTab: Int,
    isRunning: Boolean,
    currentWeek: Int?,
    onTabSelected: (Int) -> Unit
) {
    val items = listOf(
        BottomNavItem("今天", Icons.Default.Home),
        BottomNavItem("课表", Icons.Default.DateRange),
        BottomNavItem("便签", Icons.Default.Edit),
        BottomNavItem("设置", Icons.Default.Settings)
    )
    // 运行中 ↔ 未启动之间切换时颜色做过渡，不是硬切。
    // 这里刻意不用 rememberInfiniteTransition 做「呼吸」：那是一条永不停止的动画，
    // 会让整个 App 一帧都闲不下来（一直占着帧时钟），得不偿失。
    val dotColor by animateColorAsState(
        targetValue = if (isRunning) Color(0xFF4CAF50) else Color(0xFFE53935),
        animationSpec = tween(ENTER_MS),
        label = "serviceDotColor"
    )

    Column {
        // ── 状态指示行 ──
        // 服务状态是「系统级」信息，不参与导航。用一条 outlineVariant 细线和导航区分开，
        // 底色比导航栏浅一档（surfaceVariant 半透明），让它看起来是「附属在上面」而不是同级
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(dotColor, CircleShape)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = buildString {
                    append(if (isRunning) "提醒服务运行中" else "提醒服务未启动")
                    currentWeek?.let { append(" · 第 $it 周") }
                },
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // ── 导航项 ──
        // containerColor 用 surface（而不是默认的 surfaceContainer）：本项目整体是「白底 + 极淡描边」
        // 的 Google 扁平风，导航栏再抬一层灰会显得脏。分隔靠上面那条细线
        NavigationBar(
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label, fontSize = 11.sp) },
                    // M3 的选中指示器是 primaryContainer 胶囊 + primary 图标/文字
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

// ── 快速便签 ────────────────────────────────────────────────────

/**
 * 单行便签的默认高度。
 *
 * 最初按原课程卡片（ClassCard）单行高度约 78dp 的 67% 取 52dp；后来为了让点按更从容、
 * 也让左滑露出的编辑按钮不至于太扁，上调到 60dp。
 * 内容折成两行时会自然变高，60dp 只是下限。
 */
private val NOTE_ROW_HEIGHT = 60.dp

/** 便签卡片左右留白的宽度；滑出动画要把它一起算进去，否则卡片会残留一条边 */
private val NOTE_ROW_H_PADDING = 12.dp

/** 左滑露出的编辑按钮宽度。高度跟行高走，所以它是个比正方形略宽的圆角矩形 */
private val SWIPE_EDIT_WIDTH = 78.dp

/** 左滑露出时，卡片右边缘与编辑按钮之间留出的空隙，免得两块贴在一起 */
private val SWIPE_EDIT_GAP = 8.dp

/** 拖动过程中最多能让出「行宽 × 这个比例」，再往后的距离交给松手后的滑出动画 */
private const val SWIPE_MAX_DRAG_FRACTION = 0.6f

/** 左滑超过「行宽 × 这个比例」算大幅度左滑，直接删除。值越小越容易触发 */
private const val SWIPE_DISMISS_FRACTION = 0.35f

/** 左滑超过「露出距离 × 这个比例」就吸附到露出编辑按钮。值越小越容易触发 */
private const val SWIPE_REVEAL_TRIGGER = 0.3f

/** 长按拖动时整行放大的倍数 */
private const val DRAG_SCALE = 1.04f

/**
 * 便签列表。
 *
 * 长按任意一栏即可整条上下拖动。手势挂在每一行上，但拖动一旦开始，这一行就会持续接管指针事件，
 * 手指移出该行范围后依然能继续拖，等效于「在整个便签界面内拖动排序」。
 * 单击是「选中」（高亮），不是编辑；点在没被任何便签接住的空白处则取消选中。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteListView(
    notes: List<NoteEntity>,
    searching: Boolean,
    selectedId: Int?,
    onSelect: (NoteEntity) -> Unit,
    onDeselect: () -> Unit,
    onEdit: (NoteEntity) -> Unit,
    onDelete: (NoteEntity) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit
) {
    if (notes.isEmpty()) {
        EmptyNotes(searching = searching, onAdd = onAdd)
        return
    }

    val listState = rememberLazyListState()
    // 正在被拖动的行下标；null = 当前没有拖动
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    // 手指的累计位移（px）
    var dragDelta by remember { mutableStateOf(0f) }
    // 拖动开始时该行在视口里的 offset，用来换算「手指期望它待在哪儿」
    var dragStartOffset by remember { mutableStateOf(0) }
    // 同一时间只允许一栏处于「已划开、露出编辑按钮」的状态
    var revealedId by remember { mutableStateOf<Int?>(null) }

    // 被拖行的视觉位移 = 期望位置 - 它当前实际的布局位置。
    // 中途换了下标（排序真的生效了）时两者一起变，所以行不会跳一下。
    val draggedOffset = draggingIndex?.let { index ->
        listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
            ?.let { (dragStartOffset + dragDelta - it.offset).roundToInt() }
            ?: 0
    } ?: 0

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            // 点空白处取消选中。便签自己会把点击消费掉，所以这里只接住「没人要的」点击
            .pointerInput(Unit) {
                detectTapGestures { onDeselect() }
            },
        contentPadding = PaddingValues(
            top = 4.dp,
            // 选中时左下角会浮出操作区，给列表底部留出避让空间，别把最后一条压住
            bottom = if (selectedId != null) 84.dp else 4.dp
        )
    ) {
        itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
            val isDragging = draggingIndex == index
            NoteRow(
                note = note,
                dragging = isDragging,
                selected = selectedId == note.id,
                dragOffsetY = if (isDragging) draggedOffset else 0,
                revealed = revealedId == note.id,
                onSelect = { onSelect(note) },
                onReveal = { revealedId = note.id },
                onClose = { if (revealedId == note.id) revealedId = null },
                // 搜索时列表是过滤后的子集，下标和整表对不上，这时不开放拖动
                onDragStart = {
                    if (!searching) {
                        draggingIndex = index
                        dragDelta = 0f
                        dragStartOffset = listState.layoutInfo.visibleItemsInfo
                            .firstOrNull { it.index == index }?.offset ?: 0
                    }
                },
                onDrag = { deltaY ->
                    val current = draggingIndex ?: return@NoteRow
                    dragDelta += deltaY
                    // 用「这一行当前的中心点」落在谁身上，来决定换到哪个下标
                    val info = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == current } ?: return@NoteRow
                    val center = dragStartOffset + dragDelta + info.size / 2f
                    val target = listState.layoutInfo.visibleItemsInfo.firstOrNull {
                        it.index != current && center >= it.offset && center <= it.offset + it.size
                    }
                    if (target != null) {
                        onMove(current, target.index)
                        draggingIndex = target.index
                    }
                },
                onDragStop = {
                    draggingIndex = null
                    dragDelta = 0f
                },
                onEdit = { onEdit(note) },
                onDelete = { onDelete(note) },
                modifier = Modifier
                    // 被拖的那行不参与位移动画，否则会和手指位移打架
                    .then(if (isDragging) Modifier else Modifier.animateItemPlacement())
                    // 选中项会原地放大，抬到上层才不会被下面那条压住
                    .zIndex(
                        when {
                            isDragging -> 2f
                            selectedId == note.id -> 1f
                            else -> 0f
                        }
                    )
            )
        }
    }
}

/**
 * 一行便签。
 *
 * 三个手势都挂在这一行上，靠「谁先消费事件」自然分流：
 *  - 轻点 → 选中（高亮）；编辑走左下角浮出的编辑按钮，避免误触就直接改内容
 *  - 长按后上下拖 → 排序（只用 y 分量，所以和左滑互不干扰）
 *  - 直接左右拖 → 左滑露出编辑按钮；大幅度左滑直接删除并滑出窗口
 * 长按拖动时整行略微放大，并在下方抬起一层阴影，明确「手里正拿着哪一张」。
 */
@Composable
private fun NoteRow(
    note: NoteEntity,
    dragging: Boolean,
    selected: Boolean,
    dragOffsetY: Int,
    revealed: Boolean,
    onSelect: () -> Unit,
    onReveal: () -> Unit,
    onClose: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragStop: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    // drawBehind / pointerInput 的 lambda 都不是 @Composable，颜色得先取出来
    val scope = rememberCoroutineScope()
    val pressInteraction = remember { MutableInteractionSource() }
    // 选中态：一条进度派生全部属性（底色 / 色条 / 文字 / 描边 / 放大），拖动和按下另算
    val selection = rememberSelectionProgress(selected)
    // 悬停 / 按下也走同一套高亮语言：桌面端移上来就提前亮，点下去再亮一档
    val hover = rememberHoverProgress(pressInteraction)
    val pressed by pressInteraction.collectIsPressedAsState()
    // 底色 / 文字色 / 描边 / 色条全部由 HighlightSpec 统一算 —— 和课表卡片同一份配方，
    // 所以两个列表的「选中」看起来是同一套东西，而不是各写各的
    val spec = HighlightSpec.of(
        baseContainer = MaterialTheme.colorScheme.surface,
        baseTitle = MaterialTheme.colorScheme.onSurface,
        selection = selection,
        pressed = pressed,
        hovering = hover > 0.5f
    )
    val containerColor = spec.containerColor
    val textColor = spec.titleColor
    // 拖动中不画描边（卡片在手指下、外框会晃），其余交给 spec
    val borderColor = if (dragging) Color.Transparent else spec.borderColor
    val borderWidth = if (dragging) 0.dp else spec.borderWidth
    // 选中时原地放大：graphicsLayer 不参与布局，所以是「自己长大」而不是把上下两条挤开
    val selectedScale = 1f + (SELECTED_SCALE - 1f) * selection
    val elevation by animateDpAsState(
        // 拖起来时抬一层阴影（落在卡片下方）；选中时也抬一档，配合放大明确「就是这一条」
        targetValue = when {
            dragging -> 12.dp
            selected -> 4.dp
            hover > 0.5f -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(ENTER_MS),
        label = "noteElevation"
    )
    // 横向偏移（px，负值 = 已经左滑出去多少）
    var offsetX by remember { mutableFloatStateOf(0f) }
    var rowWidth by remember { mutableStateOf(0) }
    var rowHeight by remember { mutableStateOf(0) }
    // 滑出动画进行中：避免重复触发删除，也避免被「别栏划开就收回」打断
    var dismissing by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    // 露出按钮的高度跟行高走，一行/两行便签都贴合；宽度固定，所以它比正方形宽一些
    val revealHeightDp = with(density) { rowHeight.toDp() }
    // 按钮宽度和中间的空隙都只跟密度有关，是稳定值，可以放心给闭包捕获
    val revealPx = with(density) { SWIPE_EDIT_WIDTH.toPx() }
    val gapPx = with(density) { SWIPE_EDIT_GAP.toPx() }
    // 卡片要让出的总距离 = 按钮宽度 + 中间的空隙
    val revealTargetPx = revealPx + gapPx
    // 卡片左侧那点外边距。滑出动画要把它一起算进去，少一点卡片都会残留一条边
    val edgePx = with(density) { NOTE_ROW_H_PADDING.toPx() }

    // 被拖起来时略微放大；手指按下时再缩一点，松手弹回
    val dragScale by animateFloatAsState(if (dragging) DRAG_SCALE else 1f, label = "noteScale")
    val pressScale = rememberPressScale(pressInteraction)
    // 左滑露出的那个编辑按钮也带按下反馈
    val revealInteraction = remember { MutableInteractionSource() }
    val revealScale = rememberPressScale(revealInteraction, pressedScale = 0.94f)

    // pointerInput 的 block 只在 key 变化时重建，回调会「冻」在创建那一刻；
    // 用 rememberUpdatedState 包一层，保证里面读到的永远是最新的回调。
    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDrag by rememberUpdatedState(onDrag)
    val currentOnDragStop by rememberUpdatedState(onDragStop)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnEdit by rememberUpdatedState(onEdit)
    val currentOnReveal by rememberUpdatedState(onReveal)
    val currentOnClose by rememberUpdatedState(onClose)

    /** 平滑吸附到某个横向偏移 */
    fun settleTo(target: Float) {
        scope.launch {
            animate(
                initialValue = offsetX,
                targetValue = target,
                animationSpec = tween(180)
            ) { value, _ -> offsetX = value }
        }
    }

    /** 大幅度左滑 / 点了露出的删除按钮：先整条滑出窗口，动画结束再真正删 */
    fun dismiss() {
        if (dismissing) return
        dismissing = true
        scope.launch {
            // rowWidth 在这里现读：pointerInput 的 block 只建一次，用外面算好的值会是 0
            animate(
                initialValue = offsetX,
                targetValue = -(rowWidth + edgePx),
                animationSpec = tween(220)
            ) { value, _ -> offsetX = value }
            currentOnDelete()
        }
    }

    // 别的栏被划开时自己收回去，避免同时有好几栏敞着
    LaunchedEffect(revealed) {
        if (!revealed && !dismissing && offsetX != 0f) {
            animate(
                initialValue = offsetX,
                targetValue = 0f,
                animationSpec = tween(180)
            ) { value, _ -> offsetX = value }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = NOTE_ROW_H_PADDING, vertical = 4.dp)
            .onSizeChanged {
                rowWidth = it.width
                rowHeight = it.height
            }
    ) {
        // ── 底层：左滑露出来的方形编辑按钮。裁剪只做在这一层，免得把上层卡片的放大和阴影一起裁掉 ──
        Box(
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .width(SWIPE_EDIT_WIDTH)
                    .height(revealHeightDp)
                    // 缩放要放在 clip / background 之前，否则缩的只是图标、底色不动
                    .graphicsLayer {
                        scaleX = revealScale
                        scaleY = revealScale
                    }
                    .clip(SHAPE_CARD)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(
                        interactionSource = revealInteraction,
                        indication = LocalIndication.current
                    ) {
                        currentOnClose()
                        currentOnEdit()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = "编辑便签",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }

        // ── 上层：便签卡片，跟着手指横向移动 ──
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), dragOffsetY) }
                .graphicsLayer {
                    // 拖动放大 × 选中放大 × 按下缩小，三者相乘
                    val s = dragScale * selectedScale * pressScale
                    scaleX = s
                    scaleY = s
                }
                .pointerInput(note.id) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            // 拖动阶段最多让出 60% 行宽，剩下的交给松手后的滑出动画
                            val limit = if (rowWidth > 0) rowWidth * SWIPE_MAX_DRAG_FRACTION else 0f
                            offsetX = (offsetX + dragAmount).coerceIn(-limit, 0f)
                        },
                        onDragEnd = {
                            // rowWidth 在这里现读：pointerInput 的 block 只建一次，
                            // 用外面算好的 val 会一直停在初始值 0
                            val width = rowWidth
                            if (width <= 0) {
                                settleTo(0f)
                            } else {
                                val dragged = -offsetX
                                // 删除阈值至少要超过「露出位置 + 一个空隙」，否则窄屏上
                                // 「行宽 × 比例」会小于露出距离，露出和删除就撞在一起了
                                val dismissAt = maxOf(
                                    width * SWIPE_DISMISS_FRACTION,
                                    revealTargetPx + gapPx
                                )
                                when {
                                    // 大幅度左滑：直接删，整条滑出窗口消失
                                    dragged > dismissAt -> dismiss()
                                    // 小幅度左滑：吸附到露出编辑按钮的位置
                                    dragged > revealTargetPx * SWIPE_REVEAL_TRIGGER -> {
                                        currentOnReveal()
                                        settleTo(-revealTargetPx)
                                    }
                                    else -> {
                                        currentOnClose()
                                        settleTo(0f)
                                    }
                                }
                            }
                        },
                        onDragCancel = { settleTo(0f) }
                    )
                }
                .pointerInput(note.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { currentOnDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            currentOnDrag(amount.y)
                        },
                        onDragEnd = { currentOnDragStop() },
                        onDragCancel = { currentOnDragStop() }
                    )
                }
                .clickable(
                    interactionSource = pressInteraction,
                    indication = LocalIndication.current
                ) { onSelect() },
            shape = SHAPE_CARD,
            colors = CardDefaults.cardColors(containerColor = containerColor),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation),
            border = BorderStroke(borderWidth, borderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = NOTE_ROW_HEIGHT)
                    // 左侧色条用 drawBehind 画：不依赖 IntrinsicSize 也能跟着实际高度走。
                    // 它和课表卡片的竖条共用 `HighlightLines` 的几何规范（同样的宽度来源、
                    // 同样的上下内缩比例），所以两个列表的色条在视觉上是一条线
                    .drawBehind {
                        val inset = HL_INSET.toPx()
                        drawHighlightLines(
                            cornerRadius = SHAPE_CARD_RADIUS,
                            lines = HighlightLines(
                                barBrush = spec.barBrush,
                                barWidth = spec.barWidth
                            ),
                            barTop = inset,
                            barHeight = (size.height - inset * 2f).coerceAtLeast(0f)
                        )
                    },
                // 单行便签时内容垂直居中，别贴着上边、下面空一截
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = note.text,
                        fontSize = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 16.dp)
                    )
                }
            }
        }
    }
}

/** 没有便签 / 搜索无结果：都给一条明确出路 */
@Composable
fun EmptyNotes(searching: Boolean, onAdd: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // 「搜索无结果」和「一条都还没有」是两种不同的空态，切换时淡入淡出，别硬切
        AnimatedContent(
            targetState = searching,
            transitionSpec = {
                (fadeIn(tween(ENTER_MS)) + slideInVertically(tween(ENTER_MS)) { it / 12 }) togetherWith
                    (fadeOut(tween(EXIT_MS)) + slideOutVertically(tween(EXIT_MS)) { -it / 12 })
            },
            contentAlignment = Alignment.Center,
            label = "emptyNotes"
        ) { isSearching ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                if (isSearching) {
                    Text("没有匹配的便签", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "换个关键词试试，或清空搜索看全部便签",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                } else {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("还没有便签", fontWeight = FontWeight.Medium, fontSize = 17.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "点右下角「+」随手记一条；长按某一栏可以上下拖动排序，左滑可以删除",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("添加项目") }
                }
            }
        }
    }
}

/**
 * 左下角操作区的按钮（编辑 / 删除）。
 * 用和右下角 FAB 同一个组件，尺寸和形状天然一致；只有图标颜色跟着动作走。
 */
@Composable
private fun NoteActionButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = FloatingActionButtonDefaults.shape
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = tint,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        interactionSource = interaction
    ) {
        Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(24.dp))
    }
}

/** 回撤按钮：只留图标；尺寸和形状与右下角的加号按钮完全一致，靠 Column 的 End 对齐到同一条右边线 */
@Composable
private fun UndoButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction)
    FloatingActionButton(
        onClick = onClick,
        modifier = Modifier
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = FloatingActionButtonDefaults.shape
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.primary,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp),
        interactionSource = interaction
    ) {
        Icon(UndoIcon, contentDescription = "回撤", modifier = Modifier.size(24.dp))
    }
}

/**
 * 「回撤」图标，用的是 Material 官方 Undo 的路径。
 * 不引 material-icons-extended：为一个图标背上整个扩展包不值当，手写路径更省。
 */
private val UndoIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Undo",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12.5f, 8f)
            curveTo(9.85f, 8f, 7.45f, 8.99f, 5.6f, 10.6f)
            lineTo(2f, 7f)
            verticalLineTo(16f)
            horizontalLineTo(11f)
            lineTo(7.38f, 12.38f)
            curveTo(8.77f, 11.22f, 10.54f, 10.5f, 12.5f, 10.5f)
            curveTo(16.04f, 10.5f, 19.05f, 12.81f, 20.1f, 16f)
            lineTo(22.47f, 15.22f)
            curveTo(21.08f, 11.03f, 17.15f, 8f, 12.5f, 8f)
            close()
        }
    }.build()
}

/** 添加 / 编辑便签 */
@Composable
fun NoteEditDialog(
    initial: NoteEntity? = null,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    val focusRequester = remember { FocusRequester() }
    // 打开就聚焦，「随手记一条」不用再点一下输入框
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "添加项目" else "编辑便签") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("内容") },
                maxLines = 6,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp)
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
fun TodayChip() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, SHAPE_CHIP)
            .padding(horizontal = 7.dp, vertical = 1.5.dp)
    ) {
        Text("今天", fontSize = 10.sp, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** 表格表头里给「今天」那一列的小角标。列宽只有 40~76dp，所以比 TodayChip 更紧凑 */
@Composable
private fun TodayHeaderBadge() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primary, SHAPE_CHIP)
            .padding(horizontal = 5.dp, vertical = 0.5.dp)
    ) {
        Text(
            "今日",
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            maxLines = 1
        )
    }
}

/** 临时提醒的黄色角标，跟在课程名称右边。表格视图用底色区分，这里是列表视图的标识 */
@Composable
internal fun TemporaryBadge() {
    Box(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.secondary, SHAPE_CHIP)
            .padding(horizontal = 7.dp, vertical = 1.5.dp)
    ) {
        Text(
            "临时",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondary
        )
    }
}

/** 设备当前是星期几（Monday…Sunday，跟数据库里存的一致） */
private fun todayName(): String = TodaySchedule.dayNameOf(System.currentTimeMillis())

/** 当天第几分钟（0..1439）。时间轴上的一切比较都用它，避免毫秒级的时间戳参与 UI 判断 */
private fun minuteOfDay(millis: Long): Int {
    val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
}

/**
 * 课程的起止时刻（当天第几分钟）。解析不出来时给一个不会命中任何区间的大值 ——
 * 脏数据不该让整节课被误判成「正在上课」。
 */
private val ClassEntity.startMinute: Int
    get() = TimeAxis.minutesOf(startTime) ?: Int.MAX_VALUE

private val ClassEntity.endMinute: Int
    get() = TimeAxis.minutesOf(endTime) ?: Int.MIN_VALUE

// ── 周课表：表格 / 列表两种模式 ──────────────────────────────────

enum class WeekMode { GRID, LIST }

@Composable
private fun WeekModeSwitch(mode: WeekMode, onChange: (WeekMode) -> Unit) {
    // 自绘的 M3「分段按钮」（Segmented Button）。
    //
    // 为什么要自绘：Material3 官方的 `SingleChoiceSegmentedButtonRow` 从 1.2.0 才有，
    // 本项目锁在 compose-bom 2024.01.00（material3 1.1.2），且构建必须 --offline，
    // 拉不到新版本。所以照着 M3 规范手搓一个：外框一整条 1dp 描边 + 内部分段，
    // 选中段填充 primaryContainer、文字 onPrimaryContainer，未选中段透明底 + outlineVariant。
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    // 量出每段的实际尺寸，选中底色按它平移（不写死宽度，换文案也不会错位）
    var segmentSize by remember { mutableStateOf(IntSize.Zero) }
    val pillOffset by animateIntOffsetAsState(
        targetValue = IntOffset(if (mode == WeekMode.GRID) 0 else segmentSize.width, 0),
        animationSpec = tween(ENTER_MS),
        label = "modePill"
    )

    Box(
        modifier = Modifier
            .height(34.dp)
            .clip(RoundedCornerShape(17.dp))
            .border(1.dp, scheme.outline, RoundedCornerShape(17.dp))
    ) {
        // 选中段的填充：在文字下层，从左段平移到右段
        if (segmentSize.width > 0) {
            Box(
                modifier = Modifier
                    .offset { pillOffset }
                    .width(with(density) { segmentSize.width.toDp() })
                    .height(with(density) { segmentSize.height.toDp() })
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.primaryContainer)
            )
        }
        Row {
            WeekMode.entries.forEach { item ->
                val selected = item == mode
                // 文字颜色仍做过渡：底色滑到时文字「点亮」
                val contentColor by animateColorAsState(
                    targetValue = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                    animationSpec = tween(ENTER_MS),
                    label = "modeFg"
                )
                Text(
                    text = if (item == WeekMode.GRID) "表格" else "列表",
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                    color = contentColor,
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val size = coords.size
                            if (size != segmentSize) segmentSize = size
                        }
                        .clickable { onChange(item) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ── 表格模式：时间轴 + 七天网格 ──────────────────────────────────

/** 底部给右下角的加号按钮留出的高度，免得最后一行钻到它底下 */
private val FAB_RESERVE = 88.dp

/** 一格最少 / 最多多高。行少时不至于撑成一整屏的大格子，行多时也不挤成一团 */
private val MIN_GRID_ROW_HEIGHT = 56.dp
private val MAX_GRID_ROW_HEIGHT = 108.dp

/** 课程块的最小高度，太短的课也得看得见 */
private val MIN_BLOCK_HEIGHT = 30.dp

/**
 * 课程块四周留的缝隙。相邻两节课的块贴在一起时，上下叠着看起来像一整条，
 * 分不清是「一节长课」还是「两节课连上」。留一点缝，每节课的边界就立刻清楚了。
 * 取 5dp：再小看不出缝，再大矮块的可用高度就被吃没了。
 */
private val BLOCK_GAP = 5.dp

/** 块矮于这个高度就只留课程名，教室让位 */
private val BLOCK_ROOM_MIN_HEIGHT = 46.dp

/** 表头（星期那一行）占的高度，算可用高度时要扣掉。今天那列多一行「今日」角标，留宽裕些 */
private val GRID_HEADER_HEIGHT = 46.dp

/**
 * 表头里星期文字下方留的呼吸空间。
 *
 * **必须做在表头格子内部**（当 `padding(bottom)` 用），不能做在整行 Row 上：
 * 做在 Row 上等于在表头底与列身顶之间挖一条没有底色的透明带，
 * 高亮列就会被这条带子切成「上深下浅」两截。做进格子里，底色仍然铺满。
 */
private val HEADER_GAP = 6.dp

/**
 * 网格底纹的透明度：横向时刻线 / 竖向列分隔。
 *
 * 两者都是**底纹**而不是内容，所以都比课程块淡得多。竖线刻意比横线再淡一档：
 * 横线承载「时间刻度」这层信息（要和左侧标签对齐），竖线只是帮眼睛定位到某一天，
 * 两者同等强度的话，网格会显得比课本身还抢眼。
 */
private const val GRID_LINE_ALPHA = 0.08f
private const val GRID_COLUMN_ALPHA = 0.05f

/**
 * 「斑马纹」底纹的透明度：按小时交替铺一层极淡的横向色带。
 *
 * 网格是按时间轴定位的，没有网页表格那种「行」，但**时间本身就是行**——
 * 每一小时就是一行。按奇偶小时交替铺底，眼睛扫一行时有了参照物，
 * 定位「这是第几节」不用每次回到左边读刻度。
 *
 * 比横线还淡（横线 0.08）：它是**背景的背景**，一旦能明显看出来就会盖过课程块。
 * 0.03 是在浅色下刚好「若有若无」、深色下不至于消失的临界值。
 */
private const val ZEBRA_ALPHA = 0.03f

/** 单列最大宽度，免得大屏上几列被拉得太开 */
private val MAX_COLUMN_WIDTH = 76.dp

/**
 * 「窄屏」断点。窄于这个宽度时整套尺寸降一档（时间栏收窄、今天列少加宽、字号减小）。
 *
 * 取 360dp：这是最主流的手机竖屏可用宽度（360×640 的逻辑像素），
 * 七天 + 时间栏塞进来的话每列只剩 40dp 上下，正好是「放得下两个字」的临界线。
 * 再窄（小屏手机 / 分屏）就靠横向滚动兜底，不强行继续压。
 */
private val COMPACT_BREAKPOINT = 360.dp

/**
 * 窄屏下整套尺寸的统一缩放。0.92 是「明显小一点但不至于小到难读」的经验值 ——
 * 12sp 的标题缩到 11sp，仍在可读范围内；再往下到 10sp，中文笔画就开始糊了。
 */
private const val COMPACT_SCALE = 0.92f

/** 今天那一列的加宽系数，保证课程名不被截断；配合纵轴的放大一起调整 */
private const val TODAY_COLUMN_SCALE = 1.35f

/** 左侧时刻标签上移这么多，让文字压在它对应的那条线上而不是吊在下面 */
private val AXIS_LABEL_LIFT = 8.dp

/**
 * 表格模式：左侧时间轴 + 右侧七天的网格。
 *
 * 纵轴从本周最早的一门课开始，课程按真实时长占据纵向空间，所以能跨越好几格；
 * 左侧只标「今天」各事项的起止时刻，位置正好对齐今天的课程块边界。
 */
@Composable
fun WeekGrid(
    classes: List<ClassEntity>,
    highlightDay: String?,
    onEdit: (ClassEntity) -> Unit
) {
    val span = remember(classes) { TimeAxis.spanOf(classes) }
    // 手动高亮的列。三态：还没干预（跟随「今天」）/ 手动选了某天 / 手动全部取消。
    // 换周时（highlightDay 变了）重置回跟随。
    var pickedDay by remember(highlightDay) { mutableStateOf<String?>(null) }
    var cleared by remember(highlightDay) { mutableStateOf(false) }
    val activeDay = when {
        cleared -> null
        pickedDay != null -> pickedDay
        else -> highlightDay
    }
    // 鼠标悬停在哪一列上（桌面端）。格子里的小块几十个，独占一行时只有一块能拿到 hover，
    // 但用户想看的是「这一整列」—— 所以状态提升到这里，由格子往上报，整列一起亮。
    // 这是**比手动选中更弱**的一档：悬停只是「预告」，不该和真正选中的列抢眼。
    var hoveredDay by remember { mutableStateOf<String?>(null) }
    // 当前被单击选中的课程块。单击只高亮、不进编辑；双击才进编辑。
    // 和列表界面（WeekDayList）用同一个状态语义，两个界面的手感一致。
    // 不 remember 到 highlightDay 上：换周也该保留选中（用户可能只是想对照着看）
    var selectedClassId by remember { mutableStateOf<Int?>(null) }
    // 由 id 反查课程。被删掉或换周后块不在了，详情卡自动消失（不用手动兜 null）
    val selectedClass = remember(selectedClassId, classes) {
        selectedClassId?.let { id -> classes.firstOrNull { it.id == id } }
    }
    if (span == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "这一周没有课",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = 14.sp
            )
        }
        return
    }

    // mapping 的读取点特意放在 BoxWithConstraints 里面：这样每帧被失效的只有网格这一层子组合，
    // WeekGrid 自己的函数体不会跟着重组（否则每帧要组合两遍：一遍本体 + 一遍网格内容）
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val mapping = TimeAxis.mappingOf(span, null, 1f)

        // 空列折叠：只画本周有课的那几天，宽度让给有课的天。
        // 只有 1~2 天有课时不折叠，否则一两列撑满屏幕反而不像课表。
        // 按天分好组缓存起来：选中动画每帧都会重组，逐帧 filter 七天纯属浪费
        val byDay = remember(classes) { classes.groupBy { it.dayOfWeek } }
        val days = remember(classes) {
            val busy = dayOrder.filter { day -> classes.any { it.dayOfWeek == day } }
            if (busy.size >= 3) busy else dayOrder
        }

        // 时间列约占 12% 宽（夹在 38~58dp）。
        // 窄屏（手机竖屏）下 12% 会小于 38dp，所以下限要按屏宽再压一档 ——
        // 否则七天挤完后每列只剩 30dp 出头，课程名一个字都放不下。
        val compact = maxWidth < COMPACT_BREAKPOINT
        val labelWidth = (maxWidth * 0.12f)
            .coerceIn(if (compact) 30.dp else 38.dp, 58.dp)

        // 每列平分剩余宽度（不超过上限，免得大屏上被拉得太开）；
        // 今天那一列再稍微加宽一点，保证内容不被截断，多出来的宽度从其它列均摊。
        val gridWidth = (maxWidth - labelWidth - 8.dp).coerceAtLeast(0.dp)
        val normalWidth = (gridWidth / days.size).coerceAtMost(MAX_COLUMN_WIDTH)
        val todayIndex = days.indexOf(activeDay)
        val hasToday = todayIndex >= 0 && days.size > 1
        // 窄屏下今天列的加宽系数要收小：本来就窄，再按 1.35 放大，
        // 其余六列会被挤到 28dp 以下，全变成只有一个字的竖条
        val todayScale = if (compact) 1.15f else TODAY_COLUMN_SCALE
        val todayWidth = if (hasToday) {
            (normalWidth * todayScale).coerceAtMost(MAX_COLUMN_WIDTH)
        } else normalWidth
        val otherWidth = if (hasToday) {
            ((gridWidth - todayWidth) / (days.size - 1)).coerceAtMost(MAX_COLUMN_WIDTH)
        } else normalWidth
        // 逐列做宽度动画：每一列都从「它自己上一次的宽度」平滑过渡，
        // 所以点列头高亮、再点一次取消，两个方向都不会跳。
        // 注意必须是 for 循环（@Composable 调用不能写在 mapIndexed 的普通 lambda 里）。
        val columnWidths = ArrayList<Dp>(days.size)
        for (index in days.indices) {
            val target = if (index == todayIndex) todayWidth else otherWidth
            columnWidths.add(
                animateDpAsState(
                    targetValue = target,
                    animationSpec = tween(ENTER_MS),
                    label = "columnWidth"
                ).value
            )
        }

        val cellWidth = normalWidth
        // 窄屏下整套字号再乘一档。这里的阈值按**实际列宽**分档而不是按屏宽：
        // 列宽是最终决定「能放几个字」的量，屏宽只是它的间接来源
        val sizeScale = if (compact) COMPACT_SCALE else 1f
        val titleSize = when {
            cellWidth >= 62.dp -> 12.sp
            cellWidth >= 52.dp -> 11.sp
            else -> 10.sp
        } * sizeScale
        val roomSize = (if (cellWidth >= 58.dp) 10.sp else 9.sp) * sizeScale
        val titleLines = if (cellWidth >= 54.dp) 3 else 2

        // 纵向比例：按可用高度估算「放得下几行」，据此决定整张表是铺满一屏还是需要滚动。
        // 格子本身不再画等距刻度（左侧改标实际时刻了），这个行数只用来定高度。
        // 底部特意留出加号按钮的位置，别让最后一行被它压住。
        val availableHeight = (maxHeight - GRID_HEADER_HEIGHT - FAB_RESERVE)
            .coerceAtLeast(MIN_GRID_ROW_HEIGHT)
        val maxRows = (availableHeight / MIN_GRID_ROW_HEIGHT).toInt().coerceAtLeast(1)
        val hoursPerRow = TimeAxis.hoursPerRow(span, maxRows)
        val rowCount = TimeAxis.rowCount(span, hoursPerRow)
        val rowHeight = (availableHeight / rowCount)
            .coerceIn(MIN_GRID_ROW_HEIGHT, MAX_GRID_ROW_HEIGHT)
        val gridHeight = rowHeight * rowCount

        // 左侧刻度：高亮着某一列时，标那一列各事项的起止时刻（正好对齐它课程块的上下边界）；
        // 一列都没高亮时退回**真实整点**刻度（见 TimeAxis.roundMarks）——
        // 原来的相对刻度会标出 07:43、09:13 这种读不出含义的时刻
        val marksNow = remember(classes, activeDay, span, hoursPerRow) {
            if (activeDay == null) {
                TimeAxis.roundMarks(span, hoursPerRow * 60)
            } else {
                classes.filter { it.dayOfWeek == activeDay }
                    .flatMap { cls ->
                        listOfNotNull(TimeAxis.minutesOf(cls.startTime), TimeAxis.minutesOf(cls.endTime))
                    }
                    .distinct()
                    .sorted()
            }
        }
        // 换高亮列时整套刻度会换掉。直接换会「啪」地跳一下，所以留一份上一套做交叉淡入淡出；
        // 横线和标签共用同一份刻度，两层始终对得上。
        // 用 LaunchedEffect 而不是在组合期写状态：换刻度的那一帧 axisCross 还是 1（旧的一套满的），
        // 晚一帧再更新不会闪。
        var axisMarks by remember { mutableStateOf(marksNow) }
        var axisMarksPrev by remember { mutableStateOf(marksNow) }
        var axisStep by remember { mutableStateOf(0) }
        LaunchedEffect(marksNow) {
            if (marksNow != axisMarks) {
                axisMarksPrev = axisMarks
                axisMarks = marksNow
                axisStep++
            }
        }
        // 每一步让 axisProgress 从 k 涨到 k+1，减去 k 就得到 0 → 1 的交叉进度
        val axisProgress by animateFloatAsState(
            targetValue = axisStep.toFloat(),
            animationSpec = tween(ENTER_MS),
            label = "axisCross"
        )
        val axisCross = (axisProgress - axisStep + 1f).coerceIn(0f, 1f)
        val labelWidthPx = with(LocalDensity.current) { labelWidth.toPx() }
        val gridHeightPx = with(LocalDensity.current) { gridHeight.toPx() }
        val lineBase = MaterialTheme.colorScheme.onSurface
        // 表头下的分隔线。不能用网格的横线浓度（0.08），那是「底纹」；
        // 这是真正的结构线，要一眼看得见，取 outline 的中等浓度
        val headerDivider = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        // 斑马纹的色带位置。跟 span / 映射有关，与点击、悬停无关，算一次缓存住
        val zebraBands = remember(span, mapping) { TimeAxis.zebraBands(span) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = FAB_RESERVE)
        ) {
            // 表头：星期。点一下把那一列高亮起来（左侧刻度跟着换成它的时刻、列也加宽）；
            // 点已经高亮的那一列则全部取消，左侧退回固定间隔。
            //
            // **表头与列身之间不能留缝**：Row 本身不再挂 `padding(bottom)` ——
            // 挂了的话那一段高度没有底色，表头底与列身底之间会夹出一条透明带，
            // 高亮列看起来就是「上下两截颜色不一致」。改为把间距做进**表头格子内部**
            // （见下方 Box 的 `padding(bottom = HEADER_GAP)`），底色仍然铺满整格。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        val strokeWidth = 1.dp.toPx()
                        drawLine(
                            color = headerDivider,
                            start = Offset(0f, size.height - strokeWidth / 2),
                            end = Offset(size.width, size.height - strokeWidth / 2),
                            strokeWidth = strokeWidth
                        )
                    }
            ) {
                // 左侧刻度栏的占位。**必须是完全透明的空白** ——
                // 这里一旦有底色，就会在表头最左边形成一个独立色块，
                // 和下方列身的边界拼起来像「从表头伸出一根多余竖条」。
                // 网格主体对应位置也是空白（只有刻度文字），两边一致才连贯
                Box(modifier = Modifier.width(labelWidth).height(GRID_HEADER_HEIGHT))
                days.forEachIndexed { index, day ->
                    val isActive = day == activeDay
                    // 悬停时提前给一点反馈：桌面端鼠标移上来就知道「这一列可以点」。
                    // 列身里悬停某个块时，这一列的表头也跟着亮 —— 表头是列的「名字」，
                    // 列身亮了表头不亮，会让人以为亮的是某个格子而不是整列
                    val headerInteraction = remember { MutableInteractionSource() }
                    val headerHoverSelf = rememberHoverProgress(headerInteraction)
                    val headerHoverRaw = maxOf(headerHoverSelf, if (day == hoveredDay) 1f else 0f)
                    val headerHover by animateFloatAsState(
                        targetValue = headerHoverRaw,
                        animationSpec = tween(HOVER_MS),
                        label = "headerHover"
                    )
                    // 高亮切换时星期文字的颜色渐变过去，不是直接跳色。
                    // 三级色阶（Vercel 的 400/500/600 字重思路在颜色上的对应）：
                    // 普通日 → 次要色；悬停 → 向主文字色靠；选中 → 主色。
                    // 原来普通日是 onSurface α0.7，和选中列的主色之间只差一个色相，层级不明显
                    val inactiveHeader = MaterialTheme.colorScheme.onSurfaceVariant
                    val headerColor by animateColorAsState(
                        targetValue = when {
                            isActive -> MaterialTheme.colorScheme.primary
                            headerHoverRaw > 0.01f -> lerpColor(
                                inactiveHeader,
                                MaterialTheme.colorScheme.onSurface,
                                headerHoverRaw
                            )
                            else -> inactiveHeader
                        },
                        animationSpec = tween(ENTER_MS),
                        label = "dayHeaderColor"
                    )
                    // 高亮列的底色作为「列高亮」的一部分，从表头一直铺到时间轴，
                    // 表头和列身用同一个底色，整列看起来才是一块整体。
                    //
                    // 配色与 DayColumn 严格一致：**今日走主色（蓝），非今日走中性灰**。
                    // 之前两者都用 primary 只差 α，在截图里根本分不出是「今天」还是「用户选的」
                    val isTodayCol = day == highlightDay
                    val headerIsDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                    val headerAccent = when {
                        isTodayCol -> MaterialTheme.colorScheme.primary
                        headerIsDark -> MaterialTheme.colorScheme.surfaceVariant
                        else -> MaterialTheme.colorScheme.outlineVariant
                    }
                    val headerTintAlpha = when {
                        isActive && isTodayCol && headerIsDark -> 0.34f
                        isActive && isTodayCol -> 0.14f
                        isActive && headerIsDark -> 0.85f
                        isActive -> 0.75f
                        // 未选中但「是今天」：给一层很弱的底色，让今天始终有存在感。
                        // 这样即使手动高亮了别的列，也能一眼看出「今天在哪」——
                        // 光靠一个 9sp 的小角标，注意力被高亮列抢走后就看不见了。
                        // **数值必须与 DayColumn 的 todayBaseTint 一致**，否则表头与列身的
                        // 深浅对不上，整列会显得「头重脚轻」
                        isTodayCol -> if (headerIsDark) 0.07f else 0.035f
                        // 未选中且非今日：悬停时给一点点底，仍是主色的弱态
                        else -> lerp(0f, 0.10f, headerHover)
                    }
                    val headerTint = (
                        if (isActive) headerAccent
                        else MaterialTheme.colorScheme.primary
                        ).copy(alpha = headerTintAlpha)
                    // 今日列单独给一条下划线（压在分隔线上方），让「今天」不只是靠一个小角标
                    val todayUnderline = if (isTodayCol) {
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = lerp(0.45f, 0.75f, if (isActive) 1f else headerHover)
                        )
                    } else Color.Transparent
                    Box(
                        modifier = Modifier
                            .width(columnWidths[index])
                            // 表头格子**占满整段表头高度**（含下方留白区），底色才能一路铺到
                            // 列身顶上、与列身无缝相接。宽度与高度都写死，行列才能严格对齐
                            .height(GRID_HEADER_HEIGHT)
                            // 只保留**最外侧**的圆角：整条表头是一个连续的带子，
                            // 每列各自圆角会在列间切出一串缺口，露出背景形成「黑色断层」。
                            // 首列圆左上、末列圆右上，中间列不圆
                            .clip(
                                RoundedCornerShape(
                                    topStart = if (index == 0) 8.dp else 0.dp,
                                    topEnd = if (index == days.lastIndex) 8.dp else 0.dp
                                )
                            )
                            .background(headerTint)
                            .clickable(
                                interactionSource = headerInteraction,
                                indication = LocalIndication.current
                            ) {
                                if (isActive) {
                                    pickedDay = null
                                    cleared = true
                                } else {
                                    pickedDay = day
                                    cleared = false
                                }
                            }
                            .drawBehind {
                                if (todayUnderline != Color.Transparent) {
                                    val h = 2.dp.toPx()
                                    drawRect(
                                        color = todayUnderline,
                                        topLeft = Offset(0f, size.height - h),
                                        size = Size(size.width, h)
                                    )
                                }
                            }
                            // 文字区域往上收，把 HEADER_GAP 留在格子**内部**的底部：
                            // 底色依然覆盖这 6dp，表头与列身之间就不会出现无色断层
                            .padding(top = 6.dp, bottom = HEADER_GAP),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayLabel[day] ?: day,
                                fontSize = 12.sp,
                                // 选中 / 悬停时加一档字重，文字本身也跟着「站起来」。
                                // 600 是上限：表头是导航性质的标签，不是内容
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                                // 字距收紧一点：中文星期只有两字，默认字距会显得松散，
                                // 收紧后表头更像一个「标签」而不是「一句话」
                                letterSpacing = 0.2.sp,
                                color = headerColor,
                                textAlign = TextAlign.Center,
                                maxLines = 1
                            )
                            // 「今日」标在真正的那一天上，跟手动高亮哪一列无关
                            if (day == highlightDay) {
                                Spacer(Modifier.height(2.dp))
                                TodayHeaderBadge()
                            }
                        }
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
                // 点空白处取消选中：和便签页同款做法。
                // 挂在底层 Box 上是因为课程块自己会消费掉 down（clickable +
                // pointerInput 的手势层），所以这里**只接住没人要的**点击 —— 落在网格底纹
                // 或列间空白上的那一下。detectTapGestures 用 `awaitFirstDown(requireUnconsumed = true)`，
                // 块已经消费过的 down 不会再传过来，因此不会误把「点课程块」当成「点空白」
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { selectedClassId = null }
                        }
                )
                // ── 底层：时间轴（竖向列分隔 + 横向时刻线 + 左侧刻度）──
                // 横线从刻度栏右侧铺到最右；每一条都对齐一个真实时刻，和标签一一对应。
                // 新旧两套刻度各带一个 alpha，交叉淡入淡出。
                //
                // 竖线是本版新加的：原来七天之间没有任何分隔，一旦每列都排满了课程块，
                // 整片网格就糊成一块，看不出「哪一格属于星期几」。竖线只画在列与列之间、
                // 且刻意画得比横线更淡 —— 它是辅助定位的**底纹**，不该跟课程块抢视线。
                //
                // 列边界的位置：依次累加各列宽度。密度与列宽都不常变，算一次缓存住。
                // 最后一条（网格右边缘）不画 —— 它贴着屏幕边，画了反而显脏
                val densityForEdges = LocalDensity.current
                val columnEdgesPx = remember(columnWidths, labelWidthPx, densityForEdges) {
                    with(densityForEdges) {
                        buildList {
                            var x = labelWidthPx
                            for (w in columnWidths.dropLast(1)) {
                                x += w.toPx()
                                add(x)
                            }
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            // ── 斑马纹：按小时交替的横向底纹 ──
                            // 画在最底层，横线 / 竖线 / 课程块都压在它上面。
                            // 只铺到刻度栏右侧（和横线同一起点），左侧的刻度区保持干净
                            zebraBands.forEach { band ->
                                val y0 = mapping.fractionOf(band.startMinute) * gridHeightPx
                                val y1 = mapping.fractionOf(band.endMinute) * gridHeightPx
                                val h = y1 - y0
                                if (h <= 0.5f) return@forEach
                                drawRect(
                                    color = lineBase.copy(alpha = ZEBRA_ALPHA),
                                    topLeft = Offset(labelWidthPx, y0),
                                    size = Size(size.width - labelWidthPx, h)
                                )
                            }

                            fun drawMarks(marks: List<Int>, alpha: Float) {
                                if (alpha <= 0.01f) return
                                marks.forEach { minute ->
                                    val y = mapping.fractionOf(minute) * gridHeightPx
                                    drawLine(
                                        color = lineBase.copy(alpha = GRID_LINE_ALPHA * alpha),
                                        start = Offset(labelWidthPx, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            drawMarks(axisMarksPrev, 1f - axisCross)
                            drawMarks(axisMarks, axisCross)

                            // 竖向列分隔：只画列与列之间，不画最右那条边
                            columnEdgesPx.forEach { x ->
                                drawLine(
                                    color = lineBase.copy(alpha = GRID_COLUMN_ALPHA),
                                    start = Offset(x, 0f),
                                    end = Offset(x, gridHeightPx),
                                    strokeWidth = 1f
                                )
                            }
                        }
                )
                Box(modifier = Modifier.width(labelWidth).fillMaxHeight()) {
                    AxisMarkLabels(axisMarksPrev, 1f - axisCross, mapping, gridHeight)
                    AxisMarkLabels(axisMarks, axisCross, mapping, gridHeight)
                }

                // ── 上层：七天的列 ──
                Row(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
                    // 刻度栏的位置由上面那层占着，这里只留出宽度
                    Box(modifier = Modifier.width(labelWidth).fillMaxHeight())
                    days.forEachIndexed { index, day ->
                        DayColumn(
                            modifier = Modifier.width(columnWidths[index]),
                            columnWidth = columnWidths[index],
                            classes = byDay[day].orEmpty(),
                            mapping = mapping,
                            gridHeight = gridHeight,
                            highlighted = day == activeDay,
                            // 「今天」由 highlightDay 决定，跟手动高亮哪一列无关。
                            // 今日列与非今日列走两套截然不同的高亮色（蓝 / 灰）
                            isToday = day == highlightDay,
                            // 悬停整列：这一列里只要有一个块被悬停，整列轻亮。
                            // 「选中」和「悬停」可能同时落在不同列上，两档强度不同、各画各的
                            hovered = day == hoveredDay,
                            onHoverChange = { isHovered ->
                                hoveredDay = if (isHovered) day
                                else if (hoveredDay == day) null
                                else hoveredDay
                            },
                            titleSize = titleSize,
                            roomSize = roomSize,
                            titleLines = titleLines,
                            selectedClassId = selectedClassId,
                            // 单击：只高亮，不进编辑
                            onSelectClass = { selectedClassId = it.id },
                            // 双击：进编辑
                            onEdit = onEdit
                        )
                    }
                }
            }

            // ── 选中详情卡 ──
            // 单击课程块只是高亮，格子太窄放不下完整信息（教室、老师经常要截断）。
            // 所以选中后在网格下方浮出一条详情：把这一节的信息补齐，并明确提示「双击编辑」——
            // 否则用户点了一下、什么都没发生（除了变蓝），会以为这个应用坏了。
            AnimatedVisibility(
                visible = selectedClass != null,
                enter = fadeIn(tween(ENTER_MS)) + expandVertically(tween(ENTER_MS), expandFrom = Alignment.Top),
                exit = fadeOut(tween(EXIT_MS)) + shrinkVertically(tween(EXIT_MS), shrinkTowards = Alignment.Top)
            ) {
                selectedClass?.let { cls ->
                    ClassDetailCard(
                        cls = cls,
                        isToday = cls.dayOfWeek == highlightDay,
                        onEdit = { onEdit(cls) },
                        onDismiss = { selectedClassId = null }
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 选中课程后浮在网格下方的详情条。
 *
 * 布局是**两行 + 右侧动作**：第一行课程名（可折两行），第二行时间 · 教室 · 老师。
 * 左侧一条 4dp 的竖条沿用课程块的配色规则（今日=蓝 / 非今日=灰），
 * 这样详情条和网格里被选中的那一块在视觉上是同一个东西。
 */
@Composable
private fun ClassDetailCard(
    cls: ClassEntity,
    isToday: Boolean,
    onEdit: () -> Unit,
    onDismiss: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    val accent = if (isToday) scheme.primary else if (isDark) scheme.surfaceVariant else scheme.outline

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 8.dp, end = 8.dp)
            // 双击整条也能进编辑，和网格里的手感一致
            .clickable(onClick = onEdit),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // 左侧色条：和网格里的课程块同一条规则
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    text = cls.title,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = buildString {
                        append("${dayLabel[cls.dayOfWeek] ?: ""} ")
                        append("${cls.startTime}–${cls.endTime}")
                        if (cls.room.isNotBlank()) append(" · ${cls.room}")
                        if (cls.teacher.isNotBlank()) append(" · ${cls.teacher}")
                    }.trim(),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // 右侧动作：提示 + 关闭。
            // 「双击编辑」只用文字说明而不是加个按钮 —— 双击是手势，做成按钮反而误导用户以为要手点
            Column(
                modifier = Modifier.padding(end = 10.dp, top = 10.dp),
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = "双击编辑",
                    fontSize = 11.sp,
                    color = scheme.primary
                )
                Spacer(Modifier.height(6.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "取消选中",
                        modifier = Modifier.size(16.dp),
                        tint = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/**
 * 左侧刻度的一层：同一套时刻，整体带一个透明度。
 * 换高亮列时新旧两套各铺一层、交叉淡入淡出，所以刻度是「渐变」而不是跳变。
 *
 * **为什么要做防重叠**：刻度是按真实时刻落的，两个相邻时刻在纵轴上可能只差几像素 ——
 * 比如两节课 08:00 结束、08:05 开始，或者相邻课表只隔 15 分钟。11sp 的文字约 15dp 高，
 * 布局上就会叠在一起，变成两团糊掉的黑影，比不标时刻还难读。
 *
 * 处理方式：**从下往上**（实际是从前到后，即按 y 递增）逐个放，每个标签至少比上一个低
 * [AXIS_LABEL_MIN_GAP]；实在挤不下就整条丢掉 —— 宁可少标一个时刻，也不要叠出一团糊字。
 * 丢掉的一定是「被前一个挡住的那一个」，所以保留下来的密度仍然均匀。
 */
private val AXIS_LABEL_MIN_GAP = 15.dp

@Composable
private fun AxisMarkLabels(
    marks: List<Int>,
    alpha: Float,
    mapping: TimeAxis.Mapping,
    gridHeight: Dp
) {
    if (alpha <= 0.01f) return

    // 先算好「哪些标签放得下、各自放在哪个 y」。用 remember 缓存：
    // 这套计算只跟刻度与映射有关，不必每帧重算
    val placedLabels = remember(marks, mapping, gridHeight) {
        var lastY = Dp.Unspecified
        marks.mapNotNull { minute ->
            // 标签顶边压着它对应的那条线往上抬一点，所以文字基线正好落在线上
            val y = (gridHeight * mapping.fractionOf(minute) - AXIS_LABEL_LIFT).coerceAtLeast(0.dp)
            if (lastY == Dp.Unspecified || y - lastY >= AXIS_LABEL_MIN_GAP) {
                lastY = y
                minute to y
            } else null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        placedLabels.forEach { (minute, y) ->
            Text(
                text = TimeAxis.labelOf(minute),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                // 刻度是「负空间」里的信息，用次要色而不是主色 ——
                // 主色留给课程块和时间本身，左侧再抢一次主色会让整屏没有主次
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset(y = y)
                    // alpha = 1 时不挂图层：平时两套刻度里总有一套是全不透明的，
                    // 给它单独开一层 RenderNode 没有意义
                    .then(if (alpha < 1f) Modifier.alpha(alpha) else Modifier)
            )
        }
    }

}

/**
 * 一天一列：铺一层高亮底色（如果被高亮），再把课程块按时间比例摆上去。横线由上层统一画。
 *
 * **性能**：选中动画每帧都会重算 mapping，所以这一列每帧都会重组。两个关键点：
 *  - 宽度由 [columnWidth] 传进来，不再用 `BoxWithConstraints` 去量 —— 它是个 SubcomposeLayout，
 *    七天七次子组合，而内容 lambda 每帧都是新的，等于每帧白跑七遍
 *  - 点击回调做成稳定实例传给 [GridCell]，否则每帧新建的闭包会让每个块都跳不过重组
 */
@Composable
private fun DayColumn(
    classes: List<ClassEntity>,
    mapping: TimeAxis.Mapping,
    gridHeight: Dp,
    columnWidth: Dp,
    highlighted: Boolean,
    /** 这一列是不是「今天」。今日与非今日的高亮走**两套颜色**：
     *  今日是「时间事实」（主色 / 蓝），非今日是「用户选择」（中性灰）。
     *  两者之前共用 primary，只靠 α 差一档，实际根本分不出来。 */
    isToday: Boolean,
    hovered: Boolean,
    onHoverChange: (Boolean) -> Unit,
    titleSize: TextUnit,
    roomSize: TextUnit,
    titleLines: Int,
    /** 当前选中的课程 id。单击产生，只高亮、不进编辑 */
    selectedClassId: Int?,
    onSelectClass: (ClassEntity) -> Unit,
    onEdit: (ClassEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // mapping 每次重组都是新对象，用 remember 反而每帧都要重算，直接算更省事（课程量很小）
    val placed = TimeAxis.layout(classes, mapping)
    val gridHeightPx = with(LocalDensity.current) { gridHeight.toPx() }
    // 内容宽度 = 整列宽。原来这里减 2dp 是因为外层有 `padding(horizontal = 1.dp)`，
    // 那个内边距已经删掉了（它会让高亮底在列间露缝、形成断层），所以现在铺满
    val contentWidth = columnWidth.coerceAtLeast(0.dp)
    // 高亮底色淡入淡出：点列头切换高亮时不是「啪」地铺上一层。
    // 透明度分浅色 / 深色两档 —— 同一个 α 在深背景上几乎看不见，在浅背景上又容易显脏
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    // 今日走高对比的**主色**（蓝），非今日走**中性灰**（surfaceVariant 的深灰）。
    // 灰色不能直接用 onSurface 调 α：那在深色下和背景只差 1.78:1，等于没铺。
    // 深色下用一个明确的深灰（surfaceVariant 提一档亮度），浅色下用 surfaceVariant 压深
    val selectTintColor = when {
        isToday -> MaterialTheme.colorScheme.primary
        isDark -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val tintAlpha = when {
        isToday && isDark -> 0.34f
        isToday -> 0.14f
        // 灰底要更深才看得出「选中了」，因为它的色相不抢眼
        isDark -> 0.85f
        else -> 0.75f
    }
    val tint by animateColorAsState(
        targetValue = if (highlighted) selectTintColor.copy(alpha = tintAlpha)
        else Color.Transparent,
        animationSpec = tween(ENTER_MS),
        label = "dayHighlight"
    )
    // 今日列的**常态弱底**：没被高亮时也铺一层很淡的主色，让「今天」始终有存在感。
    // 与 `tint`（选中态）互斥 —— 选中时用 tint，未选中时才回落到这层。
    // 这样「手动高亮了周四」时，周二（今天）仍然能一眼看出来
    val todayBaseTint by animateColorAsState(
        targetValue = if (isToday && !highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.07f else 0.035f)
        } else Color.Transparent,
        animationSpec = tween(ENTER_MS),
        label = "dayTodayBase"
    )
    // ── 悬停整列：比选中弱一档 ──
    // 选中是「你点了它」，悬停只是「鼠标路过」。两者共用一层主色底，但悬停的 α 只有选中的
    // 三分之一左右 —— 鼠标在网格里扫过时，整列的亮灭必须是「余光级」的，一旦和选中同强，
    // 用户会分不清哪列是真的选上了。
    val hoverTintAlpha = if (isDark) 0.05f else 0.022f
    val hoverTint by animateColorAsState(
        // 已经选中的列不再叠悬停色：同一列上叠两次只会让 α 失控，
        // 而「选中」本来就比「悬停」强，够了
        targetValue = if (hovered && !highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = hoverTintAlpha)
        } else Color.Transparent,
        animationSpec = tween(HOVER_MS),
        label = "dayHover"
    )
    // 高亮列的左右边缘各描一条极细的线：光靠一层浅底色，列的边界在密集网格里读不出来。
    // 颜色跟着 selectTintColor 走，今日是蓝边、非今日是灰边。
    // **只画在列内**（第一条压左边缘、第二条压右边缘内缩 1dp），不向外溢出一像素 ——
    // 向外画会在列间叠出双线、并在最左侧形成一根「多余竖条」。
    // 非今日是灰底，要靠描边把边界「拎」出来，所以 α 比今日更高
    val edgeAlpha by animateFloatAsState(
        targetValue = if (highlighted) 1f else 0f,
        animationSpec = tween(ENTER_MS),
        label = "dayEdge"
    )
    val edgeBaseAlpha = if (isToday) 0.30f else 0.55f
    val edgeColor = selectTintColor.copy(alpha = edgeBaseAlpha * edgeAlpha)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .drawBehind {
                // 今日的常态弱底画在最底层（非选中时才可见）
                drawRect(color = todayBaseTint, size = Size(size.width, gridHeightPx))
                // 悬停底铺在选中底下面：两者互斥（同一列不会同时画两层），顺序其实不影响结果，
                // 但这样写保证「悬停 → 点选中」时，弱的那层先被盖住，过渡不会闪
                drawRect(color = hoverTint, size = Size(size.width, gridHeightPx))
                // 高亮的那一列铺一层底色。只铺到时间轴的真实高度，别盖住为展开预留的空白。
                // **铺满整个宽度**（size.width 已经是含内边距的整列宽），不留缝
                drawRect(color = tint, size = Size(size.width, gridHeightPx))
                // 左右两条描边：只在有高亮时才画。都压在列内，不外溢
                if (edgeAlpha > 0.01f) {
                    val w = 1.dp.toPx()
                    drawRect(color = edgeColor, size = Size(w, gridHeightPx))
                    drawRect(
                        color = edgeColor,
                        topLeft = Offset(size.width - w, 0f),
                        size = Size(w, gridHeightPx)
                    )
                }
            }
    ) {
        placed.forEach { spot ->
            // 块与块之间留出一点缝隙。原来相邻两节课的块是严丝合缝贴在一起的，
            // 上下叠在一起时（比如 08:00-09:35 接着 09:45-11:20）看起来像一整块长条，
            // 分不清是「一节长课」还是「两节连着的课」。
            // 注意：缝隙从**块的高度里扣**而不是往外撑 —— 往外撑会让块超出它自己的时间刻度；
            // 而 `Box` 上再套 `.padding` 等于从内部缩内容，所以高度按原始比例算，
            // 只用 padding 留缝，块的时间边界仍然精确对齐刻度线。
            val blockHeight = (gridHeight * spot.heightFraction)
                .coerceAtLeast(MIN_BLOCK_HEIGHT)
            val showRoom = blockHeight >= BLOCK_ROOM_MIN_HEIGHT
            // 自适应：块越高 → 标题行数越多、字号越大；块矮时缩小字号、减少行数，尽量完整显示
            val blockFontScale = (blockHeight.value / 80f).coerceIn(0.62f, 1f)
            val blockTitleLines = when {
                showRoom -> (blockHeight.value / 20).toInt().coerceIn(1, 4)
                else -> (blockHeight.value / 16).toInt().coerceAtLeast(1)
            }
            Box(
                modifier = Modifier
                    .offset(
                        x = contentWidth * spot.leftFraction,
                        y = gridHeight * spot.topFraction
                    )
                    .fillMaxWidth(spot.widthFraction)
                    .height(blockHeight)
                    // 缝隙只做在块**内部**：块本身的高度仍严格等于它占的时间，
                    // 所以块的上下边界永远压在对应的刻度线上，不会因为留缝而漂移
                    .padding(horizontal = BLOCK_GAP / 2, vertical = BLOCK_GAP / 2)
            ) {
                GridCell(
                    cls = spot.cls,
                    // 「这一格是不是今天」决定选中色条走蓝还是走灰 —— 与列的高亮色同源
                    isToday = isToday,
                    selected = spot.cls.id == selectedClassId,
                    showRoom = showRoom,
                    titleSize = (titleSize.value * blockFontScale).sp,
                    roomSize = (roomSize.value * blockFontScale).sp,
                    titleLines = blockTitleLines,
                    onSelect = onSelectClass,
                    onEdit = onEdit,
                    onHoverChange = onHoverChange
                )
            }
        }
    }
}

/**
 * 一格里的课程块。
 *
 * **尺寸完全由外层按时间轴比例算好**——选中时的"变大"由时间轴的分段线性映射完成，
 * 这里不再自己调高度（两套机制叠加会让块和它自己的时间刻度对不上）。
 * 这里只负责往格子里填内容：平时显示课程名和教室，被选中时再补上教师、周次/日期和起止时间。
 *
 * **性能**：选中动画每帧都会重算 mapping，所以这里必须尽量「跳得过重组」——
 *  - 「够不够高」由外层算好成两个布尔值传进来（[showRoom] / [showDetails]），而不是传每帧都在变的
 *    块高本身。传原始高度等于让每个块每帧都重组一遍
 *  - 也不用 `BoxWithConstraints` 去量高度：一个块一个 SubcomposeLayout，几十个块就是纯浪费
 *  - [onClick] 收 `ClassEntity` 而不是闭包。闭包会被外层每帧新建的 `Placed` 捕获，
 *    参数永远不相等，整块就永远跳不过重组
 *  - 字号和标题行数也由外层按块高自适应算好传进来：块越高字越大、行数越多，块矮时缩小字号
 *    减少行数，尽量让课程名完整显示而不是截断
 *  - 默认只显示科目 + 教室，教师 / 周次 / 起止时间等详情不再在这里渲染（点卡片进编辑页看全）
 */
@Composable
private fun GridCell(
    cls: ClassEntity,
    /** 这一格是不是「今天」那一列的。决定**选中色条**走蓝还是走灰 */
    isToday: Boolean,
    /** 当前是否被选中（单击产生）。选中只做高亮，不进编辑 */
    selected: Boolean,
    showRoom: Boolean,
    titleSize: TextUnit,
    roomSize: TextUnit,
    titleLines: Int,
    /** 单击：只高亮，不进编辑 */
    onSelect: (ClassEntity) -> Unit,
    /** 双击：进编辑 */
    onEdit: (ClassEntity) -> Unit,
    onHoverChange: (Boolean) -> Unit
) {
    // 临时提醒用暖黄底区分。它和「悬停 / 按下 / 选中」是两层不同的信息（一个是分类，
    // 一个是交互），所以交互层用描边、色条、抬起表达，**不去动底色** ——
    // 否则会和「临时」的黄色抢注意力
    val isTemporary = cls.date.isNotEmpty()
    val baseContainer = if (isTemporary) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surface
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f

    val interaction = remember { MutableInteractionSource() }
    val hover = rememberHoverProgress(interaction)
    val pressed by interaction.collectIsPressedAsState()
    val active = maxOf(hover, if (pressed) 1f else 0f)

    // ── 选中进度：0 → 1 一条，所有选中相关的属性由它 lerp ──
    // 和列表卡片 / 便签行共用同一条曲线，三处的选中手感天然一致
    val selection = rememberSelectionProgress(selected)

    // 悬停状态往上报，让整列一起亮（桌面端的「整列高亮」）。
    // 用 hovering 的**布尔原值**而不是动画中的 hover：上报的是「鼠标在不在」这个事实，
    // 动画由列那边自己播 —— 否则每个块各报一个中间值，列会跟着抖。
    // DisposableEffect 的 onDispose 保证块被移出组合（换周 / 滚动）时一定上报离开，
    // 不然悬停色会永远留在那一列上。
    val hovering by interaction.collectIsHoveredAsState()
    DisposableEffect(hovering) {
        onHoverChange(hovering)
        onDispose { if (hovering) onHoverChange(false) }
    }

    // ── 选中色：今日 = 蓝（primary），非今日 = 灰 ──
    // 与列表卡片（HighlightSpec.selectColor）用**同一套取色规则**：
    // 灰不能用 onSurface 调 α —— 深色下 surface 与 onSurface 只差 1.78:1，等于没铺。
    // 深色走 surfaceVariant、浅色走 outlineVariant，都是「比背景深一档」的中性色
    val selectColor = when {
        isToday -> scheme.primary
        isDark -> scheme.surfaceVariant
        else -> scheme.outlineVariant
    }

    // 块底色的默认描边：一块「存在感很轻」的边界。相邻块之间只隔 5dp，
    // 而长期课的底色都是 surface，只靠底色差别分不出两块之间的分界 —— 这根线就是分界本身。
    // 深色下不能靠灰（surface 与 onSurface 只差 1.78:1），所以用一层淡主色
    val defaultOutline = if (isDark) scheme.primary.copy(alpha = 0.16f)
    else scheme.outline.copy(alpha = 0.40f)
    // 悬停 / 按下 / 选中时描边换成明显的主色。格子很小（宽 40~76dp），反馈必须「轻」——
    // 一条细描边 + 一点点抬起就够，画粗了会糊成一团。
    // **终点用 selectColor**：选中态今日描蓝边、非今日描灰边，与左侧色条同色系
    val hoverOutline = scheme.primary.copy(alpha = if (isDark) 0.62f else 0.52f)
    val hoverOrPressOutline = lerpColor(defaultOutline, hoverOutline, active)
    val outlineColor = if (selection > 0.01f) {
        lerpColor(hoverOrPressOutline, selectColor.copy(alpha = 0.80f), selection)
    } else hoverOrPressOutline
    val borderWidth = lerpDp(lerpDp(0.75.dp, 1.dp, active), 1.5.dp, selection)

    // ── 左侧竖色条 ──
    // 未选中：临时课用暖黄（和它的底色同族），长期课用主色 —— 一眼扫过去就能分出
    // 「这周固定的课」和「临时加的课」
    // 选中：统一换成 selectColor（今日=蓝 / 非今日=灰），并加粗 —— 与列表卡片同一套规则
    val accentBarColor = if (isTemporary) {
        lerpColor(scheme.secondary, selectColor, selection)
    } else {
        lerpColor(scheme.primary.copy(alpha = 0.55f), selectColor, selection)
    }
    // 选中色条加粗：2dp → 4dp（HL_BAR_WIDTH）。格子本身小，加到 4dp 已是上限，
    // 再宽就会挤掉课程名的可用宽度
    val accentBarWidth = lerpDp(2.dp, HL_BAR_WIDTH, selection)
    // 选中光晕：向外扩一圈 selectColor。浅色主题下要更实才看得出来
    val selectGlow = selectColor.copy(
        alpha = (if (isDark) 0.30f else 0.17f) * selection
    )

    val scale = 1f - 0.02f * (if (pressed) 1f else 0f) + 0.01f * hover
        // 选中时也轻微放大：格子里只有 40~76dp，放大 5% 就是 2~4dp，够看出「它被挑中了」
        + 0.05f * selection
    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> 1.dp
            selection > 0.5f -> 3.dp
            hover > 0.5f -> 2.dp
            else -> 1.dp
        },
        animationSpec = tween(HOVER_MS),
        label = "cellElevation"
    )
    // 选中底色：一层很淡的 selectColor。**必须和左侧色条同时出现**，否则只加粗一根 4dp 的条
    // 在密集网格里读不出来
    val selectionTint by animateColorAsState(
        targetValue = if (selected) {
            selectColor.copy(alpha = if (isDark) 0.22f else 0.12f)
        } else Color.Transparent,
        animationSpec = tween(ENTER_MS),
        label = "cellSelectionTint"
    )
    // 光晕的圆角半径得先换算成像素。`LocalDensity.current` 不能在 `drawBehind` 的
    // DrawScope 里直接当 Composable 读（它在 lambda 里、不在组合作用域），必须先提出来
    val glowCornerPx = with(LocalDensity.current) {
        SHAPE_SMALL_RADIUS.toPx() + 3.dp.toPx()
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            // 缩放要在最外层：graphicsLayer 只作用于它之后的节点，
            // 挂在里面的话缩的只是文字、卡片底不动。
            // clip = false：选中光晕画在卡片边界之外，裁剪了就看不到
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            // 选中光晕：在卡片底下先铺一圈向外扩的圆角矩形。
            // 用 selectColor 而不是 shadow —— 阴影是灰的、跟主题无关，光晕取自主色/选中色，
            // 浅色深色都能融入，且非今日会自动变灰
            .drawBehind {
                if (selectGlow.alpha > 0.01f) {
                    val glowPx = 3.dp.toPx()
                    drawRoundRect(
                        color = selectGlow,
                        topLeft = Offset(-glowPx, -glowPx),
                        size = Size(size.width + glowPx * 2f, size.height + glowPx * 2f),
                        cornerRadius = CornerRadius(glowCornerPx)
                    )
                }
            }
            .background(selectionTint, SHAPE_SMALL)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                // 单击 / 双击分离：同列表卡片 —— clickable 只负责悬停与按下的 interaction，
                // onClick 留空，真正的分派交给下面的 pointerInput
                onClick = {}
            )
            // 单击 → 高亮；双击 → 编辑。
            // 必须用 detectTapGestures 而不是「clickable + 另一个 clickable」：
            // 后者在双击时会先触发两次单击（高亮闪两下再进编辑），语义和手感都不对。
            // detectTapGestures 自带双击判定窗口，单击回调会**等一个双击超时**后确认，
            // 带来约 300ms 的反馈延迟 —— 这是双击语义的固有代价，无法两全
            .pointerInput(cls.id) {
                detectTapGestures(
                    onTap = { onSelect(cls) },
                    onDoubleTap = { onEdit(cls) },
                    onPress = {
                        // **第一次点下就强制高亮**（不管是准备单击还是双击）。
                        // 理由：onTap 要等双击判定窗口关闭（约 300ms）才回调，
                        // 那段时间里块是「没反应」的 —— 手感很木。
                        // 放在 onPress 里，手指按下的当帧就亮起来，单击/双击共用这一次高亮。
                        // onSelect 必须幂等（它只写 selectedClassId），
                        // 所以这里先亮、之后 onTap 再亮一次也不会有副作用。
                        onSelect(cls)
                        // 把按下转发给 clickable 的 interaction，按下缩小 / 描边反馈保持原样。
                        // 只转发不消费，双击仍能被识别
                        val press = PressInteraction.Press(it)
                        interaction.emit(press)
                        tryAwaitRelease()
                        interaction.emit(PressInteraction.Release(press))
                    }
                )
            },
        shape = SHAPE_SMALL,
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        colors = CardDefaults.cardColors(containerColor = baseContainer),
        // 描边始终存在（默认极淡、悬停转主色、选中转 selectColor）。
        // 宽度与颜色都跟着进度过渡，不会「跳一下」
        border = BorderStroke(width = borderWidth, color = outlineColor)
    ) {
        // 左侧一根竖色条：课程块之间的区分不能只靠文字。它和列表卡片的竖条同源，
        // 未选中时更窄（块本身就小），选中时加粗到 4dp 并把光晕交给卡片层
        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .width(accentBarWidth)
                    .fillMaxHeight()
                    .background(
                        // 选中时走纵向渐变（顶实底淡），和列表卡片的色条一致，有光感
                        if (selection > 0.01f) {
                            Brush.verticalGradient(
                                listOf(
                                    accentBarColor,
                                    accentBarColor.copy(alpha = accentBarColor.alpha * 0.48f)
                                )
                            )
                        } else Brush.verticalGradient(listOf(accentBarColor, accentBarColor))
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 4.dp, end = 4.dp, top = 3.dp, bottom = 3.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = cls.title,
                    fontSize = titleSize,
                    lineHeight = titleSize * 1.2f,
                    fontWeight = FontWeight.Medium,
                    // 标题是块里最重要的信息，用主文字色保证对比度；
                    // 「临时」那层含义交给左侧色条和底色，不再额外压一层颜色到文字上
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = titleLines,
                    overflow = TextOverflow.Ellipsis
                )
                // 块够高时显示教室，否则只留科目名
                if (showRoom && cls.room.isNotBlank()) {
                    Text(
                        text = cls.room,
                        fontSize = roomSize,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                    )
                }
            }
        }
    }
}

// ── 旧版课表（v2.0 / git 版）：按天分组、可折叠 ──────────────────
//
// 设置里「实验性：时间轴网格课表」关掉时，课表用这一版渲染。
// 代码取自 git 里 v2.0 的 `WeekView`，只做了三处必要移植 / 适配：
//  1. **M2 → M3**：`MaterialTheme.colors` → `colorScheme`、`typography.subtitle1` → `titleMedium`、
//     `Card(elevation = 1.dp)` → `CardDefaults.cardElevation(...)`。原版是 M2 写法，现在编不过
//  2. **卡片套用本项目现在的圆角 / 描边**（SHAPE_CARD + cardBorder），否则和别的页面不像一套
//  3. **卡片改成可点击 → 打开编辑对话框**。v2.0 的卡片是不能点的，但本项目的课程编辑 / 删除
//     入口就在「课表 → 点课程」，不可点等于关掉开关之后没法改课程
@Composable
fun LegacyWeekView(classes: List<ClassEntity>, onEdit: (ClassEntity) -> Unit) {
    val grouped = remember(classes) { classes.groupBy { it.dayOfWeek } }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        dayOrder.forEach { day ->
            val dayClasses = grouped[day]?.sortedBy { it.startTime } ?: emptyList()
            item(key = day) {
                var expanded by remember { mutableStateOf(true) }

                Column {
                    // ── 可点击的标题行 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayLabel[day] ?: day,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp
                            else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "折叠${dayLabel[day]}" else "展开${dayLabel[day]}",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // ── 可折叠的课程列表 ──
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            if (dayClasses.isEmpty()) {
                                Text(
                                    text = "无课",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            } else {
                                dayClasses.forEach { cls ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp)
                                            .clickable { onEdit(cls) },
                                        shape = SHAPE_CARD,
                                        colors = CardDefaults.cardColors(
                                            containerColor = MaterialTheme.colorScheme.surface
                                        ),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                        border = cardBorder()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(cls.startTime, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                                Text("-", fontSize = 10.sp)
                                                Text(cls.endTime, fontSize = 13.sp)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(cls.title, fontWeight = FontWeight.SemiBold)
                                                Text(
                                                    "教室: ${cls.room}",
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 分割线
                    Divider(modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

// ── 周课表视图（可折叠） ──────────────────────────────────────────

/** 课表顶部的周次条：显示模式 + 切周 + 校准 */
@Composable
fun WeekHeader(
    currentWeek: Int?,
    shownWeek: Int,
    week1Monday: Long,
    mode: WeekMode,
    onModeChange: (WeekMode) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCalibrate: () -> Unit,
    /** 关掉实验性网格时不显示「表格 / 列表」分段控件 —— 那时候根本没有网格可选 */
    showModeSwitch: Boolean = true
) {
    val dateFormat = remember { java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()) }
    Column {
        // ── 工具行：表格/列表 + 校准 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showModeSwitch) WeekModeSwitch(mode, onModeChange)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCalibrate) {
                Text(if (currentWeek == null) "校准周数" else "校准")
            }
        }
        // ── 周次行 ──
        if (currentWeek == null) {
            Text(
                "周数未校准 · 校准后课表和提醒都会只算本周的课",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrev, enabled = shownWeek > 1) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上一周")
                }
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "第 $shownWeek 周" + if (shownWeek == currentWeek) " · 本周" else "",
                        fontWeight = FontWeight.Bold
                    )
                    val start = WeekSchedule.weekStart(week1Monday, shownWeek)
                    Text(
                        text = "${dateFormat.format(java.util.Date(start))} ~ " +
                            dateFormat.format(java.util.Date(start + 6L * 24 * 60 * 60 * 1000)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = onNext) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下一周")
                }
            }
        }
        Divider()
    }
}

@Composable
fun WeekView(
    classes: List<ClassEntity>,
    currentWeek: Int?,
    week1Monday: Long,
    mode: WeekMode,
    onModeChange: (WeekMode) -> Unit,
    shownWeek: Int,
    onShownWeekChange: (Int) -> Unit,
    onCalibrate: () -> Unit,
    onEdit: (ClassEntity) -> Unit,
    /**
     * 课表用哪种渲染：
     *  - true  → 时间轴网格（实验性，支持点列头高亮 / 加宽）
     *  - false → v2.0 那版「按天分组、可折叠」的列表（[LegacyWeekView]）
     *
     * 两种渲染共用同一个 [WeekHeader]（切周 / 校准照旧可用），只是网格那支
     * 才显示「表格 / 列表」分段控件。
     */
    experimentalGrid: Boolean = true
) {
    // 显示模式和正在浏览的周次都由 MainScreen 持有：切 Tab 回来不会丢
    val today = todayName()
    // 本周要显示的课：临时提醒按具体日期落在哪一周，长期课程按「周数」文本
    val shownWeekStart = if (week1Monday != 0L) WeekSchedule.weekStart(week1Monday, shownWeek) else null
    val visible = if (shownWeekStart == null) classes
    else classes.filter { cls ->
        if (cls.date.isNotEmpty()) {
            TodaySchedule.occursInRange(cls, shownWeekStart, shownWeekStart + 7L * 24 * 60 * 60 * 1000)
        } else {
            WeekSchedule.contains(cls.weeks, shownWeek)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 表头固定：切模式、换周时它都不动，只有下面的内容做过渡
        WeekHeader(
            currentWeek = currentWeek,
            shownWeek = shownWeek,
            week1Monday = week1Monday,
            mode = mode,
            onModeChange = onModeChange,
            onPrev = { if (shownWeek > 1) onShownWeekChange(shownWeek - 1) },
            onNext = { onShownWeekChange(shownWeek + 1) },
            onCalibrate = onCalibrate,
            showModeSwitch = experimentalGrid
        )
        // ── 渲染方式二选一 ──
        // 两条路径各自抽成独立组件，这里只剩一个分支，一眼能看出当前用的是哪套渲染
        if (experimentalGrid) {
            ExperimentalWeekContent(
                classes = classes,
                week1Monday = week1Monday,
                currentWeek = currentWeek,
                mode = mode,
                shownWeek = shownWeek,
                today = today,
                onEdit = onEdit
            )
        } else {
            LegacyWeekView(classes = visible, onEdit = onEdit)
        }
    }
}

/**
 * 实验性渲染：时间轴网格 / 列表两种模式，外加切模式与换周的过渡。
 *
 * 单独抽出来是为了让 [WeekView] 里的分支只剩「两选一」。这里的周次过滤按**目标周次**算，
 * 所以换周滑动时新旧两周各自显示各自的内容。
 */
@Composable
private fun ExperimentalWeekContent(
    classes: List<ClassEntity>,
    week1Monday: Long,
    currentWeek: Int?,
    mode: WeekMode,
    shownWeek: Int,
    today: String,
    onEdit: (ClassEntity) -> Unit
) {
    // 内容的过渡同时管两件事，靠 transitionSpec 区分：
    //  - 切「表格 / 列表」→ 淡入淡出 + 轻微横向位移，方向跟着分段控件的左右位置走
    //  - 换周 → 整屏横向滑过，方向跟着「上一周 / 下一周」
    AnimatedContent(
        targetState = mode to shownWeek,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            if (targetState.first != initialState.first) {
                val toGrid = targetState.first == WeekMode.GRID
                (fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { if (toGrid) -it / 18 else it / 18 }) togetherWith
                    (fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(EXIT_MS)) { if (toGrid) it / 18 else -it / 18 })
            } else {
                val dir = if (targetState.second > initialState.second) 1 else -1
                (fadeIn(tween(ENTER_MS)) + slideInHorizontally(tween(ENTER_MS)) { dir * it / 6 }) togetherWith
                    (fadeOut(tween(EXIT_MS)) + slideOutHorizontally(tween(EXIT_MS)) { -dir * it / 6 })
            }
        },
        label = "weekContent"
    ) { (targetMode, targetWeek) ->
        // 每一帧都按「它自己那一周」过滤，所以滑动时新旧两周各自显示各自的内容
        val weekStart = if (week1Monday != 0L) WeekSchedule.weekStart(week1Monday, targetWeek) else null
        val visible = if (weekStart == null) classes
        else classes.filter { cls ->
            // 临时提醒按具体日期落在哪一周来显示，长期课程按「周数」文本
            if (cls.date.isNotEmpty()) {
                TodaySchedule.occursInRange(cls, weekStart, weekStart + 7L * 24 * 60 * 60 * 1000)
            } else {
                WeekSchedule.contains(cls.weeks, targetWeek)
            }
        }
        // 只有正在看本周时，今天的课才值得高亮
        val dayHighlight = if (currentWeek != null && targetWeek == currentWeek) today else null
        when (targetMode) {
            WeekMode.GRID -> WeekGrid(
                classes = visible,
                highlightDay = dayHighlight,
                onEdit = onEdit
            )
            WeekMode.LIST -> WeekDayList(
                classes = visible,
                highlightDay = dayHighlight,
                onEdit = onEdit
            )
        }
    }
}

/**
 * 列表模式：按星期分组、可折叠。
 *
 * 课程卡片支持**单击高亮**：点中的那张会亮起描边、竖条换成渐变、外扩一层光晕，
 * 再点一次或点空白处取消。和便签列表的选中是同一套交互语言
 * （选中进度、动画时长、线条规范都复用），两个界面的手感一致。
 *
 * 另外，**正在上课**的那一节会额外亮一颗小圆点和一层柔光晕 —— 这是时间维度上的「现在」，
 * 和手动选中的「焦点」是两件事，可以同时成立，但用不同的视觉语言表达（圆点 vs 描边）。
 */
@Composable
fun WeekDayList(
    classes: List<ClassEntity>,
    highlightDay: String?,
    onEdit: (ClassEntity) -> Unit
) {
    val grouped = classes.groupBy { it.dayOfWeek }
    // 当前高亮的课程 id。同一时间只允许一张卡片亮着，点另一张会自动转移过去
    var selectedId by remember { mutableStateOf<Int?>(null) }
    // 切模式 / 换周时列表会被重建，顺手把选中清掉，免得留着一张「已经不在列表里」的高亮卡片
    LaunchedEffect(classes) { selectedId = null }

    // ── 「正在上课」的那一节 ──
    // 只在正在浏览本周时才成立（看别的周时「现在」根本不在那一屏里）。
    // 时间比较用「当天第几分钟」而不是毫秒，跨天 / 跨时区的边界由 highlightDay 兜住。
    val nowMinute = remember(highlightDay) { minuteOfDay(System.currentTimeMillis()) }
    val ongoingId = remember(classes, highlightDay, nowMinute) {
        if (highlightDay == null || nowMinute < 0) null
        else classes.firstOrNull { cls ->
            cls.dayOfWeek == highlightDay && nowMinute in cls.startMinute..cls.endMinute
        }?.id
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            // 点空白处取消选中。卡片自己会把点击消费掉，所以这里只接住「没人要的」点击
            .pointerInput(Unit) {
                detectTapGestures { selectedId = null }
            },
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
    ) {
        dayOrder.forEach { day ->
            val dayClasses = grouped[day]?.sortedBy { it.startTime } ?: emptyList()
            item(key = day) {
                var expanded by remember { mutableStateOf(true) }
                val isToday = day == highlightDay
                // 箭头靠旋转表达「展开 / 收起」，比来回换两个图标连贯得多
                val arrowRotation by animateFloatAsState(
                    targetValue = if (expanded) 0f else 180f,
                    animationSpec = tween(ENTER_MS),
                    label = "dayArrow"
                )
                val titleColor by animateColorAsState(
                    targetValue = if (isToday) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    animationSpec = tween(ENTER_MS),
                    label = "dayTitleColor"
                )

                Column(modifier = Modifier.padding(top = 4.dp)) {
                    // ── 可点击的标题行 ──
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { expanded = !expanded }
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dayLabel[day] ?: day,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = titleColor,
                            modifier = Modifier.weight(1f)
                        )
                        if (isToday) {
                            TodayChip()
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = if (dayClasses.isEmpty()) "无课" else "${dayClasses.size} 节",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = if (expanded) "折叠${dayLabel[day]}" else "展开${dayLabel[day]}",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.graphicsLayer { rotationZ = arrowRotation }
                        )
                    }

                    // ── 可折叠的课程列表：展开 / 收起都做高度动画，不是「唰」地出现 ──
                    AnimatedVisibility(
                        visible = expanded,
                        enter = fadeIn(tween(ENTER_MS)) +
                            expandVertically(tween(ENTER_MS), expandFrom = Alignment.Top),
                        exit = fadeOut(tween(EXIT_MS)) +
                            shrinkVertically(tween(EXIT_MS), shrinkTowards = Alignment.Top)
                    ) {
                        Column {
                            dayClasses.forEach { cls ->
                                WeekClassCard(
                                    cls = cls,
                                    isToday = isToday,
                                    selected = selectedId == cls.id,
                                    // 正在上课的只可能有一节，用 id 比对；下课时间一到它会自己熄掉
                                    ongoing = cls.id == ongoingId,
                                    // 单击：只做高亮，**不进编辑** —— 浏览多家课程时鼠标划一下就改课，
                                    // 是不可接受的。已经是选中态了就保持（不取消、不编辑）
                                    onSelect = { selectedId = cls.id },
                                    // 双击：才进编辑。这是唯一入口，代价是「要改必须双击」，
                                    // 但换来的是「随便点绝不会误改」
                                    onEdit = { onEdit(cls) }
                                )
                            }
                            if (dayClasses.isEmpty()) {
                                Text(
                                    text = "今天没有课，休息一下",
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontSize = 13.sp,
                                    modifier = Modifier.padding(start = 12.dp, bottom = 6.dp, top = 2.dp)
                                )
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
    }
}

/**
 * 课表列表里的课程卡片：左侧时间列 + 右侧信息。
 *
 * **高亮线条**：卡片外框自己画，不用 `Card(border = ...)`。竖条 / 描边 / 外光晕三样
 * 由 [drawHighlightLines] 一笔画出，共用同一份圆角几何。各属性由 [HighlightSpec] 统一算好，
 * 强度按「今天(弱) → 悬停(中) → 按下 → 选中(强)」四级递进：
 *
 * | 属性 | 未高亮 | 今天(弱) | 选中(强) |
 * |---|---|---|---|
 * | 竖条 | 主色 α0.16，0.3×宽 | 主色 α0.30，0.56×宽 | 主色纵向渐变，1×宽 |
 * | 卡片描边 | 中性 outline α0.78，0.4×宽 | 中性偏主色，0.62×宽 | 主色实线 1.6dp |
 * | 外光晕 | 无 | 无 | 主色 α0.17 / 深色 α0.30 |
 * | 卡片底色 | surface | surface | primaryContainer 混合 |
 *
 * 悬停 / 按下 / 选中都只是同一条进度上的不同刻度，所以不会叠出「第五种颜色」，
 * 状态互相切换时也是平滑滑动，不是换一套样式。
 *
 * 宽度与透明度都由进度 `lerp` 出来，所以点选 / 取消时线条是「长出来 / 收回去」，
 * 而不是瞬间换一套。和便签行共用 [rememberSelectionProgress]，两处的时长与缓动天然一致。
 */
@Composable
fun WeekClassCard(
    cls: ClassEntity,
    isToday: Boolean,
    selected: Boolean = false,
    /** 是否正在上课（时间维度上的「现在」）。用蓝色光晕 + 竖条呼吸表达，和「选中」区分开 */
    ongoing: Boolean = false,
    /** 单击：只高亮，不进编辑 */
    onSelect: () -> Unit,
    /** 双击：进编辑 */
    onEdit: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val selection = rememberSelectionProgress(selected)
    // 悬停 / 按下都从同一个 interactionSource 读：桌面端点一下之前就已经有预览反馈
    val hover = rememberHoverProgress(interaction)
    val pressed by interaction.collectIsPressedAsState()
    // 进行中单独一条进度：它和「选中」是两件事，可以同时成立（正在上的那节被点开）
    val ongoingProgress by animateFloatAsState(
        targetValue = if (ongoing) 1f else 0f,
        animationSpec = tween(SELECT_ENTER_MS),
        label = "ongoing"
    )

    // 卡片自己的基础皮肤。选中后的替换色由 HighlightSpec 算，这里只给「未选中长什么样」
    val baseContainer = if (cls.date.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surface

    val spec = HighlightSpec.of(
        baseContainer = baseContainer,
        baseTitle = MaterialTheme.colorScheme.onSurface,
        selection = selection,
        // 今天那一列是「弱高亮」：只给竖条 + 略深的描边，不给光晕 ——
        // 否则本周视图里一屏会有好几张发光的卡片，重点就散了
        accent = if (isToday) 1f else 0f,
        // 选中色条走蓝还是走灰，由「这一项是不是今天」决定
        isToday = isToday,
        pressed = pressed,
        hovering = hover > 0.5f
    )
    // 进行中：叠一层自己的光晕（用 secondary 主色的对比色系不好找，直接复用主色但更柔）
    val ongoingGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f * ongoingProgress)

    // 三样叠加：选中的强光晕 + 进行中的柔光晕，取较强的那一层即可，叠两层会过曝
    val glowColor = if (ongoingProgress > 0.5f && selection < 0.5f) ongoingGlow else spec.glowColor

    // 卡片按下 / 悬停时轻微抬起：用阴影而不是再画一条线，避免视觉噪声
    val elevation by animateDpAsState(
        targetValue = when {
            pressed -> 1.dp
            selection > 0.5f -> 3.dp
            hover > 0.5f -> 2.dp
            else -> 0.dp
        },
        animationSpec = tween(ENTER_MS),
        label = "cardElevation"
    )

    // 竖条上下端点由卡片实际高度算：外层 Row 有 CARD_V_PADDING 的垂直 padding，
    // 直接用 DrawScope 的 size.height 会把竖条画到卡片外
    val density = LocalDensity.current
    val vPadPx = with(density) { CARD_V_PADDING.toPx() }
    val borderPx = with(density) { spec.borderWidth.toPx() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            // 描边 / 光晕画在卡片边界之外，得让 Card 不裁剪才看得到
            .graphicsLayer { clip = false }
            .drawBehind {
                // 竖条两端各内缩 CARD_V_PADDING + 描边宽度，正好落在卡片圆角起点之上
                val barTop = vPadPx + borderPx
                val barHeight = size.height - barTop * 2f
                drawHighlightLines(
                    cornerRadius = SHAPE_CARD_RADIUS,
                    lines = HighlightLines(
                        barBrush = spec.barBrush,
                        barWidth = spec.barWidth,
                        borderColor = spec.borderColor,
                        borderWidth = spec.borderWidth,
                        glowColor = glowColor
                    ),
                    barTop = barTop,
                    barHeight = barHeight
                )
            }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                // 单击 / 双击分离：这里把「点击」的最终判定交给下面手势层，
                // clickable 只负责悬停与按下的 interaction（外观反馈仍走它），
                // onClick 留空 —— 真正的高亮 / 编辑在 pointerInput 里分派
                onClick = {}
            )
            // 单击 → 高亮；双击 → 编辑。
            // 必须用 detectTapGestures 而不是两个 clickable 叠加：
            // 后者会在双击时先触发两次单击（高亮闪两下再进编辑），语义和手感都不对。
            // detectTapGestures 自带双击判定窗口，onTap 要等窗口关闭才回调 ——
            // 所以高亮**提前到 onPress**（见下），不等 onTap。
            .pointerInput(cls.id) {
                detectTapGestures(
                    onTap = { onSelect() },
                    onDoubleTap = { onEdit() },
                    onPress = {
                        // **第一次点下就强制高亮**（不管是准备单击还是双击）。
                        // onTap 要等双击判定窗口关闭（约 300ms）才回调，那段时间块是「没反应」的；
                        // 放在 onPress 里，按下的当帧就亮。onSelect 幂等（只写 selectedId），
                        // 之后 onTap 再亮一次也无副作用
                        onSelect()
                        // 让 clickable 的 interaction 感知到按下，按下缩小 / 描边反馈保持原样。
                        // 这里只做「转发」，不消费事件，双击仍能被上面识别
                        val press = PressInteraction.Press(it)
                        interaction.emit(press)
                        tryAwaitRelease()
                        interaction.emit(PressInteraction.Release(press))
                    }
                )
            },
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = spec.containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        // 描边改成 drawBehind 自绘（圆角更可控），Card 自己的 border 置空
        border = null
    ) {
        // IntrinsicSize：左侧色条跟着卡片实际高度走
        Row(
            modifier = Modifier.height(IntrinsicSize.Min).padding(vertical = CARD_V_PADDING),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧竖条：宽度由 HighlightSpec 决定。它本身不画颜色 ——
            // 颜色 / 渐变统一交给 drawHighlightLines，避免两套几何各画各的
            Spacer(Modifier.width(spec.barWidth))
            Spacer(Modifier.width(CARD_BAR_GAP))
            Column(
                modifier = Modifier.width(58.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    cls.startTime,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    cls.endTime,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            // 时间列与信息列之间的分隔线：压短成一截短线，并且选中时染上主色
            // —— 一条通高的灰线会把卡片「切成两半」，短线更像排版里的次分隔
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(
                        lerpColor(
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.30f),
                            maxOf(selection, hover * 0.5f)
                        )
                    )
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cls.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // 标题色由 HighlightSpec 给：选中时加深，提升与底色的对比度
                        color = spec.titleColor,
                        // fill=false：标题短的时候角标紧跟其后，不会被推到行尾
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (cls.date.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        TemporaryBadge()
                    }
                    // 「正在上课」的小圆点：和竖条的呼吸同频，一眼看出「现在上的是这节」
                    if (ongoing) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .alpha(0.55f + 0.45f * ongoingProgress)
                        )
                    }
                }
                if (cls.room.isNotBlank()) {
                    Text(
                        "教室 ${cls.room}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                // 「临时」已经由标题右侧的黄色角标表达，这一行就不再重复写「临时提醒」
                val extra = listOf(cls.teacher, if (cls.date.isEmpty()) cls.weeks else "")
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (extra.isNotBlank()) {
                    Text(
                        extra,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

// ── 设置页面（独立全屏） ──────────────────────────────────────────

@Composable
fun SettingsPage(
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onImportTimetable: () -> Unit
) {
    val ctx = LocalContext.current
    val initialAdvance = remember { runCatching { Prefs.getAdvanceMinutes(ctx) }.getOrDefault(30) }
    var advance by remember { mutableStateOf(initialAdvance.toString()) }
    val initialAutoStart = remember { runCatching { Prefs.getAutoStart(ctx) }.getOrDefault(false) }
    var autoStart by remember { mutableStateOf(initialAutoStart) }
    val initialShowPopup = remember { runCatching { Prefs.getShowPopup(ctx) }.getOrDefault(true) }
    var showPopupPref by remember { mutableStateOf(initialShowPopup) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            // ── 提醒 ──
            SectionTitle("提醒")
            SettingsCard {
                OutlinedTextField(
                    value = advance,
                    onValueChange = { input ->
                        // 只收数字，避免写进脏值（真正的范围夹取在 Prefs 里统一做）
                        advance = input.filter { it.isDigit() }.take(3)
                        advance.toIntOrNull()?.let { Prefs.setAdvanceMinutes(ctx, it) }
                    },
                    label = { Text("提前提醒（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "1~180 分钟，超出会自动夹住",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                )
                Spacer(Modifier.height(10.dp))
                SwitchRow(
                    label = "开机自启",
                    checked = autoStart,
                    onCheckedChange = {
                        autoStart = it
                        Prefs.setAutoStart(ctx, it)
                    }
                )
                SwitchRow(
                    label = "锁屏弹窗",
                    checked = showPopupPref,
                    onCheckedChange = {
                        showPopupPref = it
                        Prefs.setShowPopup(ctx, it)
                    }
                )
                Text(
                    "开启后到点会亮屏并弹窗提醒；部分系统（如 ColorOS）会拦掉全屏弹窗，" +
                        "此时仍会发高优先级通知并亮屏。",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                )
            }

            // ── 外观 ──
            SectionTitle("外观")
            SettingsCard {
                ThemeMode.entries.forEachIndexed { index, mode ->
                    val picked = themeMode == index
                    // 选中的那一行铺一层淡主色，切换时淡入淡出，光标落到哪一行一眼看得出
                    val rowBackground by animateColorAsState(
                        targetValue = if (picked) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent,
                        animationSpec = tween(ENTER_MS),
                        label = "themeRow"
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(rowBackground)
                            .clickable { onThemeModeChanged(index) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = picked,
                            onClick = { onThemeModeChanged(index) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(mode.label, fontSize = 15.sp)
                    }
                }
            }

            // ── 通知权限 ──
            SectionTitle("通知权限")
            SettingsCard {
                Text(
                    "没有通知权限时，锁屏提醒和前台服务都无法工作。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onRequestNotificationPermission, modifier = Modifier.fillMaxWidth()) {
                    Text("请求通知权限")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("打开系统设置")
                }
            }

            // ── 国内 ROM 的特有权限（ColorOS/OxygenOS/realme UI）──
            if (RomGuide.isOplus) {
                SectionTitle("系统权限（${Build.MANUFACTURER}）")
                SettingsCard {
                    Text(
                        "这几项是厂商特有权限，系统不提供查询和申请接口，只能手动开。" +
                            "不开的话：锁屏弹窗会被系统拦掉，提醒服务也可能被省电策略清掉。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(12.dp))
                    RomGuideRow(
                        title = "背景弹出界面",
                        detail = "不开的话，到点时拉不起锁屏弹窗（ColorOS 会直接拦掉）",
                        onClick = { RomGuide.openSpecialAccess(ctx) }
                    )
                    Spacer(Modifier.height(8.dp))
                    RomGuideRow(
                        title = "自启动 / 允许后台运行",
                        detail = "在应用详情页里的「用电量管理」中开启，否则提醒服务会被清掉",
                        onClick = { RomGuide.openAppDetails(ctx) }
                    )
                }
            }

            // ── 导入课表 ──
            SectionTitle("导入课表")
            SettingsCard {
                Text(
                    "支持教务系统导出的课表 PDF；同一门课重复导入会覆盖，不会重复添加。" +
                        "PDF 里没有具体时刻，导入后按默认作息推算（可在列表里逐条修改）。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = onImportTimetable, modifier = Modifier.fillMaxWidth()) {
                    Text("选择课表 PDF 导入")
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 6.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // M3 规范里的「填充卡」：用容器色（surfaceVariant 就是浅色下最接近 surfaceContainer 的那一档），
    // 不投影、只描一条极淡的边。Google 设置页的分组卡就是这个形态。
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            content = content
        )
    }
}

@Composable
private fun RomGuideRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(
                detail,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Spacer(Modifier.width(8.dp))
        TextButton(onClick = onClick) { Text("去开启") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp, color = scheme.onSurface)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            // M3 开关配色：打开 = primary 轨道 + onPrimary 滑块；
            // 关闭 = surfaceVariant 轨道 + outline 滑块（滑块比轨道亮，停在左边时仍看得见）。
            // 原来「打开」用的是 primary α0.5 的轨道，颜色发灰，和 Google 应用的开关不像
            colors = SwitchDefaults.colors(
                checkedThumbColor = scheme.onPrimary,
                checkedTrackColor = scheme.primary,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = scheme.outline,
                uncheckedTrackColor = scheme.surfaceVariant,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

// ── 添加/编辑对话框 ──────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDialog(
    initial: ClassEntity? = null,
    oneOff: Boolean = false,
    onSave: (ClassEntity) -> Unit,
    onDismiss: () -> Unit,
    // 列表页被便签替换后，这里成了唯一的课程删除入口（课表里点格子 → 编辑 → 删除）
    onDelete: (() -> Unit)? = null
) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var day by remember { mutableStateOf(initial?.dayOfWeek ?: "Monday") }
    var dayExpanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var startTime by remember { mutableStateOf(initial?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(initial?.endTime ?: "10:00") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var room by remember { mutableStateOf(initial?.room ?: "") }
    var teacher by remember { mutableStateOf(initial?.teacher ?: "") }
    var weeks by remember { mutableStateOf(initial?.weeks ?: "") }
    // 临时提醒用具体日期代替「星期 + 周数」
    var date by remember {
        mutableStateOf(
            initial?.date?.takeIf { it.isNotEmpty() } ?: TodaySchedule.dateOf(System.currentTimeMillis())
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateValid = TodaySchedule.isValidDate(date)
    val timeRegex = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")
    fun toMinutes(t: String): Int? {
        val parts = t.split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return h * 60 + m
    }
    val startValid = timeRegex.matches(startTime)
    val endValid = timeRegex.matches(endTime)
    val startBeforeEnd = if (startValid && endValid) {
        val s = toMinutes(startTime) ?: -1
        val e = toMinutes(endTime) ?: -1
        e > s
    } else false

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                when {
                    initial == null && oneOff -> "添加临时提醒"
                    initial == null -> "添加长期提醒"
                    oneOff -> "编辑临时提醒"
                    else -> "编辑长期提醒"
                }
            )
        },
        text = {
            // 限高 + 可滚动：字段多了以后键盘弹出会把 Save/Cancel 顶到键盘下面，真机上点不到
            Column(
                modifier = Modifier
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("课程名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (oneOff) {
                    // 临时提醒：直接选日期，只生效一次
                    // readOnly 的输入框会自己吃掉点击，所以盖一层透明可点区域，整行都能点开选择器
                    Box {
                        OutlinedTextField(
                            value = date,
                            onValueChange = {},
                            label = { Text("日期") },
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                Icon(
                                    Icons.Default.DateRange,
                                    contentDescription = "选择日期",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable { showDatePicker = true }
                        )
                    }
                    if (showDatePicker) {
                        LaunchedEffect(Unit) {
                            val cal = java.util.Calendar.getInstance()
                            runCatching {
                                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                                    .parse(date)
                            }.getOrNull()?.let { cal.timeInMillis = it.time }
                            android.app.DatePickerDialog(
                                ctx as? android.app.Activity ?: ctx,
                                { _, year, month, dayOfMonth ->
                                    date = String.format(
                                        java.util.Locale.getDefault(),
                                        "%04d-%02d-%02d", year, month + 1, dayOfMonth
                                    )
                                },
                                cal.get(java.util.Calendar.YEAR),
                                cal.get(java.util.Calendar.MONTH),
                                cal.get(java.util.Calendar.DAY_OF_MONTH)
                            ).show()
                            showDatePicker = false
                        }
                    }
                    Text(
                        "临时提醒只在这一天生效一次",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                } else {
                    ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = !dayExpanded }) {
                        OutlinedTextField(
                            value = dayLabel[day] ?: day,
                            onValueChange = {},
                            label = { Text("星期") },
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                            // M3 的下拉菜单需要锚点
                            modifier = Modifier.fillMaxWidth().menuAnchor()
                        )
                        ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                            dayOrder.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(dayLabel[d] ?: d) },
                                    onClick = { day = d; dayExpanded = false }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = startTime,
                        onValueChange = {},
                        label = { Text("开始时间") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_notification_clock),
                                contentDescription = "选择开始时间",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showStartPicker = true }
                    )
                }
                if (showStartPicker) {
                    LaunchedEffect(Unit) {
                        val parts = startTime.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 9
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val picker = TimePickerDialog(ctx as? android.app.Activity ?: ctx, { _, hourOfDay, minute ->
                            startTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                        }, h, m, true)
                        picker.show()
                        showStartPicker = false
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedTextField(
                        value = endTime,
                        onValueChange = {},
                        label = { Text("结束时间") },
                        readOnly = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_notification_clock),
                                contentDescription = "选择结束时间",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showEndPicker = true }
                    )
                }
                if (showEndPicker) {
                    LaunchedEffect(Unit) {
                        val parts = endTime.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 10
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val picker = TimePickerDialog(ctx as? android.app.Activity ?: ctx, { _, hourOfDay, minute ->
                            endTime = String.format(java.util.Locale.getDefault(), "%02d:%02d", hourOfDay, minute)
                        }, h, m, true)
                        picker.show()
                        showEndPicker = false
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = room,
                    onValueChange = { room = it },
                    label = { Text("教室") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it },
                    label = { Text("教师") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (!oneOff) {
                    OutlinedTextField(
                        value = weeks,
                        onValueChange = { weeks = it },
                        label = { Text("周数（如 1-16周 / 第6周）") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (!startValid) Text("开始时间格式应为 HH:mm", color = MaterialTheme.colorScheme.error)
                if (!endValid) Text("结束时间格式应为 HH:mm", color = MaterialTheme.colorScheme.error)
                if (startValid && endValid && !startBeforeEnd) Text("结束时间必须晚于开始时间", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                // 毫秒级 id：原来按秒取整，同一秒内添加两节课会被 REPLACE 静默覆盖掉一节
                val id = initial?.id ?: (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                onSave(
                    ClassEntity(
                        id = id,
                        title = title,
                        // 临时提醒的星期由日期推出来（课表按星期分组时用得上）
                        dayOfWeek = if (oneOff) TodaySchedule.dayNameOfDate(date) ?: day else day,
                        startTime = startTime,
                        endTime = endTime,
                        room = room,
                        notes = initial?.notes.orEmpty(),
                        teacher = teacher.trim(),
                        weeks = if (oneOff) "" else weeks.trim(),
                        date = if (oneOff) date else ""
                    )
                )
            }, enabled = startValid && endValid && startBeforeEnd && title.isNotBlank() &&
                (!oneOff || dateValid)) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("删除", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        }
    )
}
