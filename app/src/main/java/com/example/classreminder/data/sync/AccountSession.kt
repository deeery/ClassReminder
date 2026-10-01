package com.example.classreminder.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.example.classreminder.data.backup.JsonValue
import com.example.classreminder.data.backup.MiniJson
import com.example.classreminder.data.backup.array
import com.example.classreminder.data.backup.bool
import com.example.classreminder.data.backup.int
import com.example.classreminder.data.backup.jsonObject
import com.example.classreminder.data.backup.long
import com.example.classreminder.data.backup.objOrNull
import com.example.classreminder.data.backup.toJson
import com.example.classreminder.data.backup.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 登录用户。字段与桌面端 [AuthUser] 对齐。
 */
data class AuthUser(
    val id: Int,
    val email: String,
    val nickname: String = "",
    val hasPassword: Boolean = true
)

/** 一对令牌。 */
data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val accessExpiresAt: String
)

/**
 * 账号会话：令牌的存取、刷新、登录注册登出。
 *
 * ## 与桌面端的差异
 *
 * 桌面端把令牌快照写在一个文件里；安卓端用 [SharedPreferences] ——
 * 这是系统为这种场景提供的标准位置，且能自动参与应用备份。
 * 令牌**不会**进Room 数据库（那是业务数据），也不会进日志。
 *
 * ## 令牌刷新的两条铁律
 *
 * 1. **先落盘再返回**。顺序反了 = 下次带着已轮转的 refresh 去请求 = 重放检测
 *    = 整台设备被踢下线。
 * 2. **锁内双重检查**。[refreshMutex] 保护轮转；`authed` 在多个协程里并发跑时，
 *    第一个刷完后，后面的必须拿**新**令牌，而不是各自拿旧 refresh 去撞。
 */
