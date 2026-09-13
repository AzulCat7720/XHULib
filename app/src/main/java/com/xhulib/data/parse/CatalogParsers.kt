package com.xhulib.data.parse

import com.xhulib.data.model.AsordCatalogItem
import com.xhulib.data.model.BookItem
import com.xhulib.data.model.BulletinDetail
import com.xhulib.data.model.LabeledValue
import com.xhulib.data.model.NoticeItem
import com.xhulib.data.model.Periodical
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * 公开栏目解析：书目检索、热门推荐、新书通报、分类浏览、学科参考、信息发布。
 */
object CatalogParsers {

    private val LEADING_INDEX = Regex("""^\s*\d+\s*[.、]\s*""")
    private val BR = Regex("""(?i)<br\s*/?>""")

    /** 检索结果页里每条书目前面的「1.」「2.」序号。 */
    private fun stripIndex(text: String): String = text.replace(LEADING_INDEX, "").trim()

    // ------------------------------------------------------------------ 检索结果 ✅

    /**
     * 解析书目检索结果（`opac/openlink.php`）。
     *
     * 结构（已在真实页面确认）：
     * ```html
     * <ol id="search_book_list">
     *   <li class="book_list_info">
     *     <h3><span>中文图书</span><a href="item.php?marc_no=...">1.题名</a> 索书号</h3>
     *     <p><span>馆藏复本：2 可借复本：2</span> 责任者 <br/> 出版信息 <br/> …</p>
     *   </li>
     * ```
     */
    fun parseSearchResults(html: String): List<BookItem> {
        val doc = Html.parse(html)
        val nodes = doc.select("ol#search_book_list li")
        if (nodes.isEmpty()) return emptyList()
        return nodes.mapNotNull { li -> toBookItem(li) }
    }

    private fun toBookItem(li: Element): BookItem? {
        val anchor = li.selectFirst("h3 a") ?: return null
        val title = stripIndex(anchor.text())
        if (title.isEmpty()) return null

        val callNo = li.selectFirst("h3")?.let { h3 ->
            val clone = h3.clone()
            clone.select("a, span").remove()
            clone.text().trim()
        }.orEmpty()

        val paragraph = li.selectFirst("p")
        var holding = ""
        var author = ""
        var publication = ""

        if (paragraph != null) {
            holding = paragraph.selectFirst("span")?.normalizedText().orEmpty()

            val clone = paragraph.clone()
            clone.select("span, img, a").remove()
            val lines = clone.html().split(BR)
                .map { Jsoup.parse(it).text().trim() }
                .filter { it.isNotEmpty() }
            author = lines.getOrNull(0).orEmpty()
            publication = lines.getOrNull(1).orEmpty()
        }

        return BookItem(
            title = title,
            author = author,
            publication = publication,
            callNo = callNo,
            holding = holding,
            detailUrl = anchor.attr("href").ifBlank { null },
        )
    }

    /** 检索结果总数；页面会显示「共 N 条」之类。 */
    fun parseSearchTotal(html: String): Int? {
        val text = Html.bodyText(Html.parse(html))
        Regex("""共\s*(\d+)\s*条""").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        Regex("""检索到\s*(\d+)\s*条""").find(text)?.let { return it.groupValues[1].toIntOrNull() }
        return null
    }

    fun hasNoSearchResult(html: String): Boolean {
        val text = Html.bodyText(Html.parse(html))
        return text.contains("没有找到") || text.contains("未找到") || text.contains("检索结果为空")
    }

    // ------------------------------------------------------------------ 热门推荐 ✅

    /**
     * 解析「热门借阅」排行（`top/top_lend.php`）。
     *
     * 表头：题名 / 责任者 / 出版信息 / 索书号 / 馆藏 / 借阅册次 / 借阅比
     * 题名单元格里带详情链接（`../opac/item.php?marc_no=…`），一并取出来，
     * 这样首页的热门推荐卡片才能点进详情。
     */
    fun parseTopLend(html: String): List<BookItem> = parseTopList(html)

    /**
     * 「热门推荐」四个标签的排行数字列名各不相同：
     * 热门借阅=借阅册次、热门评分=评价人次、热门收藏=收藏人次、热门图书=浏览次数。
     * 这里按顺序探测，命中哪个就用哪个，并记进 [BookItem.metricLabel]。
     */
    private val TOP_METRIC_COLUMNS = listOf("借阅册次", "评价人次", "收藏人次", "浏览次数")

