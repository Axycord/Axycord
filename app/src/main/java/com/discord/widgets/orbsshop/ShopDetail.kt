package com.discord.widgets.orbsshop

import com.discord.R

import androidx.fragment.app.FragmentManager
import androidx.fragment.app.DialogFragment
import com.discord.widgets.notice.WidgetNoticeDialog
import com.discord.utilities.theme.ThemeRuntime
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.discord.api.collectibles.CollectibleProduct
import com.discord.models.user.MeUser
import com.discord.stores.StoreStream
import com.discord.utilities.icon.IconUtils
import com.discord.views.VitoImageView
import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder
import java.net.URI

internal class ShopDetail(
    private val ui: Context,
    private val family: ProductFamily,
    initial: ShopState,
    private val session: ShopSession,
    private val icons: ShopIcons,
    private val dialogs: FragmentManager,
) {
    private val style = ShopStyle(ui)
    private val dialog = Dialog(ui)
    private val confirmationTag = "axycord-shop-redemption"
    private val me = StoreStream.getUsers().getMe()
    private val heading = ui.label(family.name, 24, style.text, bold = true)
    private val summary = ui.label("", 14, style.muted)
    private val stage = FrameLayout(ui)
    private var activePreview: AnimatedShopPreview? = null
    private var animationEnabled = true
    private val pause = ui.button("Pause", style) {
        animationEnabled = !animationEnabled
        activePreview?.setAnimating(animationEnabled)
        updateAnimationButton()
    }
    private val replay = ui.button("Replay", style) {
        animationEnabled = true
        activePreview?.replay()
        updateAnimationButton()
    }
    private val animationControls = ui.row().apply {
        addView(pause, LinearLayout.LayoutParams(0, -2, 1f))
        addView(replay, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dp(8) })
    }
    private val feedback = ui.label("", 13, style.muted).apply {
        accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val confirm = ui.button("", style, primary = true) { confirmRedemption() }
    private val choices = linkedMapOf<Offer, MaterialButton>()
    private var selected = family.variants.first()
    private var state = initial

    init {
        val content = ui.column().apply {
            addLine(ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(ui.label(when (family.kind) {
                    CosmeticKind.AVATAR -> "Avatar preview"
                    CosmeticKind.NAMEPLATE -> "Nameplate preview"
                    else -> "Profile preview"
                }, 13, style.muted, true),
                    LinearLayout.LayoutParams(0, -2, 1f))
            }, 8)
            val previewHeight = when (family.kind) {
                CosmeticKind.AVATAR -> 208
                CosmeticKind.NAMEPLATE -> 112
                CosmeticKind.EFFECT, CosmeticKind.FRAME -> LinearLayout.LayoutParams.WRAP_CONTENT
            }
            addLine(stage, 10, previewHeight)
            addLine(animationControls, 8)
            addLine(summary, 12)
            if (family.variants.size > 1) {
                addLine(ui.label("Colour", 15, style.text, true), 16)
                addLine(colourPicker(), 8)
            }
            setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(12))
        }
        dialog.setContentView(ui.column().apply {
            background = ui.shape(style.ink, 4, style.line)
            clipToOutline = true
            addLine(ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dp(18), ui.dp(10), ui.dp(8), ui.dp(6))
                val copy = ui.column().apply {
                    addLine(ui.label(family.kind.singular, 12, style.muted, true))
                    addLine(heading, 4)
                }
                addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
                addView(icons.button(ui, R.drawable.ic_axycord_shop_close, "Close preview", style.text) { dismiss() },
                    LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)))
            })
            addView(ScrollView(ui).apply {
                overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
                addView(content, -1, -2)
            }, LinearLayout.LayoutParams(-1, 0, 1f))
            addLine(ui.column().apply {
                pad(16, 12)
                background = ui.shape(style.panel, 4)
                addLine(confirm)
                addLine(feedback, 8)
            })
        })
    }

    fun show(onDismiss: () -> Unit) {
        dialog.setOnDismissListener {
            (dialogs.findFragmentByTag(confirmationTag) as? DialogFragment)?.dismissAllowingStateLoss()
            onDismiss()
        }
        showVariant()
        ThemeRuntime.applyOverlay(dialog.context.theme)
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val width = ui.resources.displayMetrics.widthPixels - ui.dp(20)
            val height = ui.resources.displayMetrics.heightPixels - ui.dp(84)
            val maxHeight = when (family.kind) {
                CosmeticKind.EFFECT, CosmeticKind.FRAME -> 750
                CosmeticKind.AVATAR -> 540
                CosmeticKind.NAMEPLATE -> 440
            }
            setLayout(minOf(width, ui.dp(640)), minOf(height, ui.dp(maxHeight)))
        }
    }

    fun dismiss() = dialog.dismiss()

    fun update(newState: ShopState) {
        state = newState
        val current = state.products.orEmpty().asSequence().flatMap { it.variants.asSequence() }
            .firstOrNull { it.sku == selected.sku }
        val owned = state.owned?.contains(selected.sku) == true
        val eligible = current?.orbs == selected.orbs
        val disputed = selected.sku in state.unconfirmed
        val balance = state.balance
        confirm.text = when {
            state.busy -> "Checking with Discord…"
            owned -> "In your collection"
            disputed -> "Redemption unconfirmed"
            !eligible -> "Offer changed"
            balance == null || state.owned == null -> "Orbs checkout unavailable"
            balance < selected.orbs -> "Not enough Orbs"
            else -> "Redeem · ${orbText(selected.orbs)}"
        }
        confirm.isEnabled = !state.busy && !owned && !disputed && eligible && state.owned != null &&
            balance != null && balance >= selected.orbs
        confirm.alpha = if (confirm.isEnabled) 1f else 0.62f
        if (confirm.isEnabled) confirm.orbIcon(icons, style.onAction)
        else confirm.setCompoundDrawablesRelative(null, null, null, null)
        val message = state.notice ?: when {
            disputed -> "Check your Discord collection and balance before trying again."
            !eligible -> "Close this preview to see the current offer."
            balance == null || state.owned == null -> "Close this preview and refresh your balance and collection."
            else -> ""
        }
        feedback.text = message
        feedback.visibility = if (message.isBlank()) View.GONE else View.VISIBLE
    }

    private fun colourPicker(): View = HorizontalScrollView(ui).apply {
        isHorizontalScrollBarEnabled = false
        addView(ui.row().apply {
            family.variants.forEachIndexed { index, offer ->
                val label = offer.colour?.takeIf(String::isNotBlank) ?: offer.name
                val choice = ui.button(label, style) { selected = offer; showVariant() }
                val dot = ui.shape(offer.tint(style.accent), 10, style.line).apply {
                    setBounds(0, 0, ui.dp(16), ui.dp(16))
                }
                choice.setCompoundDrawablesRelative(dot, null, null, null)
                choice.compoundDrawablePadding = ui.dp(7)
                addView(choice, LinearLayout.LayoutParams(-2, -2).apply {
                    if (index > 0) marginStart = ui.dp(6)
                })
                choices[offer] = choice
            }
        })
    }

    private fun showVariant() {
        summary.text = selected.summary
        summary.visibility = if (selected.summary.isBlank()) View.GONE else View.VISIBLE
        choices.forEach { (offer, choice) ->
            val active = offer == selected
            choice.isSelected = active
            choice.backgroundTintList = ColorStateList.valueOf(
                if (active) blend(style.ink, style.accent, 0.13f) else style.panel)
            choice.strokeColor = ColorStateList.valueOf(if (active) style.accent else style.line)
            choice.setTextColor(if (active) style.accent else style.text)
        }
        stage.removeAllViews()
        val preview = when (family.kind) {
            CosmeticKind.AVATAR -> ShopAvatarPreview(ui, style, me, selected)
            CosmeticKind.NAMEPLATE -> ShopNameplatePreview(ui, style, me, selected)
            CosmeticKind.EFFECT, CosmeticKind.FRAME -> FullEffectProfile(ui, style, me, selected)
        }
        activePreview = preview as? AnimatedShopPreview
        animationEnabled = true
        animationControls.visibility = if (activePreview != null && family.kind != CosmeticKind.FRAME) View.VISIBLE else View.GONE
        updateAnimationButton()
        stage.addView(preview, FrameLayout.LayoutParams(-1,
            if (family.kind == CosmeticKind.EFFECT || family.kind == CosmeticKind.FRAME) FrameLayout.LayoutParams.WRAP_CONTENT else -1))
        update(state)
    }

    private fun updateAnimationButton() { pause.text = if (animationEnabled) "Pause" else "Play" }

    private fun confirmRedemption() {
        val offer = selected
        val name = offer.colour?.let { "${family.name} · $it" } ?: offer.name
        if (dialogs.isStateSaved || dialogs.findFragmentByTag(confirmationTag) != null) return
        WidgetNoticeDialog.show(dialogs, headerText = "Redeem $name?",
            bodyText = "Spend ${orbText(offer.orbs)} from your Discord balance.",
            okText = "Redeem", cancelText = ui.getString(R.string.cancel), tag = confirmationTag,
            listenerMap = mapOf(R.id.notice_ok to { session.redeem(me.id, offer) }))
    }
}

