package com.discord.widgets.orbsshop

import android.util.AtomicFile
import com.google.gson.Gson
import java.io.File

/** Only catalog entries are persisted. Balance, purchases and sessions are kept in memory. */
internal class CatalogCache(directory: File) {
    private val file = AtomicFile(directory.resolve("catalog.json"))
    private val gson = Gson()

    fun read(key: CatalogKey, now: Long): CachedCatalog? = runCatching {
        file.openRead().bufferedReader().use { reader ->
            gson.fromJson(reader, CachedCatalog::class.java)
        }?.takeIf {
            it.schema == 1 && it.key == key && now - it.savedAt in 0 until MAX_AGE_MS &&
                it.products.all { family -> family.name.isNotBlank() && family.variants.isNotEmpty() && family.variants.all { offer ->
                    offer.sku > 0 && offer.orbs > 0 && offer.name.isNotBlank() &&
                        offer.kind in CosmeticKind.entries && offer.product.isJsonObject
                } }
        }
    }.getOrNull()

    fun write(catalog: CachedCatalog) {
        runCatching {
            val data = gson.toJson(catalog).toByteArray(Charsets.UTF_8)
            val stream = file.startWrite()
            try {
                stream.write(data)
                file.finishWrite(stream)
            } catch (error: Exception) {
                file.failWrite(stream)
                throw error
            }
        }
    }

    private companion object {
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
