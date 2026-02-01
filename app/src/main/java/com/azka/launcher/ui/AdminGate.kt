package com.azka.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Gerbang admin sederhana:
 * - Activity bisa memicu AdminGate.trigger()
 * - UI (HomeScreen) akan menampilkan dialog PIN
 */
object AdminGate {
    var show by mutableStateOf(false)
        private set

    fun trigger() {
        show = true
    }

    fun close() {
        show = false
    }
}
