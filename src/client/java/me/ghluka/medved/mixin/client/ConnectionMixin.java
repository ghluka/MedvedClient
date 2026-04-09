package me.ghluka.medved.mixin.client;

import io.netty.channel.ChannelHandlerContext;
import me.ghluka.medved.module.modules.player.Blink;
import me.ghluka.medved.module.modules.player.ClientBrand;
import me.ghluka.medved.module.modules.player.FakeLag;
import me.ghluka.medved.module.modules.combat.KnockbackDelay;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
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
        if (!KnockbackDelay.INSTANCE.isEnabled()) return;
        if (packet instanceof ClientboundKeepAlivePacket || packet instanceof ClientboundPingPacket) return;
        PacketListener listener = packetListener;
        if (!(listener instanceof ClientPacketListener)) return;

        boolean trigger = false;
        if (!KnockbackDelay.INSTANCE.isHolding() && packet instanceof ClientboundSetEntityMotionPacket mp) {
            if (KnockbackDelay.cachedPlayerId != -1 && mp.id() == KnockbackDelay.cachedPlayerId) {
                int chance = KnockbackDelay.INSTANCE.getChance().getValue();
                if (chance >= 100 || (int)(Math.random() * 100) < chance) {
                    KnockbackDelay.INSTANCE.triggerDelay(KnockbackDelay.cachedOnGround);
                    trigger = true;
                }
            }
        }

        if (!trigger && !KnockbackDelay.INSTANCE.isHolding()) return;

        Packet<PacketListener> p = (Packet<PacketListener>) packet;
        ci.cancel();
        KnockbackDelay.INSTANCE.bufferPacket(() -> p.handle(listener));
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void medved$onSend(Packet<?> packet, CallbackInfo ci) {
        Connection conn = (Connection)(Object) this;
        boolean isKeepalive = packet instanceof ServerboundKeepAlivePacket || packet instanceof ServerboundPongPacket;

        // Brand spoofer: intercept the outgoing brand custom payload and replace it.
        // The re-entry guard prevents infinite recursion when we call conn.send() with the replacement.
        if (!ClientBrand.INSTANCE.isResending
                && ClientBrand.INSTANCE.isEnabled()
                && packet instanceof ServerboundCustomPayloadPacket cp
                && cp.payload() instanceof BrandPayload) {
            ci.cancel();
            ClientBrand.INSTANCE.isResending = true;
            conn.send(new ServerboundCustomPayloadPacket(new BrandPayload(ClientBrand.INSTANCE.getCurrentBrand())));
            ClientBrand.INSTANCE.isResending = false;
            return;
        }

        if (Blink.INSTANCE.shouldBuffer() && !isKeepalive) {
            ci.cancel();
            Blink.INSTANCE.bufferPacket(() -> conn.send(packet));
            return;
        }

        if (FakeLag.INSTANCE.shouldDelay()) {
            ci.cancel();
            FakeLag.INSTANCE.queuePacket(() -> conn.send(packet));
        }
    }
}
