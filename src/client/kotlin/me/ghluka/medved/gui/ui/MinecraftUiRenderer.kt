package me.ghluka.medved.gui.ui

import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.util.Text
import me.ghluka.medved.util.roundedFill
import com.mojang.blaze3d.platform.NativeImage
import net.minecraft.client.Minecraft
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import kotlin.math.round
import kotlin.math.roundToInt

class MinecraftUiRenderer(private val g: GuiGraphicsExtractor) : UiRenderer {
    private val font get() = Font.getFont()
    private val vanillaFont get() = Minecraft.getInstance().font

    override val fontHeight: Float get() = font.lineHeight.toFloat()
    override fun fontHeight(font: String?): Float =
        if (font == "minecraft") vanillaFont.lineHeight.toFloat() else this.font.lineHeight.toFloat()

    override fun textWidth(text: String): Float =
        font.width(Font.styledText(text)).toFloat()

    override fun textWidth(text: String, font: String?): Float =
        if (font == "minecraft") vanillaFont.width(text).toFloat() else textWidth(text)

    override fun fill(rect: UiRect, color: Int, radius: Float) {
        roundedFill(rect, color, radius)
    }

    override fun roundedFill(rect: UiRect, color: Int, radius: Float, corners: Int) {
        val x = rect.x.roundToInt()
        val y = rect.y.roundToInt()
        val w = rect.width.roundToInt()
        val h = rect.height.roundToInt()
        if (radius > 0f) RoundedRectTextureCache.draw(g, x, y, w, h, radius.roundToInt(), color, corners)
        else g.fill(x, y, x + w, y + h, color)
    }

    override fun border(rect: UiRect, color: Int, width: Float, radius: Float) {
        val stroke = width.roundToInt().coerceAtLeast(1)
        repeat(stroke) { inset ->
            val x = rect.x.roundToInt() + inset
            val y = rect.y.roundToInt() + inset
            val w = rect.width.roundToInt() - inset * 2
            val h = rect.height.roundToInt() - inset * 2
            if (w <= 0 || h <= 0) return
            g.fill(x, y, x + w, y + 1, color)
            g.fill(x, y + h - 1, x + w, y + h, color)
            g.fill(x, y, x + 1, y + h, color)
            g.fill(x + w - 1, y, x + w, y + h, color)
        }
    }

    override fun text(text: String, x: Float, y: Float, color: Int) {
        g.Text(font, Font.styledText(text), x.roundToInt(), y.roundToInt(), color)
    }

    override fun text(text: String, x: Float, y: Float, color: Int, font: String?) {
        if (font == "minecraft") {
            g.Text(vanillaFont, text, x.roundToInt(), y.roundToInt(), color)
        } else {
            text(text, x, y, color)
        }
    }

    override fun text(
        text: String,
        x: Float,
        y: Float,
        color: Int,
        font: String?,
        shadow: Boolean,
        scale: Float,
    ) {
        g.pose().pushMatrix()
        g.pose().translate(x, y)
        if (scale != 1f) g.pose().scale(scale, scale)
        if (font == "minecraft") {
            g.Text(vanillaFont, text, 0, 0, color, shadow)
        } else {
            val styledText = Font.withRenderScale(scale) { Font.styledText(text) }
            g.Text(this.font, styledText, 0, 0, color, shadow)
        }
        g.pose().popMatrix()
    }

    override fun item(name: String, rect: UiRect) {
        val item = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace(name)) ?: return
        g.item(item.defaultInstance, rect.x.roundToInt(), rect.y.roundToInt())
    }

    override fun colorMap(rect: UiRect, hue: Float) {
        ColorMapTextureCache.draw(g, rect, hue)
    }

    override fun clip(rect: UiRect) {
        g.enableScissor(
            rect.x.roundToInt(),
            rect.y.roundToInt(),
            rect.right.roundToInt(),
            rect.bottom.roundToInt(),
        )
    }

    override fun unclip() {
        g.disableScissor()
    }
}

