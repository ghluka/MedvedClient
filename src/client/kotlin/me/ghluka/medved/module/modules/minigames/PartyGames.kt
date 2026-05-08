package me.ghluka.medved.module.modules.minigames

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.Module
import me.ghluka.medved.util.RenderUtil
import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.tags.BlockTags
import net.minecraft.world.InteractionResult
import net.minecraft.world.entity.animal.Animal
import net.minecraft.world.entity.animal.chicken.Chicken
import net.minecraft.world.entity.animal.cow.Cow
import net.minecraft.world.entity.animal.pig.Pig
import net.minecraft.world.entity.item.FallingBlockEntity
import net.minecraft.world.item.BlockItem
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.BlockHitResult
import kotlin.math.floor

object PartyGames : Module(
    name = "Party Games",
    description = "All-in-one module for the Party Games minigame",
    category = Category.MINIGAMES
) {
    private val animalSlaughterAIO = boolean("Animal Slaughter AIO", true)
    private val animalESPEnabled = boolean("Animal ESP", true).also {
        it.visibleWhen = { animalSlaughterAIO.value }
    }
    private val blockWrongClicks = boolean("Block wrong clicks", true).also {
        it.visibleWhen = { animalSlaughterAIO.value }
    }
    private val hideWrongMobs = boolean("Hide wrong mobs", true).also {
        it.visibleWhen = { animalSlaughterAIO.value }
    }
    private val cowColor = color("Cow ESP color", Color(45, 35, 20)).also {
        it.visibleWhen = { animalSlaughterAIO.value && animalESPEnabled.value }
    }
    private val pigColor = color("Pig ESP color", Color(255, 192, 203)).also {
        it.visibleWhen = { animalSlaughterAIO.value && animalESPEnabled.value }
    }
    private val chickenColor = color("Chicken ESP color", Color(255, 255, 255)).also {
        it.visibleWhen = { animalSlaughterAIO.value && animalESPEnabled.value }
    }

    private val anvilESP = boolean("Anvil ESP", true)
    private val anvilESPColor = color("Anvil ESP color", Color(255, 0, 0)).also {
        it.visibleWhen = { anvilESP.value }
    }

    private val avalancheESP = boolean("Avalanche ESP", true)
    private val avalancheESPColor = color("Avalanche ESP color", Color(0, 255, 0)).also {
        it.visibleWhen = { avalancheESP.value }
    }

    private val jigsawRushTriggerbot = boolean("Jigsaw Rush Triggerbot", true)

    private val spiderMazePathfinder = boolean("Spider Maze Pathfinder", true)
    private val spiderMazeColor = color("Spider Maze color", Color(255, 0, 0)).also {
        it.visibleWhen = { spiderMazePathfinder.value }
    }

    private const val MAZE_MIN_X = -24
    private const val MAZE_MAX_X = 114
    private const val MAZE_Y    = 4
    private const val MAZE_MIN_Z = 2029
    private const val MAZE_MAX_Z = 2168
    private val MAZE_GOAL = BlockPos(44, 4, 2099)
    private val MAZE_DIRS = arrayOf(intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1))

    private var cachedSafePositions: List<BlockPos> = emptyList()
    private var lastAvalancheCacheMs = 0L

    private var cachedMazePath: List<BlockPos> = emptyList()
    private var lastMazePlayerPos: BlockPos? = null

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            if (jigsawRushTriggerbot.value) tickJigsawRush(client)
        }

        AttackEntityCallback.EVENT.register { player, world, hand, entity, _ ->
            if (!isEnabled() || !animalSlaughterAIO.value || !blockWrongClicks.value)
                return@register InteractionResult.PASS
            if (entity is Animal) {
                val name = entity.displayName?.string ?: return@register InteractionResult.PASS
                if ("-" in name) return@register InteractionResult.FAIL
            }
            InteractionResult.PASS
        }
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val mc     = Minecraft.getInstance()
        val player = mc.player ?: return
        val level  = mc.level  ?: return

        RenderUtil.worldContext(ctx) { ctxPose, _, bufferSource ->
            if (animalSlaughterAIO.value) {
                for (entity in level.entitiesForRendering()) {
                    if (entity !is Animal) continue
                    val name = entity.displayName?.string ?: continue

                    if (hideWrongMobs.value && "-" in name) {
                        entity.isInvisible = true
                    }

                    if (animalESPEnabled.value) {
                        val col = when {
                            entity is Cow     && "+" in name -> cowColor.value
                            entity is Pig     && "+" in name -> pigColor.value
                            entity is Chicken && "+" in name -> chickenColor.value
                            else -> null
                        } ?: continue

                        val r = col.r / 255f; val g = col.g / 255f; val b = col.b / 255f
                        RenderUtil.boxFilledBothSides(
                            bufferSource.getBuffer(RenderUtil.ESP_FILLED),
                            ctxPose, entity.boundingBox,
                            r, g, b, 0.15f
                        )
                        bufferSource.endBatch(RenderUtil.ESP_FILLED)
                        RenderUtil.boxOutline(
                            bufferSource.getBuffer(RenderUtil.ESP_LINES),
                            ctxPose, entity.boundingBox,
                            r, g, b, 1.0f, 1.5f
                        )
                        bufferSource.endBatch(RenderUtil.ESP_LINES)
                    }
                }
            }

            if (anvilESP.value) {
                for (entity in level.entitiesForRendering()) {
                    if (entity !is FallingBlockEntity) continue
                    if (entity.onGround()) continue

                    val prediction = predictFallingBlock(entity, level)
                    val landingPos = prediction.landingPos
                    if (level.getBlockState(landingPos).isAir) continue

                    val col = anvilESPColor.value
                    val r = col.r / 255f; val g = col.g / 255f; val b = col.b / 255f

                    RenderUtil.boxFilledBothSides(
                        bufferSource.getBuffer(RenderUtil.ESP_FILLED),
                        ctxPose, AABB(landingPos),
                        r, g, b, 0.2f
                    )
                    bufferSource.endBatch(RenderUtil.ESP_FILLED)
                    RenderUtil.boxOutline(
                        bufferSource.getBuffer(RenderUtil.ESP_LINES),
                        ctxPose, AABB(landingPos),
                        r, g, b, 1.0f, 2.0f
                    )
                    bufferSource.endBatch(RenderUtil.ESP_LINES)

                    val pts = prediction.trajectoryPoints
                    if (pts.size >= 2) {
                        val vc = bufferSource.getBuffer(RenderUtil.ESP_LINES)
                        for (i in 0 until pts.size - 1) {
                            val (ax, ay, az) = pts[i]
                            val (bx, by, bz) = pts[i + 1]

                            val alpha = 1.0f - (i.toFloat() / pts.size) * 0.6f
                            RenderUtil.line(
                                vc, ctxPose,
                                ax.toFloat(), ay.toFloat(), az.toFloat(),
                                bx.toFloat(), by.toFloat(), bz.toFloat(),
                                r, g, b, alpha, 1.5f
                            )
                        }
                        bufferSource.endBatch(RenderUtil.ESP_LINES)
                    }
                }
            }

            if (avalancheESP.value) {
                val now = System.currentTimeMillis()
                if (now - lastAvalancheCacheMs > 150L) {
                    lastAvalancheCacheMs = now
                    val fresh = mutableListOf<BlockPos>()
                    for (x in -2405..-1892) {
                        for (z in -2381..-1868) {
                            if (level.getBlockState(BlockPos(x, 49, z)).`is`(BlockTags.WOODEN_SLABS)) {
                                fresh.add(BlockPos(x, 45, z))
                            }
                        }
                    }
                    cachedSafePositions = fresh
                }

                val col = avalancheESPColor.value
                val r = col.r / 255f; val g = col.g / 255f; val b = col.b / 255f
                for (safePos in cachedSafePositions) {
                    RenderUtil.boxFilledBothSides(
                        bufferSource.getBuffer(RenderUtil.ESP_FILLED),
                        ctxPose, AABB(safePos),
                        r, g, b, 0.15f
                    )
                    bufferSource.endBatch(RenderUtil.ESP_FILLED)
                    RenderUtil.boxOutline(
                        bufferSource.getBuffer(RenderUtil.ESP_LINES),
                        ctxPose, AABB(safePos),
                        r, g, b, 0.7f, 1.5f
                    )
                    bufferSource.endBatch(RenderUtil.ESP_LINES)
                }
            }

            if (spiderMazePathfinder.value) {
                val playerPos = BlockPos(
                    floor(player.x).toInt(),
                    MAZE_Y,
                    floor(player.z).toInt()
                )

                if (playerPos.x in MAZE_MIN_X..MAZE_MAX_X && playerPos.z in MAZE_MIN_Z..MAZE_MAX_Z) {
                    if (playerPos != lastMazePlayerPos) {
                        lastMazePlayerPos = playerPos
                        cachedMazePath = findMazePath(playerPos, MAZE_GOAL, level)
                    }

                    val col = spiderMazeColor.value
                    val r = col.r / 255f; val g = col.g / 255f; val b = col.b / 255f
                    for (pos in cachedMazePath) {
                        val pathBox = AABB(
                            pos.x.toDouble(),       pos.y.toDouble(),
                            pos.z.toDouble(),
                            (pos.x + 1).toDouble(), pos.y + 0.05,
                            (pos.z + 1).toDouble()
                        )
                        RenderUtil.boxFilledBothSides(
                            bufferSource.getBuffer(RenderUtil.ESP_FILLED),
                            ctxPose, pathBox,
                            r, g, b, 0.35f
                        )
                        bufferSource.endBatch(RenderUtil.ESP_FILLED)
                        RenderUtil.boxOutline(
                            bufferSource.getBuffer(RenderUtil.ESP_LINES),
                            ctxPose, pathBox,
                            r, g, b, 0.9f, 2.0f
                        )
                        bufferSource.endBatch(RenderUtil.ESP_LINES)
                    }
                }
            }
        }
    }

    private fun tickJigsawRush(client: Minecraft) {
        val player = client.player ?: return
        val level  = client.level  ?: return
        val partialTick = client.deltaTracker.getGameTimeDeltaPartialTick(false)
        val eyePos  = player.getEyePosition(partialTick)
        val lookVec = player.getViewVector(partialTick)
        val endPos  = eyePos.add(lookVec.scale(4.5))
        val hitResult = level.clip(
            net.minecraft.world.level.ClipContext(
                eyePos, endPos,
                net.minecraft.world.level.ClipContext.Block.OUTLINE,
                net.minecraft.world.level.ClipContext.Fluid.NONE,
                player
            )
        )
        if (hitResult.type == net.minecraft.world.phys.HitResult.Type.MISS) return
        val canvasPos = hitResult.blockPos

        try {
            if (level.getBlockState(canvasPos).block !== net.minecraft.world.level.block.Blocks.SNOW_BLOCK) return

            val pos = canvasPos.relative(hitResult.direction)
            val facingNorth  = player.direction === Direction.NORTH

            fun blockAt(p: BlockPos) = level.getBlockState(p).block
            fun notAir(p: BlockPos)  = !level.getBlockState(p).isAir

            val targetPos: BlockPos = when {
                notAir(pos.below()) -> when {
                    notAir(if (facingNorth) pos.west() else pos.east()) -> BlockPos(226, 7,  1819)
                    notAir(if (facingNorth) pos.east() else pos.west()) -> BlockPos(226, 7,  1813)
                    else                                                 -> BlockPos(226, 7,  1816)
                }
                notAir(pos.above()) -> when {
                    notAir(if (facingNorth) pos.west() else pos.east()) -> BlockPos(226, 13, 1819)
                    notAir(if (facingNorth) pos.east() else pos.west()) -> BlockPos(226, 13, 1813)
                    else                                                 -> BlockPos(226, 13, 1816)
                }
                else -> when {
                    notAir(if (facingNorth) pos.west() else pos.east()) -> BlockPos(226, 10, 1819)
                    notAir(if (facingNorth) pos.east() else pos.west()) -> BlockPos(226, 10, 1813)
                    else                                                 -> BlockPos(226, 10, 1816)
                }
            }

            clickItemFromBlockPos(client, targetPos)
        } catch (_: NullPointerException) {}
    }

    private fun clickItemFromBlockPos(client: Minecraft, pos: BlockPos) {
        try {
            val player      = client.player ?: return
            val level       = client.level  ?: return
            val targetBlock = level.getBlockState(pos).block

            for (slot in 0..8) {
                val stack = player.inventory.getItem(slot)
                if (stack.isEmpty) continue
                val item = stack.item
                if (item is BlockItem && item.block === targetBlock) {
                    if (player.inventory.selectedSlot != slot) {
                        player.inventory.selectedSlot = slot
                        KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
                    }
                    break
                }
            }
        } catch (_: NullPointerException) {}
    }

    private fun findMazePath(start: BlockPos, end: BlockPos, level: Level): List<BlockPos> {
        if (!isMazeWalkable(end, level)) return emptyList()

        val queue  = ArrayDeque<BlockPos>()
        val parent = HashMap<BlockPos, BlockPos?>()

        queue.add(start)
        parent[start] = null

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == end) return reconstructMazePath(parent, current)

            for (d in MAZE_DIRS) {
                val next = BlockPos(current.x + d[0], MAZE_Y, current.z + d[1])
                if (!inMazeBounds(next))         continue
                if (!isMazeWalkable(next, level)) continue
                if (parent.containsKey(next))    continue
                parent[next] = current
                queue.add(next)
            }
        }
        return emptyList()
    }

    /** Air at foot level and head level — same logic as the 1.8 isAirBlock + up() check. */
    private fun isMazeWalkable(pos: BlockPos, level: Level): Boolean =
        level.getBlockState(pos).isAir && level.getBlockState(pos.above()).isAir

    private fun inMazeBounds(pos: BlockPos): Boolean =
        pos.x in MAZE_MIN_X..MAZE_MAX_X && pos.z in MAZE_MIN_Z..MAZE_MAX_Z && pos.y == MAZE_Y

    private fun reconstructMazePath(parent: Map<BlockPos, BlockPos?>, end: BlockPos): List<BlockPos> {
        val path = ArrayDeque<BlockPos>()
        var at: BlockPos? = end
        while (at != null) {
            path.addFirst(at)
            at = parent[at]
        }
        return path
    }

    private data class FallingBlockPrediction(
        val landingPos: BlockPos,
        val trajectoryPoints: List<Triple<Double, Double, Double>>
    )

    private fun predictFallingBlock(entity: FallingBlockEntity, level: Level): FallingBlockPrediction {
        val dm = entity.deltaMovement
        var x = entity.x;  var vx = dm.x
        var y = entity.y;  var vy = dm.y
        var z = entity.z;  var vz = dm.z

        val hasArc = vx * vx + vz * vz > 0.005 * 0.005
        val points = if (hasArc) mutableListOf(Triple(x, y + 0.5, z)) else null

        for (tick in 0..400) {
            vy -= 0.04
            x += vx;  y += vy;  z += vz
            vx *= 0.98;  vy *= 0.98;  vz *= 0.98

            if (hasArc && tick % 3 == 0) points!!.add(Triple(x, y, z))

            val blockPos = BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())

            if (y <= level.minY)
                return FallingBlockPrediction(
                    BlockPos(floor(x).toInt(), level.minY, floor(z).toInt()),
                    points ?: emptyList()
                )

            if (!level.getBlockState(blockPos).isAir)
                return FallingBlockPrediction(blockPos, points ?: emptyList())
        }

        return FallingBlockPrediction(
            BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt()),
            points ?: emptyList()
        )
    }

    override fun onDisabled() {
        val mc    = Minecraft.getInstance()
        val level = mc.level ?: return

        if (animalSlaughterAIO.value && hideWrongMobs.value) {
            for (entity in level.entitiesForRendering()) {
                if (entity is Animal) entity.isInvisible = false
            }
        }

        cachedSafePositions = emptyList()
        cachedMazePath      = emptyList()
        lastMazePlayerPos   = null
        lastAvalancheCacheMs = 0L
    }
}