package com.xhulib.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.xhulib.R
import com.xhulib.data.model.BookItem
import com.xhulib.data.model.LoanRecord
import com.xhulib.data.model.ReaderCard
import com.xhulib.data.model.ReaderOverview
import com.xhulib.ui.common.BigCard
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.UiState
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.nav.ReaderSection

/**
 * 首页。
 *
 * 从上到下：搜索栏（+ 通知按钮）→ 热门推荐 → 提醒徽标 → 当前借阅 → 证件信息。
 * 整页可下拉刷新，四块数据各自独立加载与重试。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenNotifications: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSection: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenHotList: () -> Unit,
) {
    val viewModel: HomeViewModel = containerViewModel { HomeViewModel(it.repository) }
    val overviewState by viewModel.overview.collectAsState()
    val loansState by viewModel.loans.collectAsState()
    val topLendState by viewModel.topLend.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    Scaffold { innerPadding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SearchRow(
                    onOpenSearch = onOpenSearch,
                    onOpenNotifications = onOpenNotifications,
                )

                TopLendSection(
                    state = topLendState,
                    onRetry = viewModel::retryTopLend,
                    onOpenBook = onOpenBook,
                    onOpenHotList = onOpenHotList,
                )

                ReminderBadges(
                    state = overviewState,
                    onOpenNotifications = onOpenNotifications,
                )

                CurrentLoansCard(
                    state = loansState,
                    count = (loansState as? UiState.Success<List<LoanRecord>>)?.data?.size,
                    onRetry = viewModel::retryLoans,
                    onOpenAll = { onOpenSection(ReaderSection.CurrentLoans.key) },
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- 顶部搜索栏

/** 第一行：搜索栏（点击进搜索页）+ 右侧小通知按钮。 */
@Composable
private fun SearchRow(
    onOpenSearch: () -> Unit,
    onOpenNotifications: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onOpenSearch,
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        FilledTonalIconButton(onClick = onOpenNotifications) {
            Icon(
                imageVector = Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.notifications),
            )
        }
    }
}

// ---------------------------------------------------------------------- 热门推荐

/** 搜索栏下方的热门推荐：小标题 + 横向滚动的书目卡片。 */
@Composable
private fun TopLendSection(
    state: UiState<List<BookItem>>,
    onRetry: () -> Unit,
    onOpenBook: (String) -> Unit,
    onOpenHotList: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.hot_title), modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenHotList) {
                Text(stringResource(R.string.home_hot_more))
            }
        }
        Spacer(Modifier.height(4.dp))
        StateContent(
            state = state,
            onRetry = onRetry,
            onRelogin = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        ) { books ->
            if (books.isEmpty()) {
                EmptyView(
                    text = stringResource(R.string.hot_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(books) { book -> TopLendCard(book = book, onOpenBook = onOpenBook) }
                }
            }
        }
    }
}

@Composable
private fun TopLendCard(
    book: BookItem,
    onOpenBook: (String) -> Unit,
) {
    // 解析不出详情地址（如 hot 排行榜只有表格文本）时就做成不可点击的展示卡。
    val detailUrl = book.detailUrl
    Surface(
        onClick = { detailUrl?.let(onOpenBook) },
        enabled = detailUrl != null,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .width(150.dp)
            .height(150.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (book.author.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
            if (book.loanCount.isNotBlank()) {
                Text(
                    text = stringResource(R.string.hot_borrow_count, book.loanCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- 提醒徽标

private data class ReminderBadge(val label: String, val count: Int, val urgent: Boolean)

/** 概览里的四个提醒数字：为 0 的不显示，点击都进消息通知。 */
@Composable
private fun ReminderBadges(
    state: UiState<ReaderOverview>,
    onOpenNotifications: () -> Unit,
) {
    val overview = (state as? UiState.Success<ReaderOverview>)?.data ?: return
    val badges = buildList {
        if (overview.expiringSoon > 0) {
            add(ReminderBadge(stringResource(R.string.reminder_expiring), overview.expiringSoon, urgent = true))
        }
        if (overview.overdue > 0) {
            add(ReminderBadge(stringResource(R.string.reminder_overdue), overview.overdue, urgent = true))
        }
        if (overview.holdsReady > 0) {
            add(ReminderBadge(stringResource(R.string.notice_cat_hold_arrived), overview.holdsReady, urgent = false))
        }
        if (overview.delegatesReady > 0) {
            add(ReminderBadge(stringResource(R.string.notice_cat_delegate_arrived), overview.delegatesReady, urgent = false))
        }
    }
    if (badges.isEmpty()) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
    ) {
        badges.forEach { badge ->
            val containerColor = if (badge.urgent) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.tertiaryContainer
            }
            val contentColor = if (badge.urgent) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onTertiaryContainer
            }
            Surface(
                onClick = onOpenNotifications,
                shape = RoundedCornerShape(50),
                color = containerColor,
            ) {
                Text(
                    text = "${badge.label} ${badge.count}",
                    style = MaterialTheme.typography.labelLarge,
                    color = contentColor,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- 当前借阅

@Composable
private fun CurrentLoansCard(
    state: UiState<List<LoanRecord>>,
    count: Int?,
    onRetry: () -> Unit,
    onOpenAll: () -> Unit,
) {
    BigCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(
                text = stringResource(R.string.current_loans),
                modifier = Modifier.weight(1f),
            )
            if (count != null) {
                Text(
                    text = stringResource(R.string.loan_total_count, count),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        StateContent(
            state = state,
            onRetry = onRetry,
            onRelogin = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp),
        ) { loans ->
            if (loans.isEmpty()) {
                EmptyView(
                    text = stringResource(R.string.loan_empty),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                )
            } else {
                Column {
                    loans.take(MAX_LOAN_ROWS).forEachIndexed { index, loan ->
                        if (index > 0) Spacer(Modifier.height(12.dp))
                        LoanRow(loan)
                    }
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenAll)
                            .padding(vertical = 10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.action_view_all),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Filled.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LoanRow(loan: LoanRecord) {
    val days = remember(loan.dueDate) { remainingDays(loan.dueDate) }
    val urgent = days != null && days <= URGENT_DAYS
    val remainingText = remainingLabel(days)

    Column {
        Text(
            text = loan.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.loan_due, loan.dueDate.ifBlank { "—" }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (remainingText != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(remainingText.res, remainingText.days),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (urgent) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (urgent) FontWeight.Medium else null,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- 小工具

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier,
    )
}
