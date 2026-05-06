package me.ghluka.medved.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.module.modules.other.Colour
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents
import org.slf4j.LoggerFactory
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap

object ConfigManager {

    private val logger = LoggerFactory.getLogger("medved/config")
    private val gson = GsonBuilder().setPrettyPrinting().create()

    private val configs = mutableListOf<Config>()

    /** Save every 6 000 ticks (~5 minutes at 20 TPS). */
    private const val SAVE_INTERVAL_TICKS = 6_000
    private var tickCounter = 0
    private const val GITHUB_CONFIGS_API_URL =
        "https://api.github.com/repos/ghluka/MedvedClient/contents/configs?ref=main"

    private lateinit var configDir: Path
    private lateinit var presetDir: Path
    private val remotePresetUrls = ConcurrentHashMap<String, String>()
    private val remotePresetCache = ConcurrentHashMap<String, JsonObject>()

    /**
     * Must be called once during [net.fabricmc.api.ClientModInitializer.onInitializeClient].
     * Registers tick and lifecycle hooks for periodic / shutdown saves.
     */
    fun init(configDir: Path) {
        this.configDir = configDir
        Files.createDirectories(configDir)
        presetDir = configDir.resolve("presets")
        refreshRemotePresetIndexAsync()

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
        val file = configDir.resolve("${config.name}.json")
        if (!Files.exists(file)) {
            save(config) // persist defaults on first run
            return
        }
        try {
            Files.newBufferedReader(file).use { reader ->
                config.deserialize(gson.fromJson(reader, JsonObject::class.java))
                refreshDynamicColors()
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
        val sanitized = name.trim().replace(Regex("[</*?\"\\\\>:|]+"), "_").take(64).ifBlank { "default" }
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
        if (Files.exists(file)) {
            try {
                Files.newBufferedReader(file).use { reader ->
                    val root = gson.fromJson(reader, JsonObject::class.java)
                    applyPresetRoot(root)
                }
            } catch (e: Exception) {
                logger.error("Failed to load preset '$name'", e)
            }
            return
        }

        val cached = remotePresetCache[name]
        if (cached != null) {
            runCatching { applyPresetRoot(cached) }
                .onFailure { logger.error("Failed to load remote preset '$name'", it) }
            return
        }

        val downloadUrl = remotePresetUrls[name] ?: return
        runCatching {
            val text = URL(downloadUrl).readText(Charsets.UTF_8)
            gson.fromJson(text, JsonObject::class.java)
        }.onSuccess {
            remotePresetCache[name] = it
            applyPresetRoot(it)
        }
            .onFailure { logger.error("Failed to load remote preset '$name'", it) }
    }

    fun listPresets(): List<String> {
        val local = if (!Files.exists(presetDir)) {
            emptyList()
        } else {
            try {
                Files.list(presetDir)
                    .filter { it.fileName.toString().endsWith(".json") }
                    .map { it.fileName.toString().removeSuffix(".json") }
                    .sorted()
                    .toList()
            } catch (_: Exception) {
                emptyList()
            }
        }
        return (local + remotePresetUrls.keys).distinct().sorted()
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

    private fun refreshRemotePresetIndexAsync() {
        Thread {
            runCatching {
                val content = URL(GITHUB_CONFIGS_API_URL).readText(Charsets.UTF_8)
                val mapped = parseGithubConfigsListing(content)
                remotePresetUrls.clear()
                remotePresetUrls.putAll(mapped)
                remotePresetCache.keys.removeIf { key -> !remotePresetUrls.containsKey(key) }
            }.onFailure {
                logger.warn("Failed to refresh remote config preset index", it)
            }
        }.also { it.isDaemon = true }.start()
    }

    private fun parseGithubConfigsListing(content: String): Map<String, String> {
        val array = gson.fromJson(content, JsonArray::class.java) ?: return emptyMap()
        val result = mutableMapOf<String, String>()
        array.forEach { el ->
            if (!el.isJsonObject) return@forEach
            val obj = el.asJsonObject
            val type = obj.get("type")?.asString.orEmpty()
            if (type != "file") return@forEach
            val nameWithExt = obj.get("name")?.asString?.trim().orEmpty()
            val downloadUrl = obj.get("download_url")?.asString?.trim().orEmpty()
            if (!nameWithExt.endsWith(".json", ignoreCase = true) || downloadUrl.isEmpty()) return@forEach
            val displayName = nameWithExt.removeSuffix(".json")
            if (displayName.isNotEmpty()) {
                result[displayName] = downloadUrl
            }
        }
        return result
    }
}
