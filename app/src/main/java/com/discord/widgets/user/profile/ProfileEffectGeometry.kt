package com.discord.widgets.user.profile

internal data class ProfileEffectTransform(val scaleX: Float, val scaleY: Float, val x: Float, val y: Float)

/** Positioned layers use desktop points; full-canvas artwork keeps its own bitmap proportions. */
internal fun profileEffectTransform(
    viewWidth: Int, viewHeight: Int, imageWidth: Int, imageHeight: Int,
    layerWidth: Int?, layerHeight: Int?, x: Int, y: Int,
): ProfileEffectTransform? {
    if (viewWidth <= 0 || viewHeight <= 0 || imageWidth <= 0 || imageHeight <= 0) return null
    if (layerWidth != null && layerHeight != null && layerWidth > 0 && layerHeight > 0) {
        val scale = viewWidth / 304f
        return ProfileEffectTransform(layerWidth * scale / imageWidth, layerHeight * scale / imageHeight,
            x * scale, y * scale)
    }
    val scale = minOf(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
    return ProfileEffectTransform(scale, scale,
        (viewWidth - imageWidth * scale) / 2f, (viewHeight - imageHeight * scale) / 2f)
}
