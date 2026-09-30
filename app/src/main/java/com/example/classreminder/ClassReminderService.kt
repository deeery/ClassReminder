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
import com.example.classreminder.data.TodaySchedule
import com.example.classreminder.data.WeekSchedule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.*

class ClassReminderService : Service() {
    companion object {
        const val ACTION_CHECK_NOW = "com.example.classreminder.action.CHECK_NOW"
        /** 前台常驻通知固定用 id 1（startForeground），提醒通知必须用别的 id，否则会互相覆盖 */
        private const val FOREGROUND_NOTIFICATION_ID = 1
        private const val ALERT_NOTIFICATION_ID = 2

        // 用 StateFlow 暴露运行状态，UI 才能响应式刷新（普通 var 改了不会触发重组）
        private val _running = MutableStateFlow(false)
        val running: StateFlow<Boolean> = _running.asStateFlow()
        var isRunning: Boolean
            get() = _running.value
            set(value) {
                _running.value = value
            }
    }
    private val channelId = "class_reminder_service"
    /**
     * 提醒渠道。v2：原来那个渠道是静音的，在 ColorOS 这类「锁屏只显示图标/拦掉全屏弹窗」的系统上
     * 等于完全没有提醒效果，所以换到带震动的新渠道（渠道属性创建后不可改，只能换 ID）。
     * 声音保持关闭——上课时间响铃不合适，震动足够叫到人；想加声音可在系统通知设置里改。
     */
    private val notifChannelId = "class_reminder_alerts_v2"
    private val legacyAlertChannelId = "class_reminder_alerts"
    /** 跟启动器标签保持一致（debug 变体是 StuMate-test），两个应用同时装时才分得清 */
    private val appLabel: String by lazy { applicationInfo.loadLabel(packageManager).toString() }
    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var future: ScheduledFuture<*>? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastShownId: Int? = null

    /** 缓存数据库实例：checkAndNotify 每 30 秒调用一次，每次都走 synchronized 单例检查是浪费 */
    private val db by lazy { AppDatabase.getInstance(applicationContext) }
    private val classDao by lazy { db.classDao() }

    /** 缓存 Calendar 实例，避免 formatTime 每次调用都新建 */
    private val timeCal = java.util.Calendar.getInstance()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        createChannels()
        startForegroundServiceWithNotification(appLabel, "运行中 — 监控课程提醒")

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

