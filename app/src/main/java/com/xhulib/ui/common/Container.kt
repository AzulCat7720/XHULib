package com.xhulib.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.xhulib.AppContainer
import com.xhulib.XhuApplication

/** 取全局依赖容器。 */
@Composable
fun appContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { XhuApplication.container(context) }
}

/**
 * 用容器里的依赖构造 ViewModel。
 *
 * 项目依赖关系很浅，不引入 DI 框架，统一走这里。
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    key: String? = null,
    noinline create: (AppContainer) -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = viewModelFactory {
            initializer { create(container) }
        },
    )
}
