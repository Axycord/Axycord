package com.discord.widgets.botuikit.views.v2

import android.text.Layout
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.TextView
import com.discord.databinding.WidgetBotUiContainerComponentBinding
import com.discord.models.botuikit.ContainerMessageComponent
import com.discord.models.botuikit.MediaGalleryMessageComponent
import com.discord.models.botuikit.MessageComponent
import com.discord.models.botuikit.SeparatorMessageComponent
import com.discord.models.botuikit.TextDisplayMessageComponent
import com.discord.utilities.feature.FeatureFlags
import java.util.WeakHashMap
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Native card-scoped port of Maarij's compact V2 layout; no activity-wide view scanning. */
internal class CompactComponentCard(private val card: ContainerComponentView) : ViewTreeObserver.OnPreDrawListener {
    private val originalWidths = WeakHashMap<View, Int>()
    private val measurements = WeakHashMap<ContainerComponentView, CardMeasurement>()
    private var model: ContainerMessageComponent? = null
    private var observer: ViewTreeObserver? = null

    fun bind(model: ContainerMessageComponent) {
        if (this.model !== model) measurements.clear()
        this.model = model
    }

    fun start() {
        if (observer != null) return
        observer = card.viewTreeObserver.also { it.addOnPreDrawListener(this) }
    }

    fun stop() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        restoreAll()
        measurements.clear()
    }

    private fun restoreAll(): Boolean {
        var changed = false
        originalWidths.forEach { (view, width) -> if (view.setWidth(width)) changed = true }
        originalWidths.clear()
        return changed
    }

    override fun onPreDraw(): Boolean {
        if (!FeatureFlags.componentsV2FixEnabled()) return !restoreAll()
        val parent = card.parent as? ViewGroup ?: return true
        val bound = model ?: return true
        return !fitCard(card, parent, bound)
    }
    private fun fitCard(card: ContainerComponentView, parent: ViewGroup, model: ContainerMessageComponent): Boolean {
        val available = parent.width - parent.paddingLeft - parent.paddingRight - card.horizontalMargins()
        if (available <= 0) return false
        val binding = WidgetBotUiContainerComponentBinding.bind(card)
        val content = binding.components
        val cached = measurements[card]
        val fit = if (cached?.model === model && cached.available == available) cached else {
            val accent = if (binding.accent.visibility == View.GONE) 0 else binding.accent.layoutParams.width
            val chrome = card.horizontalPadding() + content.horizontalPadding() + accent
            val width = measureCard(model.children, content, available, chrome)
            CardMeasurement(model, available, width, chrome).also { measurements[card] = it }
        }
        var changed = if (fit.width == null) restoreWidth(card) else constrainWidth(card, fit.width)
        for (index in 0 until content.childCount) {
            val gallery = content.getChildAt(index) as? MediaGalleryComponentView ?: continue
            val innerWidth = fit.width?.minus(fit.chrome)
            val galleryChanged = if (innerWidth == null) restoreWidth(gallery)
            else constrainWidth(gallery, innerWidth - gallery.horizontalMargins(), ViewGroup.LayoutParams.MATCH_PARENT)
            if (galleryChanged) changed = true
        }
        return changed
    }

    private fun measureCard(children: List<MessageComponent>, content: ViewGroup, available: Int, chrome: Int): Int? {
        if (children.size != content.childCount || available <= chrome) return null
        return fitCardWidth(available, chrome, children.mapIndexed { index, component ->
            val view = content.getChildAt(index)
            val width = when (component) {
                is TextDisplayMessageComponent -> (view as? TextDisplayComponentView)?.naturalTextWidth()
                is MediaGalleryMessageComponent -> component.singleCellWidth(view, available - chrome)
                is SeparatorMessageComponent -> 0
                else -> null
            }
            width?.plus(view.horizontalMargins())
        })
    }

    private fun constrainWidth(view: View, width: Int, resetWidth: Int = view.layoutParams.width): Boolean {
        if (width <= 0 || view.layoutParams.width == width) return false
        originalWidths.getOrPut(view) { resetWidth }
        return view.setWidth(width)
    }

    private fun restoreWidth(view: View): Boolean {
        val original = originalWidths.remove(view) ?: return false
        return view.setWidth(original)
    }

    private data class CardMeasurement(
        val model: ContainerMessageComponent,
        val available: Int,
        val width: Int?,
        val chrome: Int,
    )
}

internal fun fitCardWidth(available: Int, chrome: Int, childWidths: List<Int?>): Int? {
    var content = 0
    for (width in childWidths) content = maxOf(content, width ?: return null)
    return if (content > 0 && content < available - chrome) content + chrome else null
}

private fun TextDisplayComponentView.naturalTextWidth(): Int? {
    val text = getChildAt(0) as? TextView ?: return null
    val displayed = text.layout?.text ?: text.text
    return ceil(Layout.getDesiredWidth(displayed, text.paint)).toInt() + text.compoundPaddingLeft +
        text.compoundPaddingRight + horizontalPadding() + 1
}

private fun MediaGalleryMessageComponent.singleCellWidth(view: View, available: Int): Int? {
    if (view !is MediaGalleryComponentView) return null
    val media = items.singleOrNull()?.media ?: return null
    val width = media.width?.takeIf { it > 0 } ?: return null
    val height = media.height?.takeIf { it > 0 } ?: return null
    val density = view.resources.displayMetrics.density
    return MediaGalleryLayout.layout(
        count = 1,
        width = available,
        gap = (MediaGalleryLayout.GAP_DP * density).roundToInt(),
        singleMaxHeight = (MediaGalleryLayout.SINGLE_MAX_HEIGHT_DP * density).roundToInt(),
        sourceWidth = (width * density).roundToInt(),
        sourceHeight = (height * density).roundToInt(),
    ).cells.singleOrNull()?.width
}

private fun View.setWidth(width: Int): Boolean {
    val params = layoutParams ?: return false
    if (params.width == width) return false
    params.width = width
    layoutParams = params
    return true
}

private fun View.horizontalMargins(): Int {
    val params = layoutParams as? ViewGroup.MarginLayoutParams ?: return 0
    return params.leftMargin + params.rightMargin
}

private fun View.horizontalPadding(): Int = paddingLeft + paddingRight
