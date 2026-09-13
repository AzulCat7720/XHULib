package com.xhulib.data.parse

import com.xhulib.data.Opac
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * 打真实服务器的解析器集成测试。
 *
 * 只访问**公开栏目**（书目检索、热门推荐、新书通报、分类浏览、学科参考、信息发布），
 * 不需要登录、不涉及任何账号信息。
 *
 * 服务器是**校内地址**，离校网络下会连不上；这种情况下用
 * [assumeTrue] 跳过而不是失败，方便在任意环境跑 `gradlew test`。
 */
class LiveEndpointTest {

    // 兜底检索超大类别（工业技术 / 经济）实测要 12~21 秒，测试超时放宽一些
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .build()

    private val reachable: Boolean by lazy {
        runCatching {
            val request = Request.Builder().url(Opac.url(Opac.SEARCH)).build()
            http.newCall(request).execute().use { true }
        }.getOrDefault(false)
    }

    private fun fetch(path: String): String {
        assumeTrue("连不上图书馆服务器（需校园网），跳过实测", reachable)
        val request = Request.Builder()
            .url(if (path.startsWith("http")) path else Opac.url(path))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
            .build()
        return http.newCall(request).execute().use { it.body?.string().orEmpty() }
    }

    private fun searchUrl(query: String) = Opac.url("opac/openlink.php") +
        "?strText=${java.net.URLEncoder.encode(query, "UTF-8")}" +
        "&strSearchType=title&match_flag=forward&doctype=ALL&displaypg=20" +
        "&sort=CATA_DATE&location=ALL&showmode=list&orderby=desc&historyCount=1"

    // ------------------------------------------------------------------ 书目检索

    @Test
    fun `真实检索结果能被正确解析`() {
        val html = fetch(searchUrl("数学"))
        assertTrue("应返回结果页", html.contains("search_book_list"))

        val items = CatalogParsers.parseSearchResults(html)
        assertTrue("应解析出至少 10 条书目，实际 ${items.size} 条", items.size >= 10)

        val first = items.first()
        assertTrue("题名不应为空", first.title.isNotBlank())
        assertTrue("题名不应带序号前缀: ${first.title}", !first.title.first().isDigit())
        assertTrue("应取到详情页地址", !first.detailUrl.isNullOrBlank())
        assertTrue("详情页地址应指向 item.php", first.detailUrl!!.contains("item.php"))

        println("检索「数学」解析到 ${items.size} 条，首条：${first.title} / ${first.author} / ${first.callNo}")
    }

    @Test
    fun `检索结果的责任者与出版信息不为空`() {
        val items = CatalogParsers.parseSearchResults(fetch(searchUrl("数学")))
        assumeTrue(items.isNotEmpty())
        val withAuthor = items.count { it.author.isNotBlank() }
        assertTrue("多数条目应有责任者，实际 $withAuthor/${items.size}", withAuthor >= items.size / 2)
    }

    // ------------------------------------------------------------------ 热门推荐

    @Test
    fun `真实热门推荐能被解析`() {
        val items = CatalogParsers.parseTopLend(fetch(Opac.TOP_LEND))
        assertTrue("热门借阅应解析出至少 5 条，实际 ${items.size} 条", items.size >= 5)
        assertTrue("应有题名", items.first().title.isNotBlank())
        assertTrue("应有借阅册次", items.first().loanCount.isNotBlank())
        println("热门推荐解析到 ${items.size} 条，首条：${items.first().title} 借阅 ${items.first().loanCount}")
    }

    /**
     * 回归：首页热门推荐的卡片要能点进详情，因此解析必须带出书名上的链接。
     */
    @Test
    fun `热门推荐条目带有详情链接`() {
        val items = CatalogParsers.parseTopLend(fetch(Opac.TOP_LEND))
        assumeTrue(items.isNotEmpty())
        val withLink = items.count { !it.detailUrl.isNullOrBlank() }
        assertTrue("绝大多数热门条目应带详情链接，实际 $withLink/${items.size}", withLink >= items.size * 9 / 10)
        assertTrue(
            "详情链接应指向 item.php，实际 ${items.first().detailUrl}",
            items.first().detailUrl!!.contains("item.php"),
        )
        // 归一化后不应残留 ../opac/ 前缀，否则仓库层会拼成 opac/../opac/item.php
        assertTrue(
            "详情链接不应带相对前缀，实际 ${items.first().detailUrl}",
            !items.first().detailUrl!!.startsWith(".."),
        )
    }

