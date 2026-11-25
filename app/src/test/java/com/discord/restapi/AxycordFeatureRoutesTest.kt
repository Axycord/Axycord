package com.discord.restapi

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AxycordFeatureRoutesTest {
    private interface HostRoutes : AxycordFeatureRoutes

    @Test fun inheritedEndpointsPreserveCapturedAccountQueriesAndActionBodies() {
        MockWebServer().use { server ->
            val api = Retrofit.Builder().baseUrl(server.url("/api/v9/"))
                .addConverterFactory(GsonConverterFactory.create()).build().create(HostRoutes::class.java)
            val body = JsonObject().apply { addProperty("fixture", "action") }
            val token = "fixture-original-account"
            val calls = listOf<Pair<Call<*>, String>>(
                api.featureOrbsCatalog(token, "US") to "collectibles-shop?tab=orbs&include_bundles=false&variants_return_style=2&country_code=US",
                api.featureMainCatalog(token, "US") to "collectibles-categories/v2?tab=catalog&variants_return_style=2&country_code=US",
                api.featureMainCatalogFallback(token, null) to "collectibles-shop?tab=catalog&variants_return_style=2",
                api.featureOrbsBalance(token) to "users/@me/virtual-currency/balance",
                api.featureCollectiblesPurchases(token) to "users/@me/collectibles-purchases",
                api.featureRedeem(token, 123, body) to "virtual-currency/skus/123/redeem",
                api.featureCurrentQuests(token) to "quests/@me",
                api.featureClaimedQuests(token) to "quests/@me/claimed",
                api.featureEnrollQuest(token, "123", body) to "quests/123/enroll",
                api.featureClaimQuest(token, "123", body) to "quests/123/claim-reward",
                api.featureQuestRewardCode(token, "123") to "quests/123/reward-code",
                api.featureQuestVideoProgress(token, "123", body) to "quests/123/video-progress",
            )
            calls.forEachIndexed { index, (call, path) ->
                server.enqueue(MockResponse().setBody("{}"))
                val response = call.execute()
                assertTrue(response.isSuccessful)
                (response.body() as? okhttp3.ResponseBody)?.close()
                val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                assertEquals("/api/v9/$path", request.path)
                assertEquals(token, request.getHeader("Authorization"))
                val post = index in setOf(5, 8, 9, 11)
                assertEquals(if (post) "POST" else "GET", request.method)
                if (post) assertEquals(body, JsonParser.parseString(request.body.readUtf8()))
            }
        }
    }
}
