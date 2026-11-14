package com.htoc.banyuubox

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.htoc.banyuubox.home.HomeScreen
import com.htoc.banyuubox.ui.theme.BanyuuBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BanyuuBoxTheme {
                HomeScreen(
                    onFeatureClick = { feature ->
                        // 各功能页还没做，先弹提示。以后按 feature.id 跳转到对应页面。
                        Toast.makeText(this, "「${feature.title}」开发中", Toast.LENGTH_SHORT).show()
                    },
                    onSettingsClick = {
                        Toast.makeText(this, "设置页开发中", Toast.LENGTH_SHORT).show()
                    },
                )
            }
        }
    }
}
