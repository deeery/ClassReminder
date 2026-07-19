package com.example.classreminder.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.ExposedDropdownMenuBox
import androidx.compose.material.ExposedDropdownMenuDefaults
import androidx.compose.material.DropdownMenuItem
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.MainViewModel
import com.example.classreminder.Prefs

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onStartMonitoringRequested: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val classes by viewModel.classes.collectAsState(initial = emptyList())
    var editing by remember { mutableStateOf<ClassEntity?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Class Reminder") }, actions = {
                TextButton(onClick = onStartMonitoringRequested) { Text("启用提醒") }
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colors.onPrimary
                    )
                }
            })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }) { Text("+") }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(classes) { item ->
                    Card(modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)) {
                        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, style = MaterialTheme.typography.h6)
                                Text(text = "${item.dayOfWeek} ${item.startTime} - ${item.endTime}")
                                Text(text = "教室: ${item.room}")
                            }
                            Row {
                                TextButton(onClick = { editing = item }) { Text("Edit") }
                                TextButton(onClick = {
                                    viewModel.delete(item)
                                }) { Text("Delete") }
                            }
                        }
                    }
                }
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

    if (showSettings) {
        SettingsDialog(
            onDismiss = { showSettings = false },
            onRequestNotificationPermission = onRequestNotificationPermission,
            onOpenSettings = onOpenSettings
        )
    }
}

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


@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val ctx = LocalContext.current
    // Read preferences safely during composition
    val initialAdvance = remember { runCatching { Prefs.getAdvanceMinutes(ctx) }.getOrDefault(30) }
    var advance by remember { mutableStateOf(initialAdvance.toString()) }
    val initialAutoStart = remember { runCatching { Prefs.getAutoStart(ctx) }.getOrDefault(false) }
    var autoStart by remember { mutableStateOf(initialAutoStart) }
    val initialShowPopup = remember { runCatching { Prefs.getShowPopup(ctx) }.getOrDefault(true) }
    var showPopupPref by remember { mutableStateOf(initialShowPopup) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("设置") },
        text = {
            Column {
                OutlinedTextField(value = advance, onValueChange = { advance = it }, label = { Text("提前提醒分钟数") }, singleLine = true)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开机自启")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = autoStart, onCheckedChange = { autoStart = it })
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("显示弹窗（锁屏弹出）")
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(checked = showPopupPref, onCheckedChange = { showPopupPref = it })
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row {
                    Button(onClick = {
                        onRequestNotificationPermission()
                    }) { Text("请求通知权限") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onOpenSettings()
                    }) { Text("打开系统设置") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val minutes = advance.toIntOrNull() ?: 30
                Prefs.setAdvanceMinutes(ctx, minutes)
                Prefs.setAutoStart(ctx, autoStart)
                Prefs.setShowPopup(ctx, showPopupPref)
                // If user enabled auto-start or popup, they can start the service
                // via the "启用提醒" button on the main screen (which handles
                // permission checks properly). Service also starts automatically
                // on next boot via BootReceiver.
                // Do NOT call startForegroundService here — it requires
                // POST_NOTIFICATIONS + FOREGROUND_SERVICE_DATA_SYNC permissions
                // and crashes the process if those are missing.
                // No checkNow here — the service polls every 30s automatically.
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
