package com.htoc.banyuubox.utils.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 发现到的局域网设备。port 是对方信令监听端口。 */
data class LanPeer(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * 基于 NSD（mDNS）的局域网设备互相发现：
 * 启动时广播自己（服务名 = 设备型号），同时搜索同一局域网内运行本应用的设备。
 * 发现结果通过 [peers] 暴露，UI 直接 collectAsState 即可。
 */
class DeviceDiscovery(context: Context) {

    private val nsd =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()

    private var localName: String = ""
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * 开始广播并发现。[localName] 本机服务名（用设备型号），
     * [localPort] 本机信令端口（对方靠它连过来）。
     */
    @Synchronized
    fun start(localName: String, localPort: Int) {
        if (registrationListener != null) return
        this.localName = localName

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                Log.d(TAG, "已注册广播: ${info.serviceName}:${info.port}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                Log.e(TAG, "广播注册失败: $errorCode")
            }

            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localName
            serviceType = SERVICE_TYPE
            port = localPort
        }
        nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)

        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "开始发现设备")
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                // 找到的服务需要 resolve 才能拿到 IP 和端口
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        // 跳过自己广播出来的那条
                        if (resolved.serviceName == localName) return
                        val peer = LanPeer(
                            name = resolved.serviceName,
                            host = resolved.host?.hostAddress ?: return,
                            port = resolved.port,
                        )
                        _peers.value = (_peers.value.filterNot { it.name == peer.name } + peer)
                            .sortedBy { it.name }
                        Log.d(TAG, "发现设备: $peer")
                    }

                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "解析失败: ${info.serviceName} code=$errorCode")
                    }
                })
            }

            override fun onServiceLost(info: NsdServiceInfo) {
                _peers.value = _peers.value.filterNot { it.name == info.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "发现启动失败: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /** 停止广播和发现。 */
    @Synchronized
    fun stop() {
        registrationListener?.let {
            runCatching { nsd.unregisterService(it) }
            registrationListener = null
        }
        discoveryListener?.let {
            runCatching { nsd.stopServiceDiscovery(it) }
            discoveryListener = null
        }
        _peers.value = emptyList()
    }

    companion object {
        private const val TAG = "DeviceDiscovery"

        /** NSD 服务类型，双端必须一致 */
        const val SERVICE_TYPE = "_banyuu._tcp"
    }
}
