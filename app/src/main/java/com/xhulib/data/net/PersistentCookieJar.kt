package com.xhulib.data.net

import com.xhulib.data.store.SecurePrefs
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * 把 Cookie 加密持久化到本地的 [CookieJar]，用于「重启后免登录」。
 *
 * OPAC 的登录态就是一个 PHPSESSID，服务端会话存活时间很短，
 * 所以这里存的是一份「可能已经过期」的凭证——启动时先拿它试一次，
 * 失败再走重新登录流程（见 SessionManager）。
 *
 * 存储格式：每行 `url \t cookie.toString()`，便于用 Cookie.parse 还原。
 */
class PersistentCookieJar(
    private val prefs: SecurePrefs,
    private val storageKey: String,
) : CookieJar {

    private val memory = linkedMapOf<String, MutableList<Cookie>>()

    init {
        restore()
    }

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val bucket = memory.getOrPut(url.host) { mutableListOf() }
        cookies.forEach { fresh ->
            bucket.removeAll { it.name == fresh.name && it.path == fresh.path }
            if (fresh.expiresAt > System.currentTimeMillis()) bucket.add(fresh)
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val bucket = memory[url.host] ?: return emptyList()
        bucket.removeAll { it.expiresAt <= now }
        return bucket.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        memory.clear()
        prefs.remove(storageKey)
    }

    @Synchronized
    fun isEmpty(): Boolean = memory.values.all { it.isEmpty() }

    private fun persist() {
        val text = buildString {
            memory.values.flatten().forEach { cookie ->
                append(cookie.domain)
                append('\t')
                append(cookie.toString())
                append('\n')
            }
        }
        prefs.putString(storageKey, text)
    }

    private fun restore() {
        val text = prefs.getString(storageKey) ?: return
        text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
            val parts = line.split('\t', limit = 2)
            if (parts.size != 2) return@forEach
            val host = parts[0].removePrefix(".")
            val url = "http://$host/".toHttpUrlOrNull() ?: return@forEach
            val cookie = Cookie.parse(url, parts[1]) ?: return@forEach
            memory.getOrPut(host) { mutableListOf() }.add(cookie)
        }
    }
}