private val collectibleJson = GsonBuilder()
    .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
    .create()

internal fun productArt(ui: Context, style: ShopStyle, offer: Offer): View = FrameLayout(ui).apply {
    background = ui.artBackground(style, offer, 0)
    val product = offer.model()
    val art = when (offer.kind) {
        CosmeticKind.AVATAR -> offer.previewDecoration()?.imageUrls(animate = false)?.firstOrNull()
        CosmeticKind.EFFECT -> product?.profileEffectThumbnail()
        CosmeticKind.NAMEPLATE -> offer.previewNameplate()?.staticImageUrl()
        CosmeticKind.FRAME -> product?.profileFrame()?.topStaples()?.firstOrNull()?.assetUrl(offer.sku)
    } ?: offer.product.obj("preview_assets")?.string("fg_static")
    val artwork = if (offer.kind == CosmeticKind.AVATAR) {
        cosmeticAvatar(ui, style, StoreStream.getUsers().getMe(), art, 80)
    } else ui.artwork(art)
    place(artwork, -1, -1, Gravity.CENTER)
}

internal fun Offer.model() = runCatching {
    // Gson bypasses Kotlin constructor defaults on these DTOs. Catalog items omit purchase-only
    // fields and often the item SKU/layers; normalize a copy without changing the cached offer.
    val data = product.deepCopy()
    if (data.string("sku_id")?.toLongOrNull()?.takeIf { it > 0 } == null) data.addProperty("sku_id", sku)
    if (data.int("type") == null) data.addProperty("type", kind.wireType)
    if (data.string("name") == null) data.addProperty("name", name)
    if (data.string("summary") == null) data.addProperty("summary", summary)
    if (data.array("items") == null) data.add("items", com.google.gson.JsonArray())
    data.array("items").objects().forEach { item ->
        if (item.int("type") == null) item.addProperty("type", -1)
        if (item.string("sku_id")?.toLongOrNull()?.takeIf { it > 0 } == null) item.addProperty("sku_id", sku)
        if (item.array("layers") == null) item.add("layers", com.google.gson.JsonArray())
    }
    collectibleJson.fromJson(data, CollectibleProduct::class.java)
}.getOrNull()

