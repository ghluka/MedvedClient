package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.HitSelect;
import me.ghluka.medved.module.modules.combat.KnockbackDisplacement;
import me.ghluka.medved.module.modules.player.FakeLag;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
public class MultiPlayerGameModeMixin {

    /**
     * Intercepts attack() BEFORE the ServerboundInteractPacket is sent.
     *
     * Priority order:
     * 1. KnockbackDisplacement deferred re-fires (skipIntercept=true) bypass all gating
     *    but still notify HitSelect so it tracks the attack timing correctly.
     * 2. HitSelect ACTIVE mode cancels attacks when conditions aren't met.
     * 3. KnockbackDisplacement reschedules attacks with a spoofed yaw rotation.
     * 4. Any attack that reaches here fires normally; HitSelect's delay timer is updated.
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void medved$onAttack(Player player, Entity target, CallbackInfo ci) {
        if (!(player instanceof LocalPlayer localPlayer)) return;
        if (!(target instanceof LivingEntity living)) return;

        if (KnockbackDisplacement.skipIntercept) {
            HitSelect.notifyAttackFiring();
            FakeLag.notifyAttack();
            return;
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

        HitSelect.notifyAttackFiring();
        FakeLag.notifyAttack();
    }
}
