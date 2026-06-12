package me.ghluka.medved.module.modules.combat

import com.mojang.blaze3d.platform.InputConstants
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.player.Blink
import me.ghluka.medved.module.modules.player.FakeLag
import me.ghluka.medved.util.LagManager
import me.ghluka.medved.util.interactBlock
import me.ghluka.medved.util.interactEntity
import me.ghluka.medved.util.useItemStrict
import net.minecraft.client.Minecraft
import net.minecraft.client.KeyMapping
import net.minecraft.core.component.DataComponents
import net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS
import net.minecraft.world.InteractionHand
import net.minecraft.world.InteractionResult
import net.minecraft.world.attribute.modifier.AttributeModifier
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.ItemUseAnimation
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult
import kotlin.random.Random

object AutoBlock : Module(
    name = "Auto Block",
    description = "Holds block with your sword to reduce incoming damage",
    category = Category.COMBAT
) {

    enum class BlockMode { MANUAL, AUTOMATIC }

    private val mode = enum("mode", BlockMode.AUTOMATIC)

    private val blockChance = float("block chance (%)", 80f, 0f, 100f).also {
        it.visibleWhen = { mode.value == BlockMode.MANUAL }
    }
    private val blockCps = float("block cps", 6f, 1f, 8f).also {
        it.visibleWhen = { mode.value == BlockMode.AUTOMATIC }
    }

    private val blockRange = float("range", 6.5f, 1.0f, 20.0f)

    private val maxHoldMs = int("max hold (ms)", 100, 50, 200)

    private val condLmb    = boolean("check left click",  true)
    private val condRmb    = boolean("check right click", false)
    private val condDamage = boolean("check took damage", false)
    private val damageWindowMs = int("damage window (ms)", 3000, 250, 10000).also {
        it.visibleWhen = { condDamage.value }
    }

    private val releaseMs = intRange("release (ms)", 10 to 30, 1, 40)

    private val lagEnabled = boolean("lag", false)
    private val lagChance = float("lag chance (%)", 100f, 0f, 100f).also {
        it.visibleWhen = { lagEnabled.value }
    }
    private val lagDurationMs = intRange("lag duration (ms)", 150 to 250, 50, 1000).also {
        it.visibleWhen = { lagEnabled.value }
    }
    private val preventDelayAttacks = boolean("prevent delaying attacks", false).also {
        it.visibleWhen = { lagEnabled.value }
    }
    private val blockAgainImmediately = boolean("block again immediately", true).also {
        it.visibleWhen = { lagEnabled.value }
    }

    @JvmField
    val slowDown = boolean("slow down", true)

    @Volatile var isLagging  = false
        private set
    @JvmField
    var isBlocking            = false

    private var attackedThisTick = false
    private var releaseUntilMs    = 0L
    private var lagUntilMs        = 0L
    private var prevAttackDown    = false
    private var manualBlockActive = false
    private var nextAutoCycleMs   = 0L
    private var blockStartMs      = 0L
    private var lastDamageTakenMs = 0L
    private var prevHurtTime      = 0

    private var lastNearbyEntityMs = 0L

    private fun isBehaviorActive(): Boolean =
        enabled.value || (SilentAura.isEnabled() && SilentAura.autoBlock.value)

    init {
        AttackEntityCallback.EVENT.register { player, _, _, _, _ ->
            if (player === Minecraft.getInstance().player && isBehaviorActive()) {
                attackedThisTick = true
                val stoppedLag = lagEnabled.value && preventDelayAttacks.value && isLagging
                if (stoppedLag) {
                    stopLag(reblock = false)
                    releaseUntilMs = 0L
                }

                if (!stoppedLag && !(SilentAura.isEnabled() && SilentAura.autoBlock.value) && isBlocking) {
                    releaseUntilMs = System.currentTimeMillis() + randomRelease()
                    release(Minecraft.getInstance(), allowLag = true)
                }
            }
            InteractionResult.PASS
        }

        ClientTickEvents.START_CLIENT_TICK.register { client ->
            val now = System.currentTimeMillis()
            val player = client.player

            if (player != null) {
                if (player.hurtTime > prevHurtTime) lastDamageTakenMs = now
                prevHurtTime = player.hurtTime
            }

            if (isBlocking && player != null && !player.isUsingItem) {
                isBlocking = false
                blockStartMs = 0L
            }

            if (isLagging && now >= lagUntilMs) {
                val reblock = isBehaviorActive() && lagEnabled.value && blockAgainImmediately.value
                stopLag(reblock = reblock)
                if (reblock) releaseUntilMs = 0L
            }

            val silentAuraAutoBlock = SilentAura.isEnabled() && SilentAura.autoBlock.value
            val active = isBehaviorActive()

            if (!active && !silentAuraAutoBlock) {
                forceRelease(client)
                manualBlockActive = false
                return@register
            }

            if (player == null || client.gui.screen() != null) {
                forceRelease(client)
                manualBlockActive = false
                prevAttackDown = client.options.keyAttack.isDown
                return@register
            }

            if (active && !silentAuraAutoBlock && !conditionsMet(client, now)) {
                forceRelease(client)
                manualBlockActive = false
                return@register
            }

            val attackDown = client.options.keyAttack.isDown

            if (!silentAuraAutoBlock && attackDown && !prevAttackDown && isBlocking) {
                releaseUntilMs = now + randomRelease()
                release(client, allowLag = true)
                prevAttackDown = attackDown
                return@register
            }

            val hasBlockItem = InteractionHand.entries.any { hand ->
                player.getItemInHand(hand).canUseAsBlock()
            }

            if (!hasBlockItem) {
                forceRelease(client)
                manualBlockActive = false
                prevAttackDown = attackDown
                return@register
            }

            val level = client.level
            val entityNearby = level != null && level.entitiesForRendering()
                .filterIsInstance<LivingEntity>()
                .any { e -> e !== player && !e.isDeadOrDying && player.distanceTo(e) <= blockRange.value }

            if (entityNearby) {
                lastNearbyEntityMs = now
            } else {
                if (condDamage.value && (now - lastNearbyEntityMs) >= 250L) {
                    lastDamageTakenMs = 0L
                }

                release(client)
                manualBlockActive = false
                prevAttackDown = attackDown
                return@register
            }

            if (isBlocking && blockStartMs > 0 && (now - blockStartMs) >= maxHoldMs.value) {
                //println("max hold timeout reached, releasing")
                releaseUntilMs = now + randomRelease()
                release(client)
                manualBlockActive = false
                prevAttackDown = attackDown
                return@register
            }

            val silentAuraFired =
                SilentAura.isEnabled() &&
                        SilentAura.autoBlock.value

            val leftClickerFired =
                LeftClicker.isEnabled() &&
                        LeftClicker.clickedThisTick &&
                        !silentAuraFired

            attackedThisTick = false
            SilentAura.attackedThisTick = false

            val swungThisTick = leftClickerFired// || silentAuraFired

            if (swungThisTick) {
                if (mode.value == BlockMode.MANUAL) {
                    manualBlockActive = Random.nextFloat() * 100f < blockChance.value
                }

                val releaseWindow = randomRelease()
                releaseUntilMs = now + releaseWindow
                //println("release window set: now=$now, releaseUntilMs=$releaseUntilMs (window: $releaseWindow ms)")
            }

            prevAttackDown = attackDown

            if (now < releaseUntilMs) {
                val waitTime = releaseUntilMs - now
                //println("inside release window (${waitTime}ms remaining)")
                release(client)
                return@register
            }

            if (silentAuraAutoBlock && SilentAura.target != null) {
                doBlock(now)
                return@register
            }

            when (mode.value) {
                BlockMode.MANUAL -> {
                    if (manualBlockActive) {
                        doBlock(now)
                    } else release(client)
                }

                BlockMode.AUTOMATIC -> {
                    if (nextAutoCycleMs == 0L) nextAutoCycleMs = now + cycleDurationMs()

                    if (isBlocking && now >= nextAutoCycleMs) {
                        releaseUntilMs = now + randomRelease()
                        nextAutoCycleMs = now + cycleDurationMs()
                        release(client)
                    } else {
                        doBlock(now)
                    }
                }
            }
        }
    }

    private fun randomRelease(): Long {
        val (lo, hi) = releaseMs.value
        return if (hi > lo) (lo..hi).random().toLong() else lo.toLong()
    }

    private fun cycleDurationMs(): Long = (1000f / blockCps.value).toLong()

    private fun conditionsMet(client: Minecraft, now: Long): Boolean {
        if (!condLmb.value && !condRmb.value && !condDamage.value) return true
        return (condLmb.value    && isAttackHeld(client)) ||
                (condRmb.value    && isUseHeld(client))   ||
                (condDamage.value && lastDamageTakenMs > 0L && (now - lastDamageTakenMs) <= damageWindowMs.value)
    }

    override fun onEnabled() {
        isBlocking         = false
        isLagging          = false
        releaseUntilMs     = 0L
        lagUntilMs         = 0L
        prevAttackDown     = false
        manualBlockActive  = false
        nextAutoCycleMs    = 0L
        blockStartMs       = 0L
        lastDamageTakenMs  = 0L
        lastNearbyEntityMs = 0L
        prevHurtTime       = 0
    }

    override fun onDisabled() {
        if (isLagging) stopLag(reblock = false)
        Minecraft.getInstance().player?.let { removeSlowdown(it) }
        forceRelease(Minecraft.getInstance())
        Minecraft.getInstance().options.keyUse.setDown(isPhysicalKeyDown(Minecraft.getInstance().options.keyUse))
        releaseUntilMs     = 0L
        prevAttackDown     = false
        nextAutoCycleMs    = 0L
        blockStartMs       = 0L
        lastNearbyEntityMs = 0L
    }

    private fun startLag() {
        isLagging  = true
        val (lo, hi) = lagDurationMs.value
        lagUntilMs = System.currentTimeMillis() + (if (hi > lo) (lo..hi).random().toLong() else lo.toLong())
    }

    private fun stopLag(reblock: Boolean) {
        if (!isLagging) return
        isLagging  = false
        lagUntilMs = 0L

        val otherLagging =
            (Blink.isEnabled()   && Blink.holding)                              ||
                    (FakeLag.isEnabled() && FakeLag.isCurrentlyLagging)                 ||
                    (Velocity.isEnabled()
                            && Velocity.mode.value == Velocity.Mode.DELAY
                            && Velocity.isDelayWindowActive())

        if (!otherLagging) LagManager.flushAllOutgoing()
        if (reblock) releaseUntilMs = 0L
    }

    private fun doBlock(now: Long) {
        if (isBlocking) {
            //println("already isBlocking")
            return
        }
        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player?.isUsingItem == true) {
            //println("skipping, player.isUsingItem == true")
            return
        }


        if (slowDown.value && player != null) applySlowdown(player)
        //mc.options.keyUse.setDown(slowDown.value)
        block()
        if (isBlocking) blockStartMs = now
    }
    
    private fun release(client: Minecraft, allowLag: Boolean = true) {
        if (!isBlocking) {
            client.player?.let { removeSlowdown(it) }
            return
        }

        val player = client.player
        if (player == null || !player.isUsingItem) {
            isBlocking   = false
            blockStartMs = 0L
            return
        }

        if (allowLag && lagEnabled.value && !isLagging && client.gui.screen() == null) {
            if (Random.nextFloat() * 100f < lagChance.value) startLag()
        }

        isBlocking   = false
        blockStartMs = 0L
        player.stopUsingItem()
        client.player?.let { removeSlowdown(it) }
    }

    fun forceRelease(client: Minecraft) = release(client, allowLag = false)

    fun prepareAuraSwing(client: Minecraft) {
        val now = System.currentTimeMillis()

        if (releaseUntilMs > now) {
            release(client, allowLag = true)
            return
        }

        releaseUntilMs = now + randomRelease()

        //println(
        //    "aura swing, release window set: " +
        //            "now=$now, releaseUntilMs=$releaseUntilMs (SilentAura)"
        //)

        release(client, allowLag = true)
    }

    private fun block() {
        val mc     = Minecraft.getInstance()
        val player = mc.player     ?: return

        val blockHand = InteractionHand.entries.find { hand ->
            player.getItemInHand(hand).canUseAsBlock()
        } ?: run {
            return
        }

        val hitResult = mc.hitResult
        val entityHit = hitResult as? net.minecraft.world.phys.EntityHitResult

        player.startUsingItem(blockHand)
        val useItemResult = if (entityHit != null) {
            //println("hitting entity")
            interactEntity(entityHit.entity, entityHit, blockHand)
            useItemStrict(blockHand)
        } else if (hitResult != null && hitResult.type == HitResult.Type.BLOCK) {
            //println("hitting block")
            interactBlock(hitResult as BlockHitResult, blockHand)
            useItemStrict(blockHand)
        } else {
            //println("hitting")
            useItemStrict(blockHand)
        }

        isBlocking = useItemResult?.isUseItemSuccess == true
    }

    fun ItemStack.canUseAsBlock(): Boolean =
        has(BLOCKS_ATTACKS) ||
                get(DataComponents.CONSUMABLE)?.animation == ItemUseAnimation.BLOCK

    private fun isUseHeld(client: Minecraft): Boolean {
        val key = InputConstants.getKey(client.options.keyUse.saveString())
        return if (key.type == InputConstants.Type.MOUSE)
            org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.window.handle(), key.value) ==
                    org.lwjgl.glfw.GLFW.GLFW_PRESS
        else
            InputConstants.isKeyDown(client.window, key.value) || client.options.keyUse.isDown
    }

    private fun isAttackHeld(client: Minecraft): Boolean {
        val key = InputConstants.getKey(client.options.keyAttack.saveString())
        return if (key.type == InputConstants.Type.MOUSE)
            org.lwjgl.glfw.GLFW.glfwGetMouseButton(client.window.handle(), key.value) ==
                    org.lwjgl.glfw.GLFW.GLFW_PRESS
        else
            InputConstants.isKeyDown(client.window, key.value) || client.options.keyAttack.isDown
    }

    private fun isPhysicalKeyDown(mapping: KeyMapping): Boolean {
        val key = InputConstants.getKey(mapping.saveString())
        return if (key.type == InputConstants.Type.MOUSE) {
            org.lwjgl.glfw.GLFW.glfwGetMouseButton(Minecraft.getInstance().window.handle(), key.value) == org.lwjgl.glfw.GLFW.GLFW_PRESS
        } else {
            InputConstants.isKeyDown(Minecraft.getInstance().window, key.value)
        }
    }

    private fun isHoldingBlockable(client: Minecraft): Boolean {
        val player = client.player ?: return false
        return InteractionHand.entries.any { hand ->
            player.getItemInHand(hand).canUseAsBlock()
        }
    }

    @JvmStatic
    var slowingDown: Boolean = false
    private fun applySlowdown(player: net.minecraft.client.player.LocalPlayer) {
        slowingDown = true
    }

    private fun removeSlowdown(player: net.minecraft.client.player.LocalPlayer) {
        slowingDown = false
    }
}
