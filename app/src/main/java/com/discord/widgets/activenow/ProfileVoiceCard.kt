package com.discord.widgets.activenow

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import com.discord.utilities.feature.FeatureFlags
import rx.Observable
import android.view.Gravity
import android.view.ViewGroup
import android.view.LayoutInflater
import com.google.android.material.button.MaterialButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.discord.R
import com.discord.utilities.rx.ui
import com.discord.widgets.user.usersheet.WidgetUserSheet

import rx.Subscription

internal class ProfileVoiceCard(context: Context) : LinearLayout(context) {
    private var profile: ProfileVoiceContext? = null
    private var location: VoiceLocation? = null
    private var subscription: Subscription? = null
    private val frame = GradientDrawable().apply { cornerRadius = context.dp(12).toFloat() }
    private val iconBackground = GradientDrawable().apply { cornerRadius = context.dp(9).toFloat() }
    private val icon = ImageView(context).apply {
        setImageResource(R.drawable.ic_channel_voice_16dp)
        scaleType = ImageView.ScaleType.CENTER
        background = iconBackground
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private val label = TextView(context).apply {
        text = "In voice"
        textSize = 12f
        typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
    }
    private val channel = TextView(context).apply {
        textSize = 16f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
    }
    private val server = TextView(context).apply {
        textSize = 13f
        minHeight = context.dp(40)
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
        compoundDrawablePadding = context.dp(6)
        setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_right_grey_12dp, 0)
        setOnClickListener {
            if (!FeatureFlags.activeNowEnabled()) return@setOnClickListener
            val guildId = location?.guildId ?: return@setOnClickListener
            owner()?.let { openVoiceServer(it, guildId) }
        }
    }
    private val voiceInfo = LinearLayout(context).apply {
        orientation = VERTICAL
        ViewCompat.setScreenReaderFocusable(this, true)
        label.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        channel.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(label)
        addView(channel)
    }
    private val joinButton = (LayoutInflater.from(context).inflate(
        R.layout.view_axycord_feature_button, this, false) as MaterialButton).apply {
        text = "Join VC"
        isAllCaps = false
        textSize = 15f
        minimumHeight = context.dp(48)
        typeface = context.discordTypeface(R.attr.font_primary_semibold, R.font.whitney_semibold)
        setOnClickListener { joinCurrentVoice() }
    }

    init {
        visibility = GONE
        orientation = VERTICAL
        background = frame
        val padding = context.dp(12)
        setPadding(padding, padding, padding, padding)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(16)
            marginEnd = context.dp(16)
            topMargin = context.dp(12)
            bottomMargin = context.dp(4)
        }
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        addView(LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(icon, LayoutParams(context.dp(36), context.dp(36)).apply { marginEnd = context.dp(12) })
            addView(voiceInfo, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(server, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = context.dp(48)
        })
        addView(joinButton, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = context.dp(8)
        })
    }

    fun bind(profile: ProfileVoiceContext) {
        val previous = this.profile
        if (previous == profile) return
        this.profile = profile
        frame.setColor(profile.surfaceColor)
        frame.setStroke(context.dp(1), profile.borderColor)
        iconBackground.setColor(ColorUtils.setAlphaComponent(profile.primaryTextColor, 25))
        icon.imageTintList = ColorStateList.valueOf(profile.primaryTextColor)
        label.setTextColor(profile.secondaryTextColor)
        channel.setTextColor(profile.primaryTextColor)
        server.setTextColor(profile.primaryTextColor)
        server.compoundDrawableTintList = ColorStateList.valueOf(profile.secondaryTextColor)
        val fill = ColorUtils.setAlphaComponent(profile.interactiveColor, 255)
        joinButton.backgroundTintList = ColorStateList.valueOf(fill)
        joinButton.setTextColor(if (ColorUtils.calculateContrast(Color.WHITE, fill) >= 4.5) Color.WHITE else Color.BLACK)
        joinButton.rippleColor = ColorStateList.valueOf(ColorUtils.setAlphaComponent(profile.primaryTextColor, 46))

        if (previous == null || previous.userId != profile.userId || previous.guildId != profile.guildId) {
            observeBoundProfile()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        observeBoundProfile()
    }

    override fun onDetachedFromWindow() {
        disconnect()
        super.onDetachedFromWindow()
    }

    fun dispose() {
        profile = null
        disconnect()
        render(null)
        (parent as? ViewGroup)?.removeView(this)
    }

    private fun observeBoundProfile() {
        disconnect()
        render(null)
        val profile = profile ?: return
        if (!isAttachedToWindow) return
        subscription = FeatureFlags.observeActiveNowEnabled()
            .switchMap { enabled -> if (enabled) observeProfileVoice(profile.userId, profile.guildId)
                else Observable.just<VoiceLocation?>(null) }
            .ui()
            .subscribe(::render) {
                render(null)
            }
    }

    private fun disconnect() {
        subscription?.unsubscribe()
        subscription = null
    }

    private fun render(location: VoiceLocation?) {
        this.location = location
        if (location == null) {
            visibility = GONE
            return
        }
        val name = location.channelName ?: location.fallbackName
        channel.text = name
        server.text = location.serverName
        server.contentDescription = location.serverName?.let { "Open server $it" }
        server.visibility = if (location.guildId != 0L && location.serverName != null) VISIBLE else GONE
        joinButton.visibility = if (location.canJoin) VISIBLE else GONE
        voiceInfo.contentDescription = "${label.text}. $name"
        visibility = VISIBLE
    }

    private fun owner(): WidgetUserSheet? =
        (runCatching { FragmentManager.findFragment<Fragment>(this) }.getOrNull() as? WidgetUserSheet)
            ?.takeIf { it.isAdded && !it.parentFragmentManager.isStateSaved }

    private fun joinCurrentVoice() {
        if (!FeatureFlags.activeNowEnabled()) return
        val target = location?.takeIf { it.canJoin } ?: return
        val boundProfile = profile ?: return
        val sheet = owner() ?: return
        val directory = VoiceDirectory()
        joinProfileVoice(
            sheet,
            readChannel = {
                val currentProfile = profile
                if (FeatureFlags.activeNowEnabled() && isAttachedToWindow && sheet.isAdded && currentProfile?.userId == boundProfile.userId &&
                    currentProfile.guildId == boundProfile.guildId
                ) {
                    directory.profileLocation(boundProfile.userId, boundProfile.guildId)
                        ?.takeIf { it.channelId == target.channelId && it.guildId == target.guildId && it.canJoin }
                        ?.let { directory.channels.getChannel(it.channelId) }
                        ?.takeIf { it.guildId == target.guildId }
                } else {
                    null
                }
            },
            onJoining = { joining ->
                joinButton.isEnabled = !joining
                joinButton.alpha = if (joining) 0.65f else 1f
            },
        )
    }
}

internal data class ProfileVoiceContext(
    val userId: Long,
    val guildId: Long?,
    val surfaceColor: Int,
    val borderColor: Int,
    val primaryTextColor: Int,
    val secondaryTextColor: Int,
    val interactiveColor: Int,
)
