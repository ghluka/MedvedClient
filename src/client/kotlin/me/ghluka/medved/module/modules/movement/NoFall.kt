package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket

object NoFall : Module("No Fall", "Prevents taking fall damage", Category.MOVEMENT) {
    enum class Mode {
        PACKET, SPOOF, DISTANCE
    }

    val mode = enum("mode", Mode.PACKET)
    @JvmField var cachedOnGround: Boolean = false

    override fun hudInfo(): String = mode.value.name.lowercase()

    init {
        ClientTickEvents.END_CLIENT_TICK.register { mc ->
            val player = mc.player ?: return@register
            if (!isEnabled()) return@register
            onTick(player)
        }
    }

    private fun onTick(player: LocalPlayer) {
        if (player.abilities.invulnerable || player.isFallFlying) return

        val fd = player.fallDistance
        var shouldProtect = false

        when (mode.value) {
            Mode.PACKET -> {
                if (fd > 2.5f) {
                    shouldProtect = true
                }
            }
            Mode.SPOOF -> {
                if (fd > 1.5f && player.deltaMovement.y < -0.5) {
                    shouldProtect = true
                }
            }
            Mode.DISTANCE -> {
                if (fd > 0.0f && (fd % 2.5f < 0.25f) && player.deltaMovement.y < -0.1) {
                    shouldProtect = true
                }
            }
        }

        if (shouldProtect) {
            player.connection.send(ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision))
            if (mode.value == Mode.PACKET) {
                player.fallDistance = 0.0
            }
        }
    }
}