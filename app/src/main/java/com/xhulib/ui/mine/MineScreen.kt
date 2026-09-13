package com.xhulib.ui.mine

import androidx.compose.foundation.layout.Box
import androidx.annotation.StringRes
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryAdd
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Recommend
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stars
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import java.util.Calendar
import java.util.TimeZone
import com.xhulib.data.model.ReaderCard
import com.xhulib.R
import com.xhulib.ui.common.BigCard
import com.xhulib.ui.common.SectionRow
import com.xhulib.ui.common.appContainer
import com.xhulib.data.store.ThemeMode
import com.xhulib.ui.common.StateContent
import com.xhulib.ui.common.containerViewModel
import com.xhulib.ui.nav.ReaderSection
import com.xhulib.ui.nav.WebOnlyTask

/**
 * 「我的」页。
 *
 * 布局：顶部账号栏（账号 + 账号切换 + 设置）→ 证件信息大卡片 → 各栏目的单行入口。
 * 页面只做导航，不发起任何写操作。
 */
@Composable
fun MineScreen(
    onOpenSection: (String) -> Unit,
    onOpenWebOnly: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenAsordCatalog: () -> Unit,
) {
    val container = appContainer()
    val themeMode by container.settingsStore.themeMode.collectAsState()
    val isDark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val viewModel: MineViewModel = containerViewModel { MineViewModel(it) }
    val state by viewModel.state.collectAsState()

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AccountBar(
                isDark = isDark,
                onToggleTheme = {
                    container.settingsStore.setThemeMode(
                        if (isDark) ThemeMode.LIGHT else ThemeMode.DARK,
                    )
                },
                onOpenSettings = onOpenSettings,
            )
            StateContent(
                state = state,
                onRetry = viewModel::refresh,
                onRelogin = {}, // 会话失效由父级统一处理
                modifier = Modifier.weight(1f),
            ) { data ->
                MineContent(
                    data = data,
                    onOpenSection = onOpenSection,
                    onOpenWebOnly = onOpenWebOnly,
                    onOpenAsordCatalog = onOpenAsordCatalog,
                )
            }
        }
    }
}

/** 顶部一行：左账号、右两个图标按钮。 */
@Composable
private fun AccountBar(
    isDark: Boolean,
    onToggleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(remember { greetingByBeijingTime() }),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        // 一键在日间/夜间之间切换（原「账号切换」入口已移除）
        IconButton(onClick = onToggleTheme) {
            Icon(
                imageVector = if (isDark) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                contentDescription = stringResource(
                    if (isDark) R.string.theme_switch_to_light else R.string.theme_switch_to_dark,
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MineContent(
    data: MineData,
    onOpenSection: (String) -> Unit,
    onOpenWebOnly: (String) -> Unit,
    onOpenAsordCatalog: () -> Unit,
) {
    val rows = mineRows(data, onOpenSection, onOpenAsordCatalog)
    val webRows = webOnlyRows(onOpenWebOnly)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "reader_card") {
            ReaderCardCard(data.card)
        }
        itemsIndexed(rows, key = { _, row -> row.titleRes }) { _, row ->
            MineRowItem(row)
        }

        // 需要到图书馆网站办理的栏目统一放到最后，并加一条小标题
        item(key = "web_only_header") {
            Text(
                text = stringResource(R.string.mine_web_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp),
            )
        }
        itemsIndexed(webRows, key = { _, row -> row.titleRes }) { index, row ->
            MineRowItem(row)
            if (index != webRows.lastIndex) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 20.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
        }
    }
}

@Composable
private fun MineRowItem(row: MineRow) {
    SectionRow(
        title = stringResource(row.titleRes),
        leadingIcon = row.icon,
        trailingText = row.trailingText,
        onClick = row.onClick,
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** 证件信息大卡片：突出姓名，下面列证件号 / 读者类型 / 系别 / 各类额度。 */
@Composable
private fun ReaderCardCard(card: ReaderCard) {
    Box(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        BigCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = card.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.reader_fallback_name),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.height(12.dp))
            CardLine(stringResource(R.string.field_cert_no), card.certNo)
            CardLine(stringResource(R.string.field_reader_type), card.readerType)
            CardLine(stringResource(R.string.field_department), card.department)
            CardLine(stringResource(R.string.field_max_books), card.maxBooks)
            CardLine(stringResource(R.string.field_max_holds), card.fieldOf("最大可预约图书", stringResource(R.string.field_max_holds)))
            CardLine(stringResource(R.string.field_max_delegates), card.fieldOf("最大可委托图书", stringResource(R.string.field_max_delegates)))
        }
    }
}

/** 大卡片里的一行「标签 + 值」，空值不显示。 */
@Composable
private fun CardLine(label: String, value: String?, labelWidth: Dp = 84.dp) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
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

/** [ReaderCard.fields] 里按标签取一个值（解析器把所有明细都放在这里）。 */
private fun ReaderCard.fieldOf(vararg labels: String): String? =
    labels.firstNotNullOfOrNull { label ->
        fields.firstOrNull { it.label.replace(" ", "") == label }
            ?.value
            ?.takeIf { it.isNotBlank() }
    }

/** 「我的」页的一个入口行。 */
private data class MineRow(
    @StringRes val titleRes: Int,
    val icon: ImageVector,
    val trailingText: String? = null,
    val onClick: () -> Unit,
)

/**
 * 按**北京时间**给一句问候。
 *
 * 卡片里已经显示证件号了，左上角再显示一遍没有意义。
 */
@StringRes
private fun greetingByBeijingTime(): Int {
    val hour = Calendar.getInstance(TimeZone.getTimeZone("Asia/Shanghai"))
        .get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..8 -> R.string.greeting_morning
        in 9..11 -> R.string.greeting_forenoon
        in 12..13 -> R.string.greeting_noon
        in 14..17 -> R.string.greeting_afternoon
        in 18..22 -> R.string.greeting_evening
        else -> R.string.greeting_late_night
    }
}

