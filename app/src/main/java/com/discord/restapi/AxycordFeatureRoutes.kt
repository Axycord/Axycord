package com.discord.restapi

import com.google.gson.JsonObject
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Built-in features share the default client transport and pin each request to its owning session. */
interface AxycordFeatureRoutes {
    @GET("collectibles-shop?tab=orbs&include_bundles=false&variants_return_style=2")
    fun featureOrbsCatalog(@Header("Authorization") token: String, @Query("country_code") country: String?): Call<ResponseBody>

    @GET("collectibles-categories/v2?tab=catalog&variants_return_style=2")
    fun featureMainCatalog(@Header("Authorization") token: String, @Query("country_code") country: String?): Call<ResponseBody>

    @GET("collectibles-shop?tab=catalog&variants_return_style=2")
    fun featureMainCatalogFallback(@Header("Authorization") token: String, @Query("country_code") country: String?): Call<ResponseBody>

    @GET("users/@me/virtual-currency/balance")
    fun featureOrbsBalance(@Header("Authorization") token: String): Call<JsonObject>

    @GET("users/@me/collectibles-purchases")
    fun featureCollectiblesPurchases(@Header("Authorization") token: String): Call<ResponseBody>

    @POST("virtual-currency/skus/{sku}/redeem")
    fun featureRedeem(@Header("Authorization") token: String, @Path("sku") sku: Long, @Body body: JsonObject): Call<ResponseBody>

    @GET("quests/@me")
    fun featureCurrentQuests(@Header("Authorization") token: String): Call<JsonObject>

    @GET("quests/@me/claimed")
    fun featureClaimedQuests(@Header("Authorization") token: String): Call<JsonObject>

    @POST("quests/{id}/enroll")
    fun featureEnrollQuest(@Header("Authorization") token: String, @Path("id") id: String, @Body body: JsonObject): Call<JsonObject>

    @POST("quests/{id}/claim-reward")
    fun featureClaimQuest(@Header("Authorization") token: String, @Path("id") id: String, @Body body: JsonObject): Call<JsonObject>

    @GET("quests/{id}/reward-code")
    fun featureQuestRewardCode(@Header("Authorization") token: String, @Path("id") id: String): Call<JsonObject>

    @POST("quests/{id}/video-progress")
    fun featureQuestVideoProgress(@Header("Authorization") token: String, @Path("id") id: String, @Body body: JsonObject): Call<JsonObject>
}
