package com.xhulib.ui.nav

import androidx.annotation.StringRes
import com.xhulib.R

/**
 * 「我的」页里各只读栏目的键。
 *
 * 这些栏目在服务端都是 `reader/` 目录下的独立 PHP 页面，客户端统一用一个
 * 通用列表页 + [key] 来渲染。
 */
enum class ReaderSection(val key: String, @StringRes val titleRes: Int) {
    CurrentLoans("current_loans", R.string.section_current_loans),
    LoanHistory("loan_history", R.string.section_loan_history),
    Bookshelf("bookshelf", R.string.section_bookshelf),
    Reviews("reviews", R.string.section_reviews),
    AsordHistory("asord_history", R.string.section_asord_history),
    SearchHistory("search_history", R.string.section_search_history),
    Courses("courses", R.string.section_courses),
    Holds("holds", R.string.section_holds),
    Delegates("delegates", R.string.section_delegates),
    BookLoss("book_loss", R.string.section_book_loss),
    Account("account", R.string.section_account),
    Credits("credits", R.string.section_credits),
    ;

    companion object {
        fun fromKey(key: String?): ReaderSection? = entries.firstOrNull { it.key == key }
    }
}

/**
 * 第一版**不做**、只引导用户去网页端办理的功能。
 */
enum class WebOnlyTask(
    val key: String,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
) {
    Fines(
        key = "fines",
        titleRes = R.string.webonly_fines,
        messageRes = R.string.webonly_fines_message,
    ),
    ReportLoss(
        key = "report_loss",
        titleRes = R.string.webonly_report_loss,
        messageRes = R.string.webonly_report_loss_message,
    ),
    EmailVerify(
        key = "email_verify",
        titleRes = R.string.webonly_email_verify,
        messageRes = R.string.webonly_email_verify_message,
    ),
    CourseManage(
        key = "course_manage",
        titleRes = R.string.webonly_course_manage,
        messageRes = R.string.webonly_course_manage_message,
    ),
    ;

    companion object {
        fun fromKey(key: String?): WebOnlyTask? = entries.firstOrNull { it.key == key }
    }
}
