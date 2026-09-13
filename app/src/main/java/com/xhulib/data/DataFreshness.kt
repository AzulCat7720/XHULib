package com.xhulib.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 数据新鲜度。
 *
 * 离线缓存生效时（网络不通、退回本地缓存）把 [offline] 置为 true，
 * 根组件据此在顶部显示一条「离线数据」提示条，避免用户把旧数据当成实时数据。
 * 任意一次真实网络请求成功即恢复为在线。
 */
object DataFreshness {

    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    private val _cachedAt = MutableStateFlow<Long?>(null)

    /** 当前展示的离线数据是何时抓的；在线时为 null。 */
    val cachedAt: StateFlow<Long?> = _cachedAt.asStateFlow()

    fun markOnline() {
        _offline.value = false
        _cachedAt.value = null
    }

    fun markOffline(savedAt: Long) {
        _offline.value = true
        _cachedAt.value = savedAt
    }

    fun reset() {
        _offline.value = false
        _cachedAt.value = null
    }
}
