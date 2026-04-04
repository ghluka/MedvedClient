package me.ghluka.medved.util

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.rendertype.RenderTypes
import net.minecraft.world.phys.AABB
import kotlin.math.sqrt

object RenderUtil {

    inline fun worldContext(ctx: LevelRenderContext, block: (pose: PoseStack.Pose, buf: MultiBufferSource.BufferSource) -> Unit) {
        val camera = Minecraft.getInstance().gameRenderer.mainCamera.position()
        ctx.poseStack().pushPose()
        ctx.poseStack().translate(-camera.x, -camera.y, -camera.z)
        block(ctx.poseStack().last(), ctx.bufferSource())
        ctx.poseStack().popPose()
    }

    fun boxOutline(
        vc: VertexConsumer, pose: PoseStack.Pose,
        box: AABB, r: Float, g: Float, b: Float, a: Float,
        lineWidth: Float = 1.5f
    ) {
        val x0 = box.minX.toFloat(); val y0 = box.minY.toFloat(); val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat(); val y1 = box.maxY.toFloat(); val z1 = box.maxZ.toFloat()

        fun seg(ax: Float, ay: Float, az: Float, bx: Float, by: Float, bz: Float) {
            val dx = bx - ax; val dy = by - ay; val dz = bz - az
            val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
            vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(lineWidth)
            vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(lineWidth)
        }

        // bottom
        seg(x0, y0, z0, x1, y0, z0); seg(x1, y0, z0, x1, y0, z1)
        seg(x1, y0, z1, x0, y0, z1); seg(x0, y0, z1, x0, y0, z0)
        // top
        seg(x0, y1, z0, x1, y1, z0); seg(x1, y1, z0, x1, y1, z1)
        seg(x1, y1, z1, x0, y1, z1); seg(x0, y1, z1, x0, y1, z0)
        // verticals
        seg(x0, y0, z0, x0, y1, z0); seg(x1, y0, z0, x1, y1, z0)
        seg(x1, y0, z1, x1, y1, z1); seg(x0, y0, z1, x0, y1, z1)
    }

    fun boxFilled(
        vc: VertexConsumer, pose: PoseStack.Pose,
        box: AABB, r: Float, g: Float, b: Float, a: Float
    ) {
        val x0 = box.minX.toFloat(); val y0 = box.minY.toFloat(); val z0 = box.minZ.toFloat()
        val x1 = box.maxX.toFloat(); val y1 = box.maxY.toFloat(); val z1 = box.maxZ.toFloat()

        fun quad(
            ax: Float, ay: Float, az: Float,
            bx: Float, by: Float, bz: Float,
            cx: Float, cy: Float, cz: Float,
            dx: Float, dy: Float, dz: Float
        ) {
            vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
            vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a)
            vc.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
            vc.addVertex(pose, dx, dy, dz).setColor(r, g, b, a)
        }

        quad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0) // bottom
        quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1) // top
        quad(x0, y1, z0, x0, y0, z0, x1, y0, z0, x1, y1, z0) // north
        quad(x1, y1, z1, x1, y0, z1, x0, y0, z1, x0, y1, z1) // south
        quad(x0, y1, z1, x0, y0, z1, x0, y0, z0, x0, y1, z0) // west
        quad(x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1) // east
    }

    fun line(
        vc: VertexConsumer, pose: PoseStack.Pose,
        x0: Float, y0: Float, z0: Float,
        x1: Float, y1: Float, z1: Float,
        r: Float, g: Float, b: Float, a: Float,
        lineWidth: Float = 1.5f
    ) {
        val dx = x1 - x0; val dy = y1 - y0; val dz = z1 - z0
        val len = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001f)
        vc.addVertex(pose, x0, y0, z0).setColor(r, g, b, a).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(lineWidth)
        vc.addVertex(pose, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, dx / len, dy / len, dz / len).setLineWidth(lineWidth)
    }
}
