package me.ghluka.medved.config

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.slf4j.Logger
import java.nio.file.Files
import java.nio.file.Path

internal class PresetStore(
    private val presetDir: Path,
    private val gson: Gson,
    private val logger: Logger
) {
    fun save(name: String, configs: List<Config>) {
        val sanitized = sanitizeName(name)
        Files.createDirectories(presetDir)

        val root = JsonObject().apply {
            configs.forEach { add(it.name, it.serialize()) }
        }

        runCatching {
            Files.newBufferedWriter(presetDir.resolve("$sanitized.json")).use {
                gson.toJson(root, it)
            }
        }.onFailure {
            logger.error("Failed to save preset '$sanitized'", it)
        }
    }

    fun load(name: String): JsonObject? {
        val file = presetDir.resolve("$name.json")
        if (!Files.exists(file)) return null

        return runCatching {
            Files.newBufferedReader(file).use { reader ->
                gson.fromJson(reader, JsonObject::class.java)
            }
        }.onFailure {
            logger.error("Failed to load preset '$name'", it)
        }.getOrNull()
    }

    fun list(): List<String> {
        if (!Files.exists(presetDir)) return emptyList()

        return runCatching {
            Files.list(presetDir).use { paths ->
                paths
                    .filter { it.fileName.toString().endsWith(".json") }
                    .map { it.fileName.toString().removeSuffix(".json") }
                    .sorted()
                    .toList()
            }
        }.getOrElse { emptyList() }
    }

    fun openFolder() {
        Files.createDirectories(presetDir)
        val dir = presetDir.toAbsolutePath().toString()
        runCatching {
            val os = System.getProperty("os.name").lowercase()
            when {
                "win" in os -> ProcessBuilder("explorer.exe", dir).start()
                "mac" in os -> ProcessBuilder("open", dir).start()
                else -> ProcessBuilder("xdg-open", dir).start()
            }
        }
    }

    private fun sanitizeName(name: String): String =
        name.trim().replace(Regex("[</*?\"\\\\>:|]+"), "_").take(64).ifBlank { "default" }
}
