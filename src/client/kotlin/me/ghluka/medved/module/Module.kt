package me.ghluka.medved.module

import me.ghluka.medved.config.Config
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import org.lwjgl.glfw.GLFW

/**
 * Base class for all client modules. Extends [Config] so that every module
 * owns its own serialised config file.
 *
 * ### Subclass hooks  (override to react inside the module)
 * ```kotlin
 * override fun onEnabled()  { ... }
 * override fun onDisabled() { ... }
 * override fun onTick(client: Minecraft) { ... }
 * override fun onLevelRender(ctx: LevelRenderContext) { ... }
 * override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) { ... }
 * ```
 *
 * ### External event subscriptions  (for code outside the module)
 * ```kotlin
 * SprintModule.onEnable  { ... }
 * SprintModule.onDisable { ... }
 * SprintModule.onTick    { client -> ... }
 * SprintModule.onLevelRender { ctx -> ... }
 * SprintModule.onHudRender   { extractor, delta -> ... }
 * ```
 *
 * [ModuleManager.register] automatically passes the module to [me.ghluka.medved.config.ConfigManager]
 * so you only ever need to call `ModuleManager.register(MyModule)`.
 */
abstract class Module(
    name: String,
    val description: String,
    val category: Category,
) : Config(name) {

    /** Protected modules are always enabled and cannot be toggled or key-bound. */
    open val isProtected: Boolean = false

    /** When false this module is never listed in the ModulesListHUD overlay. */
    open val showInModulesList: Boolean = true

    val enabled = boolean("enabled", false)
    val keybind = keybind("keybind", GLFW.GLFW_KEY_UNKNOWN)
        .onPress { if (!isProtected) toggle() }

    private val enableListeners      = mutableListOf<() -> Unit>()
    private val disableListeners     = mutableListOf<() -> Unit>()
    private val tickListeners        = mutableListOf<(Minecraft) -> Unit>()
    private val levelRenderListeners = mutableListOf<(LevelRenderContext) -> Unit>()
    private val hudRenderListeners   = mutableListOf<(GuiGraphicsExtractor, DeltaTracker) -> Unit>()

    /** Subscribe a listener that fires each time this module is enabled. */
    fun onEnable(handler: () -> Unit): Module  { enableListeners  += handler; return this }
    /** Subscribe a listener that fires each time this module is disabled. */
    fun onDisable(handler: () -> Unit): Module { disableListeners += handler; return this }
    /** Subscribe a listener that fires every tick while the module is enabled. */
    fun onTick(handler: (Minecraft) -> Unit): Module { tickListeners += handler; return this }
    /** Subscribe a listener that fires each level-render frame while the module is enabled. */
    fun onLevelRender(handler: (LevelRenderContext) -> Unit): Module { levelRenderListeners += handler; return this }
    /** Subscribe a listener that fires each HUD-render frame while the module is enabled. */
    fun onHudRender(handler: (GuiGraphicsExtractor, DeltaTracker) -> Unit): Module { hudRenderListeners += handler; return this }

    protected open fun onEnabled() {}
    protected open fun onDisabled() {}
    protected open fun onTick(client: Minecraft) {}
    protected open fun onLevelRender(ctx: LevelRenderContext) {}
    protected open fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {}

    fun isEnabled(): Boolean = enabled.value

    open fun hudInfo(): String = ""

    open fun hudInfoColor(): Int = (255 shl 24) or (170 shl 16) or (170 shl 8) or 170

    fun toggle() { if (enabled.value) disable() else enable() }

    fun enable() {
        if (enabled.value) return
        enabled.value = true
        onEnabled()
        enableListeners.forEach { it() }
    }

    fun disable() {
        if (!enabled.value) return
        enabled.value = false
        onDisabled()
        disableListeners.forEach { it() }
    }

    internal fun dispatchTick(client: Minecraft) {
        if (!isEnabled()) return
        onTick(client)
        tickListeners.forEach { it(client) }
    }

    internal fun dispatchLevelRender(ctx: LevelRenderContext) {
        if (!isEnabled()) return
        onLevelRender(ctx)
        levelRenderListeners.forEach { it(ctx) }
    }

    internal fun dispatchHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        if (!isEnabled()) return
        onHudRender(extractor, delta)
        hudRenderListeners.forEach { it(extractor, delta) }
    }

    enum class Category {
        COMBAT, MOVEMENT, RENDER, PLAYER, WORLD, MISC, HUD, OTHER
    }
}