    // ------------------------------------------------------------------ 分类浏览 / 新书通报

    @Test
    fun `真实分类树能被解析`() {
        val tree = CatalogParsers.parseClassTree(fetch(Opac.CLS_BROWSING_TREE))
        assertTrue("中图法分类树应解析出至少 10 个分类，实际 ${tree.size} 个", tree.size >= 10)
        println("分类浏览树解析到 ${tree.size} 个分类，首个：${tree.first().label}")
    }

    /** 子类展开：`?cls=A&lvl=1` 会返回 A 的子类（A1/A2/…）连同其它大类。 */
    @Test
    fun `分类的子类能被取到`() {
        val html = fetch("${Opac.CLS_BROWSING_TREE}?s_doctype=all&cls=A&lvl=1")
        val subs = CatalogParsers.parseClassTree(html)
            .filter { it.value.length > 1 && it.value.startsWith("A") }
        assertTrue("A 类下应有子类，实际 ${subs.size} 个", subs.isNotEmpty())
        assertTrue("子类号都应以 A 开头：${subs.map { it.value }}", subs.all { it.value.startsWith("A") })
        println("A 类子类 ${subs.size} 个：${subs.take(6).joinToString { it.label }}")
    }

    @Test
    fun `真实新书通报分类树能被解析`() {
        val tree = CatalogParsers.parseClassTree(fetch(Opac.NEW_BOOK_TREE))
        assertTrue("新书通报分类树应解析出至少 10 个分类，实际 ${tree.size} 个", tree.size >= 10)
        println("新书通报树解析到 ${tree.size} 个分类，首个：${tree.first().label} / ${tree.first().value}")
    }

    @Test
    fun `分类树的 value 是可直接传回的分类号`() {
        val tree = CatalogParsers.parseClassTree(fetch(Opac.CLS_BROWSING_TREE))
        assumeTrue(tree.isNotEmpty())
        // 中图法顶层分类号是单个大写字母 A~Z
        assertTrue(
            "分类号应为单个大写字母，实际：${tree.map { it.value }}",
            tree.all { it.value.length == 1 && it.value[0].isUpperCase() },
        )
    }

    // ------------------------------------------------------------------ 分类浏览书目列表

    /**
     * 记录一个**图书馆系统自身的限制**：分类浏览页对超大类别（文学 I、工业技术 T、
     * 经济 F …）会直接返回 HTTP 500，而对中等类别（G/K/H/D）正常。
     *
     * 这条测试是为了在馆方修好之后能第一时间发现 —— 一旦不再 500 就该调整兜底逻辑。
     */
    @Test
    fun `分类浏览页对超大类别会返回服务器错误`() {
        val codes = mutableListOf<Int>()
        listOf("G", "I", "T").forEach { cls ->
            val conn = java.net.URL("${Opac.BASE_URL}browse/cls_browsing_book.php?s_doctype=all&cls=$cls")
                .openConnection() as java.net.HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            codes += runCatching { conn.responseCode }.getOrDefault(-1)
            conn.disconnect()
        }
        println("分类浏览 G/I/T 的 HTTP 状态：$codes")
        assertTrue("G 应正常", codes[0] == 200)
        // I / T 目前是 500；若馆方修好了会变成 200，那时这条断言会失败并提醒我们
        assertTrue("I 或 T 至少有一个仍应是 500（馆方限制）", codes[1] == 500 || codes[2] == 500)
    }

    /**
     * 兜底方案：分类浏览 500 时改用**按分类号检索**（`strSearchType=coden`），
     * 检索接口能正常处理超大类别。
     */
    @Test
    fun `超大类别可用检索接口兜底`() {
        // I / I2 / F 实测正确；T 会返回无关结果，因此仓库层会按分类号过滤掉
        listOf("I", "I2", "F").forEach { cls ->
            val items = CatalogParsers.parseSearchResults(fetch(codenSearchUrl(cls)))
                .filter { it.callNo.startsWith(cls) }
            assertTrue("分类号 $cls 的兜底检索应有结果，实际 ${items.size} 条", items.isNotEmpty())
            println("兜底检索 $cls -> ${items.size} 条，首个「${items.first().title}」${items.first().callNo}")
        }
    }

