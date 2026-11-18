package dev.microcord.questhome

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.widget.Button
import com.discord.R as HostR
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

internal class QuestPalette(context: Context) {
    private val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES

    private val page = context.themeColor(HostR.attr.colorBackgroundMobilePrimary)
        ?: context.themeColor(HostR.attr.colorBackgroundPrimary)
        ?: if (night) 0xFF202024.toInt() else 0xFFF5F6F8.toInt()

    val surface = context.themeColor(HostR.attr.colorBackgroundMobileSecondary)
        ?: context.themeColor(HostR.attr.colorBackgroundSecondary)
        ?: blend(page, readableColor(page), 0.04)

    val ink = accessibleColor(context.themeColor(HostR.attr.colorTextNormal), surface)
    val muted = accessibleColor(context.themeColor(HostR.attr.colorTextMuted), surface, blend(surface, ink, 0.76))
    val pageInk = accessibleColor(context.themeColor(HostR.attr.colorTextNormal), page)
    val border = blend(surface, ink, 0.18)
    val progress = accessibleColor(context.themeColor(HostR.attr.colorTextLink), surface)
    val regular = context.questFont(HostR.font.whitney_medium, Typeface.NORMAL)
    val bold = context.questFont(HostR.font.whitney_bold, Typeface.BOLD)

    fun button(context: Context, label: String, primary: Boolean = false): Button = Button(context).apply {
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
        backgroundTintList = null
        val fill = if (primary) 0xFF4752C4.toInt() else surface
        val base = GradientDrawable().apply {
            cornerRadius = dp(8).toFloat()
            setColor(fill)
            if (!primary) setStroke(dp(1), border)
        }
        val ripple = if (primary) 0x33FFFFFF else (ink and 0x00FFFFFF) or 0x26000000
        background = RippleDrawable(ColorStateList.valueOf(ripple), base, null)
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
    return color?.takeIf { it ushr 24 == 255 }
}

private fun Context.questFont(id: Int, style: Int): Typeface =
    if (Build.VERSION.SDK_INT >= 26) runCatching { resources.getFont(id) }.getOrNull()
        ?: Typeface.create("sans-serif", style)
    else Typeface.create("sans-serif", style)

internal fun accessibleColor(preferred: Int?, background: Int, fallback: Int? = null): Int =
    preferred?.takeIf { contrastRatio(it, background) >= 4.5 }
        ?: fallback?.takeIf { contrastRatio(it, background) >= 4.5 }
        ?: readableColor(background)

private fun readableColor(background: Int): Int =
    if (contrastRatio(Color.WHITE, background) >= contrastRatio(Color.BLACK, background)) Color.WHITE else Color.BLACK

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

private fun blend(background: Int, foreground: Int, amount: Double): Int {
    fun channel(shift: Int): Int {
        val from = (background ushr shift) and 255
        val to = (foreground ushr shift) and 255
        return (from + (to - from) * amount).roundToInt()
    }
    return (255 shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}
