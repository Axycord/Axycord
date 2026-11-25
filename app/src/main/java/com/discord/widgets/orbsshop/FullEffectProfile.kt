package com.discord.widgets.orbsshop

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.discord.api.user.UserProfile
import com.discord.models.user.MeUser
import com.discord.stores.StoreStream
import com.discord.utilities.SnowflakeUtils
import com.discord.utilities.icon.IconUtils
import com.discord.widgets.user.profile.ProfileEffectView
import com.discord.widgets.user.profile.ProfileThemeColors
import com.discord.widgets.user.profile.ProfileFrameView
import kotlin.math.roundToInt
import java.text.DateFormat
import java.util.Date
import rx.Subscription

internal class FullEffectProfile(
    private val ui: Context,
    private val style: ShopStyle,
    private val me: MeUser,
    private val offer: Offer,
) : AnimatedShopPreview(ui) {
    private val spec = EffectSpec.fromProduct(offer.product)
    private val effect = offer.previewEffect()
    private val frame = if (offer.kind == CosmeticKind.FRAME) offer.model()?.profileFrame() else null
    private val frameBack = ProfileFrameView(ui).apply { half = ProfileFrameView.Half.BACK; bind(frame) }
    private val frameFront = ProfileFrameView(ui).apply { half = ProfileFrameView.Half.FRONT; bind(frame) }
    private val stillArtwork = ui.artwork(spec.still)
    private val effectArtwork = ProfileEffectView(ui)
    private val body = ui.column()
    private val card = FrameLayout(ui).apply {
        background = ui.shape(style.ink, 4, style.line)
        clipToOutline = true
        place(ScrollView(ui).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(body, -1, -2)
        }, -1, -1, Gravity.FILL)
        place(stillArtwork, -1, -1, Gravity.FILL)
        place(effectArtwork, -1, -1, Gravity.FILL)
    }
    private var profileUpdates: Subscription? = null

    init {
        background = ui.artBackground(style, offer, 4)
        clipChildren = false
        place(frameBack, -1, -1, Gravity.FILL)
        place(card, -1, -1, Gravity.TOP or Gravity.CENTER_HORIZONTAL)
        place(frameFront, -1, -1, Gravity.FILL)
        render(null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val profiles = StoreStream.getUserProfiles()
        profileUpdates = profiles.observeUserProfile(me.id).subscribe({ profile ->
            post { if (isAttachedToWindow) render(profile) }
        }, {})
        profiles.fetchProfileIfMissing(me.id)
    }

    override fun onDetachedFromWindow() {
        profileUpdates?.unsubscribe()
        profileUpdates = null
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1)
        // Keep the full width. The enclosing scroll view accommodates tall effect canvases.
        val innerWidth = frame?.innerWidth?.takeIf { it > 0 } ?: 1200
        val horizontal = frame?.overflowHorizontal?.coerceAtLeast(0) ?: 0
        val frameScale = width / (innerWidth.toFloat() + 2f * horizontal)
        val top = ((frame?.overflowTop?.coerceAtLeast(0) ?: 0) * frameScale).roundToInt()
        val bottom = ((frame?.overflowBottom?.coerceAtLeast(0) ?: 0) * frameScale).roundToInt()
        val size = spec.atWidth((innerWidth * frameScale).roundToInt().coerceIn(1, width))
        card.layoutParams.width = size.width
        card.layoutParams.height = size.height
        (card.layoutParams as LayoutParams).topMargin = top
        super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(size.height + top + bottom, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        frameBack.anchorTo(card.left, card.top, card.width)
        frameFront.anchorTo(card.left, card.top, card.width)
    }

    override fun onPlaybackChanged(playing: Boolean) {
        stillArtwork.visibility = if (playing) View.GONE else View.VISIBLE
        if (playing) effectArtwork.bind(effect, reducedMotion = false)
        else effectArtwork.clear()
    }

    private fun render(profile: UserProfile?) {
        val metadata = profile?.userProfile
        body.removeAllViews()
        val themeColours = metadata?.themeColors.orEmpty()
        val top = themeColours.getOrNull(0)?.let { it or Color.BLACK } ?: offer.tint(style.accent)
        val bottom = themeColours.getOrNull(1)?.let { it or Color.BLACK } ?: top
        val colors = if (themeColours.size >= 2) ProfileThemeColors.from(top, bottom,
            StoreStream.getUserSettingsSystem().isDarkTheme(),
            StoreStream.getAccessibility().isSyncProfileThemeWithUserThemeEnabled()) else null
        val text = colors?.textDefault ?: style.text
        val muted = colors?.textMuted ?: style.muted
        card.background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(colors?.gradientTop ?: style.ink, colors?.gradientBottom ?: style.ink),
        ).apply {
            cornerRadius = ui.dp(8).toFloat()
            setStroke(ui.dp(1), colors?.borderOverlay ?: style.line)
        }
        val banner = ui.artwork(IconUtils.getForUserBanner(me.id, metadata?.banner ?: me.banner, 512, false)).apply {
            background = ui.shape(metadata?.accentColor?.let { it or Color.BLACK } ?: colors?.headerSurface ?: style.panel, 0)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        val artwork = me.avatarDecoration?.imageUrls(animate = false)?.firstOrNull()
        body.addLine(FrameLayout(ui).apply {
            place(banner, -1, 80, Gravity.TOP)
            place(cosmeticAvatar(ui, style, me, artwork, 62, colors?.bodySurface ?: style.panel),
                80, 80, Gravity.TOP or Gravity.START, left = 8, top = 44)
        }, height = 126)
        body.addLine(ui.column().apply {
            pad(14, 10)
            addLine(ui.label(me.globalName?.takeIf(String::isNotBlank) ?: me.username, 20, text, true))
            val pronouns = metadata?.pronouns?.takeIf(String::isNotBlank)
            addLine(ui.label(listOfNotNull("@${me.username}", pronouns).joinToString(" · "), 13, muted), 4)
            val badges = profile?.badges.orEmpty()
            if (badges.isNotEmpty()) {
                addLine(ui.row().apply {
                    badges.take(8).forEach { badge ->
                        val image = ui.artwork("https://cdn.discordapp.com/badge-icons/${badge.icon}.png").apply {
                            contentDescription = badge.description
                            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
                        }
                        addView(image, LinearLayout.LayoutParams(ui.dp(18), ui.dp(18)).apply {
                            marginEnd = ui.dp(4)
                        })
                    }
                }, 8)
            }
            val bio = metadata?.bio ?: me.bio
            if (!bio.isNullOrBlank()) addLine(section("About me", bio, colors), 12)
            val joined = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(SnowflakeUtils.toTimestamp(me.id)))
            addLine(section("Discord member since", joined, colors), 12)
            val connections = profile?.connectedAccounts.orEmpty().filter { it.visibility == 1 }
                .map { it.name }.filter(String::isNotBlank)
            if (connections.isNotEmpty()) addLine(section("Connections", connections.joinToString("\n"), colors), 12)
        })
    }

    private fun section(title: String, text: String, colors: ProfileThemeColors?) = ui.column().apply {
        background = ui.shape(colors?.sectionOverlay ?: style.panel, 4)
        pad(10, 10)
        addLine(ui.label(title, 12, colors?.textDefault ?: style.text, true))
        addLine(ui.label(text, 13, colors?.textMuted ?: style.muted), 5)
    }
}
