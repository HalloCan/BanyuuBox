package com.htoc.banyuubox

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.htoc.banyuubox.home.HomeScreen
import com.htoc.banyuubox.ui.pages.dns.DNSOptimizeScreen
import com.htoc.banyuubox.ui.pages.pdf.PdfToImageScreen
import com.htoc.banyuubox.utils.settings.AppSettings
import com.htoc.banyuubox.utils.settings.SettingsScreen
import com.htoc.banyuubox.utils.settings.ThemeMode
import com.htoc.banyuubox.ui.theme.BanyuuBoxTheme

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object PdfToImage : Screen
    data object DNSOptimize : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge()
        setContent {
            val themeMode by AppSettings.themeMode
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            BanyuuBoxTheme(darkTheme = darkTheme) {
                AppNavHost()
            }
        }
    }
}

@Composable
private fun AppNavHost() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (screen) {
        Screen.Home -> HomeScreen(
            onFeatureClick = { feature ->
                when (feature.id) {
                    "pdf_to_image" -> screen = Screen.PdfToImage
                    "dns_optimize" -> screen = Screen.DNSOptimize
                    // 其余功能页还没做，先弹提示。以后按 feature.id 加分支即可。
                    else -> Toast.makeText(context, "「${feature.title}」开发中", Toast.LENGTH_SHORT).show()
                }
            },
            onSettingsClick = { screen = Screen.Settings },
        )
        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
        Screen.PdfToImage -> PdfToImageScreen(onBack = { screen = Screen.Home })
        Screen.DNSOptimize -> DNSOptimizeScreen(onBack = { screen = Screen.Home } )
    }
}
