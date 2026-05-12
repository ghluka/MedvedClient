package me.ghluka.medved.mixin.client;

import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Mutable
    @Accessor("noJumpDelay")
    void setNoJumpDelay(int value);

    @Invoker("getAttributeValue")
    double medved$getAttributeValue(final Holder<Attribute> attribute);
}