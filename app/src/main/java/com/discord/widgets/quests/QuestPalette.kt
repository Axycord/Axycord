package com.discord.widgets.quests

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.widget.Button
import android.view.LayoutInflater
import com.discord.R
import com.discord.R as HostR
import com.discord.utilities.theme.ThemeRuntime

internal class QuestPalette(context: Context) {
    private val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    private val text = context.themeColor(HostR.attr.colorTextNormal)
    private val base = questThemeBase(
        runCatching { ThemeRuntime.activeBase }.getOrNull(),
        text,
        night,
    )
    private val page = compositeColor(
        context.themeColor(HostR.attr.colorBackgroundMobilePrimary)
            ?: context.themeColor(HostR.attr.colorBackgroundPrimary),
        base,
    )

    val surface = compositeColor(
        context.themeColor(HostR.attr.colorBackgroundMobileSecondary)
            ?: context.themeColor(HostR.attr.colorBackgroundSecondary),
        page,
        blend(page, readableColor(page), 0.04),
    )

    val ink = accessibleColor(text, surface)
    val muted = accessibleColor(context.themeColor(HostR.attr.colorTextMuted), surface, blend(surface, ink, 0.76))
    val pageInk = accessibleColor(text, page)
    val border = blend(surface, ink, 0.18)
    val progress = accessibleColor(context.themeColor(HostR.attr.colorTextLink), surface)
    val regular = context.questFont(HostR.font.whitney_medium, Typeface.NORMAL)
    val bold = context.questFont(HostR.font.whitney_bold, Typeface.BOLD)

    fun button(context: Context, label: String, primary: Boolean = false): Button =
        (LayoutInflater.from(context).inflate(R.layout.view_axycord_feature_button, null) as com.google.android.material.button.MaterialButton).apply {
        text = label
        isAllCaps = false
        typeface = bold
        setTextColor(if (primary) Color.WHITE else ink)
        minimumHeight = dp(48)
        minHeight = dp(48)
        minimumWidth = 0
        minWidth = 0
        setPadding(dp(6), 0, dp(6), 0)
        stateListAnimator = null
        val fill = if (primary) 0xFF4752C4.toInt() else surface
        backgroundTintList = ColorStateList.valueOf(fill)
        strokeColor = ColorStateList.valueOf(border)
        strokeWidth = if (primary) 0 else dp(1)
        cornerRadius = dp(8)
    }
}

private fun Context.themeColor(attribute: Int): Int? {
    val value = TypedValue()
    if (!theme.resolveAttribute(attribute, value, true)) return null
    val color = when {
        value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT -> value.data
        value.resourceId != 0 -> runCatching { getColorStateList(value.resourceId).defaultColor }.getOrNull()
        else -> null
    }
    return color
}

private fun Context.questFont(id: Int, style: Int): Typeface =
    if (Build.VERSION.SDK_INT >= 26) runCatching { resources.getFont(id) }.getOrNull()
        ?: Typeface.create("sans-serif", style)
    else Typeface.create("sans-serif", style)
