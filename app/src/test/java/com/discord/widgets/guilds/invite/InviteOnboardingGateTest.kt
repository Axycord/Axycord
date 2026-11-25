package com.discord.widgets.guilds.invite

import com.discord.models.member.GuildMemberFlags.COMPLETED_ONBOARDING
import com.discord.models.member.GuildMemberFlags.STARTED_ONBOARDING
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InviteOnboardingGateTest {
    @Test fun completedMembersDoNotRepeatOnboardingFromAnotherInvite() {
        assertFalse(shouldShowInviteOnboarding(true, true, COMPLETED_ONBOARDING or STARTED_ONBOARDING, false))
        assertFalse(shouldShowInviteOnboarding(true, true, 0, true))
    }

    @Test fun establishedMembershipDoesNotDependOnMemberCacheHydration() {
        assertFalse(shouldShowInviteOnboarding(true, true, null, false))
        assertFalse(shouldShowInviteOnboarding(true, true, 0, false))
    }

    @Test fun unfinishedExistingMembershipCanResume() {
        assertTrue(shouldShowInviteOnboarding(true, true, STARTED_ONBOARDING, false))
    }

    @Test fun newMembershipReceivesOnboardingEvenAfterGuildHydrationOrOldLocalCompletion() {
        assertTrue(shouldShowInviteOnboarding(true, false, null, false))
        assertTrue(shouldShowInviteOnboarding(true, false, 0, true))
        assertFalse(shouldShowInviteOnboarding(true, false, COMPLETED_ONBOARDING, false))
    }

    @Test fun serversWithoutOnboardingNeverLaunchIt() {
        assertFalse(shouldShowInviteOnboarding(false, false, null, false))
        assertFalse(shouldShowInviteOnboarding(false, true, STARTED_ONBOARDING, false))
    }
}
