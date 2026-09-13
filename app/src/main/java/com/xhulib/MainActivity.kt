package com.xhulib

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.xhulib.data.store.AppLanguage
import com.xhulib.data.store.BackgroundImageStore
import com.xhulib.data.store.SettingsStore
import com.xhulib.data.store.ThemeMode
import com.xhulib.ui.XhuApp
import com.xhulib.ui.theme.XhuLibTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /**
     * 在 Activity 创建**之前**套用语言设置。
     *
     * 这里还不能用 AppContainer（Application 可能尚未初始化完），
     * 所以直接读偏好文件 —— 键名与 [SettingsStore] 共用常量。
     */
    override fun attachBaseContext(newBase: Context) {
        val stored = AppLanguage.fromName(
            newBase.getSharedPreferences(SettingsStore.PREFS_NAME, Context.MODE_PRIVATE)
                .getString(SettingsStore.KEY_LANGUAGE, null),
        )
        val target = if (stored == AppLanguage.SYSTEM) {
            AppLanguage.resolveSystem(Locale.getDefault())
        } else {
            stored
        }
        super.attachBaseContext(newBase.withLanguage(target))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val context = this
            val container = XhuApplication.container(context)
            val themeMode by container.settingsStore.themeMode.collectAsState()
            val backgroundPath by container.settingsStore.backgroundImagePath.collectAsState()

            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            // 路径变了才重新解码，避免每次重组都读一遍大图
            val background = remember(backgroundPath) {
                BackgroundImageStore.load(backgroundPath)
            }

            // 状态栏/导航栏图标跟随**本应用**的主题反色。
            // enableEdgeToEdge() 只在启动时按系统主题设一次，应用内切换夜间模式后
            // 不会跟着变，所以这里每次主题变化都重新设一遍。
            val view = LocalView.current
            if (!view.isInEditMode) {
                val activity = context.findActivity()
                SideEffect {
                    activity?.window?.let { window ->
                        val controller = WindowCompat.getInsetsController(window, view)
                        controller.isAppearanceLightStatusBars = !darkTheme
                        controller.isAppearanceLightNavigationBars = !darkTheme
                    }
                }
            }

            XhuLibTheme(
                darkTheme = darkTheme,
                hasCustomBackground = background != null,
            ) {
                Box(Modifier.fillMaxSize()) {
                    if (background != null) {
                        CustomBackground(background)
                    }
                    XhuApp()
                }
            }
        }
    }
}

/**
 * 背景图之上那层蒙版的不透明度。
 *
 * 底色的不透明度已经是 0，这层是唯一压住图片、保证正文可读的东西，
 * 因此取得比较轻（图片可见度约 65%）。
 */
private const val SCRIM_ALPHA = 0.35f

/**
 * 用指定语言包一层 Context。
 *
 * 走 `createConfigurationContext` 而不是 AppCompatDelegate，
 * 这样不用引入 appcompat 依赖，且在所有受支持的系统版本上行为一致。
 */
private fun Context.withLanguage(language: AppLanguage): Context {
    val locale = when (language) {
        AppLanguage.SYSTEM -> return this
        AppLanguage.SIMPLIFIED -> Locale.SIMPLIFIED_CHINESE
        AppLanguage.TRADITIONAL -> Locale.TRADITIONAL_CHINESE
        AppLanguage.ENGLISH -> Locale.ENGLISH
    }
    val configuration = Configuration(resources.configuration)
    configuration.setLocale(locale)
    return createConfigurationContext(configuration)
}

/** 从 Compose 的 Context 里找到宿主 Activity。 */
private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 自定义全局背景。
 *
 * 图之上再蒙一层当前主题的 surface 色，深浅色主题下都能压住底图，
 * 保证正文文字始终清楚。
 */
@androidx.compose.runtime.Composable
private fun CustomBackground(image: ImageBitmap) {
    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = image,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = SCRIM_ALPHA)),
        )
    }
}
