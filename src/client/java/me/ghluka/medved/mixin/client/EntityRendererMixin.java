package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.render.ESP3D;
import me.ghluka.medved.module.modules.render.Nametags;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void medved$applyPlayerEspGlow(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        int outlineColor = ESP3D.glowOutlineColor(entity);
        if (outlineColor != 0) {
            state.outlineColor = outlineColor;
        }

        if (Nametags.shouldHideVanilla(entity)) {
            state.nameTag = null;
            state.scoreText = null;
        }
    }
}