private object RoundedRectTextureCache {
    private const val MAX_TEXTURES = 192
    private val cache = object : LinkedHashMap<Key, Entry>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean {
            val remove = size > MAX_TEXTURES
            if (remove) eldest.value.texture.close()
            return remove
        }
    }
    private var nextId = 0

    fun draw(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, radius: Int, color: Int, corners: Int) {
        if (w <= 0 || h <= 0) return
        val scale = Minecraft.getInstance().window.guiScale.toFloat().coerceAtLeast(1f)
        val key = Key(w, h, radius.coerceAtLeast(0), color, corners, (scale * 100f).roundToInt())
        val entry = cache.getOrPut(key) { create(key, scale) }
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            entry.id,
            x,
            y,
            0f,
            0f,
            w,
            h,
            entry.width,
            entry.height,
            entry.width,
            entry.height,
        )
    }

    private fun create(key: Key, scale: Float): Entry {
        val textureW = (key.w * scale).roundToInt().coerceAtLeast(1)
        val textureH = (key.h * scale).roundToInt().coerceAtLeast(1)
        val image = NativeImage(textureW, textureH, false)
        val rgb = key.color and 0x00FFFFFF
        val baseA = (key.color ushr 24) and 0xFF
        val samples = 3
        val sampleStep = 1f / samples
        for (py in 0 until textureH) {
            for (px in 0 until textureW) {
                var coverage = 0
                for (sy in 0 until samples) {
                    val gy = (py + (sy + 0.5f) * sampleStep) / scale
                    for (sx in 0 until samples) {
                        val gx = (px + (sx + 0.5f) * sampleStep) / scale
                        if (insideRoundedRect(gx, gy, key.w.toFloat(), key.h.toFloat(), key.radius.toFloat(), key.corners)) {
                            coverage++
                        }
                    }
                }
                val alpha = (baseA * coverage / (samples * samples)).coerceIn(0, 255)
                image.setPixelABGR(px, py, argbToAbgr((alpha shl 24) or rgb))
            }
        }

        val id = Identifier.fromNamespaceAndPath("medved", "ui/rounded_rect_${nextId++}")
        val texture = DynamicTexture({ "Medved rounded rect" }, image)
        Minecraft.getInstance().textureManager.register(id, texture)
        return Entry(id, texture, textureW, textureH)
    }

    private fun insideRoundedRect(x: Float, y: Float, w: Float, h: Float, radius: Float, corners: Int): Boolean {
        val r = radius.coerceAtMost(minOf(w, h)).coerceAtLeast(0f)
        if (r <= 0f) return x >= 0f && y >= 0f && x < w && y < h
        return when {
            x < r && y < r && corners and 1 != 0 -> insideCorner(x, y, r, r, r)
            x >= w - r && y < r && corners and 2 != 0 -> insideCorner(x, y, w - r, r, r)
            x < r && y >= h - r && corners and 4 != 0 -> insideCorner(x, y, r, h - r, r)
            x >= w - r && y >= h - r && corners and 8 != 0 -> insideCorner(x, y, w - r, h - r, r)
            else -> x >= 0f && y >= 0f && x < w && y < h
        }
    }

    private fun insideCorner(x: Float, y: Float, centerX: Float, centerY: Float, radius: Float): Boolean {
        val dx = x - centerX
        val dy = y - centerY
        return dx * dx + dy * dy <= radius * radius
    }

    private fun argbToAbgr(argb: Int): Int {
        val a = argb and 0xFF000000.toInt()
        val r = (argb ushr 16) and 0xFF
        val g = argb and 0x0000FF00
        val b = (argb and 0xFF) shl 16
        return a or b or g or r
    }

    private data class Key(
        val w: Int,
        val h: Int,
        val radius: Int,
        val color: Int,
        val corners: Int,
        val scaleKey: Int,
    )

    private data class Entry(
        val id: Identifier,
        val texture: DynamicTexture,
        val width: Int,
        val height: Int,
    )
}

private object ColorMapTextureCache {
    private const val TEX_W = 128
    private const val TEX_H = 64
    private val id = Identifier.fromNamespaceAndPath("medved", "ui/color_map")
    private var texture: DynamicTexture? = null
    private var hueKey = Int.MIN_VALUE

    fun draw(g: GuiGraphicsExtractor, rect: UiRect, hue: Float) {
        ensureTexture(hue)
        g.blit(
            RenderPipelines.GUI_TEXTURED,
            id,
            rect.x.roundToInt(),
            rect.y.roundToInt(),
            0f,
            0f,
            rect.width.roundToInt().coerceAtLeast(1),
            rect.height.roundToInt().coerceAtLeast(1),
            TEX_W,
            TEX_H,
            TEX_W,
            TEX_H,
        )
    }

    private fun ensureTexture(hue: Float) {
        val nextHueKey = round(hue.coerceIn(0f, 360f) * 10f).toInt()
        val existing = texture
        if (existing != null && nextHueKey == hueKey) return

        val image = existing?.getPixels() ?: NativeImage(TEX_W, TEX_H, false)
        for (y in 0 until TEX_H) {
            val value = 1f - y.toFloat() / (TEX_H - 1)
            for (x in 0 until TEX_W) {
                val saturation = x.toFloat() / (TEX_W - 1)
                image.setPixelABGR(x, y, hsvToAbgr(hue, saturation, value))
            }
        }

        if (existing == null) {
            texture = DynamicTexture({ "Medved color map" }, image).also {
                Minecraft.getInstance().textureManager.register(id, it)
            }
        } else {
            existing.upload()
        }
        hueKey = nextHueKey
    }

    private fun hsvToAbgr(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = value - c
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (b shl 16) or (g shl 8) or r
    }
}
