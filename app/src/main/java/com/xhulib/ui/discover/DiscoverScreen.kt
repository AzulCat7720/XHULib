package com.xhulib.ui.discover

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xhulib.R
import com.xhulib.data.model.BookItem
import com.xhulib.data.model.LabeledValue
import com.xhulib.ui.book.BookList
import com.xhulib.ui.book.ClassDropdown
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.common.ErrorView
import com.xhulib.ui.common.LoadingBox
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.peri.PeriodicalContent

/** 「发现」页顶部的三个栏目。 */
private enum class DiscoverTab(@StringRes val labelRes: Int) {
    ClassBrowse(R.string.discover_tab_class),
    NewBook(R.string.discover_tab_newbook),
    Periodical(R.string.discover_tab_periodical),
    Subject(R.string.discover_tab_subject),
}

/**
 * 底部导航第二个一级页面：分类浏览 / 新书通报 / 学科参考。
 *
 * @param onOpenBook 打开书目详情，参数是 `BookItem.detailUrl`（相对地址原样传递）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(onOpenBook: (String) -> Unit) {
    val viewModel = containerViewModel { DiscoverViewModel(it.repository) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    val currentTab = DiscoverTab.entries.getOrElse(selectedTab) { DiscoverTab.ClassBrowse }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                DiscoverTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = currentTab == tab,
                        onClick = { selectedTab = index },
                        text = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
            when (currentTab) {
                DiscoverTab.ClassBrowse -> ClassBrowseTab(
                    viewModel = viewModel,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.weight(1f),
                )

                DiscoverTab.NewBook -> NewBookTab(
                    viewModel = viewModel,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.weight(1f),
                )

                DiscoverTab.Periodical -> PeriodicalContent(onOpenBook = onOpenBook)

                DiscoverTab.Subject -> SubjectTab(
                    viewModel = viewModel,
                    onOpenBook = onOpenBook,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

// ------------------------------------------------------------------ 分类浏览

@Composable
private fun ClassBrowseTab(
    viewModel: DiscoverViewModel,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val classes by viewModel.classes.collectAsState()
    val selectedClass by viewModel.selectedClass.collectAsState()
    val subClasses by viewModel.subClasses.collectAsState()
    val selectedSubClass by viewModel.selectedSubClass.collectAsState()
    val books by viewModel.books.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureClasses() }

    ClassBookPane(
        title = stringResource(R.string.discover_tab_class),
        classes = classes,
        selected = selectedClass,
        books = books,
        onSelect = { viewModel.selectClass(it) },
        onRetryClasses = { viewModel.reloadClasses() },
        onRetryBooks = { viewModel.reloadBooks() },
        emptyBooksText = if (selectedClass == null) stringResource(R.string.discover_pick_class_first) else stringResource(R.string.discover_empty_class_books),
        onOpenBook = onOpenBook,
        modifier = modifier,
        subClasses = subClasses,
        selectedSubClass = selectedSubClass,
        onSelectSubClass = { viewModel.selectSubClass(it) },
    )
}

// ------------------------------------------------------------------ 新书通报

@Composable
private fun NewBookTab(
    viewModel: DiscoverViewModel,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val classes by viewModel.newClasses.collectAsState()
    val selectedClass by viewModel.selectedNewClass.collectAsState()
    val subClasses by viewModel.newSubClasses.collectAsState()
    val selectedSubClass by viewModel.selectedNewSubClass.collectAsState()
    val books by viewModel.newBooks.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureNewBooks() }

    ClassBookPane(
        title = stringResource(R.string.discover_tab_newbook),
        classes = classes,
        selected = selectedClass,
        books = books,
        onSelect = { viewModel.selectNewClass(it) },
        onRetryClasses = { viewModel.reloadNewClasses() },
        onRetryBooks = { viewModel.reloadNewBooks() },
        emptyBooksText = if (selectedClass == null) stringResource(R.string.discover_pick_class_first) else stringResource(R.string.discover_empty_class_newbooks),
        onOpenBook = onOpenBook,
        modifier = modifier,
        subClasses = subClasses,
        selectedSubClass = selectedSubClass,
        onSelectSubClass = { viewModel.selectNewSubClass(it) },
    )
}

/**
 * 「分类筛选条 + 书目列表」的通用版式，分类浏览与新书通报共用。
 */
