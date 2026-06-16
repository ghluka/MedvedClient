package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.ChamsItemSubmit;
import me.ghluka.medved.module.modules.render.Chams;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemFeatureRenderer.Submit.class)
public class ItemSubmitMixin implements ChamsItemSubmit {
    @Unique
    private boolean medved$chamsHeldItem;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void medved$initChamsHeldItem(CallbackInfo ci) {
        this.medved$chamsHeldItem = Chams.isSubmittingHeldItem();
    }

    @Override
    public boolean medved$isChamsHeldItem() {
        return this.medved$chamsHeldItem;
    }

    @Override
    public void medved$setChamsHeldItem(boolean value) {
        this.medved$chamsHeldItem = value;
    }
}
