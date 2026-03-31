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

    /**
     * Must be called once during [net.fabricmc.api.ClientModInitializer.onInitializeClient].
     * Registers tick and lifecycle hooks for periodic / shutdown saves.
     */
    fun init(configDir: Path) {
        this.configDir = configDir
        Files.createDirectories(configDir)

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
}
