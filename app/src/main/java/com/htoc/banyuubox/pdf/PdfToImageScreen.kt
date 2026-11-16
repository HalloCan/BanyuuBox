package com.htoc.banyuubox.pdf

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import java.io.File
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.htoc.banyuubox.ui.common.FeaturePageScaffold
import com.htoc.banyuubox.ui.theme.BanyuuBoxTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 预览中的一页图片。
 *
 * [id] 用于列表稳定定位；[pageIndex] 是它在原 PDF 中的页码。
 * 接入真实转换逻辑后，可以在这里补上 bitmap / uri 等字段。
 */
data class PageImage(
    val id: Long,
    val pageIndex: Int,
    val bitmap: Bitmap,
)

/** 保存格式。quality 仅对 JPEG / WebP 有效，PNG 无损压缩会忽略它。 */
enum class SaveFormat(val label: String, val compressFormat: Bitmap.CompressFormat) {
    PNG("PNG", Bitmap.CompressFormat.PNG),
    JPEG("JPEG", Bitmap.CompressFormat.JPEG),
    WEBP("WebP", Bitmap.CompressFormat.WEBP_LOSSY),
}

private val CARD_WIDTH = 112.dp
private val CARD_HEIGHT = 158.dp
private val CARD_SPACING = 12.dp

