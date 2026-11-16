package com.htoc.banyuubox.data

import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * DNS 测速器。真实逻辑留空，当前为随机模拟，方便先把界面跑起来。
 */
object DnsSpeedTester {

    /**
     * 测量一台 DNS 服务器的延迟（毫秒）；无法联通返回 null。
     *
     * TODO: 在这里实现真实测速逻辑（例如向该 DNS 发起一次域名解析并计时），
     *  必须可挂起（suspend），耗时操作放在 Dispatchers.IO。
     */
    suspend fun measureLatency(address: String): Long? {
        // 随机模拟：每次耗时 0.2~0.9 秒，约 1/10 概率不通，延迟 8~320ms
        delay(Random.nextLong(200, 900))
        return if (Random.nextInt(10) == 0) null else Random.nextLong(8, 320)
    }
}
