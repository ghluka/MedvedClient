package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.util.CORNERS_LEFT
import me.ghluka.medved.util.CORNERS_TOP
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.util.Mth
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player

object TargetHud : HudModule("Target HUD", "Displays target info and fight prediction when in combat") {

    enum class PositionMode { STATIC, FLOATING }
    enum class Design { COMPACT, DETAILED, MINIMAL }

    private val positionMode = enum("position", PositionMode.FLOATING)
    private val design       = enum("design", Design.DETAILED)
    private val showWinChance = boolean("win chance", true)
    private val bgColor      = color("bg color", Color(0, 0, 0, 160), allowAlpha = true)
    private val textShadow   = boolean("text shadow", false)

    private const val LINGER_MS = 3000L

    private var target: LivingEntity? = null
    private var lastHitTime = 0L

    private var smoothX = -1f
    private var smoothY = -1f
    /** Cached pixel width for the MINIMAL design (updated each render frame). */
    private var minimalWidth = 80

    override fun onEnabled() {
        target = null
        smoothX = -1f
        smoothY = -1f
    }

    override fun onDisabled() {
        target = null
    }

    override fun onTick(client: Minecraft) {
        val player = client.player ?: return
        val level  = client.level  ?: return
        val now = System.currentTimeMillis()

        val cur = target
        if (cur != null && (cur.isDeadOrDying || cur.isRemoved || player.distanceTo(cur) > 20f)) {
            target = null
        }

        val candidates = level.players()
            .filter { it !== player && !it.isDeadOrDying && player.distanceTo(it) <= 20f }
        if (candidates.isEmpty()) {
            target = null
            return
        }

        val lookVec = player.lookAngle
        val best = candidates.maxByOrNull { e ->
            val dx = e.x - player.x
            val dy = (e.y + e.bbHeight / 2) - player.eyeY
            val dz = e.z - player.z
            val len = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001)
            (lookVec.x * dx + lookVec.y * dy + lookVec.z * dz) / len
        }!!

