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

    fun getFont(): Font = Minecraft.getInstance().font

    fun fontStyle(): Style {
        val choice = fontChoice.value
        val desc = FontDescription.Resource(Identifier.fromNamespaceAndPath(choice.namespace, choice.path))
        return Style.EMPTY.withFont(desc)
    }

    fun styledText(text: String): Component =
        Component.literal(text).withStyle(fontStyle())
}
