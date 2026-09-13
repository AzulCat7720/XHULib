package com.xhulib.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局会话事件。
 *
 * 服务端会话存活时间很短，用户可能在任何一页停留时失效。与其让每个页面
 * 各自处理「重新登录」，不如由数据层在发现会话失效时统一广播，
 * 由根组件把状态切回登录页——此时账号密码已填好，用户只需再输一次验证码。
 */
object SessionEvents {

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expired: SharedFlow<Unit> = _expired.asSharedFlow()

    fun notifyExpired() {
        _expired.tryEmit(Unit)
    }
}
