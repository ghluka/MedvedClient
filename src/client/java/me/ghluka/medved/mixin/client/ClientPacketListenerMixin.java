package me.ghluka.medved.mixin.client;

import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleRotatePlayer", at = @At("RETURN"))
    private void medved$afterRotatePlayer(ClientboundPlayerRotationPacket packet, CallbackInfo ci) {
        if (RotationManager.isActive()) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player != null) {
                RotationManager.restoreClientCamera(player);
            }
        }
    }
}
