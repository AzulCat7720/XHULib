package com.xhulib.data.store

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** 缓存下来的一页 HTML 及其抓取时间。 */
data class CachedPage(
    val html: String,
    val savedAt: Long,
)

/**
 * 页面级磁盘缓存。
 *
 * 缓存的是**原始 HTML** 而不是解析后的模型——省掉给每个数据类写序列化，
 * 解析逻辑改了解析器就自动生效，不用管旧缓存格式。
 *
 * 存在 App 私有目录（`filesDir`）下，卸载即清除。读者页面里含个人信息，
 * 因此退出登录时会连同缓存一起清掉。
 *
 * 抓取时间直接用文件的 `lastModified`，不再维护额外的索引文件。
 */
class PageCache(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "page-cache").apply { mkdirs() }

    private fun fileFor(key: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(key.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$digest.html")
    }

    fun put(key: String, html: String) {
        if (html.isBlank()) return
        runCatching {
            val file = fileFor(key)
            file.writeText(html)
            file.setLastModified(System.currentTimeMillis())
        }
    }

    fun get(key: String): CachedPage? = runCatching {
        val file = fileFor(key)
        if (!file.isFile || file.length() == 0L) return null
        CachedPage(html = file.readText(), savedAt = file.lastModified())
    }.getOrNull()

    fun has(key: String): Boolean = runCatching { fileFor(key).isFile }.getOrDefault(false)

    /** 最近一次成功缓存的页面的时间，用于「离线数据 · 更新于 …」。 */
    fun latestSavedAt(): Long? =
        dir.listFiles()?.maxOfOrNull { it.lastModified() }?.takeIf { it > 0L }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    fun sizeBytes(): Long = runCatching {
        dir.listFiles()?.sumOf { it.length() } ?: 0L
    }.getOrDefault(0L)
}
