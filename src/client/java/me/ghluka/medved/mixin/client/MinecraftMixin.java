package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Inject(method = "pick", at = @At("RETURN"))
    private void medved$overrideHitResult(float partialTick, CallbackInfo ci) {
        if (RotationManager.isActive()) {
            RotationManager.updateHitResult();
        }
    }
}
