package me.ghluka.medved.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import me.ghluka.medved.module.modules.render.Chams;
import me.ghluka.medved.util.ChamsItemSubmit;
import me.ghluka.medved.util.RenderUtil;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public abstract class ItemFeatureRendererMixin {
    @WrapOperation(
            method = "prepareMainSubmit",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V"
            )
    )
    private void medved$addHeldItemChamsPass(
            VertexConsumer consumer,
            PoseStack.Pose pose,
            BakedQuad quad,
            QuadInstance quadInstance,
            Operation<Void> original,
            @Local(argsOnly = true) ItemFeatureRenderer.Submit submit
    ) {
        if (((ChamsItemSubmit) (Object) submit).medved$isChamsHeldItem()) {
            BakedQuad.MaterialInfo material = quad.materialInfo();
            RenderType originalType = material.itemRenderType();
            RenderType chamsType = Chams.itemRenderType(material.sprite().atlasLocation(), originalType.hasBlending());
            ((RenderTypeFeatureRendererAccessor) this).medved$getVertexBuilder(chamsType).putBakedQuad(pose, quad, quadInstance);
        } else {
            original.call(consumer, pose, quad, quadInstance);
        }
    }
}
