package com.discord.widgets.orbsshop

import com.discord.api.user.Nameplate
import com.discord.api.user.AvatarDecorationData
import com.discord.api.user.UserProfile

internal fun Offer.previewDecoration(): AvatarDecorationData? {
    if (kind != CosmeticKind.AVATAR) return null
    val item = product.array("items").objects().firstOrNull { it.int("type") == kind.wireType } ?: return null
    val itemSku = item.string("sku_id")?.toLongOrNull()?.takeIf { it > 0 } ?: sku
    if (itemSku <= 0) return null
    // Modern media assets resolve by SKU, even when the legacy preset hash is absent.
    return AvatarDecorationData(item.string("asset").orEmpty(), itemSku)
}

/** The catalog can omit the per-item SKU that the owned-collectibles endpoint normally includes. */
internal fun Offer.previewNameplate(): Nameplate? {
    if (kind != CosmeticKind.NAMEPLATE) return null
    val item = product.array("items").objects().firstOrNull { it.int("type") == kind.wireType } ?: return null
    val itemSku = item.string("sku_id")?.toLongOrNull()?.takeIf { it > 0 } ?: sku
    if (itemSku <= 0) return null
    return Nameplate(item.string("asset").orEmpty(), itemSku,
        item.string("label") ?: name, item.string("palette").orEmpty())
}

internal fun Offer.previewEffect(): UserProfile.ProfileEffect? {
    if (kind != CosmeticKind.EFFECT) return null
    val spec = EffectSpec.fromProduct(product)
    val layers = spec.layers.filter { isDiscordArtwork(it.source) }.map { layer ->
        UserProfile.ProfileEffectLayer(
            src = layer.source, loop = layer.looping, width = layer.width, height = layer.height,
            duration = layer.duration, start = layer.start, loopDelay = layer.delay,
            position = UserProfile.ProfileEffectPosition(layer.x, layer.y), zIndex = layer.z,
            randomizedSources = layer.alternatives.takeIf { urls -> urls.all(::isDiscordArtwork) }
                ?.map { UserProfile.ProfileEffectSource(it) },
        )
    }
    val still = spec.still?.takeIf(::isDiscordArtwork)
    return UserProfile.ProfileEffect(sku, product = UserProfile.ProfileEffectProduct(listOf(
        UserProfile.ProfileEffectItem(sku, CosmeticKind.EFFECT.wireType, animationType = 0,
            staticFrameSrc = still, reducedMotionSrc = still, effects = layers),
    )))
}
