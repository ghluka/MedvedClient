package me.ghluka.medved.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

internal class ConfigStore(
    private val configDir: Path,
    private val gson: Gson,
    private val logger: Logger
) {
    init {
        Files.createDirectories(configDir)
    }

    fun load(config: Config): Boolean {
        val file = fileFor(config)
        if (!Files.exists(file)) return false

        runCatching {
            Files.newBufferedReader(file).use { reader ->
                config.deserialize(gson.fromJson(reader, JsonObject::class.java))
            }
        }.onFailure {
            logger.error("Failed to load config '${config.name}'", it)
            return false
        }

        return true
    }

    fun save(config: Config) {
        val file = fileFor(config)
        runCatching {
            Files.newBufferedWriter(file).use { writer ->
                gson.toJson(config.serialize(), writer)
            }
        }.onFailure {
            logger.error("Failed to save config '${config.name}'", it)
        }
    }

    private fun fileFor(config: Config): Path = configDir.resolve("${config.name}.json")
}
