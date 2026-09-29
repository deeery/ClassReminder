package com.example.classreminder

import android.content.Context
import com.example.classreminder.data.WeekSchedule

object Prefs {
    private const val NAME = "classreminder_prefs"
    private const val KEY_ADVANCE_MIN = "advance_minutes"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_SHOW_POPUP = "show_popup"

    private const val DEFAULT_ADVANCE_MIN = 30
    private const val MIN_ADVANCE_MIN = 1
    private const val MAX_ADVANCE_MIN = 180

    /**
     * 提前提醒的时间窗（分钟）。这里是唯一的读取入口，统一夹到 1~180：
     * 脏值（负数/超大）会让 Service 的「即将上课」判定要么永不命中、要么整周命中。
     */
    fun getAdvanceMinutes(ctx: Context): Int {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getInt(KEY_ADVANCE_MIN, DEFAULT_ADVANCE_MIN)
            .coerceIn(MIN_ADVANCE_MIN, MAX_ADVANCE_MIN)
    }

    fun setAdvanceMinutes(ctx: Context, minutes: Int) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putInt(KEY_ADVANCE_MIN, minutes).apply()
    }

    fun getAutoStart(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_AUTO_START, false)
    }

    fun setAutoStart(ctx: Context, enabled: Boolean) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_AUTO_START, enabled).apply()
    }

    fun getShowPopup(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_SHOW_POPUP, true)
    }

    fun setShowPopup(ctx: Context, show: Boolean) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_SHOW_POPUP, show).apply()
    }

    private const val KEY_FIRST_RUN = "first_run"
    private const val KEY_THEME_MODE = "theme_mode"  // 0=follow_system, 1=light, 2=dark

    fun isFirstRun(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_FIRST_RUN, true)
    }

    fun setFirstRunDone(ctx: Context) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_FIRST_RUN, false).apply()
    }

    fun getThemeMode(ctx: Context): Int {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getInt(KEY_THEME_MODE, 0)  // default follow system
    }

    fun setThemeMode(ctx: Context, mode: Int) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putInt(KEY_THEME_MODE, mode).apply()
    }

    // ── 周次校准 ────────────────────────────────────────────────────

    private const val KEY_WEEK1_MONDAY = "week1_monday"

    /** 第 1 周的周一（0 = 还没校准） */
    fun getWeek1Monday(ctx: Context): Long {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getLong(KEY_WEEK1_MONDAY, 0L)
    }

    fun setWeek1Monday(ctx: Context, mondayMillis: Long) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putLong(KEY_WEEK1_MONDAY, mondayMillis).apply()
    }

    /** 今天是第几周；没校准过返回 null（此时课表不按周次过滤） */
    fun currentWeek(ctx: Context): Int? {
        val week1Monday = getWeek1Monday(ctx)
        return if (week1Monday == 0L) null
        else WeekSchedule.weekNumber(week1Monday, System.currentTimeMillis())
    }

    // ── 界面状态（下次打开时接着上次看） ──────────────────────────────

    private const val KEY_LAST_TAB = "last_tab"
    private const val KEY_WEEK_GRID = "week_grid"

    /**
     * 上次停留的非设置页：0=列表，1=课表。
     * 设置页（2）不记录，所以从设置页退出后仍会回到之前那个页面。
     */
    fun getLastTab(ctx: Context): Int {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getInt(KEY_LAST_TAB, 0).coerceIn(0, 1)
    }

    fun setLastTab(ctx: Context, tab: Int) {
        // 唯一的守卫放在这里：只记非设置页
        if (tab !in 0..1) return
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putInt(KEY_LAST_TAB, tab).apply()
    }

    /** 课表默认按表格还是列表显示 */
    fun isWeekGrid(ctx: Context): Boolean {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getBoolean(KEY_WEEK_GRID, false)
    }

    fun setWeekGrid(ctx: Context, grid: Boolean) {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        sp.edit().putBoolean(KEY_WEEK_GRID, grid).apply()
    }
}

