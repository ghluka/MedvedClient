package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
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
    enum class Mode {
        BLATANT,
        LEGIT
    }

    private val mode          = enum("mode", Mode.LEGIT)
    private val flickAngle    = floatRange("flick angle", 60f to 85f, 10f, 180f)
    private val flickMode     = enum("flick mode", FlickMode.RIGHT)
    private val requireSprint = boolean("require sprint", true)
    private val clearBefore   = boolean("clear before hit", true)

    @JvmField
    var skipIntercept = false

    @JvmField
    var skipInterceptNextVanillaAttack = false

    private var rotationHeld = false
    private var hitHeld = false
    private var pendingAttack: (() -> Unit)? = null

    override fun onEnabled() {
        rotationHeld = false
        hitHeld = false
        skipIntercept = false
        pendingAttack = null
    }

    override fun onDisabled() {
        if (rotationHeld) {
            RotationManager.clearRotation()
            rotationHeld = false
        }
        hitHeld = false
        skipIntercept = false
        skipInterceptNextVanillaAttack = false
        pendingAttack = null
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register {
            if (!isEnabled()) return@register

            // Fire the deferred attack from the previous tick BEFORE sendPosition runs.
            // The server already has the flicked yaw from last tick's position packet.
            val attack = pendingAttack
            if (attack != null && hitHeld) {
                pendingAttack = null
                skipIntercept = true
                attack()
                if (!skipInterceptNextVanillaAttack) {
                    skipIntercept = false
                }
            }
            hitHeld = false
            if (attack != null) {
                pendingAttack = null
                // Tick N+1: push flick the rest of the way (handles any sub-count remainder
                // left from tick N) so the interact packet goes out at the exact target yaw.
                RotationManager.flickTick()
                skipIntercept = true
                if (!clearBefore.value) {
                    attack()
                }
                else {
                    hitHeld = true
                    if (rotationHeld) {
                        RotationManager.clearRotation()
                        rotationHeld = false
                    }
                    return@register
                }
                if (!skipInterceptNextVanillaAttack) {
                    skipIntercept = false
                }
            }

            // Reset the inter-tick flag once the vanilla attack has been consumed.
            // (Set back to false by the mixin in medved$onAttack when it sees the flag.)
            if (!skipInterceptNextVanillaAttack) {
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

        val isLegit = mode.value == Mode.LEGIT

        RotationManager.movementMode = RotationManager.MovementMode.SERVER
        RotationManager.rotationMode  = RotationManager.RotationMode.SERVER
        RotationManager.setTargetRotation(flickYaw, player.xRot)
        RotationManager.physicsYawOverride = flickYaw
        RotationManager.skipPositionSnap = true
        RotationManager.flickTick()
        rotationHeld = true

        // Defer attack to the start of next tick so it fires BEFORE sendPosition.
        pendingAttack = if (isLegit) {
            {
                skipInterceptNextVanillaAttack = true
                val mc = Minecraft.getInstance()
                KeyMapping.click(InputConstants.getKey(mc.options.keyAttack.saveString()))
            }
        } else {
            { gameMode.attack(player, target) }
        }

        return true
    }
}
