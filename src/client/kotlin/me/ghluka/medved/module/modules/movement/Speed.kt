package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.phys.Vec3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

object Speed : Module("Speed", "Increases your movement speed", Category.MOVEMENT) {

    enum class Mode {
        ON_GROUND, BHOP, LOWHOP
    }

    @JvmField val mode = enum("mode", Mode.ON_GROUND)
    private val speedMult = float("multiplier", 1.2f, 1.0f, 3.0f)

    //override fun onLevelRender(ctx: LevelRenderContext) {
    //    val client = Minecraft.getInstance()
    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        
        val m = mode.value
        val isMoving = player.input.moveVector.x != 0f || player.input.moveVector.y != 0f

        if (!isMoving) {
            player.setDeltaMovement(0.0, player.deltaMovement.y, 0.0)
            return
        }

        val baseSpeed = 0.22 * speedMult.value

        when (m) {
            Mode.ON_GROUND -> {
                if (player.onGround()) {
                    setMovementSpeed(player, baseSpeed)
                }
            }
            Mode.BHOP -> {
                if (player.onGround()) {
                    player.jumpFromGround()
                    setMovementSpeed(player, baseSpeed * 1.5)
                } else {
                    setMovementSpeed(player, baseSpeed)
                }
            }
            Mode.LOWHOP -> {
                if (player.onGround()) {
                    val vec3 = player.deltaMovement
                    player.setDeltaMovement(vec3.x, 0.3, vec3.z)
                    setMovementSpeed(player, baseSpeed * 1.5)
                } else {
                    setMovementSpeed(player, baseSpeed)
                    if (player.deltaMovement.y < 0) {
                        player.setDeltaMovement(player.deltaMovement.x, player.deltaMovement.y * 1.2, player.deltaMovement.z)
                    }
                }
            }
            else -> {}
        }
    }

    private fun setMovementSpeed(player: LocalPlayer, speed: Double) {
        var yaw = player.yRot
        var forward = player.input.moveVector.y
        var strafe = player.input.moveVector.x

        if (forward == 0f && strafe == 0f) {
            player.setDeltaMovement(0.0, player.deltaMovement.y, 0.0)
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
        player.setDeltaMovement(0.0, player.deltaMovement.y, 0.0)
    }

    override fun hudInfo(): String = mode.value.name.replace("_", " ")
}
