package me.ghluka.medved.mixin.client;

import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Camera.class)
public interface CameraAccessor {

    @Invoker("setRotation")
    void medved$setRotation(float yaw, float pitch);

    @Invoker("getCameraEntityPartialTicks")
    float medved$getCameraEntityPartialTicks(DeltaTracker deltaTracker);

    @Invoker("calculateFov")
    float medved$calculateFov(float partialTicks);

    @Invoker("calculateHudFov")
    float medved$calculateHudFov(float partialTicks);

    @Invoker("getViewRotationMatrix")
    Matrix4f medved$getViewRotationMatrix(final Matrix4f dest);

    @Invoker("createProjectionMatrixForCulling")
    Matrix4f medved$createProjectionMatrixForCulling();

    @Invoker("prepareCullFrustum")
    void medved$prepareCullFrustum(final Matrix4fc modelViewMatrix, final Matrix4f projectionMatrixForCulling, final Vec3 cameraPos);

    @Invoker("setupPerspective")
    void medved$setupPerspective(final float zNear, final float zFar, final float fov, final float width, final float height);

    @Invoker("setPosition")
    void medved$setPosition(final double x, final double y, final double z);

    @Invoker("setPosition")
    void medved$setPosition(final Vec3 position);

    @Invoker("setEntity")
    void medved$setEntity(final Entity entity);

    @Invoker("alignWithEntity")
    void medved$alignWithEntity(float partialTicks);

    @Accessor("eyeHeight")
    float medved$getEyeHeight();

    @Accessor("eyeHeightOld")
    float medved$getEyeHeightOld();

    @Invoker("isDetached")
    boolean medved$isDetached();

    @Invoker("getMaxZoom")
    float medved$getMaxZoom(float cameraDist);

    @Invoker("move")
    void medved$move(final float forwards, final float up, final float right);
}