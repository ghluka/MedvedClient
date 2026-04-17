package me.ghluka.medved

import me.ghluka.medved.alt.AltManager
import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.module.modules.combat.*
import me.ghluka.medved.module.modules.player.*
import me.ghluka.medved.module.modules.hud.*
import me.ghluka.medved.module.modules.movement.*
import me.ghluka.medved.module.modules.other.*
import me.ghluka.medved.module.modules.world.*
import me.ghluka.medved.update.UpdateChecker
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

        // combat
        ModuleManager.register(KillAura)
        ModuleManager.register(AimAssist)
        ModuleManager.register(LeftClicker)
        ModuleManager.register(RightClicker)
        ModuleManager.register(NoHitDelay)
        ModuleManager.register(AutoBlock)
        ModuleManager.register(HitSelect)
        ModuleManager.register(Velocity)
        ModuleManager.register(ComboTap)
        ModuleManager.register(KnockbackDelay)
        ModuleManager.register(Reach)
        ModuleManager.register(KnockbackDisplacement)
        ModuleManager.register(Backtrack)
        ModuleManager.register(Criticals)
        // movement
        ModuleManager.register(Sprint)
        ModuleManager.register(Speed)
        ModuleManager.register(Flight)
        ModuleManager.register(Timer)
        ModuleManager.register(NoFall)
        // render
        // player
        ModuleManager.register(FakeLag)
        ModuleManager.register(Blink)
        ModuleManager.register(ClientBrand)
        // world
        ModuleManager.register(Scaffold)
        ModuleManager.register(Clutch)
        ModuleManager.register(FastPlace)
        ModuleManager.register(AutoPlace)
        ModuleManager.register(BedBreaker)
        // other
        ModuleManager.register(ClickGui)
        ModuleManager.register(Font)
        ModuleManager.register(Colour)
        ModuleManager.register(Rotations)
        ModuleManager.register(TargetFilter)
        // hud
        ModuleManager.register(ModulesList)
        ModuleManager.register(TargetHud)
        ModuleManager.register(ScaffoldInfo)
        // skyblock
        ModuleManager.register(ChestAura)

        ModuleManager.init()
        UpdateChecker.init()

        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            me.ghluka.medved.manager.LagManager.onTick()
            val handle = mc.window.handle()
            val down = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_INSERT) == GLFW.GLFW_PRESS
            if (down && !insertWasDown) ClickGui.toggle()
            insertWasDown = down
        }
    }
}