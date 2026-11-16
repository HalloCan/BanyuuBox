package com.htoc.banyuubox.utils.dns

import com.htoc.banyuubox.utils.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/** API 返回的一台 DNS 服务器信息。 */
data class DnsServerInfo(
    val address: String,
    val name: String,
    val protocol: String,
)

class GetDNSServersFromAPI {
    // 服务端地址取自设置页「服务器地址」，也可在调用时传入 urlStr 覆盖
    val server: String get() = AppSettings.serverUrl.value

    suspend fun fetchDnsServers(urlStr: String = server): List<DnsServerInfo> =
        withContext(Dispatchers.IO) {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    // 读错误流，避免连接挂起
                    conn.errorStream?.bufferedReader()?.use { it.readText() }
                    error("HTTP $code")
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val arr = JSONArray(body)
                List(arr.length()) { i ->
                    val o = arr.getJSONObject(i)
                    DnsServerInfo(
                        address = o.optString("address"),
                        name = o.optString("name"),
                        protocol = o.optString("protocol"),
                    )
                }
            } finally {
                conn.disconnect()
            }
        }
}
