package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.AutoBlock;
import me.ghluka.medved.module.modules.combat.Criticals;
import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Unique
    private int debounce = 0;

    @Inject(method = "modifyInput", at = @At("RETURN"), cancellable = true)
    private void applyBlockingSlowdown(Vec2 input, CallbackInfoReturnable<Vec2> cir) {
        if (input.lengthSquared() == 0.0F) {
            cir.setReturnValue(input);
        } else {
            LocalPlayerAccessor g = (LocalPlayerAccessor)this;
            Vec2 newInput = input.scale(0.98F);
            if (g.medved$isUsingItem()
                    && Minecraft.getInstance().player != null
                    && !Minecraft.getInstance().player.isPassenger()) {
                newInput = newInput.scale(g.medved$itemUseSpeedMultiplier());
            }
            else if (AutoBlock.getSlowingDown() && AutoBlock.slowDown.getValue()) {
                debounce = 5;
                newInput = newInput.scale(g.medved$itemUseSpeedMultiplier());
            }
            else if (debounce > 0) {
                debounce -= 1;
                newInput = newInput.scale(g.medved$itemUseSpeedMultiplier());
            }

            if (g.medved$isMovingSlowly()) {
                float sneakingMovementFactor = (float)((LivingEntityAccessor)g)
                        .medved$getAttributeValue(Attributes.SNEAKING_SPEED);
                newInput = newInput.scale(sneakingMovementFactor);
            }

            cir.setReturnValue(g.medved$modifyInputSpeedForSquareMovement(newInput));
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void medved$preAiStep(CallbackInfo ci) {
        float override = RotationManager.physicsYawOverride;
        if (!RotationManager.perspective && !Float.isNaN(override)) {
            ((LocalPlayer)(Object)this).setYRot(override);
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void medved$postAiStep(CallbackInfo ci) {
        if (!RotationManager.perspective && !Float.isNaN(RotationManager.physicsYawOverride)) {
            ((LocalPlayer)(Object)this).setYRot(RotationManager.getClientYaw());
            RotationManager.physicsYawOverride = Float.NaN;
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void medved$preSendPosition(CallbackInfo ci) {
        RotationManager.applyOverride((LocalPlayer) (Object) this);
        if (Criticals.INSTANCE.isEnabled() && Criticals.INSTANCE.getMode().getValue() == Criticals.Mode.NO_GROUND) {
            Criticals.cachedOnGround = ((LocalPlayer)(Object)this).onGround();
            ((LocalPlayer)(Object)this).setOnGround(false);
        }
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void medved$postSendPosition(CallbackInfo ci) {
        RotationManager.restoreRotation((LocalPlayer) (Object) this);
        LocalPlayer lp = (LocalPlayer)(Object)this;
        if (Criticals.INSTANCE.isEnabled() && Criticals.INSTANCE.getMode().getValue() == Criticals.Mode.NO_GROUND) {
            lp.setOnGround(Criticals.cachedOnGround);
        }
    }
}
