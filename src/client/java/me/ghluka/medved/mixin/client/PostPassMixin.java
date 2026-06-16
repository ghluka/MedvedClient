package me.ghluka.medved.mixin.client;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import me.ghluka.medved.module.modules.render.ESP3D;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.Identifier;
import org.lwjgl.system.MemoryStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.ByteBuffer;
import java.util.Map;

@Mixin(PostPass.class)
public abstract class PostPassMixin {
    @Unique
    private static final int MEDVED_BLUR_CONFIG_SIZE = new Std140SizeCalculator()
            .putVec2()
            .putFloat()
            .get();

    @Shadow
    @Final
    private String name;

    @Shadow
    @Final
    private Identifier outputTargetId;

    @Shadow
    @Final
    private Map<String, GpuBuffer> customUniforms;

    @Unique
    private boolean medved$replacedBlurConfig;

    @Inject(method = "addToFrame", at = @At("HEAD"))
    private void medved$updateEntityOutlineRadius(
            FrameGraphBuilder frameGraphBuilder,
            Map<Identifier, ResourceHandle<?>> targets,
            GpuBufferSlice projection,
            CallbackInfo ci
    ) {
        if (!this.name.startsWith("minecraft:entity_outline/")) return;

        Direction direction = this.medved$outlineDirection();
        if (direction == null) return;

        float radius = ESP3D.glowPostRadius();
        if (radius <= 0.0F) radius = 2.0F;

        ByteBuffer data = this.medved$blurConfigData(direction.x, direction.y, radius);
        GpuBuffer blurConfig = this.customUniforms.get("BlurConfig");
        if (!this.medved$replacedBlurConfig) {
            if (blurConfig != null) blurConfig.close();
            blurConfig = RenderSystem.getDevice().createBuffer(
                    () -> "Medved entity outline blur config",
                    GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST,
                    data
            );
            this.customUniforms.put("BlurConfig", blurConfig);
            this.medved$replacedBlurConfig = true;
            return;
        }

        if (blurConfig != null) {
            data.rewind();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(blurConfig.slice(), data);
        }
    }

    @Unique
    private Direction medved$outlineDirection() {
        String output = this.outputTargetId.toString();
        if ("minecraft:entity_outline".equals(output)) return new Direction(1.0F, 0.0F);
        if ("swap".equals(output) || "minecraft:swap".equals(output)) return new Direction(0.0F, 1.0F);
        return null;
    }

    @Unique
    private ByteBuffer medved$blurConfigData(float dirX, float dirY, float radius) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer data = Std140Builder.onStack(stack, MEDVED_BLUR_CONFIG_SIZE)
                    .putVec2(dirX, dirY)
                    .putFloat(radius)
                    .get();
            ByteBuffer copy = ByteBuffer.allocateDirect(data.remaining());
            copy.put(data);
            copy.flip();
            return copy;
        }
    }

    @Unique
    private record Direction(float x, float y) {
    }
}
