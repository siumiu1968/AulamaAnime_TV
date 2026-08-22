package com.jing.sakura.detail

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailRelatedPreviewRequestOwnershipTest {
    @Test
    fun staleCompletionCannotReleaseNewRequestOwnership() {
        val ownership = DetailRelatedPreviewRequestOwnership()
        val staleGeneration = ownership.startRequest()
        val currentGeneration = ownership.startRequest()

        assertFalse(ownership.owns(staleGeneration))
        assertTrue(ownership.owns(currentGeneration))
    }

    @Test
    fun cancellationInvalidatesCurrentRequestOwnership() {
        val ownership = DetailRelatedPreviewRequestOwnership()
        val currentGeneration = ownership.startRequest()

        ownership.invalidate()

        assertFalse(ownership.owns(currentGeneration))
    }
}
