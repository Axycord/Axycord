package com.discord.widgets.user.profile

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import android.view.ContextThemeWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.discord.R
import com.discord.api.user.UserProfile
import com.linecorp.apng.ApngDrawable
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProfileEffectPlaybackTest {
    @Test fun loopLayerSurvivesMultiplePassesAndClearReleasesIt() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: ProfileEffectView
        lateinit var drawable: ApngDrawable
        instrumentation.runOnMainSync {
            view = ProfileEffectView(context()).apply { layout(0, 0, 304, 595) }
            drawable = decodeFixture()
            view.playDecodedLayers(listOf(layer(loop = true) to drawable), introDelay = 0)
        }
        try {
            SystemClock.sleep(660)
            instrumentation.runOnMainSync {
                assertFalse(drawable.isRecycled)
                assertEquals(1, view.childCount)
                assertEquals(4f, view.getChildAt(0).translationZ, 0f)
            }
        } finally {
            instrumentation.runOnMainSync {
                view.clear()
                assertTrue(drawable.isRecycled)
                assertEquals(0, view.childCount)
            }
        }
    }

    @Test fun introLayerIsRecycledWhenItsSinglePassFinishes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var view: ProfileEffectView
        lateinit var drawable: ApngDrawable
        instrumentation.runOnMainSync {
            view = ProfileEffectView(context()).apply { layout(0, 0, 304, 595) }
            drawable = decodeFixture()
            view.playDecodedLayers(listOf(layer(loop = false) to drawable), introDelay = 0)
        }
        try {
            SystemClock.sleep(260)
            instrumentation.runOnMainSync {
                assertTrue(drawable.isRecycled)
                assertEquals(0, view.childCount)
            }
        } finally { instrumentation.runOnMainSync { view.clear() } }
    }

    private fun layer(loop: Boolean) = UserProfile.ProfileEffectLayer(
        "unused-fixture", loop, duration = 200, loopDelay = 20, zIndex = 4)

    private fun context(): Context = ContextThemeWrapper(
        InstrumentationRegistry.getInstrumentation().targetContext, R.style.AppTheme_Dark)

    private fun decodeFixture() = ApngDrawable.decode(ByteArrayInputStream(Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAACGFjVEwAAAACAAAAAPONk3AAAAAa" +
            "ZmNUTAAAAAAAAAABAAAAAQAAAAAAAAAAAAEACgAAWn8w0AAAAA1JREFUeJxj+M/A8B8ABQAB/4mZ" +
            "PR0AAAAaZmNUTAAAAAEAAAABAAAAAQAAAAAAAAAAAAEACgAAwQzaBAAAABFmZEFUAAAAAnicY2Bg" +
            "+P8fAAMCAf/1e6XXAAAAAElFTkSuQmCC", Base64.DEFAULT)))
}
