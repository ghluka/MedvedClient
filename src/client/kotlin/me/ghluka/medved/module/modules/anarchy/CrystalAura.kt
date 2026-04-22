package me.ghluka.medved.module.modules.anarchy

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.ItemTags
import net.minecraft.util.Mth
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.boss.enderdragon.EndCrystal
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.AxeItem
import net.minecraft.world.item.Items
import net.minecraft.world.level.ClipContext
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

object CrystalAura : Module(
    name = "Crystal Aura",
    description = "Automatically places end crystals on obsidian and detonates them to deal damage to nearby targets",
    category = Category.ANARCHY
) {
    enum class TargetMode {
        DISTANCE, YAW, ARMOR, THREAT, HEALTH
    }

    enum class Optimization {
        NONE, RAPID_FIRE, PREDICT
    }

    private val targetMode = enum("target mode", TargetMode.DISTANCE)
    private val range = float("range", 6.0f, 1.0f, 8.0f)

    private val antiSuicide = boolean("anti-suicide", true)
    private val maxSelfDamage = float("max self damage", 4.0f, 0.0f, 20.0f).also {
        it.visibleWhen = { antiSuicide.value }
    }

    private val optimization = enum("crystal optimization", Optimization.NONE)
    private val minEfficiency = float("crystal min efficiency", 6.0f, 0.0f, 20.0f)
    private val autoObsidian = boolean("auto obsidian", true)
    private val onlyPlayers = boolean("only players", false)

    private fun getExposure(crystalPos: Vec3, target: LivingEntity, ignorePos: BlockPos? = null): Float {
        val level = target.level() ?: return 1.0f
        val bbox = target.boundingBox
        val d0 = 1.0 / ((bbox.maxX - bbox.minX) * 2.0 + 1.0)
        val d1 = 1.0 / ((bbox.maxY - bbox.minY) * 2.0 + 1.0)
        val d2 = 1.0 / ((bbox.maxZ - bbox.minZ) * 2.0 + 1.0)
        val d3 = (1.0 - floor(1.0 / d0) * d0) / 2.0
        val d4 = (1.0 - floor(1.0 / d2) * d2) / 2.0
        if (d0 >= 0.0 && d1 >= 0.0 && d2 >= 0.0) {
            var hits = 0
            var rays = 0
            var f = 0.0f
            while (f <= 1.0f) {
                var f1 = 0.0f
                while (f1 <= 1.0f) {
                    var f2 = 0.0f
                    while (f2 <= 1.0f) {
                        val d5 = Mth.lerp(f.toDouble(), bbox.minX, bbox.maxX)
                        val d6 = Mth.lerp(f1.toDouble(), bbox.minY, bbox.maxY)
                        val d7 = Mth.lerp(f2.toDouble(), bbox.minZ, bbox.maxZ)
                        val vec3 = Vec3(d5 + d3, d6, d7 + d4)
                        val hit = level.clip(ClipContext(vec3, crystalPos, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, target))
                        if (hit.type == HitResult.Type.MISS || (ignorePos != null && hit.type == HitResult.Type.BLOCK && (hit as BlockHitResult).blockPos == ignorePos)) {
                            hits++
                        }
                        rays++
                        f2 = (f2 + d2).toFloat()
                    }
                    f1 = (f1 + d1).toFloat()
                }
                f = (f + d0).toFloat()
            }
            return if (rays == 0) 0.0f else hits.toFloat() / rays.toFloat()
        }
        return 0.0f
    }

    private fun getDamage(crystalPos: Vec3, target: LivingEntity, ignorePos: BlockPos? = null): Float {
        val distSq = target.distanceToSqr(crystalPos)
        if (distSq > 144.0) return 0f
        val level = target.level() ?: return 0f
        val dist = sqrt(distSq)
        val impact = 1.0 - (dist / 12.0)

        val exposure = getExposure(crystalPos, target, ignorePos)
        val modifiedImpact = impact * exposure

        var damage = ((modifiedImpact * modifiedImpact + modifiedImpact) / 2.0 * 7.0 * 12.0 + 1.0).toFloat()
        if (damage <= 0f) return 0f

        val diff = level.difficulty.id
        damage = when (diff) {
            0 -> 0f
            1 -> min(damage / 2.0f + 1.0f, damage)
            3 -> damage * 1.5f
            else -> damage
        }

        val armor = target.armorValue.toFloat()
        val toughness = if (armor > 15.0f) 8.0f else 0.0f

        val f = 2.0f + toughness / 4.0f
        val g = max(armor - damage / f, armor * 0.2f)
        damage *= (1.0f - g / 25.0f)

        val epf = if (armor >= 15.0f) 16 else 0
        val f2 = min(epf.toFloat(), 20.0f)
        damage *= (1.0f - f2 / 25.0f)

        return max(0f, damage)
    }

    private fun calcYawTarget(player: Player, vec: Vec3): Float {
        val dx = vec.x - player.x
        val dz = vec.z - player.z
        return Math.toDegrees(atan2(-dx, dz)).toFloat()
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (client.screen != null) return

        val maxRange = range.value.toDouble()
        var targetBlock: BlockPos? = null
        var obsTargetInteraction: BlockHitResult? = null

        val crystals = level.entitiesForRendering()
            .filterIsInstance<EndCrystal>()
            .filter { it.distanceTo(player) <= maxRange }

        val candidates = level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .filter { it !== player && !it.isDeadOrDying && player.distanceTo(it) <= maxRange && (!onlyPlayers.value || it is Player) && TargetFilter.isValidTarget(player, it) }

        val bestTarget = candidates.minByOrNull { e ->
            when (targetMode.value) {
                TargetMode.DISTANCE -> player.distanceTo(e).toFloat()
                TargetMode.HEALTH -> e.health
                TargetMode.YAW -> {
                    val tYaw = calcYawTarget(player, e.position())
                    abs(Mth.wrapDegrees(tYaw - player.yRot))
                }
                TargetMode.ARMOR -> e.armorValue.toFloat()
                TargetMode.THREAT -> {
                    var threat = e.health + e.armorValue.toFloat()
                    if (e is Player) {
                        val handItem = e.mainHandItem.item
                        if (e.mainHandItem.`is`(ItemTags.SWORDS)) threat += 10f
                        if (handItem is AxeItem) threat += 12f
                        if (handItem == Items.END_CRYSTAL) threat += 20f
                    }
                    -threat
                }
            }
        }

        if (bestTarget != null) {
            val predX = if (optimization.value == Optimization.PREDICT) bestTarget.deltaMovement.x * 2 else 0.0
            val predZ = if (optimization.value == Optimization.PREDICT) bestTarget.deltaMovement.z * 2 else 0.0
            val targetPredictedPos = bestTarget.position().add(predX, 0.0, predZ)

            for (crystal in crystals) {
                if (crystal.distanceTo(bestTarget) <= 8.0) {
                    val selfDmg = getDamage(crystal.position(), player)
                    if (antiSuicide.value && selfDmg >= maxSelfDamage.value) continue
                    val enemyDmg = getDamage(crystal.position(), bestTarget)
                    if (enemyDmg >= minEfficiency.value || enemyDmg > selfDmg) {
                        client.gameMode?.attack(player, crystal)
                        player.swing(InteractionHand.MAIN_HAND)
                    }
                }
            }

            val hasCrystal = player.offhandItem.item == Items.END_CRYSTAL || (0..8).any { player.inventory.getItem(it).item == Items.END_CRYSTAL }
            val hasObsidian = (0..8).any { player.inventory.getItem(it).item == Items.OBSIDIAN }

            if (hasCrystal) {
                var bestPlace: BlockPos? = null
                var bestDamage = -1f
                var requiresObs = false

                val startPos = BlockPos.containing(targetPredictedPos)
                for (dx in -4..4) {
                    for (dy in -3..3) {
                        for (dz in -4..4) {
                            val pos = startPos.offset(dx, dy, dz)
                            val obsPosVec = Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                            val crystalPosVec = Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)

                            if (player.distanceToSqr(obsPosVec) > maxRange * maxRange) continue
                            if (player.distanceToSqr(crystalPosVec) > maxRange * maxRange) continue
                            if (!level.isEmptyBlock(pos.above()) || !level.isEmptyBlock(pos.above(2))) continue

                            val state = level.getBlockState(pos)
                            val isBuiltObsidian = state.`is`(Blocks.OBSIDIAN) || state.`is`(Blocks.BEDROCK)
                            val isReplaceable = state.canBeReplaced()

                            if (!isBuiltObsidian && (!autoObsidian.value || !isReplaceable || !hasObsidian)) continue

                            val crystalBox = AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, pos.y + 3.0, pos.z + 1.0).inflate(0.01)
                            val expandedTargetBox = bestTarget.boundingBox.inflate(0.1)
                            if (crystalBox.intersects(expandedTargetBox) || crystalBox.intersects(player.boundingBox)) continue
                            if (level.getEntities(null, crystalBox).any { !it.isSpectator }) continue

                            var builtInteraction: BlockHitResult? = null
                            if (!isBuiltObsidian) {
                                val obsBox = AABB(pos).inflate(0.01)
                                if (obsBox.intersects(expandedTargetBox) || obsBox.intersects(player.boundingBox)) continue
                                if (level.getEntities(null, obsBox).any { !it.isSpectator }) continue

                                val dirs = arrayOf(Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST)
                                for (dir in dirs) {
                                    val adj = pos.relative(dir)
                                    if (!level.getBlockState(adj).canBeReplaced()) {
                                        val face = dir.opposite
                                        val vec = Vec3(
                                            adj.x + 0.5 + face.stepX * 0.5,
                                            adj.y + 0.5 + face.stepY * 0.5,
                                            adj.z + 0.5 + face.stepZ * 0.5
                                        )
                                        builtInteraction = BlockHitResult(vec, face, adj, false)
                                        break
                                    }
                                }
                            }
                            if (!isBuiltObsidian && builtInteraction == null) continue

                            val selfDmg = getDamage(crystalPosVec, player, pos)
                            if (antiSuicide.value && selfDmg >= maxSelfDamage.value) continue

                            var enemyDmg = getDamage(crystalPosVec, bestTarget, pos)
                            if (enemyDmg < minEfficiency.value) continue

                            if (isBuiltObsidian) enemyDmg += 1000f

                            if (enemyDmg > bestDamage) {
                                bestDamage = enemyDmg
                                bestPlace = pos
                                requiresObs = !isBuiltObsidian
                                obsTargetInteraction = builtInteraction

                                val realDmg = if (isBuiltObsidian) enemyDmg - 1000f else enemyDmg
                                if (optimization.value == Optimization.RAPID_FIRE && isBuiltObsidian && realDmg > minEfficiency.value + 4.0f) {
                                    break
                                }
                            }
                        }
                    }
                }

                if (bestPlace != null) {
                    targetBlock = bestPlace
                }
            }
        }

        if (obsTargetInteraction != null) {
            val obsSlot = (0..8).find { player.inventory.getItem(it).item == Items.OBSIDIAN }
            if (obsSlot != null) {
                player.inventory.selectedSlot = obsSlot
                client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, obsTargetInteraction)
                player.swing(InteractionHand.MAIN_HAND)
                targetBlock = obsTargetInteraction.blockPos
            }
        }
        if (targetBlock != null) {
            val crystalSlot = (0..8).find { player.inventory.getItem(it).item == Items.END_CRYSTAL }
            val offhandCrystal = player.offhandItem.item == Items.END_CRYSTAL
            if (crystalSlot != null || offhandCrystal) {
                var hand = InteractionHand.MAIN_HAND
                if (!offhandCrystal && crystalSlot != null) {
                    player.inventory.selectedSlot = crystalSlot
                } else if (offhandCrystal) {
                    hand = InteractionHand.OFF_HAND
                }

                val center = Vec3(targetBlock.x + 0.5, targetBlock.y + 0.5, targetBlock.z + 0.5)
                val hitResult = BlockHitResult(center, Direction.UP, targetBlock, false)
                client.gameMode?.useItemOn(player, hand, hitResult)
                player.swing(hand)
            }
        }
    }
}