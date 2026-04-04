package me.ghluka.medved.gui

import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

class HudEditorScreen(
    private val module: HudModule,
    private val returnScreen: Screen,
) : Screen(Component.literal("HUD Editor")) {

    companion object {
        private val GRID_FRACTIONS = floatArrayOf(1f / 4f, 1f / 3f, 1f / 2f, 2f / 3f, 3f / 4f)
        private const val SNAP_PX = 7
        private const val HANDLE_SIZE = 10

        private const val BTN_W = 64
        private const val BTN_H = 16
        private const val BTN_PAD = 10
        private const val BTN_GAP = 6
    }

    private var dragging = false
    private var dragOffX = 0
    private var dragOffY = 0

    private var isDraggingHandle = false
    private var handleBaseW = 0
    private var handleElemX = 0

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private val accentRaw get() = Colour.accent.value
    private val ACCENT get() = accentRaw.argb

    override fun isPauseScreen() = false

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        g.fill(0, 0, width, height, argb(210, 0, 0, 0))

        drawGridLines(g)

        val sc = module.hudScale.value
        val px = (module.hudX.value * width).toInt()
        val py = (module.hudY.value * height).toInt()
        val ew = (module.hudWidth() * sc).toInt().coerceAtLeast(4)
        val eh = (module.hudHeight() * sc).toInt().coerceAtLeast(4)

        g.pose().pushMatrix()
        g.pose().translate(px.toFloat(), py.toFloat())
        if (sc != 1.0f) g.pose().scale(sc, sc)
        module.renderHudElement(g)
        g.pose().popMatrix()

        g.outline(px, py, ew, eh, ACCENT)

        val hx = px + ew
        val hy = py + eh
        val hoverHandle = mx in hx until hx + HANDLE_SIZE && my in hy until hy + HANDLE_SIZE
        g.fill(hx, hy, hx + HANDLE_SIZE, hy + HANDLE_SIZE,
            if (hoverHandle) argb(255, 0, 230, 0) else argb(200, 0, 160, 0))
        g.outline(hx, hy, HANDLE_SIZE, HANDLE_SIZE, argb(255, 0, 80, 0))

        drawButton(g, BTN_PAD, height - BTN_PAD - BTN_H, "Back", mx, my)
        drawButton(g, BTN_PAD + BTN_W + BTN_GAP, height - BTN_PAD - BTN_H, "Reset", mx, my)

        val guiFont = Font.getFont()
        val scaleStr = "Scale: ${"%.2f".format(sc)}"
        val scComp = Font.styledText(scaleStr)
        g.text(guiFont, scComp,
            width - guiFont.width(scComp) - BTN_PAD,
            height - BTN_PAD - BTN_H + (BTN_H - 8) / 2,
            argb(180, 180, 180, 180))

        val hint = Font.styledText("Drag element to move  •  Drag green square to scale  •  Scroll to fine-tune  •  ESC to go back")
        g.centeredText(guiFont, hint, width / 2, BTN_PAD, argb(140, 200, 200, 200))
    }

    private fun drawGridLines(g: GuiGraphicsExtractor) {
        val ac = accentRaw
        for (f in GRID_FRACTIONS) {
            val isCenter = f == 0.5f
            val col = if (isCenter) argb(130, ac.r, ac.g, ac.b) else argb(45, 200, 200, 200)
            val gx = (f * width).toInt()
            val gy = (f * height).toInt()
            g.fill(gx, 0, gx + 1, height, col)
            g.fill(0, gy, width, gy + 1, col)
        }
    }

    private fun drawButton(g: GuiGraphicsExtractor, x: Int, y: Int, label: String, mx: Int, my: Int) {
        val hov = mx in x until x + BTN_W && my in y until y + BTN_H
        val ac = accentRaw
        val bg = if (hov)
            argb(230, ac.r / 4, ac.g / 4, ac.b / 4)
        else
            argb(180, ac.r / 6, ac.g / 6, ac.b / 6)
        g.fill(x, y, x + BTN_W, y + BTN_H, bg)
        g.fill(x, y, x + 2, y + BTN_H, ACCENT)
        g.centeredText(Font.getFont(), Font.styledText(label),
            x + BTN_W / 2, y + (BTN_H - 8) / 2,
            argb(255, 215, 215, 228))
    }

    override fun mouseClicked(event: MouseButtonEvent, inBounds: Boolean): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()

        if (isOverBtn(BTN_PAD, height - BTN_PAD - BTN_H, mx, my)) {
            minecraft.setScreen(returnScreen)
            return true
        }
        if (isOverBtn(BTN_PAD + BTN_W + BTN_GAP, height - BTN_PAD - BTN_H, mx, my)) {
            module.hudX.reset()
            module.hudY.reset()
            module.hudScale.reset()
            return true
        }

        val sc = module.hudScale.value
        val px = (module.hudX.value * width).toInt()
        val py = (module.hudY.value * height).toInt()
        val ew = (module.hudWidth() * sc).toInt().coerceAtLeast(4)
        val eh = (module.hudHeight() * sc).toInt().coerceAtLeast(4)
        val hx = px + ew
        val hy = py + eh

        if (mx in hx until hx + HANDLE_SIZE && my in hy until hy + HANDLE_SIZE) {
            isDraggingHandle = true
            handleBaseW = module.hudWidth()
            handleElemX = px
            return true
        }

        dragging = true
        dragOffX = mx - px
        dragOffY = my - py
        return true
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val mx = event.x().toInt()
        val my = event.y().toInt()

        if (isDraggingHandle) {
            val targetW = (mx - handleElemX).coerceAtLeast(handleBaseW / 4)
            module.hudScale.value = (targetW.toFloat() / handleBaseW)
                .coerceIn(module.hudScale.min, module.hudScale.max)
            return true
        }

        if (dragging) {
            val rawX = mx - dragOffX
            val rawY = my - dragOffY
            module.hudX.value = (snapToGrid(rawX, width).toFloat() / width).coerceIn(0f, 1f)
            module.hudY.value = (snapToGrid(rawY, height).toFloat() / height).coerceIn(0f, 1f)
            return true
        }

        return false
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        dragging = false
        isDraggingHandle = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(x: Double, y: Double, deltaH: Double, deltaV: Double): Boolean {
        module.hudScale.value = (module.hudScale.value + deltaV.toFloat() * 0.1f)
            .coerceIn(module.hudScale.min, module.hudScale.max)
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            minecraft.setScreen(returnScreen)
            return true
        }
        return super.keyPressed(event)
    }

    private fun isOverBtn(bx: Int, by: Int, mx: Int, my: Int) =
        mx in bx until bx + BTN_W && my in by until by + BTN_H

    private fun snapToGrid(pos: Int, dimension: Int): Int {
        for (f in GRID_FRACTIONS) {
            val gridPx = (f * dimension).toInt()
            if (abs(pos - gridPx) < SNAP_PX) return gridPx
        }
        return pos
    }
}
