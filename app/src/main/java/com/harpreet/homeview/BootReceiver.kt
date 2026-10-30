package com.harpreet.homeview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Opens Home View after the tablet restarts (if enabled in settings). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!Prefs.sp(context).getBoolean("boot", false)) return
        // Android 10 only lets apps open a screen from the background with "Display over other apps".
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(context)) return
        val i = Intent(context, MainActivity::class.java)
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
