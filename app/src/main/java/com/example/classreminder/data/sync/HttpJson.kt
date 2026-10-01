package com.example.classreminder.data.sync

import com.example.classreminder.data.backup.JsonValue
import com.example.classreminder.data.backup.MiniJson
import com.example.classreminder.data.backup.bool
import com.example.classreminder.data.backup.objOrNull
import com.example.classreminder.data.backup.str
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/**
 * 服务端返回的错误 / 网络异常。
 *
 * [isNetwork] 用来区分「连不上」和「服务端明确拒绝」——
 * 前者显示「稍后会自动重试」，后者显示具体原因。
 * 这两类在UI 上的措辞完全不同，混在一起会让用户以为网络坏了。
 */
class ApiException(
    val code: String,
    override val message: String,
    val status: Int
) : Exception(message) {

    /**
     * 是否属于「网络层失败」。
     *
     * 401 不算网络问题：那是令牌过期/失效，服务端明确回应了，
     * 应当引导重新登录，而不是傻等重试。
     */
    val isNetwork: Boolean
        get() = status == 0
}

/**
 * 走 [HttpURLConnection] 的极简 JSON HTTP 客户端。
 *
 * ## 为什么不用 OkHttp / Retrofit
 *
 * 本项目**不引入三方库**（桌面端同理：HTTP 用 JDK 自带，JSON 用自研 MiniJson）。
 * `HttpURLConnection` 是 `java.net` 里的标准库，安卓完整支持，
 * 对我们这个「一次只发一个请求、不需要拦截器/连接池定制」的场景完全够用。
 *
 * 与桌面端 `HttpJson`（基于 JDK 17 `HttpClient`）**接口刻意对齐**：
 * 同样的 [get] / [post] / [BASE_URL]，只是底层实现不同。
 *这样协议层代码在两端几乎可以逐字复用，出问题也好对照排查。
 *
 * ⚠️ 调用方**必须**在后台线程调用（`Dispatchers.IO`）——
 * 安卓从 4.11 起会抛 `NetworkOnMainThreadException`。
 */
object HttpJson {

    const val BASE_URL = "https://deeer.online/api/stumate/v1"

    /** 单次请求超时（毫秒）。手机网络下不宜太短，但也不能让界面干等。 */
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 20_000

    fun get(path: String, token: String?): JsonValue =
        request("GET", path, null, token)

    fun post(path: String, body: JsonValue, token: String?): JsonValue =
        request("POST", path, MiniJson.write(body, pretty = false), token)

    /**
     * 发一次请求并解析响应。
     *
     * ⚠️ 响应字段**一律驼峰**（`hasMore` / `replaceLocal` / `dayOfWeek` …）。
     * 服务端三个 sync 路由全用驼峰，只有 `replace_local` 等少数是下划线——
     * 这是**实测确认过**的（曾在这里按 JSON 惯例写成 `has_more`，
     * 结果 `hasMore` 读成 false，超过 500 条时静默丢后半截数据）。
     * 改动前先看 `dev-verify-sync.mjs` 里的断言。
     */
    private fun request(
        method: String,
        path: String,
        body: String?,
        token: String?
    ): JsonValue {
        val url = URL(BASE_URL + path)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            // 云同步依赖服务端与设备的时钟一致性来做 LWW 判定，
            // 虽然协议本身用 updatedAt 而非服务端时间，但带上头便于排查问题
            setRequestProperty("Accept", "application/json")
            if (body != null) setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (body != null) {
                doOutput = true
                outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
        }

        try {
            val status = conn.responseCode
            if (status in 200..299) {
                val text = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
                    .use { it.readText() }
                if (text.isBlank()) return JsonValue.Obj(emptyMap())
                return MiniJson.parse(text)
            }
            // 错误响应在 errorStream，连接失败时它可能是 null
            val errText = conn.errorStream?.let {
                BufferedReader(InputStreamReader(it, Charsets.UTF_8)).use { r -> r.readText() }
            }.orEmpty()
            throw toApiException(status, errText)
        } catch (e: ApiException) {
            throw e
        } catch (e: Exception) {
            // 任何 IO / DNS / 超时都归为「网络层失败」，status=0 是标记
            throw ApiException("NETWORK", e.message ?: "连不上服务器", 0)
        } finally {
            conn.disconnect()
        }
    }

    /**
     * 把服务端的错误响应解析成 [ApiException]。
     *
     * 服务端错误格式是 `{"error":{"code":"RATE_LIMITED","message":"…"}}`，
     * 但**不能假设它一定符合格式**（500 页面、反代错误页都不是 JSON），
     * 所以解析失败要退回按状态码给一句人话。
     */
    private fun toApiException(status: Int, errText: String): ApiException {
        val fallback = when (status) {
            401 -> "登录已失效，请重新登录"
            403 -> "没有权限执行这个操作"
            404 -> "接口不存在"
            429 -> "操作太频繁，请稍后再试"
            in 500..599 -> "服务器开小差了"
            else -> "请求失败（$status）"
        }
        if (errText.isBlank()) return ApiException("HTTP_$status", fallback, status)
        return try {
            val root = MiniJson.parse(errText) as? JsonValue.Obj
            val err = root?.objOrNull("error")
            ApiException(
                code = err?.str("code") ?: "HTTP_$status",
                message = err?.str("message")?.takeIf { it.isNotBlank() } ?: fallback,
                status = status
            )
        } catch (e: Exception) {
            ApiException("HTTP_$status", fallback, status)
        }
    }
}
