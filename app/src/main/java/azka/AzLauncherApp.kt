package azka

import androidx.compose.runtime.Composable
import azka.ui.HomeScreen
import azka.ui.theme.AzLauncherTheme

@Composable
fun AzLauncherApp() {
    AzLauncherTheme {
        HomeScreen()
    }
}
