package me.ghluka.medved.config.entry

import com.google.gson.JsonElement
import com.google.gson.JsonPrimitive
import net.minecraft.client.Minecraft
import org.lwjgl.glfw.GLFW

/**
 * An integer entry holding a GLFW key code.
 *
 * In addition to the standard [onChange] listener inherited from [ConfigEntry],
 * keybinds expose an [onPress] event that fires on the tick the key goes down
 * (i.e. leading-edge detection, not repeat). The key is polled each client tick
 * by [ConfigManager] via [tick].
 *
 * Use [GLFW.GLFW_KEY_UNKNOWN] (-1) to represent an unbound key.
 */
class KeybindEntry(name: String, default: Int) : ConfigEntry<Int>(name, default) {

    private val pressListeners = mutableListOf<() -> Unit>()
    private var wasDown = false

    /** Register a listener that fires once on the tick the key is first pressed. */
    fun onPress(listener: () -> Unit): KeybindEntry {
        pressListeners += listener
        return this
    }

    /**
     * Pretend the key was already down, so the next tick won't fire a press event.
     * Call this immediately after programmatically assigning a new key value.
     */
    fun suppressNextPress() { wasDown = true }

    /** Called every client tick by [me.ghluka.medved.config.ConfigManager]. */
    fun tick() {
        if (value == GLFW.GLFW_KEY_UNKNOWN) {
            wasDown = false
            return
        }
        val mc = Minecraft.getInstance()
        val window = mc.window.handle()
        val isDown = GLFW.glfwGetKey(window, value) == GLFW.GLFW_PRESS
        if (mc.gui.screen() != null) {
            wasDown = isDown
            return
        }
        if (isDown && !wasDown) {
            pressListeners.forEach { it() }
        }
        wasDown = isDown
    }

    override fun toJson(): JsonElement = JsonPrimitive(value)
    override fun fromJson(element: JsonElement) { value = element.asInt }
}