        val bestDot = run {
            val dx = best.x - player.x
            val dy = (best.y + best.bbHeight / 2) - player.eyeY
            val dz = best.z - player.z
            val len = Math.sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.001)
            (lookVec.x * dx + lookVec.y * dy + lookVec.z * dz) / len
        }
        val inView = bestDot > 0.5 || player.distanceTo(best) <= 4f

        if (inView) {
            if (target !== best) {
                smoothX = -1f
                smoothY = -1f
            }
            target = best
            lastHitTime = now
        } else if (now - lastHitTime > LINGER_MS) {
            target = null
        }
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val tgt = target ?: return
        val mc  = Minecraft.getInstance()
        val sw  = mc.window.guiScaledWidth
        val sh  = mc.window.guiScaledHeight

        val sc = hudScale.value

        when (positionMode.value) {
            PositionMode.STATIC -> {
                val px = (hudX.value * sw).toInt()
                val py = (hudY.value * sh).toInt()
                extractor.pose().pushMatrix()
                extractor.pose().translate(px.toFloat(), py.toFloat())
                if (sc != 1.0f) extractor.pose().scale(sc, sc)
                renderHudElement(extractor)
                extractor.pose().popMatrix()
            }
            PositionMode.FLOATING -> {
                val sp = projectToScreen(tgt, mc)
                val w  = hudWidth()
                val h  = hudHeight()
                val swf = sw.toFloat()
                val shf = sh.toFloat()
                val margin = 6f

                if (sp != null) {
                    var tx = sp.first  - w * sc * 0.5f
                    var ty = sp.second - h * sc - 6f
                    tx = tx.coerceIn(margin, swf - w * sc - margin)
                    ty = ty.coerceIn(margin, shf - h * sc - margin)

                    if (smoothX < 0f) {
                        smoothX = tx; smoothY = ty
                    } else {
                        smoothX += (tx - smoothX) * 0.2f
                        smoothY += (ty - smoothY) * 0.2f
                    }
                }

                if (smoothX < 0f) return
                extractor.pose().pushMatrix()
                extractor.pose().translate(smoothX, smoothY)
                if (sc != 1.0f) extractor.pose().scale(sc, sc)
                renderHudElement(extractor)
                extractor.pose().popMatrix()
            }
        }
    }

    private fun projectToScreen(entity: LivingEntity, mc: Minecraft): Pair<Float, Float>? {
        val camera = mc.gameRenderer.mainCamera
        val camPos = camera.position()

        val wx = entity.x - camPos.x
        val wy = entity.y + entity.bbHeight.toDouble() + 0.2 - camPos.y
        val wz = entity.z - camPos.z

        val yawRad   = Math.toRadians(camera.yRot().toDouble())
        val pitchRad = Math.toRadians(camera.xRot().toDouble())
        val sinYaw = Math.sin(yawRad);   val cosYaw = Math.cos(yawRad)
        val sinPit = Math.sin(pitchRad); val cosPit = Math.cos(pitchRad)

        val viewX = (-wx * cosYaw               - wz * sinYaw).toFloat()
        val viewZ = (-wx * sinYaw * cosPit - wy * sinPit + wz * cosYaw * cosPit).toFloat()
        val viewY = ( wx * sinYaw * sinPit - wy * cosPit - wz * cosYaw * sinPit).toFloat()

        if (viewZ <= 0.1f) return null  // behind camera

        val swf = mc.window.guiScaledWidth.toFloat()
        val shf = mc.window.guiScaledHeight.toFloat()
        val fovRad = Math.toRadians(mc.options.fov().get().toDouble())
        val f = (swf * 0.5f / Math.tan(fovRad / 2.0)).toFloat()

        return (swf / 2f + viewX / viewZ * f) to (shf / 2f + viewY / viewZ * f)
    }

    override fun hudWidth(): Int = when (design.value) {
        Design.MINIMAL  -> minimalWidth
        Design.COMPACT  -> 120
        Design.DETAILED -> 150
    }

    override fun hudHeight(): Int = when (design.value) {
        Design.MINIMAL  -> 20
        Design.COMPACT  -> 36
        Design.DETAILED -> 52
    }

    override fun renderHudElement(g: GuiGraphicsExtractor) {
        val tgt  = target ?: return
        val mc   = Minecraft.getInstance()
        val self = mc.player ?: return
        val font = Font.getFont()
        val accent = Colour.accent.value.argb

        val w = hudWidth()
        val h = hudHeight()

        when (design.value) {
            Design.MINIMAL  -> renderMinimal(g, tgt, self, font, w, h, accent)
            Design.COMPACT  -> renderCompact(g, tgt, self, font, w, h, accent)
            Design.DETAILED -> renderDetailed(g, tgt, self, font, w, h, accent)
        }
    }

    private fun renderMinimal(
        g: GuiGraphicsExtractor, tgt: LivingEntity, self: Player,
        font: net.minecraft.client.gui.Font, w: Int, h: Int, accent: Int
    ) {
        val nameComp = Font.styledText(tgt.name.string)
        // Update cached width so next frame's layout is correct
        minimalWidth = (font.width(nameComp) + 14).coerceAtLeast(60).coerceAtMost(150)

        val bgC = bgColor.value.argb
        g.roundedFill(0, 0, w, h, 3, bgC)
        g.roundedFill(0, 0, 2, h, 3, accent, CORNERS_LEFT)

        textWithShadow(g, font, nameComp, 5, 3, 0xFFD7D7E4.toInt())
        renderBar(g, 5, 13, w - 10, 4, tgt.health / tgt.maxHealth, hpColor(tgt), 0xFF202020.toInt())
    }

    private fun renderCompact(
        g: GuiGraphicsExtractor, tgt: LivingEntity, self: Player,
        font: net.minecraft.client.gui.Font, w: Int, h: Int, accent: Int
    ) {
        val bgC = bgColor.value.argb
        g.roundedFill(0, 0, w, h, 3, bgC)
        g.roundedFill(0, 0, 2, h, 3, accent, CORNERS_LEFT)

        val hpStr  = "%.1f / %.1f".format(tgt.health, tgt.maxHealth)
        val hpComp = Font.styledText(hpStr)
        val nameAvailW = w - 5 - font.width(hpComp) - 10
        val name = fitName(tgt.name.string, nameAvailW, font)

        textWithShadow(g, font, Font.styledText(name), 5, 3, 0xFFD7D7E4.toInt())
        textWithShadow(g, font, hpComp, w - 4 - font.width(hpComp), 3, hpColor(tgt))

        renderBar(g, 5, 14, w - 10, 4, tgt.health / tgt.maxHealth, hpColor(tgt), 0xFF202020.toInt())

        if (showWinChance.value) {
            val chance  = calcWinChance(self, tgt)
            val chStr   = if (chance >= 0) "Win: ${"%.0f".format(chance)}%" else "Win: ?"
            val chColor = when {
                chance < 0   -> 0xFFAAAAAA.toInt()
                chance >= 60 -> 0xFF55FF55.toInt()
                chance >= 40 -> 0xFFFFAA00.toInt()
                else         -> 0xFFFF5555.toInt()
            }
            textWithShadow(g, font, Font.styledText(chStr), 5, 23, chColor)
        }
    }

    private fun renderDetailed(
        g: GuiGraphicsExtractor, tgt: LivingEntity, self: Player,
        font: net.minecraft.client.gui.Font, w: Int, h: Int, accent: Int
    ) {
        val bgC = bgColor.value.argb
        g.roundedFill(0, 0, w, h, 3, bgC)
        g.roundedFill(0, 0, w, 14, 3, darken(bgC, 0.6f), CORNERS_TOP)
        g.roundedFill(0, 0, 2, h, 3, accent, CORNERS_LEFT)

        textWithShadow(g, font, Font.styledText(tgt.name.string), 5, 3, 0xFFD7D7E4.toInt())

        val hpRow = 17
        val hpValStr  = "%.1f".format(tgt.health)
        val hpValComp = Font.styledText(hpValStr)
        val hpValW    = font.width(hpValComp) + 4
        val hpLabelComp = Font.styledText("HP")
        val hpBarX = 5 + font.width(hpLabelComp) + 3
        textWithShadow(g, font, hpLabelComp, 5, hpRow, 0xFFAAAAAA.toInt())
        renderBar(g, hpBarX, hpRow + 1, w - hpBarX - hpValW - 5, 5, tgt.health / tgt.maxHealth, hpColor(tgt), 0xFF202020.toInt())
        textWithShadow(g, font, hpValComp, w - hpValW, hpRow, hpColor(tgt))

        var row = hpRow + 10

        val abs = tgt.absorptionAmount
        if (abs > 0f) {
            val absLabelComp = Font.styledText("Abs")
            val absBarX = 5 + font.width(absLabelComp) + 3
            textWithShadow(g, font, absLabelComp, 5, row, 0xFFFFDD44.toInt())
            renderBar(g, absBarX, row + 1, w - absBarX - 5, 4, (abs / 20f).coerceIn(0f, 1f), 0xFFFFDD44.toInt(), 0xFF202020.toInt())
            row += 9
        }

        val mc = Minecraft.getInstance()
        val player = mc.player
        if (player != null) {
            val dist = "%.1fm".format(player.distanceTo(tgt))
            textWithShadow(g, font, Font.styledText("Dist: $dist"), 5, row, 0xFFAAAAAA.toInt())
            row += 9
        }

        if (showWinChance.value && player != null) {
            val chance  = calcWinChance(player, tgt)
            val chStr   = if (chance >= 0) "Win: ${"%.0f".format(chance)}%" else "Win: ?"
            val chColor = when {
                chance < 0   -> 0xFFAAAAAA.toInt()
                chance >= 60 -> 0xFF55FF55.toInt()
                chance >= 40 -> 0xFFFFAA00.toInt()
                else         -> 0xFFFF5555.toInt()
            }
            textWithShadow(g, font, Font.styledText(chStr), 5, row, chColor)
        }
    }

    private fun renderBar(
        g: GuiGraphicsExtractor,
        x: Int, y: Int, w: Int, h: Int,
        fraction: Float,
        filledColor: Int,
        emptyColor: Int
    ) {
        val filled = (w * fraction.coerceIn(0f, 1f)).toInt()
        g.fill(x, y, x + w, y + h, emptyColor)
        if (filled > 0) g.fill(x, y, x + filled, y + h, filledColor)
    }

    private fun textWithShadow(
        g: GuiGraphicsExtractor,
        font: net.minecraft.client.gui.Font,
        comp: net.minecraft.network.chat.Component,
        x: Int, y: Int, color: Int
    ) {
        if (textShadow.value) g.text(font, comp, x + 1, y + 1, (color and 0x00FFFFFF) or (0xA0 shl 24))
        g.text(font, comp, x, y, color)
    }

    private fun hpColor(entity: LivingEntity): Int {
        val pct = entity.health / entity.maxHealth
        return when {
            pct > 0.6f -> 0xFF55FF55.toInt()
            pct > 0.3f -> 0xFFFFAA00.toInt()
            else       -> 0xFFFF5555.toInt()
        }
    }

    /** Truncates [name] by pixel width so it fits within [availW] using the given [font]. */
    private fun fitName(name: String, availW: Int, font: net.minecraft.client.gui.Font): String {
        if (availW <= 0 || font.width(Font.styledText(name)) <= availW) return name
        var s = name
        while (s.isNotEmpty() && font.width(Font.styledText("$s\u2026")) > availW) s = s.dropLast(1)
        return if (s.length < name.length) "$s\u2026" else name
    }

    private fun calcWinChance(self: Player, enemy: LivingEntity): Float {
        if (self.maxHealth <= 0f || enemy.maxHealth <= 0f) return -1f

        val selfDmg  = weaponDamage(self)
        val enemyDmg = weaponDamage(enemy)
        val selfEhp  = effectiveHp(self)
        val enemyEhp = effectiveHp(enemy)

        if (selfDmg <= 0f) return 0f

        val hitsToKillEnemy = kotlin.math.ceil((enemyEhp / selfDmg).toDouble()).toLong().coerceAtLeast(1L)
        val hitsToKillSelf  = if (enemyDmg > 0f)
            kotlin.math.ceil((selfEhp / enemyDmg).toDouble()).toLong().coerceAtLeast(1L)
        else Long.MAX_VALUE / 2

        val total = (hitsToKillEnemy + hitsToKillSelf).toFloat()
        return (hitsToKillSelf.toFloat() / total * 100f).coerceIn(0f, 100f)
    }

    private fun weaponDamage(entity: LivingEntity): Float {
        val stack = entity.mainHandItem
        if (!stack.isEmpty) {
            val mods = stack.get(net.minecraft.core.component.DataComponents.ATTRIBUTE_MODIFIERS)
            if (mods != null) {
                var bonus = 0.0
                mods.forEach(net.minecraft.world.entity.EquipmentSlot.MAINHAND) { attr, modifier ->
                    if (attr == net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE &&
                        modifier.operation() == net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE) {
                        bonus += modifier.amount()
                    }
                }
                if (bonus > 0.0) return (1f + bonus.toFloat())
            }
        }
        return entity.attributes.getValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
            .toFloat().coerceAtLeast(1f)
    }

    private fun effectiveHp(entity: LivingEntity): Float {
        val armorReduction = 1f - (entity.armorValue * 0.04f).coerceIn(0f, 0.8f)
        return (entity.health + entity.absorptionAmount) / armorReduction
    }

    private fun darken(argb: Int, factor: Float): Int {
        val a = (argb ushr 24) and 0xFF
        val r = ((argb ushr 16) and 0xFF) * factor
        val g = ((argb ushr 8)  and 0xFF) * factor
        val b = (argb            and 0xFF) * factor
        return (a shl 24) or (r.toInt() shl 16) or (g.toInt() shl 8) or b.toInt()
    }

    init {
        hudX.value = 0.5f
        hudY.value = 0.7f
    }
}
