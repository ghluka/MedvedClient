package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module

object NoPush : Module(
    name = "No Push",
    description = "Prevents other entities from displacing you on collision",
    category = Category.MOVEMENT
)