package com.discord.activities

import android.webkit.WebView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.discord.api.embeddedactivities.EmbeddedActivityDiscordConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EmbeddedActivityWebViewSmokeTest {
    @Test
    fun webViewStartsHardenedWithoutNetwork() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val webView = EmbeddedActivityWebView(
                ApplicationProvider.getApplicationContext(),
                applicationId = 12L,
                frameId = "smoke",
                discordConfig = EmbeddedActivityDiscordConfig("//discord.com/api", "production", "cdn.discordapp.com"),
                onExternalUrl = { _, result -> result(false) },
                onPermissionRequest = { it.deny() },
                onAuthorizeRequest = { _, _, deny -> deny() },
                onCrash = {},
                onLoadError = {},
            )
            assertTrue(webView.settings.javaScriptEnabled)
            assertFalse(webView.settings.allowFileAccess)
            assertFalse(webView.settings.allowContentAccess)
            assertFalse(webView.settings.javaScriptCanOpenWindowsAutomatically)
            assertEquals(WebView.OVER_SCROLL_NEVER, webView.overScrollMode)
            webView.loadDataWithBaseURL(
                "https://discord.com/",
                "<html><body>offline smoke</body></html>",
                "text/html",
                "utf-8",
                null,
            )
            webView.destroySafely()
        }
    }
}
