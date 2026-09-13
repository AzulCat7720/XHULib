package com.xhulib.ui.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xhulib.R
import com.xhulib.data.model.AsordEntry
import com.xhulib.data.model.CreditEntry
import com.xhulib.data.model.DelegateEntry
import com.xhulib.data.model.HoldEntry
import com.xhulib.data.model.LoanRecord
import com.xhulib.data.model.SearchHistoryEntry
import com.xhulib.data.model.ShelfItem
import com.xhulib.ui.common.EmptyView
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.nav.ReaderSection
import com.xhulib.ui.nav.WebOnlyTask

/**
 * 「我的」页里各只读栏目的通用页面。
 *
 * 按 [sectionKey]（[ReaderSection.key]）分派到不同数据源，标题取 `section.title`。
 */
@Composable
fun ReaderSectionScreen(
    sectionKey: String,
    onBack: () -> Unit,
    onOpenWebOnly: (String) -> Unit = {},
    onOpenBook: (String) -> Unit = {},
) {
    val section = ReaderSection.fromKey(sectionKey)
    if (section == null) {
        ReaderScaffold(title = stringResource(R.string.reader_unknown_section), onBack = onBack) { modifier ->
            EmptyView(text = stringResource(R.string.empty_record), modifier = modifier)
        }
        return
    }

    val viewModel: ReaderSectionViewModel = containerViewModel(key = "reader:${section.key}") {
        ReaderSectionViewModel(it, section)
    }
    val state by viewModel.state.collectAsState()
    val hasMore by viewModel.hasMore.collectAsState()
    val loadingMore by viewModel.loadingMore.collectAsState()

    ReaderScaffold(title = stringResource(section.titleRes), onBack = onBack) { modifier ->
        Column(modifier) {
            // 「课程完成度」原本是「我的」页里单独一行，现在并进「我的课程」，
            // 它需要到图书馆网站添加课程（写操作），所以只做引导。
            if (section == ReaderSection.Courses) {
                CourseManageEntry(
                    onClick = { onOpenWebOnly(WebOnlyTask.CourseManage.key) },
                )
            }

            StateContent(
                state = state,
                onRetry = viewModel::refresh,
                onRelogin = {}, // 会话失效由父级统一处理
                modifier = Modifier.weight(1f),
            ) { data ->
                ReaderSectionBody(
                    section = section,
                    data = data,
                    hasMore = hasMore,
                    loadingMore = loadingMore,
                    onLoadMore = viewModel::loadMore,
                    onOpenBook = onOpenBook,
                )
            }
        }
    }
}

/** 「我的课程」页顶部的「课程完成度」入口。 */
@Composable
private fun CourseManageEntry(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.MenuBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.course_manage_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = stringResource(R.string.course_manage_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
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
        content(Modifier.fillMaxSize().padding(innerPadding))
    }
}

@Composable
private fun ReaderSectionBody(
    section: ReaderSection,
    data: ReaderSectionData,
    hasMore: Boolean,
    loadingMore: Boolean,
    onLoadMore: () -> Unit,
    onOpenBook: (String) -> Unit,
) {
    val paging = SectionPaging(hasMore, loadingMore, onLoadMore)
    when (data) {
        is ReaderSectionData.Loans -> SectionList(data.items, paging) { record ->
            LoanCard(record = record, highlightDue = section == ReaderSection.CurrentLoans)
        }

        is ReaderSectionData.Shelves -> SectionList(data.items, paging) { item ->
            ShelfCard(item, onOpenBook)
        }
        is ReaderSectionData.Asord -> SectionList(data.items, paging) { entry -> AsordCard(entry) }
        is ReaderSectionData.Holds -> SectionList(data.items, paging) { entry -> HoldCard(entry) }
        is ReaderSectionData.Delegates -> SectionList(data.items, paging) { entry -> DelegateCard(entry) }
        is ReaderSectionData.Credits -> SectionList(data.items, paging) { entry -> CreditCard(entry) }
        is ReaderSectionData.Searches -> SectionList(data.items, paging) { entry -> SearchCard(entry) }

        ReaderSectionData.Empty -> EmptyView(text = stringResource(R.string.empty_record))

        ReaderSectionData.WebOnly -> WebOnlyNotice(stringResource(section.titleRes))
    }
}

/** 分页信息：是否还有下一页、是否正在加载、加载下一页的动作。 */
private data class SectionPaging(
    val hasMore: Boolean,
    val loadingMore: Boolean,
    val onLoadMore: () -> Unit,
)

/** 统一列表外壳：LazyColumn + 稳定 key + 「加载更多」页脚。 */
@Composable
private fun <T> SectionList(
    items: List<T>,
    paging: SectionPaging,
    itemContent: @Composable (T) -> Unit,
) {
    if (items.isEmpty()) {
        EmptyView(text = stringResource(R.string.empty_record))
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 同一条记录可能重复出现（同名同索书号），因此 key 里带上下标。
        itemsIndexed(items, key = { index, item -> "$index-${item.hashCode()}" }) { _, item ->
            itemContent(item)
        }

        // 服务端分页：还有下一页时给一个「加载更多」
        if (paging.hasMore || paging.loadingMore) {
            item(key = "__load_more__") {
                LoadMoreRow(loading = paging.loadingMore, onClick = paging.onLoadMore)
            }
        }
    }
}

/** 列表底部的「加载更多」。 */
@Composable
private fun LoadMoreRow(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
        } else {
            TextButton(onClick = onClick) {
                Text(stringResource(R.string.load_more))
            }
        }
    }
}

