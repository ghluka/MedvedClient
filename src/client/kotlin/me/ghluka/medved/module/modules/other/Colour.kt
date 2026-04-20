package me.ghluka.medved.module.modules.other

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.Module

object Colour : Module("Color", "Global accent color for GUI and HUD elements", Category.OTHER) {

    override val isProtected = true
    override val showInModulesList = false
    init { enabled.value = true }

    val bg = color("bg", Color(9, 9, 9), allowAlpha = false)
    val accent = color("accent", Color(255, 0, 0), allowAlpha = false)
}
