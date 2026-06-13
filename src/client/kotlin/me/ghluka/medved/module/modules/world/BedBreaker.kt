package me.ghluka.medved.module.modules.world

import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.player.LocalPlayer
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.BlockTags
import net.minecraft.util.Mth
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.item.DyeColor
import net.minecraft.world.level.block.BedBlock
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.sqrt

object BedBreaker : Module(
    name = "Bed Breaker",
    description = "Automatically breaks enemy beds",
    category = Category.WORLD,
) {
    enum class BreakMode { LEGIT, BLATANT, HYPIXEL }
    enum class RotationMode { NONE, CLIENT, SERVER }

    private val breakMode    = enum("mode", BreakMode.LEGIT)
    private val rotationMode = enum("rotations", RotationMode.SERVER)
    private val range        = float("range", 4.5f, 1f, 6f).also {
        it.visibleWhen = { breakMode.value == BreakMode.LEGIT }
    }
    private val blatantRange = float("blatant range", 6f, 1f, 10f).also {
        it.visibleWhen = { breakMode.value != BreakMode.LEGIT }
    }
    private val autoTool = boolean("auto tool", true).also {
        it.visibleWhen = { breakMode.value != BreakMode.BLATANT }
    }
    private val ignoreOwnBed = boolean("ignore own bed", true)

    @JvmField var pendingHitPos:  BlockPos?  = null
    @JvmField var pendingHitFace: Direction  = Direction.UP

    private var savedSlot = -1
    private var breakingPos: BlockPos? = null
    private var breakingFace: Direction = Direction.UP
    private var activeBreakMode = breakMode.value

    override fun onEnabled() {
        RotationManager.clearRotation()
        pendingHitPos = null
        breakingPos   = null
        savedSlot     = -1
        activeBreakMode = breakMode.value
    }

    override fun onDisabled() {
        stopAndClean()
    }

    override fun hudInfo(): String = breakMode.value.name.lowercase()
        .replaceFirstChar { it.uppercase() }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            if (Scaffold.isEnabled()) return@register
            val player = client.player ?: return@register
            val level  = client.level  ?: return@register

            if (activeBreakMode != breakMode.value) {
                client.gameMode?.stopDestroyBlock()
                breakingPos = null
                restoreTool()
                activeBreakMode = breakMode.value
            }

            val effectiveRange = if (breakMode.value == BreakMode.LEGIT)
                range.value else blatantRange.value

            val bedPos = findNearestBed(player, level, effectiveRange)
            if (bedPos == null) {
                stopAndClean()
                return@register
            }

            val selectedTarget = when (breakMode.value) {
                BreakMode.LEGIT   -> findLegitTarget(player, level, bedPos)
                BreakMode.BLATANT -> bedPos to Direction.UP
                BreakMode.HYPIXEL   -> findShellTarget(player, level, bedPos)
            }
            val (targetPos, targetFace) = if (shouldKeepBreaking(player, level, effectiveRange, selectedTarget.first)) {
                breakingPos!! to breakingFace
            } else {
                selectedTarget
            }

            pendingHitPos  = targetPos
            pendingHitFace = targetFace

            when (rotationMode.value) {
                RotationMode.CLIENT -> {
                    val (yaw, pitch) = calcRotation(player, targetPos)
                    RotationManager.perspective = false
                    RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                    RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                    RotationManager.setTargetRotation(yaw, pitch)
                }
                RotationMode.SERVER -> {
                    val (yaw, pitch) = calcRotation(player, targetPos)
                    RotationManager.perspective = true
                    RotationManager.movementMode = RotationManager.MovementMode.CLIENT
                    RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
                    RotationManager.setTargetRotation(yaw, pitch)
                }
                RotationMode.NONE -> {
                    RotationManager.clearRotation()
                }
            }

            if (breakMode.value != BreakMode.BLATANT && autoTool.value) {
                val state = level.getBlockState(targetPos)
                val best  = findBestHotbarSlot(player, state)
                if (best != player.inventory.selectedSlot) {
                    if (savedSlot == -1) savedSlot = player.inventory.selectedSlot
                    player.inventory.selectedSlot = best
                }
            }

            val needsRestart = targetPos != breakingPos || targetFace != breakingFace
            if (needsRestart) {
                client.gameMode?.stopDestroyBlock()
                client.gameMode?.startDestroyBlock(targetPos, targetFace)
                breakingPos  = targetPos
                breakingFace = targetFace
            } else {
                client.gameMode?.continueDestroyBlock(targetPos, targetFace)
            }
            player.swing(net.minecraft.world.InteractionHand.MAIN_HAND)
        }
    }

    private fun stopAndClean() {
        val mc = Minecraft.getInstance()
        if (breakingPos != null) {
            mc.gameMode?.stopDestroyBlock()
            breakingPos = null
        }
        pendingHitPos = null
        restoreTool()
        RotationManager.clearRotation()
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        if (rotationMode.value != RotationMode.NONE && RotationManager.isActive()) {
            RotationManager.tick()
        }
    }

    private fun restoreTool() {
        if (savedSlot != -1) {
            Minecraft.getInstance().player?.inventory?.let { it.selectedSlot = savedSlot }
            savedSlot = -1
        }
    }

    private fun findNearestBed(player: LocalPlayer, level: ClientLevel, range: Float): BlockPos? {
        val eye = player.eyePosition
        val r   = range.toInt() + 2
        val base = player.blockPosition()
        val rangeSq = (range * range).toDouble()
        var bestSq  = rangeSq
        var bestPos: BlockPos? = null

        for (x in -r..r) for (y in -r..r) for (z in -r..r) {
            val pos = base.offset(x, y, z)
            if (!level.getBlockState(pos).`is`(BlockTags.BEDS)) continue
            if (ignoreOwnBed.value && isOwnBed(player, level.getBlockState(pos))) continue
            val center = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            val sq = eye.distanceToSqr(center)
            if (sq < bestSq) {
                bestSq  = sq
                bestPos = pos
            }
        }
        return bestPos
    }

    private fun shouldKeepBreaking(
        player: LocalPlayer,
        level: ClientLevel,
        effectiveRange: Float,
        selectedPos: BlockPos,
    ): Boolean {
        val pos = breakingPos ?: return false
        val state = level.getBlockState(pos)
        if (state.isAir) return false
        if (player.eyePosition.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) > effectiveRange * effectiveRange) {
            return false
        }
        if (level.getBlockState(selectedPos).`is`(BlockTags.BEDS) && !state.`is`(BlockTags.BEDS)) {
            return false
        }
        return state.`is`(BlockTags.BEDS) || isDefenseBlock(state)
    }

    private fun findLegitTarget(player: LocalPlayer, level: ClientLevel, bedPos: BlockPos): Pair<BlockPos, Direction> {
        val eye = player.eyePosition
        val parts = bedParts(level, bedPos)
        val candidates = mutableListOf<Pair<BlockPos, Direction>>()

        for (part in parts) {
            for (dir in Direction.values()) {
                if (part.relative(dir) in parts) continue
                val target = bedFacePoint(part, dir)
                val hit = firstSolidOnRay(level, eye, target) ?: continue
                val face = if (hit in parts) dir else faceToward(eye, hit)
                candidates.add(hit to face)
            }
        }

        candidates.firstOrNull { (pos, _) ->
            level.getBlockState(pos).`is`(BlockTags.BEDS)
        }?.let { return it }

        return candidates.minWithOrNull(
            compareBy<Pair<BlockPos, Direction>> { (pos, _) -> miningTicks(player, level, pos) }
                .thenBy { (pos, _) -> eye.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5) }
        ) ?: (bedPos to faceToward(eye, bedPos))
    }

    private fun bedFacePoint(pos: BlockPos, face: Direction): Vec3 {
        return Vec3(
            pos.x + 0.5 + face.stepX * 0.49,
            pos.y + 0.5 + face.stepY * 0.49,
            pos.z + 0.5 + face.stepZ * 0.49,
        )
    }

    private fun firstSolidOnRay(level: ClientLevel, start: Vec3, end: Vec3): BlockPos? {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val dz = end.z - start.z
        val steps = 96
        var prevPos: BlockPos? = null
        for (i in 0..steps) {
            val t = i.toDouble() / steps
            val pos = BlockPos(
                Mth.floor(start.x + dx * t),
                Mth.floor(start.y + dy * t),
                Mth.floor(start.z + dz * t),
            )
            if (pos == prevPos) continue
            prevPos = pos
            val state = level.getBlockState(pos)
            if (!state.isAir) return pos
        }
        return null
    }

    private fun findShellTarget(player: LocalPlayer, level: ClientLevel, bedPos: BlockPos): Pair<BlockPos, Direction> {
        val parts = bedParts(level, bedPos)
        findExposedBedFace(level, parts)?.let { (pos, face) ->
            return pos to face
        }

        val candidates = linkedSetOf<BlockPos>()
        for (part in parts) {
            for (dir in Direction.values()) {
                val pos = part.relative(dir)
                if (pos in parts) continue
                val state = level.getBlockState(pos)
                if (isDefenseBlock(state) && miningTicks(player, level, pos) != Int.MAX_VALUE) {
                    candidates.add(pos)
                }
            }
        }

        val target = candidates.minWithOrNull(
            compareBy<BlockPos> { miningTicks(player, level, it) }
                .thenBy { player.eyePosition.distanceToSqr(it.x + 0.5, it.y + 0.5, it.z + 0.5) }
        ) ?: bedPos

        return target to faceToward(player.eyePosition, target)
    }

    private fun findExposedBedFace(level: ClientLevel, parts: Set<BlockPos>): Pair<BlockPos, Direction>? {
        for (part in parts) {
            for (dir in Direction.values()) {
                val neighbor = part.relative(dir)
                if (neighbor in parts) continue
                val state = level.getBlockState(neighbor)
                if (!isDefenseBlock(state)) {
                    return part to dir
                }
            }
        }
        return null
    }

    private fun bedParts(level: ClientLevel, bedPos: BlockPos): Set<BlockPos> {
        val parts = linkedSetOf(bedPos)
        for (dir in listOf(Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)) {
            val other = bedPos.relative(dir)
            if (level.getBlockState(other).`is`(BlockTags.BEDS)) {
                parts.add(other)
            }
        }
        return parts
    }

    private fun faceToward(eye: Vec3, pos: BlockPos): Direction {
        val dx  = eye.x - (pos.x + 0.5)
        val dy  = eye.y - (pos.y + 0.5)
        val dz  = eye.z - (pos.z + 0.5)
        val adx = abs(dx); val ady = abs(dy); val adz = abs(dz)
        return when {
            ady >= adx && ady >= adz -> if (dy > 0) Direction.UP    else Direction.DOWN
            adx >= adz               -> if (dx > 0) Direction.EAST  else Direction.WEST
            else                     -> if (dz > 0) Direction.SOUTH else Direction.NORTH
        }
    }

    private fun findBestHotbarSlot(player: LocalPlayer, state: BlockState): Int {
        var bestSlot  = player.inventory.selectedSlot
        var bestSpeed = player.inventory.getItem(player.inventory.selectedSlot).getDestroySpeed(state)
        for (slot in 0..8) {
            val speed = player.inventory.getItem(slot).getDestroySpeed(state)
            if (speed > bestSpeed) {
                bestSpeed = speed
                bestSlot  = slot
            }
        }
        return bestSlot
    }

    private fun miningTicks(player: LocalPlayer, level: ClientLevel, pos: BlockPos): Int {
        val progress = level.getBlockState(pos).getDestroyProgress(player, level, pos)
        if (progress <= 0f) return Int.MAX_VALUE
        return ceil(1f / progress).toInt()
    }

    private fun isDefenseBlock(state: BlockState): Boolean =
        !state.isAir && !state.`is`(BlockTags.BEDS)

    private fun isOwnBed(player: LocalPlayer, state: BlockState): Boolean {
        val bedColor = (state.block as? BedBlock)?.color ?: return false
        return playerLeatherColors(player).any { closestDyeColor(it) == bedColor }
    }

    private fun playerLeatherColors(player: LocalPlayer): List<Int> {
        val slots = arrayOf(
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET,
        )
        return slots.mapNotNull { slot ->
            player.getItemBySlot(slot).get(DataComponents.DYED_COLOR)?.rgb()
        }
    }

    private fun closestDyeColor(rgb: Int): DyeColor =
        DyeColor.values().minBy { colorDistance(rgb, it.textureDiffuseColor) }

    private fun colorDistance(a: Int, b: Int): Int {
        val ar = (a shr 16) and 0xFF
        val ag = (a shr 8) and 0xFF
        val ab = a and 0xFF
        val br = (b shr 16) and 0xFF
        val bg = (b shr 8) and 0xFF
        val bb = b and 0xFF
        val dr = ar - br
        val dg = ag - bg
        val db = ab - bb
        return dr * dr + dg * dg + db * db
    }

    private fun calcRotation(player: LocalPlayer, pos: BlockPos): Pair<Float, Float> {
        val dx         = pos.x + 0.5 - player.x
        val dy         = pos.y + 0.5 - player.eyeY
        val dz         = pos.z + 0.5 - player.z
        val horizDist  = sqrt(dx * dx + dz * dz)
        val yaw        = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch      = (-Math.toDegrees(atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }
}
