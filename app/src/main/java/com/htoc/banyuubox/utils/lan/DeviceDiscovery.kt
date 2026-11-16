package com.htoc.banyuubox.utils.lan

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 发现到的局域网设备（一台"对端"）。
 *
 * @property name 对方的服务名，用设备型号（如 "Redmi K60"），只用于展示和去重
 * @property host 对方的局域网 IP（解析后才能拿到，如 "192.168.1.23"），连接信令用
 * @property port 对方信令监听端口（注册 NSD 时登记的端口，系统随机分配，每次进页面都不同）
 */
data class LanPeer(
    val name: String,
    val host: String,
    val port: Int,
)

/**
 * 基于 NSD 的局域网设备互相发现。
 *
 * 以下内容由AI生成
 * ## NSD 是什么
 * NSD（Network Service Discovery，网络服务发现）是 Android 封装的 mDNS / DNS-SD 协议，
 * 也就是平时 Bonjour / Zeroconf 那一套：设备往局域网**组播**一条"我在这里"的通告，
 * 同时监听别人的通告，全程不需要中心服务器、不需要手动输 IP，所以叫"零配置"。
 * 路由器开 AP 隔离 / 访客网络时组播被阻断，就发现不了。
 *
 * ## 本类做两件事
 * 1. **注册（广播自己）**：把「设备型号 + 信令端口」登记成一条服务记录广播出去，
 *    对方发现后就知道该连我的哪个端口；
 * 2. **发现（搜索别人）**：搜同一局域网内类型为 [SERVICE_TYPE] 的服务，
 *    解析出 IP / 端口后放进 [peers] 列表。
 *
 * ## 结果如何被使用
 * [peers] 是 StateFlow，UI 里 `discovery.peers.collectAsState()` 即可自动刷新列表。
 * 注意 NSD 的回调都发生在主线程，所以直接改 Flow 是安全的。
 *
 * ## 生命周期
 * [start] / [stop] 必须成对调用（页面 onDispose 时 stop），
 * 否则系统里的注册 / 搜索监听器会泄漏，下次进来还会重复回调。
 */
class DeviceDiscovery(context: Context) {

    // NSD 是系统服务，通过 getSystemService 拿到；用 applicationContext 避免持有 Activity，避免内存泄漏
    private val nsd =
        context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager

    // 对外只读的发现结果；内部用 MutableStateFlow 更新
    private val _peers = MutableStateFlow<List<LanPeer>>(emptyList())
    val peers: StateFlow<List<LanPeer>> = _peers.asStateFlow()  // 只读了就

    // 本机注册时用的服务名（对端发现我们时显示的名字）
    private var localName: String = ""

    // 两个监听器：一个管"广播自己"，一个管"搜索别人"；置空表示当前未在工作
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /**
     * 开始广播自己 + 搜索别人（幂等：重复调用直接返回）。
     *
     * @param localName  本机服务名，用设备型号即可（同型号撞名时系统会自动追加序号）
     * @param localPort  本机信令端口——本类只负责把它广播出去，
     *                   真正监听这个端口的是 [SignalingServer]，两者要配合使用
     */
    @Synchronized
    fun start(localName: String, localPort: Int) {
        // 已经启动过就不再重复注册，防止监听器叠罗汉
        if (registrationListener != null) return
        this.localName = localName

        // ---------- 1. 注册（广播自己） ----------
        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) {
                // 成功后系统会持续替我们通告这条记录，直到 unregisterService
                Log.d(TAG, "已注册广播: ${info.serviceName}:${info.port}")
            }

            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) {
                // 常见原因：服务类型格式不对、系统 NSD 服务异常
                Log.e(TAG, "广播注册失败: $errorCode")
            }

            // 以下两个回调只是接口要求，注销动作由 stop() 发起，不要动了就
            override fun onServiceUnregistered(info: NsdServiceInfo) {}
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) {}
        }
        // 要广播的服务记录：名字 + 类型 + 端口（对方拿到端口才能连我们的信令）
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = localName
            serviceType = SERVICE_TYPE
            port = localPort
        }
        nsd.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)

        // ---------- 2. 发现（搜索别人） ----------
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "开始发现设备")
            }

            /**
             * 找到一条服务时回调。注意：此时只拿到"服务名 + 类型"，
             * 还没有 IP 和端口，必须再调 resolveService 做一次"解析"。
             */
            override fun onServiceFound(info: NsdServiceInfo) {
                // resolveService 在 API 34 标记了废弃（新接口是 registerServiceCallback），
                // 但 minSdk 31 还需要它，且各版本行为稳定，这里继续使用
                @Suppress("DEPRECATION")
                nsd.resolveService(info, object : NsdManager.ResolveListener {
                    /** 解析成功：拿到 IP / 端口，收进列表 */
                    override fun onServiceResolved(resolved: NsdServiceInfo) {
                        // 组播是"所有人听所有人"，会收到自己广播的那条，按名字过滤掉
                        if (resolved.serviceName == localName) return
                        val peer = LanPeer(
                            name = resolved.serviceName,
                            // host 理论上一定有；为 null 说明解析结果异常，直接丢弃这条
                            host = resolved.host?.hostAddress ?: return,
                            port = resolved.port,
                        )
                        // 先按名字去重（同一设备可能被重复发现），再按名字排序让列表顺序稳定
                        _peers.value = (_peers.value.filterNot { it.name == peer.name } + peer)
                            .sortedBy { it.name }
                        Log.d(TAG, "发现设备: $peer")
                    }

                    /** 解析失败（网络抖动、对方刚好下线等），忽略这条即可，下次发现会重试 */
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        Log.w(TAG, "解析失败: ${info.serviceName} code=$errorCode")
                    }
                })
            }

            /** 对方离开网络（正常下线 / 超时），从列表移除 */
            override fun onServiceLost(info: NsdServiceInfo) {
                _peers.value = _peers.value.filterNot { it.name == info.serviceName }
            }

            // 发现停止没有需要善后的状态
            override fun onDiscoveryStopped(serviceType: String) {}

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                // 常见原因：没有本地网络权限（Android 16+）、重复启动发现
                Log.e(TAG, "发现启动失败: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /**
     * 停止广播和发现，并清空列表。页面销毁时务必调用。
     * 回调里带 try-catch 是因为重复注销 / 系统已回收时会抛异常，属预期情况。
     */
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

        /**
         * NSD 服务类型，格式固定为 `_名字._tcp`（或 `_名字._udp`），
         * 后缀表示传输层协议，信令走 TCP。
         * 毕竟你是测速来了，用TCP相互发现应该是可以被相互发现的吧
         * 双端必须完全一致，否则互相看不见对方；名字部分换成别的应用也不会冲突。
         */
        const val SERVICE_TYPE = "_banyuu._tcp"
    }
}
