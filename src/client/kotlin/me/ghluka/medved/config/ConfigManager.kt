package me.ghluka.medved.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.module.modules.other.Colour
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import org.slf4j.LoggerFactory
import java.nio.file.Path

object ConfigManager {

    private val logger = LoggerFactory.getLogger("medved/config")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configs = mutableListOf<Config>()
    private lateinit var store: ConfigStore
    private lateinit var presetStore: PresetStore
    private lateinit var remotePresetSource: RemotePresetSource

    /** Save every 6 000 ticks (~5 minutes at 20 TPS). */
    private const val SAVE_INTERVAL_TICKS = 6_000
    private var tickCounter = 0

    /**
     * Must be called once during [net.fabricmc.api.ClientModInitializer.onInitializeClient].
     * Registers tick and lifecycle hooks for periodic / shutdown saves.
     */
    fun init(configDir: Path) {
        store = ConfigStore(configDir, gson, logger)
        presetStore = PresetStore(configDir.resolve("presets"), gson, logger)
        remotePresetSource = RemotePresetSource(gson, logger)
        remotePresetSource.refreshAsync()

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            configs.forEach { it.tickKeybinds() }
            refreshDynamicColors()
            if (++tickCounter >= SAVE_INTERVAL_TICKS) {
                tickCounter = 0
                saveAll()
            }
        }

        LevelRenderEvents.END_MAIN.register { _ ->
            refreshDynamicColors()
        }

        ClientLifecycleEvents.CLIENT_STOPPING.register { _ ->
            saveAll()
        }
    }

    /**
     * Register a config with the manager. The config's values are loaded from
     * disk immediately (or written as defaults if no file exists yet).
     */
    fun <T : Config> register(config: T): T {
        configs += config
        load(config)
        return config
    }

    fun saveAll() = configs.forEach(::save)

    fun loadAll() = configs.forEach(::load)

    private fun load(config: Config) {
        if (!store.load(config)) {
            store.save(config)
        }
        refreshDynamicColors()
    }

    private fun save(config: Config) = store.save(config)

    fun savePreset(name: String) {
        presetStore.save(name, configs)
    }

    fun loadPreset(name: String) {
        val local = presetStore.load(name)
        if (local != null) {
            applyPresetRoot(local)
            return
        }

        remotePresetSource.load(name)?.let(::applyPresetRoot)
    }

    fun listPresets(): List<String> {
        val local = presetStore.list()
        return (local + remotePresetSource.list()).distinct().sorted()
    }

    fun openPresetFolder() {
        presetStore.openFolder()
    }

    fun refreshDynamicColors() {
        refreshDynamicColorsInternal()
    }

    private fun refreshDynamicColorsInternal() {
        val themeColor = Colour.accent.liveColor(Colour.accent.value)
        val timeSeconds = ColorEntry.chromaTimeSeconds()
        val supportsTheme: (ColorEntry) -> Boolean = { entry -> entry !== Colour.accent }
        configs.forEach { it.refreshDynamicColors(themeColor, timeSeconds, supportsTheme) }
    }

    private fun applyPresetRoot(root: JsonObject) {
        configs.forEach { config ->
            if (root.has(config.name)) config.deserialize(root.getAsJsonObject(config.name))
        }
        refreshDynamicColors()
    }
}
