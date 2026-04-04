package me.ghluka.medved.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface MinecraftUserAccessor {

    @Accessor("user")
    User getAltUser();

    @Mutable
    @Accessor("user")
    void setAltUser(User user);
}
