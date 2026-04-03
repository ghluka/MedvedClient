package me.ghluka.medved

import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.movement.SprintModule
import me.ghluka.medved.module.modules.other.ClickGuiModule
import me.ghluka.medved.module.modules.other.ColorModule
import me.ghluka.medved.module.modules.other.FontModule
import me.ghluka.medved.module.modules.world.ScaffoldModule
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.lwjgl.glfw.GLFW

object MedvedClient : ClientModInitializer {

    private var insertWasDown = false

    override fun onInitializeClient() {
        val gameDir = FabricLoader.getInstance().gameDir

        ConfigManager.init(gameDir.resolve("config/medved"))

        ModuleManager.register(SprintModule)
        ModuleManager.register(ClickGuiModule)
        ModuleManager.register(FontModule)
        ModuleManager.register(ColorModule)
        ModuleManager.register(ScaffoldModule)

        ModuleManager.init()

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val handle = mc.window.handle()
            val down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_INSERT) == GLFW.GLFW_PRESS
            if (down && !insertWasDown) ClickGuiModule.toggle()
            insertWasDown = down
        }
    }
}