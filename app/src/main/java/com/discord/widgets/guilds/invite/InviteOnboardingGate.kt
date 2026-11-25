package com.discord.widgets.guilds.invite

import com.discord.models.member.GuildMemberFlags

/** Membership is captured before accepting, since a new join can hydrate the guild before its response. */
internal fun shouldShowInviteOnboarding(
    hasOnboarding: Boolean,
    wasAlreadyMember: Boolean,
    memberFlags: Int?,
    locallyCompleted: Boolean,
): Boolean {
    if (!hasOnboarding) return false
    if (memberFlags != null && GuildMemberFlags.has(memberFlags, GuildMemberFlags.COMPLETED_ONBOARDING)) return false
    if (wasAlreadyMember && locallyCompleted) return false
    // Existing members resume only an explicitly unfinished flow. Missing member data is not a
    // reason to make an established member repeat onboarding; new members still receive it.
    return !wasAlreadyMember || (memberFlags != null &&
        GuildMemberFlags.has(memberFlags, GuildMemberFlags.STARTED_ONBOARDING))
}
