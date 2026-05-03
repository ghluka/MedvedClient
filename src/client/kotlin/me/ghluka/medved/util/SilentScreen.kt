package me.ghluka.medved.util

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor
import net.minecraft.client.KeyMapping
import net.minecraft.client.Options
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent

class SilentScreen(val wrapped: Screen) : Screen(
    wrapped.getTitle()
) {
    override fun init() {
        this.wrapped.init(this.width, this.height)
    }

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
    }

    override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
    }

    override fun added() {
        wrapped.added()
        minecraft.execute {
            if (minecraft.screen === this) {
                minecraft.mouseHandler.grabMouse()
            }
        }
    }

    override fun removed() {
        wrapped.removed()
        resyncMovementKeys()
    }

    private fun resyncMovementKeys() {
        val opts: Options = minecraft.options
        val movementKeys = arrayOf<KeyMapping?>(
            opts.keyUp,
            opts.keyDown,
            opts.keyLeft,
            opts.keyRight,
            opts.keyJump,
            opts.keyShift,
            opts.keySprint
        )

        for (key in movementKeys) {
            KeyMapping.set(InputConstants.getKey(key!!.saveString()), key!!.isDown())
        }
    }

    override fun isPauseScreen(): Boolean {
        return wrapped.isPauseScreen()
    }

    override fun isInGameUi(): Boolean {
        return wrapped.isInGameUi()
    }

    override fun shouldCloseOnEsc(): Boolean {
        return wrapped.shouldCloseOnEsc()
    }

    override fun onClose() {
        wrapped.onClose()
    }

    override fun tick() {
        wrapped.tick()
    }

    override fun canInterruptWithAnotherScreen(): Boolean {
        return wrapped.canInterruptWithAnotherScreen()
    }

    override fun showsActiveEffects(): Boolean {
        return false
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        return wrapped.keyPressed(event)
    }
}