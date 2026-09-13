package com.xhulib.data.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用真实的页面结构片段验证解析器。
 *
 * HTML 片段照抄自 `http://202.115.151.118:8080/` 的实际输出
 * （书目检索结果页 / 借阅历史页 / 证件信息页），只把个人数据替换成假数据。
 */
class ParserTest {

    // ------------------------------------------------------------------ 检索结果

    private val searchResultHtml = """
        <html><body>
        <ol id="search_book_list">
          <li class="book_list_info">
            <h3><span>中文图书</span><a href="item.php?marc_no=abc&amp;list=1" >1.示例书目甲:副题名示例</a>     O1/T000/2401  </h3>
            <p> <span>馆藏复本：2 <br>
              可借复本：2</span> (英) 示例作者著 <br />
              示例出版社 2024.01 <br />
              <img src="../tpl/images/star0.gif" title="总体评分及评价人数"/>(0)
              <a href="item.php?marc_no=abc" class="tooltip">评价</a>
            </p>
          </li>
          <li class="book_list_info">
            <h3><span>中文图书</span><a href="item.php?marc_no=def&amp;list=1" >2.示例书目乙</a>  O1/T001/2402  </h3>
            <p> <span>馆藏复本：5 <br>
              可借复本：3</span> 吴军著 <br />
              示例出版社 2020 <br />(12)
            </p>
          </li>
        </ol>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析检索结果的书名 去掉序号`() {
        val items = CatalogParsers.parseSearchResults(searchResultHtml)
        assertEquals(2, items.size)
        assertEquals("示例书目甲:副题名示例", items[0].title)
        assertEquals("示例书目乙", items[1].title)
    }

    @Test
    fun `解析检索结果的责任者与出版信息`() {
        val item = CatalogParsers.parseSearchResults(searchResultHtml)[0]
        assertEquals("(英) 示例作者著", item.author)
        assertEquals("示例出版社 2024.01", item.publication)
    }

    @Test
    fun `解析检索结果的索书号与馆藏`() {
        val item = CatalogParsers.parseSearchResults(searchResultHtml)[0]
        assertEquals("O1/T000/2401", item.callNo)
        assertTrue(item.holding.contains("馆藏复本：2"))
        assertTrue(item.holding.contains("可借复本：2"))
    }

    @Test
    fun `保留详情页相对地址`() {
        val item = CatalogParsers.parseSearchResults(searchResultHtml)[0]
        assertEquals("item.php?marc_no=abc&list=1", item.detailUrl)
    }

    // ------------------------------------------------------------------ 借阅历史

    /**
     * 关键点：数据行比表头多一个「序号」列，解析器必须自动左补空列对齐，
     * 否则所有字段都会错位一格。
     */
    private val loanHistoryHtml = """
        <html><body>
        <table>
          <tr><th>条码号</th><th>题名</th><th>责任者</th><th>借阅日期</th><th>归还日期</th><th>馆藏地</th></tr>
          <tr><td>1</td><td>1000001</td><td>示例书目一</td><td>示例作者甲</td><td>2024-01-01</td><td>2024-02-01</td><td>示例馆藏地</td></tr>
          <tr><td>2</td><td>1000002</td><td>示例书目二</td><td>示例作者乙</td><td>2024-03-01</td><td>2024-04-01</td><td>示例馆藏地</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `带序号列时借阅历史字段不会错位`() {
        val records = ReaderParsers.parseLoanHistory(loanHistoryHtml)
        assertEquals(2, records.size)

        val first = records[0]
        assertEquals("示例书目一", first.title)
        assertEquals("示例作者甲", first.author)
        assertEquals("1000001", first.barcode)
        assertEquals("2024-01-01", first.loanDate)
        assertEquals("2024-02-01", first.returnDate)
        assertEquals("示例馆藏地", first.location)
    }

    // ------------------------------------------------------------------ 证件信息

    private val idCardHtml = """
        <html><body>
        <div>五天内即将过期图书[0]，已超期图书[2]，预约到书[1]， 委托到书[0]</div>
        <div>最近两个月您一共有【3】条荐购，图书馆已处理了【1】条</div>
        <table>
          <tr><td>姓名：</td><td>张三</td></tr>
          <tr><td>证件号： ****0000</td></tr>
          <tr><td>读者类型：学生</td></tr>
          <tr><td>系别：示例学院</td></tr>
          <tr><td>最大可借图书：</td><td>10</td></tr>
        </table>
        <table>
          <tr><th>规则名称</th><th>适用馆藏地</th><th>图书流通类型</th><th>最大借阅册数</th><th>借期</th><th>预约</th><th>续借</th></tr>
          <tr><td>92d10b</td><td>流通书库,外文书库</td><td>所有流通类型</td><td>10</td><td>92</td><td>允许</td><td>允许</td></tr>
        </table>
        </body></html>
    """.trimIndent()

    @Test
    fun `解析证件信息的关键字段`() {
        val card = ReaderParsers.parseReaderCard(idCardHtml)
        assertEquals("张三", card.name)
        assertEquals("****0000", card.certNo)
        assertEquals("学生", card.readerType)
        assertEquals("示例学院", card.department)
        assertEquals("10", card.maxBooks)
    }

    /** 值既可能在同一个单元格（`证件号： ****0000`），也可能在下一个单元格。 */
    @Test
    fun `兼容标签与值同格或分格两种排版`() {
        val card = ReaderParsers.parseReaderCard(idCardHtml)
        assertEquals("****0000", card.certNo)
        assertEquals("张三", card.name)
    }

