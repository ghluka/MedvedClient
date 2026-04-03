package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "turn", at = @At("RETURN"))
    private void medved$onTurn(double yaw, double pitch, CallbackInfo ci) {
        if ((Object) this instanceof LocalPlayer player) {
            RotationManager.onTurn(player);
        }
    }
}
