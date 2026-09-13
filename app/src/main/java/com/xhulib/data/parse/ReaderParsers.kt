package com.xhulib.data.parse

import com.xhulib.data.model.AsordEntry
import com.xhulib.data.model.CreditEntry
import com.xhulib.data.model.DelegateEntry
import com.xhulib.data.model.HoldEntry
import com.xhulib.data.model.LabeledValue
import com.xhulib.data.model.LoanRecord
import com.xhulib.data.model.LoanRule
import com.xhulib.data.model.ReaderCard
import com.xhulib.data.model.ReaderOverview
import com.xhulib.data.model.SearchHistoryEntry
import com.xhulib.data.model.ShelfItem
import org.jsoup.nodes.Document

/**
 * 「我的图书馆」各栏目解析。
 *
 * 标注说明：
 * - ✅ 已用真实登录会话验证过字段结构
 * - ⚠️ 字段名取自汇文标准栏目，尚未用有数据的账号验证（空态账号看不到表格）
 */
object ReaderParsers {

    // ------------------------------------------------------------------ 证件信息 ✅

    /**
     * 解析「证件信息」（`reader/redr_info_rule.php`）。
     *
     * 页面是典型的 `标签：值` 表格，另外附一张「适用的借阅规则」表。
     */
    fun parseReaderCard(html: String): ReaderCard {
        val doc = Html.parse(html)
        val pairs = Html.labelValuePairs(doc)
        fun pick(vararg labels: String): String? {
            for (label in labels) {
                val hit = pairs.firstOrNull { it.label.replace(" ", "") == label }
                if (hit != null && hit.value.isNotBlank()) return hit.value
            }
            return null
        }

        val rules = parseLoanRules(doc)
        return ReaderCard(
            name = pick("姓名", "读者姓名"),
            certNo = pick("证件号"),
            barNo = pick("条码号"),
            readerType = pick("读者类型"),
            department = pick("系别", "院系", "单位"),
            maxBooks = pick("最大可借图书"),
            expireDate = pick("失效日期"),
            fields = pairs.filterNot { it.label in EXCLUDED_CARD_LABELS },
            rules = rules,
        )
    }

    /** 证件信息页里这些标签已在卡片区单独展示，不必在明细里重复。 */
    private val EXCLUDED_CARD_LABELS = setOf("姓名", "证件号", "条码号", "读者类型")

    private fun parseLoanRules(doc: Document): List<LoanRule> {
        val table = Html.table(doc, listOf("规则名称", "最大借阅册数")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val name = table.value(row, "规则名称")
            if (name.isBlank()) return@mapNotNull null
            LoanRule(
                name = name,
                collections = table.value(row, "适用馆藏地"),
                circulationType = table.value(row, "图书流通类型"),
                maxBooks = table.value(row, "最大借阅册数"),
                loanDays = table.value(row, "借期"),
                hold = table.value(row, "预约"),
                renew = table.value(row, "续借"),
            )
        }
    }

    // ------------------------------------------------------------------ 概览数据 ✅

    private val BRACKET_NUMBER = { label: String ->
        Regex("""${Regex.escape(label)}\s*[\[【]\s*(\d+)\s*[\]】]""")
    }
    private val PLAIN_NUMBER = { label: String ->
        Regex("""${Regex.escape(label)}\s*[:：]?\s*(\d+)""")
    }

