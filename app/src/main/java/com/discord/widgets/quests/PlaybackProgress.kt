package com.discord.widgets.quests

internal class PlaybackProgress(resumeSeconds: Int) {
    private var position = resumeSeconds.toLong() * 1_000L
    val seconds: Int get() = (position / 1_000L).toInt()
    val positionMillis: Long get() = position

    fun observe(playerPosition: Int, elapsedMillis: Long): Boolean {
        val observed = playerPosition.toLong()
        // Credit only measured playback and reject jumps beyond the elapsed sample interval.
        if (observed in position..(position + elapsedMillis.coerceIn(0L, 2_000L) + 500L)) {
            position = observed
            return true
        }
        return false
    }
}
