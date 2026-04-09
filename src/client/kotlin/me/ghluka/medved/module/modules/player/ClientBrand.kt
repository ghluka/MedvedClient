package me.ghluka.medved.module.modules.player

import me.ghluka.medved.module.Module

object ClientBrand : Module(
    "Client Brand",
    "Spoofs the client brand sent to the server on join",
    Category.PLAYER
) {

    enum class Brand(val value: String) {
        VANILLA("vanilla"),
        LUNAR("lunarclient"),
        FORGE("forge"),
        FABRIC("fabric"),
        OPTIFINE("optifine"),
        FEATHER("feather"),
        CUSTOM("custom")
    }

    private val brand  = enum("brand", Brand.VANILLA)
    private val custom = string("custom brand", "vanilla").also {
        it.visibleWhen = { brand.value == Brand.CUSTOM }
    }

    @JvmField
    var isResending = false

    fun getCurrentBrand(): String = when (brand.value) {
        Brand.CUSTOM -> custom.value.ifBlank { "vanilla" }
        else         -> brand.value.value
    }

    override fun hudInfo(): String = getCurrentBrand()
}
