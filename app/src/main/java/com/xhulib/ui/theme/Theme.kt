package com.xhulib.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * 有自定义背景图时，页面底色的不透明度。
 *
 * 取 0（完全透明）—— 否则底色会像一层膜盖在图片上，背景图几乎看不出来。
 * 图片本身另有一层薄蒙版保证文字可读，见 MainActivity 的 CustomBackground。
 */
private const val BACKGROUND_ALPHA = 0f

/** 有自定义背景图时，卡片等容器的底部不透明度（比页面底色更实，保证可读）。 */
private const val CARD_ALPHA = 0.92f

private val LightColors = lightColorScheme(
    primary = GreenPrimaryLight,
    onPrimary = GreenOnPrimaryLight,
    primaryContainer = GreenPrimaryContainerLight,
    onPrimaryContainer = GreenOnPrimaryContainerLight,
    secondary = GreenSecondaryLight,
    onSecondary = GreenOnSecondaryLight,
    secondaryContainer = GreenSecondaryContainerLight,
    onSecondaryContainer = GreenOnSecondaryContainerLight,
    tertiary = GreenTertiaryLight,
    onTertiary = GreenOnTertiaryLight,
    tertiaryContainer = GreenTertiaryContainerLight,
    onTertiaryContainer = GreenOnTertiaryContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = GreenPrimaryDark,
    onPrimary = GreenOnPrimaryDark,
    primaryContainer = GreenPrimaryContainerDark,
    onPrimaryContainer = GreenOnPrimaryContainerDark,
    secondary = GreenSecondaryDark,
    onSecondary = GreenOnSecondaryDark,
    secondaryContainer = GreenSecondaryContainerDark,
    onSecondaryContainer = GreenOnSecondaryContainerDark,
    tertiary = GreenTertiaryDark,
    onTertiary = GreenOnTertiaryDark,
    tertiaryContainer = GreenTertiaryContainerDark,
    onTertiaryContainer = GreenOnTertiaryContainerDark,
)

/**
 * @param dynamicColor Android 12+ 上跟随系统壁纸取色；默认关闭，
 *        以保证图书馆品牌绿在各机型上一致。
 * @param hasCustomBackground 用户设置了自定义背景图。此时把「背景色/表面色」
 *        调成半透明，让底图透出来，同时保证文字仍然清晰；卡片用的是
 *        surfaceContainer 系列，保持不透明。
 */
@Composable
fun XhuLibTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    hasCustomBackground: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    val colorScheme = if (hasCustomBackground) {
        baseScheme.copy(
            background = baseScheme.background.copy(alpha = BACKGROUND_ALPHA),
            surface = baseScheme.surface.copy(alpha = BACKGROUND_ALPHA),
            surfaceContainerLowest = baseScheme.surfaceContainerLowest.copy(alpha = CARD_ALPHA),
            surfaceContainerLow = baseScheme.surfaceContainerLow.copy(alpha = CARD_ALPHA),
            surfaceContainer = baseScheme.surfaceContainer.copy(alpha = CARD_ALPHA),
            surfaceContainerHigh = baseScheme.surfaceContainerHigh.copy(alpha = CARD_ALPHA),
            surfaceContainerHighest = baseScheme.surfaceContainerHighest.copy(alpha = CARD_ALPHA),
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = XhuTypography,
        content = content,
    )
}
