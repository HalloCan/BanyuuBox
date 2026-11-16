package com.htoc.banyuubox.ui.pages.lan

import android.os.Build
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.htoc.banyuubox.ui.common.FeaturePageScaffold
import com.htoc.banyuubox.utils.lan.DeviceDiscovery
import com.htoc.banyuubox.utils.lan.IperfResult
import com.htoc.banyuubox.utils.lan.IperfRole
import com.htoc.banyuubox.utils.lan.IperfRunner
import com.htoc.banyuubox.utils.lan.LanPeer
import com.htoc.banyuubox.utils.lan.SessionPlan
import com.htoc.banyuubox.utils.lan.SignalingServer
import com.htoc.banyuubox.utils.lan.negotiateAsInitiator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** 一个等待用户确认的入站测速请求。 */
private class IncomingRequest(
    val peerName: String,
    val respond: (Boolean) -> Unit,
)

@Composable
fun LanSpeedTestScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val deviceName = remember { Build.MODEL }

    val discovery = remember { DeviceDiscovery(context) }
    val peers by discovery.peers.collectAsState()

    var status by remember { mutableStateOf("正在启动…") }
    var busy by remember { mutableStateOf(false) }
    var plan by remember { mutableStateOf<SessionPlan?>(null) }
    var result by remember { mutableStateOf<IperfResult?>(null) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var incoming by remember { mutableStateOf<IncomingRequest?>(null) }

    // 双方约定完成后启动 iperf3 测速
    fun runTest(p: SessionPlan) {
        plan = p
        busy = true
        status = if (p.role == IperfRole.SERVER) {
            "测速中… 本机为 iperf3 服务端（-s），端口 ${p.port}"
        } else {
            "测速中… 本机为 iperf3 客户端（-c），连接 ${p.peerHost}:${p.port}"
        }
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                when (p.role) {
                    IperfRole.SERVER -> IperfRunner.runServer(context, p.port)
                    IperfRole.CLIENT -> IperfRunner.runClient(context, p.peerHost, p.port)
                }
            }
            r.fold(
                onSuccess = {
                    result = it
                    busy = false
                    status = "测速完成"
                },
                onFailure = {
                    errorText = it.message ?: "测速失败"
                    busy = false
                    status = "空闲"
                },
            )
        }
    }

    // 启动信令监听 + 设备发现；离开页面时关闭
    DisposableEffect(Unit) {
        val server = SignalingServer(
            myName = deviceName,
            onRequest = { peerName ->
                suspendCancellableCoroutine { cont ->
                    incoming = IncomingRequest(peerName) { accepted ->
                        incoming = null
                        if (cont.isActive) cont.resume(accepted)
                    }
                }
            },
            onPlan = { p -> runTest(p) },
            onError = { msg -> errorText = msg },
        )
        val port = server.start()
        discovery.start(deviceName, port)
        status = "正在搜索同局域网设备…"
        onDispose {
            server.stop()
            discovery.stop()
        }
    }

    FeaturePageScaffold(
        title = "局域网测速",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // 本机状态
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(text = deviceName, style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = status,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (busy) {
                        Spacer(modifier = Modifier.width(12.dp))
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "发现的设备 · 点按发起测速",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (peers.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "暂未发现设备。\n请确认两台手机连接同一个 Wi-Fi，\n并且都打开了「局域网测速」页面。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(20.dp),
                    )
                }
            } else {
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
                        items(peers) { peer ->
                            PeerRow(
                                peer = peer,
                                enabled = !busy,
                                onClick = {
                                    busy = true
                                    status = "正在连接 ${peer.name}…"
                                    scope.launch {
                                        runCatching { negotiateAsInitiator(peer, deviceName) }
                                            .onSuccess { runTest(it) }
                                            .onFailure {
                                                errorText = it.message ?: "连接失败"
                                                busy = false
                                                status = "空闲"
                                            }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    // 入站测速请求确认
    incoming?.let { req ->
        AlertDialog(
            onDismissRequest = { req.respond(false) },
            title = { Text(text = "测速请求") },
            text = {
                Text(text = "设备「${req.peerName}」想和你进行局域网测速，是否接受？")
            },
            confirmButton = {
                TextButton(onClick = { req.respond(true) }) { Text(text = "接受") }
            },
            dismissButton = {
                TextButton(onClick = { req.respond(false) }) { Text(text = "拒绝") }
            },
        )
    }

    // 测速结果
    result?.let { r ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text(text = "测速结果") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ResultRow("角色", if (r.role == IperfRole.SERVER) "iperf3 服务端 (-s)" else "iperf3 客户端 (-c)")
                    ResultRow("端口", plan?.port?.toString() ?: "-")
                    ResultRow("吞吐", formatBandwidth(r.bandwidthBps))
                    ResultRow("传输量", formatBytes(r.transferredBytes))
                    r.retransmits?.let { ResultRow("重传", "$it 次") }
                    ResultRow("时长", "%.1f s".format(r.seconds))
                }
            },
            confirmButton = {
                TextButton(onClick = { result = null }) { Text(text = "关闭") }
            },
        )
    }

    // 错误提示
    errorText?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorText = null },
            title = { Text(text = "出错了") },
            text = { Text(text = msg) },
            confirmButton = {
                TextButton(onClick = { errorText = null }) { Text(text = "好的") }
            },
        )
    }
}

@Composable
private fun PeerRow(
    peer: LanPeer,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Devices,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = peer.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = peer.host,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun formatBandwidth(bps: Double): String = when {
    bps >= 1e9 -> "%.2f Gbps".format(bps / 1e9)
    bps >= 1e6 -> "%.2f Mbps".format(bps / 1e6)
    bps >= 1e3 -> "%.2f kbps".format(bps / 1e3)
    else -> "%.0f bps".format(bps)
}

private fun formatBytes(bytes: Double): String = when {
    bytes >= 1e9 -> "%.2f GB".format(bytes / 1e9)
    bytes >= 1e6 -> "%.2f MB".format(bytes / 1e6)
    bytes >= 1e3 -> "%.2f KB".format(bytes / 1e3)
    else -> "%.0f B".format(bytes)
}
