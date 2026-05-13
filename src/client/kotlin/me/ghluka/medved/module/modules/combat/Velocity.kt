package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.util.LagManager
import me.ghluka.medved.util.InputUtil
import me.ghluka.medved.module.Module
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket
import net.minecraft.world.InteractionHand
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.EntityHitResult
import net.minecraft.world.phys.HitResult
import net.minecraft.world.phys.Vec3

object Velocity : Module("Velocity", "Modifies knockback you receive from attacks", Category.COMBAT) {

    enum class Mode {
        MODIFY,
        REDUCE,
        FREEZE,
        REVERSE,
        CANCEL,
        JUMP_RESET,
        DELAY
    }

    val mode = enum("mode", Mode.MODIFY)

    val modifyPercent = float("modify %", 50f, 0f, 100f).also {
        it.visibleWhen = { mode.value == Mode.MODIFY }
    }
    val modifyYPercent = float("modify y %", 0f, 0f, 100f).also {
        it.visibleWhen = { mode.value == Mode.MODIFY }
    }

    val reduceAttackCount = intRange("attack count", 3 to 3, 1, 20).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }
    val reduceRange = float("range", 6f, 1f, 16f).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }
    val reduceHorizontal = float("horizontal", 0.6f, 0f, 1f).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }
    val reduceVertical = float("vertical", 1.0f, 0f, 1f).also {
        it.visibleWhen = { mode.value == Mode.REDUCE }
    }

    val reversePercent = float("reverse %", 50f, 0f, 100f).also {
        it.visibleWhen = { mode.value == Mode.REVERSE }
    }
    val reverseDelay = int("reverse delay (ticks)", 2, 0, 20).also {
        it.visibleWhen = { mode.value == Mode.REVERSE }
    }

    val jumpChance = int("chance %", 100, 0, 100).also {
        it.visibleWhen = { mode.value == Mode.JUMP_RESET }
    }
    val jumpTiming = intRange("timing (ms)", 70 to 100, 0, 500).also {
        it.visibleWhen = { mode.value == Mode.JUMP_RESET }
    }

    val groundDelay = intRange("ground delay (ms)", 200 to 250, 0, 2000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }
    val airDelay = intRange("air delay (ms)", 50 to 100, 0, 2000).also {
        it.visibleWhen = { mode.value == Mode.DELAY }
    }

    private var scheduledJumpAt = 0L
    private var releaseJumpAt = 0L

    @Volatile
    var packetDelayActive = false
        private set
    @Volatile
    private var packetDelayUntilMs = 0L
    private const val PACKET_DELAY_WINDOW_MS = 1500L

    @Volatile
    var remainingAttackCount = 0
        private set
    @Volatile
    var reduceTarget: Entity? = null
        private set

    @Volatile
    var receivedDamage = false

    private data class PendingReverse(val mx: Double, val my: Double, val mz: Double, var ticksLeft: Int)

    private val pendingReverses = ArrayDeque<PendingReverse>()

    @Volatile
    var freezeDelayActive = false
        private set
    @Volatile
    private var freezeCancelNext = false
    @Volatile
    private var freezeNeedClick = false

    @JvmField
    @Volatile
    var freezeWaitForUpdate = false
    @Volatile
    private var freezeWaitForPing = false

    @Volatile
    private var freezePostBlockUpdateTick = false
    @Volatile
    private var freezePostPongTick = false
    private var freezeFreezeTicks = 0
    private const val freeze_MAX_FREEZE_TICKS = 20

    fun scheduleJump(at: Long) {
        scheduledJumpAt = at
    }

    fun startPacketDelay() {
        val now = System.currentTimeMillis()
        packetDelayActive = true
        packetDelayUntilMs = maxOf(packetDelayUntilMs, now + PACKET_DELAY_WINDOW_MS)
    }

    fun isDelayWindowActive(): Boolean =
        packetDelayActive && System.currentTimeMillis() < packetDelayUntilMs

    fun triggerReduce(client: Minecraft) {
        receivedDamage = false
        val player = client.player ?: return
        val hitResult = client.hitResult ?: return
        if (hitResult.type != HitResult.Type.ENTITY) return

        val target = (hitResult as? EntityHitResult)?.entity as? LivingEntity ?: return
        if (target === player || target.isRemoved) return
        val rangeSq = (reduceRange.value * reduceRange.value).toDouble()
        if (target.distanceToSqr(player) > rangeSq) return

        val (lo, hi) = reduceAttackCount.value
        remainingAttackCount = if (hi > lo) lo + (Math.random() * (hi - lo + 1)).toInt() else lo
        reduceTarget = target
    }

    fun scheduleReverse(mx: Double, my: Double, mz: Double) {
        pendingReverses.addLast(PendingReverse(mx, my, mz, reverseDelay.value))
    }

    fun onFreezeDamage() {
        freezeCancelNext = true
    }

    fun tryConsumeFreezeVelocity(): Boolean {
        if (!freezeCancelNext) return false
        freezeCancelNext = false
        freezeDelayActive = true
        freezeNeedClick = true
        return true
    }

    fun onFreezeBlockUpdate() {
        freezePostBlockUpdateTick = true
    }

    fun onFreezePong() {
        if (!freezeWaitForPing) return
        freezePostPongTick = true
    }

    init {
        ClientTickEvents.START_CLIENT_TICK.register { client ->
            if (!isEnabled()) return@register
            val player = client.player ?: return@register
            val world  = client.level  ?: return@register
            if (client.player == null || client.level == null) {
                resetState(client)
                return@register
            }

            val now = System.currentTimeMillis()

            if (scheduledJumpAt > 0L && now >= scheduledJumpAt) {
                scheduledJumpAt = 0L
                if (player.onGround()) {
                    client.options.keyJump.setDown(true)
                    releaseJumpAt = now + 100L
                }
            }
            if (releaseJumpAt > 0L && now >= releaseJumpAt) {
                releaseJumpAt = 0L
                client.options.keyJump.setDown(false)
            }

            if (packetDelayActive && now >= packetDelayUntilMs) {
                packetDelayActive = false
            }

            if (mode.value == Mode.REDUCE && remainingAttackCount > 0) {
                val target = reduceTarget
                if (target == null || target.isRemoved) {
                    remainingAttackCount = 0
                    reduceTarget = null
                } else {
                    player.isSprinting = false
                    client.gameMode?.attack(player, target)
                    player.swing(InteractionHand.MAIN_HAND)

                    val h = reduceHorizontal.value.toDouble()
                    val v = reduceVertical.value.toDouble()
                    val dm = player.deltaMovement
                    player.deltaMovement = Vec3(dm.x * h, dm.y * v, dm.z * h)

                    remainingAttackCount--
                    if (remainingAttackCount == 0) reduceTarget = null
                }
            }

            val reverseIter = pendingReverses.iterator()
            while (reverseIter.hasNext()) {
                val pending = reverseIter.next()
                if (pending.ticksLeft <= 0) {
                    val factor = reversePercent.value / 100f
                    val curY = player.deltaMovement.y
                    player.lerpMotion(Vec3(-pending.mx * factor, curY, -pending.mz * factor))
                    reverseIter.remove()
                } else {
                    pending.ticksLeft--
                }
            }

            if (mode.value == Mode.FREEZE) {
                tickFreeze(client, player)
            }
        }
    }
    private fun tickFreeze(client: Minecraft, player: LocalPlayer) {
        if (freezePostBlockUpdateTick) {
            freezePostBlockUpdateTick = false
            freezeWaitForPing         = true
            freezeNeedClick           = false
        }

        if (freezePostPongTick) {
            freezePostPongTick   = false
            freezeWaitForUpdate  = false
            freezeWaitForPing    = false
            freezeFreezeTicks    = 0

            client.options.keyUp.setDown(InputUtil.isPhysicalKeyDown(client.options.keyUp))
            client.options.keyDown.setDown(InputUtil.isPhysicalKeyDown(client.options.keyDown))
            client.options.keyLeft.setDown(InputUtil.isPhysicalKeyDown(client.options.keyLeft))
            client.options.keyRight.setDown(InputUtil.isPhysicalKeyDown(client.options.keyRight))
        }

        if (freezeWaitForUpdate) {
            freezeFreezeTicks++
            if (freezeFreezeTicks > freeze_MAX_FREEZE_TICKS) {
                resetFreezeState(client)
                return
            }
            client.options.keyUp.setDown(false)
            client.options.keyDown.setDown(false)
            client.options.keyLeft.setDown(false)
            client.options.keyRight.setDown(false)
        }

        if (freezeNeedClick && !freezeWaitForUpdate && !player.isUsingItem) {
            val hitResult = freezeRaytraceDown(client, player) ?: return
            if (hitResult.blockPos.relative(hitResult.direction) != player.blockPosition()) return

            freezeDelayActive = false
            LagManager.flushAllIncoming()

            client.gameMode?.useItemOn(player, InteractionHand.MAIN_HAND, hitResult)
            player.swing(InteractionHand.MAIN_HAND)

            val conn = client.connection
            if (conn != null) {
                if (player.xRot != 90f) {
                    conn.send(ServerboundMovePlayerPacket.Rot(
                        player.yRot, 90f, player.onGround(), player.horizontalCollision
                    ))
                } else {
                    conn.send(ServerboundMovePlayerPacket.StatusOnly(
                        player.onGround(), player.horizontalCollision
                    ))
                }
            }

            freezeFreezeTicks   = 0
            freezeWaitForUpdate = true
            freezeNeedClick     = false
        }
    }

    private fun freezeRaytraceDown(client: Minecraft, player: LocalPlayer): BlockHitResult? {
        val level  = client.level ?: return null
        val eyePos = player.getEyePosition(1f)
        val endPos = eyePos.add(0.0, -5.0, 0.0)
        val result = level.clip(
            ClipContext(eyePos, endPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player)
        )
        return if (result.type == HitResult.Type.BLOCK) result else null
    }

    override fun hudInfo(): String = when (mode.value) {
        Mode.MODIFY     -> "Modify ${modifyPercent.value.toInt()}% ${modifyYPercent.value.toInt()}%"
        Mode.REDUCE     -> "Reduce ${(reduceHorizontal.value * 100).toInt()}% ${(reduceVertical.value * 100).toInt()}%"
        Mode.FREEZE     -> "Freeze"
        Mode.REVERSE    -> "Reverse ${reversePercent.value.toInt()}%"
        Mode.JUMP_RESET -> "Jump Reset"
        Mode.DELAY      -> "Delay"
        Mode.CANCEL     -> "Cancel"
    }


    override fun hudInfoColor(): Int =
        if (freezeWaitForUpdate) (255 shl 24) or (255 shl 16) or (80 shl 8) or 80
        else if (isDelayWindowActive()) (255 shl 24) or (255 shl 16) or (80 shl 8) or 80
        else super.hudInfoColor()

    private fun resetFreezeState(client: Minecraft? = null) {
        freezeDelayActive         = false
        freezeCancelNext          = false
        freezeNeedClick           = false
        freezeWaitForUpdate       = false
        freezeWaitForPing         = false
        freezePostBlockUpdateTick  = false
        freezePostPongTick         = false
        freezeFreezeTicks         = 0
        client?.options?.let { opts ->
            opts.keyUp.setDown(InputUtil.isPhysicalKeyDown(opts.keyUp))
            opts.keyDown.setDown(InputUtil.isPhysicalKeyDown(opts.keyDown))
            opts.keyLeft.setDown(InputUtil.isPhysicalKeyDown(opts.keyLeft))
            opts.keyRight.setDown(InputUtil.isPhysicalKeyDown(opts.keyRight))
        }
    }

    private fun resetState(client: Minecraft? = null) {
        packetDelayActive    = false
        remainingAttackCount = 0
        reduceTarget         = null
        receivedDamage       = false
        pendingReverses.clear()
        resetFreezeState(client)
    }

    override fun onDisabled() {
        scheduledJumpAt    = 0L
        releaseJumpAt      = 0L
        packetDelayUntilMs = 0L
        resetState(Minecraft.getInstance())
        Minecraft.getInstance().options?.keyJump?.setDown(false)
        LagManager.flushAllOutgoing()
        LagManager.flushAllIncoming()
    }
}