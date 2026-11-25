package com.discord.widgets.orbsshop

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.discord.models.user.MeUser
import com.discord.utilities.icon.IconUtils
import com.discord.views.AvatarDecorationView
import com.discord.views.NameplateView

/** Explicit, pausable preview playback; catalog thumbnails never activate this path. */
internal abstract class AnimatedShopPreview(context: Context) : FrameLayout(context) {
    private var enabled = true
    private var playing = false

    fun setAnimating(enabled: Boolean) {
        this.enabled = enabled
        updatePlayback()
    }

    fun replay() {
        enabled = true
        if (playing) onPlaybackChanged(false)
        playing = false
        updatePlayback()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updatePlayback()
    }

    override fun onDetachedFromWindow() {
        if (playing) onPlaybackChanged(false)
        playing = false
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        updatePlayback()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        updatePlayback()
    }

    private fun updatePlayback() {
        val desired = enabled && isAttachedToWindow && isShown && windowVisibility == View.VISIBLE && hasWindowFocus()
        if (desired == playing) return
        playing = desired
        onPlaybackChanged(desired)
    }

    protected abstract fun onPlaybackChanged(playing: Boolean)
}

internal class ShopAvatarPreview(ui: Context, style: ShopStyle, me: MeUser, offer: Offer) : AnimatedShopPreview(ui) {
    private val avatar = ui.artwork(IconUtils.getForUser(me, size = 256)).apply {
        background = ui.shape(style.panel, 112)
        clipToOutline = true
        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
    }
    private val decoration = offer.previewDecoration()
    private val overlay = AvatarDecorationView(ui)

    init {
        place(avatar, 112, 112, Gravity.CENTER)
        place(overlay, 0, 0, Gravity.CENTER)
        overlay.bindPreview(decoration, avatar, animate = false)
        contentDescription = "${offer.name} avatar decoration preview"
    }

    override fun onPlaybackChanged(playing: Boolean) = overlay.bindPreview(decoration, avatar, playing)
}

internal class ShopNameplatePreview(ui: Context, style: ShopStyle, me: MeUser, offer: Offer) : AnimatedShopPreview(ui) {
    private val plate = offer.previewNameplate()
    private val artwork = NameplateView(ui)

    init {
        place(FrameLayout(ui).apply {
            background = ui.shape(style.panel, 4, style.line)
            place(artwork, -1, -1, Gravity.FILL)
            place(cosmeticAvatar(ui, style, me, null, 40), 48, 48, Gravity.CENTER_VERTICAL or Gravity.START, left = 8)
            place(ui.label(me.globalName?.takeIf(String::isNotBlank) ?: me.username, 16, style.text, true),
                -1, -2, Gravity.CENTER_VERTICAL, left = 64, right = 16)
        }, -1, 64, Gravity.CENTER)
        artwork.bindPreview(plate, animate = false)
        contentDescription = "${offer.name} nameplate preview"
    }

    override fun onPlaybackChanged(playing: Boolean) = artwork.bindPreview(plate, playing)
}
