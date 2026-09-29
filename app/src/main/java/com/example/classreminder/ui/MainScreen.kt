package com.example.classreminder.ui

import android.app.TimePickerDialog
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classreminder.ClassReminderService
import com.example.classreminder.R
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.MainViewModel
import com.example.classreminder.data.TodaySchedule
import com.example.classreminder.data.WeekSchedule
import com.example.classreminder.Prefs

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
    val serviceRunning by ClassReminderService.running.collectAsState()
    var editing by remember { mutableStateOf<ClassEntity?>(null) }
    var deleting by remember { mutableStateOf<ClassEntity?>(null) }
    var addingKind by remember { mutableStateOf<AddKind?>(null) }
    var fabExpanded by remember { mutableStateOf(false) }
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    // 0=列表, 1=课表, 2=设置；启动时接着上次停留的非设置页
    var selectedTab by remember { mutableStateOf(Prefs.getLastTab(ctx)) }
    // 课表的显示模式：既要在切 Tab 时不丢，也要在下次打开时沿用
    var weekMode by remember { mutableStateOf(if (Prefs.isWeekGrid(ctx)) WeekMode.GRID else WeekMode.LIST) }

    // 周次校准：存的是「第 1 周的周一」，改了它课表和提醒都会跟着变
    var week1Monday by remember { mutableStateOf(Prefs.getWeek1Monday(ctx)) }
    var showCalibrate by remember { mutableStateOf(false) }
    val currentWeek = remember(week1Monday) {
        if (week1Monday == 0L) null else WeekSchedule.weekNumber(week1Monday, System.currentTimeMillis())
    }
    // 课表正在浏览第几周：也提到这里，切 Tab 回来还停在原来那周（重新校准会跟着回到本周）
    var shownWeek by remember(currentWeek) { mutableStateOf(currentWeek ?: 1) }

    // 搜索过滤
    val filteredClasses = remember(searchQuery, classes) {
        if (searchQuery.isBlank()) classes
        else classes.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.dayOfWeek.contains(searchQuery, ignoreCase = true) ||
            it.room.contains(searchQuery, ignoreCase = true) ||
            it.startTime.contains(searchQuery) ||
            it.endTime.contains(searchQuery)
        }
    }

    Scaffold(
        topBar = {
            if (selectedTab == 0) {
                TopAppBar(
                    title = {
                        AnimatedContent(targetState = isSearchOpen, label = "searchToggle") { open ->
                            if (open) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    placeholder = { Text("搜索课程、星期、教室...", fontSize = 13.sp) },
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
                                Text("课程提醒", modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    },
                    colors = TopAppBarDefaults.smallTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    actions = {
                        if (isSearchOpen) {
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
                    // 只记非设置页（守卫在 Prefs 里）
                    Prefs.setLastTab(ctx, tab)
                }
            )
        },
        floatingActionButton = {
            if (selectedTab != 2) {
                Column(horizontalAlignment = Alignment.End) {
                    AnimatedVisibility(visible = fabExpanded) {
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
                    FloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(
                            if (fabExpanded) Icons.Default.Close else Icons.Default.Add,
                            contentDescription = if (fabExpanded) "收起添加菜单" else "添加"
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ClassListView(
                    classes = filteredClasses,
                    searching = searchQuery.isNotBlank(),
                    currentWeek = currentWeek,
                    onEdit = { editing = it },
                    onDelete = { deleting = it },
                    onAdd = { fabExpanded = true },
                    onImport = onImportTimetable
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
                2 -> SettingsPage(
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenSettings = onOpenSettings,
                    onImportTimetable = onImportTimetable
                )
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

    if (editing != null) {
        AddEditDialog(
            initial = editing,
            // 已有日期的就是临时提醒
            oneOff = !editing?.date.isNullOrEmpty(),
            onSave = { updated ->
                viewModel.save(updated)
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
    Surface(
        shape = SHAPE_CARD,
        color = MaterialTheme.colorScheme.surface,
        border = cardBorder(),
        shadowElevation = 6.dp,
        modifier = Modifier.size(84.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
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
        BottomNavItem("列表", Icons.AutoMirrored.Filled.List),
        BottomNavItem("课表", Icons.Default.DateRange),
        BottomNavItem("设置", Icons.Default.Settings)
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
                    .background(
                        if (isRunning) Color(0xFF4CAF50) else Color(0xFFE53935),
                        CircleShape
                    )
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

// ── 列表视图 ────────────────────────────────────────────────────

@Composable
fun ClassListView(
    classes: List<ClassEntity>,
    searching: Boolean,
    currentWeek: Int?,
    onEdit: (ClassEntity) -> Unit,
    onDelete: (ClassEntity) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit
) {
    if (classes.isEmpty()) {
        EmptyState(searching = searching, onAdd = onAdd, onImport = onImport)
        return
    }

    val todayName = todayName()
    val todayDate = TodaySchedule.dateOf(System.currentTimeMillis())

    fun isOneOff(c: ClassEntity) = c.date.isNotEmpty()
    // 今天：长期课看「星期 + 本周要上」，临时提醒只认自己的日期
    fun isToday(c: ClassEntity) = if (isOneOff(c)) c.date == todayDate
    else c.dayOfWeek == todayName && isThisWeek(c.weeks, currentWeek)

    val todayClasses = classes.filter { isToday(it) }.sortedBy { it.startTime }
    val upcomingOneOff = classes.filter { isOneOff(it) && it.date > todayDate }
        .sortedWith(compareBy({ it.date }, { it.startTime }))
    val expiredOneOff = classes.filter { isOneOff(it) && it.date < todayDate }
        .sortedWith(compareBy({ it.date }, { it.startTime }))
    val longTerm = classes.filterNot { isOneOff(it) }
    val restOfWeek = longTerm
        .filter { it.dayOfWeek != todayName && isThisWeek(it.weeks, currentWeek) }
        .sortedWith(compareBy({ dayOrder.indexOf(it.dayOfWeek) }, { it.startTime }))
    val greyed = longTerm
        .filterNot { isThisWeek(it.weeks, currentWeek) }
        .sortedWith(compareBy({ dayOrder.indexOf(it.dayOfWeek) }, { it.startTime })) + expiredOneOff
    val greyHeader = when {
        expiredOneOff.isNotEmpty() && greyed.size > expiredOneOff.size ->
            if (currentWeek == null) "已过期" else "已过期 / 本周不上（第 $currentWeek 周）"
        expiredOneOff.isNotEmpty() -> "已过期"
        else -> "本周不上（第 $currentWeek 周）"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        if (todayClasses.isNotEmpty()) {
            item(key = "hdr-today") { ListSectionHeader("今天 ${dayLabel[todayName] ?: ""}") }
            items(todayClasses, key = { "t-${it.id}" }) { item ->
                ClassCard(
                    item = item,
                    highlightToday = true,
                    dimmed = false,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
        if (upcomingOneOff.isNotEmpty()) {
            item(key = "hdr-oneoff") { ListSectionHeader("一次性提醒") }
            items(upcomingOneOff, key = { "o1-${it.id}" }) { item ->
                ClassCard(
                    item = item,
                    highlightToday = false,
                    dimmed = false,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
        if (restOfWeek.isNotEmpty()) {
            if (todayClasses.isNotEmpty() || upcomingOneOff.isNotEmpty()) {
                item(key = "hdr-week") { ListSectionHeader("本周其它") }
            }
            items(restOfWeek, key = { "w-${it.id}" }) { item ->
                ClassCard(
                    item = item,
                    highlightToday = false,
                    dimmed = false,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
        if (greyed.isNotEmpty()) {
            item(key = "hdr-other") { ListSectionHeader(greyHeader) }
            items(greyed, key = { "g-${it.id}" }) { item ->
                ClassCard(
                    item = item,
                    highlightToday = false,
                    dimmed = true,
                    onEdit = { onEdit(item) },
                    onDelete = { onDelete(item) }
                )
            }
        }
    }
}

/** 没校准周次时不做「本周」判断，一律算本周 */
private fun isThisWeek(weeks: String, currentWeek: Int?): Boolean =
    currentWeek == null || WeekSchedule.contains(weeks, currentWeek)

@Composable
private fun ListSectionHeader(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
        Spacer(Modifier.width(8.dp))
        Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

/** 空列表 / 搜索无结果：都给一条明确出路，别只放一句话 */
@Composable
fun EmptyState(searching: Boolean, onAdd: () -> Unit, onImport: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            if (searching) {
                Text("没有匹配的课程", fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "换个关键词试试，或清空搜索看全部课程",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            } else {
                Icon(
                    Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
                Spacer(Modifier.height(12.dp))
                Text("还没有课程", fontWeight = FontWeight.Medium, fontSize = 17.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "手动添加，或直接导入教务系统导出的课表 PDF",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("添加课程") }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("从 PDF 导入课表")
                }
            }
        }
    }
}

/** 列表卡片：左侧色条标今天，右侧图标按钮编辑/删除；dimmed = 本周不上 */
@Composable
fun ClassCard(
    item: ClassEntity,
    highlightToday: Boolean,
    dimmed: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .alpha(if (dimmed) 0.45f else 1f),
        shape = SHAPE_CARD,
        // M3 的 Card 默认底色是 surfaceVariant（浅灰），这里显式用 surface 保持原来的白卡
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (highlightToday && !dimmed) 2.dp else 0.dp),
        border = if (highlightToday && !dimmed) null
        else BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.07f))
    ) {
        // IntrinsicSize：左侧色条跟着卡片实际高度走，不会因为文案变多而短一截
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        when {
                            dimmed -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                            highlightToday -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        }
                    )
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (highlightToday && !dimmed) {
                        Spacer(Modifier.width(6.dp))
                        TodayChip()
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = whenText(item),
                    fontSize = 13.sp,
                    color = if (highlightToday && !dimmed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    fontWeight = FontWeight.Medium
                )
                if (item.room.isNotBlank()) {
                    Text(
                        text = "教室 ${item.room}",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                val extra = listOf(item.teacher, if (item.date.isNotEmpty()) "临时提醒" else item.weeks)
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (extra.isNotBlank()) {
                    Text(
                        text = extra,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "编辑", modifier = Modifier.size(19.dp))
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                )
            }
        }
    }
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

/** 设备当前是星期几（Monday…Sunday，跟数据库里存的一致） */
private fun todayName(): String = TodaySchedule.dayNameOf(System.currentTimeMillis())

/** 卡片上「什么时候」那一行：临时提醒显示日期，长期课程显示星期 */
private fun whenText(item: ClassEntity): String = if (item.date.isNotEmpty()) {
    "${item.date.takeLast(5)} ${dayLabel[item.dayOfWeek].orEmpty()} ${item.startTime} - ${item.endTime}"
} else {
    "${dayLabel[item.dayOfWeek] ?: item.dayOfWeek} ${item.startTime} - ${item.endTime}"
}

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

/**
 * 表格模式：左侧时间段 + 右侧七天的网格。
 * 行不是写死的"第几节"，而是取本周实际出现过的「时间段」，手填的任意时间也能正确成行。
 * 列宽、字号、行高都按屏幕实际尺寸算，小屏不至于挤成一团，大屏也不会空一片。
 */
@Composable
fun WeekGrid(classes: List<ClassEntity>, highlightDay: String?, onEdit: (ClassEntity) -> Unit) {
    val slots = classes.map { it.startTime to it.endTime }.distinct().sortedBy { it.first }
    if (slots.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "这一周没有课",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                fontSize = 14.sp
            )
        }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // 空列折叠：只画本周有课的那几天，宽度让给有课的天。
        // 只有 1~2 天有课时不折叠，否则一两列撑满屏幕反而不像课表。
        val busyDays = dayOrder.filter { day -> classes.any { it.dayOfWeek == day } }
        val days = if (busyDays.size >= 3) busyDays else dayOrder

        // 时间列约占 12% 宽（夹在 38~58dp），剩下的天平分
        val labelWidth = (maxWidth * 0.12f).coerceIn(38.dp, 58.dp)
        val cellWidth = (maxWidth - labelWidth - 8.dp) / days.size
        val titleSize = when {
            cellWidth >= 62.dp -> 12.sp
            cellWidth >= 52.dp -> 11.sp
            else -> 10.sp
        }
        val roomSize = if (cellWidth >= 58.dp) 10.sp else 9.sp
        val titleLines = if (cellWidth >= 54.dp) 3 else 2
        // 行高：行少就把整屏铺满（上限 112dp），行多则不低于 68dp 并滚动
        val rowHeight = ((maxHeight - 36.dp) / slots.size).coerceIn(68.dp, 112.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp)
        ) {
            // 表头：星期
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                Box(modifier = Modifier.width(labelWidth))
                days.forEach { day ->
                    val isToday = day == highlightDay
                    Text(
                        text = dayLabel[day] ?: day,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            slots.forEach { (start, end) ->
                Row(modifier = Modifier.fillMaxWidth().height(rowHeight).padding(vertical = 2.dp)) {
                    Column(
                        modifier = Modifier.width(labelWidth).fillMaxHeight(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(start, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(end, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    days.forEach { day ->
                        val cell = classes.filter {
                            it.dayOfWeek == day && it.startTime == start && it.endTime == end
                        }
                        val isToday = day == highlightDay
                        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 1.dp)) {
                            if (cell.isEmpty()) {
                                // 空位只留淡边框；今天那一列再铺一层主色，表格结构更清楚
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                            else Color.Transparent,
                                            SHAPE_SMALL
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                            shape = SHAPE_SMALL
                                        )
                                )
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    cell.forEach { cls ->
                                        Box(modifier = Modifier.weight(1f)) {
                                            GridCell(cls, titleSize, roomSize, titleLines, onEdit)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun GridCell(
    cls: ClassEntity,
    titleSize: TextUnit,
    roomSize: TextUnit,
    titleLines: Int,
    onEdit: (ClassEntity) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize().clickable { onEdit(cls) },
        shape = SHAPE_SMALL,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        // 临时提醒换个底色，一眼能区分
        colors = CardDefaults.cardColors(
            containerColor = if (cls.date.isNotEmpty()) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = cls.title,
                fontSize = titleSize,
                lineHeight = titleSize * 1.2f,
                fontWeight = FontWeight.Medium,
                maxLines = titleLines,
                overflow = TextOverflow.Ellipsis
            )
            if (cls.room.isNotBlank()) {
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
    onCalibrate: () -> Unit
) {
    val dateFormat = remember { java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault()) }
    Column {
        // ── 工具行：表格/列表 + 校准 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WeekModeSwitch(mode, onModeChange)
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
    onEdit: (ClassEntity) -> Unit
) {
    // 显示模式和正在浏览的周次都由 MainScreen 持有：切 Tab 回来不会丢
    val shownWeekStart = if (week1Monday != 0L) WeekSchedule.weekStart(week1Monday, shownWeek) else null
    val visible = if (shownWeekStart == null) classes
    else classes.filter { cls ->
        // 临时提醒按具体日期落在哪一周来显示，长期课程按「周数」文本
        if (cls.date.isNotEmpty()) {
            TodaySchedule.occursInRange(cls, shownWeekStart, shownWeekStart + 7L * 24 * 60 * 60 * 1000)
        } else {
            WeekSchedule.contains(cls.weeks, shownWeek)
        }
    }
    val today = todayName()
    // 只有正在看本周时，今天的课才值得高亮
    val highlightDay = if (currentWeek != null && shownWeek == currentWeek) today else null

    Column(modifier = Modifier.fillMaxSize()) {
        // 表头固定：切模式时不动，只有下面的内容做过渡
        WeekHeader(
            currentWeek = currentWeek,
            shownWeek = shownWeek,
            week1Monday = week1Monday,
            mode = mode,
            onModeChange = onModeChange,
            onPrev = { if (shownWeek > 1) onShownWeekChange(shownWeek - 1) },
            onNext = { onShownWeekChange(shownWeek + 1) },
            onCalibrate = onCalibrate
        )
        // 表格 ↔ 列表：淡入淡出 + 轻微横向位移，方向跟着上面分段控件的左右位置走
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                val toGrid = targetState == WeekMode.GRID
                // 入场 220ms 淡入 + 280ms 轻微横移；出场更快淡出（110ms），减少两层同时可见的"糊"感
                (fadeIn(tween(220)) + slideInHorizontally(tween(280)) { if (toGrid) -it / 18 else it / 18 }) togetherWith
                    (fadeOut(tween(110)) + slideOutHorizontally(tween(240)) { if (toGrid) it / 18 else -it / 18 })
            },
            label = "weekMode"
        ) { current ->
            when (current) {
                WeekMode.GRID -> WeekGrid(classes = visible, highlightDay = highlightDay, onEdit = onEdit)
                WeekMode.LIST -> WeekDayList(classes = visible, highlightDay = highlightDay)
            }
        }
    }
}

/** 列表模式：按星期分组、可折叠 */
@Composable
fun WeekDayList(classes: List<ClassEntity>, highlightDay: String?) {
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
                            color = if (isToday) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
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
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "折叠${dayLabel[day]}" else "展开${dayLabel[day]}",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // ── 可折叠的课程列表 ──
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            dayClasses.forEach { cls ->
                                WeekClassCard(cls, isToday = isToday)
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
fun WeekClassCard(cls: ClassEntity, isToday: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = SHAPE_CARD,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isToday) 2.dp else 0.dp),
        border = if (isToday) null else cardBorder()
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
                Text(
                    cls.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (cls.room.isNotBlank()) {
                    Text(
                        "教室 ${cls.room}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                val extra = listOf(cls.teacher, if (cls.date.isNotEmpty()) "临时提醒" else cls.weeks)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onThemeModeChanged(index) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = themeMode == index,
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
    onDismiss: () -> Unit
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
