package com.discord.widgets.orbsshop

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShopPreviewAssetsTest {
    @Test fun modernSkuOnlyDecorationsDoNotRequireALegacyPresetHash() {
        val decoration = requireNotNull(offer(CosmeticKind.AVATAR,
            """{"items":[{"type":0}]}""").previewDecoration())
        assertEquals("", decoration.asset)
        assertEquals(42L, decoration.skuId)
        val urls = decoration.imageUrls(animate = true)
        assertEquals(1, urls.size)
        assertEquals(true, urls.single().endsWith("/42/animated"))
    }
    @Test fun decorationCatalogDefaultsSurviveGsonWithoutMutatingTheCachedOffer() {
        val offer = offer(CosmeticKind.AVATAR, """{"items":[{"type":0,"asset":"decoration"}]}""")
        val product = requireNotNull(offer.model())
        val decoration = requireNotNull(product.avatarDecoration())
        assertEquals(42L, product.skuId)
        assertEquals("Preview", product.name)
        assertEquals("", product.summary)
        assertEquals(42L, decoration.skuId)
        assertEquals("decoration", decoration.asset)
        assertEquals(emptyList<Any>(), product.items.single().layers)
        assertEquals(false, offer.product.has("sku_id"))
        assertEquals(false, offer.product.getAsJsonArray("items")[0].asJsonObject.has("layers"))
    }

    @Test fun decorationAnimationRetainsBothModernAndLegacyAssetSources() {
        val decoration = requireNotNull(requireNotNull(offer(CosmeticKind.AVATAR,
            """{"items":[{"type":0,"sku_id":"77","asset":"decoration"}]}""").model()).avatarDecoration())
        val urls = decoration.imageUrls(animate = true)
        assertEquals(2, urls.size)
        assertEquals(true, urls.first().endsWith("/77/animated"))
        assertEquals(true, urls.last().contains("avatar-decoration-presets/decoration.png"))
        assertEquals(true, urls.last().contains("passthrough=true"))
    }
    @Test fun nameplateUsesCatalogSkuWithoutRequiringOwnershipOrAPalette() {
        val offer = offer(CosmeticKind.NAMEPLATE, """{"items":[{"type":2,"asset":"plate"}]}""")
        val plate = requireNotNull(offer.previewNameplate())
        assertEquals(42L, plate.skuId)
        assertEquals("plate", plate.asset)
        assertEquals("Preview", plate.label)
        assertEquals("", plate.palette)
    }

    @Test fun variantSkuAndPaletteStayWithTheSelectedNameplate() {
        val offer = offer(CosmeticKind.NAMEPLATE,
            """{"items":[{"type":2,"sku_id":"77","asset":"variant","palette":"sky"}]}""")
        assertEquals(77L, requireNotNull(offer.previewNameplate()).skuId)
        assertEquals("sky", requireNotNull(offer.previewNameplate()).palette)
        assertNull(offer.copy(kind = CosmeticKind.AVATAR).previewNameplate())
    }

    @Test fun profileEffectPassesAnimationLayersAndLoopTimingToTheNativeRenderer() {
        val offer = offer(CosmeticKind.EFFECT, """{"items":[{"type":1,"staticFrameSrc":"https://cdn.discordapp.com/still.png",
            "effects":[{"src":"https://cdn.discordapp.com/loop.png","loop":true,"start":1000,"duration":2000,
            "loopDelay":500,"width":304,"height":260,"position":{"x":-3,"y":190},"zIndex":12}]}]}""")
        val effect = requireNotNull(offer.previewEffect())
        assertEquals(42L, effect.skuId)
        val item = requireNotNull(effect.product).items.single()
        assertEquals("https://cdn.discordapp.com/still.png", item.staticFrameSrc)
        val layer = item.effects.single()
        assertEquals(true, layer.loop)
        assertEquals(1000L, layer.start)
        assertEquals(2000L, layer.duration)
        assertEquals(500L, layer.loopDelay)
        assertEquals(12, layer.zIndex)
        assertEquals(-3, requireNotNull(layer.position).x)
        assertEquals(190, requireNotNull(layer.position).y)
        assertEquals(304, layer.width)
    }

    @Test fun effectPreviewsExcludeUntrustedArtworkSources() {
        val offer = offer(CosmeticKind.EFFECT, """{"items":[{"type":1,"staticFrameSrc":"http://cdn.discordapp.com/still.png",
            "effects":[{"src":"https://example.com/effect.png"}]}]}""")
        val item = requireNotNull(requireNotNull(offer.previewEffect()).product).items.single()
        assertNull(item.staticFrameSrc)
        assertEquals(emptyList<Any>(), item.effects)
        assertNull(offer.copy(kind = CosmeticKind.NAMEPLATE).previewEffect())
    }

    private fun offer(kind: CosmeticKind, json: String) = Offer(42L, "Preview", "", kind, 100, null, null,
        JsonParser.parseString(json).asJsonObject)
}
