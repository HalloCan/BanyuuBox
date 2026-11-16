package com.htoc.banyuubox.ui.pages.dns

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.htoc.banyuubox.data.DnsDatabase
import com.htoc.banyuubox.data.DnsListApi
import com.htoc.banyuubox.data.DnsServerDao
import com.htoc.banyuubox.data.DnsSpeedTester
import com.htoc.banyuubox.ui.common.FeaturePageScaffold
import com.htoc.banyuubox.ui.theme.BanyuuBoxTheme
import com.htoc.banyuubox.utils.settings.SettingsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 一台服务器的测速状态：done=false 测试中；done=true 且 latencyMs=null 无法联通。 */
private data class TestResult(
    val address: String,
    val latencyMs: Long? = null,
    val done: Boolean = false,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DNSOptimizeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { DnsDatabase.get(context).dnsServerDao() }
    val scope = rememberCoroutineScope()

    var showEditDialog by remember { mutableStateOf(false) }
    var showTop5 by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<TestResult>>(emptyList()) }

    FeaturePageScaffold(
        title = "DNS 优选",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 页面上方：两个操作按钮
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showEditDialog = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "修改 DNS 列表")
                }
                Button(
                    onClick = {
                        if (!testing) {
                            scope.launch {
                                val addresses = withContext(Dispatchers.IO) {
                                    dao.getAll().map { it.address }
                                }
                                if (addresses.isEmpty()) {
                                    Toast.makeText(
                                        context,
                                        "请先点击「修改 DNS 列表」添加服务器",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    return@launch
                                }
                                testing = true
                                results = addresses.map { TestResult(address = it) }
                                val working = results.toMutableList()
                                addresses.forEachIndexed { index, address ->
                                    val latency = withContext(Dispatchers.IO) {
                                        DnsSpeedTester.measureLatency(address)
                                    }
                                    working[index] =
                                        TestResult(address = address, latencyMs = latency, done = true)
                                    results = working.toList()
                                }
                                testing = false
                                showTop5 = true
                            }
                        }
                    },
                    enabled = !testing,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = if (testing) "测试中…" else "开始测试")
                }
            }

            // 页面下方：两列测速结果列表
            if (results.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "测试结果",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (testing) {
                            "测试中…（${results.count { it.done }}/${results.size}）"
                        } else {
                            "共 ${results.size} 个"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(results) { index, result ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 20.dp, end = 20.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = result.address,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                when {
                                    !result.done -> Text(
                                        text = "测试中…",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )

                                    result.latencyMs != null -> Text(
                                        text = "${result.latencyMs} ms",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )

                                    else -> Text(
                                        text = "无法联通",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog) {
        DnsListEditDialog(
            dao = dao,
            onDismiss = { showEditDialog = false },
        )
    }
    if (showTop5) {
        Top5Dialog(
            results = results,
            onDismiss = { showTop5 = false },
        )
    }
}

/**
 * DNS 列表编辑弹窗：手动输入（一行一个）或从 API 获取，输入停止后自动保存到 Room。
 */
@Composable
private fun DnsListEditDialog(
    dao: DnsServerDao,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }

    // 打开时从 Room 读出已有列表
    LaunchedEffect(Unit) {
        text = withContext(Dispatchers.IO) {
            dao.getAll().joinToString("\n") { it.address }
        }
        ready = true
    }

    // 实时保存：停止输入约 0.6 秒后自动落库
    LaunchedEffect(Unit) {
        snapshotFlow { text to ready }
            .filter { it.second }
            .collectLatest { (current, _) ->
                delay(600)
                withContext(Dispatchers.IO) {
                    dao.replaceAll(current.toAddresses())
                }
            }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "修改 DNS 列表") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    supportingText = {
                        Text(text = "一行一个 DNS 地址，停止输入后自动保存")
                    },
                )
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        scope.launch {
                            val list = withContext(Dispatchers.IO) { DnsListApi.fetch() }
                            if (list.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "暂未获取到数据（API 逻辑待实现）",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                // 追加去重后填入输入框，自动保存会接着落库
                                text = (text.toAddresses() + list).distinct().joinToString("\n")
                            }
                        }
                    },
                ) {
                    Text(text = "从 API 获取")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) { dao.replaceAll(text.toAddresses()) }
                        onDismiss()
                    }
                },
            ) {
                Text(text = "完成")
            }
        },
    )
}

/**
 * 测速完成后弹窗：列出最快的 5 个服务器，点击地址复制到剪贴板。
 */
@Composable
private fun Top5Dialog(
    results: List<TestResult>,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val top5 = results
        .filter { it.done && it.latencyMs != null }
        .sortedBy { it.latencyMs!! }
        .take(5)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "测试完成") },
        text = {
            if (top5.isEmpty()) {
                Text(text = "没有可联通的 DNS 服务器")
            } else {
                Column {
                    Text(
                        text = "最快的 ${top5.size} 个服务器（点击地址可复制）",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    top5.forEach { result ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { copyToClipboard(context, result.address) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = result.address,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${result.latencyMs} ms",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(text = "关闭") }
        },
    )
}


@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DNSOptimisePreview() {
    BanyuuBoxTheme {
        DNSOptimizeScreen(onBack = {})
    }
}

private fun String.toAddresses(): List<String> =
    lines().map { it.trim() }.filter { it.isNotEmpty() }

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("dns_address", text))
    Toast.makeText(context, "已复制 $text", Toast.LENGTH_SHORT).show()
}
