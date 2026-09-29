package com.example.classreminder.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.NoteEntity
import com.example.classreminder.data.TodaySchedule
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * 「今天」页 —— 首屏。
 *
 * 设计意图：用户打开 App 想知道的只有一件事 —— **我现在该干什么？**
 * 所以这一页按「紧迫度」而非「数据结构」排布：
 *
 *  1. **当前 / 下一节课**（主卡）— 正在上课时是实心主色卡 + 倒计时，
 *     这是全屏最高优先级的信息；没课/课间则显示下一节。
 *  2. **今天剩余**（时间轴列表）— 一眼扫完后面还有什么。
 *  3. **便签摘要**（最多 2 条）— 便签不再占首屏，但需要时伸手可及。
 *
 * 时间每 30 秒刷新一次，所以「还剩 N 分钟」会自己走字，不用退回重进。
 */
@Composable
fun TodayScreen(
    classes: List<ClassEntity>,
    notes: List<NoteEntity>,
    currentWeek: Int?,
    onOpenClass: (ClassEntity) -> Unit,
    onOpenNotes: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 「现在」每 30 秒走一格。前台 Service 也是 30 秒轮询，两者节奏一致
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000L)
            now = System.currentTimeMillis()
        }
    }

    val today = remember(classes, currentWeek, now) {
        TodaySchedule.today(classes, currentWeek, now)
    }
    val upcoming = remember(today, now) {
        today.filter { it.endMillis > now }
    }
    val current = upcoming.firstOrNull { it.ongoingAt(now) }
    // 主卡优先展示「正在上的」，没有则展示「下一节」
    val featured = current ?: upcoming.firstOrNull()
    // 列表里去掉已经在主卡里展示的那一节
    val rest = upcoming.filter { it !== featured }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 1. 主卡：当前 / 下一节课 ──
        item(key = "featured") {
            if (featured != null) {
                FeaturedClassCard(
                    entry = featured,
                    ongoing = current != null,
                    now = now,
                    onClick = { onOpenClass(featured.entity) }
                )
            } else {
                EmptyTodayCard(hasClassToday = today.isNotEmpty())
            }
        }

        // ── 2. 今天剩余 ──
        if (rest.isNotEmpty()) {
            item(key = "rest-header") {
                SectionLabel(if (current != null) "接下来" else "今天的课")
            }
            items(rest.size, key = { rest[it].entity.id }) { index ->
                val entry = rest[index]
                UpcomingRow(entry = entry, now = now, onClick = { onOpenClass(entry.entity) })
            }
        }

        // ── 3. 便签摘要 ──
        if (notes.isNotEmpty()) {
            item(key = "notes-header") {
                SectionLabel("便签 · ${notes.size} 条", onClick = onOpenNotes)
            }
            items(minOf(notes.size, 2), key = { "note-" + notes[it].id }) { index ->
                NoteSummaryRow(notes[index], onClick = onOpenNotes)
            }
        }
    }
}

