package com.xhulib.ui.notice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.R
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.BulletinDetail
import com.xhulib.ui.common.LoadingBox
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通知详情。
 *
 * 对应信息发布里各个分类挂的公告页（如 `preg_arri_bulletin.php`）。
 * 馆方对「没有记录」和「有记录」用两套版式：前者是一句提示，后者是一张表格。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoticeDetailScreen(title: String, path: String, onBack: () -> Unit) {
    val viewModel: NoticeDetailViewModel = containerViewModel(key = "notice:$path") {
        NoticeDetailViewModel(it.repository, path)
    }
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { stringResource(R.string.notice_detail_title) }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        StateContent(
            state = state,
            onRetry = viewModel::refresh,
            onRelogin = {},
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { detail ->
            BulletinBody(detail)
        }
    }
}

@Composable
private fun BulletinBody(detail: BulletinDetail) {
    when {
        detail.message.isNotBlank() -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = detail.message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }

        detail.rows.isNotEmpty() -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(detail.rows) { index, row ->
                BulletinRow(headers = detail.headers, cells = row)
                if (index != detail.rows.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        else -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.notice_detail_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** 表格的一行：按列名逐项列出，列名缺失时用「第 N 项」兜底。 */
@Composable
private fun BulletinRow(headers: List<String>, cells: List<String>) {
    Column(Modifier.fillMaxWidth()) {
        cells.forEachIndexed { index, value ->
            if (value.isBlank()) return@forEachIndexed
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = headers.getOrNull(index)?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.notice_field_index, index + 1),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(88.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

class NoticeDetailViewModel(
    private val repository: OpacRepository,
    private val path: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<BulletinDetail>>(UiState.Loading)
    val state: StateFlow<UiState<BulletinDetail>> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = loadState { repository.noticeDetail(path) }
        }
    }
}
