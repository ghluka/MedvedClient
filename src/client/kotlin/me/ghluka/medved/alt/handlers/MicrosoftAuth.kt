package me.ghluka.medved.alt.handlers

import com.google.gson.JsonParser
import me.ghluka.medved.alt.AltAccount
import me.ghluka.medved.util.HttpUtils
import net.minecraft.client.Minecraft
import java.util.UUID

object MicrosoftAuth {

    private const val CLIENT_ID            = "54fd49e4-2103-4044-9603-2b028c814ec3"
    private const val LEGACY_CLIENT_ID     = "00000000402b5328"
    private const val LEGACY_REDIRECT_URI  = "https://login.live.com/oauth20_desktop.srf"
    private const val LEGACY_SCOPE         = "service::user.auth.xboxlive.com::MBI_SSL"
    private const val DEVICE_CODE_URL      = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val DEVICE_TOKEN_URL     = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val LEGACY_TOKEN_URL     = "https://login.live.com/oauth20_token.srf"
    private const val OAUTH_SCOPE           = "XboxLive.signin offline_access"
    private const val XBL_URL              = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL             = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL         = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL       = "https://api.minecraftservices.com/minecraft/profile"
    private val XBOX_HEADERS = mapOf("x-xbl-contract-version" to "1")

    data class DeviceCodeInfo(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val interval: Int
    )

    data class McProfile(
        val username: String,
        val uuid: UUID,
        val accessToken: String,
        val refreshToken: String
    )

