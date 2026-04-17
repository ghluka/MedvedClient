package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin

object Flight : Module("Flight", "Allows you to fly or glide in the air", Category.MOVEMENT) {

    enum class Mode {
        VANILLA, CREATIVE, AIR_HOP, GLIDE
    }

    enum class AntiKickMode {
        LATEST, LEGACY
    }

    @JvmField val mode = enum("mode", Mode.VANILLA)
    private val flightSpeed = float("speed", 1.0f, 0.1f, 5.0f).also {
        it.visibleWhen = { mode.value == Mode.VANILLA }
    }

    val antiKick = boolean("anti-kick", false)
    val antiKickMode = enum("anti-kick mode", AntiKickMode.LEGACY).also {
        it.visibleWhen = { antiKick.value }
    }
    
    val latestInterval = int("interval", 70, 5, 80).also {
        it.visibleWhen = { antiKick.value && antiKickMode.value == AntiKickMode.LATEST }
    }
    val latestDistance = float("distance", 0.035f, 0.01f, 0.2f).also {
        it.visibleWhen = { antiKick.value && antiKickMode.value == AntiKickMode.LATEST }
    }

    val oldInterval = int("interval", 30, 5, 80).also {
        it.visibleWhen = { antiKick.value && antiKickMode.value == AntiKickMode.LEGACY }
    }

    private var tickCounter = 0
    private var targetY = 0.0

    override fun onEnabled() {
        val player = Minecraft.getInstance().player ?: return
        targetY = player.y
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        
        when (mode.value) {
            Mode.CREATIVE -> {
                player.abilities.mayfly = true
                player.abilities.flying = true
            }
            Mode.VANILLA -> {
                player.abilities.flying = false
                
                var yVelocity = 0.0
                if (client.options.keyJump.isDown) yVelocity += flightSpeed.value * 0.5
                if (client.options.keyShift.isDown) yVelocity -= flightSpeed.value * 0.5

                player.setDeltaMovement(0.0, yVelocity, 0.0)
                setMovementSpeed(player, flightSpeed.value.toDouble() * 0.5)
            }
            Mode.GLIDE -> {
                val vec = player.deltaMovement
                if (vec.y < -0.05) {
                    player.setDeltaMovement(vec.x, -0.05, vec.z)
                }
            }
            Mode.AIR_HOP -> {
                if (client.options.keyShift.isDown) {
                    targetY = player.y
                } else if (client.options.keyJump.isDown) {
                    if (player.deltaMovement.y < 0.0 && !player.onGround()) {
                        player.jumpFromGround()
                    }
                    targetY = player.y
                } else {
                    if (!player.onGround() && player.y <= targetY && player.deltaMovement.y < 0.0) {
                        player.jumpFromGround()
                        targetY = player.y
                    }
                }
            }
        }

        if (antiKick.value) {
            when (antiKickMode.value) {
                AntiKickMode.LATEST -> doWurstAntiKick(player)
                AntiKickMode.LEGACY -> doOldAntiKick(player, client)
            }
        }
    }

    private fun doWurstAntiKick(player: LocalPlayer) {
        if (tickCounter > latestInterval.value + 1) {
            tickCounter = 0
        }

        val velocity = player.deltaMovement

        when (tickCounter) {
            0 -> {
                if (velocity.y <= -latestDistance.value) {
                    tickCounter = 2
                } else {
                    player.setDeltaMovement(velocity.x, -latestDistance.value.toDouble(), velocity.z)
                }
            }
            1 -> {
                player.setDeltaMovement(velocity.x, latestDistance.value.toDouble(), velocity.z)
            }
        }

        tickCounter++
    }

    private fun doOldAntiKick(player: LocalPlayer, client: Minecraft) {
        if (tickCounter > oldInterval.value) {
            tickCounter = 0
        }

        if (tickCounter == 0) {
            goToGround18(player, client)
        }

        tickCounter++
    }

    private fun goToGround18(player: LocalPlayer, client: Minecraft) {
        var step = 1.0
        val precision = 0.0625
        var flyHeight = 0.0

        val box = player.boundingBox.inflate(precision)

        while (flyHeight < player.y) {
            val nextBox = box.move(0.0, -flyHeight, 0.0)

            if (!client.level!!.noCollision(nextBox)) {
                if (step < precision) break
                flyHeight -= step
                step /= 2.0
            } else {
                flyHeight += step
            }
        }

        if (flyHeight > 300.0) return

        val minY = player.y - flyHeight
        if (minY <= 0.0) return

        var y = player.y
        while (y > minY) {
            y -= 8.0
            if (y < minY) y = minY
            player.connection.send(ServerboundMovePlayerPacket.Pos(Vec3(player.x, y, player.z), true, true))
        }

        y = minY
        while (y < player.y) {
            y += 8.0
            if (y > player.y) y = player.y
            player.connection.send(ServerboundMovePlayerPacket.Pos(Vec3(player.x, y, player.z), true, true))
        }
    }

    private fun setMovementSpeed(player: LocalPlayer, speed: Double) {
        var yaw = player.yRot
        var forward = player.input.moveVector.y
        var strafe = player.input.moveVector.x

        if (forward == 0f && strafe == 0f) {
            return
        }

        if (forward != 0f) {
            if (strafe > 0f) {
                yaw += (if (forward > 0f) -45 else 45).toFloat()
            } else if (strafe < 0f) {
                yaw += (if (forward > 0f) 45 else -45).toFloat()
            }
            strafe = 0f
            if (forward > 0f) {
                forward = 1f
            } else if (forward < 0f) {
                forward = -1f
            }
        }

        val rad = Math.toRadians((yaw + 90f).toDouble())
        val dx = forward * speed * cos(rad) + strafe * speed * sin(rad)
        val dz = forward * speed * sin(rad) - strafe * speed * cos(rad)

        player.setDeltaMovement(dx, player.deltaMovement.y, dz)
    }

    override fun onDisabled() {
        val player = Minecraft.getInstance().player ?: return

        if (!player.isCreative && !player.isSpectator) {
            player.abilities.mayfly = false
            player.abilities.flying = false
        }
    }

    override fun hudInfo(): String = mode.value.name.lowercase().replace("_", " ")
}
