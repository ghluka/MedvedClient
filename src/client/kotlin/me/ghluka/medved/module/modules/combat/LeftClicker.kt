package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.TridentItem
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import kotlin.random.Random

object LeftClicker : Module(
    name = "Left Clicker",
    description = "Automatically left-clicks while holding the attack key",
    category = Category.COMBAT
) {

    private val cps            = floatRange("cps", 11.0f to 13.0f, 1.0f, 20.0f)
    private val jitter         = float("jitter", 0.0f, 0.0f, 2.0f)

    private val onlyWhenAiming = boolean("only when targeting", false)

    private val breakBlocks    = boolean("break blocks", false)

    private val weaponOnly     = boolean("weapon only", false)

    private var accumulator = 0.0f
    private var targetCps   = 12.0f

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

        if (!client.options.keyAttack.isDown) return

        if (weaponOnly.value) {
            val stack = player.mainHandItem
            val isWeapon = stack.`is`(ItemTags.SWORDS) ||
                           stack.`is`(ItemTags.AXES)   ||
                           stack.item is TridentItem
            if (!isWeapon) return
        }

        val targeting = onlyWhenAiming.value
        val blocks    = breakBlocks.value
        if (targeting || blocks) {
            val hr       = client.hitResult
            val isEntity = hr is EntityHitResult && hr.entity is LivingEntity
            val isBlock  = hr is BlockHitResult
            if (!(targeting && isEntity) && !(blocks && isBlock)) return
        }

        accumulator += targetCps / 20.0f
        var clicked = false
        while (accumulator >= 1.0f) {
            KeyMapping.click(InputConstants.getKey(client.options.keyAttack.saveString()))
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
