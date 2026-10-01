package com.example.classreminder.data.sync

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.classreminder.data.AppDatabase
import com.example.classreminder.data.MainViewModel
import com.example.classreminder.data.backup.BackupCodec
import com.example.classreminder.data.backup.BackupDocument
import com.example.classreminder.data.backup.BackupFormat
import com.example.classreminder.data.backup.BackupModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 应用级的账号 + 同步宿主。
 *
 * ## 为什么单独一个 ViewModel，而不是塞进 [MainViewModel]
 *
 * [MainViewModel] 管的是「课程和便签」这一摊业务数据，它的构造只需要 Room。
 * 而账号会话要 `SharedPreferences`、同步引擎要 `lifecycleScope` 和预备份回调 ——
 * 混进去会让业务层依赖网络与 Android 上下文，纯逻辑就不好单测了。
 *
 * 拆开之后边界很干净：
 *  - [MainViewModel]：数据怎么改，**只管发「我改完了」的信号**
 *  - [AppViewModel]：信号接到同步引擎上，顺带管登录态
 *
 * ## 为什么是 ViewModel 而不是普通单例
 *
 * 引擎持有一把 `Mutex` 和一批游标状态，配置变更（旋转屏幕）时若重建，
 * 正在跑的那轮同步会被腰斩、已推进的游标也可能丢。
 * ViewModel 活过旋转、只在真正退出 Activity 时才销毁，正好匹配这个生命周期。
 */
class AppViewModel(application: Application) : AndroidViewModel(application) {

    val accountSession: AccountSession = AccountSession(application)

    val syncEngine: SyncEngine = SyncEngine(
        syncDao = AppDatabase.getInstance(application).syncDao(),
        accountSession = accountSession,
        prefs = SyncPrefsImpl(application),
        scope = viewModelScope,
        // 远端数据落库后刷新业务层界面。少这一步的话，
        // 用户看到「已同步」但课表纹丝不动，会以为同步没生效。
        onApplied = { onDataApplied?.invoke() },
        onBackupBeforePurge = { writePreInitBackup() }
    )

    /**
     * 业务层刷新回调，由 `MainActivity` 注入 [MainViewModel::reloadFromDb]。
     *
     * 刻意用可空属性而不是构造参数：两个 ViewModel 谁先创建是不确定的
     * （`by viewModels()` 是懒加载的），构造期注入大概率拿到 null。
     */
    @Volatile
    var onDataApplied: (() -> Unit)? = null

    /**
     * 启动流程：恢复登录态 → 跑一轮同步。
     *
     * **顺序不能反**。先同步的话 [AccountSession.restore] 还没把令牌读回来，
     * [SyncEngine.syncOnStart] 会判定「未登录」直接跳过，
     * 于是这次启动永远不同步 —— 得等用户手动点一次同步才会恢复。
     *
     * [scope] 传进来而不是直接用 [viewModelScope]：调用方（Activity）决定
     * 这次启动动作能活多久，视图层不该替数据层做决定。
     */
    fun bootstrap(scope: CoroutineScope) {
        scope.launch {
            accountSession.restore()
            syncEngine.syncOnStart()
        }
    }

    /**
     * 把业务层接进来：写入变化 → 防抖同步；远端落库 → 刷新界面。
     *
     * 由 `MainActivity.onCreate` 一次性调用。两个 ViewModel 都是懒加载的，
     * 所以这个时机保证它们都已就绪 —— 早于它写回调，信号会被静默丢掉。
     */
    fun attachDataViewModel(vm: MainViewModel) {
        onDataApplied = { vm.reloadFromDb() }
        vm.onDataChanged = { syncEngine.scheduleSync() }
    }

    /**
     * 登录状态变化时调用：登录成功要立刻同步一次，登出要清游标。
     *
     * 用 `isSignedIn` 判断而不是直接比 `user`：登出时 `user` 会先被置空，
     * 而登录流程里 `user` 是拿到响应后才赋值 —— 两者时序不同，
     * 统一成「有没有令牌」这一个信号才不会漏触发。
     */
    fun onSignInChanged() {
        if (accountSession.isSignedIn()) syncEngine.syncOnStart() else syncEngine.resetForSignOut()
    }

    // ── 首端切换前的自动备份 ──────────────────────────────────────

    /**
     * 服务端说「你不是第一台」时，把本地全量导出一份再清空。
     *
     * ## 为什么必须存在
     *
     * 这一刻用户本机的课表和便签会被**整体清空**，然后全量换成服务端的。
     * 万一服务端的数据不是他想要的（比如换账号时登出漏了、或误在另一台设备
     * 做了首轮同步），没有这份备份就是**不可逆的数据丢失**。
     *
     * ## 为什么放应用私有目录而不是外部存储
     *
     * 这是同步引擎的内部保险，不是给用户手动取的文件。放`filesDir` 里
     * 既不需要 `WRITE_EXTERNAL_STORAGE`（Android 10+ 已彻底拿不到这个权限，
     * 走 MediaStore 又会进相册、被清理），也不会在用户浏览文件时误露。
     * 路径会显示在同步卡上，真需要找回时通过「用备份文件恢复」导进去即可。
     *
     * 文件名与桌面端一致（`StuMate-preinit-backup-<时间戳>.json`），
     * 方便用户在两台设备之间辨认哪份是哪份。
     *
     * 返回完整路径；返回 null 表示写失败，**引擎仍会继续清库**
     * （用户已经选了「以服务端为准」，停在中间态更糟），
     * 但 UI 会因此显示「备份失败」让他知道没有退路。
     */
    private suspend fun writePreInitBackup(): String? = runCatching {
        val syncDao = AppDatabase.getInstance(getApplication()).syncDao()
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
        val file = File(getApplication<Application>().filesDir, "StuMate-preinit-backup-$stamp.json")

        val doc = BackupDocument(
            schema = BackupFormat.SCHEMA,
            app = BackupFormat.APP_NAME,
            exportedAt = System.currentTimeMillis(),
            modules = linkedMapOf(
                BackupModule.COURSES to BackupCodec.encodeCourses(syncDao.allClasses()),
                BackupModule.NOTES to BackupCodec.encodeNotes(syncDao.allNotes())
            )
        )
        file.writeText(doc.toJson(pretty = true), Charsets.UTF_8)
        file.absolutePath
    }.getOrNull()
}