    /**
     * 检索接口对个别大类（实测 T 工业技术）返回的结果与分类号无关 ——
     * 仓库层必须把它们过滤掉，宁可显示「暂无」也不能给错书。
     */
    @Test
    fun `兜底检索会过滤掉不匹配分类号的结果`() {
        val raw = CatalogParsers.parseSearchResults(fetch(codenSearchUrl("T")))
        val kept = raw.filter { it.callNo.startsWith("T") }
        assertTrue("原始结果应有内容（说明接口确实返回了东西）", raw.isNotEmpty())
        assertTrue("T 的无关结果应被全部过滤掉，实际剩 ${kept.size} 条", kept.isEmpty())
        println("T 兜底检索：原始 ${raw.size} 条，过滤后 ${kept.size} 条")
    }

    private fun codenSearchUrl(cls: String) = Opac.url("opac/openlink.php") +
        "?strText=$cls&strSearchType=coden&match_flag=forward&doctype=ALL" +
        "&displaypg=20&sort=CATA_DATE&location=ALL&showmode=list&orderby=desc&historyCount=1"


    @Test
    fun `真实分类浏览书目列表能被解析`() {
        val items = CatalogParsers.parseBookList(
            fetch("browse/cls_browsing_book.php?s_doctype=all&cls=A"),
        )
        assertTrue("应解析出书目，实际 ${items.size} 条", items.size >= 3)

        val first = items.first()
        assertTrue("题名不应为空", first.title.isNotBlank())
        assertTrue("题名不应带序号前缀: ${first.title}", !first.title.first().isDigit())
        assertTrue("应取到详情页地址", !first.detailUrl.isNullOrBlank())
        assertTrue("详情页地址应指向 item.php，实际 ${first.detailUrl}", first.detailUrl!!.contains("item.php"))
        println("分类浏览 A 类解析到 ${items.size} 条，首条：${first.title} / ${first.author}")
    }

    // ------------------------------------------------------------------ 学科参考

    @Test
    fun `真实学科参考能被解析`() {
        val groups = CatalogParsers.parseSubjects(fetch(Opac.SUBJECT))
        val subjects = groups.flatMap { it.subjects }
        assertTrue("学科导航应解析出条目，实际 ${subjects.size} 个", subjects.size >= 20)

        // 关键回归：顶部导航（暂存书架 / 书目检索 / 我的图书馆…）不能被当成学科
        val bogus = subjects.map { it.label }.filter {
            it.contains("暂存书架") || it.contains("书目检索") || it.contains("我的图书馆") ||
                it.contains("新书通报") || it.contains("信息发布")
        }
        assertTrue("学科列表里混入了导航项：$bogus", bogus.isEmpty())

        assertTrue("学科链接应指向 xueke_book.php", subjects.first().value.contains("xueke_book.php"))
        println("学科参考解析到 ${groups.size} 组共 ${subjects.size} 个学科，首组：${groups.first().name}")
    }

    // ------------------------------------------------------------------ 信息发布

    @Test
    fun `真实信息发布页能被访问`() {
        val html = fetch(Opac.NEWSLETTER)
        assertTrue("信息发布页应可访问", html.isNotBlank())
        val notices = CatalogParsers.parseNotices(html)
        println("信息发布解析到 ${notices.size} 条通知：${notices.joinToString { it.title }}")
    }

