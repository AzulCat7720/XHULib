package com.xhulib.ui.login

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.xhulib.data.Opac
import com.xhulib.data.SessionManager
import com.xhulib.data.net.LoginPrep
import androidx.annotation.StringRes
import com.xhulib.R
import com.xhulib.data.net.LoginResult
import com.xhulib.data.store.AccountStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 登录页表单状态。 */
data class LoginUiState(
    val number: String = "",
    val password: String = "",
    val captcha: String = "",
    val loginType: Opac.LoginType = Opac.LoginType.CERT_NO,
    val rememberPassword: Boolean = true,
    /** 验证码图片；为 null 表示还没取到（缺账号，或上一次获取失败）。 */
    val captchaImage: ImageBitmap? = null,
    val captchaLoading: Boolean = false,
    val submitting: Boolean = false,
    /** 账号与密码来自本机保存的记录，用户只需输入验证码。 */
    val prefilled: Boolean = false,
    /** 服务器返回的原文（如「验证码错误」），不翻译。 */
    val error: String? = null,

    /** 本地文案（网络类错误），按当前语言解析。 */
    @StringRes val errorRes: Int? = null,
) {
    val canSubmit: Boolean
        get() = !submitting && !captchaLoading &&
            number.isNotBlank() && password.isNotEmpty() && captcha.isNotBlank()
}

/**
 * 登录页逻辑。
 *
 * 需要特别小心的两点（都来自 OPAC 的实现）：
 * 1. **验证码是一次性的**：每次提交后，无论成败都必须重新 [refreshCaptcha]，
 *    否则下一次一定提示「验证码错误」。
 * 2. **验证码绑在会话（PHPSESSID）上**：而会话是按账号隔离的（cookie jar 以账号为键），
 *    所以账号一变，手上的验证码与 csrf / sca 全部作废，必须重新取。
 */
