package com.example.classreminder

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import com.example.classreminder.data.AppDatabase
import com.example.classreminder.data.ClassEntity
import kotlinx.coroutines.*

class ClassReminderService : Service() {
    companion object {
        const val ACTION_CHECK_NOW = "com.example.classreminder.action.CHECK_NOW"
    }
    private val channelId = "class_reminder_service"
    private val notifChannelId = "class_reminder_alerts"
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var future: ScheduledFuture<*>? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastShownId: Int? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundServiceWithNotification("ClassReminder", "运行中 — 监控课程提醒")

        // Check every 30 seconds for an active/upcoming class
        future = scheduler.scheduleWithFixedDelay({
            try {
                // run DB access in coroutine
                serviceScope.launch {
                    try {
                        checkAndNotify()
                    } catch (t: Throwable) {
                        t.printStackTrace()
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }, 5, 30, TimeUnit.SECONDS)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val svc = NotificationChannel(channelId, "ClassReminder Service", NotificationManager.IMPORTANCE_LOW)
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(svc)

            val alerts = NotificationChannel(notifChannelId, "Class Alerts", NotificationManager.IMPORTANCE_HIGH)
            alerts.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            alerts.setSound(null, null)
            nm.createNotificationChannel(alerts)
        }
    }

    private fun startForegroundServiceWithNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(com.example.classreminder.R.drawable.ic_notification_clock)
            .setOngoing(true)
            .build()

        startForeground(1, notification)
    }

    private fun updateForegroundNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(com.example.classreminder.R.drawable.ic_notification_clock)
            .setOngoing(true)
            .build()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(1, notification)
    }

    private suspend fun checkAndNotify() {
        val now = System.currentTimeMillis()
        val db = AppDatabase.getInstance(applicationContext)
        val all = db.classDao().getAll()

        val cal = java.util.Calendar.getInstance()
        val todayName = when (cal.get(java.util.Calendar.DAY_OF_WEEK)) {
            java.util.Calendar.SUNDAY -> "Sunday"
            java.util.Calendar.MONDAY -> "Monday"
            java.util.Calendar.TUESDAY -> "Tuesday"
            java.util.Calendar.WEDNESDAY -> "Wednesday"
            java.util.Calendar.THURSDAY -> "Thursday"
            java.util.Calendar.FRIDAY -> "Friday"
            else -> "Saturday"
        }

        val todayClasses = all.filter { it.dayOfWeek == todayName }

        data class Candidate(val entity: ClassEntity, val startMillis: Long, val endMillis: Long)

        val candidates = todayClasses.mapNotNull { entry ->
            val partsStart = entry.startTime.split(":")
            val partsEnd = entry.endTime.split(":")
            val sh = partsStart.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val sm = partsStart.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
            val eh = partsEnd.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val em = partsEnd.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null

            val sCal = java.util.Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(java.util.Calendar.HOUR_OF_DAY, sh)
                set(java.util.Calendar.MINUTE, sm)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            val eCal = java.util.Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(java.util.Calendar.HOUR_OF_DAY, eh)
                set(java.util.Calendar.MINUTE, em)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val startMillis = sCal.timeInMillis
            var endMillis = eCal.timeInMillis
            // if end before start, assume ends next day
            if (endMillis <= startMillis) endMillis += 24L * 60L * 60L * 1000L

            Candidate(entry, startMillis, endMillis)
        }

        val ongoing = candidates.filter { now in it.startMillis..it.endMillis }
        // read advance minutes from preferences (default 30)
        val advance = Prefs.getAdvanceMinutes(applicationContext)
        val upcomingWindow = advance * 60 * 1000L
        val upcoming = candidates.filter { now >= (it.startMillis - upcomingWindow) && now < it.startMillis }

        val chosen = when {
            ongoing.isNotEmpty() -> ongoing.maxByOrNull { it.startMillis }
            upcoming.isNotEmpty() -> upcoming.minByOrNull { it.startMillis }
            else -> null
        }

        // Update the foreground notification to show detection status
        if (chosen != null) {
            val prefix = if (now in chosen.startMillis..chosen.endMillis) "正在上课" else "即将上课"
            updateForegroundNotification(
                "$prefix：${chosen.entity.title}",
                "${chosen.entity.room}  ${formatTime(chosen.startMillis)} - ${formatTime(chosen.endMillis)}"
            )
        } else if (todayClasses.isNotEmpty()) {
            // No class needs an alert right now, but there are classes today — show the overview
            val next = todayClasses.minByOrNull { it.startTime }
            val label = next?.let { "${it.title} ${it.startTime} ${it.room}" } ?: ""
            updateForegroundNotification(
                "ClassReminder — 今日 ${todayClasses.size} 节课",
                "下一节：$label"
            )
        } else {
            updateForegroundNotification("ClassReminder", "运行中 — 今日无课")
        }

        // ── 有课程需提醒：升级前台通知（高优先级 + 可选全屏弹窗） ──
        if (chosen != null) {
            val id = chosen.entity.id
            if (lastShownId == id) return

            // Check notification permission on Android 13+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    return
                }
            }

            val showPopup = Prefs.getShowPopup(applicationContext)
            val prefix = if (now in chosen.startMillis..chosen.endMillis) "正在上课" else "即将上课"

            // Build the same foreground notification but with HIGH priority
            var builder = NotificationCompat.Builder(this, channelId)
                .setContentTitle("$prefix：${chosen.entity.title}")
                .setContentText("${chosen.entity.room}  ${formatTime(chosen.startMillis)} - ${formatTime(chosen.endMillis)}")
                .setSmallIcon(com.example.classreminder.R.drawable.ic_notification_clock)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)

            if (showPopup) {
                val intent = Intent(this, LockOverlayActivity::class.java).apply {
                    putExtra("name", chosen.entity.title)
                    putExtra("start", chosen.startMillis)
                    putExtra("end", chosen.endMillis)
                    putExtra("room", chosen.entity.room)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                val pending = PendingIntent.getActivity(this, 1001, intent,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    else PendingIntent.FLAG_UPDATE_CURRENT)
                builder = builder.setFullScreenIntent(pending, true)
            }

            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(1, builder.build())

            lastShownId = id
        } else {
            // no candidate; reset lastShownId so future ones can show
            lastShownId = null
        }
    }

    private fun formatTime(millis: Long): String {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = millis }
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = cal.get(java.util.Calendar.MINUTE)
        return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        future?.cancel(true)
        scheduler.shutdownNow()
        serviceScope.cancel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CHECK_NOW) {
            serviceScope.launch {
                try {
                    checkAndNotify()
                } catch (t: Throwable) {
                    t.printStackTrace()
                }
            }
        }
        return START_STICKY
    }
}


