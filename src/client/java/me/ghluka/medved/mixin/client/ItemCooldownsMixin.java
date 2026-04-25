package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.*;
import me.ghluka.medved.module.modules.movement.NoSlow;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemCooldowns;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = ItemCooldowns.class, priority = 2000)
public class ItemCooldownsMixin {
    @Inject(method = "addCooldown(Lnet/minecraft/resources/Identifier;I)V", at = @At("HEAD"), cancellable = true)
    private void dontSetCooldown(CallbackInfo ci) {
        if (NoHitDelay.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }
}