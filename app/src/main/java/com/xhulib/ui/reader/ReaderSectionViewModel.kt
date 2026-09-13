package com.xhulib.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.AppContainer
import com.xhulib.data.Opac
import com.xhulib.data.model.AsordEntry
import com.xhulib.data.model.CreditEntry
import com.xhulib.data.model.DelegateEntry
import com.xhulib.data.model.HoldEntry
import com.xhulib.data.model.LoanRecord
import com.xhulib.data.model.SearchHistoryEntry
import com.xhulib.data.model.ShelfItem
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.loadState
import com.xhulib.ui.nav.ReaderSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「我的」页某个栏目拿到的数据。
 *
 * 每个栏目一种形态，界面按形态渲染；[Empty] 与 [WebOnly] 是三个「本应用不展示
 * 具体条目」的栏目（我的书评 / 书刊遗失 / 账目清单）的两种结果。
 */
sealed interface ReaderSectionData {

    /** 当前借阅 / 借阅历史。 */
    data class Loans(val items: List<LoanRecord>) : ReaderSectionData

    /** 我的书架 / 我的课程。 */
    data class Shelves(val items: List<ShelfItem>) : ReaderSectionData

    /** 荐购历史。 */
    data class Asord(val items: List<AsordEntry>) : ReaderSectionData

    /** 预约信息。 */
    data class Holds(val items: List<HoldEntry>) : ReaderSectionData

    /** 委托信息。 */
    data class Delegates(val items: List<DelegateEntry>) : ReaderSectionData

    /** 我的积分。 */
    data class Credits(val items: List<CreditEntry>) : ReaderSectionData

    /** 检索历史。 */
    data class Searches(val items: List<SearchHistoryEntry>) : ReaderSectionData

    /** 服务端返回「您的该项记录为空！」。 */
    data object Empty : ReaderSectionData

    /** 该栏目有内容，但本应用不解析/不展示，引导去网页端。 */
    data object WebOnly : ReaderSectionData
}

/**
 * 通用栏目页 ViewModel：按 [section] 决定调哪个仓库接口。
 *
 * 第一版只读，不提供续借 / 取消预约 / 取消委托等写操作。
 */
class ReaderSectionViewModel(
    container: AppContainer,
    private val section: ReaderSection,
) : ViewModel() {

    private val repository = container.repository

    private val _state = MutableStateFlow<UiState<ReaderSectionData>>(UiState.Loading)
    val state: StateFlow<UiState<ReaderSectionData>> = _state.asStateFlow()

    /** 服务端是分页的（借阅历史、积分、检索历史、荐购历史），还有下一页时为 true。 */
    private val _hasMore = MutableStateFlow(false)
    val hasMore: StateFlow<Boolean> = _hasMore.asStateFlow()

    private val _loadingMore = MutableStateFlow(false)
    val loadingMore: StateFlow<Boolean> = _loadingMore.asStateFlow()

    private var currentPage = 1
    private var totalPages = 1

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            currentPage = 1
            totalPages = 1
            _hasMore.value = false
            _state.value = loadState { loadPage(1) }
            _hasMore.value = currentPage < totalPages
        }
    }

    /** 追加下一页；失败时保留已加载的内容，只是不再往下翻。 */
    fun loadMore() {
        if (!_hasMore.value || _loadingMore.value) return
        val loaded = (_state.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            _loadingMore.value = true
            try {
                val next = currentPage + 1
                val more = loadPage(next)
                _state.value = UiState.Success(append(loaded, more))
                _hasMore.value = currentPage < totalPages
            } catch (e: Exception) {
                _hasMore.value = false
            } finally {
                _loadingMore.value = false
            }
        }
    }

    private suspend fun loadPage(page: Int): ReaderSectionData = when (section) {
        // 当前借阅通常只有几本，服务端也不分页
        ReaderSection.CurrentLoans -> ReaderSectionData.Loans(repository.currentLoans())

        ReaderSection.LoanHistory -> repository.loanHistory(page).let {
            totalPages = it.totalPages
            currentPage = page
            ReaderSectionData.Loans(it.items)
        }

        ReaderSection.Bookshelf -> ReaderSectionData.Shelves(repository.bookshelf())
        ReaderSection.Courses -> ReaderSectionData.Shelves(repository.courses())

        ReaderSection.AsordHistory -> repository.asordHistory(page).let {
            totalPages = it.totalPages
            currentPage = page
            ReaderSectionData.Asord(it.items)
        }

        ReaderSection.Holds -> ReaderSectionData.Holds(repository.holds())
        ReaderSection.Delegates -> ReaderSectionData.Delegates(repository.delegates())

        ReaderSection.Credits -> repository.credits(page).let {
            totalPages = it.totalPages
            currentPage = page
            ReaderSectionData.Credits(it.items)
        }

        ReaderSection.SearchHistory -> repository.searchHistory(page).let {
            totalPages = it.totalPages
            currentPage = page
            ReaderSectionData.Searches(it.items)
        }

        ReaderSection.Reviews -> emptyOrWebOnly(Opac.READER_REVIEWS)
        ReaderSection.BookLoss -> emptyOrWebOnly(Opac.READER_BOOK_LOSS)
        ReaderSection.Account -> emptyOrWebOnly(Opac.READER_ACCOUNT)
    }

    /** 把新一页拼到已加载的内容后面。 */
    private fun append(old: ReaderSectionData, more: ReaderSectionData): ReaderSectionData = when {
        old is ReaderSectionData.Loans && more is ReaderSectionData.Loans ->
            ReaderSectionData.Loans(old.items + more.items)

        old is ReaderSectionData.Asord && more is ReaderSectionData.Asord ->
            ReaderSectionData.Asord(old.items + more.items)

        old is ReaderSectionData.Credits && more is ReaderSectionData.Credits ->
            ReaderSectionData.Credits(old.items + more.items)

        old is ReaderSectionData.Searches && more is ReaderSectionData.Searches ->
            ReaderSectionData.Searches(old.items + more.items)

        else -> old
    }

    /**
     * 我的书评 / 书刊遗失 / 账目清单：只判断有没有内容。
     *
     * 有内容也不在手机端展示（列结构未验证），引导用户去网页端。
     */
    private suspend fun emptyOrWebOnly(path: String): ReaderSectionData =
        if (repository.isEmptySection(path)) ReaderSectionData.Empty else ReaderSectionData.WebOnly
}
