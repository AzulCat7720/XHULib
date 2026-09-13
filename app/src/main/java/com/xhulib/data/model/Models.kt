package com.xhulib.data.model

/** 一行「标签 - 值」，用于证件信息等详情展示。 */
data class LabeledValue(
    val label: String,
    val value: String,
)

/** 借阅规则（证件信息页附表）。 */
data class LoanRule(
    val name: String,
    val collections: String,
    val circulationType: String,
    val maxBooks: String,
    val loanDays: String,
    val hold: String,
    val renew: String,
)

/** 证件信息 / 读者信息。 */
data class ReaderCard(
    val name: String?,
    val certNo: String?,
    val barNo: String?,
    val readerType: String?,
    val department: String?,
    val maxBooks: String?,
    val expireDate: String?,
    val fields: List<LabeledValue>,
    val rules: List<LoanRule>,
)

/**
 * 「我的首页」概览数据。
 *
 * [expiringSoon] / [overdue] / [holdsReady] / [delegatesReady] 来自证件信息页顶部的提醒条。
 */
data class ReaderOverview(
    val expiringSoon: Int = 0,
    val overdue: Int = 0,
    val holdsReady: Int = 0,
    val delegatesReady: Int = 0,
    val maxBooks: Int? = null,
    val maxHolds: Int? = null,
    val maxDelegates: Int? = null,
    val totalCredits: Int? = null,
    val availableCredits: Int? = null,
    val asordCount: Int? = null,
)

/** 当前借阅 / 借阅历史中的一条记录。 */
data class LoanRecord(
    val title: String,
    val author: String = "",
    val barcode: String = "",
    val loanDate: String = "",
    val dueDate: String = "",
    val returnDate: String = "",
    val location: String = "",
)

/** 我的积分明细。 */
data class CreditEntry(
    val type: String,
    val changeType: String,
    val amount: String,
    val note: String,
    val date: String,
)

/** 检索历史。 */
data class SearchHistoryEntry(
    val content: String,
    val time: String,
)

/** 荐购历史。 */
data class AsordEntry(
    val title: String,
    val author: String,
    val publication: String,
    val date: String,
    val status: String,
    val note: String,
)

/**
 * 读者荐购 ·「新书目录推荐」（征订分类浏览）里的一条待征订书目。
 *
 * [recommendCount] 是「荐购人数」，即已有多少读者推荐订购这本书。
 */
data class AsordCatalogItem(
    val title: String,
    val author: String = "",
    val publication: String = "",
    val classNo: String = "",
    val recommendCount: String = "",
)

/** 预约信息。 */
data class HoldEntry(
    val callNo: String,
    val title: String,
    val location: String,
    val holdDate: String,
    val deadline: String,
    val pickupPlace: String,
    val status: String,
)

/** 委托信息。 */
data class DelegateEntry(
    val callNo: String,
    val title: String,
    val author: String,
    val location: String,
    val delegateDate: String,
    val deadline: String,
    val pickupPlace: String,
    val status: String,
)

/** 我的书架 / 我的课程 中的书目条目。 */
data class ShelfItem(
    val title: String,
    val author: String,
    val publisher: String,
    val pubDate: String,
    val callNo: String,
    val status: String = "",
    /** 来自哪个书架（如「待阅」「已读」）；我的课程下为空。 */
    val shelf: String = "",
    /** 详情页相对地址（`item.php?marc_no=…`）。 */
    val detailUrl: String? = null,
)

/** 书目检索结果 / 热门推荐 / 新书通报 中的一条书目。 */
data class BookItem(
    val title: String,
    val author: String = "",
    val publication: String = "",
    val callNo: String = "",
    val holding: String = "",
    /** 排行数字（借阅册次 / 评价人次 / 收藏人次 / 浏览次数）。 */
    val loanCount: String = "",
    val loanRatio: String = "",
    /** [loanCount] 这个数字代表什么，用于界面上的小标签。 */
    val metricLabel: String = "",
    val detailUrl: String? = null,
)

/**
 * 期刊导航里的一条期刊记录。
 *
 * 不同子标签能拿到的字段不一样，用不上的留空。
 */
data class Periodical(
    val title: String,
    /** 刊名拼音 / 西文字母导航里是 ISSN。 */
    val issn: String = "",
    val publisher: String = "",
    val pubYear: String = "",
    val callNo: String = "",
    /** 年度订购期刊：文献类型、订购年度、分配地。 */
    val docType: String = "",
    val orderYear: String = "",
    val location: String = "",
    /** 学科导航下的期刊带详情链接（`item.php?marc_no=…`）。 */
    val detailUrl: String? = null,
)

/** 消息通知（来自「信息发布」）。 */
data class NoticeItem(
    val category: String,
    val title: String,
    val detail: String = "",
    val date: String = "",
    /** 该通知的详情页（相对 `info/` 的文件名，如 `preg_arri_bulletin.php`）。 */
    val url: String = "",
)

/**
 * 通知详情（公告页）的内容。
 *
 * 馆方对「没有记录」和「有记录」用的是两套版式：
 * 前者是一个 `div#err` 提示块，后者是表格。
 */
data class BulletinDetail(
    val message: String = "",
    val headers: List<String> = emptyList(),
    val rows: List<List<String>> = emptyList(),
)

/** 通用分页结果。 */
data class Paged<T>(
    val items: List<T>,
    val page: Int = 1,
    val totalPages: Int = 1,
)
