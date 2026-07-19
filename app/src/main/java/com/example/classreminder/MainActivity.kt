// Add MainActivity with Jetpack Compose UI
package com.example.classreminder

import android.os.Bundle
import android.Manifest
import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material.AlertDialog
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.classreminder.data.MainViewModel
import com.example.classreminder.ui.MainScreen

class MainActivity : ComponentActivity() {
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Hide the platform ActionBar so Compose TopAppBar is the only app bar
        try {
            actionBar?.hide()
        } catch (_: Exception) {}

        // Permission launchers: assign to activity properties so lambdas passed to Compose remain stable
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                startReminderService()
            } else {
                Toast.makeText(this, "需要通知权限才能显示锁屏提醒", Toast.LENGTH_LONG).show()
            }
        }

        fun ensureNotificationPermissionAndStart() {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    startReminderService()
                } else {
                    try {
                        if (!isFinishing) requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            } else {
                startReminderService()
            }
        }

        // Note: don't immediately request permissions here. We show a first-run explanation dialog
        // inside the Compose UI and request permissions only after the user confirms.
        setContent {
            MaterialTheme {
                Surface {
                    // permission request lambdas
                    val requestNotification: () -> Unit = {
                        try {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                                    == android.content.pm.PackageManager.PERMISSION_GRANTED
                                ) {
                                    Toast.makeText(this@MainActivity, "已拥有通知权限", Toast.LENGTH_SHORT).show()
                                } else if (!isFinishing) {
                                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                Toast.makeText(this@MainActivity, "当前 Android 版本无需额外请求通知权限", Toast.LENGTH_SHORT).show()
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                        }
                    }
                    val openAppSettings: () -> Unit = {
                        try {
                            val intent = android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS.let { action ->
                                Intent(action).apply {
                                    data = android.net.Uri.parse("package:" + packageName)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            }
                            startActivity(intent)
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            Toast.makeText(this@MainActivity, "无法打开系统设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                    val showFirstRun = remember { mutableStateOf(Prefs.isFirstRun(this@MainActivity)) }

                    if (showFirstRun.value) {
                        AlertDialog(onDismissRequest = {
                            Prefs.setFirstRunDone(this@MainActivity)
                            showFirstRun.value = false
                        }, title = { Text("首次运行") }, text = { Text("应用需要通知权限才能在上课前显示锁屏提醒。请在下个页面点击「启用提醒」按钮以授权。") }, confirmButton = {
                            TextButton(onClick = {
                                Prefs.setFirstRunDone(this@MainActivity)
                                showFirstRun.value = false
                            }) { Text("知道了") }
                        }, dismissButton = {
                            TextButton(onClick = {
                                Prefs.setFirstRunDone(this@MainActivity)
                                showFirstRun.value = false
                            }) { Text("拒绝") }
                        })
                    }
                    MainScreen(
                        viewModel = viewModel(),
                        onStartMonitoringRequested = { ensureNotificationPermissionAndStart() },
                        onRequestNotificationPermission = requestNotification,
                        onOpenSettings = openAppSettings
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Auto-start service whenever the activity resumes, if notification
        // permission is already granted. This covers:
        //   - first grant via "启用提醒" button
        //   - subsequent app launches
        //   - returning from system settings after toggling permissions
        tryAutoStartService()
    }

    private fun tryAutoStartService() {
        val canStart = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (canStart) {
            startReminderService()
        }
    }

    private fun startReminderService() {
        try {
            val svc = Intent(this, ClassReminderService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(svc)
            } else {
                startService(svc)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }
}

