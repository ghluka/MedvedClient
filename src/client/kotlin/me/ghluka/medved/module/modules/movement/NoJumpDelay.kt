package me.ghluka.medved.module.modules.movement

import me.ghluka.medved.mixin.client.LivingEntityAccessor
import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft

object NoJumpDelay : Module(
    name = "No Jump Delay",
    description = "Removes the jump cooldown. Works mostly for head hitters if you hold space and run down a 1x2 corridor",
    category = Category.MOVEMENT,
) {
    override fun onTick(client: Minecraft) {
        (client.player as LivingEntityAccessor).setNoJumpDelay(0)
    }
}
