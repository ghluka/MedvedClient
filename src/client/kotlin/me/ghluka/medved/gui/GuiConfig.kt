package me.ghluka.medved.gui

import me.ghluka.medved.config.Config
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

object GuiConfig : Config("gui") {
    val openKey = keybind("open_key", GLFW.GLFW_KEY_RIGHT_SHIFT)
        .onPress {
            val mc = Minecraft.getInstance()
            if (mc.screen is ClickGui) mc.setScreen(null)
            else if (mc.screen == null) mc.setScreen(ClickGui())
        }
}
