package com.xhulib.ui.notice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.NoticeItem
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 消息通知（图书馆「信息发布」，公开页面，不需要登录）。
 *
 * 拉到新数据前一直保留旧列表，避免刷新时页面闪成空白。
 */
class NotificationsViewModel(private val repository: OpacRepository) : ViewModel() {

    private val _state = MutableStateFlow<UiState<List<NoticeItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<NoticeItem>>> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _refreshing.value = true
            try {
                _state.value = loadState { repository.notices() }
            } finally {
                _refreshing.value = false
            }
        }
    }
}
