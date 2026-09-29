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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val dayLabel = mapOf(
    "Monday" to "周一", "Tuesday" to "周二", "Wednesday" to "周三",
    "Thursday" to "周四", "Friday" to "周五", "Saturday" to "周六", "Sunday" to "周日"
)

// ── 统一的圆角/描边，避免每个组件各写各的 ─────────────────────────
private val SHAPE_CARD = RoundedCornerShape(14.dp)
private val SHAPE_SMALL = RoundedCornerShape(8.dp)
private val SHAPE_CHIP = RoundedCornerShape(50)

/** 普通卡片用一条极淡的描边代替阴影，深色浅色都干净 */
@Composable
private fun cardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))

// ── 统一动效 ────────────────────────────────────────────────────
// 全应用的过渡时长都收在这里，风格才一致：入场稍慢、出场更快（避免两层长时间同时可见）。
// tween 的默认缓动就是 FastOutSlowInEasing，所以这里不再逐个指定。

/** 入场时长：淡入 / 展开 / 位移 */
private const val ENTER_MS = 240

/** 出场时长：比入场快一档 */
private const val EXIT_MS = 160

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
 */
@Composable
private fun rememberSelectionProgress(selected: Boolean): Float {
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(ENTER_MS),
        label = "selection"
    )
    return progress
}

/** 按下反馈的规格。用短 tween 而不是弹簧：弹簧会来回震荡好几帧，帧数不好预测 */
private fun pressSpec() = tween<Float>(120)

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
 * 统一的「点按反馈」：保留系统水波纹（indication 取当前的 LocalIndication），
 * 同时在按下时把整块略微缩小、松手弹回 —— 让每一次点击都有实感。
 *
 * 用法与 `Modifier.clickable { }` 完全一致，直接替换即可。
 */