/** 小节标题：灰色小字，右侧可带「查看全部」 */
@Composable
private fun SectionLabel(text: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(start = 4.dp, end = 4.dp, top = 12.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        if (onClick != null) {
            Text(
                text = "全部",
                fontSize = 12.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/**
 * 主卡 —— 全屏视觉权重最高的一张。
 *
 * 正在上课时用**实心主色**卡：这是 Google Calendar「Now」卡的同一手法 ——
 * 把「时间上的此刻」用颜色本身表达，不靠任何图标或文字解释。
 */
@Composable
private fun FeaturedClassCard(
    entry: TodaySchedule.TodayClass,
    ongoing: Boolean,
    now: Long,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    // 运行中 → 实心主色；否则 → 普通卡片 + 主色描边
    val container by animateColorAsState(
        targetValue = if (ongoing) scheme.primary else scheme.surfaceVariant,
        animationSpec = tween(ENTER_MS),
        label = "featuredContainer"
    )
    val contentColor = if (ongoing) scheme.onPrimary else scheme.onSurface
    val subColor = if (ongoing) scheme.onPrimary.copy(alpha = 0.85f) else scheme.onSurfaceVariant

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .clickable(onClick = onClick),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // ── 状态行：圆点 + 文案 ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (ongoing) contentColor else scheme.primary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (ongoing) {
                        val left = ((entry.endMillis - now) / 60_000L).coerceAtLeast(0)
                        "正在上课 · 还剩 $left 分钟"
                    } else {
                        val until = ((entry.startMillis - now) / 60_000L).coerceAtLeast(0)
                        when {
                            until <= 1 -> "马上开始"
                            until < 60 -> "$until 分钟后开始"
                            else -> "${until / 60} 小时 ${until % 60} 分钟后开始"
                        }
                    },
                    fontSize = 12.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = if (ongoing) contentColor else scheme.primary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = entry.entity.title,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = buildString {
                    append("${entry.entity.startTime} – ${entry.entity.endTime}")
                    if (entry.entity.room.isNotBlank()) append(" · ${entry.entity.room}")
                    if (entry.entity.teacher.isNotBlank()) append(" · ${entry.entity.teacher}")
                },
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = subColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 今天完全没课时 / 今天的课上完了 */
@Composable
private fun EmptyTodayCard(hasClassToday: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (hasClassToday) "今天的课上完了" else "今天没有课",
                fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (hasClassToday) "好好休息，或者看看便签" else "享受闲暇的一天",
                fontSize = 13.sp,
                color = scheme.onSurfaceVariant
            )
        }
    }
}

/** 时间轴列表里的一行 */
@Composable
private fun UpcomingRow(
    entry: TodaySchedule.TodayClass,
    now: Long,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val ongoing = entry.ongoingAt(now)
    val isTemp = entry.entity.date.isNotEmpty()

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 时间列：固定宽度，让所有行的时间左对齐成一条线
            Text(
                text = entry.entity.startTime,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = scheme.primary,
                modifier = Modifier.width(46.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.entity.title,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                    color = scheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (entry.entity.room.isNotBlank() || entry.entity.teacher.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            if (entry.entity.room.isNotBlank()) append(entry.entity.room)
                            if (entry.entity.teacher.isNotBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(entry.entity.teacher)
                            }
                        },
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (isTemp) {
                Spacer(Modifier.width(8.dp))
                TemporaryBadge()
            }
        }
    }
}

/** 便签摘要行 —— 只读展示，点进去到便签页 */
@Composable
private fun NoteSummaryRow(note: NoteEntity, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.primary)
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = note.text,
                fontSize = 13.sp,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** 今天日期 + 星期 + 周次的那行副标题（给顶部栏用） */
fun todaySubtitle(currentWeek: Int?, now: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = now }
    val month = cal.get(Calendar.MONTH) + 1
    val day = cal.get(Calendar.DAY_OF_MONTH)
    val weekday = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "周一"
        Calendar.TUESDAY -> "周二"
        Calendar.WEDNESDAY -> "周三"
        Calendar.THURSDAY -> "周四"
        Calendar.FRIDAY -> "周五"
        Calendar.SATURDAY -> "周六"
        else -> "周日"
    }
    return buildString {
        append("$month 月 $day 日 $weekday")
        if (currentWeek != null) append(" · 第 $currentWeek 周")
    }
}

/**
 * 按当前时刻给出的问候语。顶栏大标题用它替代原来的「今天」。
 *
 * 分档按一天的作息节奏，而不是均分 24 小时 —— 均分出来的边界（比如 12:00 一刀切）
 * 会让人觉得「中午 12 点还算上午」很别扭。这里取的是日常口语的边界：
 *
 * - 05:00–08:59 **早上好** —— 起床到出门
 * - 09:00–11:59 **午安**   —— 上午到中午（用户原话这一档叫「午安」，不走「上午好」）
 * - 12:00–17:59 **下午好** —— 午休后到傍晚
 * - 18:00–22:59 **晚上好** —— 入夜
 * - 23:00–04:59 **夜深了** —— 深夜，语气从问候变成关照
 *
 * 边界值（整点）归**后一档**：08:59 还是早上好，09:00 就切到午安，
 * 和「到点就换」的直觉一致。传入的 [millis] 是本地时间戳。
 */
fun greetingFor(millis: Long): String {
    val hour = Calendar.getInstance().apply { timeInMillis = millis }
        .get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..8 -> "早上好。"
        in 9..11 -> "午安。"
        in 12..17 -> "下午好。"
        in 18..22 -> "晚上好。"
        else -> "夜深了。"
    }
}
