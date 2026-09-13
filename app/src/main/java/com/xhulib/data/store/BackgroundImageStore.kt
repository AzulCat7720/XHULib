package com.xhulib.data.store

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 自定义全局背景图片。
 *
 * 用户从相册选一张图后，**复制进 App 私有目录**再使用 —— 相册返回的 Uri
 * 只有临时读取权限，重启后就失效了，直接存 Uri 会导致下次启动背景图丢失。
 */
class BackgroundImageStore(private val context: Context) {

    private val file = File(context.applicationContext.filesDir, FILE_NAME)

    fun currentPath(): String? = file.takeIf { it.isFile }?.absolutePath

    /** 把选中的图片复制到私有目录，成功返回新路径。 */
    suspend fun save(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    fun clear() {
        runCatching { file.delete() }
    }

    companion object {
        private const val FILE_NAME = "custom_background.img"

        /**
         * 解码背景图。
         *
         * 相册里的原图动辄几千像素，直接整张读进内存很容易 OOM，
         * 因此先探尺寸再按需降采样。
         */
        fun load(path: String?, maxSize: Int = 1600): ImageBitmap? {
            if (path.isNullOrBlank()) return null
            return runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

                var sample = 1
                while (bounds.outWidth / sample > maxSize || bounds.outHeight / sample > maxSize) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
}
