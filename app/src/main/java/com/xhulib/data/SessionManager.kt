package com.xhulib.data

import android.content.Context
import com.xhulib.data.net.LoginPrep
import com.xhulib.data.net.LoginResult
import com.xhulib.data.net.OpacClient
import com.xhulib.R
import com.xhulib.data.net.OpacException
import com.xhulib.data.net.PersistentCookieJar
import com.xhulib.data.store.AccountStore
import com.xhulib.data.store.PageCache
import com.xhulib.data.store.SavedAccount
import com.xhulib.data.store.SecurePrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 当前登录状态。 */
sealed interface SessionState {
    /** 启动时正在用本地凭证试探服务端。 */
    data object Checking : SessionState

    /** 未登录（或本地凭证已失效）。 */
    data object LoggedOut : SessionState

    /** 已登录。 */
    data class LoggedIn(val account: SavedAccount) : SessionState
}

/**
 * 会话管理：负责「一次登录、重启免登录」。
 *
 * 服务端会话存活时间很短，所以策略是：
 * 1. 启动时先拿本地保存的 Cookie 试探一次 `redr_info.php`；
 * 2. 还有效 → 直接进主页，用户无感知；
 * 3. 已失效 → 进入登录页，**账号密码自动填好**，用户只需看图输入 4 个验证码字符。
 *
 * 验证码是一次性的，因此每次尝试都必须重新 [prepareLogin]。
 */
class SessionManager(
    context: Context,
    private val accounts: AccountStore,
    private val securePrefs: SecurePrefs,
    private val cache: PageCache,
) {

    private val _state = MutableStateFlow<SessionState>(SessionState.Checking)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _expiredNotice = MutableStateFlow(false)

    /**
     * 是否因为「登录状态过期」而回到登录页。
     *
     * 服务端会话很短，用着用着就会失效。直接跳回登录页会让人莫名其妙，
     * 登录页据此显示一句「登录状态已过期」。
     */
    val expiredNotice: StateFlow<Boolean> = _expiredNotice.asStateFlow()

    /** 当前客户端，以及它属于哪个账号，避免切换账号时用错 Cookie。 */
    private var client: OpacClient? = null
    private var clientNumber: String? = null

    fun clientOrNull(): OpacClient? = client

    fun requireClient(): OpacClient = client ?: throw OpacException(R.string.error_not_logged_in)

    private fun clientFor(number: String): OpacClient {
        val existing = client
        if (existing != null && clientNumber == number) return existing
        return OpacClient(PersistentCookieJar(securePrefs, cookieKey(number))).also {
            client = it
            clientNumber = number
        }
    }

    private fun cookieKey(number: String) = "cookies:$number"

    /**
     * 冷启动 / 切换账号后调用：用本地凭证试探会话是否还有效。
     *
     * 网络不通时抛 [OpacException]，且**不会**清除本地凭证——
     * 让界面去区分「会话过期」和「连不上校园网」。
     */
    suspend fun verifySession(account: SavedAccount): Boolean {
        val candidate = clientFor(account.number)
        val valid = candidate.hasValidSession()
        return if (valid) {
            accounts.setCurrent(account.number)
            _state.value = SessionState.LoggedIn(account)
            true
        } else {
            _state.value = SessionState.LoggedOut
            false
        }
    }

    /**
     * 启动时调用：没有已保存账号就直接判定未登录。
     *
     * 连不上服务器时（多半是没连校园网），只要本地有缓存就进入**离线模式**，
     * 让用户仍能查看上次抓到的数据；连缓存都没有才把错误抛给界面。
     */
    suspend fun bootstrap(): Boolean {
        val account = accounts.currentAccount()
        if (account == null) {
            _state.value = SessionState.LoggedOut
            return false
        }
        return try {
            verifySession(account)
        } catch (e: OpacException) {
            val cachedAt = cache.latestSavedAt()
            if (cachedAt != null && cache.has(Opac.READER_HOME)) {
                client = clientFor(account.number)
                DataFreshness.markOffline(cachedAt)
                _state.value = SessionState.LoggedIn(account)
                true
            } else {
                throw e
            }
        }
    }

    /** 取一次性的登录参数（csrf + sca + 验证码）。 */
    suspend fun prepareLogin(number: String): LoginPrep = clientFor(number).prepareLogin()

    /** 提交登录；成功后保存账号、密码与 Cookie，并切换为已登录状态。 */
    suspend fun login(
        number: String,
        password: String,
        captcha: String,
        loginType: Opac.LoginType,
        prep: LoginPrep,
        rememberPassword: Boolean = true,
    ): LoginResult {
        val result = clientFor(number).submitLogin(
            prep = prep,
            number = number,
            password = password,
            captcha = captcha,
            loginType = loginType,
        )

        if (result is LoginResult.Success) {
            _expiredNotice.value = false
            accounts.save(
                number = number,
                displayName = AccountStore.maskNumber(number),
                loginType = loginType,
                password = if (rememberPassword) password else null,
            )
            _state.value = SessionState.LoggedIn(
                SavedAccount(
                    number = number,
                    displayName = AccountStore.maskNumber(number),
                    loginType = loginType,
                    lastUsedAt = System.currentTimeMillis(),
                ),
            )
        }
        return result
    }

    /** 切换到另一个已保存账号：复用其本地 Cookie。 */
    suspend fun switchTo(number: String): Boolean {
        accounts.setCurrent(number)
        val account = accounts.accounts().firstOrNull { it.number == number } ?: return false
        return verifySession(account)
    }

    /** 主动退出：清掉服务端会话、本地 Cookie 与页面缓存。[forgetAccount] 为 true 时连账号密码一并删除。 */
    suspend fun logout(forgetAccount: Boolean = false) {
        val number = clientNumber ?: accounts.currentAccount()?.number
        runCatching { client?.logout() }
        client = null
        clientNumber = null
        // 缓存里有借阅记录、证件信息等个人数据，退出时一并清掉
        cache.clear()
        DataFreshness.reset()
        if (number != null && forgetAccount) accounts.remove(number)
        _expiredNotice.value = false
        _state.value = SessionState.LoggedOut
    }

    /** 供「设置 → 清除本地数据」使用：清掉所有账号的 Cookie 与页面缓存。 */
    fun purgeSessionData() {
        accounts.accounts().forEach { securePrefs.removeByPrefix("cookies:${it.number}") }
        cache.clear()
        DataFreshness.reset()
        client = null
        clientNumber = null
        _expiredNotice.value = false
        _state.value = SessionState.LoggedOut
    }

    /**
     * 服务端会话中途失效时调用。
     *
     * 只把状态切回未登录，**保留账号与密码**，这样用户回到登录页时
     * 账号密码已经填好，只需再看图输一次验证码。
     */
    fun markSessionExpired() {
        _expiredNotice.value = true
        _state.value = SessionState.LoggedOut
    }
}
