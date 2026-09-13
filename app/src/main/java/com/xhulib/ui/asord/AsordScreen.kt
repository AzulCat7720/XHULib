package com.xhulib.ui.asord

import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.R
import com.xhulib.data.Opac
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.AsordCatalogItem
import com.xhulib.data.model.AsordEntry
import com.xhulib.data.model.LabeledValue
import com.xhulib.ui.book.ClassDropdown
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 「读者荐购」专区里的三个子标签，顺序与图书馆网站一致。 */
internal enum class AsordTab(@StringRes val labelRes: Int) {
    History(R.string.asord_tab_history),
    Submit(R.string.asord_tab_submit),
    Catalog(R.string.asord_tab_catalog),
}

/**
 * 读者荐购（只读）。
 *
 * 对应图书馆网站的 `asord/asord_hist.php` —— 该页是「荐购」专区，
 * 顶部有三个子标签：荐购历史 / 读者荐购 / 新书目录推荐，这里原样还原，
 * 默认停在「荐购历史」，与直接打开 `asord_hist.php` 看到的一致。
 *
 * 其中「读者荐购」是提交表单（`asord_redr.php`，且需要登录），属写操作，
 * 按第一版「纯只读」的约定只做提示，引导到图书馆网站办理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AsordScreen(onBack: () -> Unit) {
    val viewModel: AsordViewModel = containerViewModel { AsordViewModel(it.repository) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val current = AsordTab.entries.getOrElse(selectedTab) { AsordTab.History }

    // 每个标签首次进入才加载
    LaunchedEffect(current) { viewModel.ensureLoaded(current) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.asord_title)) },
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
                AsordTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }

            when (current) {
                AsordTab.History -> HistoryPane(viewModel)
                AsordTab.Submit -> SubmitPane()
                AsordTab.Catalog -> CatalogPane(viewModel)
            }
        }
    }
}

// ------------------------------------------------------------------ 荐购历史

@Composable
private fun HistoryPane(viewModel: AsordViewModel) {
    val state by viewModel.history.collectAsState()
    val hasMore by viewModel.historyHasMore.collectAsState()
    StateContent(
        state = state,
        onRetry = viewModel::reloadHistory,
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
                    key = { index, item -> "$index-${item.title}-${item.date}" },
                ) { _, entry -> HistoryRow(entry) }

                if (hasMore) {
                    item(key = "__more__") {
                        LoadMoreFooter(onClick = viewModel::loadMoreHistory)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(entry: AsordEntry) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (entry.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.publication.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.publication,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (entry.status.isNotBlank()) {
                    Text(
                        text = entry.status,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                if (entry.date.isNotBlank()) {
                    Text(
                        text = entry.date,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.asord_note, entry.note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 读者荐购（提交）

/** 提交荐购是写操作，且需要登录，这里只做引导。 */
@Composable
private fun SubmitPane() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.asord_submit_notice),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = Opac.WEB_PORTAL,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(Opac.WEB_PORTAL)),
                    )
                }
            },
        ) {
            Icon(Icons.Filled.OpenInNew, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.open_in_browser))
        }
    }
}

// ------------------------------------------------------------------ 新书目录推荐

