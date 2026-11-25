package com.discord.widgets.user.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileEffectGeometryTest {
    @Test fun fullCanvasEffectFitsTheWideProfileWithoutCropping() {
        val matrix = requireNotNull(profileEffectTransform(360, 704, 450, 880, null, null, 0, 0))
        assertEquals(0.8f, matrix.scaleX, 0.0001f)
        assertEquals(matrix.scaleX, matrix.scaleY, 0f)
        assertEquals(0f, matrix.x, 0.0001f)
        assertEquals(0f, matrix.y, 0.0001f)
    }

    @Test fun positionedLayerScalesItsAuthoredSizeAndOffset() {
        val matrix = requireNotNull(profileEffectTransform(608, 900, 608, 520, 304, 260, -3, 190))
        assertEquals(ProfileEffectTransform(1f, 1f, -6f, 380f), matrix)
    }

    @Test fun mismatchedCanvasIsCenteredAndInvalidBoundsAreDeferred() {
        val matrix = requireNotNull(profileEffectTransform(304, 600, 304, 432, null, null, 0, 0))
        assertEquals(84f, matrix.y, 0f)
        assertNull(profileEffectTransform(0, 600, 304, 432, null, null, 0, 0))
        assertNull(profileEffectTransform(304, 600, 0, 432, null, null, 0, 0))
    }
}
