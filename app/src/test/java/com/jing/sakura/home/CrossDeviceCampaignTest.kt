package com.jing.sakura.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URI

class CrossDeviceCampaignTest {
    @Test fun readyHomeShowsTheCurrentCampaignUntilExplicitlyDismissed() {
        assertTrue(shouldShowCrossDeviceCampaign(
            dismissed = false, homeVisible = true, startupStage = HomeStartupStage.Ready, blockingOverlay = false))
        assertFalse(shouldShowCrossDeviceCampaign(
            dismissed = true, homeVisible = true, startupStage = HomeStartupStage.Ready, blockingOverlay = false))
    }

    @Test fun startupLoadingAndAttentionActionsHavePriorityOverTheCampaign() {
        for (stage in listOf(HomeStartupStage.LoadingData, HomeStartupStage.LoadingPosters, HomeStartupStage.NeedsAttention)) {
            assertFalse("Campaign must wait while startup is $stage", shouldShowCrossDeviceCampaign(
                dismissed = false, homeVisible = true, startupStage = stage, blockingOverlay = false))
        }
    }

    @Test fun loginRegionAndNonHomeScreensAlwaysTakePriority() {
        assertFalse(shouldShowCrossDeviceCampaign(
            dismissed = false, homeVisible = true, startupStage = HomeStartupStage.Ready, blockingOverlay = true))
        assertFalse(shouldShowCrossDeviceCampaign(
            dismissed = false, homeVisible = false, startupStage = HomeStartupStage.Ready, blockingOverlay = false))
        assertFalse(shouldShowCrossDeviceCampaign(
            dismissed = false, homeVisible = false, startupStage = HomeStartupStage.Ready, blockingOverlay = true))
    }

    @Test fun qrDestinationsUseTheOfficialWebsiteAndStableAndroidDownloadRoute() {
        assertEquals(URI("https://aulama.org/anime/"), URI(CrossDeviceCampaign.WEB_URL))
        val download = URI(CrossDeviceCampaign.ANDROID_DOWNLOAD_URL)
        assertEquals("https", download.scheme)
        assertEquals("aulama.org", download.host)
        assertTrue(download.path in listOf("/anime/downloads/", "/anime/downloads/aulama-anime.apk"))
    }
}
