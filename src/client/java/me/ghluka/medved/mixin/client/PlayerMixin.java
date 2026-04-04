package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.NoHitDelay;
import me.ghluka.medved.module.modules.combat.Reach;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public class PlayerMixin {

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
}