@Composable
fun PdfToImageScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var pages by remember { mutableStateOf<List<PageImage>>(emptyList()) } //默认预览界面为空
    var previewPage by remember { mutableStateOf<PageImage?>(null) }

    // 保存属性
    var saveFormat by remember { mutableStateOf(SaveFormat.PNG) }
    var quality by remember { mutableStateOf(90) }
    // 输出分辨率倍率（导入 PDF 时生效）
    var scale by remember { mutableStateOf(2) }

    val scope = rememberCoroutineScope()

    // 注册启动器
    val pickPdf = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        // 用户选完文件后回到这里；取消选择时 uri 为 null
        if (uri != null) {
            scope.launch {
                // 渲染 PDF 比较重，放到 IO 线程；完了回主线程更新 UI
                val bitmaps = withContext(Dispatchers.IO) { pdfLoader(context, uri, scale) }
                pages = bitmaps?.mapIndexed { index, bmp ->
                    PageImage(id = index.toLong(), pageIndex = index + 1, bitmap = bmp)
                } ?: emptyList()
            }
        }
    }

    FeaturePageScaffold(
        title = "PDF 转图片",
        onBack = onBack,
        actionLabel = "保存图片",
        actionEnabled = pages.isNotEmpty(),
        onAction = {
            pages.forEachIndexed { index, page ->
                saveBitmapToGallery(
                    context, page.bitmap, "pdf_page_${index + 1}",
                    saveFormat.compressFormat, quality,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "输出分辨率", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = scale.toFloat(),
                    onValueChange = { scale = it.toInt() },
                    valueRange = 1f..5f,
                    steps = 3,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "×$scale",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "导入 PDF 时按此倍率渲染，每页图像为原始页面的 $scale 倍",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(20.dp))
            SaveOptionsCard(
                format = saveFormat,
                onFormatChange = { saveFormat = it },
                quality = quality,
                onQualityChange = { quality = it },
            )
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
            onSave = {
                saveBitmapToGallery(
                    context, page.bitmap, "pdf_image",
                    saveFormat.compressFormat, quality,
                )
            },
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
            Image(
                bitmap = page.bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
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

/**
 * 保存属性设置卡片：图片格式 + 质量。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveOptionsCard(
    format: SaveFormat,
    onFormatChange: (SaveFormat) -> Unit,
    quality: Int,
    onQualityChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text(
                text = "保存设置",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(12.dp))

            Text(text = "图片格式", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SaveFormat.entries.forEachIndexed { index, item ->
                    SegmentedButton(
                        selected = format == item,
                        onClick = { onFormatChange(item) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = SaveFormat.entries.size,
                        ),
                    ) {
                        Text(text = item.label)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                // PNG 无损，质量无意义，视觉上淡出提示
                modifier = Modifier.alpha(if (format == SaveFormat.PNG) 0.4f else 1f),
            ) {
                Text(text = "质量", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.width(12.dp))
                Slider(
                    value = quality.toFloat(),
                    onValueChange = { onQualityChange(it.toInt()) },
                    valueRange = 1f..100f,
                    enabled = format != SaveFormat.PNG,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$quality",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (format == SaveFormat.PNG) {
                Text(
                    text = "PNG 为无损格式，不使用质量参数",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PagePreviewDialog(
    page: PageImage,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "第 ${page.pageIndex} 页") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = page.placeholderColor()),
                    modifier = Modifier.size(width = 220.dp, height = 310.dp),
                ) {
                    Image(
                        bitmap = page.bitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
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
            TextButton(onClick = onSave) { Text("保存本张") }
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

fun pdfLoader(context: Context, uri: Uri, scale: Int): ArrayList<Bitmap>? {
    // 1.将pdf拷贝到应用缓存目录
    val file: File? = uriToFile(context, uri)
    val bitmaps = renderPdfPageToBitmap(file, scale)
    Log.d("PdfToImageScreen", "Bitmaps length:" + bitmaps?.size)
    return bitmaps
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
fun renderPdfPageToBitmap(pdfFile: File?, scale: Int = 1): ArrayList<Bitmap>? {
    var fileDescriptor: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    var page: PdfRenderer.Page? = null

    return try {
        // 2. 以只读方式打开文件，获取可随机访问的描述符
        fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)

        // 3. 创建 PdfRenderer。注意：构造过程可能耗时，必须在后台线程进行[citation:1][citation:2]
        renderer = PdfRenderer(fileDescriptor)

        // 4.创建一个包含所有bitmap的列表
        val pageCount = renderer.getPageCount()
        val bitmaps = ArrayList<Bitmap>(pageCount)

        // 5. 循环所有页面（索引从开始）
        repeat(pageCount){ index ->
            page = renderer.openPage(index)

            // 6. 创建空白 Bitmap，尺寸 = 原始页面尺寸 × scale（输出分辨率倍率）
            val bitmap = Bitmap.createBitmap(page.width * scale, page.height * scale, Bitmap.Config.ARGB_8888)

            // 7. 将 Bitmap 背景填充为白色。PDF 背景可能是透明的，不填充会得到黑色背景
            bitmap.eraseColor(Color.WHITE)

            // 8. 执行渲染。transform 把 PDF 页面坐标缩放到放大后的画布上
            val matrix = Matrix().apply { postScale(scale.toFloat(), scale.toFloat()) }
            page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

            // 9.存数组
            bitmaps.add(bitmap)
        }
        pdfFile?.delete()
        // return此bitmap array
        bitmaps
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

fun saveBitmapToGallery(
    context: Context,
    bitmap: Bitmap,
    displayName: String,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG,
    quality: Int = 100,
) {
    val extension = when (format) {
        Bitmap.CompressFormat.JPEG -> "jpg"
        Bitmap.CompressFormat.WEBP_LOSSY, Bitmap.CompressFormat.WEBP_LOSSLESS -> "webp"
        else -> "png"
    }
    val mimeType = when (format) {
        Bitmap.CompressFormat.JPEG -> "image/jpeg"
        Bitmap.CompressFormat.WEBP_LOSSY, Bitmap.CompressFormat.WEBP_LOSSLESS -> "image/webp"
        else -> "image/png"
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.$extension")
        put(MediaStore.Images.Media.MIME_TYPE, mimeType)
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BanyuuBox")  // 相册里的目录
        put(MediaStore.Images.Media.IS_PENDING, 1)  // 写完前先不让别的 App 看到
    }
    val uri = context.contentResolver
        .insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: return

    context.contentResolver.openOutputStream(uri)?.use { out ->
        bitmap.compress(format, quality, out)
    }

    values.clear()
    values.put(MediaStore.Images.Media.IS_PENDING, 0)  // 写完了，对相册可见
    Toast.makeText(context, "保存成功！", Toast.LENGTH_SHORT).show()
    context.contentResolver.update(uri, values, null, null)
}