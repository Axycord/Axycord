package com.discord.widgets.orbsshop

internal data class CatalogKey(val userId: Long, val locale: String?, val country: String?)

internal data class CachedCatalog(
    val key: CatalogKey,
    val savedAt: Long,
    val products: List<ProductFamily>,
    val schema: Int = 1,
) {
    fun isFresh(now: Long) = now - savedAt in 0 until FRESH_CATALOG_MS
}

internal const val FRESH_CATALOG_MS = 15 * 60 * 1000L
