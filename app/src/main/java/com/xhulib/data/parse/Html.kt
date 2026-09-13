package com.xhulib.data.parse

import com.xhulib.data.model.LabeledValue
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private val WHITESPACE = Regex("""\s+""")
private val LABEL_VALUE = Regex("""^(.{1,14}?)\s*[:：]\s*(.*)$""")

/** 归一化文本：去掉 &nbsp;、折叠空白。 */
fun Element.normalizedText(): String =
    text().replace('\u00A0', ' ').replace(WHITESPACE, " ").trim()

/**
 * HTML 解析工具。
 *
 * 设计原则：**按表头文字定位，而不是按 CSS 路径定位**。
 * 汇文 OPAC 的页面结构在不同栏目/版本间差异不小，但列表页的表头
 * （题名、责任者、借阅日期…）是稳定的，因此统一用「找到表头包含这些
 * 列名的表格」的方式取数，比硬编码选择器抗变化得多。
 */
object Html {

    fun parse(html: String): Document = Jsoup.parse(html, "UTF-8")

    /** 页面主体文本（去掉脚本与样式），用于空态与提示语识别。 */
    fun bodyText(doc: Document): String {
        val clone = doc.clone()
        clone.select("script, style").remove()
        return clone.body().normalizedText()
    }

    /**
     * 表格数据：表头 + 数据行，并支持按列名取值。
     *
     * 汇文的列表页常在数据行最前面多一个「序号」列而表头里没有它，
     * 这里会自动在表头左侧补空列对齐。
     *
     * [rowElements] 与 [rows] 一一对应，需要在单元格里取链接等元素时用。
     */
    class TableData(
        headers: List<String>,
        val rows: List<List<String>>,
        val rowElements: List<Element> = emptyList(),
    ) {
        val headers: List<String>

        init {
            val widest = rows.maxOfOrNull { it.size } ?: 0
            this.headers = if (headers.size < widest) {
                List(widest - headers.size) { "" } + headers
            } else {
                headers
            }
        }

        fun columnIndex(name: String): Int = headers.indexOfFirst { it.contains(name) }

        /** 按列名取某一行的值；列不存在时返回空串。 */
        fun value(row: List<String>, name: String): String {
            val index = columnIndex(name)
            return if (index < 0) "" else row.getOrNull(index)?.trim().orEmpty()
        }

        fun hasColumn(name: String): Boolean = columnIndex(name) >= 0

        /** 取某行指定列里第一个链接的 href，没有则返回 null。 */
        fun href(rowIndex: Int, columnName: String): String? {
            val index = columnIndex(columnName)
            if (index < 0) return null
            val cells = rowElements.getOrNull(rowIndex)?.select("th, td") ?: return null
            return cells.getOrNull(index)?.selectFirst("a[href]")?.attr("href")?.takeIf { it.isNotBlank() }
        }
    }

    /**
     * 找到表头包含 [requiredColumns] 全部列名的表格，返回表头与数据行。
     * 找不到返回 null。
     */
    fun table(doc: Document, requiredColumns: List<String>): TableData? {
        for (table in doc.select("table")) {
            val rows = table.select("tr")
            for ((headerIndex, row) in rows.withIndex()) {
                val cells = row.select("th, td").map { it.normalizedText() }
                if (cells.isEmpty()) continue
                val matched = requiredColumns.all { required ->
                    cells.any { it.contains(required) }
                }
                if (!matched) continue

                val dataElements = rows.drop(headerIndex + 1)
                    .filter { r -> r.select("th, td").any { it.normalizedText().isNotBlank() } }
                val data = dataElements.map { r -> r.select("th, td").map { it.normalizedText() } }
                return TableData(cells, data, dataElements)
            }
        }
        return null
    }

    /**
     * 找到表头包含 [requiredColumns] 全部列名的表格，返回其数据行。
     *
     * 每个数据行是「单元格文本」列表。找不到返回 null。
     */
    fun tableRows(doc: Document, requiredColumns: List<String>): List<List<String>>? =
        table(doc, requiredColumns)?.rows

    /**
     * 提取「标签：值」对。
     *
     * 兼容两种排版：值跟在同一个单元格里（`姓名：张三`），
     * 或值在相邻的下一个单元格里（`<td>姓名：</td><td>张三</td>`）。
     */
    fun labelValuePairs(doc: Document): List<LabeledValue> {
        val result = mutableListOf<LabeledValue>()
        val seen = mutableSetOf<String>()

        for (cell in doc.select("td, th")) {
            val text = cell.normalizedText()
            if (text.isEmpty() || text.length > 40) continue
            val match = LABEL_VALUE.matchEntire(text) ?: continue
            val label = match.groupValues[1].trim()
            var value = match.groupValues[2].trim()

            if (value.isEmpty()) {
                value = cell.nextElementSibling()?.normalizedText().orEmpty()
            }
            if (label.isEmpty()) continue
            if (seen.add(label)) {
                result += LabeledValue(label = label, value = value)
            }
        }
        return result
    }

    /** 该栏目是否为空态（汇文统一文案：`您的该项记录为空！`）。 */
    fun isEmptyRecord(doc: Document): Boolean = bodyText(doc).let {
        it.contains("记录为空") || it.contains("没有找到") || it.contains("暂无")
    }

    /**
     * 解析列表页的总页数。
     *
     * 汇文的分页控件形如：
     * ```html
     * <div class="numstyle"> 上一页 <b>1 / 2</b>
     *   <a href='/asord/asord_cls_browse.php?cls_no=A&page=2'>下一页</a>
     *   到第 <select name='topage' onchange='...page="+this.value'>
     * ```
     * 依次尝试：分页链接里的最大页码 → 跳页下拉框 → 正文里的「x / y」。
     */
    fun totalPages(doc: Document): Int {
        val pageParam = Regex("""[?&]page=(\d+)""")

        val fromLinks = doc.select("a[href*=page=]")
            .mapNotNull { pageParam.find(it.attr("href"))?.groupValues?.get(1)?.toIntOrNull() }
            .maxOrNull()

        val fromSelect = doc.select("select[name=topage] option")
            .mapNotNull { it.attr("value").toIntOrNull() }
            .maxOrNull()

        val fromText = Regex("""(\d+)\s*/\s*(\d+)""")
            .find(bodyText(doc))
            ?.groupValues?.get(2)?.toIntOrNull()

        return maxOf(fromLinks ?: 1, fromSelect ?: 1, fromText ?: 1, 1)
    }

    /** 从文本里抽第一个整数，抽不到返回 null。 */
    fun firstInt(text: String): Int? = Regex("""\d+""").find(text)?.value?.toIntOrNull()
}
