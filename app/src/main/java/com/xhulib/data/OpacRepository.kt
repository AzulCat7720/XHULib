package com.xhulib.data

import androidx.annotation.StringRes
import com.xhulib.R
import com.xhulib.data.model.AsordEntry
import com.xhulib.data.model.BookItem
import com.xhulib.data.model.CreditEntry
import com.xhulib.data.model.DelegateEntry
import com.xhulib.data.model.HoldEntry
import com.xhulib.data.model.LoanRecord
import com.xhulib.data.model.NoticeItem
import com.xhulib.data.model.Paged
import com.xhulib.data.model.ReaderCard
import com.xhulib.data.model.ReaderOverview
import com.xhulib.data.model.SearchHistoryEntry
import com.xhulib.data.model.ShelfItem
import com.xhulib.data.net.OpacException
import com.xhulib.data.parse.CatalogParsers
import com.xhulib.data.parse.Html
import com.xhulib.data.parse.ReaderParsers
import com.xhulib.data.store.PageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/** 本地会话在服务端已失效——界面应引导用户重新登录（输一次验证码即可）。 */
class SessionExpiredException : Exception("session expired")

/**
 * OPAC 数据仓库：把「取页面 → 解析 → 领域模型」封成一个个挂起函数。
 *
 * 所有需要登录的栏目都会检查返回的是不是登录页，是则抛 [SessionExpiredException]。
 *
 * 每一页抓成功都会落盘缓存；网络不通时退回缓存并标记为离线数据
 * （见 [DataFreshness]），这样没连校园网也能看上次的内容。
 */
