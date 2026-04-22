package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import me.ghluka.medved.util.CameraOverriddenEntity;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public class EntityMixin implements CameraOverriddenEntity {
    @Unique
    private float cameraPitch;

    @Unique
    private float cameraYaw;

    @Unique
    private boolean turnCancelled = false;

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void changeCameraLookDirection(double xDelta, double yDelta, CallbackInfo ci) {
        turnCancelled = false;
        if (RotationManager.perspective && (Object) this instanceof LocalPlayer) {
            double pitchDelta = (yDelta * 0.15);
            double yawDelta = (xDelta * 0.15);

            this.cameraPitch = Mth.clamp(this.cameraPitch + (float) pitchDelta, -90.0f, 90.0f);
            this.cameraYaw += (float) yawDelta;

            ci.cancel();
        }
    }

    @Inject(method = "turn", at = @At("RETURN"))
    private void medved$postTurn(double yaw, double pitch, CallbackInfo ci) {
        if (turnCancelled) {
            turnCancelled = false;
            return;
        }
        if ((Object) this instanceof LocalPlayer) {
            RotationManager.onTurn((LocalPlayer)(Object) this);
        }
    }

    @Override
    @Unique
    public float medved$getCameraPitch() {
        return this.cameraPitch;
    }

    @Override
    @Unique
    public float medved$getCameraYaw() {
        return this.cameraYaw;
    }

    @Override
    @Unique
    public void medved$setCameraPitch(float pitch) {
        this.cameraPitch = pitch;
    }

    @Override
    @Unique
    public void medved$setCameraYaw(float yaw) {
        this.cameraYaw = yaw;
    }
}
