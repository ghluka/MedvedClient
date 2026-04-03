package me.ghluka.medved.mixin.client;

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

    /**
     * Fires right after KeyboardInput.tick() has built keyPresses + moveVector
     * from KeyMapping.isDown(). If the scaffold movement-freeze flag is set,
     * replace both with zeroed values so aiStep() computes no movement.
     * If suppressJump is set, rebuild keyPresses with jump=false so the player
     * doesn't jump until they reach the block edge during WASD+space bridging.
     */
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
    }
}
