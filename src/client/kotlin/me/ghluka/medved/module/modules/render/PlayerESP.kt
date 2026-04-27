package me.ghluka.medved.module.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.math.Axis
import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.core.component.DataComponents
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.AABB
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PlayerESP : Module(
    name = "Box ESP",
    description = "Highlights nearby players",
    category = Category.RENDER
) {
    enum class ColorMode { THEME, TEAM, HEALTH, STATIC }
    enum class RenderMode { BOX, CORNERS }

    private val colorMode  = enum("color mode", ColorMode.THEME)
    private val renderMode = enum("render mode", RenderMode.BOX)

    private val staticColor = color("color", Color(255, 80, 80), allowAlpha = false).also {
        it.visibleWhen = { colorMode.value == ColorMode.STATIC }
    }

    private val teamOnly    = boolean("team only", true).also {
        it.visibleWhen = { colorMode.value == ColorMode.TEAM }
    }

    private val fillAlpha   = double("fill alpha",    0.1, 0.0,  0.4)
    private val outlineAlpha = double("outline alpha", 0.8,  0.0,  1.0)
    private val lineWidth   = double("line width",    1.0,  0.5,  4.0)
    private val expand      = double("expand",        0.0, 0.0,  0.3)

    private val cornerSize  = double("corner size",   0.25, 0.05, 0.5).also {
        it.visibleWhen = { renderMode.value == RenderMode.CORNERS }
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return
        val level  = mc.level  ?: return

        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)

        RenderUtil.worldContext(ctx) { ctxPose, bufferSource ->
            for (entity in level.players()) {
                if (entity == player) continue
                if (!TargetFilter.isValidTarget(player, entity)) continue

                val color = resolveColor(player, entity) ?: continue

                val ex = entity.xOld + (entity.x - entity.xOld) * partialTick
                val ey = entity.yOld + (entity.y - entity.yOld) * partialTick
                val ez = entity.zOld + (entity.z - entity.zOld) * partialTick
                val e  = expand.value

                val box = AABB(
                    ex - entity.bbWidth  / 2 - e, ey - e,                   ez - entity.bbWidth  / 2 - e,
                    ex + entity.bbWidth  / 2 + e, ey + entity.bbHeight + e, ez + entity.bbWidth  / 2 + e
                )

                val r = color.r / 255f
                val g = color.g / 255f
                val b = color.b / 255f
                val fa = fillAlpha.value.toFloat()
                val oa = outlineAlpha.value.toFloat()
                val lw = lineWidth.value.toFloat()

                val fillRT = RenderUtil.ESP_FILLED
                val lineRT = RenderUtil.ESP_LINES

                when (renderMode.value) {
                    RenderMode.BOX -> {
                        val fillVC = bufferSource.getBuffer(fillRT)
                        RenderUtil.boxFilledBothSides(fillVC, ctxPose, box, r, g, b, fa)
                        bufferSource.endBatch(fillRT)

                        val lineVC = bufferSource.getBuffer(lineRT)
                        RenderUtil.boxOutline(lineVC, ctxPose, box, r, g, b, oa, lw)
                        bufferSource.endBatch(lineRT)
                    }

                    RenderMode.CORNERS -> {
                        val lineVC = bufferSource.getBuffer(lineRT)
                        drawCorners(lineVC, ctxPose, box, r, g, b, oa, lw)
                        bufferSource.endBatch(lineRT)
                    }

                    //RenderMode.OUTLINE -> {
                    //    val lineVC = bufferSource.getBuffer(lineRT)
                    //    RenderUtil.boxOutline(lineVC, ctxPose, box, r, g, b, oa, lw)
                    //    bufferSource.endBatch(lineRT)
                    //}
                }
            }
        }
    }

    private fun resolveColor(viewer: Player, target: Player): Color? {
        return when (colorMode.value) {
            ColorMode.THEME -> Colour.accent.value

            ColorMode.STATIC -> Color(staticColor.value.r, staticColor.value.g, staticColor.value.b)

            ColorMode.HEALTH -> {
                val ratio = (target.health / target.maxHealth).coerceIn(0f, 1f)
                
                val red   = if (ratio < 0.5f) 1f else 2f * (1f - ratio)
                val green = if (ratio > 0.5f) 1f else 2f * ratio
                Color((red * 255).toInt(), (green * 255).toInt(), 0)
            }

            ColorMode.TEAM -> {
                val helmet = target.getItemBySlot(EquipmentSlot.HEAD)
                val rgb = helmet.get(DataComponents.DYED_COLOR)?.rgb()

                if (rgb == null) {
                    if (teamOnly.value) return null
                    // fallback to theme if teamOnly is off and they have no helmet colour
                    Colour.accent.value
                } else {
                    Color((rgb shr 16) and 0xFF, (rgb shr 8) and 0xFF, rgb and 0xFF)
                }
            }
        }
    }

    private fun drawCorners(
        vc: net.minecraft.client.renderer.rendertype.RenderType,
        pose: PoseStack.Pose,
        box: AABB,
        r: Float, g: Float, b: Float, a: Float,
        lw: Float
    ) { }

    private fun drawCorners(
        vc: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        box: AABB,
        r: Float, g: Float, b: Float, a: Float,
        lw: Float
    ) {
        val x0 = box.minX.toFloat(); val y0 = box.minY.toFloat(); val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat(); val y1 = box.maxY.toFloat(); val z1 = box.maxZ.toFloat()
        val cx = ((x1 - x0) * cornerSize.value).toFloat()
        val cy = ((y1 - y0) * cornerSize.value).toFloat()
        val cz = ((z1 - z0) * cornerSize.value).toFloat()

        fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
            RenderUtil.line(vc, pose, ax, ay, az, bx, by, bz, r, g, b, a, lw)
        }

        // 8 corners × 3 axes each
        for ((px, py, pz) in listOf(
            Triple(x0, y0, z0), Triple(x1, y0, z0),
            Triple(x0, y1, z0), Triple(x1, y1, z0),
            Triple(x0, y0, z1), Triple(x1, y0, z1),
            Triple(x0, y1, z1), Triple(x1, y1, z1)
        )) {
            val sx = if (px == x0) 1f else -1f
            val sy = if (py == y0) 1f else -1f
            val sz = if (pz == z0) 1f else -1f
            seg(px, py, pz, px + sx * cx, py,          pz)
            seg(px, py, pz, px,          py + sy * cy, pz)
            seg(px, py, pz, px,          py,           pz + sz * cz)
        }
    }
}