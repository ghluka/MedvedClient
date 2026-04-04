package me.ghluka.medved.mixin.client;

import me.ghluka.medved.gui.AltManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin extends Screen {

    protected JoinMultiplayerScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void medved$addAltButton(CallbackInfo ci) {
        this.addRenderableWidget(
            Button.builder(Component.literal("Alts"),
                btn -> Minecraft.getInstance().setScreen(new AltManagerScreen(this))
            ).bounds(5, 5, 67, 20).build()
        );
    }
}
