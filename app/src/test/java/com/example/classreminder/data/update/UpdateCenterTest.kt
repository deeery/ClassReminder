package com.example.classreminder.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 安卓端更新逻辑的纯函数测试。**与桌面端 `UpdateCenterTest` 一一对应**。
 *
 * 两端不共享代码，所以「同一个规则写两遍」是既定事实；能防住两边跑偏的
 * 只有「两边各钉一套同样名字、同样断言的测试」。下面这些用例在桌面端都能找到同名的。
 *
 * 网络与安装流程不在这里测 —— 它们要 Context / 系统安装器，属于实机验收范围。
 */
class UpdateCenterTest {

    // ── 版本号归一化 ────────────────────────────────────────────

    @Test
    fun `tag 的 v 前缀会被去掉`() {
        assertEquals("1.5.0", UpdateCenter.normalizeVersion("v1.5.0"))
        assertEquals("1.5.0", UpdateCenter.normalizeVersion("V1.5.0"))
        assertEquals("1.5.0", UpdateCenter.normalizeVersion("1.5.0"))
    }

    @Test
    fun `预发布后缀会被切掉`() {
        // GitHub 上打 tag 很容易带上 -beta / +build，版本比较不能因此误判
        assertEquals("1.5.0", UpdateCenter.normalizeVersion("v1.5.0-beta.1"))
        assertEquals("1.5.0", UpdateCenter.normalizeVersion("1.5.0+build7"))
    }

    @Test
    fun `前后空白会被吃掉`() {
        assertEquals("1.5.0", UpdateCenter.normalizeVersion("  v1.5.0  "))
    }

    // ── 版本比较 ────────────────────────────────────────────────

    @Test
    fun `逐段比较而不是按字符串比`() {
        // 这是最容易踩的坑：字符串比较下 "1.10.0" < "1.9.0"，用户永远收不到更新
        assertTrue(UpdateCenter.compareVersions("1.10.0", "1.9.0") > 0)
        assertTrue(UpdateCenter.compareVersions("1.9.0", "1.10.0") < 0)
        assertTrue(UpdateCenter.compareVersions("2.0.0", "1.99.99") > 0)
    }

    @Test
    fun `段数不同时缺的段按 0 算`() {
        assertEquals(0, UpdateCenter.compareVersions("1.6", "1.6.0"))
        assertEquals(0, UpdateCenter.compareVersions("1.6.0", "1.6"))
        assertTrue(UpdateCenter.compareVersions("1.6.1", "1.6") > 0)
        assertTrue(UpdateCenter.compareVersions("1.5.9", "1.6") < 0)
    }

    @Test
    fun `相等返回 0`() {
        assertEquals(0, UpdateCenter.compareVersions("1.5.0", "1.5.0"))
        assertEquals(0, UpdateCenter.compareVersions("v1.5.0", "1.5.0"))
    }

    @Test
    fun `解析不出来的段当 0，不抛异常`() {
        // 远端 tag 写成 "v1.x" 这种鬼东西时，宁可判成「1.0」也不能让整个检查崩掉
        assertEquals(0, UpdateCenter.compareVersions("1.x.0", "1.0.0"))
        assertEquals(0, UpdateCenter.compareVersions("abc", "0.0.0"))
    }

    // ── 资产命名 ────────────────────────────────────────────────

    @Test
    fun `APK 资产名与 dist 目录的历史命名一致`() {
        assertEquals("StuMate-1.5-release.apk", UpdateCenter.apkAssetName("1.5"))
        assertEquals("StuMate-1.4-release.apk", UpdateCenter.apkAssetName("1.4"))
    }

    // ── 状态机 ──────────────────────────────────────────────────

    @Test
    fun `有 APK 且没被拦时才能自己装`() {
        val apk = UpdateAsset("StuMate-1.5-release.apk", "https://example.com/a.apk", 100L, null)
        assertTrue(available(apk = apk, blocked = null).canSelfInstall)
        assertFalse(available(apk = apk, blocked = "没权限").canSelfInstall)
        assertFalse(available(apk = null, blocked = null).canSelfInstall)
    }

    @Test
    fun `下载进度在总长度未知时返回 -1 而不是除零`() {
        assertEquals(-1, UpdateState.Downloading(100L, 0L).percent)
        assertEquals(-1, UpdateState.Downloading(100L, -1L).percent)
    }

    @Test
    fun `下载进度取整到百分数`() {
        assertEquals(0, UpdateState.Downloading(0L, 200L).percent)
        assertEquals(50, UpdateState.Downloading(100L, 200L).percent)
        assertEquals(100, UpdateState.Downloading(200L, 200L).percent)
    }

    // ── 失败原因的人话化 ────────────────────────────────────────
    //
    // 背景：`UpdateCenter.check()` 里网络调用是阻塞的（项目不引三方库，
    // 用 HttpURLConnection），曾经**靠调用方**包 `withContext(Dispatchers.IO)`，
    // 结果启动那条路径包了、用户手点的三条漏了 ——
    // `rememberCoroutineScope()` 跑在 main dispatcher 上，
    // 于是主线程发 HTTP，直接 `NetworkOnMainThreadException`。
    //
    // 现在改成**本类自持线程**：公开的 suspend 函数自己
    // `withContext(Dispatchers.IO)`，内部实现叫 `xxxOnIo`。
    // 调用方无论从哪个 dispatcher 调都安全。
    //
    // ⚠️ 这条约束**没有单测能自动守住** —— 它要真机才能复现
    // （单测跑在 JVM 上没有 Android 的 StrictMode 主线程网络检查）。
    // 所以写在注释里，改代码时看见别把它删掉。
    // 实机验收：点「设置 → 关于 → 检查更新」，不应该崩。

