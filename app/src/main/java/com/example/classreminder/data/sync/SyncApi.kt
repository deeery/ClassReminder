package com.example.classreminder.data.sync

import com.example.classreminder.data.backup.JsonValue
import com.example.classreminder.data.backup.array
import com.example.classreminder.data.backup.bool
import com.example.classreminder.data.backup.boolOrNull
import com.example.classreminder.data.backup.int
import com.example.classreminder.data.backup.jsonArray
import com.example.classreminder.data.backup.jsonObject
import com.example.classreminder.data.backup.objOrNull
import com.example.classreminder.data.backup.str
import com.example.classreminder.data.backup.toJson

/**
 * 服务端返回的一条变更。
 *
 * [data] 是**完整记录**的 JSON —— 服务端存的就是完整记录，
 * 所以这里不需要「字段级 patch」那套东西，客户端直接整条落库即可。
 *
 * 与桌面端 `SyncApi.RemoteChange` 字段逐一对齐，两端协议表现一致。
 */
data class RemoteChange(
    val entity: String,
    val uid: String,
    val op: String,
    val data: JsonValue.Obj,
    val version: Int
)

/** `GET /sync/pull` 的响应 */
data class PullResult(
    val cursor: Int,
    /** ⚠️ 驼峰！服务端就是 `hasMore`。写成 `has_more` 会读成 false → 超 500 条静默丢数据 */
    val hasMore: Boolean,
    val changes: List<RemoteChange>,
    /** 服务端说「你不是第一台，本地要清空后全量重拉」（设计 §5.8） */
    val replaceLocal: Boolean
)

/** `POST /sync/push` 的响应 */
data class PushResult(
    val cursor: Int,
    val applied: Int,
    /** 服务端比客户端新，客户端要拿这些覆盖本地 */
    val conflicts: List<RemoteChange>,
    /** 单条不合法的记录（不阻断整批） */
    val rejected: Int,
    val replaceLocal: Boolean
)

/** `GET /sync/status` 的响应 */
data class SyncStatus(
    val cursor: Int,
    /**
     * ⚠️ **三态**，不是普通布尔：
     *   - `true`  = 我就是首端
     *   - `false` = 首端是别的设备
     *   - `null`  = 还没任何设备认领过（用户刚注册）
     *
     * 用 `Boolean`（非空）装会把后两种压成 `false`，
     * 于是「还没人认领」被当成「首端是别人」→ 客户端清库 → 丢数据。
     */
    val isInitialDevice: Boolean?,
    val courses: Int,
    val notes: Int,
    val deleted: Int
)

/** 待推送的一条本地变更 */
data class LocalChange(
    val entity: String,
    val uid: String,
    val data: JsonValue.Obj,
    val updatedAt: Long,
    val deletedAt: Long,
    val baseVersion: Int
)

/**
 * 云同步的 HTTP 接口。
 *
 * 与桌面端 `SyncApi` **逐字对齐**（只有 [HttpJson] 底层实现不同），
 * 所以协议变更时改一处、两端一起改。
 */
internal object SyncApi {

    fun pull(cursor: Int, limit: Int = 500, token: String): PullResult {
        val root = HttpJson.get("/sync/pull?cursor=$cursor&limit=$limit", token)
        val o = root as? JsonValue.Obj ?: throw ApiException("BAD_RESPONSE", "pull 响应不是对象", 0)
        return PullResult(
            cursor = o.int("cursor"),
            hasMore = o.bool("hasMore"),
            changes = o.array("changes").orEmpty().mapNotNull(::parseChange),
            replaceLocal = o.bool("replace_local")
        )
    }

    fun push(changes: List<LocalChange>, token: String): PushResult {
        if (changes.isEmpty()) {
            // 一条都没有时不发请求：省一次往返，也避免服务端把空数组当异常
            return PushResult(0, 0, emptyList(), 0, false)
        }
        val body = jsonObject(
            "changes" to jsonArray(
                changes.map {
                    jsonObject(
                        "entity" to it.entity.toJson(),
                        "uid" to it.uid.toJson(),
                        "data" to it.data,
                        "updatedAt" to it.updatedAt.toJson(),
                        "deletedAt" to it.deletedAt.toJson(),
                        "baseVersion" to it.baseVersion.toJson()
                    )
                }
            )
        )
        val root = HttpJson.post("/sync/push", body, token)
        val o = root as? JsonValue.Obj
            ?: throw ApiException("BAD_RESPONSE", "push 响应不是对象", 0)
        return PushResult(
            cursor = o.int("cursor"),
            applied = o.array("applied").orEmpty().size,
            conflicts = o.array("conflicts").orEmpty().mapNotNull(::parseChange),
            rejected = o.array("rejected").orEmpty().size,
            replaceLocal = o.bool("replace_local")
        )
    }

    fun status(token: String): SyncStatus {
        val root = HttpJson.get("/sync/status", token)
        val o = root as? JsonValue.Obj
            ?: throw ApiException("BAD_RESPONSE", "status 响应不是对象", 0)
        val counts = o.objOrNull("counts")
        return SyncStatus(
            cursor = o.int("cursor"),
            // ⚠️ 用 boolOrNull 保三态，别用 bool。
            // 字段名是蛇形 `is_initial_device`（实测响应确认）。
            isInitialDevice = o.boolOrNull("is_initial_device"),
            courses = counts?.int("classes") ?: 0,
            notes = counts?.int("notes") ?: 0,
            deleted = counts?.int("deleted") ?: 0
        )
    }

    /** 解析不出 uid 的变更直接丢掉 —— 没有 uid 无法落库 */
    private fun parseChange(raw: JsonValue): RemoteChange? {
        val o = raw as? JsonValue.Obj ?: return null
        val uid = o.str("uid")
        if (uid.isBlank()) return null
        return RemoteChange(
            entity = o.str("entity"),
            uid = uid,
            op = o.str("op"),
            data = o.objOrNull("data") ?: JsonValue.Obj(emptyMap()),
            version = o.int("version", 1)
        )
    }
}
