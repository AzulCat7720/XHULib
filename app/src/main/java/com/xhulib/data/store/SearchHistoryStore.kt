package com.xhulib.data.store

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 本地检索历史。
 *
 * 只记**关键词**，不记检索项与匹配方式，也不涉及任何账号信息，
 * 因此用普通 SharedPreferences 明文存即可（卸载即清除）。
 *
 * 图书馆系统自己也有「检索历史」，但那份要登录才能看、且是服务端记的流水；
 * 这份是给搜索框做「最近搜过」用的，二者互不影响。
 */
class SearchHistoryStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("xhulib_search", Context.MODE_PRIVATE)

    private val _history = MutableStateFlow(load())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private fun load(): List<String> =
        prefs.getString(KEY, null)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toList()
            .orEmpty()

    /** 记一条；重复的提到最前，超出上限的丢弃。 */
    fun add(query: String) {
        val text = query.trim()
        if (text.isEmpty()) return
        save((listOf(text) + _history.value.filterNot { it.equals(text, ignoreCase = true) }).take(MAX))
    }

    fun remove(query: String) {
        save(_history.value.filterNot { it == query })
    }

    fun clear() {
        save(emptyList())
    }

    private fun save(list: List<String>) {
        prefs.edit().putString(KEY, list.joinToString("\n")).apply()
        _history.value = list
    }

    private companion object {
        const val KEY = "recent_queries"
        const val MAX = 20
    }
}
