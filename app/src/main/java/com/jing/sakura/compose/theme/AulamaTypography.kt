@file:OptIn(androidx.tv.material3.ExperimentalTvMaterial3Api::class)

package com.jing.sakura.compose.theme

import androidx.compose.material3.Typography as MaterialTypography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Typography as TvTypography
import com.jing.sakura.R
import com.jing.sakura.compose.common.TvLanguage

private val ClaudeSans = FontFamily(
    Font(
        resId = R.font.anthropic_sans_variable,
        weight = FontWeight.Normal
    )
)

private fun TextStyle.withClaudeStyle(weight: FontWeight): TextStyle = copy(
    fontFamily = ClaudeSans,
    fontWeight = weight,
    letterSpacing = 0.sp
)

private fun TextStyle.withAnimeDisplayStyle(fontFamily: FontFamily): TextStyle = copy(
    fontFamily = fontFamily,
    fontWeight = FontWeight.Black,
    letterSpacing = 0.sp
)

private fun animeDisplayFontFamily(language: TvLanguage): FontFamily = FontFamily(
    Font(
        resId = when (language) {
            TvLanguage.Traditional -> R.font.resource_han_rounded_hk_heavy
            TvLanguage.Simplified -> R.font.resource_han_rounded_cn_heavy
        },
        weight = FontWeight.Black
    )
)

private val defaultTvTypography = TvTypography()

internal fun createAulamaTvTypography(language: TvLanguage): TvTypography {
    val displayFont = animeDisplayFontFamily(language)
    return defaultTvTypography.copy(
        displayLarge = defaultTvTypography.displayLarge.withAnimeDisplayStyle(displayFont),
        displayMedium = defaultTvTypography.displayMedium.withAnimeDisplayStyle(displayFont),
        displaySmall = defaultTvTypography.displaySmall.withAnimeDisplayStyle(displayFont),
        headlineLarge = defaultTvTypography.headlineLarge.withAnimeDisplayStyle(displayFont),
        headlineMedium = defaultTvTypography.headlineMedium.withAnimeDisplayStyle(displayFont),
        headlineSmall = defaultTvTypography.headlineSmall.withAnimeDisplayStyle(displayFont),
        titleLarge = defaultTvTypography.titleLarge.withAnimeDisplayStyle(displayFont),
        titleMedium = defaultTvTypography.titleMedium.withAnimeDisplayStyle(displayFont),
        titleSmall = defaultTvTypography.titleSmall.withAnimeDisplayStyle(displayFont),
        bodyLarge = defaultTvTypography.bodyLarge.withClaudeStyle(FontWeight.Normal),
        bodyMedium = defaultTvTypography.bodyMedium.withClaudeStyle(FontWeight.Normal),
        bodySmall = defaultTvTypography.bodySmall.withClaudeStyle(FontWeight.Normal),
        labelLarge = defaultTvTypography.labelLarge.withAnimeDisplayStyle(displayFont),
        labelMedium = defaultTvTypography.labelMedium.withClaudeStyle(FontWeight.SemiBold),
        labelSmall = defaultTvTypography.labelSmall.withClaudeStyle(FontWeight.Medium)
    )
}

private val defaultMaterialTypography = MaterialTypography()

internal fun createAulamaMaterialTypography(language: TvLanguage): MaterialTypography {
    val displayFont = animeDisplayFontFamily(language)
    return defaultMaterialTypography.copy(
        displayLarge = defaultMaterialTypography.displayLarge.withAnimeDisplayStyle(displayFont),
        displayMedium = defaultMaterialTypography.displayMedium.withAnimeDisplayStyle(displayFont),
        displaySmall = defaultMaterialTypography.displaySmall.withAnimeDisplayStyle(displayFont),
        headlineLarge = defaultMaterialTypography.headlineLarge.withAnimeDisplayStyle(displayFont),
        headlineMedium = defaultMaterialTypography.headlineMedium.withAnimeDisplayStyle(displayFont),
        headlineSmall = defaultMaterialTypography.headlineSmall.withAnimeDisplayStyle(displayFont),
        titleLarge = defaultMaterialTypography.titleLarge.withAnimeDisplayStyle(displayFont),
        titleMedium = defaultMaterialTypography.titleMedium.withAnimeDisplayStyle(displayFont),
        titleSmall = defaultMaterialTypography.titleSmall.withAnimeDisplayStyle(displayFont),
        bodyLarge = defaultMaterialTypography.bodyLarge.withClaudeStyle(FontWeight.Normal),
        bodyMedium = defaultMaterialTypography.bodyMedium.withClaudeStyle(FontWeight.Normal),
        bodySmall = defaultMaterialTypography.bodySmall.withClaudeStyle(FontWeight.Normal),
        labelLarge = defaultMaterialTypography.labelLarge.withAnimeDisplayStyle(displayFont),
        labelMedium = defaultMaterialTypography.labelMedium.withClaudeStyle(FontWeight.SemiBold),
        labelSmall = defaultMaterialTypography.labelSmall.withClaudeStyle(FontWeight.Medium)
    )
}
