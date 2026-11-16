package com.htoc.banyuubox.utils.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.htoc.banyuubox.ui.common.FeaturePageScaffold

/**
 * 一个开源组件的标注信息。
 */
private data class OpenSourceItem(
    val name: String,
    val author: String,
    val license: String,
    val usage: String,
)

/**
 * 本项目运行时包含的开源组件清单。
 * 注意：Gradle / Android SDK / NDK 等构建期工具不随应用分发，故不在此列出。
 */
private val openSourceItems = listOf(
    OpenSourceItem(
        name = "Kotlin & Kotlin 标准库",
        author = "JetBrains",
        license = "Apache License 2.0",
        usage = "应用开发语言与标准库",
    ),
    OpenSourceItem(
        name = "AndroidX（Core / Activity / Lifecycle）",
        author = "The Android Open Source Project",
        license = "Apache License 2.0",
        usage = "Android 基础组件与生命周期管理",
    ),
    OpenSourceItem(
        name = "Jetpack Compose（含 Material 3）",
        author = "The Android Open Source Project",
        license = "Apache License 2.0",
        usage = "声明式 UI 框架与主题",
    ),
    OpenSourceItem(
        name = "Material Design Icons",
        author = "Google",
        license = "Apache License 2.0",
        usage = "界面图标（material-icons-extended）",
    ),
    OpenSourceItem(
        name = "kotlinx.coroutines",
        author = "JetBrains",
        license = "Apache License 2.0",
        usage = "协程异步框架（网络、进程执行）",
    ),
    OpenSourceItem(
        name = "AndroidX Room",
        author = "The Android Open Source Project",
        license = "Apache License 2.0",
        usage = "本地数据库（DNS 服务器列表持久化）",
    ),
    OpenSourceItem(
        name = "iperf3",
        author = "ESnet / University of Illinois",
        license = "BSD 3-Clause License",
        usage = "局域网测速引擎（arm64 原生二进制）",
    ),
    OpenSourceItem(
        name = "cJSON",
        author = "Dave Gamble",
        license = "MIT License",
        usage = "iperf3 内置的 JSON 解析库",
    ),
)

/**
 * 「开放源代码许可」页面：列出本项目使用的所有开源组件及其许可证。
 */
@Composable
fun OpenSourceLicensesScreen(onBack: () -> Unit) {
    FeaturePageScaffold(
        title = "开放源代码许可",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    openSourceItems.forEachIndexed { index, item ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 20.dp),
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                        }
                        OpenSourceRow(item = item)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "以上为随应用分发的开源组件。Gradle、Android SDK、NDK 等构建期工具不包含在应用内，故未列出。各许可证全文请见对应项目官网。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun OpenSourceRow(item: OpenSourceItem) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text = item.name, style = MaterialTheme.typography.titleMedium)
        Text(
            text = "${item.author} · ${item.license}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = item.usage,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
