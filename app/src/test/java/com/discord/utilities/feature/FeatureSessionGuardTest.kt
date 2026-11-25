package com.discord.utilities.feature

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureSessionGuardTest {
    @Test fun requiresEnabledFeatureDiscordBackendAndOriginalCredentials() {
        val session = FeatureSessionGuard(42, "fixture-session")
        assertTrue(session.permits(true, true, 42, "fixture-session"))
        assertFalse(session.permits(false, true, 42, "fixture-session"))
        assertFalse(session.permits(true, false, 42, "fixture-session"))
        assertFalse(session.permits(true, true, 43, "fixture-session"))
        assertFalse(session.permits(true, true, 42, "replacement-session"))
        assertFalse(session.permits(true, true, 42, null))
    }

    @Test fun closedSessionCannotResumeWhenFlagOrAccountReturns() {
        val session = FeatureSessionGuard(42, "fixture-session")
        session.close()
        session.close()
        assertFalse(session.permits(true, true, 42, "fixture-session"))
    }

    @Test fun rejectsSignedOutOrEmptyCredentials() {
        assertFalse(FeatureSessionGuard(0, "fixture-session").permits(true, true, 0, "fixture-session"))
        assertFalse(FeatureSessionGuard(42, " ").permits(true, true, 42, " "))
        assertFalse(FeatureSessionGuard(42, null).permits(true, true, 42, null))
    }
}
