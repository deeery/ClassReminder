package com.example.classreminder.ui

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.MainViewModel
import com.example.classreminder.Prefs

private val dayOrder = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
private val dayLabel = mapOf(
    "Monday" to "周一", "Tuesday" to "周二", "Wednesday" to "周三",
    "Thursday" to "周四", "Friday" to "周五", "Saturday" to "周六", "Sunday" to "周日"
)

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<ClassEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0=列表, 1=课表, 2=设置

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Class Reminder", modifier = Modifier.padding(top = 6.dp)) },
                backgroundColor = MaterialTheme.colors.surface,
                contentColor = MaterialTheme.colors.onSurface
            )
        },
        bottomBar = {
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { tab -> selectedTab = tab }
            )
        },
        floatingActionButton = {
            if (selectedTab != 2) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    backgroundColor = MaterialTheme.colors.primary
                ) { Text("+") }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (selectedTab) {
                0 -> ClassListView(classes, onEdit = { editing = it }, onDelete = { viewModel.delete(it) })
                1 -> WeekView(classes)
                2 -> SettingsPage(
                    themeMode = themeMode,
                    onThemeModeChanged = onThemeModeChanged,
                    onRequestNotificationPermission = onRequestNotificationPermission,
                    onOpenSettings = onOpenSettings
                )
            }
        }
    }

    if (showAdd) {
        AddEditDialog(onSave = { new ->
            viewModel.save(new)
            showAdd = false
        }, onDismiss = { showAdd = false })
    }

    if (editing != null) {
        AddEditDialog(initial = editing, onSave = { updated ->
            viewModel.save(updated)
            editing = null
        }, onDismiss = { editing = null })
    }
}

// ── 底部导航栏 ──────────────────────────────────────────────────

data class BottomNavItem(val label: String, val icon: ImageVector)

