package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.TridentItem

object AutoBlock : Module(
    name = "Auto Block",
    description = "Holds block with your sword to reduce incoming damage",
    category = Category.COMBAT
) {

    enum class BlockMode { STATIC, DYNAMIC }

    private val mode = enum("mode", BlockMode.DYNAMIC)

    private val releaseOnSwing = boolean("release on swing", true)
    private val releaseMs = intRange("release (ms)", 50 to 80, 20, 3000).also {
        it.visibleWhen = { releaseOnSwing.value }
    }
    private val dynamicRange = float("dynamic range", 5.0f, 1.0f, 10.0f).also {
        it.visibleWhen = { mode.value == BlockMode.DYNAMIC }
    }

    private var wasBlocking    = false
    private var releaseUntilMs = 0L
    private var prevAttackDown = false

    override fun onEnabled() {
        wasBlocking    = false
        releaseUntilMs = 0L
        prevAttackDown = false
    }

    override fun onDisabled() {
        release(Minecraft.getInstance())
        releaseUntilMs = 0L
        prevAttackDown = false
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.screen != null) { release(client); return }

        val stack = player.mainHandItem
        val isSword = stack.`is`(ItemTags.SWORDS) ||
                      stack.`is`(ItemTags.AXES)   ||
                      stack.item is TridentItem

        val attackDown = client.options.keyAttack.isDown

        if (!isSword || !attackDown) {
            release(client)
            prevAttackDown = attackDown
            return
        }

        val now = System.currentTimeMillis()

        if (releaseOnSwing.value && now >= releaseUntilMs) {
            val autoClickerFired = LeftClicker.isEnabled() && LeftClicker.clickedThisTick
            val manualClickEdge  = !LeftClicker.isEnabled() && attackDown && !prevAttackDown
            if (autoClickerFired || manualClickEdge) {
                val (lo, hi) = releaseMs.value
                val ms = if (hi > lo) (lo..hi).random().toLong() else lo.toLong()
                releaseUntilMs = now + ms
            }
        }
        prevAttackDown = attackDown

        if (now < releaseUntilMs) {
            release(client)
            return
        }

        if (shouldBlock(player, client)) {
            client.options.keyUse.setDown(true)
            wasBlocking = true
        } else {
            release(client)
        }
    }

    private fun shouldBlock(player: net.minecraft.world.entity.player.Player, client: Minecraft): Boolean {
        if (mode.value == BlockMode.STATIC) return true

        val level = client.level ?: return false
        return level.entitiesForRendering()
            .filterIsInstance<LivingEntity>()
            .any { e -> e !== player && !e.isDeadOrDying && player.distanceTo(e) <= dynamicRange.value }
    }

    private fun release(client: Minecraft) {
        if (wasBlocking) {
            client.options.keyUse.setDown(false)
            wasBlocking = false
        }
    }
}

