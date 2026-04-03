package me.ghluka.medved.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

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
}
