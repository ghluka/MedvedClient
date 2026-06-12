package me.ghluka.medved.module.modules.render

import com.mojang.math.Axis
import me.ghluka.medved.mixin.client.LivingEntityAccessor
import me.ghluka.medved.module.Module
import net.minecraft.world.entity.HumanoidArm
import net.minecraft.util.Mth
import com.mojang.blaze3d.vertex.PoseStack
import me.ghluka.medved.module.modules.combat.AutoBlock
import me.ghluka.medved.module.modules.combat.AutoBlock.canUseAsBlock
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.tags.ItemTags
import net.minecraft.world.item.ItemStack

object Animations : Module(
    name = "Animations",
    description = "Affects item animations and how you hold items",
    category = Category.RENDER,
) {
    enum class Mode {
        ONE_SEVEN,
        PUSHDOWN,
        SPIN,
        CHILL,
        HELICOPTER
    }
    val mode = enum("mode", Mode.ONE_SEVEN)
    val translateY = float("translate y", 0.1f, 0.05f, 0.3f).also {
        it.visibleWhen = { mode.value == Mode.ONE_SEVEN || mode.value == Mode.CHILL }
    }
    val swingProgressScale = float("swing scale", 0.9f, 0.1f, 1.0f).also {
        it.visibleWhen = { mode.value == Mode.ONE_SEVEN }
    }

    val visualBlock = boolean("visual block", false)
    val visualBlockSwords = boolean("visual block swords", false).also {
        it.visibleWhen = { visualBlock.value }
    }

    val mainHand = boolean("main hand", false)
    val mainHandScale = float("main hand scale", 0f, -5f, 5f).also { it.visibleWhen = { mainHand.value } }
    val mainHandX = float("main hand x", 0f, -5f, 5f).also { it.visibleWhen = { mainHand.value } }
    val mainHandY = float("main hand y", 0f, -5f, 5f).also { it.visibleWhen = { mainHand.value } }
    val mainHandRotX = float("main hand rot x", 0f, -50f, 50f).also { it.visibleWhen = { mainHand.value } }
    val mainHandRotY = float("main hand rot y", 0f, -50f, 50f).also { it.visibleWhen = { mainHand.value } }
    val mainHandRotZ = float("main hand rot z", 0f, -50f, 50f).also { it.visibleWhen = { mainHand.value } }

    val offHand = boolean("off hand", false)
    val offHandScale = float("off hand scale", 0f, -5f, 5f).also { it.visibleWhen = { offHand.value } }
    val offHandX = float("off hand x", 0f, -5f, 5f).also { it.visibleWhen = { offHand.value } }
    val offHandY = float("off hand y", 0f, -5f, 5f).also { it.visibleWhen = { offHand.value } }
    val offHandRotX = float("off hand rot x", 0f, -50f, 50f).also { it.visibleWhen = { offHand.value } }
    val offHandRotY = float("off hand rot y", 0f, -50f, 50f).also { it.visibleWhen = { offHand.value } }
    val offHandRotZ = float("off hand rot z", 0f, -50f, 50f).also { it.visibleWhen = { offHand.value } }

    val swingDuration = int("swing duration", 6, 2, 20)
    val cancelEquipProgress = boolean("cancel equip progress", true)

    fun applySwingOffset(matrices: PoseStack, arm: HumanoidArm, swingProgress: Float) {
        val armSide = if (arm == HumanoidArm.RIGHT) 1 else -1
        val f = Mth.sin((swingProgress * swingProgress * Math.PI.toFloat()).toDouble())
        matrices.mulPose(Axis.YP.rotationDegrees(armSide.toFloat() * (45.0f + f * -20.0f)))
        val g = Mth.sin((Mth.sqrt(swingProgress) * Math.PI.toFloat()).toDouble())
        matrices.mulPose(Axis.ZP.rotationDegrees(armSide.toFloat() * g * -20.0f))
        matrices.mulPose(Axis.XP.rotationDegrees(g * -80.0f))
        matrices.mulPose(Axis.YP.rotationDegrees(armSide.toFloat() * -45.0f))
    }

    private fun swingPulse(swingProgress: Float): Float {
        return Mth.sin((Mth.sqrt(swingProgress) * Math.PI.toFloat()).toDouble()).toFloat()
    }

    private fun applyBlockBase(
        matrices: PoseStack,
        arm: HumanoidArm,
        swingProgress: Float,
        swingScale: Float = 0.9f,
        translateY: Float = 0.1f,
        reverseSwing: Boolean = false,
    ) {
        matrices.translate(if (arm == HumanoidArm.RIGHT) -0.1f else 0.1f, translateY, 0.0f)
        applySwingOffset(matrices, arm, swingProgress * swingScale * if (reverseSwing) -1f else 1f)
    }

    fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
        when (mode.value) {
            Mode.ONE_SEVEN -> {
                applyBlockBase(matrices, arm, swingProgress, swingProgressScale.value, translateY.value)
            }
            Mode.PUSHDOWN -> {
                matrices.translate(if (arm == HumanoidArm.RIGHT) -0.1f else 0.1f, 0.1f, 0.0f)
                val g = Mth.sin((Mth.sqrt(swingProgress) * Math.PI.toFloat()).toDouble())
                matrices.mulPose(
                    Axis.ZP.rotationDegrees(
                        (if (arm == HumanoidArm.RIGHT) 1 else -1) * g * 10.0f
                    )
                )
                matrices.mulPose(Axis.XP.rotationDegrees(g * -35.0f))
            }
            Mode.SPIN -> {
                applyBlockBase(matrices, arm, swingProgress)
                matrices.mulPose(Axis.ZP.rotationDegrees((if (arm == HumanoidArm.RIGHT) 1 else -1) * swingProgress * 360.0f))
            }
            Mode.CHILL -> {
                applyBlockBase(matrices, arm, swingProgress * 0.45f, translateY = translateY.value)
                val pulse = swingPulse(swingProgress)
                matrices.mulPose(Axis.ZP.rotationDegrees((if (arm == HumanoidArm.RIGHT) 1 else -1) * pulse * 30.0f))
                matrices.mulPose(Axis.YP.rotationDegrees((if (arm == HumanoidArm.RIGHT) 1 else -1) * pulse * 18.0f))
                matrices.mulPose(Axis.XP.rotationDegrees(-15.0f - pulse * 20.0f))
            }
            Mode.HELICOPTER -> {
                applyBlockBase(matrices, arm, swingProgress, swingScale = 0.0f)
                val spin = (System.currentTimeMillis() / 3L % 360L).toFloat()
                matrices.mulPose(Axis.ZP.rotationDegrees((if (arm == HumanoidArm.RIGHT) 1 else -1) * spin))
                matrices.mulPose(Axis.XP.rotationDegrees(-90.0f))
            }
        }
    }

    private var wasBlocking = false

    @JvmStatic
    fun shouldUseBlockAnimationItem(stack: ItemStack): Boolean =
        stack.canUseAsBlock() || (visualBlock.value && visualBlockSwords.value && stack.`is`(ItemTags.SWORDS))

    @JvmStatic
    fun shouldAnimateSwordBlock(entity: AbstractClientPlayer, swingProgress: Float): Boolean {
        if (!isEnabled()) return false

        val hasBlockItem = shouldUseBlockAnimationItem(entity.mainHandItem) ||
                shouldUseBlockAnimationItem(entity.offhandItem)
        if (!hasBlockItem) {
            wasBlocking = false
            return false
        }

        val isBlockingNow = entity.isUsingItem || AutoBlock.isBlocking
        if (isBlockingNow) {
            wasBlocking = true
            return true
        }

        if ((wasBlocking || visualBlock.value) && swingProgress > 0f) {
            return true
        }

        wasBlocking = false
        return false
    }

    object OneSevenDefault {
        fun transform(matrices: PoseStack, arm: HumanoidArm, equipProgress: Float, swingProgress: Float) {
            matrices.translate(if (arm == HumanoidArm.RIGHT) -0.1f else 0.1f, 0.1f, 0.0f)
            Animations.applySwingOffset(matrices, arm, swingProgress * 0.9f)
        }
    }


}
