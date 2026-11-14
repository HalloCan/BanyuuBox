package com.htoc.banyuubox.feature

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lan
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Speed
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
            title = "局域网",
            icon = Icons.Rounded.Lan,
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
            ),
        ),
        FeatureSection(
            title = "实用工具",
            icon = Icons.Rounded.Description,
            items = listOf(
                FeatureItem(
                    id = "pdf_to_image",
                    title = "PDF 转图片",
                    description = "将 PDF 页面导出为图片文件",
                    icon = Icons.Rounded.PictureAsPdf,
                ),
            ),
        ),
    )

    fun find(id: String): FeatureItem? = sections.flatMap { it.items }.firstOrNull { it.id == id }
}
