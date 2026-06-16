package me.ghluka.medved.util

import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.SubmitNodeCollector
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.LayeringTransform
import net.minecraft.client.renderer.rendertype.OutputTarget
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.phys.AABB
import java.util.Optional
import kotlin.math.sqrt

object RenderUtil {
    enum class ChamsShader {
        GALAXY,
        VOID,
        COLOR
    }

    private val CHAMS_DEPTH_STATE = DepthStencilState(
        CompareOp.ALWAYS_PASS,
        true
    )

    class GeometrySink(
        private val collector: SubmitNodeCollector,
        private val stack: PoseStack
    ) {
        fun draw(
            renderType: RenderType,
            block: (pose: PoseStack.Pose, vc: VertexConsumer) -> Unit
        ) {
            collector.submitCustomGeometry(stack, renderType) { pose, vc ->
                block(pose, vc)
            }
        }
    }

    val ESP_FILLED: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("medved", "esp_filled"))
                .withDepthStencilState(Optional.empty())
                .withCull(false)
                .build()
        )
        RenderType.create(
            "esp_filled",
            RenderSetup.builder(pipeline)
                .sortOnUpload()
                .createRenderSetup()
        )
    }

    val ESP_LINES: RenderType by lazy {
        val pipeline = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.LINES_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("medved", "esp_lines"))
                .withDepthStencilState(Optional.empty())
                .build()
        )
        RenderType.create(
            "esp_lines",
            RenderSetup.builder(pipeline)
                .setLayeringTransform(LayeringTransform.VIEW_OFFSET_Z_LAYERING)
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup()
        )
    }

    private val shaderChamsPipelines = mutableMapOf<Pair<ChamsShader, Int>, RenderPipeline>()

    private fun shaderFragment(shader: ChamsShader): Identifier =
        Identifier.fromNamespaceAndPath(
            "medved",
            when (shader) {
                ChamsShader.GALAXY -> "core/chams_entity"
                ChamsShader.VOID -> "core/chams_void"
                ChamsShader.COLOR -> "core/chams_color"
            }
        )

    private fun shaderChamsPipeline(shader: ChamsShader, tint: Int): RenderPipeline =
        shaderChamsPipelines.getOrPut(shader to shaderTintKey(shader, tint)) {
            val builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "medved",
                        "shader_chams_entity_${shader.name.lowercase()}_${shaderTintKey(shader, tint).toUInt().toString(16)}"
                    )
                )
                .withVertexShader(Identifier.fromNamespaceAndPath("medved", "core/chams_entity"))
                .withFragmentShader(shaderFragment(shader))
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState(CompareOp.ALWAYS_PASS, true))
                .withCull(false)

            if (shader == ChamsShader.COLOR) {
                builder.withShaderDefine("CHAMS_COLOR_R", ((tint ushr 16) and 0xFF) / 255f)
                builder.withShaderDefine("CHAMS_COLOR_G", ((tint ushr 8) and 0xFF) / 255f)
                builder.withShaderDefine("CHAMS_COLOR_B", (tint and 0xFF) / 255f)
                builder.withShaderDefine("CHAMS_COLOR_A", ((tint ushr 24) and 0xFF) / 255f)
            }

            RenderPipelines.register(
                builder.build()
            )
        }

    private val VANILLA_CHAMS_PIPELINE by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("medved", "vanilla_chams_entity"))
                .withShaderDefine("PER_FACE_LIGHTING")
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withDepthStencilState(CHAMS_DEPTH_STATE)
                .build()
        )
    }

    private val shaderItemChamsPipelines = mutableMapOf<Pair<ChamsShader, Int>, RenderPipeline>()

    private fun shaderItemChamsPipeline(shader: ChamsShader, tint: Int): RenderPipeline =
        shaderItemChamsPipelines.getOrPut(shader to shaderTintKey(shader, tint)) {
            val builder = RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
                .withLocation(
                    Identifier.fromNamespaceAndPath(
                        "medved",
                        "shader_item_chams_${shader.name.lowercase()}_${shaderTintKey(shader, tint).toUInt().toString(16)}"
                    )
                )
                .withVertexShader(Identifier.fromNamespaceAndPath("medved", "core/chams_entity"))
                .withFragmentShader(shaderFragment(shader))
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, true))

            if (shader == ChamsShader.COLOR) {
                builder.withShaderDefine("CHAMS_COLOR_R", ((tint ushr 16) and 0xFF) / 255f)
                builder.withShaderDefine("CHAMS_COLOR_G", ((tint ushr 8) and 0xFF) / 255f)
                builder.withShaderDefine("CHAMS_COLOR_B", (tint and 0xFF) / 255f)
                builder.withShaderDefine("CHAMS_COLOR_A", ((tint ushr 24) and 0xFF) / 255f)
            }

            RenderPipelines.register(
                builder.build()
            )
        }

    private val ITEM_CHAMS_PIPELINE by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("medved", "item_chams"))
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withDepthStencilState(CHAMS_DEPTH_STATE)
                .build()
        )
    }

    private val ITEM_CHAMS_TRANSLUCENT_PIPELINE by lazy {
        RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.ITEM_SNIPPET)
                .withLocation(Identifier.fromNamespaceAndPath("medved", "item_chams_translucent"))
                .withShaderDefine("ALPHA_CUTOUT", 0.1f)
                .withColorTargetState(ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(CHAMS_DEPTH_STATE)
                .build()
        )
    }

    private data class ShaderEntityKey(val texture: Identifier, val outline: Boolean, val shader: ChamsShader, val tint: Int)
    private data class ShaderItemKey(val texture: Identifier, val translucent: Boolean, val shader: ChamsShader, val tint: Int)

    private val shaderChamsEntityCache = mutableMapOf<ShaderEntityKey, RenderType>()
    private val vanillaChamsEntityCache = mutableMapOf<Pair<Identifier, Boolean>, RenderType>()
    private val itemChamsCache = mutableMapOf<Pair<Identifier, Boolean>, RenderType>()
    private val shaderItemChamsCache = mutableMapOf<ShaderItemKey, RenderType>()

    fun shaderChamsEntity(texture: Identifier, outline: Boolean, shader: ChamsShader, tint: Int = 0xFFFFFFFF.toInt()): RenderType =
        shaderChamsEntityCache.getOrPut(ShaderEntityKey(texture, outline, shader, shaderTintKey(shader, tint))) {
            val setup = RenderSetup.builder(shaderChamsPipeline(shader, tint))
                .withTexture("Sampler0", texture)
                .sortOnUpload()
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .setOutline(
                    if (outline) RenderSetup.OutlineProperty.AFFECTS_OUTLINE
                    else RenderSetup.OutlineProperty.NONE
                )
                .createRenderSetup()
            RenderType.create("medved_shader_chams_entity_${shader.name.lowercase()}_${shaderTintKey(shader, tint).toUInt().toString(16)}", setup)
        }

    fun vanillaChamsEntity(texture: Identifier, outline: Boolean): RenderType =
        vanillaChamsEntityCache.getOrPut(texture to outline) {
            val setup = RenderSetup.builder(VANILLA_CHAMS_PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .sortOnUpload()
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .setOutline(
                    if (outline) RenderSetup.OutlineProperty.AFFECTS_OUTLINE
                    else RenderSetup.OutlineProperty.NONE
                )
                .createRenderSetup()
            RenderType.create("medved_vanilla_chams_entity", setup)
        }

    fun itemChams(texture: Identifier, translucent: Boolean): RenderType =
        itemChamsCache.getOrPut(texture to translucent) {
            val setup = RenderSetup.builder(if (translucent) ITEM_CHAMS_TRANSLUCENT_PIPELINE else ITEM_CHAMS_PIPELINE)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .createRenderSetup()
            RenderType.create(if (translucent) "medved_item_chams_translucent" else "medved_item_chams", setup)
        }

    fun shaderItemChams(texture: Identifier, translucent: Boolean, shader: ChamsShader, tint: Int = 0xFFFFFFFF.toInt()): RenderType =
        shaderItemChamsCache.getOrPut(ShaderItemKey(texture, translucent, shader, shaderTintKey(shader, tint))) {
            val setup = RenderSetup.builder(shaderItemChamsPipeline(shader, tint))
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .sortOnUpload()
                .setOutputTarget(OutputTarget.ITEM_ENTITY_TARGET)
                .setOutline(RenderSetup.OutlineProperty.AFFECTS_OUTLINE)
                .createRenderSetup()
            RenderType.create(
                if (translucent) "medved_shader_item_chams_${shader.name.lowercase()}_${shaderTintKey(shader, tint).toUInt().toString(16)}_translucent"
                else "medved_shader_item_chams_${shader.name.lowercase()}_${shaderTintKey(shader, tint).toUInt().toString(16)}",
                setup
            )
        }

    private fun shaderTintKey(shader: ChamsShader, tint: Int): Int =
        if (shader == ChamsShader.COLOR) tint else 0xFFFFFFFF.toInt()

    inline fun worldContext(
        ctx: LevelRenderContext,
        block: (pose: PoseStack.Pose, stack: PoseStack, sink: GeometrySink) -> Unit
    ) {
        val camera = ctx.levelState().cameraRenderState.pos
        ctx.poseStack().pushPose()
        ctx.poseStack().translate(-camera.x, -camera.y, -camera.z)
        block(ctx.poseStack().last(), ctx.poseStack(), GeometrySink(ctx.submitNodeCollector(), ctx.poseStack()))
        ctx.poseStack().popPose()
    }

    inline fun worldContext(ctx: LevelRenderContext, block: (pose: PoseStack.Pose, sink: GeometrySink) -> Unit) {
        val camera = ctx.levelState().cameraRenderState.pos
        ctx.poseStack().pushPose()
        ctx.poseStack().translate(-camera.x, -camera.y, -camera.z)
        block(ctx.poseStack().last(), GeometrySink(ctx.submitNodeCollector(), ctx.poseStack()))
        ctx.poseStack().popPose()
    }

    fun boxFilledBothSides(
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
            vc.addVertex(pose, dx, dy, dz).setColor(r, g, b, a)
            vc.addVertex(pose, cx, cy, cz).setColor(r, g, b, a)
            vc.addVertex(pose, bx, by, bz).setColor(r, g, b, a)
            vc.addVertex(pose, ax, ay, az).setColor(r, g, b, a)
        }

        quad(x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0) // bottom
        quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1) // top
        quad(x0, y1, z0, x0, y0, z0, x1, y0, z0, x1, y1, z0) // north
        quad(x1, y1, z1, x1, y0, z1, x0, y0, z1, x0, y1, z1) // south
        quad(x0, y1, z1, x0, y0, z1, x0, y0, z0, x0, y1, z0) // west
        quad(x1, y1, z0, x1, y0, z0, x1, y0, z1, x1, y1, z1) // east
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
