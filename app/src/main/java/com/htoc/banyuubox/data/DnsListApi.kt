package com.htoc.banyuubox.data

import android.util.Log
import com.htoc.banyuubox.utils.dns.GetDNSServersFromAPI

/**
 * 从 API 拉取 DNS 服务器列表。
 */
object DnsListApi {

    /**
     * 请求你的 API，返回 DNS 地址列表（如 ["223.5.5.5", "8.8.8.8"]）。
     * 失败返回空列表，界面会提示"暂未获取到数据"。
     */
    suspend fun fetch(): List<String> {
        return try {
            val servers = GetDNSServersFromAPI().fetchDnsServers()
            servers.forEach { Log.d("DNS", "${it.address}  ${it.name}  ${it.protocol}") }
            servers.map { it.address }
        } catch (e: Exception) {
            Log.e("DNS", "请求失败", e)
            emptyList()
        }
    }
}
