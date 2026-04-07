package me.ghluka.medved.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

object ConfigManager {

    private val logger = LoggerFactory.getLogger("medved/config")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configs = mutableListOf<Config>()

    /** Save every 6 000 ticks (~5 minutes at 20 TPS). */
    private const val SAVE_INTERVAL_TICKS = 6_000
    private var tickCounter = 0

    private lateinit var configDir: Path
    private lateinit var presetDir: Path

    /**
     * Must be called once during [net.fabricmc.api.ClientModInitializer.onInitializeClient].
     * Registers tick and lifecycle hooks for periodic / shutdown saves.
     */
    fun init(configDir: Path) {
        this.configDir = configDir
        Files.createDirectories(configDir)
        presetDir = configDir.resolve("presets")

        ClientTickEvents.END_CLIENT_TICK.register { _ ->
            configs.forEach { it.tickKeybinds() }
            if (++tickCounter >= SAVE_INTERVAL_TICKS) {
                tickCounter = 0
                saveAll()
            }
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
        val file = configDir.resolve("${config.name}.json")
        if (!Files.exists(file)) {
            save(config) // persist defaults on first run
            return
        }
        try {
            Files.newBufferedReader(file).use { reader ->
                config.deserialize(gson.fromJson(reader, JsonObject::class.java))
            }
        } catch (e: Exception) {
            logger.error("Failed to load config '${config.name}'", e)
        }
    }

    private fun save(config: Config) {
        val file = configDir.resolve("${config.name}.json")
        try {
            Files.newBufferedWriter(file).use { writer ->
                gson.toJson(config.serialize(), writer)
            }
        } catch (e: Exception) {
            logger.error("Failed to save config '${config.name}'", e)
        }
    }

    fun savePreset(name: String) {
        val sanitized = name.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifBlank { "default" }
        Files.createDirectories(presetDir)
        val root = JsonObject()
        configs.forEach { root.add(it.name, it.serialize()) }
        try {
            Files.newBufferedWriter(presetDir.resolve("$sanitized.json")).use { gson.toJson(root, it) }
        } catch (e: Exception) {
            logger.error("Failed to save preset '$sanitized'", e)
        }
    }

    fun loadPreset(name: String) {
        val file = presetDir.resolve("$name.json")
        if (!Files.exists(file)) return
        try {
            Files.newBufferedReader(file).use { reader ->
                val root = gson.fromJson(reader, JsonObject::class.java)
                configs.forEach { config ->
                    if (root.has(config.name)) config.deserialize(root.getAsJsonObject(config.name))
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to load preset '$name'", e)
        }
    }

    fun listPresets(): List<String> {
        if (!Files.exists(presetDir)) return emptyList()
        return try {
            Files.list(presetDir)
                .filter { it.fileName.toString().endsWith(".json") }
                .map { it.fileName.toString().removeSuffix(".json") }
                .sorted()
                .toList()
        } catch (_: Exception) { emptyList() }
    }

    fun openPresetFolder() {
        Files.createDirectories(presetDir)
        val dir = presetDir.toAbsolutePath().toString()
        try {
            val os = System.getProperty("os.name").lowercase()
            when {
                "win" in os -> ProcessBuilder("explorer.exe", dir).start()
                "mac" in os -> ProcessBuilder("open", dir).start()
                else        -> ProcessBuilder("xdg-open", dir).start()
            }
        } catch (_: Exception) {}
    }
}
