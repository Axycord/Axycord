package com.discord.utilities.feature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeFeatureFlagsTest {
    @Test fun featuresStartDisabledAndParticipateInSettingsBackup() {
        listOf(ClientSettings.orbsShop, ClientSettings.quests, ClientSettings.componentsV2Fix, ClientSettings.activeNow).forEach { flag ->
            assertFalse(flag.default)
            assertEquals(ClientSettings.Type.BOOLEAN, flag.type)
            assertEquals(ClientSettings.BackupPolicy.INCLUDE, flag.backupPolicy)
            assertTrue(ClientSettings.registered.contains(flag))
        }
    }
}