    fun requestDeviceCode(): DeviceCodeInfo {
        val body = "client_id=${HttpUtils.encode(CLIENT_ID)}&scope=${HttpUtils.encode(OAUTH_SCOPE)}"
        val json = HttpUtils.httpPost(DEVICE_CODE_URL, body)
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
                    val body = "client_id=${HttpUtils.encode(CLIENT_ID)}" +
                            "&device_code=${HttpUtils.encode(deviceCode)}" +
                            "&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                    val json = HttpUtils.httpPost(DEVICE_TOKEN_URL, body)
                    val obj  = JsonParser.parseString(json).asJsonObject
                    if (obj.has("error")) {
                        when (obj["error"].asString) {
                            "authorization_pending" -> continue
                            "slow_down" -> { interval += 5; continue }
                            else -> { mc.execute { onDone(null) }; return@Thread }
                        }
                    }

                    val refreshToken = obj.get("refresh_token")?.asString ?: ""
                    val profile = exchangeForProfile(obj["access_token"].asString, refreshToken)
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

    private fun exchangeForProfile(
        msAccessToken: String,
        refreshToken: String = "",
        legacyRpsTicket: Boolean = false,
    ): McProfile {
        val rpsTicket = if (legacyRpsTicket) msAccessToken else "d=$msAccessToken"
        val xblBody = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"$rpsTicket"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val xblObj = parseObject(
            HttpUtils.httpPost(XBL_URL, xblBody, "application/json", XBOX_HEADERS),
            "Xbox user authentication",
        )
        val xblToken = requiredString(xblObj, "Token", "Xbox user authentication")
        val xstsBody = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val xstsObj = parseObject(
            HttpUtils.httpPost(XSTS_URL, xstsBody, "application/json", XBOX_HEADERS),
            "Xbox security token exchange",
        )
        val xstsToken = requiredString(xstsObj, "Token", "Xbox security token exchange")
        val uhs = runCatching {
            xstsObj["DisplayClaims"].asJsonObject["xui"].asJsonArray[0].asJsonObject["uhs"].asString
        }.getOrElse { throw IllegalStateException("Xbox security token exchange did not return a user hash") }
        val mcBody  = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val mcObj = parseObject(
            HttpUtils.httpPost(MC_LOGIN_URL, mcBody, "application/json"),
            "Minecraft authentication",
        )
        val mcToken = requiredString(mcObj, "access_token", "Minecraft authentication")
        val profileObj = parseObject(
            HttpUtils.httpGet(MC_PROFILE_URL, mapOf("Authorization" to "Bearer $mcToken")),
            "Minecraft profile lookup",
        )
        val name = requiredString(profileObj, "name", "Minecraft profile lookup")
        val raw = requiredString(profileObj, "id", "Minecraft profile lookup")
        val uuid  = UUID.fromString("${raw.substring(0,8)}-${raw.substring(8,12)}-${raw.substring(12,16)}-${raw.substring(16,20)}-${raw.substring(20)}")
        return McProfile(name, uuid, mcToken, refreshToken)
    }

    fun fetchMcProfile(mcAccessToken: String): Pair<String, UUID>? {
        return try {
            val json = HttpUtils.httpGet(MC_PROFILE_URL, mapOf("Authorization" to "Bearer $mcAccessToken"))
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

    fun authenticateRefreshToken(refreshToken: String): McProfile {
        require(refreshToken.isNotBlank()) { "Refresh token cannot be empty" }
        val tokens = refreshMicrosoftToken(refreshToken, legacyFirst = true)
        return exchangeForProfile(tokens.accessToken, tokens.refreshToken, tokens.legacy)
    }

    fun refresh(account: AltAccount): AltAccount? {
        if (account.refreshToken.isBlank()) return null
        return try {
            val profile = authenticateRefreshToken(account.refreshToken)

            account.copy(
                username     = profile.username,
                token        = profile.accessToken,
                uuid         = profile.uuid.toString(),
                refreshToken = profile.refreshToken
            )
        } catch (_: Exception) { null }
    }

    private data class MicrosoftTokens(
        val accessToken: String,
        val refreshToken: String,
        val legacy: Boolean,
    )

    private fun refreshMicrosoftToken(refreshToken: String, legacyFirst: Boolean = false): MicrosoftTokens {
        val deviceBody = "client_id=${HttpUtils.encode(CLIENT_ID)}" +
                "&refresh_token=${HttpUtils.encode(refreshToken)}" +
                "&grant_type=refresh_token" +
                "&scope=${HttpUtils.encode(OAUTH_SCOPE)}"
        val legacyBody = "client_id=${HttpUtils.encode(LEGACY_CLIENT_ID)}" +
                "&grant_type=refresh_token" +
                "&redirect_uri=${HttpUtils.encode(LEGACY_REDIRECT_URI)}" +
                "&refresh_token=${HttpUtils.encode(refreshToken)}" +
                "&scope=${HttpUtils.encode(LEGACY_SCOPE)}"

        val attempts = if (legacyFirst) {
            listOf(Triple(LEGACY_TOKEN_URL, legacyBody, true), Triple(DEVICE_TOKEN_URL, deviceBody, false))
        } else {
            listOf(Triple(DEVICE_TOKEN_URL, deviceBody, false), Triple(LEGACY_TOKEN_URL, legacyBody, true))
        }

        for ((url, body, legacy) in attempts) {
            val tokens = parseTokenResponse(HttpUtils.httpPost(url, body), refreshToken, legacy)
            if (tokens != null) return tokens
        }
        throw IllegalArgumentException("Microsoft rejected the refresh token")
    }

    private fun parseTokenResponse(
        json: String,
        previousRefreshToken: String,
        legacy: Boolean,
    ): MicrosoftTokens? {
        val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
        val accessToken = obj.get("access_token")?.takeUnless { it.isJsonNull }?.asString
        if (obj.has("error") || accessToken.isNullOrBlank()) return null
        return MicrosoftTokens(
            accessToken = accessToken,
            refreshToken = obj.get("refresh_token")?.takeUnless { it.isJsonNull }?.asString ?: previousRefreshToken,
            legacy = legacy,
        )
    }

    private fun parseObject(json: String, stage: String): com.google.gson.JsonObject {
        val element = runCatching { JsonParser.parseString(json) }.getOrNull()
        if (element == null || element.isJsonNull || !element.isJsonObject) {
            throw IllegalStateException("$stage returned an empty or invalid response")
        }
        val obj = element.asJsonObject
        if (obj.has("error")) {
            val message = obj.get("error_description")?.takeUnless { it.isJsonNull }?.asString
                ?: obj.get("Message")?.takeUnless { it.isJsonNull }?.asString
                ?: obj.get("error")?.takeUnless { it.isJsonNull }?.asString
                ?: "request failed"
            throw IllegalStateException("$stage failed: ${message.take(100)}")
        }
        return obj
    }

    private fun requiredString(obj: com.google.gson.JsonObject, key: String, stage: String): String {
        return obj.get(key)?.takeUnless { it.isJsonNull }?.asString?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("$stage did not return $key")
    }
}
