package me.ghluka.medved.mixin.client;

import io.netty.channel.ChannelHandlerContext;
import me.ghluka.medved.module.modules.player.ClientBrand;
import me.ghluka.medved.module.modules.combat.KnockbackDelay;
import me.ghluka.medved.util.LagManager;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
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
        PacketListener listener = packetListener;
        if (!(listener instanceof ClientPacketListener)) return;

        if (KnockbackDelay.INSTANCE.isEnabled() && !KnockbackDelay.INSTANCE.isHolding() && packet instanceof ClientboundSetEntityMotionPacket mp) {
            if (KnockbackDelay.cachedPlayerId != -1 && mp.id() == KnockbackDelay.cachedPlayerId) {
                int chance = KnockbackDelay.INSTANCE.getChance().getValue();
                if (chance >= 100 || (int)(Math.random() * 100) < chance) {
                    KnockbackDelay.INSTANCE.triggerDelay(KnockbackDelay.cachedOnGround);
                }
            }
        }

        if (LagManager.INSTANCE.shouldBufferIncoming()) {
            Packet<PacketListener> p = (Packet<PacketListener>) packet;
            ci.cancel();
            LagManager.INSTANCE.bufferIncoming(() -> p.handle(listener));
            return;
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void medved$onSend(Packet<?> packet, CallbackInfo ci) {
        medved$handleOutgoing(packet, ci);
    }

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/util/concurrent/GenericFutureListener;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void medved$onSendWithListener(Packet<?> packet, io.netty.util.concurrent.GenericFutureListener<? extends io.netty.util.concurrent.Future<? super Void>> listener, CallbackInfo ci) {
        Connection conn = (Connection)(Object) this;

        if (!ClientBrand.INSTANCE.isResending
                && ClientBrand.INSTANCE.isEnabled()
                && packet instanceof ServerboundCustomPayloadPacket cp
                && cp.payload() instanceof BrandPayload) {
            ci.cancel();
            ClientBrand.INSTANCE.isResending = true;
            conn.send(new ServerboundCustomPayloadPacket(new BrandPayload(ClientBrand.INSTANCE.getCurrentBrand())), (io.netty.channel.ChannelFutureListener) listener);
            ClientBrand.INSTANCE.isResending = false;
            return;
        }

        try {
            if (LagManager.INSTANCE.shouldBufferOutgoing()) {
                ci.cancel();
                LagManager.INSTANCE.bufferOutgoing(() -> conn.send(packet, (io.netty.channel.ChannelFutureListener) listener));
            }
        } catch (Exception e) {
            // Failsafe escape hatch
        }
    }

    private void medved$handleOutgoing(Packet<?> packet, CallbackInfo ci) {
        Connection conn = (Connection)(Object) this;

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

        try {
            if (LagManager.INSTANCE.shouldBufferOutgoing()) {
                ci.cancel();
                LagManager.INSTANCE.bufferOutgoing(() -> conn.send(packet));
            }
        } catch (Exception e) {
            // Failsafe escape hatch
        }
    }
}
