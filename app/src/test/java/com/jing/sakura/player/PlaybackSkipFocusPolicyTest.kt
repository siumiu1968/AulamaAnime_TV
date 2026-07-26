package com.jing.sakura.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSkipFocusPolicyTest {
    @Test
    fun seekLeftAndRightStayWithTransportWhenSegmentIsVisible() {
        listOf(
            PlaybackSkipDirection.LEFT,
            PlaybackSkipDirection.RIGHT
        ).forEach { direction ->
            assertEquals(
                PlaybackSkipFocusAction.KEEP_CURRENT,
                PlaybackSkipFocusPolicy.action(
                    zone = PlaybackSkipFocusZone.TRANSPORT,
                    direction = direction,
                    actionsVisible = true,
                    secondaryVisible = true
                )
            )
        }
    }

    @Test
    fun showingSegmentDoesNotRequestAnyImplicitFocusMove() {
        assertEquals(
            PlaybackSkipFocusAction.KEEP_CURRENT,
            PlaybackSkipFocusPolicy.action(
                zone = PlaybackSkipFocusZone.TRANSPORT,
                direction = PlaybackSkipDirection.DOWN,
                actionsVisible = true,
                secondaryVisible = true
            )
        )
    }

    @Test
    fun onlyDirectionUpEntersVisibleSkipActions() {
        assertEquals(
            PlaybackSkipFocusAction.ENTER_PRIMARY,
            PlaybackSkipFocusPolicy.action(
                zone = PlaybackSkipFocusZone.TRANSPORT,
                direction = PlaybackSkipDirection.UP,
                actionsVisible = true,
                secondaryVisible = true
            )
        )
        assertEquals(
            PlaybackSkipFocusAction.KEEP_CURRENT,
            PlaybackSkipFocusPolicy.action(
                zone = PlaybackSkipFocusZone.TRANSPORT,
                direction = PlaybackSkipDirection.UP,
                actionsVisible = false,
                secondaryVisible = false
            )
        )
    }

    @Test
    fun directionDownReturnsFromEitherActionToTransport() {
        listOf(
            PlaybackSkipFocusZone.PRIMARY_ACTION,
            PlaybackSkipFocusZone.SECONDARY_ACTION
        ).forEach { zone ->
            assertEquals(
                PlaybackSkipFocusAction.RETURN_TO_TRANSPORT,
                PlaybackSkipFocusPolicy.action(
                    zone = zone,
                    direction = PlaybackSkipDirection.DOWN,
                    actionsVisible = true,
                    secondaryVisible = true
                )
            )
        }
    }

    @Test
    fun directionLeftReturnsFromPrimaryActionToPrimaryControls() {
        assertEquals(
            PlaybackSkipFocusAction.RETURN_TO_PRIMARY_CONTROLS,
            PlaybackSkipFocusPolicy.action(
                zone = PlaybackSkipFocusZone.PRIMARY_ACTION,
                direction = PlaybackSkipDirection.LEFT,
                actionsVisible = true,
                secondaryVisible = false
            )
        )
    }

    @Test
    fun overflowPaddingCoversFocusedCapsuleScale() {
        val widestHalfOverflow = PlaybackSkipLayoutPolicy.requiredOverflowDp(144f)
        val verticalHalfOverflow = PlaybackSkipLayoutPolicy.requiredOverflowDp(50f)

        assertTrue(PlaybackSkipLayoutPolicy.OVERFLOW_PADDING_DP > widestHalfOverflow)
        assertTrue(PlaybackSkipLayoutPolicy.OVERFLOW_PADDING_DP > verticalHalfOverflow)
    }
}
