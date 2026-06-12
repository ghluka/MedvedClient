package me.ghluka.medved.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Cancellable;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.ghluka.medved.module.modules.combat.AutoBlock;
import me.ghluka.medved.module.modules.render.Animations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public class ItemInHandRendererMixin {
    
    @Final
    @Shadow
    private Minecraft minecraft;

    @Shadow
    private ItemStack offHandItem;

    @Shadow
    @Final
    private static float ITEM_POS_Y;
    
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER))
    private void hookRenderFirstPersonItem(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (Animations.INSTANCE.isEnabled()) {
            var isInBothHands = InteractionHand.MAIN_HAND == hand && item.has(DataComponents.MAP_ID) && offHandItem.isEmpty();
            boolean mainHand = Animations.INSTANCE.getMainHand().getValue();
            boolean offHand = Animations.INSTANCE.getOffHand().getValue();
            if (isInBothHands && mainHand && offHand) {
                medved$applyTransformations(matrices,
                        (Animations.INSTANCE.getMainHandX().getValue() + Animations.INSTANCE.getOffHandX().getValue()) / 2f,
                        (Animations.INSTANCE.getMainHandY().getValue() + Animations.INSTANCE.getOffHandY().getValue()) / 2f,
                        (Animations.INSTANCE.getMainHandScale().getValue() + Animations.INSTANCE.getOffHandScale().getValue()) / 2f,
                        (Animations.INSTANCE.getMainHandRotX().getValue() + Animations.INSTANCE.getOffHandRotX().getValue()) / 2f,
                        (Animations.INSTANCE.getMainHandRotY().getValue() + Animations.INSTANCE.getOffHandRotY().getValue()) / 2f,
                        (Animations.INSTANCE.getMainHandRotZ().getValue() + Animations.INSTANCE.getOffHandRotZ().getValue()) / 2f
                );
            } else if (isInBothHands && mainHand) {
                matrices.translate(0f, 0f, Animations.INSTANCE.getMainHandScale().getValue());
            } else if (InteractionHand.MAIN_HAND == hand && mainHand) {
                medved$applyTransformations(matrices,
                        Animations.INSTANCE.getMainHandX().getValue(),
                        Animations.INSTANCE.getMainHandY().getValue(),
                        Animations.INSTANCE.getMainHandScale().getValue(),
                        Animations.INSTANCE.getMainHandRotX().getValue(),
                        Animations.INSTANCE.getMainHandRotY().getValue(),
                        Animations.INSTANCE.getMainHandRotZ().getValue()
                );
            } else if (offHand) {
                medved$applyTransformations(matrices,
                        Animations.INSTANCE.getOffHandX().getValue(),
                        Animations.INSTANCE.getOffHandY().getValue(),
                        Animations.INSTANCE.getOffHandScale().getValue(),
                        Animations.INSTANCE.getOffHandRotX().getValue(),
                        Animations.INSTANCE.getOffHandRotY().getValue(),
                        Animations.INSTANCE.getOffHandRotZ().getValue()
                );
            }
        }
    }

    @Unique
    private static void medved$applyTransformations(PoseStack matrices, float translateX, float translateY, float translateZ, float rotateX, float rotateY, float rotateZ) {
        matrices.translate(translateX, translateY, translateZ);
        matrices.mulPose(Axis.XP.rotationDegrees(rotateX));
        matrices.mulPose(Axis.YP.rotationDegrees(rotateY));
        matrices.mulPose(Axis.ZP.rotationDegrees(rotateZ));
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private float medved$cancelEquipProgress(
            float equipProgress,
            @Local(argsOnly = true) AbstractClientPlayer player,
            @Local(argsOnly = true) InteractionHand hand
    ) {
        if (Animations.INSTANCE.isEnabled()
                && Animations.INSTANCE.getCancelEquipProgress().getValue()
                && Animations.shouldUseBlockAnimationItem(player.getItemInHand(hand))) {
            return 0.0f;
        }
        return equipProgress;
    }

    @Inject(method = "submitArmWithItem",
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;")),
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V", ordinal = 0, shift = At.Shift.AFTER))
    private void transformBlockAnimation(
            AbstractClientPlayer player, float tickProgress, float pitch, InteractionHand hand, float swingProgress, ItemStack item, float equipProgress, PoseStack matrices, SubmitNodeCollector orderedRenderCommandQueue, int light, CallbackInfo ci) {
        if (Animations.shouldUseBlockAnimationItem(player.getItemInHand(hand))) {
            var arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();

            if (Animations.INSTANCE.isEnabled()) {
                Animations.INSTANCE.transform(matrices, arm, equipProgress, swingProgress);
            } else {
                // Default animation
                Animations.OneSevenDefault.INSTANCE.transform(matrices, arm, equipProgress, swingProgress);
            }
        }
    }

    @ModifyExpressionValue(method = "submitArmWithItem", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;",
            ordinal = 0
    ))
    private ItemUseAnimation hookUseAction(ItemUseAnimation original, @Local(argsOnly = true, ordinal = 2) float swingProgress, @Local(argsOnly = true) AbstractClientPlayer entity) {
        if (Animations.shouldAnimateSwordBlock(entity, swingProgress)) {
            return ItemUseAnimation.BLOCK;
        }
        return original;
    }

    @ModifyExpressionValue(method = "submitArmWithItem", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z",
            ordinal = 1
    ))
    private boolean hookIsUseItem(boolean original, @Local(argsOnly = true, ordinal = 2) float swingProgress, @Local(argsOnly = true) AbstractClientPlayer entity) {
        if (Animations.shouldAnimateSwordBlock(entity, swingProgress)) {
            return true;
        }

        return original;
    }

    @ModifyExpressionValue(method = "submitArmWithItem", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;",
            ordinal = 1
    ))
    private InteractionHand hookActiveHand(InteractionHand original, @Local(argsOnly = true, ordinal = 2) float swingProgress, @Local(argsOnly = true) AbstractClientPlayer entity) {
        if (Animations.shouldAnimateSwordBlock(entity, swingProgress)) {
            return InteractionHand.MAIN_HAND;
        }

        return original;
    }

    @ModifyExpressionValue(method = "submitArmWithItem", at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUseItemRemainingTicks()I",
            ordinal = 2
    ))
    private int hookItemUseItem(int original, @Local(argsOnly = true, ordinal = 2) float swingProgress, @Local(argsOnly = true) AbstractClientPlayer entity) {
        if (Animations.shouldAnimateSwordBlock(entity, swingProgress)) {
            return 7200;
        }

        return original;
    }

    @Inject(method = "itemUsed", at = @At("HEAD"), cancellable = true)
    public void itemUsed(final InteractionHand hand, CallbackInfo ci) {
        if (Animations.INSTANCE.isEnabled())
            ci.cancel();
    }
}
