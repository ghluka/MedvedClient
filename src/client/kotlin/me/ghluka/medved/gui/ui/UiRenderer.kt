package me.ghluka.medved.gui.ui

interface UiRenderer {
    val fontHeight: Float

    fun fontHeight(font: String?): Float = fontHeight
    fun textWidth(text: String): Float
    fun textWidth(text: String, font: String?): Float = textWidth(text)
    fun fill(rect: UiRect, color: Int, radius: Float = 0f)
    fun roundedFill(rect: UiRect, color: Int, radius: Float = 0f, corners: Int = 15) =
        fill(rect, color, radius)
    fun border(rect: UiRect, color: Int, width: Float, radius: Float = 0f)
    fun text(text: String, x: Float, y: Float, color: Int)
    fun text(text: String, x: Float, y: Float, color: Int, font: String?) = text(text, x, y, color)
    fun text(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        font: String?,
        shadow: Boolean,
        scale: Float,
    ) = text(text, x, y, color, font)
    fun item(name: String, rect: UiRect) {}
    fun colorMap(rect: UiRect, hue: Float) {}
    fun clip(rect: UiRect)
    fun unclip()
}