    fun parseTopList(html: String): List<BookItem> {
        val doc = Html.parse(html)
        for (metric in TOP_METRIC_COLUMNS) {
            val table = Html.table(doc, listOf("题名", metric)) ?: continue
            return table.rows.mapIndexedNotNull { index, row ->
                val title = table.value(row, "题名")
                if (title.isBlank()) return@mapIndexedNotNull null
                BookItem(
                    title = stripIndex(title),
                    author = table.value(row, "责任者"),
                    publication = table.value(row, "出版信息"),
                    callNo = table.value(row, "索书号"),
                    holding = table.value(row, "馆藏"),
                    loanCount = table.value(row, metric),
                    loanRatio = table.value(row, "借阅比"),
                    metricLabel = metric,
                    detailUrl = normalizeDetailUrl(table.href(index, "题名").orEmpty()),
                )
            }
        }
        return emptyList()
    }

    // ------------------------------------------------------------------ 期刊导航 ✅

    /**
     * 解析「期刊学科导航」的分类树（`peri/peri_nav_cls_tree.php`）。
     *
     * 该页没有 `<a href>`，节点挂在 JS 调用上：
     * ```html
     * <div id="node010101" ...>…<span onclick="searchF('010101','%E9%A9%AC…')">马克思主义哲学</span></div>
     * ```
     * `searchF` 的第二个参数是 URL 编码的学科名，取回来要解码。
     */
    fun parsePeriodicalClassTree(html: String): List<LabeledValue> {
        val call = Regex("""searchF\('([^']+)'\s*,\s*'([^']*)'""")
        val out = linkedMapOf<String, String>()
        for (m in call.findAll(html)) {
            val id = m.groupValues[1].trim()
            val name = runCatching {
                java.net.URLDecoder.decode(m.groupValues[2], "UTF-8")
            }.getOrDefault(m.groupValues[2]).trim()
            if (id.isNotEmpty() && name.isNotEmpty()) out.putIfAbsent(id, name)
        }
        return out.map { (id, name) -> LabeledValue(label = name, value = id) }
    }

    /**
     * 解析期刊导航的期刊列表。
     *
     * - 刊名拼音 / 西文字母导航：刊名 / ISSN / 出版社 / 出版年 / 索书号
     * - 年度订购期刊：刊名 / 出版社 / 文献类型 / 订购年度 / 分配地
     *
     * 两种表头差别较大，按「有没有 ISSN 列」来区分。
     */
    fun parsePeriodicals(html: String): List<Periodical> {
        val doc = Html.parse(html)

        Html.table(doc, listOf("刊名", "ISSN"))?.let { table ->
            return table.rows.mapIndexedNotNull { index, row ->
                val title = table.value(row, "刊名")
                if (title.isBlank()) return@mapIndexedNotNull null
                Periodical(
                    title = stripIndex(title),
                    issn = table.value(row, "ISSN"),
                    publisher = table.value(row, "出版社"),
                    pubYear = table.value(row, "出版年"),
                    callNo = table.value(row, "索书号"),
                    // 刊名格子里带详情链接，取出来让期刊也能点进详情
                    detailUrl = normalizeDetailUrl(table.href(index, "刊名").orEmpty()),
                )
            }
        }

        Html.table(doc, listOf("刊名", "订购年度"))?.let { table ->
            return table.rows.mapNotNull { row ->
                val title = table.value(row, "刊名")
                if (title.isBlank()) return@mapNotNull null
                Periodical(
                    title = stripIndex(title),
                    publisher = table.value(row, "出版社"),
                    docType = table.value(row, "文献类型"),
                    orderYear = table.value(row, "订购年度"),
                    location = table.value(row, "分配地"),
                )
            }
        }

        return emptyList()
    }

    // ------------------------------------------------------------------ 新书通报 / 分类浏览 ✅（列名待验证）

