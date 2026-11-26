package com.discord.widgets.user

import com.discord.models.message.Message

internal object MentionFilters {
    const val YOU = 1
    const val EVERYONE = 2
    const val ROLES = 4
    const val ALL = YOU or EVERYONE or ROLES

    fun matches(mask: Int, message: Message, userId: Long, myRoleIds: Collection<Long>): Boolean =
        userId > 0L && (
            // The endpoint already returns this account's mentions. Preserve its complete feed
            // when every type is selected, including historical pings with uncached role data.
            mask == ALL ||
                (mask and YOU != 0 && message.mentions.orEmpty().any { it.id == userId }) ||
                (mask and EVERYONE != 0 && message.mentionEveryone == true) ||
                (mask and ROLES != 0 && message.mentionRoles.orEmpty().any { it in myRoleIds })
            )

    /** A repeated page must not keep an empty filtered list fetching the same cursor forever. */
    fun isLastPage(before: Long?, ids: List<Long>, pageSize: Int): Boolean =
        ids.size < pageSize || (before != null && ids.none { it < before })
}
