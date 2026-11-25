package com.discord.widgets.settings

import android.view.View
import androidx.core.view.ViewCompat
import com.discord.R
import com.discord.app.AppFragment
import com.discord.databinding.WidgetSettingsAccessibilityBinding
import com.discord.widgets.debugging.WidgetDebugging
import com.discord.models.domain.ModelUserSettings
import com.discord.stores.StoreStream
import com.discord.utilities.rx.ui
import com.discord.utilities.feature.FeatureFlags
import com.discord.views.CheckedSetting
import com.discord.views.RadioManager
import rx.Observable
import rx.functions.Action1

/**
 * "Accessibility" user settings.
 *
 * Reference: `com.discord.widgets.settings.WidgetSettingsAccessibility`.
 *
 * The screen has one non-obvious rule: reduced motion **overrides** the animated-emoji, autoplay-GIF
 * and sticker-animation settings without changing their stored values. While it is on, those rows
 * render as off/animate-on-interaction, are made non-interactive with an explanation toast
 * (`accessibility_reduced_motion_settings_override` / `stickers_auto_play_help_disabled`) and the
 * stored value is left alone, so turning reduced motion back off restores the user's own choices.
 *
 * Every render therefore comes from [Model], which observes the *raw* stored settings
 * (`useReducedMotion = false`) plus the reduced-motion flag. A sticker radio tap only writes to
 * `StoreUserSettings`; the re-render arrives from the store, so the tap cannot desynchronise the
 * override state.
 */
class WidgetSettingsAccessibility : AppFragment(R.layout.widget_settings_accessibility) {

    /** Reference `WidgetSettingsAccessibility.Model`. */
    data class Model(
        val reducedMotionEnabled: Boolean,
        val displayNameStylesEnabled: Boolean,
        val allowAnimatedEmoji: Boolean,
        val autoPlayGifs: Boolean,
        val currentStickerAnimationSettings: Int,
    ) {
        companion object {
            fun get(): Observable<Model> {
                val settings = StoreStream.getUserSettings()
                return Observable.combineLatest(
                    StoreStream.getAccessibility().observeReducedMotionEnabled(),
                    StoreStream.getAccessibility().observeDisplayNameStylesEnabled(),
                    settings.observeIsAnimatedEmojisEnabled(false),
                    settings.observeIsAutoPlayGifsEnabled(false),
                    settings.observeStickerAnimationSettings(false),
                ) { reducedMotion, displayNameStyles, animatedEmoji, autoPlayGifs, stickerAnimation ->
                    Model(reducedMotion, displayNameStyles, animatedEmoji, autoPlayGifs, stickerAnimation)
                }
            }
        }
    }

    private var stickersAnimationRadioManager: RadioManager? = null
    private var _binding: WidgetSettingsAccessibilityBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onViewBound(view: View) {
        super.onViewBound(view)
        _binding = WidgetSettingsAccessibilityBinding.bind(view)
        binding.settingsAccessibilityDebug.setOnClickListener { WidgetDebugging.launch(requireContext()) }
    }

    /** Collectibles keep their [FeatureFlags] storage; only where they are surfaced has moved. */
    private fun bindCollectible(view: CheckedSetting, get: () -> Boolean, set: (Boolean) -> Unit) {
        view.apply {
            setChecked(get(), false)
            setOnCheckedListener(Action1 { set(it) })
        }
    }

