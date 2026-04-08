package me.ghluka.medved.module.modules.other

import me.ghluka.medved.module.Module

/**
 * Global settings for how RotationManager interpolates server-side rotations.
 * All modules that call RotationManager.tick() pick these up automatically.
 */
object Rotations : Module("Rotations", "Global aim interpolation settings for all rotation modules", Category.OTHER) {

    override val isProtected = true
    override val showInModulesList = false
    init { enabled.value = true }

    enum class AimStyle {
        LINEAR, QUADRATIC, LOGARITHMIC, EXPONENTIAL, CIRCULAR
    }

    val aimStyle = enum("aim style", AimStyle.QUADRATIC)

    val speed = floatRange("speed", 0.50f to 0.80f, 0.05f, 1.0f)
    val maxCounts = int("max counts", 80, 10, 300)
    val maxSpeedDeg = float("max speed deg", 8f, 1f, 120f)
    val countJitter = float("count jitter", 0.30f, 0.0f, 2.0f)
    val overshootChance = int("overshoot chance %", 0, 0, 100)
    val overshootAmount = floatRange("overshoot amount", 3f to 8f, 0.5f, 30f)
    val microJitter = float("micro jitter", 0.0f, 0.0f, 3.0f)

    fun ease(fraction: Float): Float {
        val f = fraction.coerceIn(0f, 1f)
        return when (aimStyle.value) {
            AimStyle.LINEAR      -> f
            AimStyle.QUADRATIC   -> f * f
            AimStyle.LOGARITHMIC -> (kotlin.math.ln(1.0 + f * Math.E) / kotlin.math.ln(1.0 + Math.E)).toFloat()
            AimStyle.EXPONENTIAL -> ((Math.E.pow(f.toDouble()) - 1.0) / (Math.E - 1.0)).toFloat()
            AimStyle.CIRCULAR    -> kotlin.math.sqrt(1f - (1f - f) * (1f - f))
        }.coerceIn(0f, 1f)
    }
}

private fun Double.pow(exp: Double) = Math.pow(this, exp)
