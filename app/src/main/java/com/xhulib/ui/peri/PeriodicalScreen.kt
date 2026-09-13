package com.xhulib.ui.peri

import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.R
import com.xhulib.data.OpacRepository
import com.xhulib.data.model.LabeledValue
import com.xhulib.data.model.Periodical
import com.xhulib.ui.book.ClassDropdown
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.common.loadState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 期刊导航的四个子标签，顺序与图书馆网站一致。 */
internal enum class PeriTab(@StringRes val labelRes: Int) {
    ByPinyin(R.string.peri_tab_pinyin),
    ByLetter(R.string.peri_tab_letter),
    ByClass(R.string.peri_tab_class),
    ByYear(R.string.peri_tab_year),
}

/** 首字母选项：站点的 `?title=0` 表示 0-9。 */
private val LETTERS: List<Pair<String, String>> =
    listOf("0" to "0-9") + ('A'..'Z').map { it.toString() to it.toString() }

/**
 * 期刊导航（只读）。
 *
 * 对应图书馆顶栏的「期刊导航」，四个子标签：
 * 刊名拼音导航 / 西文字母导航 / 期刊学科导航 / 年度订购期刊。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodicalScreen(onBack: () -> Unit, onOpenBook: (String) -> Unit = {}) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.peri_title)) },
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
        PeriodicalContent(
            onOpenBook = onOpenBook,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

/**
 * 期刊导航的主体（四个子标签 + 列表），不含标题栏。
 *
 * 抽出来是为了让「发现」页能把它直接当一个标签页用，
 * 不必为了复用而套两层 Scaffold。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PeriodicalContent(
    onOpenBook: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val viewModel: PeriodicalViewModel = containerViewModel { PeriodicalViewModel(it.repository) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val current = PeriTab.entries.getOrElse(selectedTab) { PeriTab.ByPinyin }

    LaunchedEffect(current) { viewModel.ensureLoaded(current) }

    Column(modifier) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            PeriTab.entries.forEachIndexed { index, tab ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(stringResource(tab.labelRes)) },
                )
            }
        }

        when (current) {
            PeriTab.ByPinyin -> LetterPane(viewModel, OpacRepository.LETTER_CN, onOpenBook)
            PeriTab.ByLetter -> LetterPane(viewModel, OpacRepository.LETTER_EN, onOpenBook)
            PeriTab.ByClass -> ClassPane(viewModel, onOpenBook)
            PeriTab.ByYear -> YearPane(viewModel, onOpenBook)
        }
    }
}

// ------------------------------------------------------------------ 按首字母

@Composable
private fun LetterPane(
    viewModel: PeriodicalViewModel,
    byTitle: String,
    onOpenBook: (String) -> Unit,
) {
    val letter by viewModel.letter.collectAsState()
    val state by viewModel.state(PeriTab.ByPinyin).collectAsState()

    LaunchedEffect(letter) { viewModel.loadByLetter(byTitle, letter) }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(LETTERS, key = { it.first }) { (value, label) ->
                FilterChip(
                    selected = value == letter,
                    onClick = { viewModel.selectLetter(value) },
                    label = { Text(label) },
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        PeriodicalList(
            state = state,
            onRetry = { viewModel.loadByLetter(byTitle, letter) },
            onOpenBook = onOpenBook,
        )
    }
}

// ------------------------------------------------------------------ 按学科

@Composable
private fun ClassPane(viewModel: PeriodicalViewModel, onOpenBook: (String) -> Unit) {
    val classes by viewModel.classes.collectAsState()
    val selected by viewModel.selectedClass.collectAsState()
    val state by viewModel.state(PeriTab.ByClass).collectAsState()

    Column(Modifier.fillMaxSize()) {
        (classes as? UiState.Success)?.data?.takeIf { it.isNotEmpty() }?.let { list ->
            ClassDropdown(
                title = stringResource(R.string.peri_class_title),
                items = list,
                selected = selected,
                onSelect = { viewModel.selectClass(it) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        PeriodicalList(state = state, onRetry = viewModel::reloadClassList, onOpenBook = onOpenBook)
    }
}

// ------------------------------------------------------------------ 年度订购

@Composable
private fun YearPane(viewModel: PeriodicalViewModel, onOpenBook: (String) -> Unit) {
    val state by viewModel.state(PeriTab.ByYear).collectAsState()
    PeriodicalList(
        state = state,
        onRetry = { viewModel.reload(PeriTab.ByYear) },
        onOpenBook = onOpenBook,
        // 该栏目对未登录用户不下发数据，空态文案说清楚原因
        emptyText = stringResource(R.string.peri_year_empty),
    )
}

// ------------------------------------------------------------------ 列表

@Composable
private fun PeriodicalList(
    state: UiState<List<Periodical>>,
    onRetry: () -> Unit,
    onOpenBook: (String) -> Unit,
    emptyText: String = "",
) {
    StateContent(
        state = state,
        onRetry = onRetry,
        onRelogin = {},
        modifier = Modifier.fillMaxSize(),
    ) { list ->
        if (list.isEmpty()) {
            EmptyView(text = emptyText.ifBlank { stringResource(R.string.peri_empty) })
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = list,
                    key = { index, item -> "$index-${item.title}-${item.issn}" },
                ) { _, item -> PeriodicalRow(item, onOpenBook) }
            }
        }
    }
}

@Composable
private fun PeriodicalRow(item: Periodical, onOpenBook: (String) -> Unit) {
    val detailUrl = item.detailUrl
    Surface(
        onClick = { detailUrl?.let(onOpenBook) },
        enabled = !detailUrl.isNullOrBlank(),
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
            Spacer(Modifier.height(6.dp))
            Field(stringResource(R.string.peri_issn), item.issn)
            Field(stringResource(R.string.peri_publisher), item.publisher)
            Field(stringResource(R.string.peri_pub_year), item.pubYear)
            Field(stringResource(R.string.peri_call_no), item.callNo)
            Field(stringResource(R.string.peri_doc_type), item.docType)
            Field(stringResource(R.string.peri_order_year), item.orderYear)
            Field(stringResource(R.string.peri_location), item.location)
        }
    }
}

/** 只显示有值的字段。 */
@Composable
private fun Field(label: String, value: String) {
    if (value.isBlank()) return
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(vertical = 1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

// ------------------------------------------------------------------ ViewModel

/**
 * 期刊导航的 ViewModel。
 *
 * 首字母两个标签共用一份「按首字母」的数据与状态；学科导航另外维护分类树。
 */
class PeriodicalViewModel(private val repository: OpacRepository) : ViewModel() {

    private val _letter = MutableStateFlow("0")
    val letter: StateFlow<String> = _letter.asStateFlow()

    private val _classes = MutableStateFlow<UiState<List<LabeledValue>>>(UiState.Loading)
    val classes: StateFlow<UiState<List<LabeledValue>>> = _classes.asStateFlow()

    private val _selectedClass = MutableStateFlow<LabeledValue?>(null)
    val selectedClass: StateFlow<LabeledValue?> = _selectedClass.asStateFlow()

    private val byLetter = MutableStateFlow<UiState<List<Periodical>>>(UiState.Loading)
    private val byClass = MutableStateFlow<UiState<List<Periodical>>>(UiState.Loading)
    private val byYear = MutableStateFlow<UiState<List<Periodical>>>(UiState.Loading)

    private var classTreeLoaded = false
    private var yearLoaded = false

    internal fun state(tab: PeriTab): StateFlow<UiState<List<Periodical>>> = when (tab) {
        PeriTab.ByPinyin, PeriTab.ByLetter -> byLetter.asStateFlow()
        PeriTab.ByClass -> byClass.asStateFlow()
        PeriTab.ByYear -> byYear.asStateFlow()
    }

    internal fun ensureLoaded(tab: PeriTab) {
        when (tab) {
            PeriTab.ByPinyin, PeriTab.ByLetter -> Unit // 由 LetterPane 的 LaunchedEffect 负责
            PeriTab.ByClass -> if (!classTreeLoaded) {
                classTreeLoaded = true
                loadClassTree()
            }

            PeriTab.ByYear -> if (!yearLoaded) {
                yearLoaded = true
                reload(PeriTab.ByYear)
            }
        }
    }

    fun selectLetter(value: String) {
        _letter.value = value
    }

    fun loadByLetter(byTitle: String, letter: String) {
        viewModelScope.launch {
            byLetter.value = UiState.Loading
            byLetter.value = loadState { repository.periodicalsByLetter(byTitle, letter) }
        }
    }

    private fun loadClassTree() {
        viewModelScope.launch {
            _classes.value = UiState.Loading
            _classes.value = loadState { repository.periodicalClassTree() }
            (_classes.value as? UiState.Success)?.data?.firstOrNull()?.let { first ->
                _selectedClass.value = first
                reloadClassList()
            } ?: run { byClass.value = UiState.Success(emptyList()) }
        }
    }

    fun selectClass(item: LabeledValue) {
        if (_selectedClass.value?.value == item.value) return
        _selectedClass.value = item
        reloadClassList()
    }

    fun reloadClassList() {
        viewModelScope.launch {
            byClass.value = UiState.Loading
            byClass.value = loadState { repository.periodicalsByClass(_selectedClass.value?.value) }
        }
    }

    internal fun reload(tab: PeriTab) {
        when (tab) {
            PeriTab.ByYear -> viewModelScope.launch {
                byYear.value = UiState.Loading
                byYear.value = loadState { repository.subscribedPeriodicals() }
            }

            PeriTab.ByClass -> reloadClassList()
            else -> Unit
        }
    }
}
