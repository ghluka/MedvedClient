package me.ghluka.medved.module.modules.other

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.Module

object ColorModule : Module("Color", "Global accent color for GUI and HUD elements", Category.OTHER) {

    override val isProtected = true
    init { enabled.value = true }

    val accent = color("accent", Color(150, 75, 0), allowAlpha = false)
}
