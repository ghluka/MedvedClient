package me.ghluka.medved.module.modules.other

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object Font : Module("Font", "Customize the font used in GUI and HUD elements", Category.OTHER) {

    override val isProtected = true
    override val showInModulesList = false
    init { enabled.value = true }

    enum class FontChoice(val namespace: String, val path: String) {
        MINECRAFT("medved", "minecraft"),
        ARIAL("medved", "arial"),
        SAN_FRANCISCO("medved", "sanfrancisco"),
        MONTSERRAT("medved", "montserrat"),
        JETBRAINS_MONO("medved", "jetbrains_mono"),
        COMIC_RELIEF("medved", "comic_relief"),
        IMPACT("medved", "impact"),
        UNIFORM("minecraft", "uniform"),
    }

    val fontChoice = enum("font", FontChoice.MONTSERRAT)
    private val renderScale = ThreadLocal.withInitial { 1.0f }

    fun getFont(): Font = Minecraft.getInstance().font

    fun <T> withRenderScale(scale: Float, block: () -> T): T {
        val previous = renderScale.get()
        renderScale.set(previous * scale.coerceAtLeast(0.01f))
        return try {
            block()
        } finally {
            renderScale.set(previous)
        }
    }

    fun fontStyle(): Style {
        val choice = fontChoice.value
        val path = choice.pathForCurrentScale()
        val desc = FontDescription.Resource(Identifier.fromNamespaceAndPath(choice.namespace, path))
        return Style.EMPTY.withFont(desc)
    }

    fun styledText(text: String): Component =
        Component.literal(text).withStyle(fontStyle())

    private fun FontChoice.pathForCurrentScale(): String {
        if (namespace != "medved") return path
        val physicalScale = Minecraft.getInstance().window.guiScale * renderScale.get()
        return if (physicalScale >= 3.0) "${path}_hd" else path
    }
}