    override fun onViewBoundOrOnResume() {
        super.onViewBoundOrOnResume()
        setActionBarTitle(R.string.accessibility)
        setActionBarSubtitle(R.string.user_settings)
        setActionBarDisplayHomeAsUpEnabled()
        SettingsNavigation.bindBack(this)

        if (_binding == null) return
        val accessibility = StoreStream.getAccessibility()
        val settings = StoreStream.getUserSettings()

        binding.settingsAccessibilityReducedMotionSwitch
            .setOnCheckedListener(Action1 { accessibility.setReducedMotionEnabled(it) })
        binding.settingsAccessibilityDisplayNameStylesSwitch
            .setOnCheckedListener(Action1 { accessibility.setDisplayNameStylesEnabled(it) })
        bindCollectible(binding.settingsFeatureAvatarDecorations, FeatureFlags::avatarDecorationsEnabled, FeatureFlags::setAvatarDecorationsEnabled)
        bindCollectible(binding.settingsFeatureNameplates, FeatureFlags::nameplatesEnabled, FeatureFlags::setNameplatesEnabled)
        bindCollectible(binding.settingsFeatureProfileEffects, FeatureFlags::profileEffectsEnabled, FeatureFlags::setProfileEffectsEnabled)
        bindCollectible(binding.settingsFeatureProfileFrames, FeatureFlags::profileFramesEnabled, FeatureFlags::setProfileFramesEnabled)
        binding.settingsAccessibilityReducedMotionDescription.apply {
            text = SettingsStrings.render(
                context,
                R.string.accessibility_prefers_reduced_motion_description,
                "helpdeskArticle" to REDUCED_MOTION_HELP_URL,
            )
            movementMethod = android.text.method.LinkMovementMethod.getInstance()
        }
        binding.settingsAccessibilityAllowAnimateEmojiSwitch
            .setOnCheckedListener(Action1 { settings.setIsAnimatedEmojisEnabled(it) })
        binding.settingsAccessibilityAllowAutoplayGifSwitch
            .setOnCheckedListener(Action1 { settings.setIsAutoPlayGifsEnabled(it) })

        stickersAnimationRadioManager = RadioManager(stickerRadios())

        listOf(
            binding.settingsAccessibilityReducedMotionHeader,
            binding.settingsAccessibilityImagesHeader,
            binding.settingsAccessibilityStickersHeader,
        ).forEach { ViewCompat.setAccessibilityHeading(it, true) }

        Model.get().ui(this).subscribe(::configureUI)
    }

    override fun onDestroyView() {
        stickersAnimationRadioManager = null
        _binding = null
        super.onDestroyView()
    }

    private fun configureUI(model: Model) {
        if (_binding == null) return
        val emoji = binding.settingsAccessibilityAllowAnimateEmojiSwitch
        val gifs = binding.settingsAccessibilityAllowAutoplayGifSwitch
        val radios = stickerRadios()

        binding.settingsAccessibilityReducedMotionSwitch
            .setChecked(model.reducedMotionEnabled, false)
        binding.settingsAccessibilityDisplayNameStylesSwitch
            .setChecked(model.displayNameStylesEnabled, false)

        if (model.reducedMotionEnabled) {
            emoji.setDisabledTooltip(R.string.accessibility_reduced_motion_settings_override)
            emoji.setChecked(false, false)
            radios.forEach { it.setDisabledTooltip(R.string.stickers_auto_play_help_disabled) }
            stickersAnimationRadioManager?.setChecked(
                if (model.currentStickerAnimationSettings != ModelUserSettings.STICKER_ANIMATION_NEVER_ANIMATE) {
                    binding.stickersAnimateOnInteraction
                } else {
                    binding.stickersNeverAnimate
                },
            )
            gifs.setChecked(false, false)
            gifs.setDisabledTooltip(R.string.accessibility_reduced_motion_settings_override)
        } else {
            emoji.setChecked(model.allowAnimatedEmoji, false)
            configureStickerAnimationRadio(
                model.currentStickerAnimationSettings,
                binding.stickersAlwaysAnimate,
                ModelUserSettings.STICKER_ANIMATION_ALWAYS_ANIMATE,
            )
            configureStickerAnimationRadio(
                model.currentStickerAnimationSettings,
                binding.stickersAnimateOnInteraction,
                ModelUserSettings.STICKER_ANIMATION_ANIMATE_ON_INTERACTION,
            )
            configureStickerAnimationRadio(
                model.currentStickerAnimationSettings,
                binding.stickersNeverAnimate,
                ModelUserSettings.STICKER_ANIMATION_NEVER_ANIMATE,
            )
            gifs.setChecked(model.autoPlayGifs, false)
            emoji.setContainerClickListener { emoji.setChecked(!emoji.isChecked, true) }
            gifs.setContainerClickListener { gifs.setChecked(!gifs.isChecked, true) }
        }
    }

    private fun configureStickerAnimationRadio(
        currentStickerAnimationSettings: Int,
        radio: CheckedSetting,
        stickerAnimationSetting: Int,
    ) {
        radio.setContainerClickListener {
            StoreStream.getUserSettings().setStickerAnimationSettings(stickerAnimationSetting)
        }
        if (currentStickerAnimationSettings == stickerAnimationSetting) {
            stickersAnimationRadioManager?.setChecked(radio)
        }
    }

    private fun stickerRadios(): List<CheckedSetting> = listOf(
        binding.stickersAlwaysAnimate,
        binding.stickersAnimateOnInteraction,
        binding.stickersNeverAnimate,
    )

    private companion object {
        const val REDUCED_MOTION_HELP_URL =
            "https://support.discord.com/hc/en-us/articles/360040613412"
    }
}