    /**
     * 从证件信息页顶部提醒条提取概览数字：
     * `五天内即将过期图书[N]，已超期图书[N]，预约到书[N]，委托到书[N]`
     */
    fun parseOverview(html: String): ReaderOverview {
        val doc = Html.parse(html)
        val text = Html.bodyText(doc)

        fun num(label: String): Int =
            BRACKET_NUMBER(label).find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: PLAIN_NUMBER(label).find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: 0

        fun nullableNum(label: String): Int? =
            BRACKET_NUMBER(label).find(text)?.groupValues?.get(1)?.toIntOrNull()
                ?: PLAIN_NUMBER(label).find(text)?.groupValues?.get(1)?.toIntOrNull()

        val card = parseReaderCard(html)
        val pairs = Html.labelValuePairs(doc)
        fun cardField(label: String): Int? = pairs
            .firstOrNull { it.label.replace(" ", "") == label }
            ?.value
            ?.let { Html.firstInt(it) }

        return ReaderOverview(
            expiringSoon = num("五天内即将过期图书"),
            overdue = num("已超期图书"),
            holdsReady = num("预约到书"),
            delegatesReady = num("委托到书"),
            maxBooks = card.maxBooks?.let { Html.firstInt(it) } ?: cardField("最大可借图书"),
            maxHolds = cardField("最大可预约图书"),
            maxDelegates = cardField("最大可委托图书"),
            totalCredits = nullableNum("总积分"),
            availableCredits = nullableNum("可用积分"),
            asordCount = Regex("""共有\s*[【\[]\s*(\d+)\s*[】\]]\s*条荐购""")
                .find(text)?.groupValues?.get(1)?.toIntOrNull(),
        )
    }

    // ------------------------------------------------------------------ 当前借阅 ⚠️

