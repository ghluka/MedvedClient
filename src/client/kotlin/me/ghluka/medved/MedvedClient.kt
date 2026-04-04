package me.ghluka.medved

import me.ghluka.medved.alt.AltManager
import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.combat.Backtrack
import me.ghluka.medved.module.modules.combat.NoHitDelay
import me.ghluka.medved.module.modules.combat.Reach
import me.ghluka.medved.module.modules.combat.Velocity
import me.ghluka.medved.module.modules.hud.ModulesList
import me.ghluka.medved.module.modules.movement.Sprint
import me.ghluka.medved.module.modules.other.ClickGui
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.module.modules.world.Scaffold
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import org.lwjgl.glfw.GLFW

object MedvedClient : ClientModInitializer {

    private var insertWasDown = false

    override fun onInitializeClient() {
        val gameDir = FabricLoader.getInstance().gameDir

        ConfigManager.init(gameDir.resolve("config/medved"))
        AltManager.init(gameDir.resolve("config/medved"))

        ModuleManager.register(Sprint)
        ModuleManager.register(ClickGui)
        ModuleManager.register(Font)
        ModuleManager.register(Colour)
        ModuleManager.register(NoHitDelay)
        ModuleManager.register(Backtrack)
        ModuleManager.register(Velocity)
        ModuleManager.register(Reach)
        ModuleManager.register(Scaffold)
        ModuleManager.register(ModulesList)

        ModuleManager.init()

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val handle = mc.window.handle()
            val down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_INSERT) == GLFW.GLFW_PRESS
            if (down && !insertWasDown) ClickGui.toggle()
            insertWasDown = down
        }
    }
}