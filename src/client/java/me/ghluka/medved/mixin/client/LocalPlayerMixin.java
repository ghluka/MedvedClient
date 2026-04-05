package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void medved$preSendPosition(CallbackInfo ci) {
        RotationManager.applyOverride((LocalPlayer) (Object) this);
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void medved$postSendPosition(CallbackInfo ci) {
        RotationManager.restoreRotation((LocalPlayer) (Object) this);
    }
}
