package com.htoc.banyuubox.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.DeveloperMode
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Handyman
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.NetworkPing
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 一个功能入口：主页上的一行卡片。
 * 想加新功能，往 [FeatureCatalog.sections] 里加一条就行，UI 会自动渲染。
 */
data class FeatureItem(
    val id: String,
    val title: String,
    val description: String,
    val icon: ImageVector,
)

/**
 * 主页上的一个功能板块，包含若干 [FeatureItem]。
 */
data class FeatureSection(
    val title: String,
    val icon: ImageVector,
    val items: List<FeatureItem>,
)

object FeatureCatalog {

    val sections: List<FeatureSection> = listOf(
        FeatureSection(
            title = "网络功能",
            icon = Icons.Rounded.Wifi,
            items = listOf(
                FeatureItem(
                    id = "lan_speed_test",
                    title = "局域网测速",
                    description = "检测设备之间的传输速度与网络质量",
                    icon = Icons.Rounded.Speed,
                ),
                FeatureItem(
                    id = "lan_live_stream",
                    title = "实时图传",
                    description = "在局域网内实时传输与查看画面",
                    icon = Icons.Rounded.Cast,
                ),
                FeatureItem(
                    id = "ping_traceroute",
                    title = "Ping / Tracert",
                    description = "测试连通性与延迟，跟踪路由路径",
                    icon = Icons.Rounded.NetworkPing,
                ),
                FeatureItem(
                    id = "dns_lookup",
                    title = "DNS 查询",
                    description = "查询域名的 DNS 解析记录",
                    icon = Icons.Rounded.Dns,
                ),
                FeatureItem(
                    id = "dns_optimize",
                    title = "DNS 优选",
                    description = "测速对比并挑选最优 DNS 服务器",
                    icon = Icons.AutoMirrored.Rounded.CompareArrows,
                ),
            ),
        ),
        FeatureSection(
            title = "实用工具",
            icon = Icons.Rounded.Handyman,
            items = listOf(
                FeatureItem(
                    id = "pdf_to_image",
                    title = "PDF 转图片",
                    description = "将 PDF 页面导出为图片文件",
                    icon = Icons.Rounded.PictureAsPdf,
                ),
                FeatureItem(
                    id = "qr_tools",
                    title = "QR 码生成/识别",
                    description = "生成二维码，或识别图片中的二维码",
                    icon = Icons.Rounded.QrCodeScanner,
                ),
                FeatureItem(
                    id = "password_generator",
                    title = "强密码生成器",
                    description = "生成安全的随机强密码",
                    icon = Icons.Rounded.Password,
                ),
            ),
        ),
        FeatureSection(
            title = "硬件功能",
            icon = Icons.Rounded.Memory,
            items = listOf(
                FeatureItem(
                    id = "device_sensors",
                    title = "传感器数据",
                    description = "读取光照、加速度、陀螺仪等传感器数据",
                    icon = Icons.Rounded.Sensors,
                ),
            ),
        ),
        FeatureSection(
            title = "开发者功能",
            icon = Icons.Rounded.DeveloperMode,
            items = listOf(
                FeatureItem(
                    id = "markdown_preview",
                    title = "Markdown 预览",
                    description = "预览渲染 Markdown 文档",
                    icon = Icons.Rounded.Visibility,
                ),
            ),
        ),
    )

    fun find(id: String): FeatureItem? = sections.flatMap { it.items }.firstOrNull { it.id == id }
}
