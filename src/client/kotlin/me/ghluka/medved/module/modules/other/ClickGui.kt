package me.ghluka.medved.module.modules.other

import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object ClickGui : Module("Click Gui", "The click GUI", Category.OTHER) {

    override val showInModulesList = false

    private var screenInstance: me.ghluka.medved.gui.ClickGui? = null


    enum class Mode { DROPDOWN, SIDEBAR }
    val currentMode  = enum("mode", Mode.DROPDOWN)

    val showDescriptions = boolean("show_descriptions", true).also {
        it.visibleWhen = { currentMode.value == Mode.DROPDOWN }
    }
    val showBackground   = boolean("show_background", true)
    val resetLayout      = button("reset_layout", "Reset Layout") { ClickGui.resetPositions() }

    init {
        keybind.value = GLFW.GLFW_KEY_RIGHT_SHIFT
    }

    override fun onEnabled() {
        val mc = Minecraft.getInstance()
        val screen = screenInstance ?: me.ghluka.medved.gui.ClickGui().also { screenInstance = it }
        if (mc.gui.screen() !== screen) mc.gui.setScreen(screen)
    }

    override fun onDisabled() {
        val mc = Minecraft.getInstance()
        if (mc.gui.screen() is ClickGui) mc.gui.setScreen(null)
    }
}