    /**
     * 回归：通知页曾经把整站导航菜单也当成通知显示出来。
     *
     * 信息发布页只有四个分类，绝不能出现「书目检索 / 热门推荐 / 我的图书馆」这类栏目名。
     */
    @Test
    fun `信息发布不会把导航菜单当成通知`() {
        val notices = CatalogParsers.parseNotices(fetch(Opac.NEWSLETTER))
        assertTrue("应解析出四个通知分类，实际 ${notices.size} 条", notices.size == 4)

        val expected = listOf("预约到书", "委托到书", "超期欠款", "超期催还")
        assertEquals("通知分类应与站点一致", expected, notices.map { it.title })

        val junk = listOf("书目检索", "热门推荐", "分类浏览", "新书通报", "期刊导航",
            "读者荐购", "学科参考", "信息发布", "我的图书馆", "暂存书架", "登录")
        val bogus = notices.map { it.title }.filter { it in junk }
        assertTrue("通知里混入了导航项：$bogus", bogus.isEmpty())
        assertTrue("每条通知都应有说明文字", notices.all { it.detail.isNotBlank() })
        println("通知分类：${notices.joinToString { "${it.title}(${it.detail})" }}")
    }

    // ------------------------------------------------------------------ 图书详情

    @Test
    fun `真实图书详情页能被解析`() {
        val items = CatalogParsers.parseSearchResults(fetch(searchUrl("数学")))
        assumeTrue(items.isNotEmpty())
        val detailUrl = items.first().detailUrl ?: return

        val fullUrl = Opac.url("opac/" + detailUrl.removePrefix("./"))
        val detail = CatalogParsers.parseBookDetail(fetch(fullUrl))

        assertTrue("详情页应有书名", detail.title.isNotBlank())
        // 关键回归：书名必须是书的名字，不能取成网页标题
        assertTrue(
            "书名不应是网站名，实际：${detail.title}",
            !detail.title.contains("西华大学图书馆") && !detail.title.contains("Online Public Access"),
        )
        assertTrue("详情页应有书目字段，实际 ${detail.fields.size} 项", detail.fields.size >= 3)
        assertTrue("详情页应有馆藏表，实际 ${detail.holdings.size} 行", detail.holdings.isNotEmpty())
        assertTrue(
            "馆藏表应含索书号列",
            detail.holdingsHeader.any { it.contains("索书号") },
        )
        println(
            "详情页书名：${detail.title}，字段 ${detail.fields.size} 项" +
                "（${detail.fields.first().label}），馆藏 ${detail.holdings.size} 行",
        )
    }

    /** 通知详情：没有记录时页面是 `div#err` 提示块。 */
    @Test
    fun `通知详情能解析出无记录提示`() {
        val detail = CatalogParsers.parseBulletin(fetch("info/preg_arri_bulletin.php"))
        assertTrue("应解析出「没有记录」提示，实际「${detail.message}」", detail.message.isNotBlank())
        assertTrue("提示应包含「记录」二字", detail.message.contains("记录"))
        println("预约到书公告 -> ${detail.message}")
    }

    /** 通知详情：另一套版式是表格（列名各页不同，不能写死）。 */
    @Test
    fun `通知详情能解析出表格版式`() {
        val detail = CatalogParsers.parseBulletin(fetch("info/hasten_return_bulletin.php"))
        assertTrue("应解析出表头，实际 ${detail.headers}", detail.headers.isNotEmpty())
        println("超期催还公告表头 -> ${detail.headers.joinToString()}")
    }

    // ------------------------------------------------------------------ 读者荐购

    /**
     * 契约测试：图书馆顶栏的「读者荐购」指向 `asord/asord_hist.php`。
     *
     * App 里「我的 → 读者荐购」就是按这个地址做的（该页是荐购专区，
     * 内含 荐购历史 / 读者荐购 / 新书目录推荐 三个子标签）。
     * 如果哪天站点改了入口，这条会失败，提醒同步修改。
     */
    @Test
    fun `站点顶栏的读者荐购入口指向 asord_hist`() {
        val html = fetch(Opac.SEARCH_ADV)
        val anchor = Regex("""(?s)<a[^>]*href="([^"]*)"[^>]*>(.*?)</a>""")
            .findAll(html)
            .firstOrNull { m ->
                m.groupValues[2].replace(Regex("""<[^>]*>|\s"""), "") == "读者荐购"
            }
        assertTrue("顶栏应能找到「读者荐购」入口", anchor != null)
        val href = anchor!!.groupValues[1]
        assertTrue("「读者荐购」应指向 asord_hist.php，实际 $href", href.contains("asord_hist.php"))
        println("顶栏「读者荐购」→ $href")
    }

