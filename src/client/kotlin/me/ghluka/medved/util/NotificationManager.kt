package me.ghluka.medved.util

import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

object NotificationManager {

    private const val SLIDE_MS = 280L
    private const val W        = 160
    private const val MARGIN   = 8
    private const val GAP      = 4
    private const val PAD_X    = 8
    private const val PAD_Y    = 4

    private class Notif(
        val title: String,
        val message: String,
        val durationMs: Long,
    ) {
        val createdAt = System.currentTimeMillis()
        var currentY  = Float.NaN

        val hasMessage get() = message.isNotBlank()
        fun height(lh: Int) = if (hasMessage) lh * 2 + PAD_Y * 3 else lh + PAD_Y * 2

        val elapsed get() = System.currentTimeMillis() - createdAt
        val expired  get() = elapsed >= durationMs

        /** 0 = hidden (off-screen), 1 = fully visible. */
        val slideProgress: Float get() {
            val e = elapsed
            return when {
                e < SLIDE_MS -> {
                    val t = e.toFloat() / SLIDE_MS
                    1f - (1f - t) * (1f - t)   // ease-out (slide in)
                }
                e > durationMs - SLIDE_MS -> {
                    val t = (durationMs - e).coerceAtLeast(0L).toFloat() / SLIDE_MS
                    t * t                        // ease-in (slide out)
                }
                else -> 1f
            }
        }
    }

    private val queue = ArrayDeque<Notif>()

    @JvmStatic fun show(title: String, message: String = "", durationMs: Long = 3000L) {
        val d = durationMs.coerceAtLeast(SLIDE_MS * 2 + 200L)
        synchronized(queue) { queue.addLast(Notif(title, message, d)) }
    }

    @JvmStatic fun render(g: GuiGraphicsExtractor) {
        val mc   = Minecraft.getInstance()
        val sw   = mc.window.guiScaledWidth
        val sh   = mc.window.guiScaledHeight
        val font = Font.getFont()
        val lh   = font.lineHeight

        synchronized(queue) {
            queue.removeAll { it.expired }
            if (queue.isEmpty()) return

            val accentRgb = Colour.accent.liveColor(Colour.accent.value).argb and 0x00FFFFFF

            var bottomY = sh - MARGIN
            for (notif in queue.reversed()) {
                val h = notif.height(lh)
                val targetY = (bottomY - h).toFloat()
                bottomY -= h + GAP

                // Smooth Y interpolation for stacking animation
                if (notif.currentY.isNaN()) notif.currentY = targetY
                else notif.currentY += (targetY - notif.currentY) * 0.3f

                val eased = notif.slideProgress
                // Slide in from the right: when eased=0 x=sw (off screen), when eased=1 x=sw-MARGIN-W
                val x = (sw - (MARGIN + W) * eased).toInt()
                val y = notif.currentY.toInt()

                val bgA = (200 * eased).toInt().coerceIn(0, 255)
                val fgA = (255 * eased).toInt().coerceIn(0, 255)

                val titleH = lh + PAD_Y * 2

                g.roundedFill(x, y, W, h, 4, (bgA shl 24) or 0x00101420)

                if (notif.hasMessage) {
                    g.roundedFill(x, y, W, titleH, 4, (bgA shl 24) or 0x00080B14, CORNERS_TOP)
                }

                g.roundedFill(x, y, 3, h, 3, (fgA shl 24) or accentRgb, CORNERS_LEFT)

                g.Text(font, Font.styledText(notif.title), x + PAD_X, y + PAD_Y, (fgA shl 24) or 0x00D7D7E4)

                if (notif.hasMessage) {
                    g.Text(font, Font.styledText(notif.message), x + PAD_X, y + titleH + 1, (fgA shl 24) or 0x00767688)
                }
            }
        }
    }
}
