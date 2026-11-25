package com.discord.widgets.orbsshop

// Gradient themes can leave the page surface clear so the host wallpaper shows through.
internal fun shopSurfaceColor(page: Int, floating: Int, fallback: Int): Int = when {
    page ushr 24 == 255 -> page
    floating ushr 24 == 255 -> floating
    else -> fallback
}
