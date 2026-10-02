package com.example.classreminder.data.sync

import android.content.Context
import android.content.SharedPreferences

/**
 * 安卓端的游标持久化（[SyncPrefs] 的实现）。
 *
 * ## 为什么单独存，不混进 `Prefs`
 *
 * `Prefs` 是**用户偏好**（提前几分钟提醒、深色主题…），会跟着「导出/导入设置」走。
 * 同步游标是**本机同步状态**，跟着设置备份跑到别的设备上是错的：
 * 那台设备的库是空的、游标却已经是 800，于是服务端 revision ≤ 800 的记录
 * 全部拉不下来 —— 表现为「同步显示成功，但课表空空如也」。
 *
 * ## 为什么写盘失败不能抛
 *
 * 游标丢了最坏后果是**退化成全量重拉**（服务端会全量下发给非首端），
 * 数据不会出错。而抛异常会让整轮同步失败、用户看到红色报错 ——
 * 为了一个「重拉一次就能自愈」的问题把功能整个弄挂，不划算。
 */
class SyncPrefsImpl(context: Context) : SyncPrefs {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override var lastCursor: Int
        get() = sp.getInt(KEY_CURSOR, 0)
        set(value) {
            // commit() 而非 apply()：apply 是异步的，进程被杀时可能丢。
            // 游标在同步收尾时才写，那一刻本来就在等这个操作完成。
            runCatching { sp.edit().putInt(KEY_CURSOR, value).commit() }
        }

    override var syncedAccountId: Int
        get() = sp.getInt(KEY_ACCOUNT_ID, 0)
        set(value) {
            runCatching { sp.edit().putInt(KEY_ACCOUNT_ID, value).commit() }
        }

    private companion object {
        /**
         * 文件名带 `_sync` 后缀，避免和 `Prefs` / `AccountSession` 的
         * SharedPreferences 混淆 —— 三个都用不同名字，读起来一眼能对上。
         */
        const val FILE_NAME = "stumate_sync"
        const val KEY_CURSOR = "cursor"

        /**
         * 本地数据归属的账号 id。和游标放同一个文件、同生共死：
         * 单独存会出现「游标是新的、账号还是旧的」这种自相矛盾的状态。
         */
        const val KEY_ACCOUNT_ID = "accountId"
    }
}