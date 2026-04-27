package me.ghluka.medved.module.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.entity.LivingEntity
import kotlin.math.cos
import kotlin.math.sin

object RiceFarmer : Module(
    name = "Rice Farmer",
    description = "Renders a conical hat above your player",
    category = Category.RENDER
) {
    enum class ColorMode { RAINBOW, GRADIENT, SINGLE, THEME }

    private val radius      = double("radius",   0.5, 0.5, 1.0)
    private val height      = double("height",   0.3, 0.1, 0.7)
    private val pos         = double("position", 0.1, -0.5, 0.5)
    private val rotSpeed    = double("rotation", 5.0, 0.0, 5.0)
    private val angles      = int("angles",      32,   4,   90)
    private val firstPerson = boolean("first person", false)
    private val shade       = boolean("shade", true)
    private val colorMode   = enum("color mode", ColorMode.RAINBOW)

    private val single = color("color", Color(9, 9, 9), allowAlpha = false).also {
        it.visibleWhen = { colorMode.value == ColorMode.SINGLE }
    }
    private val gradient1 = color("color start", Color(255, 0, 255), allowAlpha = false).also {
        it.visibleWhen = { colorMode.value == ColorMode.GRADIENT }
    }
    private val gradient2 = color("color end", Color(90, 10, 255), allowAlpha = false).also {
        it.visibleWhen = { colorMode.value == ColorMode.GRADIENT }
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        val player = mc.player ?: return

        if (mc.options.cameraType.isFirstPerson && !firstPerson.value) return

        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)

        RenderUtil.worldContext(ctx) { pose, bufferSource ->
            drawChinaHat(player, partialTick, pose, bufferSource)
        }
    }

    private fun drawChinaHat(
        entity: LivingEntity,
        partialTick: Float,
        ctxPose: PoseStack.Pose,
        bufferSource: MultiBufferSource.BufferSource
    ) {
        val mc = Minecraft.getInstance()

        val ex = entity.xOld + (entity.x - entity.xOld) * partialTick
        val ey = entity.yOld + (entity.y - entity.yOld) * partialTick
        val ez = entity.zOld + (entity.z - entity.zOld) * partialTick

        val yOffset = entity.bbHeight + pos.value - (if (entity.isCrouching) 0.23 else 0.0)

        val yaw = ((entity.tickCount + partialTick) * rotSpeed.value - 90f).toFloat()
        val camYaw = -(mc.player!!.yBodyRotO + (mc.player!!.yBodyRot - mc.player!!.yBodyRotO) * partialTick)

        val r = radius.value
        val n = angles.value

        val stack = PoseStack()
        stack.last().pose().set(ctxPose.pose())
        stack.last().normal().set(ctxPose.normal())
        stack.pushPose()
        stack.translate(ex, ey + yOffset, ez)
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(yaw))
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(camYaw))
        val localPose = stack.last()

        // cone face
        val filledVC = bufferSource.getBuffer(RenderTypes.debugFilledBox())
        val apexAlpha = if (shade.value) 0.8f else 0.5f
        val hy = height.value.toFloat()

        for (j in 0 until n) {
            val angle0 = j * Math.PI / (n / 2.0)
            val angle1 = (j + 1) * Math.PI / (n / 2.0)

            val apex = getColor(0.0, n.toDouble(), true)
            val c0   = getColor(j.toDouble(), n.toDouble(), false)
            val c1   = getColor((j + 1).toDouble(), n.toDouble(), false)

            val rx0 = (cos(angle0) * r).toFloat()
            val rz0 = (sin(angle0) * r).toFloat()
            val rx1 = (cos(angle1) * r).toFloat()
            val rz1 = (sin(angle1) * r).toFloat()

            // front face
            filledVC.addVertex(localPose, 0f,  hy,  0f).setColor(apex.r / 255f, apex.g / 255f, apex.b / 255f, apexAlpha)
            filledVC.addVertex(localPose, rx0, 0f, rz0).setColor(c0.r / 255f,   c0.g / 255f,   c0.b / 255f,   0.3f)
            filledVC.addVertex(localPose, rx1, 0f, rz1).setColor(c1.r / 255f,   c1.g / 255f,   c1.b / 255f,   0.3f)
            filledVC.addVertex(localPose, 0f,  hy,  0f).setColor(apex.r / 255f, apex.g / 255f, apex.b / 255f, apexAlpha)

            // back face
            filledVC.addVertex(localPose, 0f,  hy,  0f).setColor(apex.r / 255f, apex.g / 255f, apex.b / 255f, apexAlpha)
            filledVC.addVertex(localPose, rx1, 0f, rz1).setColor(c1.r / 255f,   c1.g / 255f,   c1.b / 255f,   0.3f)
            filledVC.addVertex(localPose, rx0, 0f, rz0).setColor(c0.r / 255f,   c0.g / 255f,   c0.b / 255f,   0.3f)
            filledVC.addVertex(localPose, 0f,  hy,  0f).setColor(apex.r / 255f, apex.g / 255f, apex.b / 255f, apexAlpha)
        }

        bufferSource.endBatch(RenderTypes.debugFilledBox())

        // rim outline
        val linesVC = bufferSource.getBuffer(RenderTypes.lines())

        for (i in 0..n) {
            val angle0 = i * Math.PI / (n / 2.0)
            val angle1 = (i + 1) * Math.PI / (n / 2.0)
            val c0 = getColor(i.toDouble(), n.toDouble(), false)
            val c1 = getColor((i + 1).toDouble(), n.toDouble(), false)

            val x0 = (cos(angle0) * r).toFloat()
            val z0 = (sin(angle0) * r).toFloat()
            val x1 = (cos(angle1) * r).toFloat()
            val z1 = (sin(angle1) * r).toFloat()

            val nx = (x1 - x0); val nz = (z1 - z0)
            val len = kotlin.math.sqrt(nx * nx + nz * nz).coerceAtLeast(0.001f)

            linesVC.addVertex(localPose, x0, 0f, z0)
                .setColor(c0.r / 255f, c0.g / 255f, c0.b / 255f, 0.5f)
                .setNormal(localPose, nx / len, 0f, nz / len)
                .setLineWidth(1.5f)

            linesVC.addVertex(localPose, x1, 0f, z1)
                .setColor(c1.r / 255f, c1.g / 255f, c1.b / 255f, 0.5f)
                .setNormal(localPose, nx / len, 0f, nz / len)
                .setLineWidth(1.5f)
        }

        bufferSource.endBatch(RenderTypes.lines())

        stack.popPose()
    }

    private fun getColor(i: Double, max: Double, first: Boolean): Color {
        return when (colorMode.value) {
            ColorMode.RAINBOW -> {
                val hsb = java.awt.Color.getHSBColor((i / max).toFloat(), 0.65f, 1.0f)
                Color(hsb.red, hsb.green, hsb.blue, hsb.alpha)
            }

            ColorMode.GRADIENT -> if (first) {
                Color(gradient1.value.r, gradient1.value.g, gradient1.value.b)
            } else {
                Color(gradient2.value.r, gradient2.value.g, gradient2.value.b)
            }

            ColorMode.THEME -> Colour.accent.value

            ColorMode.SINGLE -> Color(single.value.r, single.value.g, single.value.b)
        }
    }
}