package com.example.classreminder.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.classreminder.BuildConfig
import com.example.classreminder.data.backup.JsonValue
import com.example.classreminder.data.backup.MiniJson
import com.example.classreminder.data.backup.array
import com.example.classreminder.data.backup.long
import com.example.classreminder.data.backup.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * 基于 GitHub Releases 的更新检测与安装。**与桌面端同一套语义，代码各写一遍。**
 *
 * ## 为什么不需要内置 token
 * 仓库是 **public**，`api.github.com/repos/.../releases/latest` 匿名可读。
 * 把 PAT 打进 APK 等于把密码发出去 —— APK 是可以被反编译的，绝对不做。
 *
 * ## 安卓能做到哪一步
 * 安卓**不允许应用静默安装**。所以「自动更新」在这里的终点是：
 * 下载 APK → 校验 → 交给系统安装器（`ACTION_VIEW` + `application/vnd.android.package-archive`），
 * 用户在系统弹窗上点「安装」。这不是偷懒，是平台的硬约束。
 *
 * 另外从 Android 8 起，装未知来源应用需要用户**按应用**授权
 * （`canRequestPackageInstalls()`）；没授权时不硬闯，直接把用户送到那个设置页。
 */
object UpdateCenter {

    private const val LATEST_URL =
        "https://api.github.com/repos/deeery/ClassReminder/releases/latest"

    private const val USER_AGENT = "StuMate-Android-Updater"

    /**
     * 与 AndroidManifest 里 `<provider android:authorities="${applicationId}.fileprovider">`
     * 严格对应。
     *
     * **必须由 `packageName` 拼**，不能写死 `com.example.classreminder.fileprovider`：
     * debug 变体的 applicationId 是 `com.example.classreminder.test`，两个包同机共存，
     * 写死的话两个应用会声明同一个 authority，`getUriForFile` 落到哪个是不确定的。
     */
    private fun authority(ctx: Context): String = "${ctx.packageName}.fileprovider"

    val currentVersion: String get() = BuildConfig.VERSION_NAME

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    // ── 检查 ────────────────────────────────────────────────────────

    /**
     * @param manual 用户主动点的。`true` 时失败如实回执；`false`（启动静默检查）
     *   失败回到 [UpdateState.Idle]，不留错误痕 —— 没网不是用户该看到的报错。
     */
    suspend fun check(ctx: Context, manual: Boolean) {
        if (_state.value is UpdateState.Checking) return
        _state.value = UpdateState.Checking
        val result = runCatching { fetchLatest() }
        result.onFailure { t ->
            _state.value = if (manual) {
                UpdateState.Failed(t.message ?: t::class.java.simpleName)
            } else {
                UpdateState.Idle
            }
            return
        }
        val release = result.getOrThrow()
        if (compareVersions(release.version, currentVersion) <= 0) {
            _state.value = UpdateState.UpToDate(currentVersion)
            return
        }
        _state.value = UpdateState.Available(
            version = release.version,
            notes = release.notes,
            pageUrl = release.pageUrl,
            apk = release.apk,
            blocked = installBlocker(ctx)
        )
    }

