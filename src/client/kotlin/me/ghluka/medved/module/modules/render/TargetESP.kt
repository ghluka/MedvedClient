package me.ghluka.medved.module.modules.render

import com.mojang.blaze3d.vertex.PoseStack
import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.combat.SilentAura
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RenderUtil
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object TargetESP : Module(
    name = "Target ESP",
    description = "Draws a moving halo around the current combat target",
    category = Category.RENDER
) {
    private val color = color("color", Color(255, 255, 255, 210), allowAlpha = true)
    private val speed = double("speed", 0.25, 0.25, 1.0)
    private val lineWidth = double("line width", 2.0, 0.5, 6.0)
    private val expand = double("expand", 0.12, 0.0, 0.55)
    private val glowRings = int("glow rings", 4, 1, 8)
    private val lingerMs = int("linger ms", 700, 100, 2500)
    private val segments = int("smoothness", 72, 24, 160)

    private var recentTarget: LivingEntity? = null
    private var recentTargetAtMs = 0L

    override fun onDisabled() {
        recentTarget = null
        recentTargetAtMs = 0L
    }

    @JvmStatic
    fun notifyAttack(target: Entity) {
        val living = target as? LivingEntity ?: return
        recentTarget = living
        recentTargetAtMs = System.currentTimeMillis()
    }

    override fun onLevelRender(ctx: LevelRenderContext) {
        val mc = Minecraft.getInstance()
        val viewer = mc.player ?: return
        val target = activeTarget(viewer) ?: return
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)

        val ex = target.xOld + (target.x - target.xOld) * partialTick
        val ey = target.yOld + (target.y - target.yOld) * partialTick
        val ez = target.zOld + (target.z - target.zOld) * partialTick

        val halfW = target.bbWidth / 2.0 + expand.value
        val minY = ey + 0.05
        val maxY = ey + target.bbHeight - 0.05
        val height = (maxY - minY).coerceAtLeast(0.1)
        val y = minY + height * movingHeightFactor()

        val live = color.liveColor(color.value)
        val r = live.r / 255f
        val g = live.g / 255f
        val b = live.b / 255f
        val baseAlpha = live.a / 255f
        val baseWidth = lineWidth.value.toFloat()
        val ringCount = glowRings.value.coerceAtLeast(1)

        RenderUtil.worldContext(ctx) { pose, sink ->
            sink.draw(RenderUtil.ESP_LINES) { drawPose, vc ->
                for (i in ringCount downTo 1) {
                    val t = i.toFloat() / ringCount.toFloat()
                    val ringExpand = (1f - t) * 0.16f
                    val alpha = baseAlpha * (0.16f + t * 0.72f)
                    val width = baseWidth * (0.7f + (1f - t) * 1.6f)
                    drawHaloRing(
                        vc = vc,
                        pose = drawPose,
                        cx = ex,
                        cy = y + (1f - t) * 0.015,
                        cz = ez,
                        rx = halfW + ringExpand,
                        rz = halfW + ringExpand,
                        red = r,
                        green = g,
                        blue = b,
                        alpha = alpha.coerceIn(0f, 1f),
                        width = width,
                    )
                }
            }
        }
    }

    private fun activeTarget(viewer: Player): LivingEntity? {
        val auraTarget = SilentAura.target
        if (SilentAura.isEnabled() && auraTarget != null && isRenderableTarget(viewer, auraTarget)) {
            return auraTarget
        }

        val target = recentTarget ?: return null
        if (System.currentTimeMillis() - recentTargetAtMs > lingerMs.value) return null
        return target.takeIf { isRenderableTarget(viewer, it) }
    }

    private fun isRenderableTarget(viewer: Player, target: LivingEntity): Boolean {
        if (target === viewer) return false
        if (target.isRemoved || target.isDeadOrDying) return false
        return TargetFilter.isValidTarget(viewer, target)
    }

    private fun movingHeightFactor(): Double {
        val seconds = System.nanoTime() / 1_000_000_000.0
        val phase = seconds * speed.value * PI * 2.0
        return (sin(phase - PI / 2.0) + 1.0) * 0.5
    }

    private fun drawHaloRing(
        vc: com.mojang.blaze3d.vertex.VertexConsumer,
        pose: PoseStack.Pose,
        cx: Double,
        cy: Double,
        cz: Double,
        rx: Double,
        rz: Double,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float,
        width: Float,
    ) {
        val count = segments.value.coerceIn(12, 192)
        var prevX = (cx + cos(0.0) * rx).toFloat()
        var prevZ = (cz + sin(0.0) * rz).toFloat()
        val fy = cy.toFloat()

        for (i in 1..count) {
            val angle = i.toDouble() / count.toDouble() * PI * 2.0
            val x = (cx + cos(angle) * rx).toFloat()
            val z = (cz + sin(angle) * rz).toFloat()
            RenderUtil.line(vc, pose, prevX, fy, prevZ, x, fy, z, red, green, blue, alpha, width)
            prevX = x
            prevZ = z
        }
    }
}
