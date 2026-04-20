package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.tags.ItemTags
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.projectile.FishingHook
import net.minecraft.world.item.EggItem
import net.minecraft.world.item.FishingRodItem
import net.minecraft.world.item.Item
import net.minecraft.world.item.SnowballItem
import net.minecraft.world.item.TridentItem
import kotlin.random.Random

object AutoRod : Module(
    "Auto Rod",
    "Automatically throws your fishing rod, retracts it, or throws projectiles",
    Category.COMBAT
) {
    private val retractSpeed = intRange("retract speed (ms)", 200 to 300, 0, 2000)
    private val actionDelay = intRange("action delay (ms)", 50 to 100, 0, 1000)
    private val randomizeDelays = boolean("randomize delays", true)
    private val switchBackToWeapon = boolean("switch to weapon", false)
    
    private val allowThrowables = boolean("allow throwables", false)
    private val throwSpeed = intRange("throw speed (ms)", 100 to 200, 0, 1000).also {
        it.visibleWhen = { allowThrowables.value }
    }

    // 0 = disabled
    private val notMeleeRange = float("not within melee range", 3.0f, 0.0f, 8.0f)

    private enum class State {
        IDLE, SWITCHING, THROWING, WAITING_HOOK, RETRACTING, SWITCHING_BACK
    }

    private var state = State.IDLE
    private var waitTime = 0L
    private var originalSlot = -1
    private var currentItemType: ItemType = ItemType.NONE
    
    private var throwTimer = 0L
    private var retractTimer = 0L

    private enum class ItemType { NONE, ROD, PROJECTILE }

    override fun onEnabled() {
        state = State.IDLE
        waitTime = 0L
        originalSlot = -1
        currentItemType = ItemType.NONE
        throwTimer = 0L
    }

    override fun onDisabled() {
        state = State.IDLE
    }

    private fun getDelay(range: Pair<Int, Int>): Long {
        val (lo, hi) = range
        if (lo >= hi || !randomizeDelays.value) return lo.toLong()
        return (lo..hi).random().toLong()
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        if (client.screen != null) return
        
        val now = System.currentTimeMillis()
        if (now < waitTime) return

        val targets = client.level?.entitiesForRendering()?.filterIsInstance<LivingEntity>()
            ?.filter { e ->
                if (e === player || !e.isAlive || !TargetFilter.isValidTarget(player, e)) return@filter false
                val dx = e.x - player.x
                val dz = e.z - player.z
                val yaw = Math.toDegrees(kotlin.math.atan2(-dx, dz)).toFloat()
                val fov = net.minecraft.util.Mth.wrapDegrees(yaw - player.yRot)
                kotlin.math.abs(fov) < 45f
            } ?: emptyList()
        
        val closest = targets.minByOrNull { player.distanceTo(it) }

        val hasValidTarget = closest != null && (notMeleeRange.value <= 0f || player.distanceTo(closest) > notMeleeRange.value)

        if (!hasValidTarget) {
            if (state != State.IDLE && state != State.SWITCHING_BACK && state != State.RETRACTING) {
                state = State.SWITCHING_BACK
                waitTime = now + getDelay(actionDelay.value)
            } else if (state == State.IDLE) {
                return
            }
        }

        when (state) {
            State.IDLE -> {
                val (slot, type) = findRodOrThrowable(player)
                if (slot == -1) return
                
                originalSlot = player.inventory.selectedSlot
                currentItemType = type
                
                if (player.inventory.selectedSlot != slot) {
                    player.inventory.selectedSlot = slot
                    state = State.THROWING
                    waitTime = now + getDelay(actionDelay.value)
                } else {
                    state = State.THROWING
                    waitTime = now
                }
            }
            State.THROWING -> {
                val useKey = InputConstants.getKey(client.options.keyUse.saveString())
                KeyMapping.click(useKey)
                
                if (currentItemType == ItemType.ROD) {
                    state = State.WAITING_HOOK
                    val delay = getDelay(actionDelay.value)
                    waitTime = now + delay
                    retractTimer = now + delay + getDelay(retractSpeed.value)
                } else {
                    state = State.SWITCHING_BACK
                    waitTime = now + getDelay(throwSpeed.value)
                }
            }
            State.WAITING_HOOK -> {
                val hook = player.fishing
                if (hook == null || hook.onGround() || hook.hookedIn != null || now > retractTimer) {
                    state = State.RETRACTING
                    waitTime = now + getDelay(actionDelay.value)
                }
            }
            State.RETRACTING -> {
                val useKey = InputConstants.getKey(client.options.keyUse.saveString())
                KeyMapping.click(useKey)
                state = State.SWITCHING_BACK
                waitTime = now + getDelay(actionDelay.value)
            }
            State.SWITCHING_BACK -> {
                if (switchBackToWeapon.value) {
                    val weaponSlot = findWeapon(player)
                    if (weaponSlot != -1) {
                        player.inventory.selectedSlot = weaponSlot
                    } else if (originalSlot != -1) {
                        player.inventory.selectedSlot = originalSlot
                    }
                } else if (originalSlot != -1) {
                    player.inventory.selectedSlot = originalSlot
                }
                state = State.IDLE

                // toggle()
                // waitTime = now + getDelay(actionDelay.value)
            }
            else -> state = State.IDLE
        }
    }

    private fun findRodOrThrowable(player: net.minecraft.world.entity.player.Player): Pair<Int, ItemType> {
        for (i in 0..8) {
            val item = player.inventory.getItem(i).item
            if (item is FishingRodItem) {
                return i to ItemType.ROD
            }
        }
        if (allowThrowables.value) {
            for (i in 0..8) {
                val item = player.inventory.getItem(i).item
                if (item is SnowballItem || item is EggItem) {
                    return i to ItemType.PROJECTILE
                }
            }
        }
        return -1 to ItemType.NONE
    }

    private fun findWeapon(player: net.minecraft.world.entity.player.Player): Int {
        for (i in 0..8) {
            val stack = player.inventory.getItem(i)
            val isWeapon = stack.`is`(ItemTags.SWORDS) ||
                           stack.`is`(ItemTags.AXES) || 
                           stack.item is TridentItem
            if (isWeapon && !stack.isEmpty) return i
        }
        return -1
    }
}