class AccountSession(private val context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("stumate_account", Context.MODE_PRIVATE)

    private val _user = MutableStateFlow<AuthUser?>(null)
    val user: StateFlow<AuthUser?> = _user.asStateFlow()

    private val refreshMutex = Mutex()

    /** 提前这么多秒就认为access 过期了，避免边界抖动 */
    private val refreshSkewSeconds = 60L

    /** 同步引擎只需要这一个入口 */
    fun token(): String? = currentTokens()?.accessToken

    /** 是否已登录。UI 用它决定显示「登录」还是「同步卡」。 */
    fun isSignedIn(): Boolean = currentTokens() != null

    // ── 登录 / 注册 / 登出 ───────────────────────────────────────

    /**
     * 设备标识。
     *
     * 服务端按 `(account, device)` 记一条登录设备，账号最多 5 台；
     * 不传的话每次登录都被当成新设备，很快就把名额占满了。
     * `name` 用系统型号，用户在「我的设备」里能认出「这是我的手机」而不是「未知设备」。
     */
    private fun deviceJson(): JsonValue.Obj = jsonObject(
        "name" to (deviceName().toJson()),
        "platform" to "android".toJson()
    )

    private fun deviceName(): String {
        val manufacturer = runCatching {
            android.os.Build.MANUFACTURER
        }.getOrDefault("")
        val model = runCatching { android.os.Build.MODEL }.getOrDefault("")
        val joined = listOf(manufacturer, model)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .trim()
        return joined.ifBlank { "Android 设备" }
    }

    suspend fun login(email: String, password: String): AuthUser =
        withContext(Dispatchers.IO) {
            val body = jsonObject(
                "email" to email.toJson(),
                "password" to password.toJson(),
                "device" to deviceJson()
            )
            val root = HttpJson.post("/auth/login", body, null)
            handleAuthResponse(root)
        }

    suspend fun register(email: String, password: String, inviteCode: String): AuthUser =
        withContext(Dispatchers.IO) {
            val body = jsonObject(
                "email" to email.toJson(),
                "password" to password.toJson(),
                "invite_code" to inviteCode.toJson(),
                "device" to deviceJson()
            )
            val root = HttpJson.post("/auth/register", body, null)
            handleAuthResponse(root)
        }

    /**
     * 登出。
     *
     * ⚠️ **先清本地、再通知服务端**。反过来写的话，若网络请求卡住/失败，
     * 用户点了退出登录却还是停留在已登录界面，而且令牌还在本地躺着。
     * 服务端那一步只是「让 refresh 失效」，失败也无所谓 ——
     * 用户这边的意图是「我不再用这个账号」，必须立刻生效。
     */
    suspend fun logout() {
        val refresh = currentTokens()?.refreshToken
        signOutLocally()
        // 已经清完本地了，这里失败绝不能往上传 —— 令牌传出去也没有对应状态可清了。
        // try/catch 而不是 runCatching{}?.let：语义上就是「尽力通知，失败算了」。
        if (!refresh.isNullOrBlank()) {
            try {
                withContext(Dispatchers.IO) {
                    HttpJson.post("/auth/logout", jsonObject("refresh_token" to refresh.toJson()), null)
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    // ── 内部 ──────────────────────────────────────────────────────

    /**
     * 解析登录/注册的响应并存盘。
     *
     * ⚠️ **这里的字段是蛇形**（`access_token` / `has_password` …），
     * 而三个 sync 路由的字段是**驼峰**（`hasMore` / `replaceLocal`）。
     * 服务端两套风格并存，写成驼峰会静默读出空串 ——
     * 登录看着成功，令牌却是空的，之后每次同步都401，
     * 症状是「明明登录了却一直提示未登录」，极难自查。
     * 已在 2026-10-02 对着真实服务端响应核对过。
     *
     * 服务端返回 `{user:{…}, tokens:{access_token,access_expires_at,refresh_token,refresh_expires_at}}`。
     */
    private fun handleAuthResponse(root: JsonValue): AuthUser {
        val o = root as? JsonValue.Obj
            ?: throw ApiException("BAD_RESPONSE", "登录响应不是对象", 0)
        val userObj = o.objOrNull("user")
            ?: throw ApiException("BAD_RESPONSE", "登录响应缺 user", 0)
        val tokensObj = o.objOrNull("tokens")
            ?: throw ApiException("BAD_RESPONSE", "登录响应缺 tokens", 0)

        val access = tokensObj.str("access_token")
        if (access.isBlank()) {
            throw ApiException("BAD_RESPONSE", "登录响应里没有 access_token", 0)
        }

        val user = AuthUser(
            id = userObj.int("id"),
            email = userObj.str("email"),
            nickname = userObj.str("nickname"),
            hasPassword = userObj.bool("has_password", true)
        )
        val tokens = AuthTokens(
            accessToken = access,
            refreshToken = tokensObj.str("refresh_token"),
            accessExpiresAt = tokensObj.str("access_expires_at")
        )
        persist(user, tokens)
        return user
    }

    /**
     * 拿一个「保证可用」的 access token，必要时先刷新。
     *
     * @throws ApiException 401 未登录
     */
    suspend fun freshAccessToken(): String {
        val current = currentTokens() ?: throw ApiException("UNAUTHORIZED", "尚未登录", 401)
        if (!needsRefresh(current.accessExpiresAt)) return current.accessToken
        return rotateRefresh()
    }

    /** 轮转 refresh。**整个应用里唯一一处调 `/auth/refresh` 的地方。** */
    private suspend fun rotateRefresh(): String = refreshMutex.withLock {
        val current = currentTokens() ?: throw ApiException("UNAUTHORIZED", "尚未登录", 401)
        // 等锁期间可能已经被别的协程刷过了，直接用新的
        if (!needsRefresh(current.accessExpiresAt)) return@withLock current.accessToken

        try {
            // ⚠️ 请求体字段是 `refresh_token`（蛇形）。写成驼峰服务端直接判 401。
            val body = jsonObject("refresh_token" to current.refreshToken.toJson())
            val root = withContext(Dispatchers.IO) { HttpJson.post("/auth/refresh", body, null) }
            val o = root as? JsonValue.Obj
                ?: throw ApiException("BAD_RESPONSE", "刷新响应不是对象", 0)
            // ⚠️ 新令牌在 `tokens` **子对象**里，不是顶层。读顶层会拿到三个空串，
            // 然后把空令牌写进本地 —— 下一次请求必然 401，而且用户已经登不回来了。
            val t = o.objOrNull("tokens")
                ?: throw ApiException("BAD_RESPONSE", "刷新响应缺 tokens", 0)
            val next = AuthTokens(
                accessToken = t.str("access_token"),
                refreshToken = t.str("refresh_token"),
                accessExpiresAt = t.str("access_expires_at")
            )
            if (next.accessToken.isBlank()) {
                throw ApiException("BAD_RESPONSE", "刷新响应里没有 access_token", 0)
            }
            // ⚠️ 先落盘再返回。顺序反了 = 下次重放 = 整台设备掉线
            persistTokens(next)
            next.accessToken
        } catch (e: ApiException) {
            if (e.status == 401) signOutLocally()
            throw e
        }
    }

    /**
     * 带令牌执行一次调用；遇 401 强刷一次重试。
     *
     * access 可能被别处吊销（用户在另一台设备登出、或服务端主动失效），
     * 所以拿到 401 要先强刷再试一次；再失败才抛出。
     */
    suspend fun <T> authed(block: suspend (String) -> T): T = withContext(Dispatchers.IO) {
        val access = freshAccessToken()
        try {
            block(access)
        } catch (e: ApiException) {
            if (e.status != 401) throw e
            block(rotateRefresh())
        }
    }

    private fun needsRefresh(expiresAt: String): Boolean {
        // 安卓 API 21 起有 java.time（需 desugaring，见 build.gradle.kts），
        // 但保险起见解析失败就当作「需要刷新」—— 多刷一次只是慢一点，
        // 解析成功却判断错误会导致 401 后才刷新，体验更差。
        return runCatching {
            val instant = java.time.Instant.parse(expiresAt)
            instant.isBefore(java.time.Instant.now().plusSeconds(refreshSkewSeconds))
        }.getOrDefault(true)
    }

    // ── 落盘 ──────────────────────────────────────────────────────

    private fun persist(user: AuthUser, tokens: AuthTokens) {
        persistTokens(tokens)
        _user.value = user
        prefs.edit()
            .putString(KEY_USER, encodeUser(user))
            .apply()
    }

    private fun persistTokens(tokens: AuthTokens) {
        prefs.edit()
            .putString(KEY_ACCESS, tokens.accessToken)
            .putString(KEY_REFRESH, tokens.refreshToken)
            .putString(KEY_EXPIRES, tokens.accessExpiresAt)
            .apply()
    }

    private fun currentTokens(): AuthTokens? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val expires = prefs.getString(KEY_EXPIRES, null) ?: return null
        return AuthTokens(access, refresh, expires)
    }

    private fun signOutLocally() {
        prefs.edit()
            .remove(KEY_ACCESS)
            .remove(KEY_REFRESH)
            .remove(KEY_EXPIRES)
            .remove(KEY_USER)
            .apply()
        _user.value = null
    }

    /**
     * 应用启动时调用：把落盘的用户读回内存。
     *
     * 放在 [user] 的 StateFlow 上是为了让界面一进来就知道「登录着没有」，
     * 不然会先闪一下「未登录」再变成已登录。
     */
    fun restore() {
        _user.value = prefs.getString(KEY_USER, null)?.let { runCatching { decodeUser(it) }.getOrNull() }
    }

    private fun encodeUser(user: AuthUser): String = MiniJson.write(
        jsonObject(
            "id" to user.id.toJson(),
            "email" to user.email.toJson(),
            "nickname" to user.nickname.toJson(),
            "hasPassword" to user.hasPassword.toJson()
        ),
        pretty = false
    )

    /**
     * 从本地快照恢复用户。
     *
     * ⚠️ 这里的字段是**驼峰** —— 它读的是本文件 [encodeUser] 写出去的本地快照，
     * 不是服务端响应。两套命名各自成立（本地驼峰 / 服务端蛇形），
     * 搞混的后果是「明明登录过，重启后变成未登录」。
     */
    private fun decodeUser(text: String): AuthUser {
        val o = MiniJson.parse(text) as JsonValue.Obj
        return AuthUser(
            id = o.int("id"),
            email = o.str("email"),
            nickname = o.str("nickname"),
            hasPassword = o.bool("hasPassword", true)
        )
    }

    private companion object {
        const val KEY_ACCESS = "access_token"
        const val KEY_REFRESH = "refresh_token"
        const val KEY_EXPIRES = "access_expires_at"
        const val KEY_USER = "user_json"
    }
}
