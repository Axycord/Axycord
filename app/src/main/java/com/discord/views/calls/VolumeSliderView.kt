package com.discord.views.calls

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.discord.R
import com.discord.databinding.VolumeSliderViewBinding
import kotlin.math.roundToInt

class VolumeSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val binding: VolumeSliderViewBinding
    private var trackingTouch = false

    var maxVolume: Int
        get() = binding.volumeSliderSeekBar.max
        set(value) { binding.volumeSliderSeekBar.max = value }

    init {
        binding = VolumeSliderViewBinding.inflate(LayoutInflater.from(context), this)
        if (attrs != null) {
            context.obtainStyledAttributes(attrs, R.styleable.VolumeSliderView).run {
                try {
                    binding.volumeSliderSeekBar.max = getInt(R.styleable.VolumeSliderView_vsv_max_volume, 100)
                    val tintResource = getResourceId(R.styleable.VolumeSliderView_vsv_icon_tint, -1)
                    if (tintResource != -1) {
                        val tint = ColorStateList.valueOf(ContextCompat.getColor(context, tintResource))
                        ImageViewCompat.setImageTintList(binding.volumeSliderMaxVol, tint)
                        ImageViewCompat.setImageTintList(binding.volumeSliderMinVol, tint)
                    }
                } finally {
                    recycle()
                }
            }
        }
    }

    /** Store-driven renders must not move the thumb out from under an active gesture. */
    fun setVolume(volume: Float) {
        if (!trackingTouch) binding.volumeSliderSeekBar.progress = volume.roundToInt().coerceIn(0, binding.volumeSliderSeekBar.max)
    }

    fun setOnVolumeChange(onVolumeChanged: (Float, Boolean) -> Unit) {
        binding.volumeSliderSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                onVolumeChanged(progress.toFloat(), fromUser)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) { trackingTouch = true }
            override fun onStopTrackingTouch(seekBar: SeekBar?) { trackingTouch = false }
        })
    }
}
