package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.KnockbackDisplacement;
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
     * If KnockbackDisplacement is active, cancels the attack here and reschedules it
     * via RotationManager.pendingPostSendAction so it fires AFTER sendPosition enqueues
     * the spoofed-yaw position packet, ensuring the server updates its yaw before
     * processing the hit.
     */
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void medved$onAttack(Player player, Entity target, CallbackInfo ci) {
        if (KnockbackDisplacement.skipIntercept) return;
        if (!(player instanceof LocalPlayer localPlayer)) return;
        if (!(target instanceof LivingEntity living)) return;
        if (!KnockbackDisplacement.INSTANCE.isEnabled()) return;
        if (KnockbackDisplacement.scheduleAttack(localPlayer, living, (MultiPlayerGameMode) (Object) this)) {
            ci.cancel();
        }
    }
}
