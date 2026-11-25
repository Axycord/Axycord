package com.discord.widgets.botuikit.views.v2

import android.view.View
import android.view.ViewTreeObserver
import com.discord.databinding.WidgetBotUiContainerComponentBinding
import com.discord.stores.StoreStream
import com.discord.utilities.feature.FeatureFlags

/** Owns only animations started for this thumbnail and releases them when its view leaves. */
internal class ComponentMediaPlayback(private val view: ThumbnailComponentView) : ViewTreeObserver.OnPreDrawListener {
    private val playback = ComponentGifPlayback()
    private var observer: ViewTreeObserver? = null

    fun start() {
        if (observer != null) return
        observer = view.viewTreeObserver.also { it.addOnPreDrawListener(this) }
    }

    fun stopPlayback() = playback.stop()

    fun stop() {
        observer?.takeIf { it.isAlive }?.removeOnPreDrawListener(this)
        observer = null
        stopPlayback()
    }

    override fun onPreDraw(): Boolean {
        if (FeatureFlags.componentsV2FixEnabled() && view.hasWindowFocus() && view.isShown &&
            view.windowVisibility == View.VISIBLE && !hasHiddenContainer() &&
            StoreStream.getUserSettings().getIsAutoPlayGifsEnabled()
        ) playback.scan(view)
        playback.finishFrame()
        return true
    }

    private fun hasHiddenContainer(): Boolean {
        var ancestor = view.parent
        while (ancestor is View) {
            if (ancestor is ContainerComponentView &&
                WidgetBotUiContainerComponentBinding.bind(ancestor).containerSpoiler.visibility == View.VISIBLE
            ) return true
            ancestor = ancestor.parent
        }
        return false
    }
}
