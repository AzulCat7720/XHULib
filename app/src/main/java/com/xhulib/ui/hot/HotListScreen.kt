package com.xhulib.ui.hot

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.R
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.BookItem
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 热门推荐。
 *
 * 站点上这个栏目有五个标签：热门借阅 / 热门评分 / 热门收藏 / 热门图书 / 借阅关系图。
 * 前四个都是可解析的排行表，这里做成四个标签页；「借阅关系图」是纯 JS 可视化、
 * 没有可解析的数据表，故不做。
 *
 * 每个标签的排行数字含义不同（借阅册次 / 评价人次 / 收藏人次 / 浏览次数），
 * 解析时记进 `BookItem.metricLabel`，界面上照实显示。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotListScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit) {
    val viewModel: HotListViewModel = containerViewModel { HotListViewModel(it.repository) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val kinds = OpacRepository.HotList.entries
    val current = kinds.getOrElse(selectedTab) { kinds.first() }

    LaunchedEffect(current) { viewModel.ensureLoaded(current) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.hot_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                kinds.forEachIndexed { index, kind ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(kind.labelRes)) },
                    )
                }
            }

            val state by viewModel.state(current).collectAsState()
            StateContent(
                state = state,
                onRetry = { viewModel.reload(current) },
                onRelogin = {},
                modifier = Modifier.fillMaxSize(),
            ) { list ->
                if (list.isEmpty()) {
                    EmptyView(text = stringResource(R.string.empty_record))
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        itemsIndexed(
                            items = list,
                            key = { index, item -> "$index-${item.title}-${item.callNo}" },
                        ) { index, item ->
                            HotRow(rank = index + 1, item = item, onOpenBook = onOpenBook)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HotRow(rank: Int, item: BookItem, onOpenBook: (String) -> Unit) {
    Surface(
        onClick = { item.detailUrl?.let(onOpenBook) },
        enabled = !item.detailUrl.isNullOrBlank(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp)) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.width(28.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.publication.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.publication,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.callNo.isNotBlank()) {
                        Text(
                            text = item.callNo,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    if (item.loanCount.isNotBlank()) {
                        Text(
                            text = "${item.metricLabel.ifBlank { stringResource(R.string.hot_default_metric) }} ${item.loanCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** 四个标签各自一份状态，互不阻塞；首次进入才加载。 */
class HotListViewModel(private val repository: OpacRepository) : ViewModel() {

    private val cache = mutableMapOf<OpacRepository.HotList, MutableStateFlow<UiState<List<BookItem>>>>()

    fun state(kind: OpacRepository.HotList): StateFlow<UiState<List<BookItem>>> =
        cache.getOrPut(kind) { MutableStateFlow(UiState.Loading) }.asStateFlow()

    fun ensureLoaded(kind: OpacRepository.HotList) {
        val flow = cache.getOrPut(kind) { MutableStateFlow(UiState.Loading) }
        if (flow.value is UiState.Success) return
        load(kind, flow)
    }

    fun reload(kind: OpacRepository.HotList) {
        load(kind, cache.getOrPut(kind) { MutableStateFlow(UiState.Loading) })
    }

    private fun load(kind: OpacRepository.HotList, flow: MutableStateFlow<UiState<List<BookItem>>>) {
        viewModelScope.launch {
            flow.value = UiState.Loading
            flow.value = loadState { repository.hotList(kind) }
        }
    }
}
