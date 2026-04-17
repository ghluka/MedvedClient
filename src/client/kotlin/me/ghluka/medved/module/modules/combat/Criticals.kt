package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.Entity

object Criticals : Module("Criticals", "Forces critical hits on attacks", Category.COMBAT) {
    enum class Mode {
        NO_GROUND, PACKET, JUMP
    }

    val mode = enum("mode", Mode.NO_GROUND)
    @JvmField var cachedOnGround: Boolean = false
    private var lastCrit: Long = 0L

    override fun hudInfo(): String = mode.value.name.lowercase().replace("_", " ")

    fun onAttack(target: Entity) {
        if (!isEnabled()) return
        val client = Minecraft.getInstance()
        val player = client.player ?: return

        if (!player.onGround() && mode.value != Mode.NO_GROUND) return
        if (player.isInWater || player.isInLava || player.onClimbable()) return 

        if (System.currentTimeMillis() - lastCrit < 500 && mode.value != Mode.NO_GROUND) {
            return
        }

        val connection = player.connection
        val x = player.x
        val y = player.y
        val z = player.z
        val hc = player.horizontalCollision

        when (mode.value) {
            Mode.PACKET -> {
                connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, true, hc))
                connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, false, hc))
                connection.send(ServerboundMovePlayerPacket.Pos(x, y + 1.1e-5, z, false, hc))
                connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, false, hc))
                lastCrit = System.currentTimeMillis()
            }
            //Mode.NCP -> {
            //    connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.11, z, false, hc))
            //    connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.1100013579, z, false, hc))
            //    connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0000013579, z, false, hc))
            //    //connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, false, hc))
            //    lastCrit = System.currentTimeMillis()
            //}
            Mode.JUMP -> {
                player.jumpFromGround()
                lastCrit = System.currentTimeMillis()
            }
            Mode.NO_GROUND -> {
            }
        }
    }
}
