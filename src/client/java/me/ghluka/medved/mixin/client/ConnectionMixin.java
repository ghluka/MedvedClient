package me.ghluka.medved.mixin.client;

import io.netty.channel.ChannelHandlerContext;
import me.ghluka.medved.module.modules.combat.AutoBlock;
import me.ghluka.medved.module.modules.combat.Backtrack;
import me.ghluka.medved.module.modules.player.ClientBrand;
import me.ghluka.medved.module.modules.combat.KnockbackDelay;
import me.ghluka.medved.util.LagManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import net.minecraft.network.protocol.common.custom.BrandPayload;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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

        if (LagManager.INSTANCE.shouldBufferIncoming()) {
            Packet<PacketListener> p = (Packet<PacketListener>) packet;
            ci.cancel();
            if (Minecraft.getInstance().level != null) {
                Entity entity = null;
                if (packet instanceof ClientboundMoveEntityPacket move) {
                    entity = move.getEntity(Minecraft.getInstance().level);
                }

                if (entity != null && Backtrack.INSTANCE.isEnabled()
                        && Backtrack.INSTANCE.getMode().getValue() == Backtrack.Mode.LAG
                        && (!Backtrack.INSTANCE.getOnlyPlayers().getValue() || entity instanceof Player)) {

                    Vec3 currentPos = Backtrack.INSTANCE.getRealPositions().getOrDefault(entity.getId(), entity.position());

                    if (packet instanceof ClientboundMoveEntityPacket move) {
                        Backtrack.INSTANCE.updateRealPosition(entity.getId(), new Vec3(
                                currentPos.x + (move.getXa() / 4096.0),
                                currentPos.y + (move.getYa() / 4096.0),
                                currentPos.z + (move.getZa() / 4096.0)
                        ));
                    }
                }
            }
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
        if (AutoBlock.isBlocking && packet instanceof ServerboundPlayerActionPacket &&
                ((ServerboundPlayerActionPacket) packet).getAction() == net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.RELEASE_USE_ITEM) {
            ci.cancel();
        }

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
                final long queuedAt = System.currentTimeMillis();
                LagManager.INSTANCE.bufferOutgoing(() -> {
                    if (packet instanceof ServerboundPongPacket) {
                        long age = System.currentTimeMillis() - queuedAt;

                        if (age > 5000L) {
                            return; // drop stale pong
                        }
                    }

                    conn.send(packet);
                });
            }
        } catch (Exception e) {
            // Failsafe escape hatch
        }
    }
}
