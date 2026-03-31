package me.ghluka.medved.config.entry

data class Color(val r: Int, val g: Int, val b: Int, val a: Int = 255) {

    val argb: Int get() = (a shl 24) or (r shl 16) or (g shl 8) or b

    fun toHex(): String = "#%02X%02X%02X%02X".format(a, r, g, b)

    companion object {
        fun fromArgb(argb: Int) = Color(
            r = (argb shr 16) and 0xFF,
            g = (argb shr 8) and 0xFF,
            b = argb and 0xFF,
            a = (argb shr 24) and 0xFF
        )

        fun fromHex(hex: String): Color {
            val clean = hex.trimStart('#')
            return when (clean.length) {
                6 -> Color(
                    r = clean.substring(0, 2).toInt(16),
                    g = clean.substring(2, 4).toInt(16),
                    b = clean.substring(4, 6).toInt(16)
                )
                8 -> Color(
                    a = clean.substring(0, 2).toInt(16),
                    r = clean.substring(2, 4).toInt(16),
                    g = clean.substring(4, 6).toInt(16),
                    b = clean.substring(6, 8).toInt(16)
                )
                else -> WHITE
            }
        }

        val WHITE = Color(255, 255, 255)
        val BLACK = Color(0, 0, 0)
        val RED   = Color(255, 0, 0)
        val GREEN = Color(0, 255, 0)
        val BLUE  = Color(0, 0, 255)
    }
}
