package com.xhulib.data

import androidx.annotation.StringRes
import com.xhulib.R

/**
 * 西华大学图书馆 OPAC（江苏汇文 v5.6.1）的地址与端点常量。
 *
 * 注意：这是**校内 HTTP 地址**，手机必须处于校园网或学校 VPN 内才能访问。
 */
object Opac {

    const val HOST = "202.115.151.118"
    const val PORT = 8080
    const val BASE_URL = "http://$HOST:$PORT/"

    // ---- 登录相关 ----
    const val LOGIN_PAGE = "reader/login.php"
    const val LOGIN_SUBMIT = "reader/redr_verify.php"
    const val LOGIN_EXTRA_PARAMS = "reader/ajax_ep.php" // 异步注入 sca（密码混淆表）
    const val CAPTCHA = "reader/captcha.php"
    const val LOGOUT = "reader/logout.php"

    // ---- 我的图书馆（需登录）----
    const val READER_HOME = "reader/redr_info.php"
    const val READER_ID_CARD = "reader/redr_info_rule.php"
    const val READER_CURRENT_LOANS = "reader/book_lst.php"
    const val READER_LOAN_HISTORY = "reader/book_hist.php"
    const val READER_ASORD_HISTORY = "reader/asord_lst.php"
    const val READER_HOLDS = "reader/preg.php"
    const val READER_DELEGATES = "reader/relegate.php"
    const val READER_BOOKSHELF = "reader/book_shelf.php"
    const val READER_BOOK_LOSS = "reader/book_loss.php"
    const val READER_REPORT_LOSS = "reader/redr_lost.php"
    const val READER_ACCOUNT = "reader/account.php"
    const val READER_FINES = "reader/fine_pec.php"
    const val READER_REVIEWS = "reader/book_rv.php"
    const val READER_SEARCH_HISTORY = "reader/search_hist.php"
    const val READER_COURSES = "reader/reader_curriculum.php"
    const val READER_CREDITS = "reader/credit_detail.php"

    // ---- 公开栏目 ----
    const val SEARCH = "opac/search.php"
    const val SEARCH_ADV = "opac/search_adv.php"
    const val SEARCH_MORE = "opac/search_more.php"
    const val BOOK_CART = "opac/book_cart.php"

    const val TOP_LEND = "top/top_lend.php"
    const val CLS_BROWSING = "browse/cls_browsing.php"
    const val CLS_BROWSING_TREE = "browse/cls_browsing_tree.php"
    const val CLS_BROWSING_BOOK = "browse/cls_browsing_book.php"
    const val NEW_BOOK = "newbook/newbook_cls_browse.php"
    const val NEW_BOOK_TREE = "newbook/newbook_cls_tree.php"
    const val NEW_BOOK_BOOK = "newbook/newbook_cls_book.php"
    const val SUBJECT = "shelf/xueke_catalog.php"

    // ---- 期刊导航（公开）----
    const val PERI_BY_TITLE_CN = "peri/peri_nav_c.php"
    const val PERI_BY_TITLE_EN = "peri/peri_nav_e.php"
    const val PERI_BY_CLASS = "peri/peri_nav_class.php"
    const val PERI_CLASS_TREE = "peri/peri_nav_cls_tree.php"
    const val PERI_CLASS_LIST = "peri/peri_nav_cls_peri.php"
    const val PERI_BY_YEAR = "peri/peri_nav_year.php"

    // ---- 热门推荐（公开）----
    const val TOP_SCORE = "top/top_score.php"
    const val TOP_SHELF = "top/top_shelf.php"
    const val TOP_BOOK = "top/top_book.php"
    const val CURRICULUM = "shelf/curriculum_browse.php"
    const val ASORD_HISTORY = "asord/asord_hist.php"
    const val ASORD_CLS = "asord/asord_cls_browse.php"
    const val ASORD_RECORD = "asord/asord_record.php"
    const val NEWSLETTER = "info/info_guide.php"

    /** 需要到网页端办理的功能所跳转的地址。 */
    const val WEB_PORTAL = BASE_URL

    // ---- 登录表单字段 ----
    const val FIELD_NUMBER = "number"
    const val FIELD_PASSWORD = "passwd"
    const val FIELD_CAPTCHA = "captcha"
    const val FIELD_LOGIN_TYPE = "select"
    const val FIELD_CSRF = "csrf_token"
    const val FIELD_RETURN_URL = "returnUrl"

    /** 登录方式单选值。 */
    enum class LoginType(val value: String, @StringRes val labelRes: Int) {
        CERT_NO("cert_no", R.string.login_type_cert_no),
        BAR_NO("bar_no", R.string.login_type_bar_no),
        EMAIL("email", R.string.login_type_email),
    }

    /** 汇文登录页用该标记判断「当前不是已登录状态」。 */
    const val LOGIN_FORM_MARK = "name=\"frm_login\""

    fun url(path: String): String = BASE_URL + path
}
