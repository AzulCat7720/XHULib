package com.xhulib.ui.search

import androidx.annotation.StringRes
import com.xhulib.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.data.OpacRepository
import com.xhulib.data.store.SearchHistoryStore
import com.xhulib.data.model.BookItem
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 检索项（对应 OPAC 的 `strSearchType`）。
 */
enum class SearchField(val value: String, @StringRes val labelRes: Int) {
    TITLE("title", R.string.search_field_title),
    AUTHOR("author", R.string.field_author),
    KEYWORD("keyword", R.string.search_field_keyword),
    ISBN("isbn", R.string.search_field_isbn),
    PUBLISHER("publisher", R.string.field_publisher),
    CALL_NO("callno", R.string.field_call_no),
}

/**
 * 匹配方式（对应 OPAC 的 `match_flag`）。
 */
enum class MatchFlag(val value: String, @StringRes val labelRes: Int) {
    FORWARD("forward", R.string.search_match_forward),
    FULL("full", R.string.search_match_full),
    ANY("any", R.string.search_match_any),
}

/**
 * 书目检索页的状态。
 *
 * 检索历史只保留在内存里（本次会话内有效）：仓库的 `searchHistory()`
 * 需要登录，属于「我的图书馆」，这里不使用。
 */
class SearchViewModel(
    private val repository: OpacRepository,
    private val searchHistory: SearchHistoryStore,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _field = MutableStateFlow(SearchField.TITLE)
    val field: StateFlow<SearchField> = _field.asStateFlow()

    private val _matchFlag = MutableStateFlow(MatchFlag.FORWARD)
    val matchFlag: StateFlow<MatchFlag> = _matchFlag.asStateFlow()

    /** null 表示「还没搜过」，界面显示引导文案。 */
    private val _result = MutableStateFlow<UiState<List<BookItem>>?>(null)
    val result: StateFlow<UiState<List<BookItem>>?> = _result.asStateFlow()

    /** 最近一次提交的检索词，用于结果标题。 */
    private val _submittedQuery = MutableStateFlow("")
    val submittedQuery: StateFlow<String> = _submittedQuery.asStateFlow()

    /** 本次会话内的检索记录。 */
    /** 本地持久化的最近检索词（关掉 App 也不丢）。 */
    val history: StateFlow<List<String>> = searchHistory.history

    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun clearQuery() {
        _query.value = ""
    }

    /** 切换检索项；已经搜过就按新条件重搜一次。 */
    fun onFieldChange(value: SearchField) {
        if (_field.value == value) return
        _field.value = value
        researchIfAlreadySearched()
    }

    /** 切换匹配方式；已经搜过就按新条件重搜一次。 */
    fun onMatchFlagChange(value: MatchFlag) {
        if (_matchFlag.value == value) return
        _matchFlag.value = value
        researchIfAlreadySearched()
    }

    /** 提交检索；检索词为空时什么都不做。 */
    fun search() {
        val text = _query.value.trim()
        if (text.isEmpty()) return

        _submittedQuery.value = text
        searchHistory.add(text)

        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _result.value = UiState.Loading
            _result.value = loadState {
                repository.search(
                    query = text,
                    searchType = _field.value.value,
                    matchFlag = _matchFlag.value.value,
                )
            }
        }
    }

    fun retry() = search()

    fun removeHistory(text: String) {
        searchHistory.remove(text)
    }

    fun clearHistory() {
        searchHistory.clear()
    }

    /** 点历史记录：填回搜索框并立即检索。 */
    fun useHistory(text: String) {
        _query.value = text
        search()
    }

    private fun researchIfAlreadySearched() {
        if (_result.value != null) search()
    }
}
