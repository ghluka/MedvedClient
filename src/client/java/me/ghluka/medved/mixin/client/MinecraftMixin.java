package me.ghluka.medved.mixin.client;

import me.ghluka.medved.module.modules.combat.Reach;
import me.ghluka.medved.module.modules.world.BedBreaker;
import me.ghluka.medved.util.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public class MinecraftMixin {

    @Mutable @Shadow public HitResult hitResult;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void medved$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (me.ghluka.medved.module.modules.combat.HitSwap.INSTANCE.isEnabled()) {
            me.ghluka.medved.module.modules.combat.HitSwap.INSTANCE.onStartAttack();
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"))
    private void medved$onContinueAttack(boolean leftClick, CallbackInfo ci) {
        if (leftClick && me.ghluka.medved.module.modules.combat.HitSwap.INSTANCE.isEnabled()) {
            me.ghluka.medved.module.modules.combat.HitSwap.INSTANCE.onStartAttack();
        }
    }

    @Inject(method = "pick", at = @At("RETURN"))
    private void medved$overrideHitResult(float partialTick, CallbackInfo ci) {
        if (RotationManager.isActive()) {
            RotationManager.updateHitResult();
        }

        // BedBreaker: after pick() sets hitResult from the player's real look direction,
        // override it to the exact target block so continueAttack() breaks the right block.
        BlockPos bbPos = BedBreaker.INSTANCE.isEnabled() ? BedBreaker.pendingHitPos : null;
        if (bbPos != null) {
            Direction bbFace = BedBreaker.pendingHitFace;
            Vec3 center = new Vec3(bbPos.getX() + 0.5, bbPos.getY() + 0.5, bbPos.getZ() + 0.5);
            this.hitResult = new BlockHitResult(center, bbFace, bbPos, false);
        }

        if (!Reach.INSTANCE.isEnabled()) return;
        double range = Reach.INSTANCE.getTickReach();
        if (range <= 0) return;

        Minecraft mc = (Minecraft)(Object) this;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        Vec3 eye = player.getEyePosition(partialTick);

        if (this.hitResult instanceof EntityHitResult ehr) {
            double dist = eye.distanceTo(ehr.getEntity().getBoundingBox().getCenter());
            float lo = ((Number) Reach.INSTANCE.getCustomRange().getValue().component1()).floatValue();
            if (dist > 3.0 && dist < lo) {
                this.hitResult = BlockHitResult.miss(eye, Direction.UP, BlockPos.containing(eye));
                return;
            }
        }

        if (!Reach.INSTANCE.getHitThroughWalls().getValue()) return;
        Vec3 look = player.getLookAngle();
        AABB scanBox = player.getBoundingBox().inflate(range + 1.0);

        Entity best = null;
        double bestDot = 0.95; // cos ~18 deg
        for (Entity e : mc.level.getEntities(player, scanBox)) {
            if (!e.isPickable()) continue;
            Vec3 toEntity = e.getBoundingBox().getCenter().subtract(eye);
            double dist = toEntity.length();
            if (dist > range) continue;
            double dot = toEntity.normalize().dot(look);
            if (dot > bestDot) { bestDot = dot; best = e; }
        }
        if (best != null) this.hitResult = new EntityHitResult(best);
    }
}
