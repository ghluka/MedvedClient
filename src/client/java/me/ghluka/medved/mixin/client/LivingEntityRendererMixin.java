package me.ghluka.medved.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import me.ghluka.medved.module.modules.render.Chams;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Shadow
    public abstract Identifier getTextureLocation(LivingEntityRenderState state);

    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/rendertype/RenderType;IIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;ILnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V"
            )
    )
    private void medved$submitChamsModel(
            SubmitNodeCollector collector,
            Model model,
            Object renderState,
            PoseStack poseStack,
            RenderType renderType,
            int lightCoords,
            int overlayCoords,
            int tint,
            TextureAtlasSprite textureAtlasSprite,
            int outlineColor,
            ModelFeatureRenderer.CrumblingOverlay crumblingOverlay,
            Operation<Void> original
    ) {
        original.call(
                collector,
                model,
                renderState,
                poseStack,
                renderType,
                lightCoords,
                overlayCoords,
                tint,
                textureAtlasSprite,
                outlineColor,
                crumblingOverlay
        );

        if (renderState instanceof LivingEntityRenderState livingState && Chams.shouldRender(livingState)) {
            collector.order(Chams.modelSubmitOrder()).submitModel(
                    model,
                    renderState,
                    poseStack,
                    Chams.renderType(livingState, this.getTextureLocation(livingState), livingState.appearsGlowing()),
                    lightCoords,
                    overlayCoords,
                    Chams.tint(livingState),
                    textureAtlasSprite,
                    outlineColor,
                    crumblingOverlay
            );
        }
    }

    @WrapOperation(
            method = "submit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"
            )
    )
    private void medved$submitChamsHeldItemLayer(
            RenderLayer layer,
            PoseStack poseStack,
            SubmitNodeCollector collector,
            int lightCoords,
            EntityRenderState renderState,
            float yRot,
            float xRot,
            Operation<Void> original
    ) {
        original.call(layer, poseStack, collector, lightCoords, renderState, yRot, xRot);

        if ((layer instanceof ItemInHandLayer || layer instanceof HumanoidArmorLayer || layer instanceof CapeLayer)
                && renderState instanceof LivingEntityRenderState livingState
                && Chams.shouldRender(livingState)) {
            Chams.beginLayerSubmit();
            try {
                original.call(layer, poseStack, collector, lightCoords, renderState, yRot, xRot);
            } finally {
                Chams.endLayerSubmit();
            }
        }
    }
}
