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

    private fun available(apk: UpdateAsset?, blocked: String?) = UpdateState.Available(
        version = "1.5",
        notes = "",
        pageUrl = "https://github.com/deeery/ClassReminder/releases/tag/v1.5",
        apk = apk,
        blocked = blocked
    )
}
