package com.htoc.banyuubox.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.htoc.banyuubox.ui.common.FeaturePageScaffold
import com.htoc.banyuubox.ui.theme.BanyuuBoxTheme

/**
 * 预览中的一页图片。
 *
 * [id] 用于列表稳定定位；[pageIndex] 是它在原 PDF 中的页码。
 * 接入真实转换逻辑后，可以在这里补上 bitmap / uri 等字段。
 */
data class PageImage(
    val id: Long,
    val pageIndex: Int,
)

private val CARD_WIDTH = 112.dp
private val CARD_HEIGHT = 158.dp
private val CARD_SPACING = 12.dp

@Composable
fun PdfToImageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember {
        mutableStateOf(List(5) { i -> PageImage(id = i.toLong(), pageIndex = i + 1) })
    }
    var previewPage by remember { mutableStateOf<PageImage?>(null) }

    // 注册启动器
    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        // 用户选完文件后回到这里；取消选择时 uri 为 null
        if (uri != null) {
            Toast.makeText(context, uri.toString(), Toast.LENGTH_SHORT).show()
        }
    }

    FeaturePageScaffold(
        title = "PDF 转图片",
        onBack = onBack,
        actionLabel = "保存图片",
        actionEnabled = pages.isNotEmpty(),
        onAction = { /* TODO: 保存导出的图片 */ },
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedButton(
                onClick = {
                    pickPdf.launch(arrayOf("application/pdf"))
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.UploadFile,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "选择 PDF 文件")
            }

            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = if (pages.isEmpty()) "页面预览" else "页面预览 · 共 ${pages.size} 页",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (pages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(CARD_HEIGHT),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "选择 PDF 文件后，这里会显示页面预览",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                PagePreviewRow(
                    pages = pages,
                    onMove = { from, to ->
                        pages = pages.toMutableList().apply { add(to, removeAt(from)) }
                    },
                    onPageClick = { previewPage = it },
                    onPageDelete = { target -> pages = pages.filterNot { it.id == target.id } },
                )
            }
        }
    }

    previewPage?.let { page ->
        PagePreviewDialog(
            page = page,
            onDelete = {
                pages = pages.filterNot { it.id == page.id }
                previewPage = null
            },
            onDismiss = { previewPage = null },
        )
    }
}

/**
 * 横向图片预览列表：点击放大预览，长按拖动排序，点右上角 × 删除。
 */
@Composable
private fun PagePreviewRow(
    pages: List<PageImage>,
    onMove: (from: Int, to: Int) -> Unit,
    onPageClick: (PageImage) -> Unit,
    onPageDelete: (PageImage) -> Unit,
) {
    // 拖动中的那张卡片的下标和它相对原位的横向偏移
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    // 拖动过程中列表会变化，用 rememberUpdatedState 保证手势回调读到最新值
    val currentPages by rememberUpdatedState(pages)
    val currentOnMove by rememberUpdatedState(onMove)

    val itemWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
        (CARD_WIDTH + CARD_SPACING).toPx()
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(CARD_SPACING),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // 故意不用 item key：排序交换时保持槽位上的手势不被打断
        itemsIndexed(currentPages) { index, page ->
            val dragging = index == draggingIndex
            PageCard(
                page = page,
                onClick = { onPageClick(page) },
                onDelete = { onPageDelete(page) },
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationX = if (dragging) dragOffsetX else 0f }
                    .pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                draggingIndex = index
                                dragOffsetX = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragOffsetX += amount.x
                                val from = draggingIndex ?: return@detectDragGesturesAfterLongPress
                                val list = currentPages
                                // 拖过一个卡片宽度就和邻居交换位置
                                if (dragOffsetX > itemWidthPx && from < list.lastIndex) {
                                    currentOnMove(from, from + 1)
                                    dragOffsetX -= itemWidthPx
                                    draggingIndex = from + 1
                                } else if (dragOffsetX < -itemWidthPx && from > 0) {
                                    currentOnMove(from, from - 1)
                                    dragOffsetX += itemWidthPx
                                    draggingIndex = from - 1
                                }
                            },
                            onDragEnd = {
                                draggingIndex = null
                                dragOffsetX = 0f
                            },
                            onDragCancel = {
                                draggingIndex = null
                                dragOffsetX = 0f
                            },
                        )
                    },
            )
        }
    }
}

@Composable
private fun PageCard(
    page: PageImage,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(width = CARD_WIDTH, height = CARD_HEIGHT),
    ) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = page.placeholderColor()),
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "第 ${page.pageIndex} 页",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "删除",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun PagePreviewDialog(
    page: PageImage,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "第 ${page.pageIndex} 页") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // TODO: 接入真实图片后，把这里换成 Image(bitmap = ...)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = page.placeholderColor()),
                    modifier = Modifier.size(width = 220.dp, height = 310.dp),
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "图片预览",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "长按缩略图可拖动排序",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {
            TextButton(onClick = onDelete) {
                Text(text = "删除", color = MaterialTheme.colorScheme.error)
            }
        },
    )
}

@Composable
private fun PageImage.placeholderColor() =
    if (pageIndex % 2 == 0) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

@Preview(showBackground = true)
@Composable
private fun PdfToImageScreenPreview() {
    BanyuuBoxTheme {
        PdfToImageScreen(onBack = {})
    }
}

fun pdfLoader(context: Context, uri: Uri){
    // 1.将pdf拷贝到应用缓存目录
    val file: File? = uriToFile(context, uri)
}

fun uriToFile(context: Context, uri: Uri): File? {
    return try {
        // 1. 打开输入流
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null

        // 2. 在应用私有目录下创建目标文件
        // 这里使用应用缓存目录
        val fileName = getFileNameFromUri(context, uri) ?: "temp_${System.currentTimeMillis()}"
        val targetFile = File(context.cacheDir, fileName)

        // 3. 将输入流复制到目标文件
        targetFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        inputStream.close()

        targetFile
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

// 辅助方法：尝试从 URI 获取原始文件名
fun getFileNameFromUri(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex != -1) {
                return it.getString(nameIndex)
            }
        }
    }
    return null
}


// 将某一页pdf转换为bitmap
// 1. 在后台线程（如 Coroutine 的 Dispatchers.IO）中执行
fun renderPdfPageToBitmap(pdfFile: File, pageIndex: Int): Bitmap? {
    var fileDescriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null

    return try {
        // 2. 以只读方式打开文件，获取可随机访问的描述符
        fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)

        // 3. 创建 PdfRenderer。注意：构造过程可能耗时，必须在后台线程进行[citation:1][citation:2]
        renderer = PdfRenderer(fileDescriptor)

        // 4. 打开指定页面（索引从 0 开始）
        page = renderer.openPage(pageIndex)

        // 5. 创建与页面尺寸一致的空白 Bitmap。使用 ARGB_8888 以获得较好质量
        val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)

        // 6. 将 Bitmap 背景填充为白色。PDF 背景可能是透明的，不填充会得到黑色背景
        bitmap.eraseColor(Color.WHITE)

        // 7. 执行渲染。RENDER_MODE_FOR_DISPLAY 适用于屏幕显示[citation:4][citation:6]
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        // return此bitmap，需要自己循环调用
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    } finally {
        // 8. 严格按顺序关闭资源：先关 Page，再关 Renderer，最后关文件描述符
        // 注意：关闭 Renderer 时，它会自动接管并关闭传入的 fileDescriptor[citation:1]
        page?.close()
        renderer?.close()
        // 如果 renderer 创建失败，才需要手动关闭 fileDescriptor
        if (renderer == null) {
            fileDescriptor?.close()
        }
    }
}