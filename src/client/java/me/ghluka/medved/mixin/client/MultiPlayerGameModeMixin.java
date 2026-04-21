package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.HitSelect;
import me.ghluka.medved.module.modules.combat.KnockbackDisplacement;
import me.ghluka.medved.module.modules.player.FakeLag;
import me.ghluka.medved.module.modules.combat.Criticals;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import me.ghluka.medved.module.modules.combat.HitSwap;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void medved$onAttack(Player player, Entity target, CallbackInfo ci) {
        if (!(player instanceof LocalPlayer localPlayer)) return;
        if (!(target instanceof LivingEntity living)) return;

        if (KnockbackDisplacement.skipIntercept) {
            HitSelect.notifyAttackFiring();
            FakeLag.notifyAttack();
            if (Criticals.INSTANCE.isEnabled()) {
                Criticals.INSTANCE.onAttack(target);
            }
        }
        // HitSelect ACTIVE mode: cancel if conditions not met
        if (HitSelect.shouldCancelAttack()) {
            ci.cancel();
            return;
        }

        // KnockbackDisplacement: reschedule attack with spoofed rotation (deferred to next tick)
        if (KnockbackDisplacement.INSTANCE.isEnabled()) {
            if (KnockbackDisplacement.scheduleAttack(localPlayer, living, (MultiPlayerGameMode) (Object) this)) {
                ci.cancel();
                return;
            }
        }

        try {
            HitSelect.notifyAttackFiring();
            FakeLag.notifyAttack();
            if (Criticals.INSTANCE.isEnabled()) {
                Criticals.INSTANCE.onAttack(target);
            }
            if (HitSwap.INSTANCE.isEnabled()) {
                HitSwap.INSTANCE.onAttack(player, target);
            }
        } catch (Throwable t) {}
    }
}
