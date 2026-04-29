package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.CameraOverriddenEntity;
import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GameRenderer.class)
public class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void onRender(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();

        if (RotationManager.perspective && camera.entity() instanceof LocalPlayer) {
            CameraOverriddenEntity e = (CameraOverriddenEntity) camera.entity();

            ((CameraAccessor)camera).medved$setRotation(
                    e.medved$getCameraYaw(),
                    e.medved$getCameraPitch()
            );
        }
    }
}