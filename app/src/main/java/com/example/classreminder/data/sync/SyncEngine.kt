package com.example.classreminder.data.sync

import com.example.classreminder.data.ClassEntity
import com.example.classreminder.data.NoteEntity
import com.example.classreminder.data.backup.BackupCodec
import com.example.classreminder.data.backup.JsonValue
import com.example.classreminder.data.backup.jsonArray
import com.example.classreminder.data.backup.jsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** 同步的阶段。UI 据此决定显示什么文案、按钮是否可点。 */
enum class SyncPhase {
    /** 还没开始 / 没登录 */
    IDLE,

    /** 正在同步。按钮禁用、不转圈（转圈在手机上过于频繁） */
    BUSY,

    /** 刚同步完 */
    SUCCESS,

    /** 同步失败，等下次自动重试 */
    FAILED,

    /** 未登录已跳过 */
    SKIPPED,

    /** 网络不通 */
    OFFLINE
}

/**
 * 同步状态。UI 只读这一个对象就能把同步卡画出来。
 *
 * @param overriddenCount 本次同步里被服务端版本覆盖的本地记录数。
 *   单独拎出来是因为这不是错误 —— 用户在手机上改的东西被桌面端更新覆盖了，
 *   得让他知道，否则会以为自己的修改丢了。
 * @param backupPath 首端切换时被备份到的那份文件的完整路径。
 *   必须如实展示：这一刻用户本地数据被清空了，他得知道去哪找备份。
 */
data class SyncState(
    val phase: SyncPhase = SyncPhase.IDLE,
    val lastSyncedAt: Long = 0L,
    val message: String = "",
    val overriddenCount: Int = 0,
    val backupPath: String? = null,
    val online: Boolean = true
)

/** 同步游标等需要持久化的偏好。 */
interface SyncPrefs {
    /** 上次同步到哪一条了（服务端 revision 游标） */
    var lastCursor: Int

    /**
     * 本地这份数据 / 这个游标属于哪个账号（`users.id`）。0 = 还没同步过任何账号。
     *
     * 用途只有一个：判断「本设备跟这个账号同步过没有」。没同步过就意味着
     * 本地这份数据的来历不明，**没有资格覆盖服务端** —— 见 [SyncEngine.runSyncOnIo]
     * 第 0 步与设计 §5.8「首端权威」。与桌面端 `SyncPrefs.syncedAccountId` 同语义。
     */
    var syncedAccountId: Int
}

/**
 * 云同步引擎。
 *
 * ## 流程（顺序**不能反**）
 *
 * 1. **先收敛本地同 uid 重复行，再 push** —— 那些是旧版 `applyRemote` 留下的脏数据，
 *    原样推上去会被服务端判成冲突，UI 上冒出「覆盖了 N 条」的假警告。
 * 2. **先 push 再 pull** —— 反过来会让刚拉下来的记录被当成新的推回去，产生假冲突。
 * 3. pull 循环到拉干净（服务端一次最多给 500 条）。
 * 4. 落库完成后才推进游标 —— 中途失败保持原值，宁可多拉一次，不可漏数据。
 *
 * ## 触发时机（与桌面端一致）
 *
 * 启动 + 每次写操作后 30 秒防抖 + 手动触发。
 * 防抖是为了「连续改 10 次只推 1 次」，但也不能无限制拖 —— 用户改完课表
 * 可能在下一秒就关了应用。
 *
 * ## 并发保护
 *
 * [mutex] 保证两次同步不重叠。安卓上界面重建、生命周期回调都可能触发同步，
 * 叠加起来会出现「两个同步同时推进游标」，其中一个把另一个的进度覆盖掉。
 */
