package com.azka.launcher

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.azka.launcher.ui.AdminGate

class MainActivity : ComponentActivity() {

    private var menuTapCount = 0
    private var lastMenuTapAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AzLauncherApp()
        }

        // Best-effort kiosk (akan jalan kalau device mengizinkan).
        // Kalau tidak diizinkan, aman (tidak crash).
        try {
            startLockTask()
        } catch (_: Throwable) {
            // ignore
        }
    }

    /**
     * Kunci keluar launcher:
     * - BACK: ditahan (no-op)
     * - SETTINGS: ditahan (supaya user tidak masuk settings)
     * - MENU: tekan 5x cepat -> buka Admin PIN
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    // blok keluar
                    return true
                }
                KeyEvent.KEYCODE_SETTINGS -> {
                    // blok settings
                    return true
                }
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

        // ekstra: saat key down BACK/SETTINGS juga ditahan biar konsisten
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_SETTINGS -> return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    /**
     * Double-safety: kalau sistem memanggil back pressed dispatcher, tetap kita tahan.
     */
    override fun onBackPressed() {
        // do nothing
    }
}
