package me.ghluka.medved.update

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.loader.api.FabricLoader
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.net.URI
import java.net.URL
import java.util.Properties

object UpdateChecker {

    private const val PROPERTIES_URL =
        "https://raw.githubusercontent.com/ghluka/MedvedClient/refs/heads/main/gradle.properties"
    private const val RELEASES_URL =
        "https://github.com/ghluka/MedvedClient/releases/latest"

    private enum class State { WAITING, SCHEDULED, FETCHING, DONE }

    @Volatile private var state = State.WAITING
    private var waitUntil = 0L

    fun init() {
        ClientTickEvents.END_CLIENT_TICK.register { client ->
            if (state == State.DONE) return@register
            client.player ?: return@register

            when (state) {
                State.WAITING -> {
                    waitUntil = System.currentTimeMillis() + 2500
                    state = State.SCHEDULED
                }
                State.SCHEDULED -> {
                    if (System.currentTimeMillis() >= waitUntil) {
                        state = State.FETCHING
                        Thread {
                            runCatching {
                                val currentVersion = FabricLoader.getInstance()
                                    .getModContainer("medved")
                                    .map { it.metadata.version.friendlyString.trim().toInt() }
                                    .orElse(0)

                                val content = URL(PROPERTIES_URL).readText(Charsets.UTF_8)
                                val props = Properties().apply { load(content.byteInputStream()) }
                                val latestVersion = props.getProperty("mod_version").trim().toInt()

                                if (latestVersion > currentVersion) {
                                    client.execute {
                                        val player = client.player ?: return@execute
                                        val prefix = "§c[§6M§c] Medved§f: "

                                        player.sendSystemMessage(
                                            Component.literal("${prefix}§fYou're using an outdated version of this mod!")
                                        )
                                        player.sendSystemMessage(
                                            Component.literal("${prefix}§fAn update is available from §7b$currentVersion§f to §7b$latestVersion§f!")
                                        )
                                        player.sendSystemMessage(
                                            Component.literal(prefix).append(
                                                Component.literal("§c§l[§6§lCLICK HERE TO UPDATE§c§l]").withStyle(
                                                    Style.EMPTY.withClickEvent(
                                                        ClickEvent.OpenUrl(URI(RELEASES_URL))
                                                    )
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                            state = State.DONE
                        }.also { it.isDaemon = true }.start()
                    }
                }
                else -> {}
            }
        }
    }
}
