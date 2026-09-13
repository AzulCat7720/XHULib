package com.xhulib.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = Typography()

/**
 * 中文界面下适当放宽行高，避免汉字行距过密。
 */
val XhuTypography = Typography(
    displaySmall = default.displaySmall.copy(fontFamily = FontFamily.Default),
    headlineMedium = default.headlineMedium.copy(lineHeight = 40.sp),
    headlineSmall = default.headlineSmall.copy(lineHeight = 36.sp),
    titleLarge = default.titleLarge.copy(lineHeight = 30.sp),
    titleMedium = default.titleMedium.copy(lineHeight = 26.sp),
    titleSmall = default.titleSmall.copy(lineHeight = 22.sp),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = default.bodyMedium.copy(lineHeight = 22.sp),
    bodySmall = default.bodySmall.copy(lineHeight = 18.sp),
    labelLarge = default.labelLarge.copy(lineHeight = 20.sp),
)
