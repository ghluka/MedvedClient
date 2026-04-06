package me.ghluka.medved.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftAccessor {

    @Accessor("rightClickDelay")
    int getRightClickDelay();

    @Mutable
    @Accessor("rightClickDelay")
    void setRightClickDelay(int value);
}
