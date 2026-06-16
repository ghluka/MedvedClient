package me.ghluka.medved.module.modules.render

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.RenderUtil
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.entity.state.AvatarRenderState
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
import net.minecraft.world.entity.player.Player

object Chams : Module(
    name = "Chams",
    description = "Tints player models and renders them through walls",
    category = Category.RENDER
) {
    enum class Style { SHADER, NONE }

    private val style = enum("style", Style.SHADER)
    private val ignoreTargetFilter = boolean("ignore target filter", false)
    private val shader = enum("shader", RenderUtil.ChamsShader.GALAXY).also {
        it.visibleWhen = { style.value == Style.SHADER }
    }
    private val shaderColor = color("shader color", Color(120, 210, 255, 190), allowAlpha = true).also {
        it.visibleWhen = { style.value == Style.SHADER && shader.value == RenderUtil.ChamsShader.COLOR }
    }

    private val heldItemSubmitDepth = ThreadLocal.withInitial { 0 }
    private val layerSubmitDepth = ThreadLocal.withInitial { 0 }

    init {
        shaderColor.pickerMode = ColorEntry.PickerMode.THEME
    }

    @JvmStatic
    fun shouldRender(state: LivingEntityRenderState): Boolean {
        if (!isEnabled()) return false
        return playerFromState(state)?.let { target ->
            val viewer = Minecraft.getInstance().player ?: return false
            target !== viewer && shouldRenderTarget(viewer, target)
        } == true
    }

    @JvmStatic
    fun tint(state: LivingEntityRenderState): Int {
        return if (style.value == Style.SHADER && shader.value == RenderUtil.ChamsShader.COLOR) shaderTint()
        else 0xFFFFFFFF.toInt()
    }

    @JvmStatic
    fun modelSubmitOrder(): Int {
        return when (style.value) {
            Style.SHADER -> 1
            Style.NONE -> -1
        }
    }

    @JvmStatic
    fun renderType(state: LivingEntityRenderState, texture: Identifier, outline: Boolean): RenderType {
        return when (style.value) {
            Style.SHADER -> RenderUtil.shaderChamsEntity(texture, outline, shader.value, shaderTint())
            Style.NONE -> RenderUtil.vanillaChamsEntity(texture, outline)
        }
    }

    @JvmStatic
    fun itemRenderType(texture: Identifier, translucent: Boolean): RenderType {
        return when (style.value) {
            Style.SHADER -> RenderUtil.shaderItemChams(texture, translucent, shader.value, shaderTint())
            Style.NONE -> RenderUtil.itemChams(texture, translucent)
        }
    }

    @JvmStatic
    fun armorRenderType(texture: Identifier): RenderType {
        return when (style.value) {
            Style.SHADER -> RenderUtil.shaderChamsEntity(texture, false, shader.value, shaderTint())
            Style.NONE -> RenderUtil.vanillaChamsEntity(texture, false)
        }
    }

    @JvmStatic
    fun beginHeldItemSubmit() {
        heldItemSubmitDepth.set(heldItemSubmitDepth.get() + 1)
    }

    @JvmStatic
    fun endHeldItemSubmit() {
        heldItemSubmitDepth.set((heldItemSubmitDepth.get() - 1).coerceAtLeast(0))
    }

    @JvmStatic
    fun isSubmittingHeldItem(): Boolean = heldItemSubmitDepth.get() > 0

    @JvmStatic
    fun beginLayerSubmit() {
        layerSubmitDepth.set(layerSubmitDepth.get() + 1)
    }

    @JvmStatic
    fun endLayerSubmit() {
        layerSubmitDepth.set((layerSubmitDepth.get() - 1).coerceAtLeast(0))
    }

    @JvmStatic
    fun isSubmittingLayer(): Boolean = layerSubmitDepth.get() > 0

    private fun playerFromState(state: LivingEntityRenderState): Player? {
        if (state !is AvatarRenderState) return null
        val level = Minecraft.getInstance().level ?: return null
        return level.getEntity(state.id) as? Player
    }

    private fun shouldRenderTarget(viewer: Player, target: Player): Boolean =
        ignoreTargetFilter.value || TargetFilter.isValidTarget(viewer, target)

    private fun shaderTint(): Int =
        if (shader.value == RenderUtil.ChamsShader.COLOR) shaderColor.liveColor(shaderColor.value).argb
        else 0xFFFFFFFF.toInt()
}
