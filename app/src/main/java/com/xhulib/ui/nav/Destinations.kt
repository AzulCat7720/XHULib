package com.xhulib.ui.nav

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import com.xhulib.R

/**
 * 底部三个一级页面。
 */
enum class TopLevelTab(
    val route: String,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    Home("home", R.string.tab_home, Icons.Filled.Home, Icons.Outlined.Home),
    Discover("discover", R.string.tab_discover, Icons.Filled.Explore, Icons.Outlined.Explore),
    Mine("mine", R.string.tab_mine, Icons.Filled.Person, Icons.Outlined.Person),
}

/**
 * 二级页面路由。带 `{...}` 的用下面这些构造函数生成实际地址。
 */
object Routes {
    const val SEARCH = "search"
    const val NOTIFICATIONS = "notifications"
    const val SETTINGS = "settings"

    /** 读者荐购（只读）：浏览图书馆征订目录。 */
    const val ASORD_CATALOG = "asord-catalog"

    /** 通知详情（信息发布里各分类挂的公告页）。 */
    const val NOTICE_DETAIL = "notice?title={title}&path={path}"
    fun noticeDetail(title: String, path: String): String =
        "notice?title=${Uri.encode(title)}&path=${Uri.encode(path)}"

    /** 热门推荐（四个排行标签）。 */
    const val HOT_LIST = "hot-list"

    /** 期刊导航（四个子标签）。 */
    const val PERIODICAL = "periodical"

    /** 图书详情；[url] 是检索结果里的相对地址（`item.php?marc_no=…`）。 */
    const val BOOK_DETAIL = "book?url={url}"
    fun bookDetail(url: String): String = "book?url=${Uri.encode(url)}"

    /** 「我的」页各只读栏目。 */
    const val READER_SECTION = "reader/{key}"
    fun readerSection(key: String): String = "reader/$key"

    /** 需要到网页端办理的功能（违章缴款 / 读者挂失）。 */
    const val WEB_ONLY = "web-only/{key}"
    fun webOnly(key: String): String = "web-only/$key"
}
