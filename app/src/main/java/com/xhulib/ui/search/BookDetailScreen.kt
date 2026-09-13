package com.xhulib.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.xhulib.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.LabeledValue
import com.xhulib.data.parse.CatalogParsers
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 馆藏表的列宽；列多时整表横向滚动。 */
private val HoldingCellWidth = 132.dp

/** 「标签 / 值」两列里标签列的宽度。 */
private val FieldLabelWidth = 92.dp

/**
 * 书目详情页。
 *
 * @param marcUrl 检索结果里的相对地址（如 `item.php?marc_no=…`），原样交给仓库补全。
 * @param onBack 返回上一页。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(marcUrl: String, onBack: () -> Unit) {
    val viewModel = containerViewModel(key = marcUrl) {
        BookDetailViewModel(repository = it.repository, marcUrl = marcUrl)
    }
    val state by viewModel.state.collectAsState()

    val detail = (state as? UiState.Success)?.data
    val barTitle = detail?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.book_detail_title)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = barTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            StateContent(
                state = state,
                onRetry = { viewModel.retry() },
                onRelogin = { viewModel.retry() },
                modifier = Modifier.fillMaxSize(),
            ) { book ->
                DetailContent(detail = book)
            }
        }
    }
}

/** 图书详情的数据：进入页面即加载，失败可重试。 */
internal class BookDetailViewModel(
    private val repository: OpacRepository,
    private val marcUrl: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<CatalogParsers.BookDetail>>(UiState.Loading)
    val state: StateFlow<UiState<CatalogParsers.BookDetail>> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = loadState { repository.bookDetail(marcUrl) }
        }
    }
}

@Composable
private fun DetailContent(
    detail: CatalogParsers.BookDetail,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        if (detail.title.isNotBlank()) {
            item(key = "detail-title") {
                Text(
                    text = detail.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        if (detail.fields.isNotEmpty()) {
            item(key = "fields-header") { SectionHeader(stringResource(R.string.book_detail_fields)) }
            itemsIndexed(
                items = detail.fields,
                key = { index, field -> "field-$index-${field.label}" },
            ) { index, field ->
                FieldRow(field = field)
                if (index != detail.fields.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }

        if (detail.holdings.isNotEmpty()) {
            item(key = "holdings-header") { SectionHeader(stringResource(R.string.book_detail_holdings)) }
            item(key = "holdings-table") {
                HoldingsTable(
                    header = detail.holdingsHeader,
                    rows = detail.holdings,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        if (detail.fields.isEmpty() && detail.holdings.isEmpty()) {
            item(key = "detail-empty") {
                Text(
                    text = stringResource(R.string.book_detail_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** 「标签 / 值」两列中的一行。 */
@Composable
private fun FieldRow(field: LabeledValue, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = field.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(FieldLabelWidth),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = field.value.ifBlank { "—" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 馆藏表：列数按 [header] 对齐（header 为空时按最宽的一行对齐），
 * 缺列的行补空格；列多放不下时整表横向滚动。
 */
@Composable
private fun HoldingsTable(
    header: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
) {
    val columnCount = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    if (columnCount == 0) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        if (header.isNotEmpty()) {
            HoldingRow(cells = header, columnCount = columnCount, isHeader = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        rows.forEachIndexed { index, row ->
            HoldingRow(cells = row, columnCount = columnCount, isHeader = false)
            if (index != rows.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun HoldingRow(
    cells: List<String>,
    columnCount: Int,
    isHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        for (column in 0 until columnCount) {
            Text(
                text = cells.getOrNull(column).orEmpty(),
                style = if (isHeader) {
                    MaterialTheme.typography.labelLarge
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (isHeader) FontWeight.SemiBold else null,
                color = if (isHeader) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(HoldingCellWidth)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}
