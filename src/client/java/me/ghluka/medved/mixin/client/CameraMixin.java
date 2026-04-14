package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import me.ghluka.medved.util.CameraOverriddenEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    boolean firstTime = true;

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;setRotation(FF)V", ordinal = 1, shift = At.Shift.AFTER))
    public void lockRotation(float f, CallbackInfo ci) {
        if (RotationManager.perspective &&
            this.entity instanceof LocalPlayer) {

            CameraOverriddenEntity cameraOverriddenEntity = (CameraOverriddenEntity) this.entity;

            if (RotationManager.firstTime && Minecraft.getInstance().player != null) {
                cameraOverriddenEntity.medved$setCameraPitch(Minecraft.getInstance().player.getXRot());
                cameraOverriddenEntity.medved$setCameraYaw(Minecraft.getInstance().player.getYRot());
                RotationManager.firstTime = false;
            }
            this.setRotation(cameraOverriddenEntity.medved$getCameraYaw(), cameraOverriddenEntity.medved$getCameraPitch());

        }
        if (!RotationManager.perspective && this.entity instanceof LocalPlayer) {
            RotationManager.firstTime = true;
        }
    }
}
