package com.xhulib.data.store

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 界面语言。
 *
 * 首次启动按设备语言自动决定（见 [AppLanguage.resolveSystem]）；
 * 设备语言不是简中/繁中/英文时统一回落简体中文。
 */
enum class AppLanguage(val tag: String?) {
    /** 跟随系统。 */
    SYSTEM(null),
    SIMPLIFIED("zh-CN"),
    TRADITIONAL("zh-TW"),
    ENGLISH("en"),
    ;

    companion object {
        fun fromName(name: String?): AppLanguage =
            entries.firstOrNull { it.name == name } ?: SYSTEM

        /**
         * 根据设备语言决定实际使用的语言。
         *
         * 非简中、繁中、英文一律回落简体中文。
         */
        fun resolveSystem(system: java.util.Locale): AppLanguage = when {
            system.language == "zh" && isTraditional(system) -> TRADITIONAL
            system.language == "zh" -> SIMPLIFIED
            system.language == "en" -> ENGLISH
            else -> SIMPLIFIED
        }

        private fun isTraditional(locale: java.util.Locale): Boolean {
            val script = runCatching { locale.script }.getOrNull()
            if (script == "Hant") return true
            if (script == "Hans") return false
            return locale.country in setOf("TW", "HK", "MO")
        }
    }
}

/** 主题模式。 */
enum class ThemeMode {
    /** 跟随系统的深色/浅色设置（默认）。 */
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: SYSTEM
    }
}

/**
 * 普通偏好设置（非敏感，明文存储）。
 *
 * 与 [SecurePrefs] 的区别：这里放的是界面偏好，泄漏也无所谓，
 * 所以不需要走 Android Keystore 加解密。
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _animationsEnabled = MutableStateFlow(prefs.getBoolean(KEY_ANIMATIONS, true))

    /** 是否启用页面切换过渡动画。根组件订阅它来决定 NavHost 的转场。 */
    val animationsEnabled: StateFlow<Boolean> = _animationsEnabled.asStateFlow()

    fun setAnimationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ANIMATIONS, enabled).apply()
        _animationsEnabled.value = enabled
    }

    private val _themeMode = MutableStateFlow(
        ThemeMode.fromName(prefs.getString(KEY_THEME_MODE, null)),
    )

    /** 主题模式：跟随系统 / 强制浅色 / 强制深色。 */
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeMode.value = mode
    }

    private val _backgroundImagePath = MutableStateFlow(
        prefs.getString(KEY_BACKGROUND, null)?.takeIf { java.io.File(it).isFile },
    )

    /** 自定义全局背景图的本地路径；null 表示使用纯色背景。 */
    val backgroundImagePath: StateFlow<String?> = _backgroundImagePath.asStateFlow()

    fun setBackgroundImage(path: String?) {
        prefs.edit().putString(KEY_BACKGROUND, path).apply()
        _backgroundImagePath.value = path
    }

    private val _language = MutableStateFlow(
        AppLanguage.fromName(prefs.getString(KEY_LANGUAGE, null)),
    )

    /** 界面语言偏好（SYSTEM = 跟随系统）。 */
    val language: StateFlow<AppLanguage> = _language.asStateFlow()

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _language.value = language
    }

    private val _disclaimerAccepted = MutableStateFlow(prefs.getBoolean(KEY_DISCLAIMER, false))

    /** 用户是否已同意首次启动的免责声明。 */
    val disclaimerAccepted: StateFlow<Boolean> = _disclaimerAccepted.asStateFlow()

    fun setDisclaimerAccepted(accepted: Boolean) {
        prefs.edit().putBoolean(KEY_DISCLAIMER, accepted).apply()
        _disclaimerAccepted.value = accepted
    }

    companion object {
        const val PREFS_NAME = "xhulib_settings"
        const val KEY_LANGUAGE = "language"
        private const val KEY_DISCLAIMER = "disclaimer_accepted"
        const val KEY_ANIMATIONS = "animations_enabled"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_BACKGROUND = "background_image"
    }
}