    /** 荐购专区的三个子标签，App 里的标签页顺序与之一致。 */
    @Test
    fun `荐购专区包含三个子标签`() {
        val html = fetch(Opac.ASORD_HISTORY)
        listOf("asord_hist.php", "asord_redr.php", "asord_cls_browse.php").forEach { target ->
            assertTrue("荐购专区应包含子标签链接 $target", html.contains(target))
        }
        val text = Html.bodyText(Html.parse(html))
        listOf("荐购历史", "读者荐购", "新书目录推荐").forEach { label ->
            assertTrue("荐购专区应显示子标签「$label」", text.contains(label))
        }
    }

    @Test
    fun `真实征订目录能被解析`() {
        val items = CatalogParsers.parseAsordCatalog(fetch(Opac.ASORD_CLS))
        assertTrue("应解析出征订书目，实际 ${items.size} 条", items.size >= 5)

        val first = items.first()
        assertTrue("题名不应为空", first.title.isNotBlank())
        assertTrue("应取到荐购人数", first.recommendCount.isNotBlank())
        assertTrue("题名不应带序号前缀: ${first.title}", !first.title.first().isDigit())
        println("征订目录解析到 ${items.size} 条，首条：${first.title} / ${first.classNo} / ${first.recommendCount} 人荐购")
    }

    /**
     * 回归：分类导航必须只取 `?cls_no=` 那批链接，
     * 不能把页面顶部的栏目菜单（暂存书架 / 我的图书馆…）当成分类。
     */
    @Test
    fun `征订目录的分类导航能被解析`() {
        val classes = CatalogParsers.parseAsordCatalogClasses(fetch(Opac.ASORD_CLS))
        assertTrue("应解析出中图法大类，实际 ${classes.size} 个", classes.size >= 20)

        val bogus = classes.map { it.label }.filter {
            it.contains("暂存书架") || it.contains("我的图书馆") ||
                it.contains("书目检索") || it.contains("信息发布")
        }
        assertTrue("分类里混入了导航项：$bogus", bogus.isEmpty())
        assertTrue(
            "分类号应是中图法大类号，实际：${classes.map { it.value }}",
            classes.all { it.value.length <= 2 },
        )
        println("征订分类解析到 ${classes.size} 个，首个：${classes.first().label}")
    }

    /**
     * 分页回归：A 类征订目录共 38 条、每页 20 条，服务端分 2 页。
     *
     * 早先只取第一页，借阅记录多的同学会被截断。
     */
    @Test
    fun `征订目录分页能被识别`() {
        val page1Html = fetch("asord/asord_cls_browse.php?cls_no=A")
        val total = Html.totalPages(Html.parse(page1Html))
        assertTrue("A 类征订目录应至少 2 页，实际 $total 页", total >= 2)

        val page1 = CatalogParsers.parseAsordCatalog(page1Html)
        val page2 = CatalogParsers.parseAsordCatalog(
            fetch("asord/asord_cls_browse.php?cls_no=A&page=2"),
        )
        assertTrue("第二页应有数据，实际 ${page2.size} 条", page2.isNotEmpty())
        assertTrue(
            "两页内容不应重合，首页都是「${page1.first().title}」",
            page1.first().title != page2.first().title,
        )
        println("征订目录共 $total 页，第 1 页 ${page1.size} 条、第 2 页 ${page2.size} 条")
    }

    // ------------------------------------------------------------------ 热门推荐四个标签

    /**
     * 四个标签的排行数字列名各不相同（借阅册次 / 评价人次 / 收藏人次 / 浏览次数），
     * 解析器要能分别认出来。
     */
    @Test
    fun `热门推荐四个标签都能解析`() {
        val expected = mapOf(
            Opac.TOP_LEND to "借阅册次",
            Opac.TOP_SCORE to "评价人次",
            Opac.TOP_SHELF to "收藏人次",
            Opac.TOP_BOOK to "浏览次数",
        )
        expected.forEach { (path, metric) ->
            val items = CatalogParsers.parseTopList(fetch(path))
            assertTrue("$path 应解析出至少 5 条，实际 ${items.size} 条", items.size >= 5)
            val first = items.first()
            assertTrue("$path 的题名不应为空", first.title.isNotBlank())
            assertEquals("$path 的排行数字列名应为 $metric", metric, first.metricLabel)
            assertTrue("$path 应带详情链接", !first.detailUrl.isNullOrBlank())
            println("$path -> ${items.size} 条，首条「${first.title}」$metric=${first.loanCount}")
        }
    }

