package com.jing.sakura.compose.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jing.sakura.home.TvDisplayDensityPolicy

internal fun ComponentActivity.setAulamaTvContent(content: @Composable () -> Unit) {
    val displayWidthPx = resources.displayMetrics.widthPixels
    val displayHeightPx = resources.displayMetrics.heightPixels

    setContent {
        val systemDensity = LocalDensity.current
        val appDensity = remember(systemDensity, displayWidthPx, displayHeightPx) {
            Density(
                density = TvDisplayDensityPolicy.effectiveDensity(
                    systemDensity = systemDensity.density,
                    displayWidthPx = displayWidthPx,
                    displayHeightPx = displayHeightPx
                ),
                fontScale = systemDensity.fontScale
            )
        }

        CompositionLocalProvider(LocalDensity provides appDensity) {
            SakuraTheme(content = content)
        }
    }
}
