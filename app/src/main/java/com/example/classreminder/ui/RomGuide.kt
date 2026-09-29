package com.example.classreminder.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * 国内 ROM（ColorOS / OxygenOS / realme UI）的特有权限引导。
 *
 * 这些权限**没有标准 API**：既查不到状态，也申请不了，只能把用户送到系统设置页手动开。
 * 组件名是在真机（一加 PLC110 / ColorOS V16.1.0）上实测抓到的；换了机型可能失效，
 * 所以每个入口都有兜底（应用详情页），不会点了没反应。
 */
object RomGuide {

    /** 是否是 OPPO 系（OPPO / 一加 / realme） */
    val isOplus: Boolean = listOf(Build.MANUFACTURER, Build.BRAND, Build.PRODUCT, Build.DEVICE)
        .any { value ->
            val v = value?.lowercase().orEmpty()
            v.contains("oppo") || v.contains("oneplus") || v.contains("realme")
        }

    /** 打开「特別應用程式權限」页（里面有「背景彈出介面」「傳送全螢幕通知」） */
    fun openSpecialAccess(ctx: Context) {
        val candidates = listOf(
            // ColorOS：设置 → 权限管理 → 特别应用程序权限（真机实测组件名）
            ComponentName(
                "com.android.settings",
                "com.oplus.settings.OplusSettingsActivity\$AppSpecialAccessSettingsMainActivity"
            ),
            // ColorOS：权限管理页（兜底一）
            ComponentName(
                "com.oplus.securitypermission",
                "com.oplusos.securitypermission.permission.PermissionGroupsActivity"
            )
        )
        for (component in candidates) {
            val intent = Intent()
                .setComponent(component)
                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (ctx.packageManager.resolveActivity(intent, 0) != null) {
                runCatching { ctx.startActivity(intent) }.onSuccess { return }
            }
        }
        openAppDetails(ctx)
    }

    /** 应用详情页（所有机型都有；自启动/用电量管理都在这一页下面） */
    fun openAppDetails(ctx: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${ctx.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { ctx.startActivity(intent) }
    }
}
