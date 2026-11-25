package com.discord.widgets.orbsshop

import com.discord.stores.StoreStream
import com.discord.utilities.feature.NativeFeatureSession
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.UUID
import okhttp3.ResponseBody
import retrofit2.Call

internal class ShopApi(private val session: NativeFeatureSession) {
    fun catalogKey(userId: Long): CatalogKey {
        session.requireActive()
        check(userId == session.userId)
        return CatalogKey(userId, StoreStream.getUserSettingsSystem().getLocale(),
            StoreStream.getUsers().getMe().storeCountry?.country)
    }

    fun open(userId: Long): Connection = Connection(catalogKey(userId).country)
    fun close() = session.close()
    fun isActive() = session.isActive

    inner class Connection internal constructor(private val country: String?) {
        fun catalog(): CatalogLoad {
            val sources = listOf(
                runCatching { ShopCatalog.products(JsonParser.parseString(session.call { featureOrbsCatalog(it, country) }.read())) },
                runCatching { ShopCatalog.products(JsonParser.parseString(mainCatalog())) },
            )
            session.requireActive()
            val available = sources.mapNotNull { it.getOrNull() }
            if (available.isEmpty()) throw requireNotNull(sources.first().exceptionOrNull())
            return CatalogLoad(ShopCatalog.merge(available.flatten()), available.size < sources.size)
        }

        private fun mainCatalog(): String = try {
            session.call { featureMainCatalog(it, country) }.read()
        } catch (error: ShopHttpError) {
            if (error.status != 404 && error.status != 405) throw error
            session.call { featureMainCatalogFallback(it, country) }.read()
        }

        fun balance(): Int {
            val response = session.call { featureOrbsBalance(it) }.execute()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw ShopHttpError(response.code())
            }
            session.requireActive()
            return response.body()?.int("balance")?.takeIf { it >= 0 }
                ?: error("Discord returned an invalid Orbs balance.")
        }

        fun owned(): Set<Long> {
            val data = JsonParser.parseString(session.call { featureCollectiblesPurchases(it) }.read()).asJsonArray
            return data.objects().mapNotNullTo(mutableSetOf()) { it.string("sku_id")?.toLongOrNull() }
        }

        fun redeem(sku: Long) {
            val body = JsonObject().apply { addProperty("checkout_session_id", UUID.randomUUID().toString()) }
            val data = JsonParser.parseString(session.call { featureRedeem(it, sku, body) }.read())
            check(data.isJsonArray && !data.asJsonArray.isEmpty) { "Discord did not confirm the redemption." }
        }

        private fun Call<ResponseBody>.read(): String {
            val response = execute()
            if (!response.isSuccessful) {
                response.errorBody()?.close()
                throw ShopHttpError(response.code())
            }
            val body = response.body()?.use(ResponseBody::string) ?: error("Discord returned an empty response.")
            session.requireActive()
            return body
        }
    }
}

internal data class CatalogLoad(val products: List<ProductFamily>, val partial: Boolean)
internal class ShopHttpError(val status: Int) : IOException("Discord returned HTTP $status.")
