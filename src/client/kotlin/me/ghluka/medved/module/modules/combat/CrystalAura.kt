package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.world.Scaffold
import me.ghluka.medved.util.RotationManager
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import kotlin.math.atan2
import kotlin.math.sqrt

object CrystalAura : Module(
    name = "Crystal Aura",
    description = "Automatically places end crystals on obsidian and detonates them to deal damage to nearby targets.",
    category = Category.COMBAT
) {
    enum class Mode {
        AUTO, MANUAL
    }

    enum class TargetMode {
        DISTANCE, YAW, ARMOR, THREAT, HEALTH
    }

    enum class Optimization {
        NONE, RAPID_FIRE, PREDICT
    }

    private val mode = enum("mode", Mode.AUTO)
    private val targetMode = enum("target mode", TargetMode.DISTANCE).also {
        it.visibleWhen = { mode.value == Mode.AUTO }
    }

    private val range = float("range", 4.0f, 1.0f, 8.0f)
    private val maxAngle = float("max angle", 180.0f, 10.0f, 360.0f)
    private val aimSpeed = float("aim speed", 30.0f, 1.0f, 100.0f)
    private val blastDelay = intRange("blast delay", 0 to 2, 0, 20)

    private val antiSuicide = boolean("anti-suicide", true)
    private val maxSelfDamage = float("max self damage", 4.0f, 0.0f, 20.0f).also {
        it.visibleWhen = { antiSuicide.value }
    }

    private val optimization = enum("crystal optimization", Optimization.NONE)
    private val minEfficiency = float("crystal min efficiency", 6.0f, 0.0f, 20.0f)
    private val autoObsidian = boolean("auto obsidian", false)
    private val onlyPlayers = boolean("only players", true)

    private var lookTarget: net.minecraft.world.phys.Vec3? = null
    private var blastDelayCounter = 0
    private var placeDelayCounter = 0
    private var manualSequenceStep = 0
    private var manualTargetPos: net.minecraft.core.BlockPos? = null
    private var sequenceTimeout = 0

    override fun onEnabled() {
        lookTarget = null
        blastDelayCounter = 0
        placeDelayCounter = 0
        manualSequenceStep = 0
        manualTargetPos = null
        sequenceTimeout = 0
    }

    override fun onDisabled() {
        if (!me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld) {
            RotationManager.clearRotation()
        }
        lookTarget = null
        manualSequenceStep = 0
    }

    private fun getDamage(crystalPos: net.minecraft.world.phys.Vec3, target: LivingEntity): Float {
        val distSq = target.distanceToSqr(crystalPos)
        if (distSq > 144.0) return 0f
        val dist = kotlin.math.sqrt(distSq)
        val impact = (1.0 - (dist / 12.0))
        return ((impact * impact + impact) / 2.0 * 7.0 * 12.0 + 1.0).toFloat()
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val level = client.level ?: return
        if (client.screen != null) return

        if (blastDelayCounter > 0) blastDelayCounter--
        if (placeDelayCounter > 0) placeDelayCounter--

        val maxRange = range.value.toDouble()
        lookTarget = null
        var actionToTake: String? = null
        var targetCrystal: net.minecraft.world.entity.boss.enderdragon.EndCrystal? = null
        var targetBlock: net.minecraft.core.BlockPos? = null

        val crystals = level.entitiesForRendering()
            .filterIsInstance<net.minecraft.world.entity.boss.enderdragon.EndCrystal>()
            .filter { it.distanceTo(player) <= maxRange }

        if (mode.value == Mode.MANUAL) {
            if (autoObsidian.value && client.options.keyUse.isDown && manualSequenceStep == 0 &&
                (player.mainHandItem.item == net.minecraft.world.item.Items.END_CRYSTAL || 
                 player.offhandItem.item == net.minecraft.world.item.Items.END_CRYSTAL)) {
                val hitRes = client.hitResult
                if (hitRes is net.minecraft.world.phys.BlockHitResult && hitRes.type != net.minecraft.world.phys.HitResult.Type.MISS) {
                    val clickedState = level.getBlockState(hitRes.blockPos)
                    if (!clickedState.`is`(net.minecraft.world.level.block.Blocks.OBSIDIAN) && !clickedState.`is`(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                        val posToPlace = if (clickedState.canBeReplaced()) hitRes.blockPos else hitRes.blockPos.relative(hitRes.direction)
                        val stateToPlace = level.getBlockState(posToPlace)
                        
                        if (stateToPlace.canBeReplaced()) {
                            manualTargetPos = posToPlace
                            manualSequenceStep = 1
                            sequenceTimeout = 20
                        }
                    }
                }
            }

            if (manualSequenceStep > 0 && manualTargetPos != null) {
                sequenceTimeout--
                val hasCrystal = player.offhandItem.item == net.minecraft.world.item.Items.END_CRYSTAL || (0..8).any { player.inventory.getItem(it).item == net.minecraft.world.item.Items.END_CRYSTAL }
                val hasObsidian = (0..8).any { player.inventory.getItem(it).item == net.minecraft.world.item.Items.OBSIDIAN }
                
                if (sequenceTimeout <= 0 || !hasCrystal) {
                    manualSequenceStep = 0
                } else if (manualSequenceStep == 1) {
                    if (!hasObsidian) {
                        manualSequenceStep = 0
                    } else {
                        actionToTake = "OBSIDIAN"
                        targetBlock = manualTargetPos
                    }
                } else if (manualSequenceStep == 2) {
                    actionToTake = "PLACE"
                    targetBlock = manualTargetPos
                } else if (manualSequenceStep == 3) {
                    // find crystal at position
                    val expectedPos = net.minecraft.world.phys.Vec3(manualTargetPos!!.x + 0.5, manualTargetPos!!.y + 1.0, manualTargetPos!!.z + 0.5)
                    val c = crystals.find { it.distanceToSqr(expectedPos) < 2.0 }
                    if (c != null) {
                        actionToTake = "HIT"
                        targetCrystal = c
                        lookTarget = c.position().add(0.0, 0.5, 0.0)
                    } else {
                        lookTarget = expectedPos // wait for it to spawn
                    }
                }
            } else {
                // break user-placed crystals
                for (crystal in crystals) {
                    val selfDamage = getDamage(crystal.position(), player)
                    if (antiSuicide.value && selfDamage >= maxSelfDamage.value) continue
                    
                    val (yaw, pitch) = calcRotationVec(player, crystal.position())
                    val dy = kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - player.yRot))
                    val dp = kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(pitch - player.xRot))
                    
                    if (dy <= maxAngle.value / 2f && dp <= maxAngle.value / 2f) {
                        targetCrystal = crystal
                        actionToTake = "HIT"
                        lookTarget = crystal.position().add(0.0, 0.5, 0.0)
                        break
                    }
                }
            }
        } else if (mode.value == Mode.AUTO) {
            val candidates = level.entitiesForRendering()
                .filterIsInstance<LivingEntity>()
                .filter { it !== player && !it.isDeadOrDying && player.distanceTo(it) <= maxRange && (!onlyPlayers.value || it is Player) && me.ghluka.medved.module.modules.other.TargetFilter.isValidTarget(player, it) }

            val bestTarget = candidates.minByOrNull { e ->
                when (targetMode.value) {
                    TargetMode.DISTANCE -> player.distanceTo(e).toFloat()
                    TargetMode.HEALTH -> e.health
                    else -> player.distanceTo(e).toFloat()
                }
            }

            if (bestTarget != null) {
                // Predictive optimization
                val predX = if (optimization.value == Optimization.PREDICT) bestTarget.deltaMovement.x * 2 else 0.0
                val predZ = if (optimization.value == Optimization.PREDICT) bestTarget.deltaMovement.z * 2 else 0.0
                val targetPredictedPos = bestTarget.position().add(predX, 0.0, predZ)

                for (crystal in crystals) {
                    if (crystal.distanceTo(bestTarget) <= 8.0) {
                        val selfDmg = getDamage(crystal.position(), player)
                        if (antiSuicide.value && selfDmg >= maxSelfDamage.value) continue
                        val enemyDmg = getDamage(crystal.position(), bestTarget)
                        if (enemyDmg >= minEfficiency.value || enemyDmg > selfDmg) {
                            targetCrystal = crystal
                            actionToTake = "HIT"
                            lookTarget = crystal.position().add(0.0, 0.5, 0.0)
                            break
                        }
                    }
                }

                val hasCrystal = player.offhandItem.item == net.minecraft.world.item.Items.END_CRYSTAL || (0..8).any { player.inventory.getItem(it).item == net.minecraft.world.item.Items.END_CRYSTAL }
                val hasObsidian = (0..8).any { player.inventory.getItem(it).item == net.minecraft.world.item.Items.OBSIDIAN }
                
                if (actionToTake == null && hasCrystal) {
                    var bestPlace: net.minecraft.core.BlockPos? = null
                    var bestDamage = -1f
                    var requiresObs = false
                    var obsTargetVec: net.minecraft.world.phys.Vec3? = null

                    val startPos = net.minecraft.core.BlockPos.containing(targetPredictedPos)
                    for (dx in -4..4) {
                        for (dy in -3..3) {
                            for (dz in -4..4) {
                                val pos = startPos.offset(dx, dy, dz)
                                val obsPosVec = net.minecraft.world.phys.Vec3(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
                                val crystalPosVec = net.minecraft.world.phys.Vec3(pos.x + 0.5, pos.y + 1.0, pos.z + 0.5)
                                
                                if (player.distanceToSqr(obsPosVec) > maxRange * maxRange) continue
                                if (player.distanceToSqr(crystalPosVec) > maxRange * maxRange) continue
                                if (!level.isEmptyBlock(pos.above()) || !level.isEmptyBlock(pos.above(2))) continue

                                val state = level.getBlockState(pos)
                                val isBuiltObsidian = state.`is`(net.minecraft.world.level.block.Blocks.OBSIDIAN) || state.`is`(net.minecraft.world.level.block.Blocks.BEDROCK)
                                val isReplaceable = state.canBeReplaced()
                                
                                if (!isBuiltObsidian && (!autoObsidian.value || !isReplaceable || !hasObsidian)) continue

                                val crystalBox = net.minecraft.world.phys.AABB(pos.x.toDouble(), pos.y + 1.0, pos.z.toDouble(), pos.x + 1.0, pos.y + 3.0, pos.z + 1.0)
                                if (level.getEntities(null, crystalBox).isNotEmpty()) continue
                                
                                var supportVec: net.minecraft.world.phys.Vec3? = null
                                if (!isBuiltObsidian) {
                                    val obsBox = net.minecraft.world.phys.AABB(pos)
                                    if (level.getEntities(null, obsBox).isNotEmpty()) continue
                                    
                                    val dirs = arrayOf(net.minecraft.core.Direction.DOWN, net.minecraft.core.Direction.UP, net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST)
                                    for (dir in dirs) {
                                        val adj = pos.relative(dir)
                                        if (!level.getBlockState(adj).canBeReplaced()) {
                                            val face = dir.opposite
                                            supportVec = net.minecraft.world.phys.Vec3(adj.x + 0.5 + face.stepX * 0.5, adj.y + 0.5 + face.stepY * 0.5, adj.z + 0.5 + face.stepZ * 0.5)
                                            break
                                        }
                                    }
                                }
                                if (!isBuiltObsidian && supportVec == null) continue

                                val selfDmg = getDamage(crystalPosVec, player)
                                if (antiSuicide.value && selfDmg >= maxSelfDamage.value) continue
                                
                                var enemyDmg = getDamage(crystalPosVec, bestTarget)
                                if (enemyDmg < minEfficiency.value) continue

                                if (isBuiltObsidian) enemyDmg += 1000f

                                if (enemyDmg > bestDamage) {
                                    bestDamage = enemyDmg
                                    bestPlace = pos
                                    requiresObs = !isBuiltObsidian
                                    obsTargetVec = supportVec
                                    
                                    // Rapid fire optimization
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
                        actionToTake = if (requiresObs) "OBSIDIAN" else "PLACE"
                        lookTarget = if (requiresObs) obsTargetVec else net.minecraft.world.phys.Vec3(bestPlace.x + 0.5, bestPlace.y + 1.0, bestPlace.z + 0.5)
                    }
                }
            }
        }

        val isManualPlace = mode.value == Mode.MANUAL && (actionToTake == "OBSIDIAN" || actionToTake == "PLACE")

        if (lookTarget == null && !isManualPlace) {
            if (!me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld) {
                RotationManager.clearRotation()
            }
            return
        }

        var readyToInteract = isManualPlace
        if (lookTarget != null) {
            val (tYaw, tPitch) = calcRotationVec(player, lookTarget!!)
            val yawDiff = kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(tYaw - player.yRot))
            val pitchDiff = kotlin.math.abs(net.minecraft.util.Mth.wrapDegrees(tPitch - player.xRot))
            readyToInteract = yawDiff < 15f && pitchDiff < 15f
        }

        if (readyToInteract) {
            if (actionToTake == "HIT" && targetCrystal != null && blastDelayCounter <= 0) {
                val attackKey = com.mojang.blaze3d.platform.InputConstants.getKey(client.options.keyAttack.saveString())
                if (client.hitResult is net.minecraft.world.phys.EntityHitResult) {
                    net.minecraft.client.KeyMapping.click(attackKey)
                    val (lo, hi) = blastDelay.value
                    blastDelayCounter = if (hi > lo) (lo..hi).random() else lo
                    if (mode.value == Mode.MANUAL && manualSequenceStep == 3) manualSequenceStep = 0
                }
            } else if (actionToTake == "PLACE" && targetBlock != null && placeDelayCounter <= 0) {
                val crystalSlot = (0..8).find { player.inventory.getItem(it).item == net.minecraft.world.item.Items.END_CRYSTAL }
                val offhandCrystal = player.offhandItem.item == net.minecraft.world.item.Items.END_CRYSTAL
                if (crystalSlot != null || offhandCrystal) {
                    val useKey = com.mojang.blaze3d.platform.InputConstants.getKey(client.options.keyUse.saveString())
                    
                    if (!offhandCrystal && crystalSlot != null) {
                        player.inventory.selectedSlot = crystalSlot
                    }
                    
                    if (client.hitResult is net.minecraft.world.phys.BlockHitResult && client.hitResult?.type != net.minecraft.world.phys.HitResult.Type.MISS) {
                        net.minecraft.client.KeyMapping.click(useKey)
                        placeDelayCounter = 2
                        if (mode.value == Mode.MANUAL && manualSequenceStep == 2) manualSequenceStep = 3
                    }
                } else if (mode.value == Mode.MANUAL) {
                    manualSequenceStep = 0 // cancel if no crystal exists anywhere
                }
            } else if (actionToTake == "OBSIDIAN" && targetBlock != null && placeDelayCounter <= 0) {
                val obsSlot = (0..8).find { player.inventory.getItem(it).item == net.minecraft.world.item.Items.OBSIDIAN }
                if (obsSlot != null) {
                    val useKey = com.mojang.blaze3d.platform.InputConstants.getKey(client.options.keyUse.saveString())
                    
                    player.inventory.selectedSlot = obsSlot
                    
                    if (client.hitResult is net.minecraft.world.phys.BlockHitResult && client.hitResult?.type != net.minecraft.world.phys.HitResult.Type.MISS) {
                        net.minecraft.client.KeyMapping.click(useKey)
                        placeDelayCounter = 2
                        if (manualSequenceStep == 1) manualSequenceStep = 2
                    }
                }
            }
        }
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val player = Minecraft.getInstance().player ?: return
        val currentLook = lookTarget ?: return

        if (Scaffold.isEnabled() ||
            me.ghluka.medved.module.modules.combat.KnockbackDisplacement.rotationHeld ||
            (me.ghluka.medved.module.modules.world.BedBreaker.isEnabled() && me.ghluka.medved.module.modules.world.BedBreaker.pendingHitPos != null) ||
            (me.ghluka.medved.module.modules.world.ChestAura.isEnabled() && RotationManager.isActive()) ||
            (me.ghluka.medved.module.modules.world.Clutch.isEnabled() && RotationManager.isActive())
        ) {
            return
        }

        val (targetYaw, targetPitch) = calcRotationVec(player, currentLook)

        RotationManager.perspective = true
        RotationManager.movementMode = RotationManager.MovementMode.CLIENT
        RotationManager.rotationMode = RotationManager.RotationMode.CLIENT

        RotationManager.setTargetRotation(targetYaw, targetPitch)
        RotationManager.quickTick(aimSpeed.value)
    }

    private fun calcRotationVec(player: Player, vec: net.minecraft.world.phys.Vec3): Pair<Float, Float> {
        val dx = vec.x - player.x
        val dy = vec.y - player.eyeY
        val dz = vec.z - player.z
        val horizDist = kotlin.math.sqrt(dx * dx + dz * dz)
        val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
        val pitch = (-Math.toDegrees(kotlin.math.atan2(dy, horizDist))).toFloat()
        return yaw to pitch
    }

    override fun hudInfo(): String {
        return mode.value.name.lowercase()
    }
}
