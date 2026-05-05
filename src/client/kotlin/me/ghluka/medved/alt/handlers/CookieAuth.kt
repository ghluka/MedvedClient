package me.ghluka.medved.alt.handlers

import com.google.gson.JsonParser
import me.ghluka.medved.util.HttpUtils
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.UUID
import kotlin.collections.iterator

object CookieAuth {

    private const val XBL_URL              = "https://user.auth.xboxlive.com/user/authenticate"
    private const val XSTS_URL             = "https://xsts.auth.xboxlive.com/xsts/authorize"
    private const val MC_LOGIN_URL         = "https://api.minecraftservices.com/authentication/login_with_xbox"
    private const val MC_PROFILE_URL       = "https://api.minecraftservices.com/minecraft/profile"
    private const val OAUTH_URL            = "https://login.live.com/oauth20_authorize.srf?redirect_uri=https://sisu.xboxlive.com/connect/oauth/XboxLive&response_type=token&client_id=000000004420578E&scope=XboxLive.Signin%20XboxLive.offline_access"

    data class McProfile(
        val username: String,
        val uuid: UUID,
        val accessToken: String
    )

    fun authenticateFromCookieFile(cookieFile: File): McProfile? {
        return try {
            val cookies = parseCookiesFromFile(cookieFile)
            if (cookies.isEmpty()) {
                System.err.println("[CookieAuth] No valid cookies found")
                return null
            }

            val accessToken = getAccessTokenFromCookie(cookies) ?: run {
                System.err.println("[CookieAuth] Failed to get access token from cookies")
                return null
            }

            exchangeForProfile(accessToken)
        } catch (e: Exception) {
            System.err.println("[CookieAuth] Authentication failed: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private fun parseCookiesFromFile(cookieFile: File): Map<String, String> {
        val cookies = linkedMapOf<String, String>()

        cookieFile.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()

                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue

                val parts = trimmed.split("\t")
                if (parts.size < 7) continue

                val domain = parts[0].lowercase()
                val cookieName = parts[5].trim()
                val cookieValue = if (parts.size > 6) parts[6].trim() else ""

                if (!domain.contains("login.live.com") &&
                    domain != ".live.com" &&
                    domain != "login.live.com" &&
                    domain != ".account.microsoft.com") continue

                if (cookieValue.isEmpty() || cookieValue == "Disabled") continue

                if (!cookies.containsKey(cookieName)) {
                    cookies[cookieName] = cookieValue
                }
            }
        }

        return cookies
    }

    private fun buildCookieHeader(cookies: Map<String, String>, useJSH: Boolean): String {
        val cookieOrder = mutableListOf(
            if (useJSH) "JSH" else "JSHP",
            "MSPAuth", "MSPBack", "MSPProf", "MSPRequ", "MSPSoftVis", "NAP", "OParams", "PPLState", "WLSSC"
        )

        for ((name, _) in cookies) {
            if (!cookieOrder.contains(name) &&
                (name == "__Host-MSAAUTH" || name.startsWith("__Host-") || name == "uaid")) {
                cookieOrder.add(name)
            }
        }

        return cookieOrder
            .filter { cookies.containsKey(it) }
            .joinToString("; ") { "${it}=${cookies[it]}" }
    }

    private fun getAccessTokenFromCookie(cookies: Map<String, String>): String? {
        // Try JSHP first, then JSH
        val useJSH = !cookies.containsKey("JSHP")
        val cookieHeader = buildCookieHeader(cookies, useJSH)

        val conn = URL(OAUTH_URL).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("Host", "login.live.com")
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            conn.setRequestProperty("Cookie", cookieHeader)
            conn.setRequestProperty("Accept-Encoding", "gzip")
            conn.setRequestProperty("Connection", "keep-alive")
            conn.setRequestProperty("Accept", "*/*")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.instanceFollowRedirects = false
            conn.connect()

            val responseCode = conn.responseCode
            val location = conn.getHeaderField("Location")

            if (responseCode == 302 && location != null && location.contains("#access_token=")) {
                val fragment = location.split("#", limit = 2)[1]
                val params = mutableMapOf<String, String>()

                for (param in fragment.split("&")) {
                    if (param.contains("=")) {
                        val (k, v) = param.split("=", limit = 2)
                        params[k] = v
                    }
                }

                val accessToken = params["access_token"]
                if (accessToken != null) {
                    return URLDecoder.decode(accessToken, "UTF-8")
                }
            }

            return null
        } finally {
            conn.disconnect()
        }
    }

    private fun exchangeForProfile(msAccessToken: String): McProfile {
        val xblBody = """{"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com","RpsTicket":"d=$msAccessToken"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}"""
        val xblObj = JsonParser.parseString(HttpUtils.httpPost(XBL_URL, xblBody, "application/json")).asJsonObject
        val xblToken = xblObj["Token"].asString
        val uhs = xblObj["DisplayClaims"].asJsonObject["xui"].asJsonArray[0].asJsonObject["uhs"].asString

        val xstsBody = """{"Properties":{"SandboxId":"RETAIL","UserTokens":["$xblToken"]},"RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}"""
        val xstsToken = JsonParser.parseString(HttpUtils.httpPost(XSTS_URL, xstsBody, "application/json")).asJsonObject["Token"].asString

        val mcBody = """{"identityToken":"XBL3.0 x=$uhs;$xstsToken"}"""
        val mcToken = JsonParser.parseString(HttpUtils.httpPost(MC_LOGIN_URL, mcBody, "application/json")).asJsonObject["access_token"].asString

        val profileObj = JsonParser.parseString(HttpUtils.httpGet(MC_PROFILE_URL, mapOf("Authorization" to "Bearer $mcToken"))).asJsonObject
        val name = profileObj["name"].asString
        val raw = profileObj["id"].asString
        val uuid = UUID.fromString("${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}")

        return McProfile(name, uuid, mcToken)
    }
}