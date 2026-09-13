package com.xhulib.data.store

import android.content.Context
import android.content.SharedPreferences
import com.xhulib.data.net.CryptoBox

/**
 * 敏感键值存储：值经 Android Keystore 的 AES/GCM 加密后落盘。
 *
 * 只用于会话 Cookie、账号密码这类东西；普通偏好设置请直接用 SharedPreferences。
 */
class SecurePrefs(
    context: Context,
    private val crypto: CryptoBox = CryptoBox(),
    name: String = "xhulib_secure",
) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)

    fun putString(key: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(key).apply()
        } else {
            prefs.edit().putString(key, crypto.encrypt(value)).apply()
        }
    }

    /** 解密失败（密钥失效、数据损坏）时返回 null 并顺手清理，调用方按未登录处理。 */
    fun getString(key: String): String? {
        val blob = prefs.getString(key, null) ?: return null
        val plain = crypto.decrypt(blob)
        if (plain == null) prefs.edit().remove(key).apply()
        return plain
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    /** 清空所有以 [prefix] 开头的键。 */
    fun removeByPrefix(prefix: String) {
        prefs.edit().apply {
            prefs.all.keys.filter { it.startsWith(prefix) }.forEach { remove(it) }
        }.apply()
    }

    fun contains(key: String): Boolean = prefs.contains(key)
}