@Composable
private fun CatalogPane(viewModel: AsordViewModel) {
    val classes by viewModel.classes.collectAsState()
    val selected by viewModel.selectedClass.collectAsState()
    val items by viewModel.items.collectAsState()
    val hasMore by viewModel.itemsHasMore.collectAsState()

    Column(Modifier.fillMaxSize()) {
        (classes as? UiState.Success)?.data?.takeIf { it.isNotEmpty() }?.let { list ->
            ClassDropdown(
                title = stringResource(R.string.asord_catalog_class),
                items = list,
                selected = selected,
                onSelect = { viewModel.selectClass(it) },
            )
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        StateContent(
            state = items,
            onRetry = viewModel::reloadItems,
            onRelogin = {},
            modifier = Modifier.weight(1f),
        ) { list ->
            if (list.isEmpty()) {
                EmptyView(text = stringResource(R.string.asord_catalog_empty))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = list,
                        key = { index, item -> "$index-${item.title}-${item.classNo}" },
                    ) { _, item -> CatalogRow(item) }

                    if (hasMore) {
                        item(key = "__more__") {
                            LoadMoreFooter(onClick = viewModel::loadMoreItems)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogRow(item: AsordCatalogItem) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (item.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (item.publication.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = item.publication,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.classNo.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.asord_catalog_class_no, item.classNo),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = stringResource(
                        R.string.asord_catalog_recommend_count,
                        item.recommendCount.ifBlank { "0" },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** 列表底部的「加载更多」。 */
@Composable
private fun LoadMoreFooter(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        TextButton(onClick = onClick) {
            Text(stringResource(R.string.load_more))
        }
    }
}

// ------------------------------------------------------------------ ViewModel

/**
 * 「读者荐购」三个标签共用的 ViewModel。
 *
 * 每个标签**首次进入才加载**；切分类时取消上一次请求，
 * 避免快速点击时旧结果覆盖新结果。
 */
class AsordViewModel(private val repository: OpacRepository) : ViewModel() {

    private val _history = MutableStateFlow<UiState<List<AsordEntry>>>(UiState.Loading)
    val history: StateFlow<UiState<List<AsordEntry>>> = _history.asStateFlow()

    private val _classes = MutableStateFlow<UiState<List<LabeledValue>>>(UiState.Loading)
    val classes: StateFlow<UiState<List<LabeledValue>>> = _classes.asStateFlow()

    private val _selectedClass = MutableStateFlow<LabeledValue?>(null)
    val selectedClass: StateFlow<LabeledValue?> = _selectedClass.asStateFlow()

    private val _items = MutableStateFlow<UiState<List<AsordCatalogItem>>>(UiState.Loading)
    val items: StateFlow<UiState<List<AsordCatalogItem>>> = _items.asStateFlow()

    private var historyLoaded = false
    private var catalogLoaded = false
    private var itemsJob: Job? = null

    internal fun ensureLoaded(tab: AsordTab) {
        when (tab) {
            AsordTab.History -> if (!historyLoaded) {
                historyLoaded = true
                reloadHistory()
            }

            AsordTab.Catalog -> if (!catalogLoaded) {
                catalogLoaded = true
                reloadClasses()
                reloadItems()
            }

            AsordTab.Submit -> Unit // 纯提示页，无需加载
        }
    }

    fun reloadHistory() {
        viewModelScope.launch {
            _history.value = UiState.Loading
            historyPage = 1
            when (val result = loadState { repository.asordHistory(1) }) {
                is UiState.Loading -> Unit
                is UiState.Failure -> {
                    _history.value = result
                    _historyHasMore.value = false
                }

                is UiState.Success -> {
                    _history.value = UiState.Success(result.data.items)
                    _historyHasMore.value = result.data.page < result.data.totalPages
                }
            }
        }
    }

    /** 荐购历史还有下一页时为 true。 */
    private val _historyHasMore = MutableStateFlow(false)
    val historyHasMore: StateFlow<Boolean> = _historyHasMore.asStateFlow()

    private var historyPage = 1

    fun loadMoreHistory() {
        if (!_historyHasMore.value) return
        val loaded = (_history.value as? UiState.Success)?.data ?: return
        viewModelScope.launch {
            runCatching { repository.asordHistory(historyPage + 1) }
                .onSuccess { paged ->
                    historyPage = paged.page
                    _history.value = UiState.Success(loaded + paged.items)
                    _historyHasMore.value = paged.page < paged.totalPages
                }
        }
    }

    private fun reloadClasses() {
        viewModelScope.launch {
            _classes.value = UiState.Loading
            _classes.value = loadState { repository.asordCatalogClasses() }
            // 服务端默认返回第一个分类的书目，这里同步选中它
            if (_selectedClass.value == null) {
                (_classes.value as? UiState.Success)?.data?.firstOrNull()?.let { first ->
                    _selectedClass.value = first
                    reloadItems()
                }
            }
        }
    }

    fun selectClass(item: LabeledValue) {
        if (_selectedClass.value?.value == item.value) return
        _selectedClass.value = item
        reloadItems()
    }

    /** 征订目录还有下一页时为 true。 */
    private val _itemsHasMore = MutableStateFlow(false)
    val itemsHasMore: StateFlow<Boolean> = _itemsHasMore.asStateFlow()

    private var itemsPage = 1

    fun reloadItems() {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            _items.value = UiState.Loading
            itemsPage = 1
            val classNo = _selectedClass.value?.value
            when (val result = loadState { repository.asordCatalog(classNo, 1) }) {
                is UiState.Loading -> Unit
                is UiState.Failure -> {
                    _items.value = result
                    _itemsHasMore.value = false
                }

                is UiState.Success -> {
                    _items.value = UiState.Success(result.data.items)
                    _itemsHasMore.value = result.data.page < result.data.totalPages
                }
            }
        }
    }

    fun loadMoreItems() {
        if (!_itemsHasMore.value) return
        val loaded = (_items.value as? UiState.Success)?.data ?: return
        val classNo = _selectedClass.value?.value
        viewModelScope.launch {
            runCatching { repository.asordCatalog(classNo, itemsPage + 1) }
                .onSuccess { paged ->
                    itemsPage = paged.page
                    _items.value = UiState.Success(loaded + paged.items)
                    _itemsHasMore.value = paged.page < paged.totalPages
                }
        }
    }

}
