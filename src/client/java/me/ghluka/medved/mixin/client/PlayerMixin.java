package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.ComboTap;
import me.ghluka.medved.module.modules.combat.NoHitDelay;
import me.ghluka.medved.module.modules.combat.Reach;
import me.ghluka.medved.module.modules.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

    @Inject(method = "attack", at = @At("HEAD"))
    private void medved$onAttack(Entity target, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer && ComboTap.INSTANCE.isEnabled()) {
            ComboTap.INSTANCE.onAttack();
        }
    }

    @Inject(method = "getAttackStrengthScale", at = @At("HEAD"), cancellable = true)
    private void medved$noAttackCooldown(float adjustTicks, CallbackInfoReturnable<Float> cir) {
        if (NoHitDelay.INSTANCE.isEnabled()) {
            cir.setReturnValue(1.0f);
        }
    }

    @Inject(method = "entityInteractionRange", at = @At("RETURN"), cancellable = true)
    private void medved$modifyReach(CallbackInfoReturnable<Double> cir) {
        if (!((Object) this instanceof LocalPlayer)) return;
        if (!Reach.INSTANCE.isEnabled()) return;
        double reach = Reach.INSTANCE.getTickReach();
        if (reach > 0) cir.setReturnValue(reach);
    }

    /**
     * The 0.2× movement-speed penalty while using an item (bow draw, eating,
     * drinking potions) is applied in Player.aiStep() via an isUsingItem() check.
     * Since LivingEntity declares the method, the call-site bytecode target is
     * LivingEntity.isUsingItem(). Redirect it to return false when NoSlow is on.
     */
    @Redirect(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isUsingItem()Z"),
        require = 0
    )
    private boolean medved$noSlowItemUse(LivingEntity entity) {
        if (entity instanceof LocalPlayer && NoSlow.active) return false;
        return entity.isUsingItem();
    }
}