    // ------------------------------------------------------------------ 期刊导航

    @Test
    fun `期刊导航按首字母能取到期刊`() {
        listOf(Opac.PERI_BY_TITLE_CN, Opac.PERI_BY_TITLE_EN).forEach { path ->
            val items = CatalogParsers.parsePeriodicals(fetch("$path?title=A"))
            assertTrue("$path 的 A 字母下应有期刊，实际 ${items.size} 条", items.isNotEmpty())
            assertTrue("刊名不应为空", items.first().title.isNotBlank())
            println("$path?title=A -> ${items.size} 条，首个「${items.first().title}」ISSN=${items.first().issn}")
        }
    }

    /** 回归：刊名拼音 / 西文字母下的期刊也要能点进详情。 */
    @Test
    fun `期刊导航的期刊带有详情链接`() {
        listOf(Opac.PERI_BY_TITLE_CN, Opac.PERI_BY_TITLE_EN).forEach { path ->
            val items = CatalogParsers.parsePeriodicals(fetch("$path?title=A"))
            assumeTrue(items.isNotEmpty())
            val withLink = items.count { !it.detailUrl.isNullOrBlank() }
            assertTrue(
                "$path 的期刊大多应带详情链接，实际 $withLink/${items.size}",
                withLink >= items.size * 9 / 10,
            )
            assertTrue(
                "$path 的详情链接应指向 item.php，实际 ${items.first().detailUrl}",
                items.first().detailUrl!!.contains("item.php"),
            )
            assertTrue(
                "$path 的详情链接不应残留相对前缀，实际 ${items.first().detailUrl}",
                !items.first().detailUrl!!.startsWith(".."),
            )
            println("$path 详情链接 OK，例如 ${items.first().detailUrl}")
        }
    }

    /**
     * 年度订购期刊：实测该页对**未登录用户不下发数据**（只有筛选表单，没有结果表），
     * 所以这里只验证页面可达、结构可识别、解析不抛异常，不硬性要求条数。
     * 将来图书馆若对匿名用户开放数据，解析器本就能处理（表头已支持）。
     */
    @Test
    fun `期刊年度订购页面结构可识别`() {
        val html = fetch(Opac.PERI_BY_YEAR)
        assertTrue("应能打开年度订购页", html.isNotBlank())
        val body = Html.bodyText(Html.parse(html))
        assertTrue("页面应显示「订购年度」筛选项", body.contains("订购年度"))

        val items = CatalogParsers.parsePeriodicals(html)
        println("年度订购：匿名访问解析到 ${items.size} 条（该栏目对匿名用户不下发数据）")
    }

    @Test
    fun `期刊学科树能解析`() {
        val classes = CatalogParsers.parsePeriodicalClassTree(fetch(Opac.PERI_CLASS_TREE))
        assertTrue("期刊学科树应解析出至少 20 个学科，实际 ${classes.size} 个", classes.size >= 20)
        // 名字是 URL 编码的，必须解码成中文
        assertTrue(
            "学科名应已解码成中文，实际首个：${classes.first().label}",
            classes.first().label.any { it.code > 0x4E00 },
        )
        println("期刊学科树 -> ${classes.size} 个学科，首个「${classes.first().label}」(${classes.first().value})")
    }

    @Test
    fun `期刊学科下能取到期刊`() {
        val classes = CatalogParsers.parsePeriodicalClassTree(fetch(Opac.PERI_CLASS_TREE))
        assumeTrue(classes.isNotEmpty())
        val top = classes.first()
        val items = CatalogParsers.parseBookList(
            fetch("${Opac.PERI_CLASS_LIST}?classid=${top.value}"),
        )
        assertTrue("学科「${top.label}」下应有期刊，实际 ${items.size} 条", items.isNotEmpty())
        println("学科「${top.label}」-> ${items.size} 条期刊，首个「${items.first().title}」")
    }
}