@Composable
fun BottomNavigationBar(selectedTab: Int, onTabSelected: (Int) -> Unit) {
    val items = listOf(
        BottomNavItem("列表", Icons.AutoMirrored.Filled.List),
        BottomNavItem("课表", Icons.Default.DateRange),
        BottomNavItem("设置", Icons.Default.Settings)
    )
    BottomNavigation {
        items.forEachIndexed { index, item ->
            BottomNavigationItem(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}

// ── 列表视图 ────────────────────────────────────────────────────

@Composable
fun ClassListView(
    classes: List<ClassEntity>,
    onEdit: (ClassEntity) -> Unit,
    onDelete: (ClassEntity) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(classes) { item ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)) {
                Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = item.title, style = MaterialTheme.typography.h6)
                        Text(text = "${dayLabel[item.dayOfWeek] ?: item.dayOfWeek} ${item.startTime} - ${item.endTime}")
                        Text(text = "教室: ${item.room}")
                    }
                    Row {
                        TextButton(onClick = { onEdit(item) }) { Text("Edit") }
                        TextButton(onClick = { onDelete(item) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

// ── 周课表视图（可折叠） ──────────────────────────────────────────

@Composable
fun WeekView(classes: List<ClassEntity>) {
    val grouped = classes.groupBy { it.dayOfWeek }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp)) {
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
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (expanded) "折叠" else "展开",
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // ── 可折叠的课程列表 ──
                    AnimatedVisibility(visible = expanded) {
                        Column {
                            if (dayClasses.isEmpty()) {
                                Text(
                                    text = "  无课",
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f),
                                    fontSize = 14.sp,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp)
                                )
                            } else {
                                dayClasses.forEach { cls ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        elevation = 1.dp
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
                                                Text("教室: ${cls.room}", fontSize = 13.sp,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
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

// ── 设置页面（独立全屏） ──────────────────────────────────────────

@Composable
fun SettingsPage(
    themeMode: Int,
    onThemeModeChanged: (Int) -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val initialAdvance = remember { runCatching { Prefs.getAdvanceMinutes(ctx) }.getOrDefault(30) }
    var advance by remember { mutableStateOf(initialAdvance.toString()) }
    val initialAutoStart = remember { runCatching { Prefs.getAutoStart(ctx) }.getOrDefault(false) }
    var autoStart by remember { mutableStateOf(initialAutoStart) }
    val initialShowPopup = remember { runCatching { Prefs.getShowPopup(ctx) }.getOrDefault(true) }
    var showPopupPref by remember { mutableStateOf(initialShowPopup) }
    var saved by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.h5, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            OutlinedTextField(
                value = advance,
                onValueChange = { advance = it; saved = false },
                label = { Text("提前提醒分钟数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(16.dp))

            // ── 开机自启 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("开机自启", fontSize = 16.sp)
                Switch(checked = autoStart, onCheckedChange = { autoStart = it; saved = false },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colors.primary,
                        checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                    )
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── 显示弹窗 ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("显示弹窗（锁屏弹出）", fontSize = 16.sp)
                Switch(checked = showPopupPref, onCheckedChange = { showPopupPref = it; saved = false },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colors.primary,
                        checkedTrackColor = MaterialTheme.colors.primary.copy(alpha = 0.5f)
                    )
                )
            }
            Spacer(Modifier.height(12.dp))

            // ── 主题模式 ──
            Text("主题模式", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            ThemeMode.entries.forEachIndexed { index, mode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onThemeModeChanged(index) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = themeMode == index,
                        onClick = { onThemeModeChanged(index) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = MaterialTheme.colors.primary
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(mode.label, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.height(24.dp))

            // ── 操作按钮 ──
            Button(
                onClick = {
                    onRequestNotificationPermission()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("请求通知权限")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onOpenSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("打开系统设置")
            }
            Spacer(Modifier.height(24.dp))

            // ── 保存按钮 ──
            Button(
                onClick = {
                    val minutes = advance.toIntOrNull() ?: 30
                    Prefs.setAdvanceMinutes(ctx, minutes)
                    Prefs.setAutoStart(ctx, autoStart)
                    Prefs.setShowPopup(ctx, showPopupPref)
                    saved = true
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = advance.toIntOrNull() != null
            ) {
                Text(if (saved) "已保存 ✓" else "保存设置")
            }
            if (saved) {
                Text(
                    "设置已保存",
                    color = MaterialTheme.colors.primary,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

// ── 添加/编辑对话框 ──────────────────────────────────────────────

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun AddEditDialog(initial: ClassEntity? = null, onSave: (ClassEntity) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf(initial?.title ?: "") }
    val days = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    var day by remember { mutableStateOf(initial?.dayOfWeek ?: "Monday") }
    var dayExpanded by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    var startTime by remember { mutableStateOf(initial?.startTime ?: "09:00") }
    var endTime by remember { mutableStateOf(initial?.endTime ?: "10:00") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var room by remember { mutableStateOf(initial?.room ?: "") }
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
        title = { Text(if (initial == null) "Add Class" else "Edit Class") },
        text = {
            Column {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title") })
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = dayExpanded, onExpandedChange = { dayExpanded = !dayExpanded }) {
                    OutlinedTextField(
                        value = day,
                        onValueChange = {},
                        label = { Text("Day") },
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dayExpanded) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = dayExpanded, onDismissRequest = { dayExpanded = false }) {
                        days.forEach { d ->
                            DropdownMenuItem(onClick = { day = d; dayExpanded = false }) {
                                Text(d)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = startTime,
                    onValueChange = {},
                    label = { Text("Start Time (HH:mm)") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showStartPicker = true },
                    trailingIcon = {
                        TextButton(onClick = { showStartPicker = true }) { Text("⏰") }
                    }
                )
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
                OutlinedTextField(
                    value = endTime,
                    onValueChange = {},
                    label = { Text("End Time (HH:mm)") },
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth().clickable { showEndPicker = true },
                    trailingIcon = {
                        TextButton(onClick = { showEndPicker = true }) { Text("⏰") }
                    }
                )
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
                OutlinedTextField(value = room, onValueChange = { room = it }, label = { Text("Room") })
                Spacer(modifier = Modifier.height(8.dp))
                if (!startValid) Text("开始时间格式应为 HH:mm", color = MaterialTheme.colors.error)
                if (!endValid) Text("结束时间格式应为 HH:mm", color = MaterialTheme.colors.error)
                if (startValid && endValid && !startBeforeEnd) Text("结束时间必须晚于开始时间", color = MaterialTheme.colors.error)
            }
        },
        confirmButton = {
            Button(onClick = {
                val id = initial?.id ?: (System.currentTimeMillis() / 1000).toInt()
                onSave(ClassEntity(id = id, title = title, dayOfWeek = day, startTime = startTime, endTime = endTime, room = room, notes = ""))
            }, enabled = startValid && endValid && startBeforeEnd && title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
