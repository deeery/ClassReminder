package com.example.classreminder

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED && context != null) {
            val auto = Prefs.getAutoStart(context)
            if (auto) {
                // On Android 13+, startForeground() needs POST_NOTIFICATIONS permission.
                // Without it the process crashes — check before starting.
                val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                } else {
                    true
                }
                if (hasPermission) {
                    val svc = Intent(context, ClassReminderService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(svc)
                    } else {
                        context.startService(svc)
                    }
                }
            }
        }
    }
}

