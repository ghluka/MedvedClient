package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.minecraft.client.Minecraft
import net.minecraft.world.InteractionResult  // ActionResult in Yarn; swap if needed
import kotlin.random.Random

object HitSelect : Module(
    name = "Hit Select",
    description = "Restricts attacks to the most effective moments",
    category = Category.COMBAT
) {

    enum class HitMode  { PAUSE, ACTIVE }
    enum class Preference { BURST, CRIT, COMBO, TIMER }


    private val mode               = enum("mode",       HitMode.ACTIVE)
    private val preference         = enum("preference", Preference.BURST)

    private val delay              = intRange("delay (ms)", 350 to 450, 100, 1500)

    private val waitForFirstHit    = int("wait for first hit (ms)", 0, 0, 3000)
    private val hitLaterInTrades   = int("hit later in trades (ms)", 0, 0, 3000)

    private val disableDuringKB    = boolean("disable during knockback", false).also {
        it.visibleWhen = { preference.value == Preference.CRIT }
    }
    private val onlyWhileDamaged   = boolean("only while damaged",       false).also {
        it.visibleWhen = { preference.value == Preference.CRIT }
    }

    private val cancelInCombat     = int("cancel rate (in combat) %", 80, 0, 100)
    private val cancelMissed       = int("cancel rate (missed) %",     0, 0, 100)

    private val fakeSwing          = boolean("fake swing", false)

    @JvmField var currentShouldAttack = false

    private var lastAttackTime     = -1L
    private var firstHitReceived   = false
    private var lastTradeHitTime   = -1L
    private var prevHurtTime       = 0
    private var fightStartTime     = -1L
    private var hasDealtFirstHit   = false

    init {
        AttackEntityCallback.EVENT.register { _, _, _, _, _ ->
            if (enabled.value) {
                lastAttackTime   = System.currentTimeMillis()
                hasDealtFirstHit = true
            }
            InteractionResult.PASS
        }
    }

    override fun onEnabled()  = resetState()
    override fun onDisabled() = resetState()

    private fun resetState() {
        currentShouldAttack = false
        lastAttackTime      = -1L
        firstHitReceived    = false
        lastTradeHitTime    = -1L
        prevHurtTime        = 0
        fightStartTime      = -1L
        hasDealtFirstHit    = false
    }

    @JvmStatic
    fun notifyMissedSwing() {
        if (enabled.value) lastAttackTime = System.currentTimeMillis()
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val now    = System.currentTimeMillis()

        val justGotHit = player.hurtTime > prevHurtTime
        prevHurtTime   = player.hurtTime

        if (justGotHit) {
            firstHitReceived = true
            lastTradeHitTime = now
        }

        val hasTarget = client.crosshairPickEntity != null
        if (hasTarget && fightStartTime < 0) {
            fightStartTime = now
        } else if (!hasTarget) {
            fightStartTime   = -1L
            firstHitReceived = false
            hasDealtFirstHit = false
        }

        currentShouldAttack = evaluate(client, player, now)
    }

    private fun evaluate(client: Minecraft, player: net.minecraft.world.entity.player.Player, now: Long): Boolean {
        val target   = client.crosshairPickEntity
        val inCombat = target != null

        if (!hasDealtFirstHit && preference.value != Preference.TIMER) return true

        if (waitForFirstHit.value > 0 && !firstHitReceived) {
            val engagedFor = if (fightStartTime < 0) -1L else now - fightStartTime
            if (engagedFor < 0 || engagedFor < waitForFirstHit.value) return false
        }

        if (hitLaterInTrades.value > 0 && lastTradeHitTime >= 0) {
            if (now - lastTradeHitTime < hitLaterInTrades.value) return false
        }

        val willDealDamage: Boolean = when (preference.value) {
            Preference.BURST -> {
                target ?: return roll(cancelMissed.value)
                targetCanTakeDamage(target)
            }

            Preference.CRIT -> {
                when {
                    player.deltaMovement.y > 0 -> return false

                    player.onGround() -> {
                        target ?: return roll(cancelMissed.value)
                        targetCanTakeDamage(target)
                    }

                    else -> {
                        if (disableDuringKB.value && player.hurtTime > 0) return false
                        if (onlyWhileDamaged.value && !firstHitReceived)  return false
                        player.deltaMovement.y < 0
                    }
                }
            }

            Preference.COMBO -> {
                val moving = client.options.keyUp.isDown   || client.options.keyDown.isDown ||
                        client.options.keyLeft.isDown || client.options.keyRight.isDown
                player.hurtTime > 0 && !player.onGround() && moving
            }

            Preference.TIMER -> {
                val (lo, hi) = delay.value
                val d = if (hi > lo) (lo + Random.nextInt(hi - lo + 1)).toLong() else lo.toLong()
                return lastAttackTime < 0 || now - lastAttackTime >= d
            }
        }

        return if (willDealDamage) true
        else roll(if (inCombat) cancelInCombat.value else cancelMissed.value)
    }

    private fun targetCanTakeDamage(target: net.minecraft.world.entity.Entity): Boolean =
        target.invulnerableTime == 0

    private fun roll(rate: Int): Boolean = Random.nextInt(100) >= rate

    @JvmStatic
    fun shouldFakeSwing(): Boolean = fakeSwing.value && isEnabled() && !currentShouldAttack

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
        val target = Minecraft.getInstance().crosshairPickEntity
        val rate   = if (target != null) cancelInCombat.value else cancelMissed.value
        return "${preference.value} $rate%"
    }

    override fun hudInfoColor(): Int =
        if (currentShouldAttack) (255 shl 24) or (170 shl 16) or (170 shl 8) or 170
        else                     0xFFFF5555.toInt()
}