    @Test
    fun `解析借阅规则表`() {
        val card = ReaderParsers.parseReaderCard(idCardHtml)
        assertEquals(1, card.rules.size)
        assertEquals("92d10b", card.rules[0].name)
        assertEquals("92", card.rules[0].loanDays)
        assertEquals("允许", card.rules[0].renew)
    }

    @Test
    fun `解析顶部提醒条的数字`() {
        val overview = ReaderParsers.parseOverview(idCardHtml)
        assertEquals(0, overview.expiringSoon)
        assertEquals(2, overview.overdue)
        assertEquals(1, overview.holdsReady)
        assertEquals(0, overview.delegatesReady)
        assertEquals(3, overview.asordCount)
    }

    // ------------------------------------------------------------------ 我的书架

    /**
     * 结构照抄真实的 `reader/book_shelf.php`（只把书名等换成虚构内容）。
     *
     * 关键点：书架入口是**只有查询串的相对链接** `?classid=…`，
     * 既不含 `book_shelf.php`，参数名也不是 `shelf_id`。
     * 早先的解析器只认 `book_shelf.php?...`，所以一个书架都取不到。
     */
    private val shelfHtml = """
        <html><body>
        <div id="mylib_content">
          <h2>我的书架<span><a class="red" href="book_shelf_man.php">管理我的书架</a></span></h2>
          <p>书架列表：<a class="blue" href="?classid=0000002808" title="">待阅(1)</a>&nbsp;&nbsp;</p>
          <p style="margin:10px auto;">当前选择: <font color="red">待阅</font> 共 <font color="red">1</font> 条记录</p>
          <table width="100%" class="table_line">
            <tr>
              <td class="greytext" width="3%"></td>
              <td class="greytext" width="25%">题名</td>
              <td class="greytext" width="20%">责任者</td>
              <td class="greytext" width="15%">出版社</td>
              <td width="10%" class="greytext">出版日期</td>
              <td class="greytext" width="10%">索书号</td>
              <td width="10%" class="greytext">操作</td>
            </tr>
            <tr bgcolor="#FFFFFF" id="del_XXXX" name="del_XXXX">
              <td bgcolor="#FFFFFF" class="whitetext">1</td>
              <td bgcolor="#FFFFFF" class="whitetext"><a class="blue" href="../opac/item.php?marc_no=AAAA">测试用书名</a></td>
              <td bgcolor="#FFFFFF" class="whitetext">测试作者</td>
              <td bgcolor="#FFFFFF" class="whitetext">测试出版社</td>
              <td align="center" bgcolor="#FFFFFF" class="whitetext">202401</td>
              <td bgcolor="#FFFFFF" class="whitetext">G210.92/T000/2401</td>
              <td align="center" bgcolor="#FFFFFF" class="whitetext"><a href="#" onclick="delbook()">删除</a></td>
            </tr>
          </table>
        </div>
        </body></html>
    """.trimIndent()

    /** 回归：书架入口是 `?classid=…` 这种只有查询串的链接，必须能取到。 */
    @Test
    fun `解析书架分组入口`() {
        val links = ReaderParsers.parseShelfLinks(shelfHtml)
        assertEquals("应解析出 1 个书架", 1, links.size)
        assertEquals("待阅(1)", links[0].label)
        assertEquals("查询串应原样带出", "classid=0000002808", links[0].value)
    }

    @Test
    fun `解析书架内书目`() {
        val items = ReaderParsers.parseShelfItems(shelfHtml)
        assertEquals(1, items.size)
        assertEquals("测试用书名", items[0].title)
        assertEquals("测试作者", items[0].author)
        assertEquals("测试出版社", items[0].publisher)
        assertEquals("G210.92/T000/2401", items[0].callNo)
        // 序号列不能导致字段错位
        assertEquals("202401", items[0].pubDate)
    }

    @Test
    fun `书架条目的详情链接已归一化`() {
        val item = ReaderParsers.parseShelfItems(shelfHtml)[0]
        assertEquals("item.php?marc_no=AAAA", item.detailUrl)
    }

    /** 默认页（未选中书架）不应解析出任何分组入口之外的东西。 */
    @Test
    fun `未选书架的页面不会误报书目`() {
        val emptyShelf = """
            <html><body><div id="mylib_content">
            <p>书架列表：<a class="blue" href="?classid=0000002808">待阅(1)</a></p>
            <p>当前选择: <font color="red"></font> 共 <font color="red"></font> 条记录</p>
            <table><tr><td></td><td>题名</td><td>索书号</td></tr></table>
            </div></body></html>
        """.trimIndent()
        assertTrue(ReaderParsers.parseShelfItems(emptyShelf).isEmpty())
        assertEquals(1, ReaderParsers.parseShelfLinks(emptyShelf).size)
    }

    // ------------------------------------------------------------------ 空态

    @Test
    fun `识别空态文案`() {
        val html = "<html><body><div>您的该项记录为空！</div></body></html>"
        assertTrue(Html.isEmptyRecord(Html.parse(html)))
    }

    @Test
    fun `表头缺失时返回空列表而不是抛异常`() {
        val html = "<html><body><table><tr><td>无关内容</td></tr></table></body></html>"
        assertTrue(CatalogParsers.parseSearchResults(html).isEmpty())
        assertTrue(ReaderParsers.parseLoanHistory(html).isEmpty())
        assertTrue(ReaderParsers.parseCredits(html).isEmpty())
    }
}
