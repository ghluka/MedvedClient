package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.CameraOverriddenEntity
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.MultiPlayerGameMode
import net.minecraft.client.player.LocalPlayer
import net.minecraft.world.entity.LivingEntity
import kotlin.random.Random

object KnockbackDisplacement : Module(
    "Hit Flick",
    "Silently flicks rotation on each attack to displace knockback sideways",
    Category.COMBAT
) {

    enum class FlickMode { LEFT, RIGHT, RANDOM }

    private val flickAngle    = floatRange("flick angle", 85f to 95f, 10f, 180f)
    private val flickMode     = enum("flick mode", FlickMode.RIGHT)
    private val requireSprint = boolean("require sprint", true)

    @JvmField
    var skipIntercept = false

    @JvmField
    var skipInterceptNextVanillaAttack = false

    var testYaw = 0f

    var rotationHeld = false
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
        skipInterceptNextVanillaAttack = false
        pendingAttack = null
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register {
            if (!isEnabled()) return@register

            val attack = pendingAttack
            if (attack != null) {
                pendingAttack = null
                skipIntercept = true
                attack()
                if (!skipInterceptNextVanillaAttack) {
                    skipIntercept = false
                }
            }

            if (!skipInterceptNextVanillaAttack) {
                skipIntercept = false
            }

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
        val baseYaw = player.yRot
        val flickYaw = baseYaw + side * angle

        rotationHeld = true
        RotationManager.perspective = true
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode  = RotationManager.RotationMode.CLIENT
        testYaw = baseYaw
        RotationManager.setTargetRotation(flickYaw, player.xRot)
        //RotationManager.physicsYawOverride = flickYaw
        //RotationManager.skipPositionSnap = true
        RotationManager.flickTick()

        // Defer attack into sendPosition so it fires while the rotation override is active.
        pendingAttack = {
            skipInterceptNextVanillaAttack = true
            val mc = Minecraft.getInstance()
            KeyMapping.click(InputConstants.getKey(mc.options.keyAttack.saveString()))
        }

        return true
    }
}
