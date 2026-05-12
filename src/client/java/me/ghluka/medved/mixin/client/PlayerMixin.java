package me.ghluka.medved.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import me.ghluka.medved.module.modules.combat.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {
    @Unique
    private Vec3 keepSprint$preAttackVelocity = Vec3.ZERO;

    @Inject(method = "attack", at = @At("HEAD"))
    private void medved$onAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer) {
            if (Backtrack.INSTANCE.isEnabled() && Backtrack.INSTANCE.getMode().getValue() == Backtrack.Mode.LAG) {
                Backtrack.onHit(target.getId());
            }

            if (KeepSprint.INSTANCE.isEnabled()) {
                Vec3 vel = ((Entity)(Object) this).getDeltaMovement();
                keepSprint$preAttackVelocity = vel;
            }

            if (ComboTap.INSTANCE.isEnabled()) {
                ComboTap.INSTANCE.onAttack();
            }
            if (Backtrack.INSTANCE.isEnabled() && Backtrack.INSTANCE.getMode().getValue() == Backtrack.Mode.LAG) {
                Backtrack.INSTANCE.triggerLag();
            }
        }
    }

    @Redirect(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec3;multiply(DDD)Lnet/minecraft/world/phys/Vec3;"))
    private Vec3 hookSlowVelocity(Vec3 instance, double x, double y, double z) {
        if ((Object) this == Minecraft.getInstance().player && KeepSprint.INSTANCE.isEnabled()) {
            x = z = KeepSprint.INSTANCE.retain();
        }

        return instance.multiply(x, y, z);
    }

    @WrapWithCondition(method = "causeExtraKnockback", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V", ordinal = 0))
    private boolean hookSlowVelocity(Player instance, boolean b) {
        if ((Object) this == Minecraft.getInstance().player) {
            return !KeepSprint.INSTANCE.isEnabled() || b;
        }

        return true;
    }

    @ModifyExpressionValue(method = "attack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/player/Player;isSprinting()Z"))
    private boolean hookSlowVelocity(boolean original) {
        if ((Object) this == Minecraft.getInstance().player && KeepSprint.INSTANCE.isEnabled()) {
            return KeepSprint.INSTANCE.shouldReset();
        }

        return original;
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void medved$noAttackCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        if (NoHitDelay.INSTANCE.isEnabled() || KnockbackDisplacement.INSTANCE.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "getItemSwapScale", at = @At("HEAD"), cancellable = true)
    private void removeSwapCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        if (NoHitDelay.INSTANCE.isEnabled()) {
            cir.setReturnValue(1.0F);
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void medved$modifyReach(CallbackInfoReturnable<Double> cir) {
        if (!((Object) this instanceof LocalPlayer)) return;
        if (!Reach.INSTANCE.isEnabled()) return;
        double reach = Reach.INSTANCE.getTickReach();
        if (reach > 0) cir.setReturnValue(reach);
    }
}