package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import kotlin.random.Random

object KnockbackDisplacement : Module(
    "Knockback Displacement",
    "Silently flicks rotation on each attack to displace knockback sideways",
    Category.COMBAT
) {

    enum class FlickMode { LEFT, RIGHT, RANDOM }

    private val flickAngle    = floatRange("flick angle", 60f to 85f, 10f, 180f)
    private val flickMode     = enum("flick mode", FlickMode.RIGHT)
    private val requireSprint = boolean("require sprint", true)

    @JvmField
    var skipIntercept = false

    private var rotationHeld = false
    private var pendingAttack: (() -> Unit)? = null

    override fun onEnabled() {
        rotationHeld = false
        skipIntercept = false
        pendingAttack = null
    }

    override fun onDisabled() {
        if (rotationHeld) {
            RotationManager.clearRotation()
            rotationHeld = false
        }
        skipIntercept = false
        pendingAttack = null
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register {
            if (!isEnabled()) return@register

            // Fire the deferred attack from the previous tick BEFORE sendPosition runs.
            // The server already has the flicked yaw from last tick's position packet.
            val attack = pendingAttack
            if (attack != null) {
                pendingAttack = null
                // Tick N+1: push flick the rest of the way (handles any sub-count remainder
                // left from tick N) so the interact packet goes out at the exact target yaw.
                RotationManager.flickTick()
                skipIntercept = true
                attack()
                skipIntercept = false
            }

            // Release the flick rotation so this tick's sendPosition goes out with normal yaw.
            if (rotationHeld) {
                RotationManager.clearRotation()
                rotationHeld = false
            }
        }
    }

    @JvmStatic
    fun scheduleAttack(player: LocalPlayer, target: LivingEntity, gameMode: MultiPlayerGameMode): Boolean {
        if (requireSprint.value && !player.isSprinting) return false

        val side = when (flickMode.value) {
            FlickMode.LEFT   -> -1f
            FlickMode.RIGHT  ->  1f
            FlickMode.RANDOM -> if (Random.nextBoolean()) -1f else 1f
        }
        val (lo, hi) = flickAngle.value
        val angle = if (hi > lo) lo + Random.nextFloat() * (hi - lo) else lo
        val flickYaw = player.yRot + side * angle

        RotationManager.movementMode = RotationManager.MovementMode.SERVER
        RotationManager.setTargetRotation(flickYaw, player.xRot)
        RotationManager.flickTick()
        rotationHeld = true

        // Defer attack to the start of next tick so it fires BEFORE sendPosition.
        pendingAttack = { gameMode.attack(player, target) }

        return true
    }
}
