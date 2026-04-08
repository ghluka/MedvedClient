package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.tags.BlockTags
import net.minecraft.world.item.BowItem
import net.minecraft.world.item.CrossbowItem
import net.minecraft.world.item.ShieldItem
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.random.Random

object RightClicker : Module(
    name = "Right Clicker",
    description = "Automatically right-clicks while holding the use key",
    category = Category.COMBAT
) {

    private val cps        = floatRange("cps", 12.0f to 14.0f, 1.0f, 20.0f)
    private val jitter     = float("jitter", 0.0f, 0.0f, 2.0f)

    private val blocksOnly = boolean("blocks only", true)

    private val woolOnly = boolean("wool only", true).also {
        it.visibleWhen = { blocksOnly.value }
    }

    private val allowBow = boolean("allow bow", false)

    private val allowEat = boolean("allow eat", true)

    private val allowShield = boolean("allow shield", false)

    private var accumulator = 0.0f
    private var targetCps   = 17.0f

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

        if (!client.options.keyUse.isDown) return

        val main = player.mainHandItem
        val off  = player.offhandItem

        if (!allowBow.value) {
            if (main.item is BowItem || main.item is CrossbowItem ||
                off.item  is BowItem || off.item  is CrossbowItem) return
        }

        if (!allowEat.value) {
            if (main.has(DataComponents.FOOD) || off.has(DataComponents.FOOD)) return
        }

        if (!allowShield.value) {
            if (main.item is ShieldItem || off.item is ShieldItem) return
        }

        if (blocksOnly.value) {
            val hr = client.hitResult
            if (hr !is BlockHitResult || hr.type != HitResult.Type.BLOCK) return
            if (woolOnly.value) {
                val blockState = client.level?.getBlockState(hr.blockPos) ?: return
                if (!blockState.`is`(BlockTags.WOOL)) return
            }
        }

        accumulator += targetCps / 20.0f
        var clicked = false
        while (accumulator >= 1.0f) {
            KeyMapping.click(InputConstants.getKey(client.options.keyUse.saveString()))
            accumulator -= 1.0f
            targetCps = pickCps()
            clicked = true
        }

        if (clicked && jitter.value > 0f) {
            val jx = (Random.nextFloat() - 0.5f) * 2f * jitter.value
            val jy = (Random.nextFloat() - 0.5f) * 2f * jitter.value
            player.yRot += jx
            player.xRot  = (player.xRot + jy).coerceIn(-90f, 90f)
        }
    }

    private fun pickCps(): Float {
        val (lo, hi) = cps.value
        return if (hi > lo) lo + Random.nextFloat() * (hi - lo) else lo
    }
}
