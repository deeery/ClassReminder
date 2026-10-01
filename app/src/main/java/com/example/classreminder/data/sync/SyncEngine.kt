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
}

/**
 * 云同步引擎。
 *
 * ## 流程（顺序**不能反**）
 *
 * 1. **先 push 再 pull** —— 反过来会让刚拉下来的记录被当成新的推回去，产生假冲突。
 * 2. pull 循环到拉干净（服务端一次最多给 500 条）。
 * 3. 落库完成后才推进游标 —— 中途失败保持原值，宁可多拉一次，不可漏数据。
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

    /** 应用启动时调用 */
    fun syncOnStart() {
        if (!accountSession.isSignedIn()) return
        scope.launch { runSync() }
    }

    /**
     * 退出登录时清掉游标 —— 换账号后不能接着上一个人的游标拉，
     * 否则新账号的数据会被「已经同步过了」误判而永远拉不下来。
     */
    fun resetForSignOut() {
        debounceJob?.cancel()
        prefs.lastCursor = 0
        _state.value = SyncState(phase = SyncPhase.SKIPPED, message = "未登录，登录后自动同步")
    }

    // ── 同步主体 ────────────────────────────────────────────────

    private suspend fun runSync(): SyncState = mutex.withLock {
        // ⚠️ Android 主线程禁止网络访问：HttpURLConnection 在主线程直接抛
        // NetworkOnMainThreadException，而**它的 message 是 null** ——
        // 下面的 catch 只能落到兜底文案，界面显示「同步失败」且没有任何线索。
        // SyncEngine 的 scope 是 viewModelScope（Dispatchers.Main.immediate），
        // 所以整个同步体（HTTP 请求 + 首端切换时的备份写盘）必须显式切到 IO。
        // 桌面端没有这个限制，这层包装是移动端特有的。
        withContext(Dispatchers.IO) { runSyncOnIo() }
    }

    private suspend fun runSyncOnIo(): SyncState {
        val token = accountSession.token()
        if (token.isNullOrBlank()) {
            val result = _state.value.copy(
                phase = SyncPhase.SKIPPED,
                message = "未登录，登录后自动同步"
            )
            _state.value = result
            return result
        }

        _state.value = _state.value.copy(phase = SyncPhase.BUSY, message = "正在同步…")

        return try {
            // ── 1. push（必须先push） ─────────────────────────
            val localChanges = collectLocalChanges()
            val push = if (localChanges.isEmpty()) null else SyncApi.push(localChanges, token)

            // ⚠️ 这里**必须**写 `== true`，不能写 `!= false` 或直接用。
            // 服务端的 `isInitialDevice` 是**三态**的：
            //   true  = 我就是首端→ 不清空
            //   false = 首端是别的设备 → 要清空
            //   null  = 还没任何设备认领过（用户刚注册）→ **绝不能清空**，会白丢数据
            // 而 pull / push 路由里写的是 `replaceLocal = (initial === false)`，
            // 把 true 和 null 合并成了 false。所以这里收到的 false 是
            // 「我是首端」或「还没人认领」两种情况 —— **都清不得**。
            // 反过来写（凡非 true 就清）会在用户刚注册首跑时把本地数据全丢掉。
            if (push?.replaceLocal == true) {
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
                val page = SyncApi.pull(cursor = cursor, token = token)
                incoming += page.changes
                cursor = page.cursor
                serverCursor = page.cursor
                hasMore = page.hasMore
            }

            // ── 3. apply ──────────────────────────────────────
            val applied = applyRemote(incoming)

            // 有东西落库就通知界面刷新 —— 否则数据进了库、界面还停在旧内容，
            // 用户看到「已同步」却发现课表纹丝不动。
            if (applied > 0) onApplied(applied)

            // ── 4. 记游标 ─────────────────────────────────────
            // 只在成功收尾后推进。中途失败保持原值，下次重来 —— 宁可多拉一次，不可漏数据。
            prefs.lastCursor = serverCursor

            // 同步成功顺手清理 30 天前的墓碑
            val purged = purgeOldTombstones()

            val overridden = push?.conflicts?.size ?: 0
            val result = SyncState(
                phase = SyncPhase.SUCCESS,
                lastSyncedAt = System.currentTimeMillis(),
                message = describe(localChanges.isNotEmpty(), applied, overridden, purged),
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
     */
    private suspend fun applyRemote(changes: List<RemoteChange>): Int {
        if (changes.isEmpty()) return 0

        var applied = 0

        val localClasses = syncDao.allClasses().associateBy { it.uid }
        val localNotes = syncDao.allNotes().associateBy { it.uid }
        var cursorClass = syncDao.nextFreeClassId()
        var cursorNote = syncDao.nextFreeNoteId()
        val usedClassIds = localClasses.values.map { it.id }.toMutableSet()
        val usedNoteIds = localNotes.values.map { it.id }.toMutableSet()

        for (change in changes) {
            val remoteUpdatedAt = change.data.syncUpdatedAt()
            val remoteDeletedAt = change.data.syncDeletedAt()

            if (change.entity == "class") {
                val decoded = BackupCodec.decodeCourses(
                    jsonObject("items" to jsonArray(listOf(change.data)))
                ).firstOrNull() ?: continue

                val local = localClasses[change.uid]
                val decision = SyncMerge.resolve(
                    localIdOfUid = local?.id,
                    localUpdatedAt = local?.updatedAt ?: 0L,
                    remoteUpdatedAt = remoteUpdatedAt,
                    remotePreferredId = decoded.id,
                    cursor = cursorClass,
                    used = usedClassIds
                )
                if (decision is SyncMerge.Decision.Skip) continue

                val id = (decision as SyncMerge.Decision.Write).id
                cursorClass = decision.nextCursor
                usedClassIds += id

                syncDao.upsertRawClass(
                    decoded.copy(
                        id = id,
                        uid = change.uid,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    )
                )
                applied++
                continue
            }

            if (change.entity == "note") {
                val decoded = BackupCodec.decodeNotes(
                    jsonObject("items" to jsonArray(listOf(change.data)))
                ).firstOrNull() ?: continue

                val local = localNotes[change.uid]
                val decision = SyncMerge.resolve(
                    localIdOfUid = local?.id,
                    localUpdatedAt = local?.updatedAt ?: 0L,
                    remoteUpdatedAt = remoteUpdatedAt,
                    remotePreferredId = decoded.id,
                    cursor = cursorNote,
                    used = usedNoteIds
                )
                if (decision is SyncMerge.Decision.Skip) continue

                val id = (decision as SyncMerge.Decision.Write).id
                cursorNote = decision.nextCursor
                usedNoteIds += id

                syncDao.upsertRawNote(
                    decoded.copy(
                        id = id,
                        uid = change.uid,
                        updatedAt = remoteUpdatedAt,
                        deletedAt = remoteDeletedAt
                    )
                )
                applied++
            }
        }

        return applied
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
        purged: Int
    ): String {
        val parts = ArrayList<String>(4)
        if (overridden > 0) parts += "服务端更新覆盖了 $overridden 条"
        if (pushedAny) parts += "已上传"
        if (applied > 0) parts += "已下载 $applied 条"
        if (purged > 0) parts += "清理了 $purged 条已删除"
        if (parts.isEmpty()) return "已是最新"
        return parts.joinToString(" · ")
    }
}
