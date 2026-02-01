package com.azka.launcher

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.azka.launcher.ui.AdminGate

class MainActivity : ComponentActivity() {

    private var menuTapCount = 0
    private var lastMenuTapAt = 0L

    // watchdog timing
    private var lastStoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent { AzLauncherApp() }

        // Best-effort kiosk: LockTask (kalau device mengizinkan).
        runCatching { startLockTask() }
    }

    override fun onResume() {
        super.onResume()

        // Re-apply best-effort lock
        runCatching { startLockTask() }

        // Watchdog: kalau sempat keluar sebentar, paksa balik ke depan
        // (bukan 100% blok, tapi mengurangi "kabur" di banyak STB)
        val now = SystemClock.elapsedRealtime()
        val delta = now - lastStoppedAt
        if (lastStoppedAt > 0L && delta < 5000L) {
            // bawa task sendiri ke depan
            runCatching {
                val i = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(i)
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastStoppedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Kunci keluar launcher (best-effort):
     * - BACK: ditahan
     * - SETTINGS: ditahan
     * - MENU: tekan 5x cepat -> buka Admin PIN
     *
     * Catatan:
     * - HOME/RECENTS tidak bisa dijamin 100% diblok oleh app biasa.
     * - Dengan default HOME + lockTask, biasanya cukup untuk operasional guest house.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> return true
                KeyEvent.KEYCODE_SETTINGS -> return true
                KeyEvent.KEYCODE_MENU -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastMenuTapAt > 1500) {
                        menuTapCount = 0
                    }
                    lastMenuTapAt = now
                    menuTapCount++

                    if (menuTapCount >= 5) {
                        menuTapCount = 0
                        AdminGate.trigger()
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_SETTINGS -> return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() {
        // do nothing
    }
}
