package com.htoc.banyuubox.utils.lan

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** 一次测速的结果（从 iperf3 的 -J JSON 输出解析）。 */
data class IperfResult(
    val role: IperfRole,
    val bandwidthBps: Double,
    val transferredBytes: Double,
    val retransmits: Int?,
    val seconds: Double,
    val raw: String,
)

/**
 * iperf3 执行器：从 assets 释放原生二进制到应用私有目录后直接执行。
 *
 * 命令等价于桌面版 iperf3：
 *  - 服务端: iperf3 -s -1 -p <port> -J   （-1 = 只接受一次测试后退出）
 *  - 客户端: iperf3 -c <peer> -p <port> -t 10 -J
 */
object IperfRunner {

    private const val TAG = "IperfRunner"
    private const val TEST_SECONDS = 10
    private const val TIMEOUT_MS = 90_000L

    /** 把 assets/iperf3/<abi>/iperf3 释放到 filesDir，返回可执行文件。 */
    fun prepareBinary(context: Context): File {
        val abi = Build.SUPPORTED_ABIS.firstOrNull { SUPPORTED_ABIS.contains(it) }
            ?: error("当前设备架构暂不支持（可用 ABI: ${Build.SUPPORTED_ABIS.joinToString()}）")
        val target = File(context.filesDir, "iperf3")
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("iperf3/$abi/iperf3").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            target.setExecutable(true, false)
        }
        return target
    }

    /** 启动 iperf3 服务端，等一个客户端完成一次测试后返回结果。 */
    suspend fun runServer(context: Context, port: Int): Result<IperfResult> = runIperf(
        context, listOf("-s", "-1", "-p", port.toString(), "-J"), IperfRole.SERVER,
    )

    /** 连接对方的 iperf3 服务端测速；服务端还没起来时自动重试。 */
    suspend fun runClient(context: Context, host: String, port: Int): Result<IperfResult> {
        var lastError: Throwable = IllegalStateException("测速失败")
        repeat(3) { attempt ->
            runIperf(
                context,
                listOf("-c", host, "-p", port.toString(), "-t", TEST_SECONDS.toString(), "-J"),
                IperfRole.CLIENT,
            ).onSuccess { return Result.success(it) }
                .onFailure { lastError = it }
            delay(1000) // 服务端可能还在启动，稍后重试
        }
        return Result.failure(lastError)
    }

    private suspend fun runIperf(
        context: Context,
        args: List<String>,
        role: IperfRole,
    ): Result<IperfResult> = withContext(Dispatchers.IO) {
        runCatching {
            val bin = prepareBinary(context)
            Log.d(TAG, "执行: ${bin.absolutePath} ${args.joinToString(" ")}")
            val (exitCode, stdout, stderr) = exec(listOf(bin.absolutePath) + args)
            if (exitCode != 0) {
                error("iperf3 退出码 $exitCode: ${stderr.take(200).ifBlank { stdout.take(200) }}")
            }
            parseResult(role, stdout)
        }
    }

    /** 执行外部进程，带超时保护，协程方式读取输出避免管道堵塞。 */
    private suspend fun exec(cmd: List<String>): Triple<Int, String, String> = coroutineScope {
        val process = ProcessBuilder(cmd).start()
        val out = async { process.inputStream.bufferedReader().use { it.readText() } }
        val err = async { process.errorStream.bufferedReader().use { it.readText() } }
        val finished = process.waitFor(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            error("iperf3 执行超时")
        }
        Triple(process.exitValue(), out.await(), err.await())
    }

    /** 解析 iperf3 -J 的 JSON 输出。 */
    private fun parseResult(role: IperfRole, stdout: String): IperfResult {
        val json = JSONObject(stdout)
        val end = json.getJSONObject("end")
        // TCP 测试以接收端统计为准；没有就退回发送端
        val sum = when {
            end.has("sum_received") -> end.getJSONObject("sum_received")
            else -> end.getJSONObject("sum_sent")
        }
        val sent = if (end.has("sum_sent")) end.getJSONObject("sum_sent") else sum
        return IperfResult(
            role = role,
            bandwidthBps = sum.optDouble("bits_per_second", 0.0),
            transferredBytes = sum.optDouble("bytes", 0.0),
            retransmits = if (sent.has("retransmits")) sent.getInt("retransmits") else null,
            seconds = sum.optDouble("seconds", 0.0),
            raw = stdout,
        )
    }

    private val SUPPORTED_ABIS = setOf("arm64-v8a", "armeabi-v7a", "x86_64")
}
