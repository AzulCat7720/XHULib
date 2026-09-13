package com.xhulib.ui.mine

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.AppContainer
import com.xhulib.data.model.ReaderCard
import com.xhulib.data.model.ReaderOverview
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 「我的」页的数据。
 *
 * [card] 是页面的主数据（证件信息大卡片）；[overview] 与两个计数只是行尾的
 * 角标，取不到就不显示，不影响整页。
 */
data class MineData(
    val card: ReaderCard,
    val overview: ReaderOverview? = null,
    val holdCount: Int? = null,
    val delegateCount: Int? = null,
)

/**
 * 「我的」页 ViewModel。
 *
 * 只读：只调用 [com.xhulib.data.OpacRepository] 的读取接口，不做任何写操作。
 */
class MineViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

    /** 顶部显示当前账号（形如 `****0000`）。本地读取，不需要网络。 */
    val accountName: String? = container.accountStore.currentAccount()?.displayName

    private val _state = MutableStateFlow<UiState<MineData>>(UiState.Loading)
    val state: StateFlow<UiState<MineData>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = loadState {
                coroutineScope {
                    // 证件信息是整页的关键数据：它失败（含会话失效）时整页显示错误态。
                    val card = async { repository.readerCard() }
                    // 概览与两个计数是可选角标，各自失败就当没有。
                    val overview = async { optional { repository.overview() } }
                    val holds = async { optional { repository.holds().size } }
                    val delegates = async { optional { repository.delegates().size } }
                    MineData(
                        card = card.await(),
                        overview = overview.await(),
                        holdCount = holds.await(),
                        delegateCount = delegates.await(),
                    )
                }
            }
        }
    }

    /** 可选数据：失败返回 null，但协程取消必须继续向上抛。 */
    private suspend fun <T> optional(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }
}
