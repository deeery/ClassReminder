package com.example.classreminder

import android.content.Context

object Prefs {
    private const val NAME = "classreminder_prefs"
    private const val KEY_ADVANCE_MIN = "advance_minutes"
    private const val KEY_AUTO_START = "auto_start"
    private const val KEY_SHOW_POPUP = "show_popup"

    fun getAdvanceMinutes(ctx: Context): Int {
        val sp = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return sp.getInt(KEY_ADVANCE_MIN, 30)
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
}

