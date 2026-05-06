package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.ComboTap;
import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends ClientInput {

    @Inject(method = "tick", at = @At("RETURN"))
    private void medved$freezeMovement(CallbackInfo ci) {
        if (RotationManager.freezeMovement) {
            this.keyPresses = Input.EMPTY;
            this.moveVector = Vec2.ZERO;
        } else if (RotationManager.suppressJump) {
            this.keyPresses = new Input(
                this.keyPresses.forward(),
                this.keyPresses.backward(),
                this.keyPresses.left(),
                this.keyPresses.right(),
                false,
                this.keyPresses.shift(),
                this.keyPresses.sprint()
            );
        }

        if (ComboTap.INSTANCE.isEnabled()) {
            boolean fwd = this.keyPresses.forward()  && !ComboTap.suppressForward;
            boolean bwd = this.keyPresses.backward() || ComboTap.forceBackward;
            boolean snk = this.keyPresses.shift()    || ComboTap.forceSneak;
            boolean jmp = this.keyPresses.jump()     || ComboTap.forceJump;
            boolean spr = this.keyPresses.sprint()   && !ComboTap.suppressSprint;

            if (fwd != this.keyPresses.forward() || bwd != this.keyPresses.backward()
                    || snk != this.keyPresses.shift() || jmp != this.keyPresses.jump()
                    || spr != this.keyPresses.sprint()) {
                this.keyPresses = new Input(fwd, bwd, this.keyPresses.left(), this.keyPresses.right(), jmp, snk, spr);
                float strafe  = (this.keyPresses.right() ? 1.0f : 0.0f) - (this.keyPresses.left()  ? 1.0f : 0.0f);
                float forward = (fwd ? 1.0f : 0.0f) - (bwd ? 1.0f : 0.0f);
                this.moveVector = new Vec2(strafe, forward);
            }
        }
    }
}
