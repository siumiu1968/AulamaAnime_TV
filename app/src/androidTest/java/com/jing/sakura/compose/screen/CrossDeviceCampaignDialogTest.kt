package com.jing.sakura.compose.screen

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.input.key.Key
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.jing.sakura.compose.common.AulamaTvColors
import com.jing.sakura.compose.common.LocalTvLanguage
import com.jing.sakura.compose.common.TvLanguage
import com.jing.sakura.compose.theme.SakuraTheme
import com.jing.sakura.home.CrossDeviceCampaign
import com.jing.sakura.home.CrossDeviceCampaignStore
import com.jing.sakura.home.TvDisplayDensityPolicy
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class, androidx.tv.material3.ExperimentalTvMaterial3Api::class)
class CrossDeviceCampaignDialogTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var preferencesName: String
    private lateinit var store: CrossDeviceCampaignStore
    private var dismissals = 0
    private val density = TvDisplayDensityPolicy.effectiveDensity(1f, 1280, 720)

    @Before fun createIsolatedCampaignStore() {
        preferencesName = "cross_device_campaign_test_${System.nanoTime()}"
        store = CrossDeviceCampaignStore(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
    }

    @After fun removeTestPreferences() {
        context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteSharedPreferences(preferencesName)
    }

    private fun open() {
        val visible = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(density, 1f)) {
                SakuraTheme {
                    CompositionLocalProvider(
                        LocalTvLanguage provides TvLanguage.Traditional,
                        androidx.tv.material3.LocalContentColor provides AulamaTvColors.TextPrimary,
                        androidx.compose.material3.LocalContentColor provides AulamaTvColors.TextPrimary
                    ) {
                        if (visible.value) CrossDeviceCampaignDialog(onDismiss = {
                            store.dismiss()
                            dismissals++
                            visible.value = false
                        })
                    }
                }
            }
        }
        compose.waitForIdle()
    }

    @Test fun readable720pArtworkQrAndCloseKeepTheRemoteFocus() {
        open()
        val panel = compose.onNodeWithTag("cross-device-campaign").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("campaign-web-preview").assertIsDisplayed()
        for (tag in listOf("campaign-title", "campaign-web-copy", "campaign-android-copy")) {
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag(tag).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue("$tag must expose its rendered text", layouts.isNotEmpty())
            assertFalse("$tag must fit without clipping", layouts.single().hasVisualOverflow)
            assertTrue("$tag must fit in two readable lines", layouts.single().lineCount <= 2)
        }
        val close = compose.onNodeWithTag("campaign-dismiss").assertIsFocused()
        for (direction in listOf(Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown)) {
            close.performKeyInput { pressKey(direction) }.assertIsFocused()
        }
        val closeBounds = close.fetchSemanticsNode().boundsInRoot
        assertTrue("Close must retain its full TV target height", closeBounds.height >= 44 * density)
        assertTrue("Close must stay inside the panel", closeBounds.top >= panel.top - 4 * density &&
            closeBounds.bottom <= panel.bottom + 4 * density)
        assertTrue("Close must remain entirely on screen", closeBounds.top >= 0 && closeBounds.bottom <= 720)
        assertEquals(CrossDeviceCampaign.WEB_URL, decodeQr("campaign-web-qr"))
        assertEquals(CrossDeviceCampaign.ANDROID_DOWNLOAD_URL, decodeQr("campaign-android-qr"))

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        assertEquals(1280, screenshot.width)
        assertEquals(720, screenshot.height)
        File(context.getExternalFilesDir(null), "cross-device-campaign.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        close.performKeyInput { pressKey(Key.DirectionCenter) }
        compose.onNodeWithTag("cross-device-campaign").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals); assertTrue(store.isDismissed()) }
    }

    @Test fun nativeBackDismissesAndPersistsTheCurrentCampaign() {
        open()
        compose.onNodeWithTag("campaign-dismiss").assertIsFocused()
        InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
        compose.waitForIdle()
        compose.onNodeWithTag("cross-device-campaign").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissals) }
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        assertTrue(preferences.edit().commit())
        assertTrue(CrossDeviceCampaignStore(preferences).isDismissed())
    }

    @Test fun freshInstallsAndExistingCampaignHistoryOnlyDismissTheExplicitCampaign() {
        assertFalse(store.isDismissed())
        store.dismiss("cross-device-previous")
        assertTrue(store.isDismissed("cross-device-previous"))
        assertFalse("An earlier announcement cannot hide this campaign after upgrading", store.isDismissed())
        store.dismiss()
        val preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
        assertTrue(preferences.edit().commit())
        val recreated = CrossDeviceCampaignStore(preferences)
        assertTrue(recreated.isDismissed())
        assertTrue(recreated.isDismissed("cross-device-previous"))
        assertFalse(recreated.isDismissed("cross-device-next"))
    }

    private fun decodeQr(tag: String): String {
        val qr = compose.onNodeWithTag(tag).assertIsDisplayed()
        val bounds = qr.fetchSemanticsNode().boundsInRoot
        assertTrue("$tag must stay large enough to scan at 720p", bounds.width >= 96 * density && bounds.height >= 96 * density)
        val bitmap = qr.captureToImage().asAndroidBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return QRCodeReader().decode(BinaryBitmap(HybridBinarizer(
            RGBLuminanceSource(bitmap.width, bitmap.height, pixels)))).text
    }
}
