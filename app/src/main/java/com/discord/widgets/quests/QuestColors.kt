package com.discord.widgets.quests

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal fun accessibleColor(preferred: Int?, background: Int, fallback: Int? = null): Int =
    preferred?.let { compositeColor(it, background) }?.takeIf { contrastRatio(it, background) >= 4.5 }
        ?: fallback?.let { compositeColor(it, background) }?.takeIf { contrastRatio(it, background) >= 4.5 }
        ?: readableColor(background)

// Axycord's selected theme may differ from Android's system light/dark setting.
internal fun questThemeBase(appBase: String?, textColor: Int?, systemNight: Boolean): Int {
    val dark = when {
        appBase != null -> appBase != "light"
        textColor != null && textColor ushr 24 != 0 -> luminance(textColor) > 0.5
        else -> systemNight
    }
    return if (dark) 0xFF202024.toInt() else 0xFFF5F6F8.toInt()
}

// Gradient themes supply translucent surfaces. Flatten them over the selected app base,
// keeping the plugin's surfaces readable even when Android itself is in light mode.
internal fun compositeColor(foreground: Int?, background: Int, missing: Int = background): Int =
    if (foreground == null) missing else blend(background, foreground, (foreground ushr 24) / 255.0)

internal fun readableColor(background: Int): Int =
    if (contrastRatio(0xFFFFFFFF.toInt(), background) >= contrastRatio(0xFF000000.toInt(), background)) 0xFFFFFFFF.toInt() else 0xFF000000.toInt()

internal fun contrastRatio(first: Int, second: Int): Double {
    val a = luminance(first)
    val b = luminance(second)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)
}

private fun luminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val srgb = ((color ushr shift) and 255) / 255.0
        return if (srgb <= 0.04045) srgb / 12.92 else ((srgb + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

internal fun blend(background: Int, foreground: Int, amount: Double): Int {
    fun channel(shift: Int): Int {
        val from = (background ushr shift) and 255
        val to = (foreground ushr shift) and 255
        return (from + (to - from) * amount).roundToInt()
    }
    return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
