package me.ghluka.medved.module.modules.combat

import me.ghluka.medved.module.Module

object KeepSprint : Module(
    name = "Keep Sprint",
    description = "Retains sprint momentum when attacking entities",
    category = Category.COMBAT
) {
    val retainFactor = float("retain factor", 1.0f, 0.0f, 1.0f)
    val resetSprint  = boolean("reset sprint", true)

    fun retain() = retainFactor.value
    fun shouldReset() = resetSprint.value

    override fun hudInfo(): String = "${retain().toInt()}"
}