    /**
     * 亮屏提醒。用 PARTIAL_WAKE_LOCK + ACQUIRE_CAUSES_WAKEUP 把屏幕点亮一小会儿，
     * 10 秒后自动释放（ON_AFTER_RELEASE 让屏幕再亮一会儿）。
     * 这是从 Service 里唯一能唤醒屏幕的办法；起不起作用最终由 ROM 决定。
     */
    private fun wakeScreen() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            @Suppress("DEPRECATION")
            val lock = pm.newWakeLock(
                android.os.PowerManager.FULL_WAKE_LOCK
                    or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                    or android.os.PowerManager.ON_AFTER_RELEASE,
                "ClassReminder:alert"
            )
            lock.acquire(10_000L)
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)

            val svc = NotificationChannel(channelId, "StuMate 服务", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(svc)

            val alerts = NotificationChannel(notifChannelId, "上课提醒", NotificationManager.IMPORTANCE_HIGH)
            alerts.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            alerts.setSound(null, null)          // 不响铃
            alerts.enableVibration(true)         // 但必须震动，否则很多 ROM 上根本察觉不到
            alerts.vibrationPattern = longArrayOf(0, 400, 200, 400)
            nm.createNotificationChannel(alerts)

            // 清掉历史遗留的静音提醒渠道，避免在系统设置里留一个用不到的条目
            nm.deleteNotificationChannel(legacyAlertChannelId)
        }
    }

    private fun startForegroundServiceWithNotification(title: String, content: String) {
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(com.example.classreminder.R.drawable.ic_notification_clock)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    /**
     * 更新前台通知。
     * [bigText] 是展开后（锁屏上滑一下）显示的今日剩余课程清单。
     * VISIBILITY_PUBLIC 让内容在锁屏上直接可见，而不是"内容已隐藏"。
     */
    private fun updateForegroundNotification(title: String, content: String, bigText: String? = null) {
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(com.example.classreminder.R.drawable.ic_notification_clock)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        if (!bigText.isNullOrBlank()) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(FOREGROUND_NOTIFICATION_ID, builder.build())
    }

    private suspend fun checkAndNotify() {
        val now = System.currentTimeMillis()
        val all = classDao.getAll()

        val currentWeek = Prefs.currentWeek(applicationContext)
        val todayClasses = TodaySchedule.today(all, currentWeek, now)

        val ongoing = todayClasses.filter { it.ongoingAt(now) }
        // read advance minutes from preferences (default 30)
        val advance = Prefs.getAdvanceMinutes(applicationContext)
        val upcomingWindow = advance * 60 * 1000L
        val upcoming = todayClasses.filter { it.startsWithin(now, upcomingWindow) }

        val chosen = when {
            ongoing.isNotEmpty() -> ongoing.maxByOrNull { it.startMillis }
            upcoming.isNotEmpty() -> upcoming.minByOrNull { it.startMillis }
            else -> null
        }
        val remaining = TodaySchedule.remaining(todayClasses, now)
        // 锁屏上展开通知时看到的今日剩余清单
        val remainingList = remaining.take(5).joinToString("\n") {
            "${formatTime(it.startMillis)}-${formatTime(it.endMillis)}  ${it.entity.title}" +
                if (it.entity.room.isNotBlank()) "  ${it.entity.room}" else ""
        }

        // Update the foreground notification to show detection status
        if (chosen != null) {
            val prefix = if (chosen.ongoingAt(now)) "正在上课" else "即将上课"
            updateForegroundNotification(
                "$prefix：${chosen.entity.title}",
                "${chosen.entity.room}  ${formatTime(chosen.startMillis)} - ${formatTime(chosen.endMillis)}",
                bigText = if (remainingList.isNotBlank()) "今日剩余：\n$remainingList" else null
            )
        } else if (todayClasses.isEmpty()) {
            updateForegroundNotification(appLabel, "运行中 — 今日无课")
        } else if (remaining.isEmpty()) {
            // 今天的课全部上完了
            updateForegroundNotification("今日课程已全部结束！", "今天共 ${todayClasses.size} 节课")
        } else {
            // 空闲时段：只报「今天还剩几节」，不是今天的总数
            val next = remaining.first()
            updateForegroundNotification(
                "$appLabel — 今日剩余 ${remaining.size} 节课",
                "下一节：${next.entity.title} ${formatTime(next.startMillis)} ${next.entity.room}",
                bigText = "今日剩余：\n$remainingList"
            )
        }

        // ── 有课程需提醒：发一条独立的高优先级提醒通知（可选全屏弹窗） ──
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

            // 先亮屏再发通知：ColorOS 会拦掉全屏弹窗，亮屏 + 高优先级通知是唯一还能用的提醒路径
            wakeScreen()

            // 提醒必须走 IMPORTANCE_HIGH 的 notifChannelId：Android 8+ 忽略 setPriority，
            // 渠道重要性才决定通知能否打断免打扰、能否拉起全屏弹窗
            var builder = NotificationCompat.Builder(this, notifChannelId)
                .setContentTitle("$prefix：${chosen.entity.title}")
                .setContentText("${chosen.entity.room}  ${formatTime(chosen.startMillis)} - ${formatTime(chosen.endMillis)}")
                .setSmallIcon(com.example.classreminder.R.drawable.ic_notification_clock)
                .setOngoing(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(PendingIntent.getActivity(this, 1002,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    else PendingIntent.FLAG_UPDATE_CURRENT))
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
            nm.notify(ALERT_NOTIFICATION_ID, builder.build())

            lastShownId = id
        } else {
            // no candidate; reset lastShownId so future ones can show, and clear the alert
            lastShownId = null
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .cancel(ALERT_NOTIFICATION_ID)
        }
    }

    private fun formatTime(millis: Long): String {
        synchronized(timeCal) {
            timeCal.timeInMillis = millis
            val h = timeCal.get(java.util.Calendar.HOUR_OF_DAY)
            val m = timeCal.get(java.util.Calendar.MINUTE)
            return String.format(java.util.Locale.getDefault(), "%02d:%02d", h, m)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
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