/** 一条记录一张小卡片。 */
@Composable
private fun EntryCard(
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        onClick = onClick ?: {},
        enabled = onClick != null,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

/** 卡片里的一行「标签 + 值」，空值不显示。 */
@Composable
private fun FieldLine(label: String, value: String?, labelWidth: Dp = 76.dp) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/** 状态小标签（荐购状态 / 预约状态 / 委托状态）。 */
@Composable
private fun StatusPill(text: String) {
    if (text.isBlank()) return
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** 标题 + 可选状态标签的一行。 */
@Composable
private fun TitleRow(title: String, status: String? = null) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (!status.isNullOrBlank()) {
            Spacer(Modifier.width(8.dp))
            StatusPill(status)
        }
    }
}

/** 当前借阅 / 借阅历史：当前借阅突出显示应还日期。 */
@Composable
private fun LoanCard(record: LoanRecord, highlightDue: Boolean) {
    EntryCard {
        TitleRow(title = record.title)
        if (record.author.isNotBlank()) {
            Text(
                text = record.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        if (highlightDue && record.dueDate.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Event,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.reader_due_date, record.dueDate),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        FieldLine(stringResource(R.string.field_barcode), record.barcode)
        FieldLine(stringResource(R.string.field_loan_date), record.loanDate)
        if (!highlightDue) FieldLine(stringResource(R.string.field_due_date), record.dueDate)
        FieldLine(stringResource(R.string.field_return_date), record.returnDate)
        FieldLine(stringResource(R.string.field_location), record.location)
    }
}

/** 我的书架 / 我的课程。 */
@Composable
private fun ShelfCard(item: ShelfItem, onOpenBook: (String) -> Unit) {
    val detailUrl = item.detailUrl
    EntryCard(
        onClick = detailUrl?.let { url -> { onOpenBook(url) } },
    ) {
        TitleRow(title = item.title)
        Spacer(Modifier.height(8.dp))
        // 我的书架会按书架分组取回，标一下这条来自哪个书架
        FieldLine(stringResource(R.string.field_shelf), item.shelf)
        FieldLine(stringResource(R.string.field_author), item.author)
        FieldLine(stringResource(R.string.field_publisher), item.publisher)
        FieldLine(stringResource(R.string.field_pub_date), item.pubDate)
        FieldLine(stringResource(R.string.field_call_no), item.callNo)
        FieldLine(stringResource(R.string.field_status), item.status)
    }
}

/** 荐购历史。 */
@Composable
private fun AsordCard(entry: AsordEntry) {
    EntryCard {
        TitleRow(title = entry.title, status = entry.status)
        if (entry.author.isNotBlank()) {
            Text(
                text = entry.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(8.dp))
        FieldLine(stringResource(R.string.field_publication), entry.publication, labelWidth = 76.dp)
        FieldLine(stringResource(R.string.field_asord_date), entry.date)
        FieldLine(stringResource(R.string.field_asord_status), entry.status)
        FieldLine(stringResource(R.string.field_note), entry.note)
    }
}

/** 预约信息。 */
@Composable
private fun HoldCard(entry: HoldEntry) {
    EntryCard {
        TitleRow(title = entry.title.ifBlank { entry.callNo }, status = entry.status)
        Spacer(Modifier.height(8.dp))
        FieldLine(stringResource(R.string.field_call_no), entry.callNo)
        FieldLine(stringResource(R.string.field_location), entry.location)
        FieldLine(stringResource(R.string.field_hold_date), entry.holdDate)
        FieldLine(stringResource(R.string.field_deadline), entry.deadline)
        FieldLine(stringResource(R.string.field_pickup_place), entry.pickupPlace)
        FieldLine(stringResource(R.string.field_status), entry.status)
    }
}

/** 委托信息。 */
@Composable
private fun DelegateCard(entry: DelegateEntry) {
    EntryCard {
        TitleRow(title = entry.title.ifBlank { entry.callNo }, status = entry.status)
        if (entry.author.isNotBlank()) {
            Text(
                text = entry.author,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(Modifier.height(8.dp))
        FieldLine(stringResource(R.string.field_call_no), entry.callNo)
        FieldLine(stringResource(R.string.field_location), entry.location)
        FieldLine(stringResource(R.string.field_delegate_date), entry.delegateDate)
        FieldLine(stringResource(R.string.field_deadline), entry.deadline)
        FieldLine(stringResource(R.string.field_pickup_place), entry.pickupPlace)
        FieldLine(stringResource(R.string.field_status), entry.status)
    }
}

/** 我的积分。 */
@Composable
private fun CreditCard(entry: CreditEntry) {
    val decreased = entry.changeType.contains(stringResource(R.string.credit_decrease))
    EntryCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = entry.type,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = entry.amount,
                style = MaterialTheme.typography.titleMedium,
                color = if (decreased) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }
        Spacer(Modifier.height(8.dp))
        FieldLine(stringResource(R.string.field_change_type), entry.changeType)
        FieldLine(stringResource(R.string.field_credit_date), entry.date)
        FieldLine(stringResource(R.string.field_credit_note), entry.note)
    }
}

/** 检索历史。 */
@Composable
private fun SearchCard(entry: SearchHistoryEntry) {
    EntryCard {
        Text(
            text = entry.content,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = entry.time,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

/**
 * 我的书评 / 书刊遗失 / 账目清单有内容时的提示：
 * 本应用不展示，引导用户去图书馆网站。
 */
@Composable
private fun WebOnlyNotice(sectionTitle: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = sectionTitle,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = stringResource(R.string.reader_webonly_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.goto_web_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                WebPortalAction()
            }
        }
    }
}