/** 需要到图书馆网站办理的栏目，单独一组放在页面末尾。 */
private fun webOnlyRows(onOpenWebOnly: (String) -> Unit): List<MineRow> = listOf(
    MineRow(R.string.webonly_report_loss, Icons.Outlined.ReportProblem) {
        onOpenWebOnly(WebOnlyTask.ReportLoss.key)
    },
    MineRow(R.string.webonly_fines, Icons.Outlined.Payments) {
        onOpenWebOnly(WebOnlyTask.Fines.key)
    },
)

private fun mineRows(
    data: MineData,
    onOpenSection: (String) -> Unit,
    onOpenAsordCatalog: () -> Unit,
): List<MineRow> = listOf(
    MineRow(R.string.section_bookshelf, Icons.Outlined.CollectionsBookmark) {
        onOpenSection(ReaderSection.Bookshelf.key)
    },
    MineRow(R.string.section_reviews, Icons.Outlined.RateReview) {
        onOpenSection(ReaderSection.Reviews.key)
    },
    MineRow(R.string.section_loan_history, Icons.Outlined.History) {
        onOpenSection(ReaderSection.LoanHistory.key)
    },
    MineRow(R.string.asord_tab_submit, Icons.Outlined.LibraryAdd) {
        onOpenAsordCatalog()
    },
    MineRow(R.string.section_search_history, Icons.Outlined.Search) {
        onOpenSection(ReaderSection.SearchHistory.key)
    },
    MineRow(R.string.section_courses, Icons.Outlined.School) {
        onOpenSection(ReaderSection.Courses.key)
    },
    MineRow(
        titleRes = R.string.section_holds,
        icon = Icons.Outlined.EventAvailable,
        trailingText = data.holdCount?.toString(),
    ) {
        onOpenSection(ReaderSection.Holds.key)
    },
    MineRow(
        titleRes = R.string.section_delegates,
        icon = Icons.Outlined.AssignmentTurnedIn,
        trailingText = data.delegateCount?.toString(),
    ) {
        onOpenSection(ReaderSection.Delegates.key)
    },
    MineRow(R.string.section_book_loss, Icons.Outlined.ErrorOutline) {
        onOpenSection(ReaderSection.BookLoss.key)
    },
    MineRow(R.string.section_account, Icons.Outlined.ReceiptLong) {
        onOpenSection(ReaderSection.Account.key)
    },
    MineRow(
        titleRes = R.string.section_credits,
        icon = Icons.Outlined.Stars,
        trailingText = data.overview?.availableCredits?.toString(),
    ) {
        onOpenSection(ReaderSection.Credits.key)
    },
)