    @Test
    fun `域名解析失败给出人话提示而不是英文异常`() {
        val msg = UpdateCenter.describe(java.net.UnknownHostException("api.github.com"))
        assertEquals("网络不可用，检查一下连接", msg)
    }

    @Test
    fun `连接超时给出人话提示`() {
        val msg = UpdateCenter.describe(java.net.SocketTimeoutException("connect timed out"))
        assertEquals("连接超时，稍后再试", msg)
    }

    @Test
    fun `其它异常保留原文，不吞掉信息`() {
        val msg = UpdateCenter.describe(IllegalStateException("GitHub 限流了，过一会儿再试"))
        assertEquals("GitHub 限流了，过一会儿再试", msg)
    }

    // ── 依赖版本失配的守门（防 LinearProgressIndicator 崩溃复发） ──────
    //
    // 背景：本项目 material3 是 **1.1.2**（它针对 compose-animation **1.4.1** 编译），
    // 但 BOM 2024.01.00 把 animation-core 解析到了 **1.6.0**。
    // 1.4.0 起 `KeyframesSpecConfig.at()` / `atFraction()` / `using()` 被
    // **上移**到新父类 `KeyframesSpecBaseConfig`；1.6.0 的
    // `KeyframesSpecConfig` 里**只剩** `createEntityFor` 和废弃的 `with`
    // （已用 `javap` + 上游 sources 双向核实）。
    //
    // 于是 material3 里那条 `LinearProgressIndicator` 一渲染就抛
    // `NoSuchMethodError: No virtual method at(...)`。
    // 「检查更新」进入 `Checking` 状态 → 正好渲染它 → **必崩**。
    //
    // 这个错误**编译期完全绿灯**（校验的是 1.4.1 的签名），只在运行时炸；
    // 又因为 `NoSuchMethodError` 继承自 `Error` 而非 `Exception`，
    // `runCatching` / `catch (e: Exception)` **都接不住**，只能靠**不调用**来规避。
    //
    // 所以这条约束无法用「调用 API 再断言行为」来测，只能做**源码扫描**式守门：
    // 一旦有人再把 `LinearProgressIndicator` / `CircularProgressIndicator` 写回来，
    // 这里就会红 —— 比等到用户点「检查更新」时崩溃要好得多。
    //
    // 现有正确做法见 `MainScreen.kt` 的 `updateProgressBar()`（自绘）与
    // `AccountSyncSection.kt`（用文字态代替圆形指示器）。

    @Test
    fun `UI 源码里不得使用 material3 的进度指示器`() {
        val uiDir = java.io.File("src/main/java/com/example/classreminder/ui")
        assertTrue("找不到 UI 源码目录: ${uiDir.absolutePath}", uiDir.isDirectory)

        // 只扫「代码」，注释行不算 —— 上面那些说明性注释里就带着这两个类名。
        val offenders = mutableListOf<String>()
        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { i, raw ->
                    val line = raw.trim()
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                    if (line.contains("LinearProgressIndicator(") || line.contains("CircularProgressIndicator(")) {
                        offenders += "${f.name}:${i + 1}  $line"
                    }
                }
            }

        assertTrue(
            "material3 1.1.2 与 animation-core 1.6.0 的 KeyframesSpecConfig.at() 签名错配，" +
                "这两个组件一渲染就抛 NoSuchMethodError（Error 级，catch 不住）。" +
                "请改用自绘进度条（见 MainScreen.kt 的 updateProgressBar）。命中：\n" +
                offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `UI 源码里不得显式使用 keyframes 动画规格`() {
        val uiDir = java.io.File("src/main/java/com/example/classreminder/ui")
        assertTrue("找不到 UI 源码目录: ${uiDir.absolutePath}", uiDir.isDirectory)

        val offenders = mutableListOf<String>()
        uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                f.readLines().forEachIndexed { i, raw ->
                    val line = raw.trim()
                    if (line.startsWith("//") || line.startsWith("*") || line.startsWith("/*")) return@forEachIndexed
                    if (line.contains("keyframes {") || line.contains("keyframes(")) {
                        offenders += "${f.name}:${i + 1}  $line"
                    }
                }
            }

        assertTrue(
            "keyframes 在 animation-core 1.4→1.6 之间把 at()/atFraction()/using() 上移到了 " +
                "KeyframesSpecBaseConfig，直接调用会 NoSuchMethodError。" +
                "需要的话改用 tween/spring 组合。命中：\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    private fun available(apk: UpdateAsset?, blocked: String?) = UpdateState.Available(
        version = "1.5",
        notes = "",
        pageUrl = "https://github.com/deeery/ClassReminder/releases/tag/v1.5",
        apk = apk,
        blocked = blocked
    )
}
