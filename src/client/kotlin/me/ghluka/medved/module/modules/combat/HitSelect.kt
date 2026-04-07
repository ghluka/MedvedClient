package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import kotlin.random.Random

object HitSelect : Module(
    name = "Hit Select",
    description = "Restricts auto-clicks and attacks to the most effective moments",
    category = Category.COMBAT
) {

    enum class HitMode { PAUSE, ACTIVE }
    enum class Preference { CRIT, COMBO, TIMER }

    private val mode       = enum("mode", HitMode.PAUSE)
    private val preference = enum("preference", Preference.CRIT)
    private val delay      = intRange("delay (ms)", 350 to 450, 100, 1500)
    private val filterPct  = int("filter %", 70, 0, 100)

    @JvmField var currentShouldAttack = false
    private var attackTime = -1L

    override fun onEnabled() {
        currentShouldAttack = false
        attackTime = -1L
    }

    override fun onDisabled() {
        currentShouldAttack = false
        attackTime = -1L
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        currentShouldAttack = false

        if (Random.nextInt(100) >= filterPct.value) {
            currentShouldAttack = true
        } else {
            when (preference.value) {
                Preference.CRIT -> {
                    currentShouldAttack = !player.onGround() && player.deltaMovement.y < 0
                }
                Preference.COMBO -> {
                    val moving = client.options.keyUp.isDown    || client.options.keyDown.isDown  ||
                                 client.options.keyLeft.isDown  || client.options.keyRight.isDown
                    currentShouldAttack = player.hurtTime > 0 && !player.onGround() && moving
                }
                Preference.TIMER -> { }
            }

            if (!currentShouldAttack) {
                val (lo, hi) = delay.value
                val d = if (hi > lo) (lo + Random.nextInt(hi - lo + 1)).toLong() else lo.toLong()
                currentShouldAttack = attackTime < 0 || System.currentTimeMillis() - attackTime >= d
            }
        }
    }

    @JvmStatic
    fun notifyAttackFiring() {
        if (isEnabled()) attackTime = System.currentTimeMillis()
    }

    fun canAutoAttack(): Boolean {
        if (!isEnabled() || mode.value == HitMode.ACTIVE) return true
        return currentShouldAttack
    }

    @JvmStatic
    fun shouldCancelAttack(): Boolean {
        if (!isEnabled() || mode.value != HitMode.ACTIVE) return false
        return !currentShouldAttack
    }

    override fun hudInfo(): String {
        return "${mode.value} ${filterPct.value}%"
    }

    override fun hudInfoColor(): Int =
        if (currentShouldAttack) (255 shl 24) or (170 shl 16) or (170 shl 8) or 170 else 0xFFFF5555.toInt()
}
