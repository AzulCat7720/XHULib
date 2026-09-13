package com.xhulib.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.xhulib.data.DataFreshness
import com.xhulib.data.SessionEvents
import com.xhulib.data.SessionState
import com.xhulib.ui.asord.AsordScreen
import com.xhulib.ui.common.appContainer
import com.xhulib.ui.disclaimer.DisclaimerScreen
import com.xhulib.ui.discover.DiscoverScreen
import com.xhulib.ui.home.HomeScreen
import com.xhulib.ui.hot.HotListScreen
import com.xhulib.ui.login.LoginScreen
import com.xhulib.ui.mine.MineScreen
import com.xhulib.ui.nav.Routes
import com.xhulib.ui.nav.TopLevelTab
import com.xhulib.ui.notice.NoticeDetailScreen
import com.xhulib.ui.notice.NotificationsScreen
import com.xhulib.ui.peri.PeriodicalScreen
import com.xhulib.ui.reader.ReaderSectionScreen
import com.xhulib.ui.reader.WebOnlyScreen
import com.xhulib.ui.search.BookDetailScreen
import com.xhulib.ui.search.SearchScreen
import com.xhulib.ui.settings.SettingsScreen
import com.xhulib.R

/**
 * 页面切换过渡时长。
 *
 * Navigation Compose 的默认转场是 700ms 的淡入淡出，在手机上明显拖沓；
 * 这里压到 180ms，保持「有动效但不拖慢操作」。
 */
private const val NAV_TRANSITION_MS = 180

/**
 * 应用根。三态：
 * - [SessionState.Checking] 启动时用本地凭证试探服务端
 * - [SessionState.LoggedOut] 登录页
 * - [SessionState.LoggedIn] 主界面（底部三页导航）
 */
@Composable
fun XhuApp() {
    val container = appContainer()
    val session = container.sessionManager
    val state by session.state.collectAsState()
    val context = LocalContext.current

    // 首次启动先弹免责声明；同意之前不初始化会话，也不联网
    val disclaimerAccepted by container.settingsStore.disclaimerAccepted.collectAsState()
    if (!disclaimerAccepted) {
        DisclaimerScreen(
            onAccept = { container.settingsStore.setDisclaimerAccepted(true) },
            // 「不同意」直接退出应用
            onDecline = { context.findActivity()?.finish() },
        )
        return
    }

    // 冷启动：用本地保存的凭证试探服务端会话是否还有效。
    // 连不上服务器时直接落到登录页，不再弹全屏错误页（登录页本身已有网络提示）。
    LaunchedEffect(Unit) {
        try {
            session.bootstrap()
        } catch (e: Exception) {
            session.markSessionExpired()
        }
    }

    // 使用过程中服务端会话失效：数据层广播，这里统一切回登录页
    // （账号密码已填好，用户只需再输一次验证码）
    LaunchedEffect(Unit) {
        SessionEvents.expired.collect { session.markSessionExpired() }
    }

    when {
        state is SessionState.Checking -> BootScreen()

        state is SessionState.LoggedOut -> LoginScreen(onLoggedIn = { /* 状态流会自动切页 */ })

        else -> MainScaffold()
    }
}

@Composable
private fun BootScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}


