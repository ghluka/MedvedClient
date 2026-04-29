package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import me.ghluka.medved.util.CameraOverriddenEntity;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.Camera;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.vehicle.minecart.Minecart;
import net.minecraft.world.entity.vehicle.minecart.NewMinecartBehavior;
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
    private boolean detached;
    @Shadow
    private Minecraft minecraft;
    @Shadow
    private Vec3 position;
    @Shadow
    private Matrix4f cachedViewRotMatrix;
    @Shadow
    private float xRot;
    @Shadow
    private float yRot;

    @Redirect(
            method = "update",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V"
            )
    )
    private void redirectAlignWithEntity(Camera instance, float partialTicks) {
        CameraAccessor invoker = (CameraAccessor)(Object)this;

        if (this.minecraft != null && RotationManager.perspective && this.entity instanceof LocalPlayer) {
            CameraOverriddenEntity e = (CameraOverriddenEntity) this.entity;

            if (RotationManager.firstTime && this.minecraft.player != null) {
                e.medved$setCameraPitch(this.minecraft.player.getXRot());
                e.medved$setCameraYaw(this.minecraft.player.getYRot());
                RotationManager.firstTime = false;
            }

            this.setRotation(e.medved$getCameraYaw(), e.medved$getCameraPitch());
            alignWithEntityNoRot(partialTicks);
        } else {
            if (this.entity instanceof LocalPlayer) {
                RotationManager.firstTime = true;
            }
            invoker.medved$alignWithEntity(partialTicks);
        }
        this.fov = invoker.medved$calculateFov(partialTicks);
        this.hudFov = invoker.medved$calculateHudFov(partialTicks);
        invoker.medved$prepareCullFrustum(invoker.medved$getViewRotationMatrix(this.cachedViewRotMatrix), invoker.medved$createProjectionMatrixForCulling(), this.position);
        float windowWidth = this.minecraft.getWindow().getWidth();
        float windowHeight = this.minecraft.getWindow().getHeight();
        invoker.medved$setupPerspective(0.05F, this.depthFar, this.fov, windowWidth, windowHeight);
        this.initialized = true;
    }


    private void alignWithEntityNoRot(final float partialTicks) {
        CameraAccessor invoker = (CameraAccessor)(Object)this;

        if (this.entity.isPassenger()
                && this.entity.getVehicle() instanceof Minecart minecart
                && minecart.getBehavior() instanceof NewMinecartBehavior behavior
                && behavior.cartHasPosRotLerp()) {
            Vec3 positionOffset = minecart.getPassengerRidingPosition(this.entity)
                    .subtract(minecart.position())
                    .subtract(this.entity.getVehicleAttachmentPoint(minecart))
                    .add(new Vec3(0.0, Mth.lerp(partialTicks, invoker.medved$getEyeHeightOld(), invoker.medved$getEyeHeight()), 0.0));
            //this.setRotation(this.entity.getViewYRot(partialTicks), this.entity.getViewXRot(partialTicks));
            invoker.medved$setPosition(behavior.getCartLerpPosition(partialTicks).add(positionOffset));
        } else {
            //this.setRotation(this.entity.getViewYRot(partialTicks), this.entity.getViewXRot(partialTicks));
            invoker.medved$setPosition(
                    Mth.lerp(partialTicks, this.entity.xo, this.entity.getX()),
                    Mth.lerp(partialTicks, this.entity.yo, this.entity.getY()) + Mth.lerp(partialTicks, invoker.medved$getEyeHeightOld(), invoker.medved$getEyeHeight()),
                    Mth.lerp(partialTicks, this.entity.zo, this.entity.getZ())
            );
        }

        this.detached = !this.minecraft.options.getCameraType().isFirstPerson();
        if (this.detached) {
            if (this.minecraft.options.getCameraType().isMirrored()) {
                this.setRotation(this.yRot + 180.0F, -this.xRot);
            }

            float cameraDistance = 4.0F;
            float cameraScale = 1.0F;
            if (this.entity instanceof LivingEntity living) {
                cameraScale = living.getScale();
                cameraDistance = (float)living.getAttributeValue(Attributes.CAMERA_DISTANCE);
            }

            float mountScale = cameraScale;
            float mountDistance = cameraDistance;
            if (this.entity.isPassenger() && this.entity.getVehicle() instanceof LivingEntity mount) {
                mountScale = mount.getScale();
                mountDistance = (float)mount.getAttributeValue(Attributes.CAMERA_DISTANCE);
            }

            invoker.medved$move(-invoker.medved$getMaxZoom(Math.max(cameraScale * cameraDistance, mountScale * mountDistance)), 0.0F, 0.0F);
        } else if (this.entity instanceof LivingEntity && ((LivingEntity)this.entity).isSleeping()) {
            Direction bedOrientation = ((LivingEntity)this.entity).getBedOrientation();
            this.setRotation(bedOrientation != null ? bedOrientation.toYRot() - 180.0F : 0.0F, 0.0F);
            invoker.medved$move(0.0F, 0.3F, 0.0F);
        }
    }
}