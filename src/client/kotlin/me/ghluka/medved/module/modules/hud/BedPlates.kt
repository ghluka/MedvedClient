package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.core.BlockPos
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.math.abs

object BedPlates : HudModule(
    "Bed Plates",
    "Highlights defended beds and shows their unique shell blocks",
) {
    private val searchRange  = float("search range",   48f,  8f,  96f)
    private val shellRadius  = int("shell radius",       6,   2,   16)
    private val scanInterval = int("scan interval ms", 500, 100, 2000)

    private data class BedEntry(
        val pos: BlockPos,
        val shell: List<BlockPos>,
        val stacks: List<ItemStack>,
    )

    private var trackedBeds: List<BedEntry> = emptyList()
    private var lastScanTime = 0L

    override fun onDisabled() {
        trackedBeds  = emptyList()
        lastScanTime = 0L
    }

    override fun onTick(client: Minecraft) {
        val now = System.currentTimeMillis()
        if (now - lastScanTime < scanInterval.value) return
        lastScanTime = now

        val player = client.player ?: return clear()
        val level  = client.level  ?: return clear()

        val allBeds = findAllBeds(player.position(), level, searchRange.value)
        trackedBeds = allBeds.map { bedPos ->
            val shell = getSurroundingBlocks(level, bedPos, shellRadius.value)
            val stacks = shell
                .asSequence()
                .map { level.getBlockState(it) }
                .mapNotNull { stackFor(it) }
                .distinctBy { it.item }
                .sortedBy { it.hoverName.string.lowercase() }
                .toList()
            BedEntry(bedPos, shell, stacks)
        }
    }

    override fun renderHudElement(g: GuiGraphicsExtractor) {
        val mc   = Minecraft.getInstance()
        val font = Font.getFont()
        val accent = Colour.accent.liveColor(Colour.accent.value).argb

        for (entry in trackedBeds) {
            val bedTop    = Vec3(entry.pos.x + 0.5, entry.pos.y + 1.2, entry.pos.z + 0.5)
            val projected = projectToScreen(bedTop, mc) ?: continue

            if (entry.stacks.isNotEmpty()) {
                renderIconStrip(g, projected, font, accent, entry.stacks)
            }
        }
    }

    override fun hudWidth(): Int {
        val iconSize = 16
        val gap = 2
        val padding = 3
        if (trackedBeds.isEmpty()) return padding * 2 + iconSize
        return trackedBeds.maxOf { padding * 2 + it.stacks.size * iconSize + (if (it.stacks.size > 0) (it.stacks.size - 1) * gap else 0) }
    }

    override fun hudHeight(): Int {
        val padding = 3
        val iconSize = 16
        return padding * 2 + iconSize
    }

    private fun renderIconStrip(
        extractor: GuiGraphicsExtractor,
        projected: Pair<Float, Float>,
        font:      net.minecraft.client.gui.Font,
        accent:    Int,
        stacks:    List<ItemStack>,
    ) {
        val iconSize = 16
        val gap      = 2
        val padding  = 3
        val width    = padding * 2 + stacks.size * iconSize + (stacks.size - 1) * gap
        val height   = padding * 2 + iconSize
        val x = (projected.first  - width  / 2f).toInt()
        val y = (projected.second - height - 4f).toInt()

        extractor.roundedFill(x, y, width, height, 4, 0xD0_0D0D16.toInt())
        extractor.fill(x, y, x + width, y + 2, accent)

        var iconX = x + padding
        for (stack in stacks) {
            extractor.item(stack, iconX, y + padding)
            extractor.itemDecorations(font, stack, iconX, y + padding)
            iconX += iconSize + gap
        }
    }

    private fun projectToScreen(worldPos: Vec3, mc: Minecraft): Pair<Float, Float>? {
        val camera = mc.gameRenderer.mainCamera
        val camPos = camera.position()

        val dx = (worldPos.x - camPos.x).toFloat()
        val dy = (worldPos.y - camPos.y).toFloat()
        val dz = (worldPos.z - camPos.z).toFloat()

        val mat = camera.getViewRotationProjectionMatrix(Matrix4f())

        val x = mat.m00() * dx + mat.m10() * dy + mat.m20() * dz + mat.m30()
        val y = mat.m01() * dx + mat.m11() * dy + mat.m21() * dz + mat.m31()
        val w = mat.m03() * dx + mat.m13() * dy + mat.m23() * dz + mat.m33()

        if (w <= 0f) return null

        val ndcX =  x / w
        val ndcY =  y / w

        val sw = mc.window.guiScaledWidth.toFloat()
        val sh = mc.window.guiScaledHeight.toFloat()

        val sx = (ndcX + 1f) * 0.5f * sw
        val sy = (1f - ndcY) * 0.5f * sh

        return sx to sy
    }

    private fun clear() {
        trackedBeds = emptyList()
    }

    private fun findAllBeds(
        playerPos: Vec3,
        level:     ClientLevel,
        range:     Float,
    ): List<BlockPos> {
        val base    = BlockPos.containing(playerPos.x, playerPos.y, playerPos.z)
        val radius  = range.toInt().coerceAtLeast(1)
        val rangeSq = (range * range).toDouble()
        val rawBeds = mutableListOf<BlockPos>()

        for (x in -radius..radius) for (y in -radius..radius) for (z in -radius..radius) {
            val pos = base.offset(x, y, z)
            if (!level.getBlockState(pos).`is`(BlockTags.BEDS)) continue
            val sq = playerPos.distanceToSqr(pos.x + 0.5, pos.y + 0.5, pos.z + 0.5)
            if (sq <= rangeSq) rawBeds.add(pos)
        }

        val visited = mutableSetOf<BlockPos>()
        val groups = mutableListOf<List<BlockPos>>()
        for (p in rawBeds) {
            if (p in visited) continue
            val queue = ArrayDeque<BlockPos>()
            val group = mutableListOf<BlockPos>()
            queue.add(p)
            visited.add(p)
            while (queue.isNotEmpty()) {
                val cur = queue.removeFirst()
                group.add(cur)
                for (dir in listOf(
                    BlockPos(1, 0, 0),
                    BlockPos(-1, 0, 0),
                    BlockPos(0, 0, 1),
                    BlockPos(0, 0, -1),
                    BlockPos(0, 1, 0),
                    BlockPos(0, -1, 0)
                )) {
                    val n = cur.offset(dir.x, dir.y, dir.z)
                    if (n in rawBeds && n !in visited) {
                        visited.add(n)
                        queue.add(n)
                    }
                }
            }
            groups.add(group)
        }

        return groups.map { grp ->
            val avgX = grp.map { it.x }.average()
            val avgY = grp.map { it.y }.average()
            val avgZ = grp.map { it.z }.average()
            BlockPos.containing(avgX, avgY, avgZ)
        }
    }

    private fun getSurroundingBlocks(
        level:       ClientLevel,
        origin: BlockPos,
        maxDistance: Int,
    ): List<BlockPos> {
        val result     = linkedSetOf<BlockPos>()
        val queue      = ArrayDeque<BlockPos>()
        val directions = arrayOf(
            BlockPos(1, 0, 0), BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1), BlockPos(0, 0, -1),
            BlockPos(0, 1, 0),
        )
        queue.add(origin); result.add(origin)
        val maxSq = maxDistance * maxDistance

        while (queue.isNotEmpty()) {
            val pos = queue.removeFirst()
            for (dir in directions) {
                val next = pos.offset(dir.x, dir.y, dir.z)
                if (next in result) continue
                val dx = next.x - origin.x
                val dy = next.y - origin.y
                val dz = next.z - origin.z
                if (abs(dx) + abs(dy) + abs(dz) > maxDistance * 2) continue
                if (dx*dx + dy*dy + dz*dz > maxSq) continue
                if (!isValidDefenseBlock(level.getBlockState(next))) continue
                result.add(next); queue.add(next)
            }
        }
        result.remove(origin)
        return result.toList()
    }

    private fun isValidDefenseBlock(state: BlockState): Boolean {
        val block = state.block
        val name  = block.descriptionId.lowercase()
        return when {
            state.`is`(BlockTags.WOOL)      -> true
            state.`is`(BlockTags.PLANKS)    -> true
            state.`is`(BlockTags.LOGS)      -> true
            state.`is`(BlockTags.LEAVES)    -> true
            state.`is`(BlockTags.STAIRS)    -> true
            state.`is`(BlockTags.SLABS)     -> true
            state.`is`(BlockTags.TERRACOTTA)-> true
            name.contains("glass")          -> true
            name.contains("carpet")         -> true
            block == Blocks.LADDER          -> true
            block == Blocks.OBSIDIAN        -> true
            block == Blocks.CRYING_OBSIDIAN -> true
            block == Blocks.END_STONE       -> true
            block == Blocks.WATER           -> true
            block == Blocks.TNT             -> true
            else                            -> false
        }
    }

    private fun stackFor(state: BlockState): ItemStack? {
        val item = state.block.asItem()
        if (item === Items.AIR) return null
        return ItemStack(item)
    }
}