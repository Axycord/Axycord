package com.discord.widgets.orbsshop

import com.discord.R

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import com.google.android.material.button.MaterialButton
import com.discord.views.SearchInputView
import com.google.android.material.textfield.TextInputEditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import com.discord.stores.StoreStream
import androidx.fragment.app.FragmentManager
import rx.subscriptions.CompositeSubscription

internal class ShopView(
    context: Context,
    private val session: ShopSession,
    private val icons: ShopIcons,
    private val dialogs: FragmentManager,
) : LinearLayout(context) {
    private val ui = context
    private val style = ShopStyle(ui)
    private val fallbackWallet = ui.row().apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }
    private val wallet = ui.label("Orbs —", 14, style.text, true).apply {
        gravity = Gravity.START
        pad(0, 8)
        maxLines = 1
    }
    private val refresh = icons.button(ui, R.drawable.ic_axycord_shop_refresh, "Refresh the shop", style.muted) {
        session.load(userId(), force = true)
    }
    private val status = ui.label("", 13, style.muted).apply {
        accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE
    }
    private val filters = linkedMapOf<CosmeticKind?, MaterialButton>()
    private val gallery = ui.column()
    private var state = ShopState()
    private var subscription = CompositeSubscription()
    private var filter: CosmeticKind? = null
    private var query = ""
    private var visibleLimit = PAGE_SIZE
    private var detail: ShopDetail? = null

    init {
        orientation = VERTICAL
        fallbackWallet.addView(wallet)
        addLine(fallbackWallet)
        addLine(searchRow())
        addLine(filterRail(), 10)
        addLine(status, 10)
        addLine(gallery)
        setPadding(0, 0, 0, ui.dp(16))
        isFocusableInTouchMode = true
        descendantFocusability = FOCUS_BEFORE_DESCENDANTS
        paintFilters()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        subscription = CompositeSubscription()
        subscription.add(session.states.subscribe(::render))
        subscription.add(StoreStream.getUsers().observeMeId().distinctUntilChanged().subscribe {
            post { if (isAttachedToWindow) session.load(it) }
        })
        session.load(userId())
    }

    override fun onDetachedFromWindow() {
        detail?.dismiss()
        subscription.unsubscribe()
        super.onDetachedFromWindow()
    }

    private fun searchRow() = ui.row().apply {
        gravity = Gravity.CENTER_VERTICAL
        val field = SearchInputView(ui).apply {
            setHint("Search cosmetics")
            (getEditText() as TextInputEditText).doAfterTextChanged {
                query = it.toString().trim()
                visibleLimit = PAGE_SIZE
                renderGallery()
            }
        }
        addView(field, LayoutParams(0, ui.dp(48), 1f))
        addView(refresh, LayoutParams(ui.dp(48), ui.dp(48)).apply { marginStart = ui.dp(4) })
    }

    private fun filterRail() = HorizontalScrollView(ui).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        addView(ui.row().apply {
            val entries = listOf(
                null to "All", CosmeticKind.AVATAR to "Avatars", CosmeticKind.EFFECT to "Effects",
                CosmeticKind.FRAME to "Frames", CosmeticKind.NAMEPLATE to "Nameplates",
            )
            entries.forEachIndexed { index, (kind, label) ->
                val tab = ui.button(label, style) {
                    filter = kind
                    visibleLimit = PAGE_SIZE
                    paintFilters()
                    renderGallery()
                }.apply {
                    contentDescription = kind?.filter ?: "All cosmetics"
                    textSize = 13f
                    pad(11, 4)
                }
                filters[kind] = tab
                addView(tab, LayoutParams(-2, ui.dp(48)).apply { if (index > 0) marginStart = ui.dp(6) })
            }
        })
    }

    private fun paintFilters() {
        filters.forEach { (kind, button) ->
            val selected = kind == filter
            button.isSelected = selected
            button.backgroundTintList = ColorStateList.valueOf(
                if (selected) blend(style.ink, style.accent, 0.13f) else Color.TRANSPARENT)
            button.strokeColor = ColorStateList.valueOf(if (selected) style.accent else style.line)
            button.setTextColor(if (selected) style.accent else style.text)
        }
    }

    private fun render(newState: ShopState) {
        val redraw = newState.products !== state.products || newState.owned != state.owned ||
            (newState.products == null && newState.busy != state.busy)
        if (state.userId != newState.userId) detail?.dismiss()
        state = newState
        wallet.text = state.balance?.let(::orbText) ?: "Orbs —"
        refresh.isEnabled = !state.busy
        refresh.alpha = if (state.busy) 0.50f else 1f
        val message = state.notice ?: when {
            state.products == null && state.busy -> "Loading cosmetics…"
            state.products != null && (state.balance == null || state.owned == null) ->
                "Refresh to check your balance and collection."
            state.partialCatalog -> "Some collections couldn’t load. Refresh to try again."
            else -> ""
        }
        status.text = message
        status.visibility = if (message.isBlank()) GONE else VISIBLE
        if (redraw) renderGallery()
        detail?.update(state)
    }

    private fun renderGallery() {
        gallery.removeAllViews()
        val products = state.products
        if (products == null) {
            if (state.busy) {
                val placeholders = ui.row()
                repeat(2) { index ->
                    placeholders.addView(View(ui).apply {
                        background = ui.shape(style.panel, 15, style.line)
                    }, LayoutParams(0, ui.dp(160), 1f).apply { if (index == 1) marginStart = ui.dp(10) })
                }
                gallery.addLine(placeholders, 12)
            }
            return
        }
        val visible = products.filter { product ->
            (filter == null || product.kind == filter) &&
                (product.name.contains(query, ignoreCase = true) ||
                    product.variants.any { it.name.contains(query, ignoreCase = true) ||
                        it.colour?.contains(query, ignoreCase = true) == true })
        }
        if (visible.isEmpty()) {
            val message = if (query.isNotEmpty()) "No cosmetics match your search."
                else "No Orbs items are available in this category."
            gallery.addLine(ui.label(message, 15, style.muted).apply { pad(0, 20) })
            return
        }

        val featured = if (query.isBlank() && filter == null && visible.size >= 3) {
            visible.take(16).firstOrNull { it.kind == CosmeticKind.AVATAR }
        } else null
        featured?.let { gallery.addLine(featuredTile(it), 16) }
        val grid = if (featured == null) visible else visible - featured
        val caption = filter?.filter ?: "All cosmetics"
        gallery.addLine(ui.label(caption, 19, style.text, true).apply {
            if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
        }, if (featured != null) 22 else 14)
        grid.take(visibleLimit).chunked(2).forEach { pair ->
            val row = ui.row()
            pair.forEachIndexed { index, family ->
                row.addView(tile(family), LayoutParams(0, -1, 1f).apply {
                    if (index == 1) marginStart = ui.dp(10)
                })
            }
            if (pair.size == 1) {
                row.addView(View(ui), LayoutParams(0, 1, 1f).apply { marginStart = ui.dp(10) })
            }
            gallery.addLine(row, 10)
        }
        if (grid.size > visibleLimit) {
            gallery.addLine(ui.button("Show more", style) { visibleLimit += PAGE_SIZE; renderGallery() }, 14)
        }
    }

    private fun featuredTile(family: ProductFamily): View {
        val offer = family.variants.first()
        return ui.row().apply {
            gravity = Gravity.CENTER_VERTICAL
            background = ui.pressable(style.panel, 4, style.line)
            pad(14, 12)
            val details = ui.column().apply {
                addLine(ui.label(offer.kind.singular, 12, style.muted, true))
                addLine(ui.label(family.name, 23, style.text, true), 6)
                addLine(priceLabel(family), 8)
                addLine(ui.label("Try on ›", 14, style.accent, true), 12)
            }
            addView(details, LayoutParams(0, -2, 1.08f))
            addView(avatarShowcase(ui, style, StoreStream.getUsers().getMe(), offer),
                LayoutParams(0, ui.dp(132), 1f))
            isFocusable = true
            setOnClickListener { open(family) }
        }
    }

    private fun tile(family: ProductFamily): View = ui.column().apply {
        val first = family.variants.first()
        background = ui.pressable(style.panel, 4, style.line)
        clipToOutline = true
        isFocusable = true
        addLine(productArt(ui, style, first), height = 104)
        val copy = ui.column().apply {
            pad(11, 10)
            addLine(ui.label(family.kind.singular, 11, style.muted, true))
            addLine(ui.label(family.name, 15, style.text, true).apply {
                minLines = 2
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, 5)
            addLine(ui.row().apply {
                gravity = Gravity.CENTER_VERTICAL
                addView(priceLabel(family), LayoutParams(0, -2, 1f))
                val colours = family.variants.mapNotNull { it.swatch?.let { _ -> it.tint(style.accent) } }.distinct()
                colours.take(3).forEach { colour ->
                    addView(View(ui).apply {
                        background = ui.shape(colour, 6, style.line)
                        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
                    }, LayoutParams(ui.dp(8), ui.dp(8)).apply { marginStart = ui.dp(3) })
                }
                if (family.variants.size > 1) {
                    contentDescription = "${priceText(family)}, ${family.variants.size} colours"
                    importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
                }
            }, 6)
        }
        addLine(copy)
        setOnClickListener { open(family) }
    }

    private fun priceText(family: ProductFamily): String {
        val owned = state.owned
        if (owned != null && family.variants.all { it.sku in owned }) return "Owned"
        val minimum = family.variants.minOf { it.orbs }
        return if (family.variants.any { it.orbs != minimum }) "From ${orbText(minimum)}" else orbText(minimum)
    }

    private fun priceLabel(family: ProductFamily) = ui.label(priceText(family), 13, style.text, true).apply {
        if (text != "Owned") orbIcon(icons, style.accent, 14)
    }

    private fun open(family: ProductFamily) {
        detail?.dismiss()
        val next = ShopDetail(ui, family, state, session, icons, dialogs)
        detail = next
        next.show { if (detail === next) detail = null }
    }

    private fun userId() = StoreStream.getUsers().getMe().id

    private companion object {
        const val PAGE_SIZE = 24
    }
}
