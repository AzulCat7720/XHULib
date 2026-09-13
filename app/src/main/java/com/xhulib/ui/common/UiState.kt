package com.xhulib.ui.common

import androidx.annotation.StringRes
import com.xhulib.R
import com.xhulib.data.SessionExpiredException
import com.xhulib.data.net.OpacException
import kotlinx.coroutines.CancellationException

/** 页面数据加载状态。 */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    data class Success<T>(val data: T) : UiState<T>

    /**
     * 加载失败。
     *
     * 文案以**字符串资源 ID** 传递，由界面按当前语言解析，这样切换语言后
     * 错误提示也会跟着变。
     */
    data class Failure(
        @StringRes val messageRes: Int,
        val sessionExpired: Boolean = false,
    ) : UiState<Nothing>
}

/** 把一次可能失败的加载包成 [UiState]，并翻译常见异常。 */
suspend fun <T> loadState(block: suspend () -> T): UiState<T> = try {
    UiState.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: SessionExpiredException) {
    UiState.Failure(R.string.error_session_expired, sessionExpired = true)
} catch (e: OpacException) {
    UiState.Failure(e.messageRes)
} catch (e: Exception) {
    UiState.Failure(R.string.error_load_failed)
}