@Composable
private fun Modifier.pressable(
    enabled: Boolean = true,
    pressedScale: Float = PRESS_SCALE,
    onClick: () -> Unit
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val scale = rememberPressScale(interaction, pressedScale)
    // 没按下时不挂 graphicsLayer：课表格子这种几十个实例的场景，
    // 每个都常驻一层 RenderNode 是白花的，缩放到 1 的时候直接不挂
    val scaleLayer = if (scale == 1f) {
        Modifier
    } else {
        Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
    return this
        .then(scaleLayer)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

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
    // 0=便签, 1=课表, 2=设置；启动时接着上次停留的非设置页
    var selectedTab by remember { mutableStateOf(Prefs.getLastTab(ctx)) }
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
            // 顶栏只在便签页存在。这里**故意不给它做高度动画** ——
            // Scaffold 的 contentPadding.top 直接取顶栏的测量高度（见 Scaffold.kt：
            // `if (topBarPlaceables.isEmpty()) insets.calculateTopPadding() else topBarHeight`），
            // 高度一动，整屏每帧都要换一套约束：课表网格每帧重组、每个课程块每帧重新排版
            // （文字布局缓存的 key 里带 constraints，高度变了就不复用），切页帧率就是这么掉的。
            // 改成随内容一起切换，代价只是没有折叠动画。
            if (selectedTab == 0) {
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
            if (selectedTab != 2) {
                // 加号是点得最多的按钮，按下反馈单独给它一份 interactionSource
                val fabInteraction = remember { MutableInteractionSource() }
                val fabScale = rememberPressScale(fabInteraction, pressedScale = 0.94f)
                Column(horizontalAlignment = Alignment.End) {
                    // ── 加号正上方那个按钮：便签页是「回撤」，课表页是「查看 / 编辑」切换，占同一个槽位 ──
                    if (selectedTab == 0) {
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
                            // 便签页 = 添加项目；课表页 = 展开 / 收起添加菜单
                            if (selectedTab == 0) addingNote = true else fabExpanded = !fabExpanded
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
                                    selectedTab == 0 -> "添加项目"
                                    else -> "添加"
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
            // 三个页面之间**瞬时切换**，不做过渡动画：
            // 切页本身就要组合出新的一屏（课表那屏很重），再叠加过渡只会让这一帧更挤。
            when (selectedTab) {
                0 -> NoteListView(
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
                targetState = if (selectedTab == 0) selectedNote else null,
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
    Surface(
        shape = SHAPE_CARD,
        color = MaterialTheme.colorScheme.surface,
        border = cardBorder(),
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
        BottomNavItem("便签", Icons.Default.Edit),
        BottomNavItem("课表", Icons.Default.DateRange),
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
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        // ── 导航项 ──
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            items.forEachIndexed { index, item ->
                NavigationBarItem(
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = { Text(item.label) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.primary,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
    val primary = MaterialTheme.colorScheme.primary
    val scope = rememberCoroutineScope()
    // 选中态：一条进度派生全部属性（底色 / 色条 / 文字 / 描边 / 放大），拖动和按下另算
    val selection = rememberSelectionProgress(selected)
    val barColor = lerpColor(primary.copy(alpha = 0.55f), primary, selection)
    // 选中态用主色容器打底，配合加粗描边和实色色条，三层一起表达高亮
    val containerColor = lerpColor(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer, selection)
    val textColor = lerpColor(MaterialTheme.colorScheme.onSurface, MaterialTheme.colorScheme.onPrimaryContainer, selection)
    // 描边不整体开关，而是让颜色和粗细一起过渡，选中 / 拖动切换时不会有「跳一下」的感觉
    val borderColor = if (dragging) Color.Transparent
    else lerpColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f), primary, selection)
    val borderWidth = if (dragging) 0.dp else lerpDp(1.dp, 1.5.dp, selection)
    // 选中时原地放大：graphicsLayer 不参与布局，所以是「自己长大」而不是把上下两条挤开
    val selectedScale = 1f + (SELECTED_SCALE - 1f) * selection
    val elevation by animateDpAsState(
        // 拖起来时抬一层阴影（落在卡片下方）；选中时也抬一档，配合放大明确「就是这一条」
        targetValue = when {
            dragging -> 12.dp
            selected -> 4.dp
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
    val pressInteraction = remember { MutableInteractionSource() }
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
                    // 左侧色条用 drawBehind 画：不依赖 IntrinsicSize 也能跟着实际高度走
                    .drawBehind {
                        drawRect(
                            color = barColor,
                            size = Size(4.dp.toPx(), size.height)
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
        Text("今日", fontSize = 9.sp, color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
    }
}

/** 临时提醒的黄色角标，跟在课程名称右边。表格视图用底色区分，这里是列表视图的标识 */@Composable
private fun TemporaryBadge() {
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

// ── 周课表：表格 / 列表两种模式 ──────────────────────────────────

enum class WeekMode { GRID, LIST }

@Composable
private fun WeekModeSwitch(mode: WeekMode, onChange: (WeekMode) -> Unit) {
    val density = LocalDensity.current
    // 量出每段的实际尺寸，滑块按它平移（不写死宽度，换文案也不会错位）
    var segmentSize by remember { mutableStateOf(IntSize.Zero) }
    val pillOffset by animateIntOffsetAsState(
        targetValue = IntOffset(if (mode == WeekMode.GRID) 0 else segmentSize.width, 0),
        animationSpec = tween(200),
        label = "modePill"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .padding(2.dp)
    ) {
        // 滑块：在文字下层，从左段平移到右段
        if (segmentSize.width > 0) {
            Box(
                modifier = Modifier
                    .offset { pillOffset }
                    .width(with(density) { segmentSize.width.toDp() })
                    .height(with(density) { segmentSize.height.toDp() })
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Row {
            WeekMode.entries.forEach { item ->
                val selected = item == mode
                // 文字颜色仍做过渡：滑块滑到时文字"点亮"
                val contentColor by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    animationSpec = tween(200),
                    label = "modeFg"
                )
                Text(
                    text = if (item == WeekMode.GRID) "表格" else "列表",
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = contentColor,
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            val size = coords.size
                            if (size != segmentSize) segmentSize = size
                        }
                        .clickable { onChange(item) }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
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

/** 块矮于这个高度就只留课程名，教室让位 */
private val BLOCK_ROOM_MIN_HEIGHT = 46.dp

/** 块再矮于这个高度就只留课程名，教师 / 教室 / 周次那一坨详情让位 */
private val BLOCK_DETAILS_MIN_HEIGHT = 76.dp

/** 表头（星期那一行）占的高度，算可用高度时要扣掉。今天那列多一行「今日」角标，留宽裕些 */
private val GRID_HEADER_HEIGHT = 46.dp

/** 单列最大宽度，免得大屏上几列被拉得太开 */
private val MAX_COLUMN_WIDTH = 76.dp

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

        // 时间列约占 12% 宽（夹在 38~58dp）
        val labelWidth = (maxWidth * 0.12f).coerceIn(38.dp, 58.dp)

        // 每列平分剩余宽度（不超过上限，免得大屏上被拉得太开）；
        // 今天那一列再稍微加宽一点，保证内容不被截断，多出来的宽度从其它列均摊。
        val gridWidth = (maxWidth - labelWidth - 8.dp).coerceAtLeast(0.dp)
        val normalWidth = (gridWidth / days.size).coerceAtMost(MAX_COLUMN_WIDTH)
        val todayIndex = days.indexOf(activeDay)
        val hasToday = todayIndex >= 0 && days.size > 1
        val todayWidth = if (hasToday) {
            (normalWidth * TODAY_COLUMN_SCALE).coerceAtMost(MAX_COLUMN_WIDTH)
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
        val titleSize = when {
            cellWidth >= 62.dp -> 12.sp
            cellWidth >= 52.dp -> 11.sp
            else -> 10.sp
        }
        val roomSize = if (cellWidth >= 58.dp) 10.sp else 9.sp
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
        // 一列都没高亮时退回等距的固定间隔。
        val marksNow = remember(classes, activeDay, rowCount, hoursPerRow, span) {
            if (activeDay == null) {
                (0 until rowCount).map { row -> span.startMinute + row * hoursPerRow * 60 }
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = FAB_RESERVE)
        ) {
            // 表头：星期。点一下把那一列高亮起来（左侧刻度跟着换成它的时刻、列也加宽）；
            // 点已经高亮的那一列则全部取消，左侧退回固定间隔。
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Box(modifier = Modifier.width(labelWidth))
                days.forEachIndexed { index, day ->
                    val isActive = day == activeDay
                    // 高亮切换时星期文字的颜色渐变过去，不是直接跳色
                    val headerColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        animationSpec = tween(ENTER_MS),
                        label = "dayHeaderColor"
                    )
                    Box(
                        modifier = Modifier
                            .width(columnWidths[index])
                            .clickable {
                                if (isActive) {
                                    pickedDay = null
                                    cleared = true
                                } else {
                                    pickedDay = day
                                    cleared = false
                                }
                            }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = dayLabel[day] ?: day,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
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
                // ── 底层：时间轴（横线 + 左侧刻度）──
                // 横线从刻度栏右侧铺到最右；每一条都对齐一个真实时刻，和标签一一对应。
                // 新旧两套刻度各带一个 alpha，交叉淡入淡出。
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawBehind {
                            fun drawMarks(marks: List<Int>, alpha: Float) {
                                if (alpha <= 0.01f) return
                                marks.forEach { minute ->
                                    val y = mapping.fractionOf(minute) * gridHeightPx
                                    drawLine(
                                        color = lineBase.copy(alpha = 0.08f * alpha),
                                        start = Offset(labelWidthPx, y),
                                        end = Offset(size.width, y),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            drawMarks(axisMarksPrev, 1f - axisCross)
                            drawMarks(axisMarks, axisCross)
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
                            titleSize = titleSize,
                            roomSize = roomSize,
                            titleLines = titleLines,
                            onEdit = onEdit
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * 左侧刻度的一层：同一套时刻，整体带一个透明度。
 * 换高亮列时新旧两套各铺一层、交叉淡入淡出，所以刻度是「渐变」而不是跳变。
 */
@Composable
private fun AxisMarkLabels(
    marks: List<Int>,
    alpha: Float,
    mapping: TimeAxis.Mapping,
    gridHeight: Dp
) {
    if (alpha <= 0.01f) return
    Box(modifier = Modifier.fillMaxSize()) {
        marks.forEach { minute ->
            Text(
                text = TimeAxis.labelOf(minute),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp)
                    .offset(
                        y = (gridHeight * mapping.fractionOf(minute) - AXIS_LABEL_LIFT)
                            .coerceAtLeast(0.dp)
                    )
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
    titleSize: TextUnit,
    roomSize: TextUnit,
    titleLines: Int,
    onEdit: (ClassEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    // mapping 每次重组都是新对象，用 remember 反而每帧都要重算，直接算更省事（课程量很小）
    val placed = TimeAxis.layout(classes, mapping)
    val gridHeightPx = with(LocalDensity.current) { gridHeight.toPx() }
    // 内容宽度 = 列宽 - 左右各 1dp 的padding，和原来 BoxWithConstraints 量出来的 maxWidth 一致
    val contentWidth = (columnWidth - 2.dp).coerceAtLeast(0.dp)
    // 高亮底色淡入淡出：点列头切换高亮时不是「啪」地铺上一层
    val tint by animateColorAsState(
        targetValue = if (highlighted) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        else Color.Transparent,
        animationSpec = tween(ENTER_MS),
        label = "dayHighlight"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(horizontal = 1.dp)
            .drawBehind {
                // 高亮的那一列铺一层主色。只铺到时间轴的真实高度，别盖住为展开预留的空白
                drawRect(color = tint, size = Size(size.width, gridHeightPx))
            }
    ) {
        placed.forEach { spot ->
            val blockHeight = (gridHeight * spot.heightFraction).coerceAtLeast(MIN_BLOCK_HEIGHT)
            Box(
                modifier = Modifier
                    .offset(
                        x = contentWidth * spot.leftFraction,
                        y = gridHeight * spot.topFraction
                    )
                    .fillMaxWidth(spot.widthFraction)
                    .height(blockHeight)
            ) {
                GridCell(
                    cls = spot.cls,
                    // 只把「够不够高」这两个判断结果传进去，而不是把每帧都在变的 blockHeight 传进去：
                    // 传原始高度会让 GridCell 每帧都跳不过重组，而它的内容其实只在跨过阈值时才变
                    showRoom = blockHeight >= BLOCK_ROOM_MIN_HEIGHT,
                    showDetails = blockHeight >= BLOCK_DETAILS_MIN_HEIGHT,
                    titleSize = titleSize,
                    roomSize = roomSize,
                    titleLines = titleLines,
                    onClick = onEdit
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
 */
@Composable
private fun GridCell(
    cls: ClassEntity,
    showRoom: Boolean,
    showDetails: Boolean,
    titleSize: TextUnit,
    roomSize: TextUnit,
    titleLines: Int,
    onClick: (ClassEntity) -> Unit
) {
    val baseContainer = if (cls.date.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer
    else MaterialTheme.colorScheme.surface
    // 块矮的时候逐级让位：先保课程名，再保教室，最后才轮到详情行（阈值判断在外层做，见 DayColumn）

    Card(
        modifier = Modifier
            .fillMaxSize()
            .pressable(pressedScale = 0.95f) { onClick(cls) },
        shape = SHAPE_SMALL,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = baseContainer),
        border = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 3.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = cls.title,
                fontSize = titleSize,
                lineHeight = titleSize * 1.2f,
                fontWeight = FontWeight.Medium,
                maxLines = if (showRoom) titleLines else 1,
                overflow = TextOverflow.Ellipsis
            )
            // 详情在块足够高时显示
            AnimatedVisibility(
                visible = showDetails,
                enter = fadeIn(tween(ENTER_MS)),
                exit = fadeOut(tween(EXIT_MS))
            ) {
                Column {
                    // 详情：教师、教室、周次/日期，最后一行是精确起止时间
                    val details = listOfNotNull(
                        cls.teacher.takeIf { it.isNotBlank() },
                        cls.room.takeIf { it.isNotBlank() },
                        (if (cls.date.isNotEmpty()) cls.date else cls.weeks).takeIf { it.isNotBlank() }
                    )
                    details.forEach { line ->
                        Text(
                            text = line,
                            fontSize = roomSize,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                    Text(
                        text = "${cls.startTime} - ${cls.endTime}",
                        fontSize = roomSize,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
            if (!selected && showRoom && cls.room.isNotBlank()) {
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

/** 列表模式：按星期分组、可折叠 */
@Composable
fun WeekDayList(
    classes: List<ClassEntity>,
    highlightDay: String?,
    onEdit: (ClassEntity) -> Unit
) {
    val grouped = classes.groupBy { it.dayOfWeek }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                                    onClick = { onEdit(cls) }
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

/** 课表里的课程卡片：左侧时间列 + 右侧信息 */
@Composable
fun WeekClassCard(
    cls: ClassEntity,
    isToday: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .pressable(onClick = onClick),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday) 2.dp else 0.dp),
        border = if (isToday) BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)) else cardBorder()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(38.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
            )
            Column(modifier = Modifier.padding(start = 12.dp, end = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        cls.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        // fill=false：标题短的时候角标紧跟其后，不会被推到行尾
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (cls.date.isNotEmpty()) {
                        Spacer(Modifier.width(6.dp))
                        TemporaryBadge()
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = cardBorder()
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
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 15.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
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