@Composable
private fun MainScaffold() {
    val container = appContainer()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val topLevelRoutes = remember { TopLevelTab.entries.map { it.route }.toSet() }
    val showBottomBar = currentRoute in topLevelRoutes

    // 过渡动画开关（设置页可关），默认开启
    val animationsEnabled by container.settingsStore.animationsEnabled.collectAsState()
    val duration = if (animationsEnabled) NAV_TRANSITION_MS else 0

    // 离线缓存生效时提示用户，避免把旧数据当成实时数据
    val offline by DataFreshness.offline.collectAsState()
    val cachedAt by DataFreshness.cachedAt.collectAsState()

    Scaffold(
        bottomBar = {
            Column {
                if (offline) {
                    // 有底部导航栏时由它负责系统手势条内边距，否则这里自己补
                    OfflineBanner(applyNavigationInset = !showBottomBar, cachedAt = cachedAt)
                }
                if (showBottomBar) {
                    MainNavigationBar(navController, currentRoute)
                }
            }
        },
        // 系统栏内边距交给各页面自己的 Scaffold 处理，避免嵌套时重复留白
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        // 底部导航栏（或离线提示条）已经处理过系统手势条的内边距。
        // 若不再声明「这部分已消费」，各页自己的 Scaffold 会在导航栏上方
        // 再留一条系统栏高度的空白 —— 看起来就是导航栏上方的一条白条。
        val bottomHandledOutside = showBottomBar || offline
        val hostModifier = Modifier
            .padding(bottom = innerPadding.calculateBottomPadding())
            .then(
                if (bottomHandledOutside) {
                    Modifier.consumeWindowInsets(WindowInsets.navigationBars)
                } else {
                    Modifier
                },
            )

        NavHost(
            navController = navController,
            startDestination = TopLevelTab.Home.route,
            modifier = hostModifier,
            enterTransition = {
                if (duration == 0) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(duration)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(duration),
                    )
                }
            },
            exitTransition = {
                if (duration == 0) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(duration)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Start,
                        tween(duration),
                    )
                }
            },
            popEnterTransition = {
                if (duration == 0) {
                    EnterTransition.None
                } else {
                    fadeIn(tween(duration)) + slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(duration),
                    )
                }
            },
            popExitTransition = {
                if (duration == 0) {
                    ExitTransition.None
                } else {
                    fadeOut(tween(duration)) + slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.End,
                        tween(duration),
                    )
                }
            },
        ) {
            composable(TopLevelTab.Home.route) {
                HomeScreen(
                    onOpenNotifications = { navController.navigate(Routes.NOTIFICATIONS) },
                    onOpenSearch = { navController.navigate(Routes.SEARCH) },
                    onOpenSection = { key -> navController.navigate(Routes.readerSection(key)) },
                    onOpenBook = { url -> navController.navigate(Routes.bookDetail(url)) },
                    onOpenHotList = { navController.navigate(Routes.HOT_LIST) },
                )
            }
            composable(TopLevelTab.Discover.route) {
                DiscoverScreen(
                    onOpenBook = { url -> navController.navigate(Routes.bookDetail(url)) },
                )
            }
            composable(TopLevelTab.Mine.route) {
                MineScreen(
                    onOpenSection = { key -> navController.navigate(Routes.readerSection(key)) },
                    onOpenWebOnly = { key -> navController.navigate(Routes.webOnly(key)) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onOpenAsordCatalog = { navController.navigate(Routes.ASORD_CATALOG) },
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBook = { url -> navController.navigate(Routes.bookDetail(url)) },
                )
            }
            composable(Routes.BOOK_DETAIL) { entry ->
                BookDetailScreen(
                    marcUrl = entry.arguments?.getString("url").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNotice = { title, path ->
                        navController.navigate(Routes.noticeDetail(title, path))
                    },
                )
            }
            composable(Routes.NOTICE_DETAIL) { entry ->
                NoticeDetailScreen(
                    title = entry.arguments?.getString("title").orEmpty(),
                    path = entry.arguments?.getString("path").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.ASORD_CATALOG) {
                AsordScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.HOT_LIST) {
                HotListScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBook = { url -> navController.navigate(Routes.bookDetail(url)) },
                )
            }
            composable(Routes.PERIODICAL) {
                PeriodicalScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBook = { url -> navController.navigate(Routes.bookDetail(url)) },
                )
            }
            composable(Routes.READER_SECTION) { entry ->
                ReaderSectionScreen(
                    sectionKey = entry.arguments?.getString("key").orEmpty(),
                    onBack = { navController.popBackStack() },
                    onOpenWebOnly = { key -> navController.navigate(Routes.webOnly(key)) },
                    onOpenBook = { url -> navController.navigate(Routes.bookDetail(url)) },
                )
            }
            composable(Routes.WEB_ONLY) { entry ->
                WebOnlyScreen(
                    taskKey = entry.arguments?.getString("key").orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onLoggedOut = { /* 会话状态流会自动回到登录页 */ },
                )
            }
        }
    }
}

@Composable
private fun MainNavigationBar(navController: NavHostController, currentRoute: String?) {
    NavigationBar {
        TopLevelTab.entries.forEach { tab ->
            val selected = currentRoute == tab.route
            NavigationBarItem(
                selected = selected,
                onClick = {
                    if (!selected) {
                        navController.navigate(tab.route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                icon = {
                    Icon(
                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                        contentDescription = stringResource(tab.labelRes),
                    )
                },
                label = { Text(stringResource(tab.labelRes)) },
            )
        }
    }
}

/**
 * 离线提示条。
 *
 * 网络不通、显示的是本地缓存时贴在底部，避免用户把旧数据当成实时数据。
 * 放在底部而不是顶部，是为了避开「外层 Scaffold + 各页内层 Scaffold」
 * 在状态栏内边距上的嵌套冲突。
 */
@Composable
private fun OfflineBanner(applyNavigationInset: Boolean, cachedAt: Long?) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (applyNavigationInset) {
                        Modifier.navigationBarsPadding()
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = cachedAt?.let {
                    stringResource(
                        R.string.offline_updated_at,
                        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(it)),
                    )
                } ?: stringResource(R.string.offline_no_network),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** 从 Compose 的 Context 里找到宿主 Activity，用于「不同意，离开」时退出应用。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
