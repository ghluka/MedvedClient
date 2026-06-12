package me.ghluka.medved.config.entry

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject

internal val JsonElement?.objectOrNull: JsonObject?
    get() = this?.takeIf { it.isJsonObject }?.asJsonObject

internal val JsonElement?.arrayOrNull: JsonArray?
    get() = this?.takeIf { it.isJsonArray }?.asJsonArray

internal val JsonElement?.stringOrNull: String?
    get() = this?.takeIf { it.isJsonPrimitive }?.runCatching { asString }?.getOrNull()

internal val JsonElement?.intOrNull: Int?
    get() = this?.takeIf { it.isJsonPrimitive }?.runCatching { asInt }?.getOrNull()

internal val JsonElement?.floatOrNull: Float?
    get() = this?.takeIf { it.isJsonPrimitive }?.runCatching { asFloat }?.getOrNull()

internal val JsonElement?.doubleOrNull: Double?
    get() = this?.takeIf { it.isJsonPrimitive }?.runCatching { asDouble }?.getOrNull()

internal val JsonElement?.booleanOrNull: Boolean?
    get() = this?.takeIf { it.isJsonPrimitive }?.runCatching { asBoolean }?.getOrNull()

internal fun JsonObject.stringOrNull(key: String): String? = get(key).stringOrNull

internal fun JsonObject.floatOrNull(key: String): Float? = get(key).floatOrNull

internal inline fun <reified T : Enum<T>> enumValueOrNull(name: String?): T? =
    name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

internal fun <T> JsonArray.toListSafely(map: (JsonElement) -> T?): List<T> =
    mapNotNull(map)
