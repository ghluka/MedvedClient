package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

object ChestAura : Module(
    "Chest Aura",
    "Automatically opens nearby chests with legit rotations.",
    Category.WORLD
) {
    private val rotationSpeed = float("rotation speed", 200f, 50f, 500f)
    private val xray = boolean("click through walls", false)
    private val drillSwap = boolean("drill swap", false)
    private val drillName = string("drill name", "gemstone drill")

    private var closestChest: BlockPos? = null
    private var lastOpenTime = 0L
    private val solved = mutableSetOf<BlockPos>()

    override fun onTick(client: Minecraft) {
        val player = client.player as? LocalPlayer ?: return
        val world = client.level ?: return
        if (!isEnabled()) return

        val maxReach = 3.0 * 3.0
        if (closestChest == null || player.distanceToSqr(Vec3(closestChest!!.x + 0.5, closestChest!!.y + 0.5, closestChest!!.z + 0.5)) > maxReach) {
            closestChest = findClosestChestInRange(player, world, maxReach)
            if (closestChest == null) {
                RotationManager.clearRotation()
                RotationManager.physicsYawOverride = Float.NaN
                return
            }
        }
        val chest = closestChest ?: return

        if (player.distanceToSqr(Vec3(chest.x + 0.5, chest.y + 0.5, chest.z + 0.5)) > maxReach) {
            closestChest = null
            RotationManager.clearRotation()
            RotationManager.physicsYawOverride = Float.NaN
            return
        }

        if (!xray.value && !canSeeChest(player, chest)) {
            closestChest = null
            RotationManager.clearRotation()
            RotationManager.physicsYawOverride = Float.NaN
            return
        }

        RotationManager.rotationMode = RotationManager.RotationMode.SERVER
        RotationManager.movementMode = RotationManager.MovementMode.SERVER
        val (yaw, pitch) = getRotationTo(player, chest)
        RotationManager.setTargetRotation(yaw, pitch)
        RotationManager.quickTick(rotationSpeed.value)
        RotationManager.physicsYawOverride = RotationManager.getCurrentYaw()
        RotationManager.skipPositionSnap = true

        if (isLookingAtChest(player, chest)) {
            clickChest(client, player, chest)
            RotationManager.clearRotation()
            RotationManager.physicsYawOverride = Float.NaN
        }
    }

    private fun findClosestChestInRange(player: LocalPlayer, world: net.minecraft.client.multiplayer.ClientLevel, maxDist: Double): BlockPos? {
        val px = floor(player.x).toInt()
        val py = floor(player.y).toInt()
        val pz = floor(player.z).toInt()
        var minDist = Double.MAX_VALUE
        var best: BlockPos? = null
        for (dx in -4..4) for (dy in -4..4) for (dz in -4..4) {
            val pos = BlockPos(px + dx, py + dy, pz + dz)
            val state = world.getBlockState(pos)
            if (state.block == Blocks.CHEST && !solved.contains(pos)) {
                val dist = player.distanceToSqr(Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5))
                if (dist < minDist && dist <= maxDist) {
                    if (!xray.value && !canSeeChest(player, pos)) continue
                    minDist = dist
                    best = pos
                }
            }
        }
        return best
    }

    private fun canSeeChest(player: LocalPlayer, chest: BlockPos): Boolean {
        val eyeYaw = player.getYRot()
        val eyePitch = player.getXRot()
        val (yaw, pitch) = getRotationTo(player, chest)
        player.setYRot(yaw)
        player.setXRot(pitch)
        val dist = sqrt(
            (chest.x + 0.5 - player.x) * (chest.x + 0.5 - player.x) +
            (chest.y + 0.5 - (player.y + player.eyeHeight)) * (chest.y + 0.5 - (player.y + player.eyeHeight)) +
            (chest.z + 0.5 - player.z) * (chest.z + 0.5 - player.z)
        ).toDouble()
        val hitResult = player.pick(dist.coerceAtMost(3.0), 1.0f, false)
        player.setYRot(eyeYaw)
        player.setXRot(eyePitch)
        val bhr = hitResult as? BlockHitResult ?: return false
        return bhr.blockPos == chest
    }

    private fun getRotationTo(player: LocalPlayer, chest: BlockPos): Pair<Float, Float> {
        val dx = chest.x + 0.5 - player.x
        val dy = chest.y + 0.5 - (player.y + player.eyeHeight)
        val dz = chest.z + 0.5 - player.z
        val dist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
        val pitch = Math.toDegrees(kotlin.math.atan2(-dy, dist)).toFloat()
        return yaw to pitch.coerceIn(-90f, 90f)
    }

    private fun findDrillSlot(player: LocalPlayer): Int {
        val needle = drillName.value.lowercase()
        for (slot in 0..8) {
            val stack = player.inventory.getItem(slot)
            if (stack.isEmpty) continue
            val name = stack.hoverName.string.lowercase()
            if (name.contains(needle)) return slot
        }
        return -1
    }

    private fun isLookingAtChest(player: LocalPlayer, chest: BlockPos): Boolean {
        val (yaw, pitch) = getRotationTo(player, chest)
        val cyaw = RotationManager.getCurrentYaw()
        val cpitch = RotationManager.getCurrentPitch()
        return abs(yaw - cyaw) < 2f && abs(pitch - cpitch) < 2f
    }

    private fun clickChest(client: Minecraft, player: LocalPlayer, chest: BlockPos) {
        if (System.currentTimeMillis() - lastOpenTime < 200L) return // debounce: 200ms

        val previousSlot = player.inventory.selectedSlot
        var swapped = false
        if (drillSwap.value) {
            val drillSlot = findDrillSlot(player)
            if (drillSlot != -1 && drillSlot != previousSlot) {
                player.inventory.setSelectedSlot(drillSlot)
                swapped = true
            }
        }

        val mc = client
        val hitVec = Vec3(chest.x + 0.5, chest.y + 0.5, chest.z + 0.5)
        val facing = Direction.UP
        mc.gameMode?.useItemOn(player, player.usedItemHand, BlockHitResult(hitVec, facing, chest, false))

        if (swapped) {
            player.inventory.setSelectedSlot(previousSlot)
        }

        solved.add(chest)
        lastOpenTime = System.currentTimeMillis()
        closestChest = null
    }

    override fun onDisabled() {
        closestChest = null
        solved.clear()
        RotationManager.clearRotation()
        RotationManager.physicsYawOverride = Float.NaN
    }
}
