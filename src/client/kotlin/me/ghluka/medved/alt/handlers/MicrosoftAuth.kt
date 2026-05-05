package me.ghluka.medved.alt.handlers

import com.google.gson.JsonParser
import me.ghluka.medved.alt.AltAccount
import me.ghluka.medved.util.HttpUtils
import net.minecraft.client.Minecraft
import java.util.UUID

object MicrosoftAuth {

    private const val CLIENT_ID            = "54fd49e4-2103-4044-9603-2b028c814ec3"
    private const val DEVICE_CODE_URL      = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
    private const val DEVICE_TOKEN_URL     = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    private const val TOKEN_REFRESH_URL    = "https://login.live.com/oauth20_token.srf"
    private const val XBL_URL              = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL             = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL         = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL       = "https://api.minecraftservices.com/minecraft/profile"

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
        val body = "client_id=${HttpUtils.encode(CLIENT_ID)}&scope=${HttpUtils.encode("XboxLive.signin XboxLive.offline_access")}"
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

    private fun exchangeForProfile(msAccessToken: String, refreshToken: String = ""): McProfile {
        val xblBody = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msAccessToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val xblObj  = JsonParser.parseString(HttpUtils.httpPost(XBL_URL, xblBody, "application/json")).asJsonObject
        val xblToken = xblObj["Token"].asString
        val uhs      = xblObj["DisplayClaims"].asJsonObject["xui"].asJsonArray[0].asJsonObject["uhs"].asString
        val xstsBody = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val xstsToken = JsonParser.parseString(HttpUtils.httpPost(XSTS_URL, xstsBody, "application/json")).asJsonObject["Token"].asString
        val mcBody  = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val mcToken = JsonParser.parseString(HttpUtils.httpPost(MC_LOGIN_URL, mcBody, "application/json")).asJsonObject["access_token"].asString
        val profileObj = JsonParser.parseString(HttpUtils.httpGet(MC_PROFILE_URL, mapOf("Authorization" to "Bearer $mcToken"))).asJsonObject
        val name  = profileObj["name"].asString
        val raw   = profileObj["id"].asString
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

    fun refresh(account: AltAccount): AltAccount? {
        if (account.refreshToken.isBlank()) return null
        return try {
            val body = "client_id=${HttpUtils.encode(CLIENT_ID)}" +
                    "&refresh_token=${HttpUtils.encode(account.refreshToken)}" +
                    "&grant_type=refresh_token" +
                    "&scope=${HttpUtils.encode("XboxLive.signin XboxLive.offline_access")}"
            val json = HttpUtils.httpPost(TOKEN_REFRESH_URL, body)
            val obj  = JsonParser.parseString(json).asJsonObject
            if (obj.has("error")) return null

            val newRefresh = obj.get("refresh_token")?.asString ?: account.refreshToken
            val profile    = exchangeForProfile(obj["access_token"].asString, newRefresh)

            account.copy(
                username     = profile.username,
                token        = profile.accessToken,
                uuid         = profile.uuid.toString(),
                refreshToken = profile.refreshToken
            )
        } catch (_: Exception) { null }
    }
}