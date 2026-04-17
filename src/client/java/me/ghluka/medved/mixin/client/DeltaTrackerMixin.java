package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.movement.Timer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Intercepts DeltaTracker$Timer.advanceGameTime(long millis) to multiply the
 * elapsed milliseconds by the Timer module's speed factor.  When Timer is
 * disabled the multiplier is 1.0 so nothing changes.
 */
@Mixin(targets = "net.minecraft.client.DeltaTracker$Timer")
public class DeltaTrackerMixin {

    @ModifyVariable(method = "advanceGameTime", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private long medved$scaleTime(long millis) {
        float factor = 1.0f;

        if (Timer.INSTANCE.isEnabled()) factor *= Timer.INSTANCE.speed.getValue();

        if (factor == 1.0f) return millis;
        return (long) (millis * factor);
    }
}