class SyncEngine(
    private val syncDao: SyncDao,
    private val accountSession: AccountSession,
    private val prefs: SyncPrefs,
    private val scope: CoroutineScope,
    /** 落库后通知界面刷新。数据进了库但界面没变，用户会以为同步没生效。 */
    private val onApplied: (Int) -> Unit = {},
    /**
     * 把当前全部数据写成一份备份文件，返回**完整路径**；返回 null 表示没写成。
     *
     * 只负责「写文件」这一件事，**不要**在这里清库或动游标 ——
     * 那两件事由引擎自己做（见 `handleReplaceLocal`）。把清库塞进回调里的话，
     * 万一调用方忘了做，症状是「换了首端但本地旧数据还在，永远合并不掉」，
     * 属于极难自查的一类。
     */
    private val onBackupBeforePurge: suspend () -> String? = { null }
) {

    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var debounceJob: Job? = null

    /** [syncOnStart] 的一次性守卫；见该方法的 KDoc */
    private var launchSyncDone = false

    companion object {
        /** 写操作后等这么久才真的同步（设计 §5.8） */
        const val DEBOUNCE_MS = 30_000L
    }

    /**
     * 手动触发同步（设置页的按钮）。
     *
     * 手动要**立即**跑，不等防抖 —— 用户主动点了就是在等结果。
     * 会先取消待执行的防抖同步，避免两轮并发推进游标。
     */
    fun syncNow(onDone: (SyncState) -> Unit = {}) {
        if (!accountSession.isSignedIn()) {
            onDone(_state.value.copy(phase = SyncPhase.SKIPPED, message = "未登录，登录后自动同步"))
            return
        }
        debounceJob?.cancel()
        scope.launch { onDone(runSync()) }
    }

    /**
     * 写操作后调用：30 秒防抖。
     *
     * 用户连续编辑会反复触发，靠取消前一个 job 实现节流。
     * 30 秒是权衡：足够把「顺手改好几条」合并成一次，
     * 又不至于让用户等太久（他们可能改完就切走了）。
     */
    fun scheduleSync() {
        if (!accountSession.isSignedIn()) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            runSync()
        }
    }

    /**
     * 应用启动时调用（恢复登录态后接着用本机数据，**不动**它）。
     *
     * **同一个登录会话内最多生效一次**，与桌面端 `SyncEngine.startOnLaunch()`
     * 的同名守卫对齐：`MainActivity` 没有声明 `configChanges`，旋转屏幕会重建
     * Activity → `onCreate` 再跑一遍 `bootstrap()` → 这里会再同步一次。
     * 多跑一次本身无害（`replace_local` 已改成一次性），但纯属浪费一次往返。
     *
     * 手动同步走 [syncNow]，不受这个守卫影响。
     */
    fun syncOnStart() {
        if (launchSyncDone) return
        if (!accountSession.isSignedIn()) return
        launchSyncDone = true
        scope.launch { runSync() }
    }

    /**
     * **用户主动登录后**调用：把本地强制对齐到服务端（＝首端设备）的配置。
     *
     * ## 和 [syncOnStart] 的区别只有一点：要不要对齐
     *
     * - 启动时恢复登录态 = 「接着用本机已有的数据」，**不动它**；
     * - 用户主动登录 = 「我要用这个账号的配置」，这时本地那份可能来自
     *   上一次登录的账号、也可能早就跟云端分叉了，所以要拉回正轨。
     *
     * 对齐的具体动作见 [runSyncOnIo] 的第 0 步：先问服务端有没有数据，
     * 有就**跳过 push** 直接「备份 → 清空 → 全量拉」。被覆盖掉的那份本地配置
     * 会写成 `StuMate-preinit-backup-<时间戳>.json`（见 `AppViewModel.writePreInitBackup`），
     * 路径显示在同步卡上。
     *
     * ## 为什么不能拿「登录态变了」当触发信号
     *
     * 登录态在「启动恢复」和「主动登录」两种情况下都会从「未登录」变「已登录」，
     * 拿它当信号会把「每次开软件」也变成「每次清库」。
     * 所以调用方要用 [AccountSession.loginEpoch] 区分，见 `AppViewModel.onSignInChanged`。
     */
    fun syncAfterLogin() {
        if (!accountSession.isSignedIn()) return
        // 本次会话的「启动同步」已被它取代，别再让 syncOnStart 多跑一遍
        launchSyncDone = true
        scope.launch { runSync(forceAlign = true) }
    }

    /**
     * 退出登录时清掉游标 —— 换账号后不能接着上一个人的游标拉，
     * 否则新账号的数据会被「已经同步过了」误判而永远拉不下来。
     */
    fun resetForSignOut() {
        debounceJob?.cancel()
        // 允许下一个登录会话再走一次启动同步
        launchSyncDone = false
        prefs.lastCursor = 0
        // 忘掉「本地数据属于哪个账号」：下一个账号的第一轮同步要重新走
        // §5.8 的归属判定，否则会拿上一个账号的资格去推新账号的数据。
        prefs.syncedAccountId = 0
        _state.value = SyncState(phase = SyncPhase.SKIPPED, message = "未登录，登录后自动同步")
    }

    // ── 同步主体 ────────────────────────────────────────────────

    private suspend fun runSync(forceAlign: Boolean = false): SyncState = mutex.withLock {
        // ⚠️ Android 主线程禁止网络访问：HttpURLConnection 在主线程直接抛
        // NetworkOnMainThreadException，而**它的 message 是 null** ——
        // 下面的 catch 只能落到兜底文案，界面显示「同步失败」且没有任何线索。
        // SyncEngine 的 scope 是 viewModelScope（Dispatchers.Main.immediate），
        // 所以整个同步体（HTTP 请求 + 首端切换时的备份写盘）必须显式切到 IO。
        // 桌面端没有这个限制，这层包装是移动端特有的。
        withContext(Dispatchers.IO) { runSyncOnIo(forceAlign) }
    }

    private suspend fun runSyncOnIo(forceAlign: Boolean = false): SyncState {
        // ⚠️ 这里**不能**用 `accountSession.token()`。它只是「存下来的 access」，
        // 而 access 的寿命只有 15 分钟 —— 登录一刻钟之后，每次同步都拿着过期令牌去请求，
        // 服务端一律回 401，界面显示「登录已失效，请重新登录」；
        // 可用户手里的 refresh 完全有效，让他重新登录纯属误报。
        // 下面两个网络调用都改走 `accountSession.authed { }`：它先判断过期并自动轮转，
        // 遇到 401 还会强刷一次再重试。
        if (!accountSession.isSignedIn()) {
            val result = _state.value.copy(
                phase = SyncPhase.SKIPPED,
                message = "未登录，登录后自动同步"
            )
            _state.value = result
            return result
        }

        _state.value = _state.value.copy(phase = SyncPhase.BUSY, message = "正在同步…")

        var alignedToServer = false

        return try {
            // ── 1. 收敛本地脏数据 ────────────────────────────
            //
            // 必须在 push 之前：否则那几行重复会被原样推上去，
            // 服务端按 uid 去重不会存两条，但 `conflicts` 会被撑起来，
            // UI 上冒出「服务端更新覆盖了 N 条」的假警告。
            //
            // 两步顺序不能换：
            //   ① 同 uid 多行 → 物理删多余行（uid 是唯一标识，多行必是脏数据）
            //   ② 内容一致但 uid 不同 → 败者转墓碑并推出去
            // 先做 ①，② 才是在「每个 uid 只剩一行」的干净输入上做内容分组。
            //
            // ⚠️ 与桌面端 `SyncEngine.syncOnce` 顺序一致，别只改一边。
            val collapsed =
                collapseSameUidDuplicates() + collapseCrossUidDuplicates()

            // ── 2. 先决定「本地这份分歧能不能推上去」（§5.8 首端权威）──
            //
            // 两种情况必须先问服务端，不能上来就 push：
            //   · [syncAfterLogin]：用户主动登录，要求「让我这台显示首端那台的配置」；
            //   · 本设备还没跟这个账号同步过：本地这份数据的来历不明
            //     （上一台设备？上一个账号？上一次安装？），没有资格覆盖服务端。
            //
            // 判定只有一条规则：
            //   服务端没有数据         → 照常 push。**必须**这样，否则
            //                            `initial_device_id` 永远是 null，谁都成不了首端
            //   服务端有数据 + 我是首端 → 照常 push（本地就是权威本身，没什么可「对齐」的）
            //   服务端有数据 + 我不是首端 → **跳过 push**，直接「备份 → 清空 → 全量拉」，
            //                            本地这份分歧只留在备份文件里
            //
            // ## 为什么非要提前问
            //
            // 原来的写法是「先 push，再看服务端回的 `replace_local`」—— 可那时
            // 本地数据**已经推上去了**，§5.8「首端权威」形同虚设。
            //
            // ⚠️ `isInitialDevice != true` 而不是 `== false`：这个字段是三态的，
            // null（还没有设备认领）和 false（首端是别人）要区别对待 ——
            // 见 `MiniJson.boolOrNull` 与 `SyncStatus.isInitialDevice`。
            val accountId = accountSession.user.value?.id ?: 0
            val neverSyncedThisAccount = prefs.syncedAccountId != accountId
            var replaceNow = false
            if (forceAlign || neverSyncedThisAccount) {
                val status = accountSession.authed { t -> SyncApi.status(t) }
                val serverHasData = status.courses > 0 || status.notes > 0
                if (serverHasData && status.isInitialDevice != true) {
                    replaceNow = true
                    alignedToServer = true
                }
            }

            // ── 3. push（必须先push） ─────────────────────────
            var localChanges: List<LocalChange> = emptyList()
            var push: PushResult? = null
            if (!replaceNow) {
                localChanges = collectLocalChanges()
                push = if (localChanges.isEmpty()) null
                else accountSession.authed { t -> SyncApi.push(localChanges, t) }

                // ⚠️ 这里**必须**写 `== true`，不能写 `!= false` 或直接用。
                // 服务端的 `isInitialDevice` 是**三态**的：
                //   true  = 我就是首端→ 不清空
                //   false = 首端是别的设备 → 要清空
                //   null  = 还没任何设备认领过（用户刚注册）→ **绝不能清空**，会白丢数据
                // 而 pull / push 路由里写的是 `replaceLocal = (initial === false)`，
                // 把 true 和 null 合并成了 false。所以这里收到的 false 是
                // 「我是首端」或「还没人认领」两种情况 —— **都清不得**。
                // 反过来写（凡非 true 就清）会在用户刚注册首跑时把本地数据全丢掉。
                if (push?.replaceLocal == true) replaceNow = true
            }

            if (replaceNow) {
                val backup = handleReplaceLocal()
                // 备份路径要如实带到 UI 上：这一刻用户本地数据被清空了，
                // 他必须知道去哪找那份备份才安心（设计 §5.8 要求「明确告知路径」）
                if (backup != null) {
                    _state.value = _state.value.copy(backupPath = backup)
                }
            }

            // ── 2. pull（循环到拉干净） ─────────────────────────
            var cursor = prefs.lastCursor
            val incoming = ArrayList<RemoteChange>()
            var serverCursor = push?.cursor ?: cursor

            // `hasMore` 必须循环处理：服务端一次最多给 500 条，
            // 用户攒到上千条时一次拉不完。不循环就会静默丢掉后半截 ——
            // 而「同步了但少了数据」比「没同步」糟糕得多。
            //
            // 首端切换时 onBackupBeforePurge 已把游标归零，所以这一轮的第一页就是全量，
            // 但**仍要翻页**（服务端每页上限 500，超过 500 条的账号第一页装不下）。
            var guard = 0
            var hasMore = true
            while (hasMore && guard++ < 1000) {
                val page = accountSession.authed { t -> SyncApi.pull(cursor = cursor, token = t) }
                incoming += page.changes
                cursor = page.cursor
                serverCursor = page.cursor
                hasMore = page.hasMore
            }

            // ── 5. apply ──────────────────────────────────────
            val applied = applyRemote(incoming)

            // 有东西落库就通知界面刷新 —— 否则数据进了库、界面还停在旧内容，
            // 用户看到「已同步」却发现课表纹丝不动。
            // 收敛掉的重复行也要算进去：那同样是用户能看见的变化。
            if (applied > 0 || collapsed > 0) onApplied(applied + collapsed)

            // ── 6. 记游标 ─────────────────────────────────────
            // 只在成功收尾后推进。中途失败保持原值，下次重来 —— 宁可多拉一次，不可漏数据。
            prefs.lastCursor = serverCursor
            // 记下「本地这份数据现在属于哪个账号」。下一轮起就不必再问服务端
            // 「我有没有资格 push」了 —— 这一轮已经按 §5.8 把归属理清了。
            // **只在成功收尾后写**：失败时保持原值，下一轮重新判定。
            prefs.syncedAccountId = accountId

            // 同步成功顺手清理 30 天前的墓碑
            val purged = purgeOldTombstones()

            val overridden = push?.conflicts?.size ?: 0
            val result = SyncState(
                phase = SyncPhase.SUCCESS,
                lastSyncedAt = System.currentTimeMillis(),
                message = if (alignedToServer) {
                    "已对齐首端配置 · 拉取 $applied 条"
                } else {
                    describe(localChanges.isNotEmpty(), applied, overridden, purged, collapsed)
                },
                overriddenCount = overridden,
                backupPath = _state.value.backupPath,
                online = true
            )
            _state.value = result
            result
        } catch (e: ApiException) {
            val result = _state.value.copy(
                phase = if (e.isNetwork) SyncPhase.OFFLINE else SyncPhase.FAILED,
                message = if (e.isNetwork) "连不上服务器，稍后会自动重试" else e.message
            )
            _state.value = result
            result
        } catch (e: Exception) {
            val result = _state.value.copy(
                phase = SyncPhase.FAILED,
                // 兜底文案带上异常类名。有些异常（NetworkOnMainThreadException 等）
                // message 就是 null，只写「同步失败」等于把线索全丢了 ——
                // 曾经因此把一个线程问题误判成「网络不通」，白查很久。
                message = e.message ?: e::class.java.simpleName.ifBlank { "同步失败" }
            )
            _state.value = result
            result
        }
    }

    // ── 收集本地变更 ────────────────────────────────────────────

    /**
     * 收集本地待推送的记录。
     *
     * 推**全部**行（含已软删的）而不是「有变化的」：
     * 客户端没有「哪些比服务端新」的本地状态，靠服务端的 LWW 判定即可。
     * 代价是流量，但每条几百字节、10 人规模，完全可接受。
     *
     * ⚠️ `uid` 为空的行直接过滤掉：v7 迁移会给老数据补 uid，
     * 但万一行是在迁移前插入的（异常路径），空 uid 推上去会被服务端判非法。
     */
    private suspend fun collectLocalChanges(): List<LocalChange> {
        val out = ArrayList<LocalChange>()

        syncDao.allClasses().forEach { c ->
            if (c.uid.isBlank()) return@forEach
            out += LocalChange(
                entity = "class",
                uid = c.uid,
                data = oneItem(BackupCodec.encodeCourses(listOf(c))),
                updatedAt = c.updatedAt,
                deletedAt = c.deletedAt,
                // LWW 按 `updatedAt` 比，baseVersion 一律填 0
                baseVersion = 0
            )
        }

        syncDao.allNotes().forEach { n ->
            if (n.uid.isBlank()) return@forEach
            out += LocalChange(
                entity = "note",
                uid = n.uid,
                data = oneItem(BackupCodec.encodeNotes(listOf(n))),
                updatedAt = n.updatedAt,
                deletedAt = n.deletedAt,
                baseVersion = 0
            )
        }

        return out
    }

    /** 把 `{count, items:[x]}` 拆出 `x` —— 服务端要的是裸记录，不要那层包装 */
    private fun oneItem(v: JsonValue): JsonValue.Obj {
        val items = (v as? JsonValue.Obj)?.fields?.get("items") as? JsonValue.Arr
        return items?.items?.firstOrNull() as? JsonValue.Obj ?: JsonValue.Obj(emptyMap())
    }

    // ── 应用服务端变更 ──────────────────────────────────────────

    /**
     * 把服务端来的变更落到本地，返回真正改动了几行。
     *
     * 判定逻辑全在 [SyncMerge] 里（纯函数，有单测穷举边界），
     * 这里只负责「查库 → 调判定 → 落库」这三步胶水。
     *
     * ## 索引必须随写随更新（[SyncMergeIndex]）
     *
     * 服务端的 `pull` 给的是**原始变更流水**，同一个 uid 改过几次就有几条。
     * 循环里若还用循环外那份快照查 uid，批内第二次遇到同一个 uid 时查不到
     * 刚写进去的那一行 → 当成新记录 → 又 INSERT 一行。
     * 全量重拉时每条记录都会被写成 2~4 份，这就是「课表里一堆一模一样的课」。
     *
     * ## 跨 uid 的同一门课
     *
     * 两台设备各自新建同一门课会各生成一个 uid，服务端只认 uid，
     * 于是同一门课在服务端是两条记录，谁拉下来都看到两份。
     * 这里按 [SyncMerge.courseKey] 的内容指纹认领，并用
     * [SyncMerge.electSurvivor]（uid 字典序小者胜）决定留哪条 ——
     * 关键是**确定性**：两台设备必须算出同一个存活者，
     * 否则会变成「你删我、我删你」把课删没。
     */
    private suspend fun applyRemote(changes: List<RemoteChange>): Int {
        if (changes.isEmpty()) return 0

        var applied = 0

        // ── 课程：活索引 + 内容指纹表 ──────────────────────────
        val classIndex = SyncMergeIndex()
        // 内容指纹 → 该内容目前「活着」的那一行的 uid。
        // 墓碑**不**进这张表：删除本来就该原样传播，拿墓碑去参与去重会把删除也去重掉。
        val classAliveUidByContent = HashMap<String, String>()
        val classAliveRow = HashMap<String, ClassEntity>()
        syncDao.allClasses().forEach { e ->
            classIndex.seed(e.uid, e.id, e.updatedAt)
            if (e.deletedAt == 0L && e.uid.isNotBlank()) {
                classAliveUidByContent[SyncMerge.courseKey(e)] = e.uid
                classAliveRow[e.uid] = e
            }
        }
        var cursorClass = syncDao.nextFreeClassId()

        // ── 便签：只做活索引 ───────────────────────────────────
        // 便签不做内容去重：它的「内容一致」比课程含糊得多（position 是各机各自的布局，
        // createdAt 又天然不同），合并错了代价大于收益。同 uid 多行的问题照样被修掉。
        val noteIndex = SyncMergeIndex()
        syncDao.allNotes().forEach { noteIndex.seed(it.uid, it.id, it.updatedAt) }
        var cursorNote = syncDao.nextFreeNoteId()

        for (change in changes) {
            if (change.entity == "class") {
                val decoded = BackupCodec.decodeCourses(
                    jsonObject("items" to jsonArray(listOf(change.data)))
                ).firstOrNull() ?: continue

                val remoteUpdatedAt = change.data.syncUpdatedAt()
                val remoteDeletedAt = change.data.syncDeletedAt()

                // ① 先看是不是「另一台设备建的同一门课」
                if (classIndex.row(change.uid) == null) {
                    val aliveUid = classAliveUidByContent[SyncMerge.courseKey(decoded)]
                    when (val plan = SyncMerge.planTwin(
                        remoteUid = change.uid,
                        remoteAlive = remoteDeletedAt == 0L,
                        localAliveUid = aliveUid
                    )) {
                        is SyncMerge.TwinPlan.TombstoneRemote -> {
                            // 本地这条留下，给远端那个 uid 写一条墓碑 ——
                            // 这样删除会同步到服务端和其他设备，本机课表也不再重复。
                            // 墓碑用**新 id**：不能覆盖掉要留下的那一行。
                            val now = System.currentTimeMillis()
                            val id = SyncMerge.pickId(0, cursorClass, classIndex.ids())
                            cursorClass = maxOf(cursorClass, id + 1)
                            syncDao.upsertRawClass(
                                decoded.copy(
                                    id = id,
                                    uid = change.uid,
                                    updatedAt = now,
                                    deletedAt = now
                                )
                            )
                            classIndex.record(change.uid, id, now)
                            applied++
                            continue
                        }

                        is SyncMerge.TwinPlan.AdoptRemote -> {
                            val local = classAliveRow[plan.localUid]
                            if (local != null) {
                                val now = System.currentTimeMillis()
                                val key = SyncMerge.courseKey(local)
                                // 旧 uid 的墓碑：换个新 id，别覆盖掉要留下的那一行
                                val tombId = SyncMerge.pickId(0, cursorClass, classIndex.ids())
                                cursorClass = maxOf(cursorClass, tombId + 1)
                                syncDao.upsertRawClass(
                                    local.copy(
                                        id = tombId,
                                        uid = plan.localUid,
                                        updatedAt = now,
                                        deletedAt = now
                                    )
                                )
                                classIndex.record(plan.localUid, tombId, now)
                                // 留下来的那一行改挂远端 uid（id 不变，界面里的位置不跳）
                                syncDao.upsertRawClass(local.copy(uid = change.uid))
                                classIndex.record(change.uid, local.id, local.updatedAt)
                                classAliveRow.remove(plan.localUid)
                                classAliveRow[change.uid] = local
                                classAliveUidByContent[key] = change.uid
                                applied++
                            }
                            // 落到下面按正常 LWW 处理远端内容：本地那份更新的就保留本地
                        }

                        SyncMerge.TwinPlan.None -> Unit
                    }
                }

                // ② 正常 LWW
                val known = classIndex.row(change.uid)
                val decision = SyncMerge.resolve(
                    localIdOfUid = known?.id,
                    localUpdatedAt = known?.updatedAt ?: 0L,
                    remoteUpdatedAt = remoteUpdatedAt,
                    remotePreferredId = decoded.id,
                    cursor = cursorClass,
                    used = classIndex.ids()
                )
                if (decision is SyncMerge.Decision.Skip) continue

                val id = (decision as SyncMerge.Decision.Write).id
                cursorClass = decision.nextCursor

                val written = decoded.copy(
                    id = id,
                    uid = change.uid,
                    updatedAt = remoteUpdatedAt,
                    deletedAt = remoteDeletedAt
                )
                syncDao.upsertRawClass(written)
                classIndex.record(change.uid, id, remoteUpdatedAt)
                if (remoteDeletedAt == 0L) {
                    classAliveUidByContent[SyncMerge.courseKey(written)] = change.uid
                    classAliveRow[change.uid] = written
                } else {
                    classAliveRow.remove(change.uid)
                }
                applied++
                continue
            }

            if (change.entity == "note") {
                val decoded = BackupCodec.decodeNotes(
                    jsonObject("items" to jsonArray(listOf(change.data)))
                ).firstOrNull() ?: continue

                val remoteUpdatedAt = change.data.syncUpdatedAt()
                val remoteDeletedAt = change.data.syncDeletedAt()

                val known = noteIndex.row(change.uid)
                val decision = SyncMerge.resolve(
                    localIdOfUid = known?.id,
                    localUpdatedAt = known?.updatedAt ?: 0L,
                    remoteUpdatedAt = remoteUpdatedAt,
                    remotePreferredId = decoded.id,
                    cursor = cursorNote,
                    used = noteIndex.ids()
                )
                if (decision is SyncMerge.Decision.Skip) continue

                val id = (decision as SyncMerge.Decision.Write).id
                cursorNote = decision.nextCursor

                syncDao.upsertRawNote(
                    decoded.copy(
                        id = id,
                        uid = change.uid,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    )
                )
                noteIndex.record(change.uid, id, remoteUpdatedAt)
                applied++
            }
        }

        return applied
    }

    // ── 同 uid 重复行收敛 ───────────────────────────────────────

    /**
     * 同一个 uid 出现多行时，只保留最新的一行，其余物理删除。
     *
     * ## 为什么这是安全的
     *
     * `uid` 是跨设备唯一标识，**同一个 uid 的多行必然是同一条记录被重复写进去的**
     * （旧版 `applyRemote` 的活索引缺陷，见 [SyncMergeIndex]）。
     * 留哪一行的语义都一样，所以这不是「猜」，是确定性清理。
     *
     * ## 为什么必须清
     *
     * 这些重复行会被 `collectLocalChanges()` 原样推上去，
     * 也会在 `associateBy { it.uid }` 里被折叠成一条 —— 后者恰好掩盖了问题，
     * 让人以为库里是干净的。
     *
     * 保留规则：`updatedAt` 大者胜；相等时取 `id` 小者（确定性 ——
     * 否则每次跑都可能删掉不同的行，行为不可复现）。
     *
     * @return 删掉了几行
     */
    private suspend fun collapseSameUidDuplicates(): Int {
        val classKeep = HashMap<String, Keeper>()
        val classDrop = ArrayList<Int>()
        syncDao.allClasses().forEach {
            keepNewest(classKeep, classDrop, it.uid, it.id, it.updatedAt)
        }

        val noteKeep = HashMap<String, Keeper>()
        val noteDrop = ArrayList<Int>()
        syncDao.allNotes().forEach {
            keepNewest(noteKeep, noteDrop, it.uid, it.id, it.updatedAt)
        }

        var removed = 0
        if (classDrop.isNotEmpty()) removed += syncDao.deleteClassesByIds(classDrop)
        if (noteDrop.isNotEmpty()) removed += syncDao.deleteNotesByIds(noteDrop)
        return removed
    }

    /**
     * 把**本机已有的**「内容一致但 uid 不同」的活行收敛成一条。
     *
     * ## 为什么 `applyRemote` 里的 `planTwin` 不够
     *
     * `planTwin` 只在**远端那条到达时**才触发。用户库里已经躺着的重复
     * （两台设备各建过一次同一门课）不会自己消失 —— 游标早就推到最后了，
     * `pull` 不会再把它们发下来，`applyRemote` 根本没机会看到。
     * 所以每轮同步要主动扫一遍本地。
     *
     * ## 为什么败者写墓碑，而不是直接删
     *
     * 直接删只清掉本机那份，**服务端还留着**。任何一次全量重拉
     * （新设备首次同步 / 用户重新登录 / 首端切换）都会把它拉回来 ——
     * 重复「复活」。写墓碑（`deletedAt = now`）才会被
     * `collectLocalChanges()` 推上去，让服务端也标成删除，两端一起收敛。
     *
     * ## 为什么留 uid 最小的那条
     *
     * 与 [SyncMerge.electSurvivor] 同一个判据。🔴 必须确定性 ——
     * 两台设备各留「自己那条」会变成「你删我、我删你」把课删没。
     *
     * 便签**不做**内容去重（理由见 [applyRemote]）。
     *
     * ⚠️ 与桌面端 `SyncEngine.collapseCrossUidDuplicates` 逐字对齐。
     *
     * @return 转成墓碑的行数
     */
    private suspend fun collapseCrossUidDuplicates(): Int {
        // 指纹 → 该内容目前 uid 最小的那条活行
        val winner = HashMap<String, ClassEntity>()
        val losers = ArrayList<ClassEntity>()

        syncDao.allClasses().forEach { e ->
            if (e.deletedAt != 0L || e.uid.isBlank()) return@forEach
            val key = SyncMerge.courseKey(e)
            val cur = winner[key]
            when {
                cur == null -> winner[key] = e
                // 与 electSurvivor 同判据：cur 更小就留 cur，否则换 e
                SyncMerge.electSurvivor(cur.uid, e.uid) == cur.uid -> losers += e
                else -> {
                    losers += cur
                    winner[key] = e
                }
            }
        }
        if (losers.isEmpty()) return 0

        // `updatedAt` 一起推到现在：服务端是按 `updatedAt` 判 LWW 的，
        // 不推新的话「删除」会比服务端那条旧记录更旧 → 被拒 → 服务端留着它。
        val now = System.currentTimeMillis()
        losers.forEach { syncDao.upsertRawClass(it.copy(updatedAt = now, deletedAt = now)) }
        return losers.size
    }

    /** 当前保留者的定位信息，只用于比较，不回表 */
    private data class Keeper(val id: Int, val updatedAt: Long)

    private fun keepNewest(
        keep: HashMap<String, Keeper>,
        drop: MutableList<Int>,
        uid: String,
        id: Int,
        updatedAt: Long
    ) {
        // uid 为空的行不该存在（v7 迁移会补），真出现了也不动它 —— 没有身份就没有判据
        if (uid.isBlank()) return
        val current = keep[uid]
        if (current == null) {
            keep[uid] = Keeper(id, updatedAt)
            return
        }
        val candidateWins = updatedAt > current.updatedAt ||
            (updatedAt == current.updatedAt && id < current.id)
        if (candidateWins) {
            drop += current.id
            keep[uid] = Keeper(id, updatedAt)
        } else {
            drop += id
        }
    }

    /**
     * 首端切换：先把本地数据全备份一份，再清空、游标归零。
     *
     * 顺序**不能反**：先清后备份就只剩一份空文件，等于没备份。
     *
     * 返回备份文件路径，null 表示备份失败（**照样继续**），
     * 但要让 UI 知道这件事 —— 此时用户的数据真的没有退路了。
     */
    private suspend fun handleReplaceLocal(): String? {
        // 先备份（回调只负责写文件，不碰库）
        val backup = runCatching { onBackupBeforePurge() }.getOrNull()

        // 备份失败也要往下走：用户已经选了「以服务端为准」，
        // 停在原地不清库反而会让他卡在一个「哪儿都不去」的中间态。
        // 真正的兜底是桌面端那份数据和备份功能，不是这一轮同步。
        syncDao.deleteAllClasses()
        syncDao.deleteAllNotes()
        // 游标归零 → 紧接着的这一轮 pull 就是全量重拉
        prefs.lastCursor = 0
        return backup
    }

    // ── 维护 ────────────────────────────────────────────────────

    /**
     * 物理清理软删除超过 30 天的行。
     *
     * 保留 30 天而不是服务端的 90 天：手机空间更紧张，
     * 而且「删了 30 天还想找回」的用户本来就该去用备份文件恢复。
     */
    private suspend fun purgeOldTombstones(): Int {
        val cutoff = System.currentTimeMillis() - 30L * 86_400_000L
        return syncDao.purgeOldClassTombstones(cutoff) +
            syncDao.purgeOldNoteTombstones(cutoff)
    }

    /**
     * 拼一句人话，说清这一轮干了什么。
     *
     * ⚠️ `overridden` **必须**出现在文案里（设计 §5.5：如实告知，不隐藏）：
     * 服务端版本盖掉本地改动不是错误，但用户必须知道 —— 否则他在手机上刚改的
     * 内容「自己变了」，第一反应是「这软件有 bug / 我没保存成功」，
     * 而真实原因只是他在桌面端也改了同一条。
     */
    private fun describe(
        pushedAny: Boolean,
        applied: Int,
        overridden: Int,
        purged: Int,
        collapsed: Int
    ): String {
        val parts = ArrayList<String>(5)
        if (overridden > 0) parts += "服务端更新覆盖了 $overridden 条"
        if (pushedAny) parts += "已上传"
        if (applied > 0) parts += "已下载 $applied 条"
        if (collapsed > 0) parts += "清理了 $collapsed 条重复记录"
        if (purged > 0) parts += "清理了 $purged 条已删除"
        if (parts.isEmpty()) return "已是最新"
        return parts.joinToString(" · ")
    }
}
