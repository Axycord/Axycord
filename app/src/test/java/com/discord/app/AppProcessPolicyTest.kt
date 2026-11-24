package com.discord.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppProcessPolicyTest {
    @Test
    fun onlyDedicatedFcmProcessSkipsTheAppGraph() {
        assertTrue(isFcmProcess("mmaarij.axycord:fcm", "mmaarij.axycord"))
        assertFalse(isFcmProcess("mmaarij.axycord", "mmaarij.axycord"))
        assertFalse(isFcmProcess(null, "mmaarij.axycord"))
    }
}