class LoginViewModel(
    private val sessionManager: SessionManager,
    private val accountStore: AccountStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /** 手上这张验证码（或正在取的那张）属于哪个账号。 */
    private var captchaFor: String? = null

    /** 与 [captchaFor] 配套的一次性登录参数：csrf + 密码混淆表 sca + 验证码图片。 */
    private var prepared: LoginPrep? = null

    /** 上一次自动填充进密码框的密码，用来判断用户是否已经改过它。 */
    private var autoFilledPassword: String? = null

    private var captchaJob: Job? = null

    init {
        accountStore.currentAccount()?.let { account ->
            val saved = accountStore.passwordOf(account.number)
            autoFilledPassword = saved
            _state.update {
                it.copy(
                    number = account.number,
                    password = saved.orEmpty(),
                    loginType = account.loginType,
                    // 上次登录没勾「记住密码」，这次也保持不勾，避免悄悄把密码存下来
                    rememberPassword = saved != null,
                    prefilled = saved != null,
                )
            }
        }
    }

    // ------------------------------------------------------------------ 表单

    fun onNumberChange(value: String) {
        val previousAutoFill = autoFilledPassword
        val account = accountStore.accounts().firstOrNull { it.number == value.trim() }
        val savedPassword = accountStore.passwordOf(value.trim())
        autoFilledPassword = savedPassword

        // 账号变了 → 旧验证码属于另一个会话，直接作废，别让用户对着废图输
        val captchaStale = captchaFor != null && captchaFor != value.trim()
        if (captchaStale) dropCaptcha()

        _state.update { current ->
            val nextPassword = when {
                savedPassword != null -> savedPassword
                // 之前是自动填进去的，换了账号就不该继续留着
                previousAutoFill != null && current.password == previousAutoFill -> ""
                else -> current.password
            }
            current.copy(
                number = value,
                password = nextPassword,
                loginType = account?.loginType ?: current.loginType,
                prefilled = savedPassword != null,
                error = null, errorRes = null,
                captcha = if (captchaStale) "" else current.captcha,
                captchaImage = if (captchaStale) null else current.captchaImage,
                captchaLoading = if (captchaStale) false else current.captchaLoading,
            )
        }
    }

    fun onPasswordChange(value: String) {
        autoFilledPassword = null
        _state.update { it.copy(password = value, prefilled = false, error = null, errorRes = null) }
    }

    fun onCaptchaChange(value: String) {
        val cleaned = value.filterNot { it.isWhitespace() }.take(4)
        _state.update { it.copy(captcha = cleaned, error = null, errorRes = null) }
    }

    fun onLoginTypeChange(type: Opac.LoginType) {
        _state.update { it.copy(loginType = type, error = null, errorRes = null) }
    }

    fun onRememberPasswordChange(remember: Boolean) {
        _state.update { it.copy(rememberPassword = remember) }
    }

    // ------------------------------------------------------------------ 验证码

    /**
     * 重新取一次登录参数与验证码。
     *
     * 没有账号时不发请求：会话按账号隔离，用空账号取的验证码提交时必然对不上。
     */
    fun refreshCaptcha() {
        dropCaptcha()

        val number = _state.value.number.trim()
        if (number.isEmpty()) {
            _state.update { it.copy(captchaImage = null, captcha = "", captchaLoading = false) }
            return
        }

        captchaFor = number
        captchaJob = viewModelScope.launch {
            _state.update { it.copy(captchaLoading = true) }
            try {
                val prep = sessionManager.prepareLogin(number)
                val bitmap = BitmapFactory.decodeByteArray(prep.captchaPng, 0, prep.captchaPng.size)
                    ?: error("captcha decode failed")
                prepared = prep
                _state.update {
                    it.copy(
                        captchaLoading = false,
                        captchaImage = bitmap.asImageBitmap(),
                        captcha = "",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                prepared = null
                _state.update {
                    it.copy(
                        captchaLoading = false,
                        captchaImage = null,
                        captcha = "",
                        errorRes = R.string.error_captcha_fetch,
                    )
                }
            }
        }
    }

    /** 账号栏失焦时调用：账号换了才重新取验证码，避免每敲一个字都发请求。 */
    fun refreshCaptchaIfNeeded() {
        val number = _state.value.number.trim()
        if (number.isEmpty() || _state.value.captchaLoading) return
        if (captchaFor == number && prepared != null) return
        refreshCaptcha()
    }

    private fun dropCaptcha() {
        captchaJob?.cancel()
        captchaJob = null
        captchaFor = null
        prepared = null
    }

    // ------------------------------------------------------------------ 提交

    fun submit(onLoggedIn: () -> Unit) {
        val current = _state.value
        if (current.submitting) return

        val number = current.number.trim()
        val captcha = current.captcha.trim()
        when {
            number.isEmpty() -> return setErrorRes(R.string.login_err_number)
            current.password.isEmpty() -> return setErrorRes(R.string.login_err_password)
            captcha.length != 4 -> return setErrorRes(R.string.login_err_captcha)
        }

        val prep = prepared
        if (prep == null || captchaFor != number) {
            // 手上没有与当前账号匹配的验证码：先换一张，别白白浪费一次提交
            setErrorRes(R.string.login_err_captcha_refreshed)
            refreshCaptcha()
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null, errorRes = null) }
            val result = try {
                sessionManager.login(
                    number = number,
                    password = current.password,
                    captcha = captcha,
                    loginType = current.loginType,
                    prep = prep,
                    rememberPassword = current.rememberPassword,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LoginResult.Failed(R.string.error_login_failed)
            }

            when (result) {
                is LoginResult.Success -> {
                    // 验证码与 csrf/sca 都是一次性的：成功后本页立即离场，不再多发一次
                    // 取图请求（此时会话已登录，login.php 也不返回 csrf_token），
                    // 但本地这份参数必须作废，避免被再次提交。
                    dropCaptcha()
                    _state.update { it.copy(submitting = false, captcha = "", error = null, errorRes = null) }
                    onLoggedIn()
                }

                is LoginResult.Rejected -> {
                    _state.update { it.copy(submitting = false, captcha = "", error = result.message) }
                    refreshCaptcha()
                }

                is LoginResult.Failed -> {
                    _state.update {
                        it.copy(submitting = false, captcha = "", errorRes = result.messageRes)
                    }
                    refreshCaptcha()
                }
            }
        }
    }

    /** 本地文案错误（按当前语言解析）。 */
    private fun setErrorRes(@StringRes res: Int) {
        _state.update { it.copy(error = null, errorRes = res) }
    }

    private fun setError(message: String) {
        _state.update { it.copy(error = message) }
    }
}
