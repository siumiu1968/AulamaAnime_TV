package com.jing.sakura.compose.theme

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.jing.sakura.home.TvDisplayDensityPolicy

internal fun ComponentActivity.setAulamaTvContent(content: @Composable () -> Unit) {
    val displayWidthPx = resources.displayMetrics.widthPixels
    val displayHeightPx = resources.displayMetrics.heightPixels

    setContent {
        AulamaTvRoot(displayWidthPx, displayHeightPx, content)
    }
}

/** Same density and theme as the Compose screens, for Compose hosted inside a View screen. */
internal fun ComposeView.setAulamaTvContent(content: @Composable () -> Unit) {
    val displayWidthPx = resources.displayMetrics.widthPixels
    val displayHeightPx = resources.displayMetrics.heightPixels

    setContent {
        AulamaTvRoot(displayWidthPx, displayHeightPx, content)
    }
}

@Composable
private fun AulamaTvRoot(
    displayWidthPx: Int,
    displayHeightPx: Int,
    content: @Composable () -> Unit
) {
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
