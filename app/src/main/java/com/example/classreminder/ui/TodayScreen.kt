package com.example.classreminder.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.NoteEntity
import com.example.classreminder.data.TodayNotePicker
import com.example.classreminder.data.TodaySchedule
import com.example.classreminder.data.deadlineCountdown
import com.example.classreminder.data.deadlineTimeLabel
import com.example.classreminder.data.hasDeadline
import com.example.classreminder.data.typeLabel
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
 *  3. **便签摘要**（最多 5 条，Deadline 类优先）— 便签不再占首屏，但需要时伸手可及。
 *     超过 5 条时末尾换成一条灰色的「…」占位栏，表示下面还有（见 [NoteSummaryMoreRow]）。
 *
 * 时间每 30 秒刷新一次，所以「还剩 N 分钟」会自己走字，不用退回重进。
 */
@Composable
fun TodayScreen(
    classes: List<ClassEntity>,
    notes: List<NoteEntity>,
    currentWeek: Int?,
    advanceMinutes: Int,
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

    // ── 「当前空闲」的判定 ──
    //
    // 用户的口径是「当前时间还未到达设置中的预警时间限度」。预警限度就是设置页那个
    // 「提前 N 分钟提醒」—— 它的语义本来就是「离上课还有 N 分钟了，该提醒我了」。
    // 所以「还没进预警窗」= 现在离下一节课开始还超过 N 分钟 = 用户此刻确实还闲着。
    //
    // 判定落在 [TodaySchedule.isIdle] 里（纯函数，可单测）：
    // 三条必要条件 —— 没有课正在进行、今天还有课没上完、离下一节开始严格超过预警窗。
    // 放在数据层而不是写在这里，是因为这段逻辑最容易踩边界（正好等于预警窗口算不算），
    // 埋在 Composable 里就只能靠实机肉眼看，抽出来才有测试钉住。
    val next = upcoming.firstOrNull()
    val idle = remember(upcoming, now, advanceMinutes) {
        TodaySchedule.isIdle(upcoming, now, advanceMinutes)
    }

    // 今天页便签摘要要展示哪几条 / 末尾要不要补「…」占位栏。
    // 与 now 无关（只看 note 自身的分类和顺序），所以依赖只有 notes。
    val pickedNotes = remember(notes) { TodayNotePicker.pick(notes) }
    val showNotesMore = remember(notes) { TodayNotePicker.needsMoreRow(notes) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ── 1. 主卡：当前 / 下一节课；空闲时是「当前空闲」卡 ──
        item(key = "featured") {
            when {
                idle -> IdleCard(next = next!!, now = now)
                featured != null -> FeaturedClassCard(
                    entry = featured,
                    ongoing = current != null,
                    now = now,
                    onClick = { onOpenClass(featured.entity) }
                )
                else -> EmptyTodayCard(
                    hasClassToday = today.isNotEmpty(),
                    hasNotes = notes.isNotEmpty()
                )
            }
        }

        // ── 2. 今天剩余 ──
        if (rest.isNotEmpty()) {
            item(key = "rest-header") {
                SectionLabel(if (current != null) "接下来" else "今天的课")
            }
            items(rest.size, key = { rest[it].entity.id }) { index ->
                val entry = rest[index]
                UpcomingRow(entry = entry, onClick = { onOpenClass(entry.entity) })
            }
        }

        // ── 3. 便签摘要 ──
        // 最多 5 条（含末尾的省略栏占位），Deadline 类优先。
        // 挑选与截断规则在 TodayNotePicker 里（纯函数，可单测），
        // 结果在 LazyColumn 之前就算好 —— LazyListScope 的 content lambda 不是 @Composable，
        // 里面放不了 remember。
        if (notes.isNotEmpty()) {
            item(key = "notes-header") {
                SectionLabel("便签 · ${notes.size} 条", onClick = onOpenNotes)
            }
            items(pickedNotes.size, key = { "note-" + pickedNotes[it].id }) { index ->
                NoteSummaryRow(pickedNotes[index], now = now, onClick = onOpenNotes)
            }
            if (showNotesMore) {
                item(key = "notes-more") { NoteSummaryMoreRow(onClick = onOpenNotes) }
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
 *
 * **有课（非上课中）时保持原有的 `surfaceVariant` 底色**，不做任何主题色染色。
 * 这是用户明确要求的「有课程时保持原有颜色，以确保整体界面视觉和谐统一」：
 * 一屏里如果主卡被染蓝、下面的行又是素色，两块看着不像一套东西。
 * 蓝色的「此刻」信号只留给真正正在上课的那一张，独占才醒目。
 */
@Composable
private fun FeaturedClassCard(
    entry: TodaySchedule.TodayClass,
    ongoing: Boolean,
    now: Long,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val container = if (ongoing) scheme.primary else scheme.surfaceVariant
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

/**
 * 「当前空闲」卡 —— 距离下一节课还远（没进预警窗）时顶替主卡。
 *
 * 为什么需要它：原来的主卡在空闲时段会显示「30 分钟后开始」这种文案，
 * 但那其实是**下一节课的信息**，被摆在了「此刻状态」的位置上。
 * 用户打开 App 时想先确认「我现在是不是没事」，所以这里把「此刻状态」和
 * 「下一件事」拆成上下两行：主文本回答「现在」，小字回答「接下来」。
 *
 * 底色沿用 `surfaceVariant`（和「有课但没上课」的主卡同一档），
 * 不额外上色 —— 空闲是常态，不该比正在上课更抢眼。
 */
@Composable
private fun IdleCard(next: TodaySchedule.TodayClass, now: Long) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(scheme.tertiary)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "当前空闲",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.tertiary
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = next.entity.title,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "距离下一件事 ${remainingText(next.startMillis - now)}",
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 「还剩多久」的人话表达，和主卡原有那套档位一致：
 * 不到 1 分钟 → 马上开始；不到 1 小时 → N 分钟；再远 → N 小时 M 分钟。
 *
 * 负数（已经过去了）夹到 0，不会出现「-3 分钟后」这种文案。
 */
private fun remainingText(deltaMillis: Long): String {
    val minutes = (deltaMillis / 60_000L).coerceAtLeast(0L)
    return when {
        minutes <= 1L -> "马上开始"
        minutes < 60L -> "$minutes 分钟后"
        else -> "${minutes / 60} 小时 ${minutes % 60} 分钟后"
    }
}

/**
 * 今天完全没课时 / 今天的课上完了。
 *
 * 副文案有两种口径，按**有没有便签**分流：
 *  - 有便签 → 「好好休息，或者看看便签。」（引导去便签页）
 *  - 没便签 → 「放松一下吧！」（既没课也便签可看，就别再指路了）
 * 「今天的课上完了」后面那个 🎉 是祝贺语气 —— 一天的课熬完了，值得一下。
 *
 * [hasClassToday] 为 false 时（今天压根没课）不走这套，用「享受闲暇的一天」——
 * 「上完了」和「本来就没有」是两种不同的状态，措辞要分开。
 *
 * 文案本身抽成 [emptyTodayTitle] / [emptyTodaySubtitle] 两个纯函数，
 * 这样「什么情况说什么话」的规则能被单测钉住，而不用靠实机碰运气凑场景。
 */
internal fun emptyTodayTitle(hasClassToday: Boolean): String =
    if (hasClassToday) "今天的课上完了 🎉" else "今天没有课"

internal fun emptyTodaySubtitle(hasClassToday: Boolean, hasNotes: Boolean): String = when {
    !hasClassToday -> "享受闲暇的一天"
    hasNotes -> "好好休息，或者看看便签。"
    else -> "放松一下吧！"
}

@Composable
private fun EmptyTodayCard(hasClassToday: Boolean, hasNotes: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = emptyTodayTitle(hasClassToday),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = emptyTodaySubtitle(hasClassToday, hasNotes),
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
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val isTemp = entry.entity.date.isNotEmpty()
    val isDarkRow = scheme.surface.luminance() < 0.5f

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
            // 时间列：固定宽度，让所有行的时间左对齐成一条线。
            // 深色下用**浅色 Google Blue**：主色 #1A73E8 压在深色卡片上只有 3.8:1、发闷；
            // 纯白又太素（和右侧课程名同色，时间就不像「时间」了）。提亮版两者兼顾
            Text(
                text = entry.entity.startTime,
                fontSize = 14.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                color = if (isDarkRow) GoogleBlueLight else scheme.primary,
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
private fun NoteSummaryRow(note: NoteEntity, now: Long, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    // 竖线和徽章都用便签自己选的色号，和便签页里那一条对得上
    val noteColor = notePaletteColor(note.colorIndex)
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
                    .background(noteColor)
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = note.text,
                fontSize = 13.sp,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 占满剩余宽度，把徽章和倒计时挤到右边去
                modifier = Modifier.weight(1f)
            )
            // 右侧：分类徽章 + Deadline 倒计时（和便签页同一套信息，位置也一致）
            val label = note.typeLabel()
            if (label.isNotEmpty() || note.hasDeadline) {
                Spacer(Modifier.width(10.dp))
                Column(horizontalAlignment = Alignment.End) {
                    if (label.isNotEmpty()) {
                        Text(
                            text = label,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = noteColor,
                            maxLines = 1
                        )
                    }
                    if (note.hasDeadline) {
                        if (label.isNotEmpty()) Spacer(Modifier.height(2.dp))
                        val overdue = note.deadlineAt <= now
                        Text(
                            text = "${deadlineTimeLabel(note.deadlineAt, now)} · " +
                                deadlineCountdown(note.deadlineAt, now),
                            fontSize = 10.sp,
                            color = if (overdue) scheme.error else noteColor.copy(alpha = 0.85f),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * 便签摘要末尾的「还有更多」占位栏。
 *
 * 格式与 [NoteSummaryRow] 完全一致（同高、同内边距、同圆角、同竖线位置），
 * 只是整体压成灰色：
 *  - 竖线换成 `outlineVariant`（不是某条便签的颜色）
 *  - 文本换成「…」并用 `onSurfaceVariant`
 *  - 底色 / 描边都比正文淡一档
 *
 * **为什么不直接砍掉超出的部分**：砍掉之后「还有没有」只能靠数「便签 · N 条」
 * 里的 N 来判断，扫一眼是看不出来的。留一条占位栏，有没有更多就是一眼的事。
 *
 * 可点击 —— 点它和点「全部」是同一个动作（进便签页）。
 */
@Composable
private fun NoteSummaryMoreRow(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            // 内边距与 NoteSummaryRow 逐字一致，保证两条栏位高度对齐、竖线在同一列
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.outlineVariant)
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = "…",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** 今天日期 + 星期 + 周次的那行副标题（给顶部栏用） */
fun todaySubtitle(currentWeek: Int?, now: Long): String {    val cal = Calendar.getInstance().apply { timeInMillis = now }
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
