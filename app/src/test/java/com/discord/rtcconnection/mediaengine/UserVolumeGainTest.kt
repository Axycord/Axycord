package com.discord.rtcconnection.mediaengine

import com.discord.widgets.settings.WidgetSettingsVoice
import org.junit.Assert.assertEquals
import org.junit.Test

class UserVolumeGainTest {
    @Test fun peerMixerReceivesFractionalUnityAndBoostGains() {
        assertEquals(0f, userVolumeToGain(0f), 0f)
        assertEquals(0.25f, userVolumeToGain(25f), 0f)
        assertEquals(1f, userVolumeToGain(100f), 0f)
        assertEquals(1.5f, userVolumeToGain(150f), 0f)
        assertEquals(2f, userVolumeToGain(200f), 0f)
        assertEquals(0f, userVolumeToGain(-5f), 0f)
        assertEquals(2f, userVolumeToGain(250f), 0f)
    }

    @Test fun profileSliderCoversSilenceUnityAndTheBoostRange() {
        assertEquals(0f, userVolumeToGain(WidgetSettingsVoice.perceptualToAmplitude(0f)), 0f)
        assertEquals(1f, userVolumeToGain(WidgetSettingsVoice.perceptualToAmplitude(100f)), 0.0001f)
        assertEquals(1.99526f, userVolumeToGain(WidgetSettingsVoice.perceptualToAmplitude(200f)), 0.0001f)
        for (position in listOf(0f, 25f, 50f, 100f, 150f, 200f)) {
            val amplitude = WidgetSettingsVoice.perceptualToAmplitude(position)
            assertEquals(position, WidgetSettingsVoice.amplitudeToPerceptual(amplitude), 0.0001f)
        }
    }
}
