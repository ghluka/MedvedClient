package me.ghluka.medved.gui.ui

data class UiStyle(
    val background: Int? = null,
    val foreground: Int? = 0xFFD7D7E4.toInt(),
    val hoverBackground: Int? = null,
    val activeBackground: Int? = null,
    val borderColor: Int? = null,
    val borderWidth: Float = 0f,
    val radius: Float = 0f,
    val padding: UiInsets = UiInsets.Zero,
    val gap: Float = 0f,
    val align: UiTextAlign = UiTextAlign.LEFT,
    val clip: Boolean = false,
) {
    fun merge(other: UiStyle): UiStyle =
        UiStyle(
            background = other.background ?: background,
            foreground = other.foreground ?: foreground,
            hoverBackground = other.hoverBackground ?: hoverBackground,
            activeBackground = other.activeBackground ?: activeBackground,
            borderColor = other.borderColor ?: borderColor,
            borderWidth = if (other.borderWidth != 0f) other.borderWidth else borderWidth,
            radius = if (other.radius != 0f) other.radius else radius,
            padding = if (other.padding != UiInsets.Zero) other.padding else padding,
            gap = if (other.gap != 0f) other.gap else gap,
            align = other.align,
            clip = other.clip || clip,
        )
}

object UiTheme {
    val BG = uncheckedColor(0x73090A12)
    val PANEL = uncheckedColor(0xF21A1B28)
    val SURFACE = uncheckedColor(0xFF202231)
    val SURFACE_HOVER = uncheckedColor(0xFF2B2E42)
    val TEXT = uncheckedColor(0xFFD7D7E4)
    val TEXT_DIM = uncheckedColor(0xFF76768C)
    val ACCENT = uncheckedColor(0xFF6FA8FF)

    val panel = UiStyle(
        background = PANEL,
        foreground = TEXT,
    )

    val row = UiStyle(
        foreground = TEXT_DIM,
    )

    val button = UiStyle(
        background = SURFACE,
        foreground = TEXT,
        hoverBackground = SURFACE_HOVER,
        activeBackground = ACCENT,
        align = UiTextAlign.CENTER,
    )
}

@Suppress("NOTHING_TO_INLINE")
inline fun uncheckedColor(value: Long): Int = value.toInt()
