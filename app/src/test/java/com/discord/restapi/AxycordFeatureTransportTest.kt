package com.discord.restapi

import com.discord.utilities.analytics.AnalyticSuperProperties
import com.discord.utilities.rest.RestAPI
import com.google.gson.JsonObject
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Call

class AxycordFeatureTransportTest {
    @Test fun everyFeatureEndpointUsesDefaultMetadataAndCapturedAuthorization() {
        MockWebServer().use { server ->
            try {
                RestAPI.install(RestAPIBuilder(server.url("/api/").toString(), EmptyCookieJar), emptyList())
                val api: AxycordFeatureRoutes = RestAPI.api
                val token = "fixture-session"
                val body = JsonObject().apply { addProperty("location", 12) }
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
                calls.forEach { (call, path) ->
                    server.enqueue(MockResponse().setBody("{}"))
                    val response = call.execute()
                    assertTrue(response.isSuccessful)
                    (response.body() as? okhttp3.ResponseBody)?.close()
                    val request = checkNotNull(server.takeRequest(5, TimeUnit.SECONDS))
                    assertEquals("/api/v9/$path", request.path)
                    assertEquals(token, request.getHeader("Authorization"))
                    assertEquals(AnalyticSuperProperties.USER_AGENT, request.getHeader("User-Agent"))
                    assertEquals(AnalyticSuperProperties.superPropertiesStringBase64, request.getHeader("X-Super-Properties"))
                    assertEquals(TimeZone.getDefault().id, request.getHeader("X-Discord-Timezone"))
                }
            } finally { RestAPI.reset() }
        }
    }

    private object EmptyCookieJar : CookieJar {
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit
        override fun loadForRequest(url: HttpUrl): List<Cookie> = emptyList()
    }
}
