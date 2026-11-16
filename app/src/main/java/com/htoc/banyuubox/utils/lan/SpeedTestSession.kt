package com.htoc.banyuubox.utils.lan

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.net.ServerSocket
import java.net.Socket
import kotlin.random.Random

/** iperf3 角色：服务端（-s）或客户端（-c）。 */
enum class IperfRole { SERVER, CLIENT }

/**
 * 一次测速的约定结果。
 * [role] 本机的 iperf3 角色；[port] 随机约定的测速端口；[peerHost] 对方 IP。
 */
data class SessionPlan(
    val role: IperfRole,
    val port: Int,
    val peerHost: String,
)

/**
 * 信令协议（换行分隔的 JSON）：
 *
 * 1. 发起方 → 接收方: {"type":"hello","name":"设备名"}
 * 2. 接收方 → 发起方: {"type":"accept"} 或 {"type":"reject"}（弹窗让用户确认）
 * 3. 发起方 → 接收方: {"type":"offer","role":"server|client","port":随机端口}
 *    （随机约定：发起方掷骰子决定双方角色和测速端口）
 * 4. 双方各自启动 iperf3，关闭信令连接。
 *
 * offer 中的 role 是「发起方」的角色，接收方自动取反。
 */

/** 本模块用到的端口/协议常量。 */
object LanProtocol {
    /** 随机测速端口范围（>1024 免 root） */
    val PORT_RANGE = 20000..55000

    fun randomPort(): Int = Random.nextInt(PORT_RANGE.first, PORT_RANGE.last + 1)

    fun randomRole(): IperfRole =
        if (Random.nextBoolean()) IperfRole.SERVER else IperfRole.CLIENT
}

/**
 * 被动端：监听信令连接。收到测速请求时弹窗（由 [onRequest] 实现），
 * 用户接受后接收对方发来的随机约定，回调 [onPlan]。
 */
class SignalingServer(
    private val myName: String,
    private val onRequest: suspend (peerName: String) -> Boolean,
    private val onPlan: (SessionPlan) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var serverSocket: ServerSocket? = null

    /** 启动监听，返回实际端口（0 表示由系统分配）。 */
    @Synchronized
    fun start(): Int {
        val socket = ServerSocket(0)
        serverSocket = socket
        scope.launch {
            while (!socket.isClosed) {
                try {
                    val client = socket.accept()
                    launch { handle(client) }
                } catch (e: Exception) {
                    if (!socket.isClosed) onError("监听异常: ${e.message}")
                    break
                }
            }
        }
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        runCatching { serverSocket?.close() }
        serverSocket = null
    }

    private suspend fun handle(socket: Socket) = withContext(Dispatchers.IO) {
        socket.use { s ->
            try {
                val input = s.getInputStream().bufferedReader()
                val output = s.getOutputStream().bufferedWriter()

                val hello = JSONObject(input.readLineOrNull() ?: return@use)
                if (hello.optString("type") != "hello") return@use
                val peerName = hello.optString("name", "未知设备")
                val peerHost = s.inetAddress.hostAddress ?: return@use

                // 弹窗等用户确认
                val accepted = onRequest(peerName)
                output.writeLine(if (accepted) """{"type":"accept"}""" else """{"type":"reject"}""")
                if (!accepted) return@use

                // 接收随机约定
                val offer = JSONObject(input.readLineOrNull() ?: return@use)
                if (offer.optString("type") != "offer") return@use
                val initiatorRole = IperfRole.valueOf(offer.optString("role").uppercase())
                val plan = SessionPlan(
                    // offer 中是发起方的角色，本机取反
                    role = if (initiatorRole == IperfRole.SERVER) IperfRole.CLIENT else IperfRole.SERVER,
                    port = offer.getInt("port"),
                    peerHost = peerHost,
                )
                Log.d(TAG, "约定完成: $plan")
                onPlan(plan)
            } catch (e: Exception) {
                Log.e(TAG, "信令处理失败", e)
                onError("信令失败: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "SignalingServer"
    }
}

/**
 * 主动端：连接对方信令端口，走完握手，掷骰子约定角色与端口，返回本机的 [SessionPlan]。
 * 对方拒绝时抛出异常。
 */
suspend fun negotiateAsInitiator(
    peer: LanPeer,
    myName: String,
): SessionPlan = withContext(Dispatchers.IO) {
    Socket(peer.host, peer.port).use { socket ->
        val input = socket.getInputStream().bufferedReader()
        val output = socket.getOutputStream().bufferedWriter()

        output.writeLine(JSONObject().apply {
            put("type", "hello")
            put("name", myName)
        }.toString())

        val reply = JSONObject(input.readLineOrNull() ?: error("对方无响应"))
        if (reply.optString("type") == "reject") {
            error("对方拒绝了测速请求")
        }

        // 随机约定：本方角色 + 测速端口
        val myRole = LanProtocol.randomRole()
        val port = LanProtocol.randomPort()
        output.writeLine(JSONObject().apply {
            put("type", "offer")
            put("role", myRole.name.lowercase())
            put("port", port)
        }.toString())

        SessionPlan(role = myRole, port = port, peerHost = peer.host)
    }
}

private fun java.io.Writer.writeLine(text: String) {
    write(text)
    write("\n")
    flush()
}

private fun BufferedReader.readLineOrNull(): String? = try {
    readLine()
} catch (e: Exception) {
    null
}
