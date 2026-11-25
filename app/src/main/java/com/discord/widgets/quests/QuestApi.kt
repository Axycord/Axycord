package com.discord.widgets.quests

import com.discord.utilities.feature.NativeFeatureSession
import com.google.gson.JsonObject

internal class QuestApi(private val session: NativeFeatureSession) {
    fun current() = session.call { featureCurrentQuests(it) }
    fun claimed() = session.call { featureClaimedQuests(it) }
    fun orbBalance() = session.call { featureOrbsBalance(it) }
    fun enroll(id: String, body: JsonObject) = session.call { featureEnrollQuest(it, id, body) }
    fun claim(id: String, body: JsonObject) = session.call { featureClaimQuest(it, id, body) }
    fun rewardCode(id: String) = session.call { featureQuestRewardCode(it, id) }
    fun videoProgress(id: String, body: JsonObject) = session.call { featureQuestVideoProgress(it, id, body) }
}

internal class QuestException(message: String) : RuntimeException(message)
