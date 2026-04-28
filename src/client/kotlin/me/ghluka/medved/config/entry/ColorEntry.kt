package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlin.math.abs

class ColorEntry(name: String, default: Color, val allowAlpha: Boolean = true) : ConfigEntry<Color>(name, default) {
    companion object {
        private val chromaStartNanos = System.nanoTime()

        @JvmStatic
        fun chromaTimeSeconds(): Float {
            val elapsed = System.nanoTime() - chromaStartNanos
            return (elapsed / 1_000_000_000.0).toFloat()
        }
    }

    enum class PickerMode { CUSTOM, THEME, CHROMA }

    var pickerMode: PickerMode = PickerMode.CUSTOM
    var customValue: Color = default
    var chromaSpeed: Float = 0.5f
    var chromaSaturation: Float = 1f
    var chromaBrightness: Float = 1f

    override fun toJson(): JsonElement {
        val mode = if (!allowThemeMode() && pickerMode == PickerMode.THEME) PickerMode.CUSTOM else pickerMode
        return JsonObject().apply {
            addProperty("value", value.toHex())
            addProperty("mode", mode.name)
            addProperty("custom", customValue.toHex())
            addProperty("chromaSpeed", chromaSpeed)
            addProperty("chromaSaturation", chromaSaturation)
            addProperty("chromaBrightness", chromaBrightness)
        }
    }

    override fun fromJson(element: JsonElement) {
        if (element.isJsonPrimitive) {
            val loaded = Color.fromHex(element.asString)
            value = loaded
            customValue = loaded
            pickerMode = PickerMode.CUSTOM
            return
        }

        if (!element.isJsonObject) return
        val obj = element.asJsonObject
        val loadedValue = obj.getStringOrNull("value")?.let(Color::fromHex)
            ?: obj.getStringOrNull("custom")?.let(Color::fromHex)
            ?: value
        val loadedCustom = obj.getStringOrNull("custom")?.let(Color::fromHex) ?: loadedValue
        value = loadedValue
        customValue = loadedCustom

        pickerMode = obj.getStringOrNull("mode")
            ?.let { runCatching { PickerMode.valueOf(it) }.getOrNull() }
            ?.let { if (!allowThemeMode() && it == PickerMode.THEME) PickerMode.CUSTOM else it }
            ?: PickerMode.CUSTOM

        chromaSpeed = obj.getFloatOrNull("chromaSpeed")?.coerceIn(0.05f, 8f) ?: chromaSpeed
        chromaSaturation = obj.getFloatOrNull("chromaSaturation")?.coerceIn(0f, 1f) ?: chromaSaturation
        chromaBrightness = obj.getFloatOrNull("chromaBrightness")?.coerceIn(0f, 1f) ?: chromaBrightness
    }

    fun applyDynamicColor(themeColor: Color, timeSeconds: Float, allowThemeMode: Boolean) {
        val mode = if (!allowThemeMode && pickerMode == PickerMode.THEME) PickerMode.CUSTOM else pickerMode
        when (mode) {
            PickerMode.CUSTOM -> {
                value = customValue
            }
            PickerMode.THEME -> {
                value = themeColor.copy(a = customValue.a)
            }
            PickerMode.CHROMA -> {
                val hue = ((timeSeconds * chromaSpeed * 360f) % 360f + 360f) % 360f
                val rgb = hsvToRgb(hue, chromaSaturation, chromaBrightness)
                value = rgb.copy(a = customValue.a)
            }
        }
    }

    fun liveColor(themeColor: Color? = null, timeSeconds: Float = chromaTimeSeconds()): Color {
        return when (pickerMode) {
            PickerMode.CUSTOM -> customValue
            PickerMode.THEME -> themeColor?.copy(a = customValue.a) ?: customValue
            PickerMode.CHROMA -> {
                val hue = ((timeSeconds * chromaSpeed * 360f) % 360f + 360f) % 360f
                hsvToRgb(hue, chromaSaturation, chromaBrightness).copy(a = customValue.a)
            }
        }
    }

    private fun allowThemeMode(): Boolean = name != "accent"

    private fun hsvToRgb(hue: Float, saturation: Float, brightness: Float): Color {
        val c = brightness * saturation
        val x = c * (1f - abs((hue / 60f) % 2f - 1f))
        val m = brightness - c
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return Color(
            ((r1 + m) * 255f).toInt().coerceIn(0, 255),
            ((g1 + m) * 255f).toInt().coerceIn(0, 255),
            ((b1 + m) * 255f).toInt().coerceIn(0, 255),
            255
        )
    }

    private fun JsonObject.getStringOrNull(key: String): String? =
        if (has(key) && get(key).isJsonPrimitive) get(key).asString else null

    private fun JsonObject.getFloatOrNull(key: String): Float? =
        if (has(key) && get(key).isJsonPrimitive) get(key).asFloat else null
}
