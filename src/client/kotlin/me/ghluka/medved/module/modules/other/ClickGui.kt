package me.ghluka.medved.module.modules.other

import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object ClickGui : Module("Click Gui", "The click GUI", Category.OTHER) {

    override val showInModulesList = false

    val showDescriptions = boolean("show_descriptions", true)

    init {
        keybind.value = GLFW.GLFW_KEY_RIGHT_SHIFT
    }

    override fun onEnabled() {
        val mc = Minecraft.getInstance()
        if (mc.screen !is ClickGui) mc.setScreen(ClickGui())
    }

    override fun onDisabled() {
        val mc = Minecraft.getInstance()
        if (mc.screen is ClickGui) mc.setScreen(null)
    }
}
