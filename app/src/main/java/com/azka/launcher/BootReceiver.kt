package com.azka.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Auto-start saat boot.
 * Catatan:
 * - Di sebagian device, startActivity dari BOOT_COMPLETED bisa dibatasi.
 * - Tapi karena kita juga jadi HOME candidate, biasanya aman setelah user set default Home ke AzLauncher.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        runCatching {
            context.startActivity(launch)
        }
    }
}
