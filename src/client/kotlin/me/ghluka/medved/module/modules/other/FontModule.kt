package me.ghluka.medved.module.modules.other

import me.ghluka.medved.module.Module
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier

object FontModule : Module("Font", "Customize the font used in GUI and HUD elements", Category.OTHER) {

    override val isProtected = true
    init { enabled.value = true }

    enum class FontChoice(val namespace: String, val path: String) {
        MINECRAFT("minecraft", "default"),
        UNIFORM("minecraft", "uniform"),
        MONTSERRAT("medved", "montserrat"),
        JETBRAINS_MONO("medved", "jetbrains_mono")
    }

    val fontChoice = enum("font", FontChoice.MINECRAFT)

    fun getFont(): Font = Minecraft.getInstance().font

    fun fontStyle(): Style {
        val choice = fontChoice.value
        val desc = FontDescription.Resource(Identifier.fromNamespaceAndPath(choice.namespace, choice.path))
        return Style.EMPTY.withFont(desc)
    }

    fun styledText(text: String): Component =
        Component.literal(text).withStyle(fontStyle())
}
