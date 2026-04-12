package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.Backtrack;
import me.ghluka.medved.module.modules.combat.Velocity;
import me.ghluka.medved.module.modules.world.Scaffold;
import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerCombatKillPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
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

    @Inject(method = "handlePlayerCombatKill", at = @At("HEAD"))
    private void medved$onPlayerDeath(ClientboundPlayerCombatKillPacket packet, CallbackInfo ci) {
        if (Scaffold.INSTANCE.isEnabled() && Scaffold.INSTANCE.getDisableOnDeath().getValue()) {
            Scaffold.INSTANCE.disable();
        }
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void medved$onRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        if (Scaffold.INSTANCE.isEnabled() && Scaffold.INSTANCE.getDisableOnWorldChange().getValue()) {
            Scaffold.INSTANCE.disable();
        }
        Backtrack.INSTANCE.clearBuffer();
    }

    @Inject(method = "clearLevel", at = @At("HEAD"))
    private void medved$onClearLevel(CallbackInfo ci) {
        Backtrack.INSTANCE.clearBuffer();
    }

    @Inject(method = "handleMoveEntity", at = @At("HEAD"), cancellable = true)
    private void medved$onMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        if (!Backtrack.INSTANCE.isEnabled() || Backtrack.INSTANCE.getFlushing()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = packet.getEntity(mc.level);
        if (entity == null || entity instanceof LocalPlayer) return;
        if (Backtrack.INSTANCE.getOnlyPlayers().getValue() && !(entity instanceof Player)) return;
        if (!Backtrack.INSTANCE.shouldDelay()) return;
        ClientPacketListener connection = (ClientPacketListener)(Object) this;
        Vec3 realPos = new Vec3(
            entity.getX() + packet.getXa() / 4096.0,
            entity.getY() + packet.getYa() / 4096.0,
            entity.getZ() + packet.getZa() / 4096.0
        );
        ci.cancel();
        Backtrack.INSTANCE.enqueue(entity.getId(), realPos, () -> connection.handleMoveEntity(packet));
    }

    @Inject(method = "handleEntityPositionSync", at = @At("HEAD"), cancellable = true)
    private void medved$onEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        if (!Backtrack.INSTANCE.isEnabled() || Backtrack.INSTANCE.getFlushing()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity entity = mc.level.getEntity(packet.id());
        if (entity == null || entity instanceof LocalPlayer) return;
        if (Backtrack.INSTANCE.getOnlyPlayers().getValue() && !(entity instanceof Player)) return;
        if (!Backtrack.INSTANCE.shouldDelay()) return;
        ClientPacketListener connection = (ClientPacketListener)(Object) this;
        Vec3 realPos = packet.values().position();
        ci.cancel();
        Backtrack.INSTANCE.enqueue(packet.id(), realPos, () -> connection.handleEntityPositionSync(packet));
    }

    @Inject(method = "handleSetEntityMotion", at = @At("HEAD"), cancellable = true)
    private void medved$onSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        if (!Velocity.INSTANCE.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || packet.id() != player.getId()) return;

        Vec3 motion = packet.movement();
        double mx = motion.x;
        double my = motion.y;
        double mz = motion.z;

        Velocity.Mode mode = Velocity.INSTANCE.getMode().getValue();
        if (mode == Velocity.Mode.REDUCE) {
            float factorXZ = 1f - (Velocity.INSTANCE.getReducePercent().getValue() / 100f);
            float factorY  = 1f - (Velocity.INSTANCE.getReduceYPercent().getValue() / 100f);
            player.lerpMotion(new Vec3(mx * factorXZ, my * factorY, mz * factorXZ));
            ci.cancel();
        } else if (mode == Velocity.Mode.REVERSE) {
            float factor = Velocity.INSTANCE.getReversePercent().getValue() / 100f;
            player.lerpMotion(new Vec3(-mx * factor, my, -mz * factor));
            ci.cancel();
        } else if (mode == Velocity.Mode.JUMP_RESET) {
            if (Math.abs(mx) < 0.1 && Math.abs(mz) < 0.1) return;
            int chance = Velocity.INSTANCE.getJumpChance().getValue();
            if (player.onGround() && (chance >= 100 || (int)(Math.random() * 100) < chance)) {
                player.lerpMotion(new Vec3(mx, my, mz));
                kotlin.Pair<?, ?> timing = Velocity.INSTANCE.getJumpTiming().getValue();
                int lo = (Integer) timing.component1();
                int hi = (Integer) timing.component2();
                int delay = hi > lo ? lo + (int)(Math.random() * (hi - lo + 1)) : lo;
                Velocity.INSTANCE.scheduleJump(System.currentTimeMillis() + delay);
                ci.cancel();
            }
        } else if (mode == Velocity.Mode.DELAY) {
            if (Math.abs(mx) < 0.1 && Math.abs(mz) < 0.1) return;
            Velocity.INSTANCE.startPacketDelay();
        }
    }
}