class OpacRepository(
    private val session: SessionManager,
    private val cache: PageCache,
) {

    /** 需要登录的页面：抓取 → 校验登录态 → 缓存；网络失败则回退缓存。 */
    private suspend fun page(path: String): String = withContext(Dispatchers.IO) {
        val client = session.requireClient()
        val cached = cache.get(path)

        val fresh = try {
            client.fetch(path)
        } catch (e: OpacException) {
            cached?.let {
                DataFreshness.markOffline(it.savedAt)
                return@withContext it.html
            }
            throw e
        }

        // 空白页或登录页说明服务端认为会话已失效（汇文对失效会话有时返回空内容）。
        // 注意：这种情况**不能**写进缓存，否则登录页会被当成离线数据展示。
        if (fresh.isBlank() || client.isLoginPage(fresh)) {
            SessionEvents.notifyExpired()
            throw SessionExpiredException()
        }

        cache.put(path, fresh)
        DataFreshness.markOnline()
        fresh
    }

    // ------------------------------------------------------------------ 我的图书馆

    /** 需要登录的分页列表：`?page=N`。 */
    private suspend fun pagedPage(path: String, page: Int): String {
        val url = if (page <= 1) path else "$path?page=$page"
        return page(url)
    }


    suspend fun readerCard(): ReaderCard =
        ReaderParsers.parseReaderCard(page(Opac.READER_ID_CARD))

    suspend fun overview(): ReaderOverview =
        ReaderParsers.parseOverview(page(Opac.READER_ID_CARD))

    /**
     * 一次取页同时得到证件信息与概览。
     *
     * 「我的」页两者都要，分开调用会重复请求同一个页面。
     */
    suspend fun readerCardAndOverview(): Pair<ReaderCard, ReaderOverview> {
        val html = page(Opac.READER_ID_CARD)
        return ReaderParsers.parseReaderCard(html) to ReaderParsers.parseOverview(html)
    }

    suspend fun currentLoans(): List<LoanRecord> =
        ReaderParsers.parseCurrentLoans(page(Opac.READER_CURRENT_LOANS))

    suspend fun loanQuota(): Pair<Int?, Int?> =
        ReaderParsers.parseLoanQuota(page(Opac.READER_CURRENT_LOANS))

    suspend fun loanHistory(page: Int = 1): Paged<LoanRecord> {
        val html = pagedPage(Opac.READER_LOAN_HISTORY, page)
        return Paged(
            items = ReaderParsers.parseLoanHistory(html),
            page = page,
            totalPages = Html.totalPages(Html.parse(html)),
        )
    }

    suspend fun asordHistory(page: Int = 1): Paged<AsordEntry> {
        val html = pagedPage(Opac.READER_ASORD_HISTORY, page)
        return Paged(
            items = ReaderParsers.parseAsordHistory(html),
            page = page,
            totalPages = Html.totalPages(Html.parse(html)),
        )
    }

    suspend fun holds(): List<HoldEntry> =
        ReaderParsers.parseHolds(page(Opac.READER_HOLDS))

    suspend fun delegates(): List<DelegateEntry> =
        ReaderParsers.parseDelegates(page(Opac.READER_DELEGATES))

    /**
     * 我的书架。
     *
     * 这一页默认**不选中任何书架**，直接取只会拿到一张空表。所以先看有没有书目；
     * 没有就从页面的「书架列表」里取出各书架的链接逐个再取，并把书架名标在条目上。
     */
    suspend fun bookshelf(): List<ShelfItem> = groupedShelfItems(Opac.READER_BOOKSHELF)

    /**
     * 「分组式」栏目（我的书架 / 我的课程）的通用取法。
     *
     * 这两页默认**不选中任何分组**，直接取只会拿到一张空表。所以要先把页面上的
     * 分组入口（`?classid=…` 这类只有查询串的链接）取出来，逐个再请求一次，
     * 并把分组名标在条目上。
     */
    private suspend fun groupedShelfItems(path: String): List<ShelfItem> {
        val html = page(path)
        val direct = ReaderParsers.parseShelfItems(html)
        if (direct.isNotEmpty()) return direct

        val groups = ReaderParsers.parseShelfLinks(html)
        if (groups.isEmpty()) return emptyList()

        return groups.flatMap { group ->
            // 某个分组取失败（例如会话刚好过期）不该让整页报错，跳过即可
            val items = try {
                ReaderParsers.parseShelfItems(page("$path?${group.value}"))
            } catch (e: Exception) {
                emptyList()
            }
            items.map { it.copy(shelf = group.label) }
        }.distinctBy { it.callNo + it.title }
    }

    suspend fun courses(): List<ShelfItem> = groupedShelfItems(Opac.READER_COURSES)

    suspend fun credits(page: Int = 1): Paged<CreditEntry> {
        val html = pagedPage(Opac.READER_CREDITS, page)
        return Paged(
            items = ReaderParsers.parseCredits(html),
            page = page,
            totalPages = Html.totalPages(Html.parse(html)),
        )
    }

    suspend fun searchHistory(page: Int = 1): Paged<SearchHistoryEntry> {
        val html = pagedPage(Opac.READER_SEARCH_HISTORY, page)
        return Paged(
            items = ReaderParsers.parseSearchHistory(html),
            page = page,
            totalPages = Html.totalPages(Html.parse(html)),
        )
    }

    /** 书刊遗失 / 账目清单 / 我的书评 / 违章缴款 这些「可能为空」的栏目只关心有没有内容。 */
    suspend fun isEmptySection(path: String): Boolean =
        Html.isEmptyRecord(Html.parse(page(path)))

    // ------------------------------------------------------------------ 公开栏目

    /**
     * 「热门推荐」的四个标签。
     *
     * 站点上还有第五个「借阅关系图」，是纯 JS 可视化、没有可解析的数据表，故不做。
     */
    enum class HotList(val path: String, @StringRes val labelRes: Int) {
        Lend(Opac.TOP_LEND, R.string.hot_tab_lend),
        Score(Opac.TOP_SCORE, R.string.hot_tab_score),
        Shelf(Opac.TOP_SHELF, R.string.hot_tab_shelf),
        Book(Opac.TOP_BOOK, R.string.hot_tab_book),
    }

    /** 按标签取热门排行。 */
    suspend fun hotList(kind: HotList): List<BookItem> =
        CatalogParsers.parseTopList(fetchPublic(kind.path))

    // ------------------------------------------------------------------ 期刊导航（公开）

    /** 刊名拼音导航 / 西文字母导航：按首字母取期刊。 */
    suspend fun periodicalsByLetter(byTitle: String, letter: String): List<com.xhulib.data.model.Periodical> {
        val path = if (byTitle == LETTER_CN) Opac.PERI_BY_TITLE_CN else Opac.PERI_BY_TITLE_EN
        return CatalogParsers.parsePeriodicals(fetchPublic("$path?title=${encode(letter)}"))
    }

    /** 期刊学科导航的分类树。 */
    suspend fun periodicalClassTree(): List<com.xhulib.data.model.LabeledValue> =
        CatalogParsers.parsePeriodicalClassTree(fetchPublic(Opac.PERI_CLASS_TREE))

    /**
     * 期刊学科导航：某个学科下的期刊。
     *
     * 这一页的输出和「分类浏览」同构（`div.list_books` + 详情链接），
     * 所以直接复用 `parseBookList`，再映射成 [com.xhulib.data.model.Periodical]。
     */
    suspend fun periodicalsByClass(classId: String?): List<com.xhulib.data.model.Periodical> {
        val path = buildString {
            append(Opac.PERI_CLASS_LIST)
            if (!classId.isNullOrBlank()) append("?classid=").append(encode(classId))
        }
        return CatalogParsers.parseBookList(fetchPublic(path)).map { item ->
            com.xhulib.data.model.Periodical(
                title = item.title,
                publisher = item.publication,
                callNo = item.callNo,
                detailUrl = item.detailUrl,
            )
        }
    }

    /** 年度订购期刊。 */
    suspend fun subscribedPeriodicals(year: String? = null): List<com.xhulib.data.model.Periodical> {
        val path = buildString {
            append(Opac.PERI_BY_YEAR)
            if (!year.isNullOrBlank()) append("?s_year=").append(encode(year))
        }
        return CatalogParsers.parsePeriodicals(fetchPublic(path))
    }

    suspend fun topLend(category: String? = null): List<BookItem> {
        val path = buildString {
            append(Opac.TOP_LEND)
            if (!category.isNullOrBlank()) append("?cls=").append(encode(category))
        }
        return CatalogParsers.parseTopLend(fetchPublic(path))
    }

    /**
     * 新书通报书目（`newbook/newbook_cls_book.php`）。
     *
     * 实测参数：`back_days`（回溯天数）+ `s_doctype`（文献类型）+ `loca_code`（馆藏地）+ `cls`（分类号）。
     */
    suspend fun newBooks(classNo: String? = null, backDays: Int = 30): List<BookItem> {
        val path = buildString {
            append(Opac.NEW_BOOK_BOOK)
            append("?back_days=").append(backDays)
            append("&s_doctype=all&loca_code=")
            if (!classNo.isNullOrBlank()) append("&cls=").append(encode(classNo))
        }
        return CatalogParsers.parseBookList(fetchPublic(path))
    }

    suspend fun newBookTree(): List<com.xhulib.data.model.LabeledValue> =
        CatalogParsers.parseClassTree(fetchPublic(Opac.NEW_BOOK_TREE))

    /**
     * 分类浏览书目（`browse/cls_browsing_book.php`）。
     *
     * 实测分类号参数名是 `cls`（不是 `cls_no`），且必须带 `s_doctype`，
     * 否则页面返回空。
     */
    suspend fun browseBooks(classNo: String? = null): List<BookItem> {
        if (classNo.isNullOrBlank()) {
            return CatalogParsers.parseBookList(
                fetchPublic("${Opac.CLS_BROWSING_BOOK}?s_doctype=all"),
            )
        }
        val path = "${Opac.CLS_BROWSING_BOOK}?s_doctype=all&cls=${encode(classNo)}"
        val direct = try {
            CatalogParsers.parseBookList(fetchPublic(path))
        } catch (e: OpacException) {
            // 分类浏览页对超大类（文学 / 工业技术 / 经济 …）会返回 500 ——
            // 这是图书馆系统自身的限制，改用检索接口按分类号查，效果一样。
            emptyList()
        }
        return direct.ifEmpty { searchByCallNumber(classNo) }
    }

    /**
     * 按分类号检索（`coden`）。
     *
     * 分类浏览页的兜底：该页面遇到超大类别会 500，而检索接口能正常处理。
     */
    private suspend fun searchByCallNumber(classNo: String): List<BookItem> {
        val params = buildString {
            append("strText=").append(encode(classNo))
            append("&strSearchType=coden&match_flag=forward&doctype=ALL")
            append("&displaypg=20&sort=CATA_DATE&location=ALL&showmode=list")
            append("&orderby=desc&historyCount=1")
        }
        val items = CatalogParsers.parseSearchResults(fetchPublic("opac/openlink.php?$params"))
        // 检索接口对个别大类（实测 T 工业技术）会返回与分类号完全无关的结果。
        // 与其展示错误的书，不如只保留分类号确实匹配的条目。
        return items.filter { it.callNo.startsWith(classNo) }
    }

    /**
     * 取某个中图法大类下的**子类**。
     *
     * 展开靠 `?cls=<父类>&lvl=<层级>` —— 服务端返回的树里同时含父类与其它大类，
     * 因此按「以父类号开头且更长」过滤。
     */
    suspend fun browseSubClasses(parent: String): List<com.xhulib.data.model.LabeledValue> {
        if (parent.isBlank()) return emptyList()
        val html = fetchPublic(
            "${Opac.CLS_BROWSING_TREE}?s_doctype=all&cls=${encode(parent)}&lvl=1",
        )
        return CatalogParsers.parseClassTree(html)
            .filter { it.value.length > parent.length && it.value.startsWith(parent) }
    }

    suspend fun browseTree(): List<com.xhulib.data.model.LabeledValue> =
        CatalogParsers.parseClassTree(fetchPublic(Opac.CLS_BROWSING_TREE))

    /**
     * 学科导航（`shelf/xueke_catalog.php`）。
     *
     * 只取页面中部「学科导航」区块里的学科，**不会**混入顶部导航菜单。
     */
    suspend fun subjectCatalog(): List<com.xhulib.data.model.LabeledValue> =
        CatalogParsers.parseSubjects(fetchPublic(Opac.SUBJECT)).flatMap { it.subjects }

    /** 带分组信息的学科导航（分组名如「哲学」「经济学」）。 */
    suspend fun subjectGroups(): List<CatalogParsers.SubjectGroup> =
        CatalogParsers.parseSubjects(fetchPublic(Opac.SUBJECT))

    suspend fun notices(): List<NoticeItem> =
        CatalogParsers.parseNotices(fetchPublic(Opac.NEWSLETTER))

    /** 通知详情：`path` 是通知条目里的相对文件名（如 `preg_arri_bulletin.php`）。 */
    suspend fun noticeDetail(path: String): com.xhulib.data.model.BulletinDetail =
        CatalogParsers.parseBulletin(fetchPublic("info/$path"))

    suspend fun asordHistoryPublic(): List<AsordEntry> =
        ReaderParsers.parseAsordHistory(fetchPublic(Opac.ASORD_HISTORY))

    // ------------------------------------------------------------------ 读者荐购（只读）

    /**
     * 新书目录推荐（征订分类浏览）。
     *
     * @param classNo 中图法分类号，如 `A`、`I`；为空时取页面默认分类
     */
    suspend fun asordCatalog(
        classNo: String? = null,
        page: Int = 1,
    ): Paged<com.xhulib.data.model.AsordCatalogItem> {
        val path = buildString {
            append(Opac.ASORD_CLS)
            append("?")
            if (!classNo.isNullOrBlank()) append("cls_no=").append(encode(classNo)).append("&")
            append("page=").append(page)
        }
        val html = fetchPublic(path)
        return Paged(
            items = CatalogParsers.parseAsordCatalog(html),
            page = page,
            totalPages = Html.totalPages(Html.parse(html)),
        )
    }

    /** 征订目录的分类导航。 */
    suspend fun asordCatalogClasses(): List<com.xhulib.data.model.LabeledValue> =
        CatalogParsers.parseAsordCatalogClasses(fetchPublic(Opac.ASORD_CLS))

    /** 公开页面：无需登录，即使会话失效也能取（热门推荐、新书通报等）。 */
    private suspend fun fetchPublic(path: String): String = withContext(Dispatchers.IO) {
        val cached = cache.get(path)
        try {
            session.requireClient().fetch(path).also { fresh ->
                if (fresh.isNotBlank()) {
                    cache.put(path, fresh)
                    DataFreshness.markOnline()
                }
            }
        } catch (e: OpacException) {
            cached?.let {
                DataFreshness.markOffline(it.savedAt)
                it.html
            } ?: throw e
        }
    }

    // ------------------------------------------------------------------ 书目检索

    /**
     * 书目检索（`opac/openlink.php`）。
     *
     * 参数取自搜索页表单，已在真实页面验证。
     */
    suspend fun search(
        query: String,
        searchType: String = "title",
        matchFlag: String = "forward",
        docType: String = "ALL",
        pageSize: Int = 20,
        sort: String = "CATA_DATE",
        location: String = "ALL",
        orderBy: String = "desc",
        page: Int = 1,
    ): List<BookItem> {
        val params = buildString {
            append("strText=").append(encode(query))
            append("&strSearchType=").append(encode(searchType))
            append("&match_flag=").append(encode(matchFlag))
            append("&doctype=").append(encode(docType))
            append("&displaypg=").append(pageSize)
            append("&sort=").append(encode(sort))
            append("&location=").append(encode(location))
            append("&showmode=list")
            append("&orderby=").append(encode(orderBy))
            append("&page=").append(page)
            append("&historyCount=1")
        }
        return CatalogParsers.parseSearchResults(fetchPublic("opac/openlink.php?$params"))
    }

    /**
     * 图书详情。
     *
     * [marcUrl] 是检索结果里的相对地址（如 `item.php?marc_no=…`），
     * 它相对于 `/opac/`，这里统一补全。
     */
    suspend fun bookDetail(marcUrl: String) = CatalogParsers.parseBookDetail(
        fetchPublic(normalizeMarcUrl(marcUrl)),
    )

    private fun normalizeMarcUrl(marcUrl: String): String = when {
        marcUrl.startsWith("http") -> marcUrl
        marcUrl.startsWith("opac/") -> marcUrl
        else -> "opac/${marcUrl.removePrefix("./")}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    companion object {
        /** [periodicalsByLetter] 的 byTitle 取值：中文刊名拼音 / 西文字母。 */
        const val LETTER_CN = "cn"
        const val LETTER_EN = "en"
    }
}