    /**
     * 解析书目列表（新书通报 `newbook/newbook_cls_book.php`、
     * 分类浏览 `browse/cls_browsing_book.php`）。
     *
     * 实测结构（两个页面同构）：
     * ```html
     * <div class="list_books">
     *   <h3><strong>1. <a href="../opac/item.php?marc_no=…">题名</a></strong></h3>
     *   <p><span id="info_…"></span> 责任者  出版信息 </p>
     * </div>
     * ```
     * 责任者与出版信息之间用连续空格分隔，故按 2 个以上空白切分。
     */
    fun parseBookList(html: String): List<BookItem> {
        val doc = Html.parse(html)

        val listBooks = doc.select("div.list_books")
        if (listBooks.isNotEmpty()) {
            return listBooks.mapNotNull { div ->
                val anchor = div.selectFirst("h3 a") ?: return@mapNotNull null
                val title = stripIndex(anchor.text())
                if (title.isBlank()) return@mapNotNull null

                val raw = div.selectFirst("p")?.let { paragraph ->
                    val clone = paragraph.clone()
                    clone.select("span").remove()
                    clone.text().trim()
                }.orEmpty()
                val parts = raw.split(Regex("""\s{2,}""")).filter { it.isNotBlank() }

                BookItem(
                    title = title,
                    author = parts.getOrNull(0).orEmpty(),
                    publication = parts.drop(1).joinToString(" ").trim(),
                    detailUrl = normalizeDetailUrl(anchor.attr("href")),
                )
            }
        }

        // 退路一：表格形态
        val table = Html.table(doc, listOf("题名"))
        if (table != null) {
            return table.rows.mapNotNull { row ->
                val title = table.value(row, "题名")
                if (title.isBlank()) return@mapNotNull null
                BookItem(
                    title = stripIndex(title),
                    author = table.value(row, "责任者"),
                    publication = table.value(row, "出版信息").ifBlank { table.value(row, "出版社") },
                    callNo = table.value(row, "索书号"),
                    holding = table.value(row, "馆藏").ifBlank { table.value(row, "馆藏地") },
                )
            }
        }

        // 退路二：与检索结果同构
        return parseSearchResults(html)
    }

    /** 把 `../opac/item.php?marc_no=…` 归一成 `item.php?marc_no=…`，交给仓库层补前缀。 */
    private fun normalizeDetailUrl(href: String): String? {
        if (href.isBlank()) return null
        return href
            .removePrefix("../opac/")
            .removePrefix("/opac/")
            .removePrefix("./opac/")
            .removePrefix("../")
    }

    /**
     * 解析分类树（`browse/cls_browsing_tree.php`、`newbook/newbook_cls_tree.php`）。
     *
     * 实测结构：
     * ```html
     * <div id="nodeA" name="nodeA" onClick="test(this)">
     *   <a href="javascript:expandTree('A',1)"><img …/></a>A 马列主义、毛泽东思想、邓小平理论
     * </div>
     * ```
     * 分类名在 `div` 的自有文本里（新书通报页则在 `<span title="…">` 上），
     * `value` 返回可直接传给书目页的 `cls` 分类号。
     *
     * 取不到 expandTree 节点时（例如学科导航页），回退为扫描普通链接。
     */
    fun parseClassTree(html: String): List<LabeledValue> {
        val doc = Html.parse(html)
        val expand = Regex("""expandTree\(\s*'([^']+)'""")

        val nodes = linkedMapOf<String, String>()
        for (anchor in doc.select("a[href*=expandTree]")) {
            val code = expand.find(anchor.attr("href"))?.groupValues?.get(1) ?: continue
            val holder = anchor.parent() ?: continue
            var name = holder.selectFirst("span[title]")?.attr("title")?.trim().orEmpty()
            if (name.isBlank()) name = holder.ownText().trim()
            if (name.isBlank()) name = holder.text().trim()
            // 节点文本自带分类号前缀（顶层是「A 马列主义…」，子类可能是「A1 马克思…」），
            // 去掉以免下面的 "$code $name" 拼出「A1 A1 马克思…」这种重复
            name = name.replace(Regex("""^[A-Za-z]{1,3}\d*\s+"""), "").trim()
            if (name.isNotBlank()) nodes[code] = name
        }
        if (nodes.isNotEmpty()) {
            return nodes.map { (code, name) -> LabeledValue(label = "$code $name", value = code) }
        }

        // 回退：普通链接（如学科导航页）
        val out = mutableListOf<LabeledValue>()
        val seen = mutableSetOf<String>()
        for (a in doc.select("a[href]")) {
            val name = a.normalizedText()
            if (name.isEmpty() || name.length > 30) continue
            val href = a.attr("href")
            if (href.isBlank() || href.startsWith("javascript")) continue
            if (seen.add(name)) out += LabeledValue(label = name, value = href)
        }
        return out
    }

    // ------------------------------------------------------------------ 信息发布（消息通知）✅

