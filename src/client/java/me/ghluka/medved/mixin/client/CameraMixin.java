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
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=Camera.class, priority = 1)
public abstract class CameraMixin {
    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Redirect(
            method = "alignWithEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;setRotation(FF)V"
            )
    )
    public void redirectRotation(Camera instance, float yaw, float pitch) {
        if (RotationManager.perspective && this.entity instanceof LocalPlayer) {

            CameraOverriddenEntity e = (CameraOverriddenEntity) this.entity;

            if (RotationManager.firstTime && Minecraft.getInstance().player != null) {
                e.medved$setCameraPitch(Minecraft.getInstance().player.getXRot());
                e.medved$setCameraYaw(Minecraft.getInstance().player.getYRot());
                RotationManager.firstTime = false;
            }

            setRotation(
                    e.medved$getCameraYaw(),
                    e.medved$getCameraPitch()
            );

        } else {
            setRotation(yaw, pitch);

            if (this.entity instanceof LocalPlayer) {
                RotationManager.firstTime = true;
            }
        }
    }

}
