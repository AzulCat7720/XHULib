package com.xhulib

import android.app.Application
import android.content.Context
import com.xhulib.data.OpacRepository
import com.xhulib.data.SessionManager
import com.xhulib.data.store.AccountStore
import com.xhulib.data.store.BackgroundImageStore
import com.xhulib.data.store.PageCache
import com.xhulib.data.store.SearchHistoryStore
import com.xhulib.data.store.SecurePrefs
import com.xhulib.data.store.SettingsStore

/**
 * 极简依赖容器。这个 App 的依赖关系很浅（一个会话 + 一个仓库），
 * 用不上 DI 框架，直接由 Application 持有。
 */
class AppContainer(context: Context) {

    val securePrefs: SecurePrefs = SecurePrefs(context)
    val settingsStore: SettingsStore = SettingsStore(context)
    val searchHistoryStore: SearchHistoryStore = SearchHistoryStore(context)
    val accountStore: AccountStore = AccountStore(securePrefs)
    val pageCache: PageCache = PageCache(context)
    val backgroundImageStore: BackgroundImageStore = BackgroundImageStore(context)
    val sessionManager: SessionManager =
        SessionManager(context, accountStore, securePrefs, pageCache)
    val repository: OpacRepository = OpacRepository(sessionManager, pageCache)
}

class XhuApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as XhuApplication).container
    }
}
