package com.htoc.banyuubox.utils.update

import android.content.Context

/**
 * 检查更新的结果。null 表示暂无新版本。
 */
data class UpdateInfo(
    val versionName: String,
    val downloadUrl: String,
    val releaseNotes: String = "",
)

/**
 * 检查更新入口，负责与更新服务端通信、比对版本。
 * 逻辑留空，由你后续实现。
 */
object UpdateChecker {

    /**
     * 检查是否有新版本。
     *
     * 实现提示：请求你的更新接口，与当前 versionName 比对；
     * 有更新回调 [UpdateInfo]，没有更新回调 null。
     * 回调可以异步发生，但请保证最终会调用一次 [onResult]。
     */
    fun checkForUpdates(context: Context, onResult: (UpdateInfo?) -> Unit) {
        // TODO: 在这里实现检查更新逻辑
        onResult(null)
    }
}
