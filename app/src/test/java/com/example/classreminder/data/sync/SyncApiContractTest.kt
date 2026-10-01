package com.example.classreminder.data.sync

import com.example.classreminder.data.backup.JsonValue
import com.example.classreminder.data.backup.MiniJson
import com.example.classreminder.data.backup.array
import com.example.classreminder.data.backup.bool
import com.example.classreminder.data.backup.boolOrNull
import com.example.classreminder.data.backup.int
import com.example.classreminder.data.backup.long
import com.example.classreminder.data.backup.objOrNull
import com.example.classreminder.data.backup.str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 协议契约测试：**拿真实服务端响应**喂给解析函数，逐个字段断言。
 *
 * ## 为什么必须写这个测试
 *
 * 服务端 `/auth` 系列路由是**蛇形**（`access_token` / `has_password` / `invite_code`），
 * 而 `/sync` 系列是**驼峰**（`hasMore` / `replaceLocal`）。两套风格并存，
 * 写错不会报错、只会**静默读出空串或false**：
 *
 *  - 令牌读成驼峰 → 登录「成功」但本地令牌是空的 → 之后每次同步都 401，
 *    症状是「明明登录了却一直提示未登录」
 *  - `hasMore` 读成蛇形 → 永远读到 false → 超过 500 条时**静默丢掉后半截数据**
 *
 * 这两类都是「不崩溃、只是结果错」，靠肉眼看代码发现不了。
 * 本测试里的 JSON 全部是从 `https://deeer.online` 实抓的响应，
 * 任何一方改了字段名，这里立刻红。
 *
 * 数据来源（2026-10-02 实测）：
 *  - POST /api/stumate/v1/auth/login
 *  - GET  /api/stumate/v1/sync/status
 */
class SyncApiContractTest {

    // ── /auth/login 的真实响应 ──────────────────────────────────────
    //
    // 注意 tokens 里**没有** `refreshExpiresAt` 之外的驼峰字段，
    // 顶层也没有散落的 token —— 全在 `tokens` 子对象里，且是蛇形。
    private val realLoginResponse = """
        {
          "user": {
            "id": 17,
            "email": "stumate-smoke-a@verify.local",
            "nickname": "",
            "created_at": "2026-10-01T06:46:52.480Z",
            "has_password": true
          },
          "tokens": {
            "access_token": "f69b58f9b97fdb36d796643123e6146987364580e435c3a83a808f3a74ef8f53",
            "access_expires_at": "2026-10-01T16:18:47.284Z",
            "refresh_token": "4c193d4581ecb71e915c27a0e11cf6772d888908564d4574d7dcfad01745a0cc",
            "refresh_expires_at": "2026-10-31T16:03:47.284Z"
          }
        }
    """.trimIndent()

    /**
     * 令牌解析。抽成internal 函数是为了能脱离 Room / Context 纯测 ——
     * `AccountSession` 本身要SharedPreferences，测不了。
     */
    @Test
    fun `登录响应的令牌字段是蛇形`() {
        val o = MiniJson.parse(realLoginResponse) as JsonValue.Obj
        val tokens = o.objOrNull("tokens")!!

        val access = tokens.str("access_token")
        val refresh = tokens.str("refresh_token")
        val expires = tokens.str("access_expires_at")

        assertTrue("access_token 应有 64 个十六进制字符", access.length == 64)
        assertTrue("refresh_token 应有 64 个十六进制字符", refresh.length == 64)
        assertTrue("access_expires_at 应是 ISO 时间戳", expires.endsWith("Z"))

        // 反向断言：驼峰写法必须读不到东西。
        // 这一条才是本测试的价值 —— 防止有人「顺手改成驼峰」而测试仍然通过。
        assertEquals("", tokens.str("accessToken"))
        assertEquals("", tokens.str("accessExpiresAt"))
    }

    @Test
    fun `登录响应的用户字段是蛇形`() {
        val o = MiniJson.parse(realLoginResponse) as JsonValue.Obj
        val user = o.objOrNull("user")!!

        assertEquals(17, user.int("id"))
        assertEquals("stumate-smoke-a@verify.local", user.str("email"))
        assertTrue(user.bool("has_password", false))
        // 驼峰写法读不到 —— 服务端根本没这个字段
        assertEquals("", user.str("hasPassword"))
    }

