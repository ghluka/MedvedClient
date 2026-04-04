package me.ghluka.medved.alt

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import me.ghluka.medved.mixin.client.MinecraftUserAccessor
import net.minecraft.client.Minecraft
import net.minecraft.client.User
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Path
import java.util.Optional
import java.util.UUID

data class AltAccount(
    val id: String = UUID.randomUUID().toString(),
    val type: AltType,
    val username: String = "",
    val token: String = "",
    val uuid: String = ""
)

enum class AltType { CRACKED, ACCESS_TOKEN, MICROSOFT }

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
        val user = buildUser(account) ?: return false
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

    private const val CLIENT_ID       = "54fd49e4-2103-4044-9603-2b028c814ec3"
    private const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val TOKEN_URL       = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val XBL_URL         = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL        = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL    = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL  = "https://api.minecraftservices.com/minecraft/profile"

    data class DeviceCodeInfo(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    )

    data class McProfile(val username: String, val uuid: UUID, val accessToken: String)

    fun requestDeviceCode(): DeviceCodeInfo {
        val body = "client_id=${encode(CLIENT_ID)}&scope=XboxLive.signin+offline_access"
        val json = httpPost(DEVICE_CODE_URL, body)
        val obj  = JsonParser.parseString(json).asJsonObject
        if (obj.has("error")) {
            val desc = obj.get("error_description")?.asString ?: obj["error"].asString
            throw RuntimeException(desc.substringBefore("\r").take(100))
        }
        return DeviceCodeInfo(
            deviceCode      = obj["device_code"].asString,
            userCode        = obj["user_code"].asString,
            verificationUri = obj["verification_uri"].asString,
            expiresIn       = obj["expires_in"].asInt,
            interval        = obj.get("interval")?.asInt ?: 5
        )
    }

    fun pollForAuth(deviceCode: String, pollInterval: Int, onDone: (McProfile?) -> Unit): Thread {
        val thread = Thread {
            val mc       = Minecraft.getInstance()
            val deadline = System.currentTimeMillis() + 900_000L
            var interval = pollInterval.toLong()
            while (System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(interval * 1000L)
                    val body = "client_id=$CLIENT_ID" +
                        "&device_code=${encode(deviceCode)}" +
                        "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    val json = httpPost(TOKEN_URL, body)
                    val obj  = JsonParser.parseString(json).asJsonObject
                    if (obj.has("error")) {
                        when (obj["error"].asString) {
                            "authorization_pending" -> continue
                            "slow_down" -> { interval += 5; continue }
                            else -> { mc.execute { onDone(null) }; return@Thread }
                        }
                    }
                    val profile = exchangeForProfile(obj["access_token"].asString)
                    mc.execute { onDone(profile) }
                    return@Thread
                } catch (_: InterruptedException) {
                    mc.execute { onDone(null) }
                    return@Thread
                } catch (_: Exception) {
                    try { Thread.sleep(interval * 1000L) } catch (_: InterruptedException) {
                        mc.execute { onDone(null) }; return@Thread
                    }
                }
            }
            mc.execute { onDone(null) }
        }
        thread.isDaemon = true
        thread.name = "medved-ms-auth"
        thread.start()
        return thread
    }

    private fun exchangeForProfile(msAccessToken: String): McProfile {
        val xblBody = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msAccessToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val xblObj  = JsonParser.parseString(httpPost(XBL_URL, xblBody, "application/json")).asJsonObject
        val xblToken = xblObj["Token"].asString
        val uhs      = xblObj["DisplayClaims"].asJsonObject["xui"].asJsonArray[0].asJsonObject["uhs"].asString
        val xstsBody = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val xstsToken = JsonParser.parseString(httpPost(XSTS_URL, xstsBody, "application/json")).asJsonObject["Token"].asString
        val mcBody  = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val mcToken = JsonParser.parseString(httpPost(MC_LOGIN_URL, mcBody, "application/json")).asJsonObject["access_token"].asString
        val profileObj = JsonParser.parseString(httpGet(MC_PROFILE_URL, mapOf("Authorization" to "Bearer $mcToken"))).asJsonObject
        val name  = profileObj["name"].asString
        val raw   = profileObj["id"].asString
        val uuid  = UUID.fromString("${raw.substring(0,8)}-${raw.substring(8,12)}-${raw.substring(12,16)}-${raw.substring(16,20)}-${raw.substring(20)}")
        return McProfile(name, uuid, mcToken)
    }

    fun fetchMcProfile(mcAccessToken: String): Pair<String, UUID>? {
        return try {
            val json = httpGet(MC_PROFILE_URL, mapOf("Authorization" to "Bearer $mcAccessToken"))
            val obj  = JsonParser.parseString(json).asJsonObject
            if (!obj.has("name")) return null
            val name = obj["name"].asString
            val raw  = obj["id"].asString
            val uuid = UUID.fromString(
                "${raw.substring(0,8)}-${raw.substring(8,12)}-${raw.substring(12,16)}-${raw.substring(16,20)}-${raw.substring(20)}"
            )
            Pair(name, uuid)
        } catch (_: Exception) { null }
    }

    private fun httpPost(
        url: String,
        body: String,
        contentType: String = "application/x-www-form-urlencoded",
        extraHeaders: Map<String, String> = emptyMap()
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", contentType)
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return try {
            conn.inputStream.bufferedReader().readText()
        } catch (_: Exception) {
            conn.errorStream?.bufferedReader()?.readText() ?: ""
        }
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("Accept", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout    = 15_000
        headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        return conn.inputStream.bufferedReader().readText()
    }

    private fun encode(s: String): String = java.net.URLEncoder.encode(s, "UTF-8")
}
