package me.ghluka.medved.mixin.client;

import me.ghluka.medved.gui.AltManagerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin() {
        super(Component.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void medved$addAltButton(CallbackInfo ci) {
        this.addRenderableWidget(
            Button.builder(Component.literal("Alts"),
                btn -> Minecraft.getInstance().setScreen(new AltManagerScreen(this))
            ).bounds(this.width - 72, 5, 67, 20).build()
        );
    }
}