    /** 能不能自己走安装流程。装不了时给出人话理由 */
    fun installBlocker(ctx: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !ctx.packageManager.canRequestPackageInstalls()
        ) {
            return "还没允许本应用安装未知来源的应用，点「去授权」开启后再试"
        }
        if (downloadDir(ctx) == null) return "拿不到可写目录，装不了"
        return null
    }

    /** 把用户送到「安装未知应用」授权页 */
    fun openInstallPermissionSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private data class Release(
        val version: String,
        val notes: String,
        val pageUrl: String,
        val apk: UpdateAsset?
    )

    private fun fetchLatest(): Release {
        val body = httpGet(LATEST_URL, accept = "application/vnd.github+json") { conn ->
            when (conn.responseCode) {
                200 -> Unit
                404 -> throw IllegalStateException("仓库还没有发布过任何版本")
                403 -> throw IllegalStateException("GitHub 限流了，过一会儿再试")
                else -> throw IllegalStateException("GitHub 返回 HTTP ${conn.responseCode}")
            }
        }.use { it.readBytes() }.toString(Charsets.UTF_8)

        val root = MiniJson.parse(body) as? JsonValue.Obj
            ?: throw IllegalStateException("GitHub 返回的不是 JSON 对象")
        val tag = root.str("tag_name")
        if (tag.isBlank()) throw IllegalStateException("Release 没有 tag_name")
        val version = normalizeVersion(tag)

        val apk = root.array("assets").mapNotNull { it as? JsonValue.Obj }
            .firstOrNull { it.str("name") == apkAssetName(version) }
            ?.let { asset ->
                val digest = asset.str("digest").takeIf { it.startsWith("sha256:") }
                    ?.removePrefix("sha256:")
                UpdateAsset(
                    name = asset.str("name"),
                    url = asset.str("browser_download_url"),
                    size = asset.long("size"),
                    sha256 = digest?.takeIf { it.isNotBlank() }
                )
            }

        return Release(
            version = version,
            notes = root.str("body").trim(),
            pageUrl = root.str("html_url"),
            apk = apk?.takeIf { it.url.isNotBlank() }
        )
    }

    // ── 下载 + 交给系统安装器 ────────────────────────────────────────

    suspend fun downloadAndInstall(ctx: Context) {
        val available = _state.value as? UpdateState.Available ?: return
        val apk = available.apk
        if (apk == null) {
            _state.value = UpdateState.NeedsFullPackage(
                available.version, "这个版本没有提供 APK", available.pageUrl
            )
            return
        }
        val blocker = installBlocker(ctx)
        if (blocker != null) {
            _state.value = UpdateState.NeedsFullPackage(available.version, blocker, available.pageUrl)
            return
        }

        _state.value = UpdateState.Downloading(0L, apk.size)
        val result = runCatching {
            withContext(Dispatchers.IO) { download(ctx, apk) { got, total ->
                _state.value = UpdateState.Downloading(got, total)
            } }
        }
        result.onSuccess { file ->
            _state.value = UpdateState.InstallerLaunched(available.version)
            launchInstaller(ctx, file)
        }.onFailure { t ->
            _state.value = UpdateState.Failed(t.message ?: t::class.java.simpleName)
        }
    }

    private fun download(ctx: Context, apk: UpdateAsset, onProgress: (Long, Long) -> Unit): File {
        val dir = downloadDir(ctx) ?: throw IllegalStateException("拿不到可写目录")
        dir.mkdirs()
        val target = File(dir, apk.name)

        httpGet(apk.url, accept = "application/octet-stream") { conn ->
            if (conn.responseCode != 200) {
                throw IllegalStateException("下载 APK 失败：HTTP ${conn.responseCode}")
            }
            val declared = conn.contentLengthLong.takeIf { it > 0 } ?: apk.size
            conn.inputStream.use { input ->
                target.outputStream().use { out ->
                    val buf = ByteArray(1 shl 16)
                    var got = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        got += n
                        onProgress(got, declared)
                    }
                }
            }
        }

        // 校验。GitHub 新版 API 会带 digest；老 Release 没有就跳过（不因为
        // 少一层校验而让用户装不上）。另外永远检查一下 APK 的 zip 头 ——
        // 下到半截的 HTML 错误页也「是个文件」，但装不了。
        apk.sha256?.let { expected ->
            val actual = sha256Of(target)
            if (!actual.equals(expected, ignoreCase = true)) {
                target.delete()
                throw IllegalStateException("APK 校验失败（sha256 对不上）")
            }
        }
        if (!looksLikeZip(target)) {
            target.delete()
            throw IllegalStateException("下载到的不是 APK（可能被网络拦截页替换了）")
        }
        return target
    }

    private fun launchInstaller(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, authority(ctx), file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(intent)
    }

    fun openReleasePage(ctx: Context, url: String) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /**
     * 下载落点用**应用私有**的外部目录，不是公共 Download。
     *
     * 两个原因：① 从 Android 10 起写公共目录要走 MediaStore，多一层没必要；
     * ② FileProvider 配的是私有目录，公共目录的文件授不出 uri 权限。
     */
    private fun downloadDir(ctx: Context): File? =
        ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(ctx.filesDir, "downloads")

    // ── 网络 ────────────────────────────────────────────────────────

    /**
     * 项目规定不引三方网络库，所以用 [HttpURLConnection]。
     *
     * ⚠️ `HttpURLConnection` 默认**跟随同协议重定向**。GitHub 的
     * `browser_download_url` 会 302 到 `objects.githubusercontent.com`（同为 https），
     * 默认行为正好够用；显式写出来是为了将来有人把 URL 换成 http 时能看见这条依赖。
     */
    private fun httpGet(
        url: String,
        accept: String,
        check: (HttpURLConnection) -> Unit
    ): java.io.InputStream {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            check(conn)
        } catch (t: Throwable) {
            conn.disconnect()
            throw t
        }
        return conn.inputStream
    }

    private fun looksLikeZip(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(4)
                if (input.read(head) != 4) return@use false
                // APK 是 zip：本地文件头 50 4B 03 04（空 zip 是 50 4B 05 06）
                head[0] == 0x50.toByte() && head[1] == 0x4B.toByte()
            }
        }.getOrDefault(false)
    }

    private fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    // ── 纯函数（与桌面端同规则，可单测） ─────────────────────────────

    /** `StuMate-1.6-release.apk` —— 与 `dist/` 里的历史命名保持一致 */
    internal fun apkAssetName(version: String): String = "StuMate-$version-release.apk"

    internal fun normalizeVersion(raw: String): String =
        raw.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')

    /** a > b 返回 1，a < b 返回 -1，相等 0。缺失的段按 0 算（`1.6` == `1.6.0`） */
    internal fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return if (x > y) 1 else -1
        }
        return 0
    }

    private fun parseVersion(v: String): List<Int> =
        normalizeVersion(v).split('.').map { it.toIntOrNull() ?: 0 }
}