    /**
     * 解析「信息发布」（`info/info_guide.php`）——消息通知的数据来源。
     *
     * 实测结构：这一页是**通知分类的索引页**，四个分类各挂一个公告详情页：
     * ```html
     * <div class="infowrap"><ul>
     *   <li>
     *     <h5><a href="preg_arri_bulletin.php">预约到书</a></h5>
     *     <p>预约到馆图书列表</p>
     *   </li>
     *   …
     * </ul></div>
     * ```
     *
     * ⚠️ 早先这里用的是「扫描页面上的所有链接」，结果把整站导航菜单
     * （书目检索 / 热门推荐 / 我的图书馆…）都当成了通知。必须只取
     * `div.infowrap` 里的分类条目。
     */
    fun parseNotices(html: String): List<NoticeItem> {
        val doc = Html.parse(html)
        val items = doc.select("div.infowrap li").mapNotNull { li ->
            val anchor = li.selectFirst("h5 a") ?: return@mapNotNull null
            val name = anchor.normalizedText()
            if (name.isBlank()) return@mapNotNull null
            NoticeItem(
                category = "信息公告",
                title = name,
                detail = li.selectFirst("p")?.normalizedText().orEmpty(),
                url = anchor.attr("href"),
            )
        }
        if (items.isNotEmpty()) return items

        // 退路：页面结构变了时，至少不要把导航当成通知，宁可返回空
        return emptyList()
    }

    /**
     * 解析通知详情（公告页，如 `info/preg_arri_bulletin.php`）。
     *
     * - 没有记录时页面是一个 `div#err` 提示块
     * - 有记录时是一张表格（列名各页不同，因此不写死列名）
     */
    fun parseBulletin(html: String): BulletinDetail {
        val doc = Html.parse(html)

        doc.selectFirst("div#err")?.let { err ->
            val message = err.selectFirst("h3")?.normalizedText().orEmpty()
                .ifBlank { err.normalizedText() }
            return BulletinDetail(message = message)
        }

        val table = doc.select("table").firstOrNull { it.select("tr").size > 1 }
            ?: return BulletinDetail()

        val rows = table.select("tr")
        val headers = rows.first().select("th, td").map { it.normalizedText() }
        val data = rows.drop(1)
            .map { row -> row.select("th, td").map { it.normalizedText() } }
            .filter { cells -> cells.any { it.isNotBlank() } }

        return BulletinDetail(headers = headers, rows = data)
    }

    // ------------------------------------------------------------------ 学科参考 ✅

    /** 学科导航里的一个分组（如「哲学」下面挂着「马克思主义哲学」「中国哲学」…）。 */
    data class SubjectGroup(
        val name: String,
        val subjects: List<LabeledValue>,
    )

    /**
     * 解析「学科导航」（`shelf/xueke_catalog.php`）。
     *
     * 实测结构：
     * ```html
     * <div id="underlinemenu_small">
     *   <h6>哲学</h6>
     *   <ul><li><a href="xueke_book.php?name=…&clc=…&slc=…">马克思主义哲学</a></li>…</ul>
     * </div>
     * ```
     * 注意：**不能**用「扫描所有链接」的办法取学科——页面顶部的
     * 导航菜单（暂存书架、书目检索…）会被一并抓进来。
     */
    fun parseSubjects(html: String): List<SubjectGroup> {
        val doc = Html.parse(html)
        return doc.select("div[id=underlinemenu_small]").mapNotNull { group ->
            val groupName = group.selectFirst("h6")?.normalizedText().orEmpty()
            val subjects = group.select("ul li a").mapNotNull { a ->
                val label = a.normalizedText()
                if (label.isBlank()) null else LabeledValue(label, a.attr("href"))
            }
            if (subjects.isEmpty()) null else SubjectGroup(groupName, subjects)
        }
    }

    // ------------------------------------------------------------------ 读者荐购 ✅

    /**
     * 解析「新书目录推荐」（`asord/asord_cls_browse.php`，页面里叫「征订分类浏览」）。
     *
     * 实测表头：题名 / 责任者 / 出版信息 / 分类号 / 荐购人数 / 荐购
     * 每行有「荐购」按钮，指向 `asorditem.php`——那是**需要登录的提交页**，
     * 第一版不做写操作，因此这里只取书目信息，不取该链接。
     */
    fun parseAsordCatalog(html: String): List<AsordCatalogItem> {
        val doc = Html.parse(html)
        val table = Html.table(doc, listOf("题名", "荐购人数")) ?: return emptyList()
        return table.rows.mapNotNull { row ->
            val title = table.value(row, "题名")
            if (title.isBlank()) return@mapNotNull null
            AsordCatalogItem(
                title = stripIndex(title),
                author = table.value(row, "责任者"),
                publication = table.value(row, "出版信息"),
                classNo = table.value(row, "分类号"),
                recommendCount = table.value(row, "荐购人数"),
            )
        }
    }