    // ── /sync/pull 的字段是驼峰 ──────────────────────────────────────
    //
    // 这里和 auth 相反：`hasMore` 是驼峰、`replace_local` 是蛇形。
    // 同一个响应里两种风格混用，是最容易写错的地方。
    //
    // ⚠️ 下面这段是2026-10-02 从 `GET /sync/pull?cursor=0&limit=1`
    // 实抓的原文（只删了另外 29 条 change）。
    // 注意 `dayOfWeek` 的值是 **"Monday"** 而不是「周一」——
    // 两端数据库存的都是英文星期名（靠字典序恰好等于时间序），
    // 写成中文会渲染不出课程。

    private val pullResponseHasMore = """
        {
          "cursor": 30,
          "hasMore": true,
          "changes": [
            {
              "entity": "class",
              "uid": "b2f89eeb-c6aa-4261-8785-e2965422e461",
              "op": "upsert",
              "data": {
                "uid": "b2f89eeb-c6aa-4261-8785-e2965422e461",
                "id": 1,
                "title": "高等数学",
                "dayOfWeek": "Monday",
                "startTime": "08:00",
                "endTime": "09:35",
                "room": "教二 305",
                "notes": "",
                "teacher": "王海燕",
                "weeks": "1-16周",
                "date": "",
                "updatedAt": 1790837406681,
                "deletedAt": 0
              },
              "updated_at": "2026-10-01T06:50:06.681Z",
              "deleted_at": null
            }
          ],
          "replace_local": true
        }
    """.trimIndent()

    @Test
    fun `pull 的 hasMore 是驼峰而 replace_local 是蛇形`() {
        val o = MiniJson.parse(pullResponseHasMore) as JsonValue.Obj

        assertEquals(30, o.int("cursor"))
        // ⚠️ 这一条是整个同步最容易错的地方：读成 has_more 会永远得到 false，
        // 于是超过 500 条时静默丢掉后半截数据。
        assertTrue("hasMore 必须读到 true", o.bool("hasMore"))
        assertFalse("has_more 在服务端不存在，必须读到 false", o.bool("has_more"))
        // 同一个响应里 replace_local 反而是蛇形 —— 且实测就是 true
        assertTrue(o.bool("replace_local"))
        assertFalse("replaceLocal 在服务端不存在", o.bool("replaceLocal"))
    }

    @Test
    fun `pull 的变更条目字段`() {
        val o = MiniJson.parse(pullResponseHasMore) as JsonValue.Obj
        val changes = o.array("changes")!!
        assertEquals(1, changes.size)

        val c = changes[0] as JsonValue.Obj
        assertEquals("class", c.str("entity"))
        assertEquals("b2f89eeb-c6aa-4261-8785-e2965422e461", c.str("uid"))
        assertEquals("upsert", c.str("op"))

        // data 里的记录字段是驼峰（与本地备份格式一致）
        val data = c.objOrNull("data")!!
        assertEquals(1, data.int("id"))
        assertEquals("高等数学", data.str("title"))
        // ⚠️ 英文星期名。写成「周一」的话课表会渲染不出课程，
        // 而且两端都按字典序排序，中文会排到 Monday 之后 —— 顺序全乱。
        assertEquals("Monday", data.str("dayOfWeek"))
        assertEquals("08:00", data.str("startTime"))
        assertEquals("09:35", data.str("endTime"))
        assertEquals("教二 305", data.str("room"))
        assertEquals("王海燕", data.str("teacher"))
        assertEquals("1-16周", data.str("weeks"))
        assertEquals(1790837406681L, data.long("updatedAt"))
        assertEquals(0L, data.long("deletedAt"))

        // 变更外层的元数据是蛇形（updated_at / deleted_at）。
        // ⚠️ deleted_at 在未删除的记录上是**显式 null**（不是 0、也不是缺字段）——
        //   所以不能直接 long()，得先判null 再给默认值。
        assertEquals("2026-10-01T06:50:06.681Z", c.str("updated_at"))
        assertEquals(JsonValue.Null, c.fields["deleted_at"])
    }

