package com.discord.utilities.feature

import com.discord.stores.StoreStream
import com.discord.utilities.rest.ClientBackend
import com.discord.utilities.rest.RestAPI
import com.discord.restapi.RestAPIInterface
import java.io.Closeable
import retrofit2.Call

internal enum class NativeFeature(val id: String) {
    ORBS_SHOP("orbs_shop"), QUESTS("quests");

    fun enabled() = when (this) {
        ORBS_SHOP -> FeatureFlags.orbsShopEnabled()
        QUESTS -> FeatureFlags.questsEnabled()
    }
}

/** Owns calls for one enabled feature and one signed-in account; never persists credentials. */
internal class NativeFeatureSession(val feature: NativeFeature) : Closeable {
    val userId = StoreStream.getUsers().getMe().id
    private val token = StoreStream.getAuthentication().getAuthState()?.token
    private val calls = mutableSetOf<Call<*>>()
    private val guard = FeatureSessionGuard(userId, token)

    val isActive: Boolean get() = guard.permits(feature.enabled(), ClientBackend.isDiscord,
        StoreStream.getUsers().getMe().id, StoreStream.getAuthentication().getAuthState()?.token)

    fun requireActive() = check(isActive) { "This feature session ended. Reopen the feature from Settings." }

    @Synchronized
    fun <T> call(create: RestAPIInterface.(String) -> Call<T>): Call<T> {
        requireActive()
        val call = PendingFeatureCall(RestAPI.api.create(requireNotNull(token)))
        calls.add(call)
        return call
    }

    @Synchronized
    override fun close() {
        guard.close()
        calls.toList().forEach { it.cancel() }
        calls.clear()
    }

    private inner class PendingFeatureCall<T>(private val delegate: Call<T>) : Call<T> by delegate {
        override fun execute(): retrofit2.Response<T> {
            requireActive()
            try { return delegate.execute() } finally { synchronized(this@NativeFeatureSession) { calls.remove(this) } }
        }

        override fun enqueue(callback: retrofit2.Callback<T>) {
            requireActive()
            delegate.enqueue(object : retrofit2.Callback<T> {
                override fun onResponse(call: Call<T>, response: retrofit2.Response<T>) {
                    synchronized(this@NativeFeatureSession) { calls.remove(this@PendingFeatureCall) }
                    callback.onResponse(this@PendingFeatureCall, response)
                }
                override fun onFailure(call: Call<T>, error: Throwable) {
                    synchronized(this@NativeFeatureSession) { calls.remove(this@PendingFeatureCall) }
                    callback.onFailure(this@PendingFeatureCall, error)
                }
            })
        }

        override fun clone(): Call<T> = PendingFeatureCall(delegate.clone()).also {
            synchronized(this@NativeFeatureSession) { requireActive(); calls.add(it) }
        }
    }
}
