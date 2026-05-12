package me.ghluka.medved.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
    @Accessor("sprintTriggerTime")
    void setSprintTriggerTime(int value);

    @Accessor("xLast")
    double getXLast();
    @Accessor("xLast")
    void setXLast(double value);

    @Accessor("zLast")
    double getZLast();
    @Accessor("zLast")
    void setZLast(double value);

    @Invoker("itemUseSpeedMultiplier")
    float medved$itemUseSpeedMultiplier();
    @Invoker("isUsingItem")
    boolean medved$isUsingItem();
    @Invoker("isMovingSlowly")
    boolean medved$isMovingSlowly();
    @Invoker("modifyInputSpeedForSquareMovement")
    Vec2 medved$modifyInputSpeedForSquareMovement(final Vec2 input);
}
