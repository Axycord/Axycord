package com.discord.widgets.user

import com.discord.api.user.User
import com.discord.models.message.Message
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MentionFiltersTest {
    private val me = 10L
    private val roles = listOf(20L)

    @Test fun directMentionsExcludeOtherUsersAndRoleOnlyPings() {
        assertTrue(matches(MentionFilters.YOU, Message(1, mentions = listOf(User(me, "me")))))
        assertFalse(matches(MentionFilters.YOU, Message(1, mentions = listOf(User(11, "other")))))
        assertFalse(matches(MentionFilters.YOU, Message(1, mentionRoles = roles)))
        assertFalse(matches(MentionFilters.YOU, Message(1, mentionEveryone = true)))
    }

    @Test fun everyoneRequiresTheSpecialMentionFlag() {
        assertTrue(matches(MentionFilters.EVERYONE, Message(1, mentionEveryone = true)))
        assertFalse(matches(MentionFilters.EVERYONE, Message(1, mentionEveryone = false)))
        assertFalse(matches(MentionFilters.EVERYONE, Message(1, content = "everyone", mentions = listOf(User(me, "me")))))
    }

    @Test fun roleMentionsMustTargetOneOfMyRoles() {
        assertTrue(matches(MentionFilters.ROLES, Message(1, mentionRoles = listOf(21, 20))))
        assertFalse(matches(MentionFilters.ROLES, Message(1, mentionRoles = listOf(21))))
        assertFalse(MentionFilters.matches(MentionFilters.ROLES, Message(1, mentionRoles = roles), me, emptyList()))
    }

    @Test fun selectedCategoriesCombineWithoutDependingOnUnselectedTypes() {
        val message = Message(1, mentions = listOf(User(me, "me")), mentionEveryone = true)
        assertTrue(matches(MentionFilters.YOU or MentionFilters.EVERYONE, message))
        assertFalse(matches(MentionFilters.ROLES, message))
        assertFalse(matches(0, message))
        assertFalse(MentionFilters.matches(MentionFilters.ALL, message, 0, roles))
    }

    @Test fun missingMetadataRemainsVisibleOnlyInTheUnfilteredFeed() {
        assertTrue(matches(MentionFilters.ALL, Message(1)))
        assertFalse(matches(MentionFilters.YOU, Message(1)))
        assertFalse(matches(MentionFilters.EVERYONE, Message(1)))
        assertFalse(matches(MentionFilters.ROLES, Message(1)))
    }

    @Test fun fullPagesAdvanceUsingRawIdsEvenWhenNoMessagesMatch() {
        assertFalse(MentionFilters.isLastPage(null, (30L..54L).toList(), 25))
        assertFalse(MentionFilters.isLastPage(30, (5L..29L).toList(), 25))
        assertTrue(MentionFilters.isLastPage(30, (30L..54L).toList(), 25))
        assertTrue(MentionFilters.isLastPage(30, emptyList(), 25))
        assertTrue(MentionFilters.isLastPage(30, listOf(29), 25))
    }

    private fun matches(mask: Int, message: Message) = MentionFilters.matches(mask, message, me, roles)
}
