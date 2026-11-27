package com.discord.widgets.user.usersheet

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import com.discord.databinding.UserProfileVoiceSettingsViewBinding

class UserProfileVoiceSettingsView(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {
    data class ViewState(
        val isMuted: Boolean,
        val isDeafened: Boolean?,
        val outputVolume: Float?,
    )

    private val binding = UserProfileVoiceSettingsViewBinding.inflate(LayoutInflater.from(context), this, true)
    private var rendering = false

    init {
        binding.userSheetVolumeSlider.maxVolume = 200
    }

    fun setOnMuteChecked(callback: (Boolean) -> Unit) {
        binding.userSheetMuted.setOnCheckedChangeListener { _, checked -> if (!rendering) callback(checked) }
    }

    fun setOnDeafenChecked(callback: (Boolean) -> Unit) {
        binding.userSheetDeafen.setOnCheckedChangeListener { _, checked -> if (!rendering) callback(checked) }
    }

    fun setOnVolumeChange(callback: (Float, Boolean) -> Unit) {
        binding.userSheetVolumeSlider.setOnVolumeChange(callback)
    }

    fun updateView(state: ViewState) {
        rendering = true
        try {
            val volumeVisible = state.outputVolume != null
            binding.userSheetVolumeLabel.visibility = if (volumeVisible) View.VISIBLE else View.GONE
            binding.userSheetVolumeSlider.visibility = if (volumeVisible) View.VISIBLE else View.GONE
            binding.userSheetMuted.isChecked = state.isMuted
            binding.userSheetDeafen.visibility = if (state.isDeafened == null) View.GONE else View.VISIBLE
            binding.userSheetDeafen.isChecked = state.isDeafened ?: false
            binding.userSheetVolumeSlider.setVolume(state.outputVolume ?: 0f)
        } finally {
            rendering = false
        }
    }
}
