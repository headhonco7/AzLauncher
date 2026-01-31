package com.azka.launcher

import androidx.compose.runtime.Composable
import com.azka.launcher.ui.HomeScreen
import com.azka.launcher.ui.theme.AzLauncherTheme

@Composable
fun AzLauncherApp() {
    AzLauncherTheme {
        HomeScreen()
    }
}
