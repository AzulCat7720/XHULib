package com.xhulib.data.net

import androidx.annotation.StringRes
import com.xhulib.R
import com.xhulib.data.Opac
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 网络层统一异常：连不上校园网、超时、服务器返回异常等。
 *
 * 面向用户的文案以**字符串资源 ID** 传递（[messageRes]），
 * 由 UI 层按当前语言解析；[message] 只用于日志，不进界面。
 */
class OpacException(
    @StringRes val messageRes: Int,
    cause: Throwable? = null,
) : IOException(messageRes.toString(), cause)

/** 登录页一次性参数：csrf、密码混淆表 sca、验证码图片。 */
class LoginPrep(
    val csrfToken: String,
    val sca: String,
    val captchaPng: ByteArray,
)

sealed interface LoginResult {
    /** 登录成功。 */
    data object Success : LoginResult

    /** 服务器明确拒绝（验证码错误 / 用户名或密码错误）—— [message] 是**服务器原文**，不翻译。 */
    data class Rejected(val message: String) : LoginResult

    /** 网络或解析层面的失败；[messageRes] 由 UI 按当前语言解析。 */
    data class Failed(@StringRes val messageRes: Int) : LoginResult
}

/**
 * 单个账号的 OPAC HTTP 客户端。
 *
 * 负责：Cookie 会话、登录握手（csrf + sca + 验证码）、以及所有页面抓取。
 * 每个账号一个实例，共用同一个 [PersistentCookieJar] 保证会话隔离。
 */
class OpacClient(
    private val cookieJar: PersistentCookieJar,
) {

    private companion object {
        const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        val CSRF_REGEX = Regex("""name="csrf_token"\s+value="([^"]*)"""")
        val SCA_REGEX = Regex("""setAttribute\("value","([^"]*)"\)""")
        val FONT_MSG_REGEX = Regex("""<font id="fontMsg"[^>]*>([^<]*)</font>""")
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        // 超大类别（工业技术 / 经济）走检索接口兜底时，服务器实测要 12~21 秒才返回
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .retryOnConnectionFailure(true)
        .build()

    // ------------------------------------------------------------------ 基础请求

    private suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", Opac.url(Opac.READER_HOME))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw OpacException(R.string.error_server)
                response.body?.string().orEmpty()
            }
        } catch (e: IOException) {
            throw OpacException(describeNetworkFailure(e), e)
        }
    }

    private suspend fun getBytes(url: String): ByteArray = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", UA)
            .header("Referer", Opac.url(Opac.LOGIN_PAGE))
            .build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw OpacException(R.string.error_server)
                response.body?.bytes() ?: ByteArray(0)
            }
        } catch (e: IOException) {
            throw OpacException(describeNetworkFailure(e), e)
        }
    }

    private suspend fun postForm(url: String, fields: Map<String, String>): String =
        withContext(Dispatchers.IO) {
            val body = FormBody.Builder().apply {
                fields.forEach { (k, v) -> add(k, v) }
            }.build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", UA)
                .header("Referer", Opac.url(Opac.LOGIN_PAGE))
                .post(body)
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw OpacException(R.string.error_server)
                    response.body?.string().orEmpty()
                }
            } catch (e: IOException) {
                throw OpacException(describeNetworkFailure(e), e)
            }
        }

    // ------------------------------------------------------------------ 页面抓取

    suspend fun fetch(path: String): String = get(Opac.url(path))

    fun isLoginPage(html: String): Boolean = html.contains(Opac.LOGIN_FORM_MARK)

    /**
     * 判断当前会话是否仍然有效。
     *
     * 会话失效时 OPAC 会把读者页面替换为登录页，用登录表单标记即可识别。
     */
    suspend fun hasValidSession(): Boolean = try {
        val html = fetch(Opac.READER_HOME)
        html.isNotBlank() && !isLoginPage(html)
    } catch (e: OpacException) {
        throw e
    }

    // ------------------------------------------------------------------ 登录

    /**
     * 取一次性的登录参数。
     *
     * 顺序很重要：先 login.php 建会话并拿 csrf，再 ajax_ep.php 拿 sca
     * （它由页面上的 `$("#epa").load("ajax_ep.php")` 异步注入），最后取验证码。
     */
    suspend fun prepareLogin(): LoginPrep {
        val loginHtml = fetch(Opac.LOGIN_PAGE)
        val csrf = CSRF_REGEX.find(loginHtml)?.groupValues?.get(1)
            ?: throw OpacException(R.string.error_login_page_changed)

        val epJs = fetch(Opac.LOGIN_EXTRA_PARAMS)
        val sca = SCA_REGEX.find(epJs)?.groupValues?.get(1)
            ?: throw OpacException(R.string.error_login_page_changed)

        val captcha = getBytes(Opac.url(Opac.CAPTCHA))
        if (captcha.isEmpty()) throw OpacException(R.string.error_captcha_failed)

        return LoginPrep(csrfToken = csrf, sca = sca, captchaPng = captcha)
    }

    /**
     * 提交登录。
     *
     * 注意：验证码是**一次性**的，无论成功失败，本次 [LoginPrep] 都作废，
     * 再次尝试必须重新调用 [prepareLogin]。
     */
    suspend fun submitLogin(
        prep: LoginPrep,
        number: String,
        password: String,
        captcha: String,
        loginType: Opac.LoginType,
    ): LoginResult {
        val encoded = try {
            PasswordCodec.encode(password, prep.sca)
        } catch (e: IllegalArgumentException) {
            return LoginResult.Failed(R.string.error_password_encode)
        }

        val html = postForm(
            Opac.url(Opac.LOGIN_SUBMIT),
            mapOf(
                Opac.FIELD_NUMBER to number,
                Opac.FIELD_PASSWORD to encoded,
                Opac.FIELD_CAPTCHA to captcha.trim(),
                Opac.FIELD_LOGIN_TYPE to loginType.value,
                Opac.FIELD_RETURN_URL to "",
                Opac.FIELD_CSRF to prep.csrfToken,
            ),
        )

        val message = FONT_MSG_REGEX.find(html)?.groupValues?.get(1)?.trim().orEmpty()
        if (message.isNotEmpty()) return LoginResult.Rejected(message)

        // 没有报错文案还不够，再确认一次读者页真的能打开
        return if (hasValidSession()) {
            LoginResult.Success
        } else {
            LoginResult.Failed(R.string.error_login_ineffective)
        }
    }

    suspend fun logout() {
        runCatching { fetch(Opac.LOGOUT) }
        cookieJar.clear()
    }

    private fun describeNetworkFailure(e: IOException): Int = when (e) {
        is java.net.UnknownHostException -> R.string.error_no_host
        is java.net.SocketTimeoutException -> R.string.error_timeout
        is java.net.ConnectException -> R.string.error_refused
        else -> R.string.error_network
    }
}
