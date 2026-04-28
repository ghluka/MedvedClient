package me.ghluka.medved.module

import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.util.NotificationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import org.slf4j.LoggerFactory

object ModuleManager {

    private val logger = LoggerFactory.getLogger("medved/modules")
    private val modules = mutableListOf<Module>()

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            modules.forEach { it.dispatchTick(client) }
        }

        LevelRenderEvents.END_MAIN.register { ctx ->
            ConfigManager.refreshDynamicColors()
            modules.forEach { it.dispatchLevelRender(ctx) }
        }

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("medved", "modules"),
            HudElement { extractor, delta ->
                if (Minecraft.getInstance().screen?.javaClass?.name != "me.ghluka.medved.gui.ClickGui")
                    modules.forEach { it.dispatchHudRender(extractor, delta) }
            }
        )

        HudElementRegistry.addLast(
            Identifier.fromNamespaceAndPath("medved", "notifications"),
            HudElement { extractor, _ -> NotificationManager.render(extractor) }
        )
    }

    /**
     * Register a module. Its config is automatically registered with
     * [ConfigManager] (loaded from disk immediately).
     */
    fun <T : Module> register(module: T): T {
        modules += module
        ConfigManager.register(module)
        logger.info("Registered module: ${module.name}")
        return module
    }

    /** Retrieve a module by its exact class. */
    fun <T : Module> get(clazz: Class<T>): T? = modules.filterIsInstance(clazz).firstOrNull()

    /** Retrieve a module by reified type. */
    inline fun <reified T : Module> get(): T? = get(T::class.java)

    /** All registered modules. */
    fun getAll(): List<Module> = modules

    /** All modules belonging to a given [Module.Category]. */
    fun getByCategory(category: Module.Category): List<Module> =
        modules.filter { it.category == category }

    /** All currently enabled modules. */
    fun getEnabled(): List<Module> = modules.filter { it.isEnabled() }
}
