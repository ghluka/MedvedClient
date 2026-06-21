package me.ghluka.medved.module.modules.minigames

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.NotificationManager
import me.ghluka.medved.util.RotationManager
import me.ghluka.medved.util.useItemStrict
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.component.DataComponents
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.Item
import net.minecraft.world.item.TooltipFlag
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.sqrt

object QuakecraftRage : Module(
    name = "Quakecraft Rage",
    description = "Snaps to visible Quakecraft targets and shoots with the railgun",
    category = Category.MINIGAMES,
) {
    private const val GUN_LORE = "rightclicktoshoot"
    private const val MIN_SHOT_COOLDOWN_TICKS = 22

    private val range = float("range", 80.0f, 5.0f, 150.0f)
    private val hitboxScale = float("hitbox scale", 1.5f, 1.0f, 3.0f)
    private val prediction = boolean("prediction", true)
    private val predictionTicks = float("prediction ticks", 2.0f, 0.0f, 6.0f).also {
        it.visibleWhen = { prediction.value }
    }
    private val aimSpeed = float("aim speed", 360.0f, 30.0f, 720.0f)
    private val fireTolerance = float("fire tolerance", 0.75f, 0.1f, 3.0f)
    private val settleTicks = int("settle ticks", 1, 0, 3)
    private val shootDelay = int("shoot cooldown", 24, 10, 60)
    private val useTargetFilter = boolean("target filter", false)

    private var delayTicks = 0
    private var ownsRotation = false
    private var lastGunDebug: String? = null
    private var lastTargetDebug: String? = null
    private var aimedTicks = 0
    private var lastAimTargetId = -1
    private var shotQueued = false

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            tick(client)
        }
    }

    override fun onDisabled() {
        if (ownsRotation) {
            RotationManager.clearRotation()
        }
        ownsRotation = false
        delayTicks = 0
        lastGunDebug = null
        lastTargetDebug = null
        aimedTicks = 0
        lastAimTargetId = -1
        shotQueued = false
        RotationManager.pendingFireAction = null
    }

    private fun tick(client: Minecraft) {
        val player = client.player ?: return clearAim()
        val level = client.level ?: return clearAim()
        if (client.gui.screen() != null) return clearAim()
        if (!hasQuakeGun(player)) {
            clearAim()
            return
        }

        if (delayTicks > 0) delayTicks--

        val aim = level.players()
            .asSequence()
            .mapNotNull { targetAim(player, it) }
            .minByOrNull { angularDistanceTo(player, it.point) }

        if (aim == null) {
            clearAim()
            return
        }

        val (yaw, pitch) = rotationTo(player, aim.point)
        RotationManager.perspective = true
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode = RotationManager.RotationMode.CLIENT
        RotationManager.setTargetRotation(yaw, pitch)
        ownsRotation = true
        RotationManager.quickTick(aimSpeed.value)

        if (aim.target.id != lastAimTargetId) {
            aimedTicks = 0
            lastAimTargetId = aim.target.id
        }

        if (RotationManager.hasReachedTarget(fireTolerance.value)) {
            aimedTicks++
        } else {
            aimedTicks = 0
        }

        if (delayTicks <= 0 && aimedTicks >= settleTicks.value && !shotQueued) {
            delayTicks = shootDelay.value.coerceAtLeast(MIN_SHOT_COOLDOWN_TICKS)
            aimedTicks = 0
            shotQueued = true
            RotationManager.pendingFireAction = Runnable {
                shoot(player)
                shotQueued = false
            }
        }
    }

    private fun clearAim() {
        if (ownsRotation) {
            RotationManager.clearRotation()
            ownsRotation = false
        }
        aimedTicks = 0
        lastAimTargetId = -1
        shotQueued = false
        RotationManager.pendingFireAction = null
    }

    private fun shoot(player: Player) {
        if (player.inventory.selectedSlot != 0) {
            player.inventory.selectedSlot = 0
        }
        useItemStrict(InteractionHand.MAIN_HAND)
    }

    private fun hasQuakeGun(player: Player): Boolean {
        val stack = player.inventory.getItem(0)
        val loreLines = if (stack.isEmpty) emptyList() else quakeGunLoreLines(player)
        val detected = !stack.isEmpty && loreLines.any { normalizeText(it).contains(GUN_LORE) }
        return detected
    }

    private fun quakeGunLoreLines(player: Player): List<String> {
        val stack = player.inventory.getItem(0)
        val lines = mutableListOf<String>()
        stack.get(DataComponents.LORE)?.lines()?.mapTo(lines) { it.string }
        if (lines.isEmpty()) {
            lines += stack.getTooltipLines(Item.TooltipContext.of(player.level()), player, TooltipFlag.NORMAL)
                .map { it.string }
        }
        return lines
    }

    private fun targetAim(player: Player, target: Player): TargetAim? {
        if (target === player) return null
        if (target.isSpectator) return null
        if (target.isDeadOrDying || target.isInvisible) return null
        if (player.distanceTo(target) > range.value) return null
        if (useTargetFilter.value && !TargetFilter.isValidTarget(player, target)) return null

        val aimBox = aimHitbox(target)
        return aimPoints(aimBox)
            .asSequence()
            .filter { hasStrictVisibility(player, it, aimBox) }
            .minByOrNull { angularDistanceTo(player, it) }
            ?.let { TargetAim(target, it) }
    }

    private fun expandedHitbox(target: Player): AABB {
        val box = target.boundingBox
        val extra = ((hitboxScale.value - 1.0f) / 2.0f).coerceAtLeast(0.0f).toDouble()
        return box.inflate(box.xsize * extra, box.ysize * extra, box.zsize * extra)
    }

    private fun aimHitbox(target: Player): AABB {
        val box = expandedHitbox(target)
        if (!prediction.value || predictionTicks.value <= 0.0f) return box
        return box.move(predictionOffset(target))
    }

    private fun predictionOffset(target: Player): Vec3 {
        var dx = target.x - target.xo
        var dy = target.y - target.yo
        var dz = target.z - target.zo

        if (!dx.isFinite() || !dy.isFinite() || !dz.isFinite()) return Vec3.ZERO

        val horizontalSpeed = hypot(dx, dz)
        if (horizontalSpeed > 0.8) {
            val scale = 0.8 / horizontalSpeed
            dx *= scale
            dz *= scale
        }
        dy = dy.coerceIn(-0.6, 0.6)

        val ticks = predictionTicks.value.toDouble()
        return Vec3(dx * ticks, dy * ticks, dz * ticks)
    }

    private fun aimPoints(box: AABB): List<Vec3> {
        val centerX = (box.minX + box.maxX) * 0.5
        val centerY = (box.minY + box.maxY) * 0.5
        val centerZ = (box.minZ + box.maxZ) * 0.5
        val insetX = (box.xsize * 0.08).coerceAtMost(0.08)
        val insetY = (box.ysize * 0.06).coerceAtMost(0.08)
        val insetZ = (box.zsize * 0.08).coerceAtMost(0.08)
        val xs = doubleArrayOf(box.minX + insetX, centerX, box.maxX - insetX)
        val ys = doubleArrayOf(box.minY + insetY, centerY, box.maxY - insetY)
        val zs = doubleArrayOf(box.minZ + insetZ, centerZ, box.maxZ - insetZ)

        val points = ArrayList<Vec3>(28)
        points += Vec3(centerX, centerY, centerZ)
        for (x in xs) {
            for (y in ys) {
                for (z in zs) {
                    points += Vec3(x, y, z)
                }
            }
        }
        return points
    }

    private fun hasStrictVisibility(player: Player, point: Vec3, aimBox: AABB): Boolean {
        val level = player.level()
        val eye = player.eyePosition
        val delta = point.subtract(eye)
        val distance = delta.length()
        if (distance <= 0.001) return true

        val steps = ceil(distance * 16.0).toInt().coerceAtLeast(1)
        var lastPos: BlockPos? = null
        for (i in 1 until steps) {
            val t = i.toDouble() / steps
            val sample = Vec3(
                eye.x + delta.x * t,
                eye.y + delta.y * t,
                eye.z + delta.z * t,
            )
            if (aimBox.contains(sample)) continue
            val pos = BlockPos.containing(
                sample.x,
                sample.y,
                sample.z,
            )
            if (pos == lastPos) continue
            lastPos = pos
            if (!level.getBlockState(pos).isAir) {
                return false
            }
        }
        return true
    }

    private fun rotationTo(player: Player, point: Vec3): Pair<Float, Float> {
        val dx = point.x - player.x
        val dy = point.y - player.eyeY
        val dz = point.z - player.z
        val horizDist = sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(atan2(dy, horizDist))).toFloat().coerceIn(-90f, 90f)
        return yaw to pitch
    }

    private fun angularDistanceTo(player: Player, point: Vec3): Float {
        val (yaw, pitch) = rotationTo(player, point)
        val dy = Mth.wrapDegrees(yaw - RotationManager.getClientYaw())
        val dp = pitch - RotationManager.getClientPitch()
        return sqrt(dy * dy + dp * dp)
    }

    private fun normalizeText(text: String): String =
        text.lowercase().filter { it.isLetterOrDigit() }

    private data class TargetAim(
        val target: Player,
        val point: Vec3,
    )
}
