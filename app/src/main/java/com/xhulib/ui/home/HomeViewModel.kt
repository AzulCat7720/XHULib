package com.xhulib.ui.home

import androidx.annotation.StringRes
import com.xhulib.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.BookItem
import com.xhulib.data.model.LoanRecord
import com.xhulib.data.model.ReaderCard
import com.xhulib.data.model.ReaderOverview
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit

/** 剩余天数小于等于该天数时按「紧急」处理，首页用警示色显示。 */
internal const val URGENT_DAYS = 5L

/** 首页「当前借阅」卡片最多列出的条数，其余走「查看全部」。 */
internal const val MAX_LOAN_ROWS = 3

/**
 * 首页数据。
 *
 * 概览 / 证件 / 当前借阅 / 热门推荐四条数据各自独立加载，各占一个 [UiState]：
 * 任何一路失败或很慢，都不会拖住其它三路（热门推荐是公开页面，与登录态无关）。
 */
class HomeViewModel(private val repository: OpacRepository) : ViewModel() {

    private val _overview = MutableStateFlow<UiState<ReaderOverview>>(UiState.Loading)
    val overview: StateFlow<UiState<ReaderOverview>> = _overview.asStateFlow()

    private val _loans = MutableStateFlow<UiState<List<LoanRecord>>>(UiState.Loading)
    val loans: StateFlow<UiState<List<LoanRecord>>> = _loans.asStateFlow()

    private val _topLend = MutableStateFlow<UiState<List<BookItem>>>(UiState.Loading)
    val topLend: StateFlow<UiState<List<BookItem>>> = _topLend.asStateFlow()

    private val _refreshing = MutableStateFlow(false)

    /** 下拉刷新指示器是否可见。 */
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    /** 还在并发中的刷新轮数；用于避免「前一轮结束就提前收起指示器」。 */
    private var refreshRounds = 0

    init {
        refresh()
    }

    /** 整页刷新：三路并发重取，互不阻塞，全部结束后收起下拉指示器。 */
    fun refresh() {
        viewModelScope.launch {
            refreshRounds++
            _refreshing.value = true
            try {
                coroutineScope {
                    launch { loadOverview() }
                    launch { loadLoans() }
                    launch { loadTopLend() }
                }
            } finally {
                refreshRounds--
                if (refreshRounds <= 0) {
                    refreshRounds = 0
                    _refreshing.value = false
                }
            }
        }
    }

    fun retryOverview() {
        viewModelScope.launch { loadOverview() }
    }

    fun retryLoans() {
        viewModelScope.launch { loadLoans() }
    }

    fun retryTopLend() {
        viewModelScope.launch { loadTopLend() }
    }

    private suspend fun loadOverview() {
        _overview.value = loadState { repository.overview() }
    }

    private suspend fun loadLoans() {
        _loans.value = loadState { repository.currentLoans() }
    }

    private suspend fun loadTopLend() {
        _topLend.value = loadState { repository.topLend() }
    }
}

// ---------------------------------------------------------------------- 应还日期

/**
 * 页面上的日期文本 → [LocalDate]。
 *
 * OPAC 各页的日期写法不完全统一（`2025-05-20` / `2025/5/20` / `2025.5.20` /
 * `20250520`，有的还带时间），这里逐个试；都解析不了就返回 null，界面只显示原文。
 */
private val DATE_PATTERNS = listOf(
    DateTimeFormatter.ofPattern("yyyy-M-d"),
    DateTimeFormatter.ofPattern("yyyy/M/d"),
    DateTimeFormatter.ofPattern("yyyy.M.d"),
    DateTimeFormatter.ofPattern("yyyyMMdd"),
)

internal fun parseDueDate(raw: String): LocalDate? {
    if (raw.isBlank()) return null
    val dateOnly = raw.trim()
        .substringBefore(' ')
        .substringBefore('T')
        .replace('年', '-')
        .replace('月', '-')
        .replace("日", "")
        .trim()
    if (dateOnly.isEmpty()) return null
    for (pattern in DATE_PATTERNS) {
        try {
            return LocalDate.parse(dateOnly, pattern)
        } catch (_: DateTimeParseException) {
            // 换下一种写法继续试
        }
    }
    return null
}

/**
 * 距应还日期的剩余天数：正数=还剩几天，0=今天到期，负数=已超期几天。
 *
 * 日期解析不了时返回 null。
 */
internal fun remainingDays(dueDate: String, today: LocalDate = LocalDate.now()): Long? =
    parseDueDate(dueDate)?.let { ChronoUnit.DAYS.between(today, it) }

/**
 * 剩余天数的说明文案。
 *
 * 返回资源 ID 与格式化参数，由界面按当前语言解析 ——
 * 数据层不产生面向用户的文本，否则切换语言时这部分不会跟着变。
 */
internal data class RemainingLabel(@StringRes val res: Int, val days: Long)

internal fun remainingLabel(days: Long?): RemainingLabel? = when {
    days == null -> null
    days < 0 -> RemainingLabel(R.string.due_overdue_days, -days)
    days == 0L -> RemainingLabel(R.string.due_today, 0)
    else -> RemainingLabel(R.string.due_remaining_days, days)
}
