package me.ghluka.medved.module.modules.anarchy

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.combat.KnockbackDisplacement
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.module.modules.world.BedBreaker
import me.ghluka.medved.module.modules.world.ChestAura
import me.ghluka.medved.module.modules.world.Clutch
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.TridentItem
import kotlin.math.atan2
import kotlin.math.sqrt

object KillAura : Module(
    name = "Kill Aura",
    description = "Automatically attacks enemies in your range",
    category = Category.ANARCHY
) {
    enum class Mode {
        APS, SEQUENTIAL
    }

    enum class TargetMode {
        SINGLE, SWITCH, MULTI
    }

    private val mode = enum("mode", Mode.APS)
    private val targetMode = enum("targeting", TargetMode.SWITCH)

    private val range = float("range", 6.0f, 1.0f, 8.0f)
    private val aps = int("aps", 20, 1, 40).also {
        it.visibleWhen = { mode.value == Mode.APS }
    }
    private val rotate = boolean("rotate", true)
    private val playersOnly = boolean("players only", true)
    private val autoWeapon = boolean("auto weapon", false)

    private var target: LivingEntity? = null
    private var accumulator = 0.0f

    override fun onEnabled() {
        accumulator = 0.0f
        target = null
    }

    override fun onDisabled() {
        if (!KnockbackDisplacement.rotationHeld) {
            RotationManager.clearRotation()
        }
        target = null
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register {
            if (!isEnabled()) return@register
            val client = Minecraft.getInstance()
            val player = client.player ?: return@register
            val level = client.level ?: return@register
            if (client.screen != null) return@register

            val maxRange = range.value.toDouble()

            val candidates = level.entitiesForRendering()
                .filterIsInstance<LivingEntity>()
                .filter { e ->
                    e !== player && !e.isDeadOrDying &&
                            !(playersOnly.value && e !is Player) &&
                            player.distanceTo(e) <= maxRange &&
                            TargetFilter.isValidTarget(player, e)
                }

            val bestTarget = candidates.minByOrNull { player.distanceTo(it) }

            if (targetMode.value == TargetMode.SINGLE && target != null && candidates.contains(target)) {
                // Keep target
            } else {
                target = bestTarget
            }

            if (target == null) {
                if (!KnockbackDisplacement.rotationHeld) {
                    RotationManager.clearRotation()
                }
                return@register
            }

            if (autoWeapon.value) {
                val bestSlot = findBestWeapon(player)
                if (bestSlot != -1 && player.inventory.selectedSlot != bestSlot) {
                    player.inventory.selectedSlot = bestSlot
                }
            }

            when (mode.value) {
                Mode.APS -> {
                    accumulator += aps.value / 20.0f
                    while (accumulator >= 1.0f) {
                        accumulator -= 1.0f
                        if (targetMode.value == TargetMode.MULTI) {
                            for (c in candidates) {
                                client.gameMode?.attack(player, c)
                            }
                        } else {
                            client.gameMode?.attack(player, target!!)
                        }
                        player.swing(InteractionHand.MAIN_HAND)
                    }
                }

                Mode.SEQUENTIAL -> {
                    if (player.getAttackStrengthScale(0.5f) >= 1.0f) {
                        client.gameMode?.attack(player, target!!)
                        player.swing(InteractionHand.MAIN_HAND)
                    }
                }
            }
        }
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val player = Minecraft.getInstance().player ?: return
        val currentTarget = target ?: return

        if (!rotate.value) {
            if (!KnockbackDisplacement.rotationHeld) {
                RotationManager.clearRotation()
            }
            return
        }

        if (Scaffold.isEnabled() ||
            KnockbackDisplacement.rotationHeld ||
            (BedBreaker.isEnabled() && BedBreaker.pendingHitPos != null) ||
            (ChestAura.isEnabled() && RotationManager.isActive()) ||
            (Clutch.isEnabled() && RotationManager.isActive())) {
            return
        }

        val aimY = currentTarget.y + currentTarget.bbHeight * 0.5
        val dx = currentTarget.x - player.x
        val dy = aimY - player.eyeY
        val dz = currentTarget.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz)
        val targetYaw   = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val targetPitch = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()

        RotationManager.perspective = false
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode  = RotationManager.RotationMode.SERVER

        RotationManager.setTargetRotation(targetYaw, targetPitch)
        RotationManager.quickTick(200f)
    }

    private fun findBestWeapon(player: Player): Int {
        for (i in 0..8) {
            val itemStack = player.inventory.getItem(i)
            if (itemStack.isEmpty) continue
            if (itemStack.`is`(ItemTags.SWORDS) || itemStack.`is`(ItemTags.AXES) || itemStack.item is TridentItem) {
                return i
            }
        }
        return -1
    }

    override fun hudInfo(): String {
        return when (mode.value) {
            Mode.APS -> "${aps.value} aps"
            Mode.SEQUENTIAL -> "sequential"
        }
    }
}