    /**
     * 解析「当前借阅」（`reader/book_lst.php`）。
     *
     * ⚠️ 验证账号没有在借图书，列名按汇文标准推测；解析按列名匹配，
     * 若服务器列名不同也只是取到空串，不会崩。
     */
    fun parseCurrentLoans(html: String): List<LoanRecord> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("题名", "借阅日期")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val title = table.value(row, "题名")
            if (title.isBlank()) return@mapNotNull null
            LoanRecord(
                title = title,
                author = table.value(row, "责任者"),
                barcode = table.value(row, "条码号"),
                loanDate = table.value(row, "借阅日期"),
                dueDate = table.value(row, "应还日期").ifBlank { table.value(row, "归还日期") },
                returnDate = table.value(row, "归还日期"),
                location = table.value(row, "馆藏地"),
            )
        }
    }

    /** 「当前借阅(N) / 最大借阅(M)」里的两个数字。 */
    fun parseLoanQuota(html: String): Pair<Int?, Int?> {
        val text = Html.bodyText(Html.parse(html))
        val m = Regex("""当前借阅\s*[（(]\s*(\d*)\s*[)）]\s*/\s*最大借阅\s*[（(]\s*(\d*)\s*[)）]""")
            .find(text) ?: return null to null
        return m.groupValues[1].toIntOrNull() to m.groupValues[2].toIntOrNull()
    }

    // ------------------------------------------------------------------ 借阅历史 ✅

    /** 解析「借阅历史」（`reader/book_hist.php`）：条码号/题名/责任者/借阅日期/归还日期/馆藏地。 */
    fun parseLoanHistory(html: String): List<LoanRecord> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("题名", "借阅日期", "归还日期")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val title = table.value(row, "题名")
            if (title.isBlank()) return@mapNotNull null
            LoanRecord(
                title = title,
                author = table.value(row, "责任者"),
                barcode = table.value(row, "条码号"),
                loanDate = table.value(row, "借阅日期"),
                returnDate = table.value(row, "归还日期"),
                dueDate = table.value(row, "应还日期"),
                location = table.value(row, "馆藏地"),
            )
        }
    }

    // ------------------------------------------------------------------ 我的积分 ✅

    /** 解析「我的积分」（`reader/credit_detail.php`）。 */
    fun parseCredits(html: String): List<CreditEntry> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("积分类型名称", "积分数")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val type = table.value(row, "积分类型名称")
            if (type.isBlank()) return@mapNotNull null
            CreditEntry(
                type = type,
                changeType = table.value(row, "积分变化类型"),
                amount = table.value(row, "积分数"),
                note = table.value(row, "积分备注"),
                date = table.value(row, "积分日期"),
            )
        }
    }

    // ------------------------------------------------------------------ 检索历史 ✅

    /** 解析「检索历史」（`reader/search_hist.php`）：检索内容 / 检索时间。 */
    fun parseSearchHistory(html: String): List<SearchHistoryEntry> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("检索内容", "检索时间")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val content = table.value(row, "检索内容")
            if (content.isBlank()) return@mapNotNull null
            SearchHistoryEntry(content = content, time = table.value(row, "检索时间"))
        }
    }

    // ------------------------------------------------------------------ 荐购历史 ✅

    /** 解析「荐购历史」（`reader/asord_lst.php`）。 */
    fun parseAsordHistory(html: String): List<AsordEntry> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("题名", "荐购日期")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val title = table.value(row, "题名")
            if (title.isBlank()) return@mapNotNull null
            AsordEntry(
                title = title,
                author = table.value(row, "责任者"),
                publication = table.value(row, "出版信息"),
                date = table.value(row, "荐购日期"),
                status = table.value(row, "荐购状态"),
                note = table.value(row, "处理备注"),
            )
        }
    }

    // ------------------------------------------------------------------ 预约信息 ✅

    /** 解析「预约信息」（`reader/preg.php`）。第一版只读，不提供「取消预约」。 */
    fun parseHolds(html: String): List<HoldEntry> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("索书号", "预约")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val title = table.value(row, "题名")
            val callNo = table.value(row, "索书号")
            if (title.isBlank() && callNo.isBlank()) return@mapNotNull null
            HoldEntry(
                callNo = callNo,
                title = title,
                location = table.value(row, "馆藏地"),
                holdDate = table.value(row, "预约"),
                deadline = table.value(row, "截止日期"),
                pickupPlace = table.value(row, "取书地"),
                status = table.value(row, "状态"),
            )
        }
    }

    // ------------------------------------------------------------------ 委托信息 ✅

    /** 解析「委托信息」（`reader/relegate.php`）。第一版只读，不提供「取消委托」。 */
    fun parseDelegates(html: String): List<DelegateEntry> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("索书号", "委托")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val title = table.value(row, "题名")
            val callNo = table.value(row, "索书号")
            if (title.isBlank() && callNo.isBlank()) return@mapNotNull null
            DelegateEntry(
                callNo = callNo,
                title = title,
                author = table.value(row, "责任者"),
                location = table.value(row, "馆藏地"),
                delegateDate = table.value(row, "委托"),
                deadline = table.value(row, "截止日期"),
                pickupPlace = table.value(row, "取书地"),
                status = table.value(row, "状态"),
            )
        }
    }

    /**
     * 解析「我的书架 / 我的课程」页顶部的**分组入口**。
     *
     * 实测这两个入口都是**只有查询串的相对链接**，参数名还各不相同：
     * ```html
     * <p>书架列表：<a href="?classid=0000002808">待阅(1)</a></p>
     * ```
     * 所以既不能只认 `book_shelf.php?...`，也不能写死参数名 —— 直接把
     * 查询串原样带回去，由调用方拼到当前页地址后面再请求一次。
     */
    fun parseShelfLinks(html: String): List<LabeledValue> {
        val doc = Html.parse(html)
        return doc.select("a[href]").mapNotNull { anchor ->
            val href = anchor.attr("href").trim()
            val query = when {
                href.startsWith("?") -> href.removePrefix("?")
                href.contains("book_shelf.php?") -> href.substringAfter("book_shelf.php?")
                else -> return@mapNotNull null
            }
            if (query.isBlank()) return@mapNotNull null
            val label = anchor.normalizedText()
            if (label.isBlank()) return@mapNotNull null
            LabeledValue(label = label, value = query)
        }.distinctBy { it.value }
    }

    // ------------------------------------------------------------------ 我的书架 / 我的课程 ✅

    /** 解析「我的书架」（`reader/book_shelf.php`）与「我的课程」（`reader/reader_curriculum.php`）。 */
    fun parseShelfItems(html: String): List<ShelfItem> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("题名", "索书号")) ?: return emptyList()
        return table.rows.mapIndexedNotNull { index, row ->
            val title = table.value(row, "题名")
            if (title.isBlank()) return@mapIndexedNotNull null
            ShelfItem(
                title = title,
                detailUrl = table.href(index, "题名")?.let { href ->
                    href.removePrefix("../opac/").removePrefix("/opac/").removePrefix("./")
                },
                author = table.value(row, "责任者"),
                publisher = table.value(row, "出版社"),
                pubDate = table.value(row, "出版日期"),
                callNo = table.value(row, "索书号"),
                status = table.value(row, "状态"),
            )
        }
    }
}
