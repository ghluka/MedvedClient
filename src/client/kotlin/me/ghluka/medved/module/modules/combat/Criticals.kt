package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.entity.Entity

object Criticals : Module("Criticals", "Forces critical hits on attacks", Category.COMBAT) {
    enum class Mode {
        NO_GROUND, PACKET, JUMP
    }

    enum class PacketMode {
        VANILLA, NCP, FALLING, LOW, DOWN, GRIM, BLOCKSMC
    }

    val mode = enum("mode", Mode.NO_GROUND)
    
    val packetMode = enum("packet mode", PacketMode.NCP).also {
        it.visibleWhen = { mode.value == Mode.PACKET }
    }

    //val jumpHeight = float("jump height", 0.42f, 0.1f, 0.42f).also {
    //    it.visibleWhen = { mode.value == Mode.JUMP }
    //}

    val jumpRange = float("jump range", 4.0f, 1.0f, 6.0f).also {
        it.visibleWhen = { mode.value == Mode.JUMP }
    }

    @JvmField var cachedOnGround: Boolean = false
    private var lastCrit: Long = 0L
    private var adjustNextJump: Boolean = false

    override fun hudInfo(): String = mode.value.name.lowercase().replace("_", " ")

    override fun onTick(client: Minecraft) {
        if (!isEnabled()) return
        if (mode.value != Mode.JUMP) return
        
        val player = client.player ?: return
        val level = client.level ?: return

        val hasEnemy = level.entitiesForRendering()
            .filterIsInstance<net.minecraft.world.entity.LivingEntity>()
            .any { it != player && it.isAlive && player.distanceTo(it) <= jumpRange.value }

        if (hasEnemy) {
            client.options.keyJump.setDown(true)
            adjustNextJump = true
        } else if (adjustNextJump) {
            client.options.keyJump.setDown(false)
            adjustNextJump = false
        }

        //if (player.deltaMovement.y > 0.0 && jumpHeight.value != 0.42f && adjustNextJump) {
        //     if (player.deltaMovement.y.toFloat() == 0.42f) {
        //         player.deltaMovement = net.minecraft.world.phys.Vec3(player.deltaMovement.x, jumpHeight.value.toDouble(), player.deltaMovement.z)
        //     }
        //}
    }

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
                val onGroundState = false // most default to false
                when (packetMode.value) {
                    PacketMode.VANILLA -> {
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.2, z, onGroundState, hc))
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.01, z, onGroundState, hc))
                    }
                    PacketMode.NCP -> {
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.11, z, onGroundState, hc))
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.1100013579, z, onGroundState, hc))
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0000013579, z, onGroundState, hc))
                    }
                    PacketMode.FALLING -> {
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0625, z, onGroundState, hc))
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0625013579, z, onGroundState, hc))
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0000013579, z, onGroundState, hc))
                    }
                    PacketMode.LOW -> {
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y + 1e-9, z, onGroundState, hc))
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, onGroundState, hc))
                    }
                    PacketMode.DOWN -> {
                        connection.send(ServerboundMovePlayerPacket.Pos(x, y - 1e-9, z, onGroundState, hc))
                    }
                    PacketMode.GRIM -> {
                        if (!player.onGround()) {
                            connection.send(ServerboundMovePlayerPacket.Pos(x, y - 0.000001, z, onGroundState, hc))
                        }
                    }
                    PacketMode.BLOCKSMC -> {
                        if (player.tickCount % 4 == 0) {
                            connection.send(ServerboundMovePlayerPacket.Pos(x, y + 0.0011, z, true, hc))
                            connection.send(ServerboundMovePlayerPacket.Pos(x, y, z, onGroundState, hc))
                        }
                    }
                }
                lastCrit = System.currentTimeMillis()
            }
            Mode.JUMP -> {
                if (player.onGround() && !adjustNextJump) {
                    client.options.keyJump.setDown(true)
                    adjustNextJump = true
                }
                lastCrit = System.currentTimeMillis()
            }
            Mode.NO_GROUND -> {
            }
        }
    }
}
