package me.ghluka.medved.alt

import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import me.ghluka.medved.alt.handlers.CookieAuth
import me.ghluka.medved.alt.handlers.MicrosoftAuth
import me.ghluka.medved.mixin.client.MinecraftUserAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import java.io.File
import java.nio.file.Path
import java.util.Optional
import java.util.UUID

data class AltAccount(
    val id: String = UUID.randomUUID().toString(),
    val type: AltType,
    val username: String = "",
    val token: String = "",
    val uuid: String = "",
    val refreshToken: String = ""
)

enum class AltType { CRACKED, ACCESS_TOKEN, MICROSOFT, COOKIE }

object AltManager {

    private val gson = GsonBuilder().setPrettyPrinting().create()
    private lateinit var file: File
    val accounts = mutableListOf<AltAccount>()

    fun init(configDir: Path) {
        file = configDir.resolve("alts.json").toFile()
        load()
    }

    private fun load() {
        accounts.clear()
        if (!file.exists()) return
        try {
            val type = object : TypeToken<MutableList<AltAccount>>() {}.type
            val loaded: MutableList<AltAccount>? = gson.fromJson(file.readText(), type)
            if (loaded != null) accounts.addAll(loaded)
        } catch (_: Exception) {}
    }

    fun save() {
        file.parentFile?.mkdirs()
        file.writeText(gson.toJson(accounts))
    }

    fun addAccount(account: AltAccount) {
        accounts.add(account)
        save()
    }

    fun removeAccount(id: String) {
        accounts.removeIf { it.id == id }
        save()
    }

    fun login(account: AltAccount): Boolean {
        var acc = account
        if (acc.type == AltType.MICROSOFT && acc.refreshToken.isNotBlank()) {
            val refreshed = MicrosoftAuth.refresh(acc)
            if (refreshed != null) {
                acc = refreshed
                val idx = accounts.indexOfFirst { it.id == acc.id }
                if (idx != -1) { accounts[idx] = acc; save() }
            }
        }
        val user = buildUser(acc) ?: return false
        (Minecraft.getInstance() as MinecraftUserAccessor).setAltUser(user)
        return true
    }

    private fun buildUser(acc: AltAccount): User? {
        if (acc.username.isBlank()) return null
        val uuid = resolveUuid(acc)
        val token = acc.token
        return User(acc.username, uuid, token, Optional.empty(), Optional.empty())
    }

    private fun resolveUuid(acc: AltAccount): UUID {
        if (acc.uuid.isNotBlank()) {
            try { return UUID.fromString(acc.uuid) } catch (_: Exception) {}
        }
        return UUID.nameUUIDFromBytes("OfflinePlayer:${acc.username}".toByteArray(Charsets.UTF_8))
    }

    fun requestDeviceCode(): MicrosoftAuth.DeviceCodeInfo {
        return MicrosoftAuth.requestDeviceCode()
    }

    fun pollForAuth(deviceCode: String, pollInterval: Int, onDone: (MicrosoftAuth.McProfile?) -> Unit): Thread {
        return MicrosoftAuth.pollForAuth(deviceCode, pollInterval, onDone)
    }

    fun authenticateFromCookieFile(cookieFile: java.io.File): CookieAuth.McProfile? {
        return CookieAuth.authenticateFromCookieFile(cookieFile)
    }
}
