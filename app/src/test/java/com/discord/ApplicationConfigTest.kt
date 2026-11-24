package com.discord

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplicationConfigTest {
    @Test
    fun usesAxycordApplicationId() {
        assertEquals("mmaarij.axycord", BuildConfig.APPLICATION_ID)
    }
}