    // ── /sync/status ────────────────────────────────────────────────

    @Test
    fun `status 响应只有三项且 is_initial_device 是三态`() {
        // 2026-10-02 实测原文（该账号首端是别的设备，所以是 false）
        val o = MiniJson.parse(
            """
            {"cursor":62,"is_initial_device":false,"counts":{"classes":23,"notes":0,"deleted":5}}
            """
        ) as JsonValue.Obj

        // ⚠️ 只有这三个键 —— 没有 initial_device_id，也没有任何客户端游标。
        assertEquals(setOf("cursor", "is_initial_device", "counts"), o.fields.keys)

        assertEquals(62, o.int("cursor"))
        val counts = o.objOrNull("counts")!!
        assertEquals(23, counts.int("classes"))
        assertEquals(0, counts.int("notes"))
        assertEquals(5, counts.int("deleted"))

        assertFalse(o.bool("is_initial_device"))
        // 驼峰写法读不到 —— 服务端没这个字段。
        // 曾经真的踩过：客户端按驼峰读is_initial_device → 恒为 false → 恒清库。
        assertFalse(o.bool("isInitialDevice"))
    }

    @Test
    fun `is_initial_device 为 null 时不能当成false`() {
        // 「还没任何设备认领过首端」时服务端返回 null。
        // 这三态必须区分，客户端判断要写 == true。
        //
        // 顺带把 boolOrNull 的行为钉死：
        // bool() 在这里是 false，boolOrNull() 是 null —— 差别就是「会不会误清库」。
        for (raw in listOf("null", "false", "true")) {
            val o = MiniJson.parse("""{"is_initial_device":$raw}""") as JsonValue.Obj
            val isTrue = o.boolOrNull("is_initial_device") == true
            val expected = raw == "true"
            assertEquals(
                "is_initial_device=$raw 时「我是首端」应为 $expected",
                expected,
                isTrue
            )
        }

        // 字段完全缺失也当 null（不是 false）
        val empty = MiniJson.parse("{}") as JsonValue.Obj
        assertEquals(null, empty.boolOrNull("is_initial_device"))

        // 反证：普通 bool 会把 null 和缺失都读成 false —— 三态在这里就丢了
        val nullCase = MiniJson.parse("""{"is_initial_device":null}""") as JsonValue.Obj
        assertFalse("bool() 会把 null 压成 false（这正是要避免的）", nullCase.bool("is_initial_device"))
    }

    // ── 邀请码字段：请求侧也是蛇形 ───────────────────────────────────

    @Test
    fun `注册请求的邀请码字段是 invite_code`() {
        // 请求体的键名不在响应里，靠对照桌面端已跑通的实现确认：
        // 若写成inviteCode，服务端会当没传 → 报 INVALID_INVITE，
        // 而用户明明填对了邀请码。
        val snake = MiniJson.parse("""{"invite_code":"ABCD-1234-EFGH"}""") as JsonValue.Obj
        assertEquals("ABCD-1234-EFGH", snake.str("invite_code"))
        assertEquals("", snake.str("inviteCode"))
    }

    @Test
    fun `刷新请求与响应都用蛇形且响应嵌在 tokens 里`() {
        // 刷新响应实测：{ "tokens": { access_token, access_expires_at, refresh_token, refresh_expires_at } }
        val o = MiniJson.parse(
            """
            {
              "tokens": {
                "access_token": "aa11",
                "access_expires_at": "2026-10-01T16:18:47.284Z",
                "refresh_token": "bb22",
                "refresh_expires_at": "2026-10-31T16:03:47.284Z"
              }
            }
            """
        ) as JsonValue.Obj

        val tokens = o.objOrNull("tokens")
        assertTrue("刷新响应必须读 tokens 子对象，token 不在顶层", tokens != null)
        assertEquals("aa11", tokens!!.str("access_token"))
        assertEquals("bb22", tokens.str("refresh_token"))

        // 顶层读不到 —— 这正是原来那版代码的 bug：
        // 写成 o.str("access_token") 会拿到空串，然后把空令牌写进本地。
        assertEquals("", o.str("access_token"))
    }
}