@Composable
private fun ClassBookPane(
    title: String,
    classes: UiState<List<LabeledValue>>,
    selected: LabeledValue?,
    books: UiState<List<BookItem>>,
    onSelect: (LabeledValue) -> Unit,
    onRetryClasses: () -> Unit,
    onRetryBooks: () -> Unit,
    emptyBooksText: String,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    subClasses: List<LabeledValue> = emptyList(),
    selectedSubClass: LabeledValue? = null,
    onSelectSubClass: (LabeledValue?) -> Unit = {},
) {
    Column(modifier.fillMaxSize()) {
        when (classes) {
            is UiState.Loading -> LoadingBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
            )

            is UiState.Failure -> ErrorView(
                message = classes.messageRes,
                sessionExpired = classes.sessionExpired,
                onRetry = onRetryClasses,
                onRelogin = onRetryClasses,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )

            is UiState.Success -> if (classes.data.isEmpty()) {
                EmptyView(
                    text = stringResource(R.string.discover_empty_classes),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            } else {
                ClassDropdown(
                    title = title,
                    items = classes.data,
                    selected = selected,
                    onSelect = onSelect,
                )
            }
        }

        // 子类：顶层大类已经包含子类的书，这一行只是用来进一步收窄
        if (subClasses.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "__all__") {
                    FilterChip(
                        selected = selectedSubClass == null,
                        onClick = { onSelectSubClass(null) },
                        label = { Text(stringResource(R.string.action_all)) },
                    )
                }
                items(subClasses, key = { it.value }) { sub ->
                    FilterChip(
                        selected = sub.value == selectedSubClass?.value,
                        onClick = { onSelectSubClass(sub) },
                        label = { Text(sub.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        StateContent(
            state = books,
            onRetry = onRetryBooks,
            onRelogin = onRetryBooks,
            modifier = Modifier.weight(1f),
        ) { list ->
            BookList(
                items = list,
                onOpenBook = onOpenBook,
                emptyText = emptyBooksText,
            )
        }
    }
}

// ------------------------------------------------------------------ 学科参考

@Composable
private fun SubjectTab(
    viewModel: DiscoverViewModel,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subjects by viewModel.subjects.collectAsState()
    val selectedSubject by viewModel.selectedSubject.collectAsState()
    val books by viewModel.subjectBooks.collectAsState()

    LaunchedEffect(Unit) { viewModel.ensureSubjects() }

    val subject = selectedSubject
    Column(modifier.fillMaxSize()) {
        if (subject != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp),
            ) {
                Text(
                    text = stringResource(R.string.discover_subject_books, subject.label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { viewModel.clearSubject() }) { Text(stringResource(R.string.discover_change_subject)) }
            }
        }

        if (subject == null) {
            StateContent(
                state = subjects,
                onRetry = { viewModel.reloadSubjects() },
                onRelogin = { viewModel.reloadSubjects() },
                modifier = Modifier.weight(1f),
            ) { list ->
                if (list.isEmpty()) {
                    EmptyView(text = stringResource(R.string.discover_empty_subjects), modifier = Modifier.fillMaxSize())
                } else {
                    SubjectGrid(
                        subjects = list,
                        onSelect = { viewModel.selectSubject(it) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            StateContent(
                state = books,
                onRetry = { viewModel.reloadSubjectBooks() },
                onRelogin = { viewModel.reloadSubjectBooks() },
                modifier = Modifier.weight(1f),
            ) { list ->
                BookList(
                    items = list,
                    onOpenBook = onOpenBook,
                    emptyText = stringResource(R.string.discover_empty_subject_books),
                )
            }
        }
    }
}

@Composable
private fun SubjectGrid(
    subjects: List<LabeledValue>,
    onSelect: (LabeledValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 12.dp,
            bottom = 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = subjects,
            key = { it.label },
        ) { subject ->
            Surface(
                onClick = { onSelect(subject) },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = subject.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
                )
            }
        }
    }
}
