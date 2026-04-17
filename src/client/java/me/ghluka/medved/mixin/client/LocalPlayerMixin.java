package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.Criticals;
import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public class LocalPlayerMixin {

    @Inject(method = "aiStep", at = @At("HEAD"))
    private void medved$preAiStep(CallbackInfo ci) {
        float override = RotationManager.physicsYawOverride;
        if (!RotationManager.perspective && !Float.isNaN(override)) {
            ((LocalPlayer)(Object)this).setYRot(override);
        }
    }

    @Inject(method = "aiStep", at = @At("RETURN"))
    private void medved$postAiStep(CallbackInfo ci) {
        if (!RotationManager.perspective && !Float.isNaN(RotationManager.physicsYawOverride)) {
            ((LocalPlayer)(Object)this).setYRot(RotationManager.getClientYaw());
            RotationManager.physicsYawOverride = Float.NaN;
        }
    }

    @Inject(method = "sendPosition", at = @At("HEAD"))
    private void medved$preSendPosition(CallbackInfo ci) {
        RotationManager.applyOverride((LocalPlayer) (Object) this);
        if (Criticals.INSTANCE.isEnabled() && Criticals.INSTANCE.getMode().getValue() == Criticals.Mode.NO_GROUND) {
            Criticals.cachedOnGround = ((LocalPlayer)(Object)this).onGround();
            ((LocalPlayer)(Object)this).setOnGround(false);
        }
    }

    @Inject(method = "sendPosition", at = @At("RETURN"))
    private void medved$postSendPosition(CallbackInfo ci) {
        RotationManager.restoreRotation((LocalPlayer) (Object) this);
        LocalPlayer lp = (LocalPlayer)(Object)this;
        if (Criticals.INSTANCE.isEnabled() && Criticals.INSTANCE.getMode().getValue() == Criticals.Mode.NO_GROUND) {
            lp.setOnGround(Criticals.cachedOnGround);
        }
    }
}
