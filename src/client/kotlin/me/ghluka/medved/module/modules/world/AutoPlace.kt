package me.ghluka.medved.module.modules.world

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.phys.BlockHitResult
import kotlin.random.Random

object AutoPlace : Module(
    name = "Auto Place",
    description = "Automatically places blocks when looking at a block surface",
    category = Category.WORLD,
) {
    private val cps      = floatRange("cps", 12.0f to 14.0f, 1.0f, 20.0f)
    private val requireHolding = boolean("require holding", true)
    private val onlyAir  = boolean("only air", true)
    private val woolOnly = boolean("wool only", false)

    private var accumulator = 0.0f
    private var targetCps   = 13.0f

    override fun onEnabled() {
        accumulator = 0.0f
        targetCps   = pickCps()
    }

    override fun onDisabled() {
        accumulator = 0.0f
    }

    override fun hudInfo(): String {
        val (lo, hi) = cps.value
        return if (hi > lo) "%.1f-%.1f cps".format(lo, hi) else "%.1f cps".format(lo)
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.screen != null) return

        if (requireHolding.value && !client.options.keyUse.isDown) return
        if (woolOnly.value && !player.mainHandItem.`is`(ItemTags.WOOL)) return

        val hr = client.hitResult
        if (hr !is BlockHitResult) return

        if (onlyAir.value) {
            val blockState = client.level?.getBlockState(hr.blockPos)
            if (blockState == null || !blockState.isSolid) return
        }

        accumulator += targetCps / 20.0f
        while (accumulator >= 1.0f) {
            KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            accumulator -= 1.0f
            targetCps = pickCps()
        }
    }

    private fun pickCps(): Float {
        val (lo, hi) = cps.value
        return if (hi > lo) lo + Random.nextFloat() * (hi - lo) else lo
    }
}
