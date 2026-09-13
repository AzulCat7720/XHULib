package com.xhulib.ui.book

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.xhulib.ui.common.EmptyView

/**
 * 书目列表里的一行：题名（粗体）/ 责任者 / 出版信息 / 索书号 / 馆藏。
 *
 * [BookItem.detailUrl] 为空时该条不可点击（服务端没给出详情链接）。
 */
@Composable
fun BookRow(
    item: BookItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val openable = item.detailUrl != null
    Surface(
        onClick = onClick,
        enabled = openable,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (item.author.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.author,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.publication.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.publication,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val callNoText = stringResource(R.string.book_row_call_no, item.callNo)
                val holdingText = stringResource(R.string.book_row_holding, item.holding)
                val loanText = stringResource(R.string.book_row_loan_count, item.loanCount)
                val meta = buildList {
                    if (item.callNo.isNotBlank()) add(callNoText)
                    if (item.holding.isNotBlank()) add(holdingText)
                    if (item.loanCount.isNotBlank()) add(loanText)
                }
                if (meta.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = meta.joinToString("　·　"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (openable) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 书目列表（分类浏览 / 新书通报 / 检索结果共用）。
 *
 * 列表项用 [BookItem.detailUrl] 作为 key；个别条目没有 detailUrl，用下标兜底保证唯一。
 */
@Composable
fun BookList(
    items: List<BookItem>,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier,
    emptyText: String = stringResource(R.string.book_empty),
) {
    if (items.isEmpty()) {
        EmptyView(text = emptyText, modifier = modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 8.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { index, item -> item.detailUrl ?: "book-row-$index" },
        ) { index, item ->
            BookRow(
                item = item,
                onClick = { item.detailUrl?.let(onOpenBook) },
            )
            if (index != items.lastIndex) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

/**
 * 分类下拉选择器（分类浏览 / 新书通报 / 读者荐购 共用）。
 *
 * 收起时只占一行：分类标题 + 当前选中的分类 + 展开箭头；
 * 展开后是一个网格面板，一屏能看到大部分分类。
 *
 * 早先用的是横向筛选条，22 个中图法大类挤在一行里，要找到目标分类得横向滑很久。
 *
 * @param title 收起时左侧显示的标题，一般传当前栏目名
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDropdown(
    title: String,
    items: List<LabeledValue>,
    selected: LabeledValue?,
    onSelect: (LabeledValue) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = selected?.label ?: stringResource(R.string.discover_pick_class),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.discover_class_collapse
                        else R.string.discover_class_expand,
                    ),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 108.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    gridItems(items, key = { it.label }) { item ->
                        FilterChip(
                            selected = item.label == selected?.label,
                            onClick = {
                                onSelect(item)
                                expanded = false
                            },
                            label = {
                                Text(
                                    text = item.label,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}
