package me.ghluka.medved.mixin.client;

import io.netty.channel.ChannelHandlerContext;
import me.ghluka.medved.module.modules.combat.Velocity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Connection.class)
public class ConnectionMixin {

    @Shadow private volatile PacketListener packetListener;

    @SuppressWarnings("unchecked")
    @Inject(method = "channelRead0", at = @At("HEAD"), cancellable = true)
    private void medved$onChannelRead(ChannelHandlerContext ctx, Packet<?> packet, CallbackInfo ci) {
        if (!Velocity.INSTANCE.isEnabled() || Velocity.INSTANCE.getMode().getValue() != Velocity.Mode.DELAY)
            return;
        PacketListener listener = packetListener;
        if (!(listener instanceof ClientPacketListener)) return;

        boolean trigger = false;
        if (!Velocity.INSTANCE.isHolding() && packet instanceof ClientboundSetEntityMotionPacket mp) {
            if (Velocity.cachedPlayerId != -1 && mp.id() == Velocity.cachedPlayerId) {
                int chance = Velocity.INSTANCE.getDelayChance().getValue();
                if (chance >= 100 || (int)(Math.random() * 100) < chance) {
                    Velocity.INSTANCE.triggerDelayForPlayer(Velocity.cachedOnGround);
                    trigger = true;
                }
            }
        }

        if (!trigger && !Velocity.INSTANCE.isHolding()) return;

        Packet<PacketListener> p = (Packet<PacketListener>) packet;
        ci.cancel();
        Velocity.INSTANCE.bufferPacket(() -> p.handle(listener));
    }
}
