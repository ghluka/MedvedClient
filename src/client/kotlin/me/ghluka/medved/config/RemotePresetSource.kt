package me.ghluka.medved.config

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.slf4j.Logger
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

internal class RemotePresetSource(
    private val gson: Gson,
    private val logger: Logger
) {
    private val presetUrls = ConcurrentHashMap<String, String>()
    private val presetCache = ConcurrentHashMap<String, JsonObject>()

    fun refreshAsync() {
        Thread {
            runCatching {
                val content = URI(GITHUB_CONFIGS_API_URL).toURL().readText(Charsets.UTF_8)
                val mapped = parseGithubConfigsListing(content)
                presetUrls.clear()
                presetUrls.putAll(mapped)
                presetCache.keys.removeIf { key -> !presetUrls.containsKey(key) }
            }.onFailure {
                logger.warn("Failed to refresh remote config preset index", it)
            }
        }.also { it.isDaemon = true }.start()
    }

    fun list(): Set<String> = presetUrls.keys

    fun load(name: String): JsonObject? {
        presetCache[name]?.let { return it }

        val downloadUrl = presetUrls[name] ?: return null
        return runCatching {
            val text = URI(downloadUrl).toURL().readText(Charsets.UTF_8)
            gson.fromJson(text, JsonObject::class.java)
        }.onSuccess {
            presetCache[name] = it
        }.onFailure {
            logger.error("Failed to load remote preset '$name'", it)
        }.getOrNull()
    }

    private fun parseGithubConfigsListing(content: String): Map<String, String> {
        val array = gson.fromJson(content, JsonArray::class.java) ?: return emptyMap()
        return buildMap {
            array.forEach { el ->
                val obj = el.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
                val type = obj.get("type")?.asString.orEmpty()
                if (type != "file") return@forEach

                val nameWithExt = obj.get("name")?.asString?.trim().orEmpty()
                val downloadUrl = obj.get("download_url")?.asString?.trim().orEmpty()
                if (!nameWithExt.endsWith(".json", ignoreCase = true) || downloadUrl.isEmpty()) return@forEach

                val displayName = nameWithExt.removeSuffix(".json")
                if (displayName.isNotEmpty()) put(displayName, downloadUrl)
            }
        }
    }

    private companion object {
        const val GITHUB_CONFIGS_API_URL =
            "https://api.github.com/repos/ghluka/MedvedClient/contents/configs?ref=main"
    }
}