internal fun Context.artwork(url: String?) = VitoImageView(this).apply {
    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
    if (url != null && isDiscordArtwork(url)) show(listOf(url), autoPlay = false)
}

internal fun isDiscordArtwork(value: String): Boolean {
    val url = runCatching { URI(value) }.getOrNull() ?: return false
    return url.scheme == "https" && url.host in setOf("cdn.discordapp.com", "media.discordapp.net", "cdn.discordapp.net")
}

internal fun Offer.tint(fallback: Int): Int {
    val colour = swatch?.let {
        runCatching { Color.parseColor(if (it.startsWith("#")) it else "#$it") }.getOrNull()
    }
    val artColour = product.obj("styles")?.array("background_colors")
        ?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()
    return colour ?: artColour?.let { it or 0xFF000000.toInt() } ?: fallback
}

internal fun Context.artBackground(style: ShopStyle, offer: Offer, radius: Int) = GradientDrawable(
    GradientDrawable.Orientation.TL_BR,
    intArrayOf(
        blend(style.ink, offer.tint(style.accent), 0.22f),
        blend(style.ink, offer.tint(style.accent), 0.06f),
    ),
).apply { cornerRadius = dp(radius).toFloat() }

internal fun avatarShowcase(ui: Context, style: ShopStyle, me: MeUser, offer: Offer): View =
    FrameLayout(ui).apply {
        val tint = offer.tint(style.accent)
        place(View(ui).apply { background = ui.shape(blend(style.ink, tint, 0.10f), 60, blend(style.line, tint, 0.35f)) },
            112, 112, Gravity.CENTER)
        val art = offer.previewDecoration()?.imageUrls(animate = false)?.firstOrNull()
        place(cosmeticAvatar(ui, style, me, art, 80), -1, -1, Gravity.FILL)
    }

internal fun cosmeticAvatar(ui: Context, style: ShopStyle, me: MeUser, art: String?, size: Int,
    avatarBackground: Int = style.panel): View =
    FrameLayout(ui).apply {
        val avatar = ui.artwork(null).apply {
            show(listOfNotNull(IconUtils.getForUser(me, size = 128)), autoPlay = false)
            background = ui.shape(avatarBackground, size)
            clipToOutline = true
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        place(avatar, size, size, Gravity.CENTER)
        val overlay = (size * 1.2f).toInt()
        place(ui.artwork(art), overlay, overlay, Gravity.CENTER)
    }
