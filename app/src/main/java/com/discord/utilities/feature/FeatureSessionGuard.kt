package com.discord.utilities.feature

/** Captured credentials deliberately have no generated value equality, copy or string rendering. */
internal class FeatureSessionGuard(private val userId: Long, private val token: String?) {
    @Volatile private var closed = false

    fun permits(enabled: Boolean, discordBackend: Boolean, currentUser: Long, currentToken: String?): Boolean =
        !closed && enabled && discordBackend && userId > 0 && !token.isNullOrBlank() &&
            currentUser == userId && currentToken == token

    fun close() { closed = true }
}
