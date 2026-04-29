package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import me.ghluka.medved.util.CameraOverriddenEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Camera.class, priority = 2001)
public abstract class CameraMixin {

    @Shadow
    private Entity entity;

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    @Shadow
    private float fov;
    @Shadow
    private float hudFov;
    @Shadow
    private float depthFar;
    @Shadow
    private boolean initialized;
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private Vec3 position;
    @Shadow
    private Matrix4f cachedViewRotMatrix;

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V"
            )
    )
    private void redirectAlignWithEntity(Camera instance, float partialTicks) {

        System.out.println("[medved] redirectAlignWithEntity called, perspective=" + RotationManager.perspective + ", entity=" + (this.entity instanceof LocalPlayer));
        CameraAccessor invoker = (CameraAccessor)(Object)this;

        if (this.minecraft != null && RotationManager.perspective && this.entity instanceof LocalPlayer) {
            CameraOverriddenEntity e = (CameraOverriddenEntity) this.entity;

            if (RotationManager.firstTime && this.minecraft.player != null) {
                e.medved$setCameraPitch(this.minecraft.player.getXRot());
                e.medved$setCameraYaw(this.minecraft.player.getYRot());
                RotationManager.firstTime = false;
            }

            this.setRotation(e.medved$getCameraYaw(), e.medved$getCameraPitch());
            invoker.medved$setPosition(
                    Mth.lerp(partialTicks, this.entity.xo, this.entity.getX()),
                    Mth.lerp(partialTicks, this.entity.yo, this.entity.getY()) + Mth.lerp(partialTicks, invoker.medved$getEyeHeightOld(), invoker.medved$getEyeHeight()),
                    Mth.lerp(partialTicks, this.entity.zo, this.entity.getZ())
            );
        } else {
            if (this.entity instanceof LocalPlayer) {
                RotationManager.firstTime = true;
            }
            invoker.medved$alignWithEntity(partialTicks);
        }
    }
}