package com.discord.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EmbeddedActivityOriginTest {
    @Test fun allowsOnlyTheApplicationHttpsOrigin() {
        assertTrue(isActivityNavigation("https://123.discordsays.com/frame/index.html?x=1", 123))
        assertTrue(isActivityNavigation("https://123.discordsays.com:443/path", 123))
        assertFalse(isActivityNavigation("https://124.discordsays.com/", 123))
        assertFalse(isActivityNavigation("http://123.discordsays.com/", 123))
        assertFalse(isActivityNavigation("https://123.discordsays.com:444/", 123))
    }

    @Test fun rejectsPrefixSpoofingAndMalformedTargets() {
        assertFalse(isActivityNavigation("https://123.discordsays.com.evil.example/", 123))
        assertFalse(isActivityNavigation("https://123.discordsays.com@evil.example/", 123))
        assertFalse(isActivityNavigation("https://evil.example@123.discordsays.com/", 123))
        assertFalse(isActivityNavigation("https://123.discordsays.com%2eevil.example/", 123))
        assertFalse(isActivityNavigation("not a URL", 123))
    }
}
