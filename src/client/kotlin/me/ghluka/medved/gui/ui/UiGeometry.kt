package me.ghluka.medved.gui.ui

data class UiRect(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val right: Float get() = x + width
    val bottom: Float get() = y + height

    fun contains(px: Float, py: Float): Boolean =
        px >= x && px < right && py >= y && py < bottom

    fun inset(insets: UiInsets): UiRect =
        UiRect(
            x + insets.left,
            y + insets.top,
            (width - insets.left - insets.right).coerceAtLeast(0f),
            (height - insets.top - insets.bottom).coerceAtLeast(0f),
        )
}

data class UiInsets(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 0f,
    val bottom: Float = 0f,
) {
    companion object {
        val Zero = UiInsets()

        fun all(value: Float) = UiInsets(value, value, value, value)

        fun xy(x: Float, y: Float) = UiInsets(x, y, x, y)
    }
}

enum class UiAxis {
    VERTICAL,
    HORIZONTAL,
}

enum class UiTextAlign {
    LEFT,
    CENTER,
    RIGHT,
}
