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
import com.htoc.banyuubox.settings.AppSettings
import com.htoc.banyuubox.settings.SettingsScreen
import com.htoc.banyuubox.settings.ThemeMode
import com.htoc.banyuubox.ui.theme.BanyuuBoxTheme

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
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
                // 各功能页还没做，先弹提示。以后按 feature.id 跳转到对应页面。
                when(feature.id){
                    "pdf_to_image" -> Toast.makeText(context, "PDF", Toast.LENGTH_SHORT).show()
                    else -> Toast.makeText(context, "「${feature.title}」开发中", Toast.LENGTH_SHORT).show()
                }
            },
            onSettingsClick = { screen = Screen.Settings },
        )

        Screen.Settings -> SettingsScreen(onBack = { screen = Screen.Home })
    }
}