    /**
     * 解析征订目录左侧的分类导航（`?cls_no=A` 形式）。
     *
     * 不能走通用的「扫描所有链接」——页面顶部还有一整套导航菜单。
     */
    fun parseAsordCatalogClasses(html: String): List<LabeledValue> {
        val doc = Html.parse(html)
        val clsNo = Regex("""[?&]cls_no=([^&"]+)""")
        val out = linkedMapOf<String, String>()
        for (anchor in doc.select("a[href*=cls_no]")) {
            val code = clsNo.find(anchor.attr("href"))?.groupValues?.get(1)?.trim() ?: continue
            val label = anchor.normalizedText()
            if (label.isNotBlank()) out[code] = label
        }
        return out.map { (code, name) -> LabeledValue(label = name, value = code) }
    }

    /** 征订目录的总记录数（`共 N 条记录`）。 */
    fun parseAsordCatalogTotal(html: String): Int? =
        Regex("""共\s*<?[^>]*>?\s*(\d+)\s*<?[^>]*>?\s*条记录""")
            .find(html)?.groupValues?.get(1)?.toIntOrNull()
            ?: Regex("""共\s*(\d+)\s*条记录""").find(Html.bodyText(Html.parse(html)))
                ?.groupValues?.get(1)?.toIntOrNull()

    // ------------------------------------------------------------------ 图书详情 ✅

    /** 详情页里我们关心的字段（标题、书目信息、馆藏表）。 */
    data class BookDetail(
        val title: String,
        val fields: List<LabeledValue>,
        val holdings: List<List<String>>,
        val holdingsHeader: List<String>,
    )

    /**
     * 解析图书详情（`opac/item.php?marc_no=…`）。
     *
     * 实测结构是 `<dl class="booklist">` 里的 `<dt>标签</dt><dd>值</dd>`：
     * ```
     * 题名/责任者:  示例书目:副题名/(英) 示例作者著 示例译者译
     * ```
     * 书名取「题名/责任者」中第一个 `/` 之前的部分。
     *
     * 馆藏表列：索书号 / 条码号 / 年卷期 / 馆藏地 / 书刊状态 / 还书位置 / 定位
     */
    fun parseBookDetail(html: String): BookDetail {
        val doc = Html.parse(html)

        val fields = parseDefinitionList(doc)
            .ifEmpty { Html.labelValuePairs(doc) }

        val title = fields
            .firstOrNull { it.label.replace(" ", "") == "题名/责任者" || it.label.contains("题名") }
            ?.value
            ?.substringBefore("/")
            ?.trim()
            .orEmpty()

        val holdingsTable = Html.table(doc, listOf("索书号", "馆藏地"))

        return BookDetail(
            title = title,
            // 书名已在页面顶部单独大字号展示，明细里去掉这一条，其余（出版发行项、
            // ISBN及定价、载体形态项、统一题名、其它题名、丛编项…）全部保留
            fields = fields.filterNot { it.label.replace(" ", "") == "题名/责任者" },
            holdings = holdingsTable?.rows.orEmpty(),
            holdingsHeader = holdingsTable?.headers.orEmpty(),
        )
    }

    /**
     * 解析详情页的书目字段。
     *
     * 实测：页面用**多个** `<dl class="booklist">`，每个只装一对
     * `<dt>标签</dt><dd>值</dd>`（题名/责任者、出版发行项、ISBN及定价…），
     * 所以必须遍历全部 `dl`，只取第一个会只剩书名。
     */
    private fun parseDefinitionList(doc: Document): List<LabeledValue> {
        val out = mutableListOf<LabeledValue>()
        for (dl in doc.select("dl.booklist")) {
            for (dt in dl.select("dt")) {
                val label = dt.normalizedText().trimEnd(':', '：').trim()
                if (label.isEmpty()) continue
                val next = dt.nextElementSibling()
                val value = if (next != null && next.tagName() == "dd") next.normalizedText() else ""
                out += LabeledValue(label, value)
            }
        }
        return out
    }
}
