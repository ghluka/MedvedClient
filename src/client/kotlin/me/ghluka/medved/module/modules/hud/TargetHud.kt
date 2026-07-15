package me.ghluka.medved.module.modules.hud

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.gui.ui.HudUiResources
import me.ghluka.medved.gui.ui.MinecraftUiRenderer
import me.ghluka.medved.gui.ui.UiDocument
import me.ghluka.medved.gui.ui.UiRect
import me.ghluka.medved.gui.ui.UiRuntime
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.modules.other.Font
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.player.AbstractClientPlayer
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState
import net.minecraft.client.resources.DefaultPlayerSkin
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.player.Player
import org.joml.Quaternionf
import org.joml.Vector3f
import kotlin.math.roundToInt

object TargetHud : HudModule("Target HUD", "Displays target info and fight prediction when in combat") {

    enum class PositionMode { STATIC, FLOATING }
    enum class Design { GRIZZLY, CAMEL }

    private val positionMode = enum("position", PositionMode.FLOATING)
    private val design       = enum("design", Design.GRIZZLY)
    private val bgColor      = color("bg color", Color(0, 0, 0, 160), allowAlpha = true)
    private val textShadow   = boolean("text shadow", false)

    private const val LINGER_MS = 3000L

    private var target: LivingEntity? = null
    private var displayTarget: LivingEntity? = null
    private var lastHitTime = 0L

    private var smoothX = -1f
    private var smoothY = -1f
    private var floatingSide = 0
    private var renderAbsX = 0f
    private var renderAbsY = 0f
    private var renderScale = 1f
    private var hudAnim = 0f

    override fun onEnabled() {
        target = null
        displayTarget = null
        smoothX = -1f
        smoothY = -1f
        floatingSide = 0
        hudAnim = 0f
    }

    override fun onDisabled() {
        target = null
        displayTarget = null
        hudAnim = 0f
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
                floatingSide = 0
            }
            target = best
            lastHitTime = now
        } else if (now - lastHitTime > LINGER_MS) {
            target = null
        }
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val liveTarget = target
        if (liveTarget != null) displayTarget = liveTarget

        val tgt = displayTarget ?: return
        val targetAnim = if (liveTarget != null) 1f else 0f
        hudAnim += (targetAnim - hudAnim) * if (liveTarget != null) 0.32f else 0.26f
        if (hudAnim <= 0.02f && liveTarget == null) {
            displayTarget = null
            hudAnim = 0f
            return
        }

        val mc  = Minecraft.getInstance()
        val sw  = mc.window.guiScaledWidth
        val sh  = mc.window.guiScaledHeight

        val sc = hudScale.value
        val animCurve = if (liveTarget != null) easeOutBack(hudAnim) else easeInCubic(hudAnim)
        val animScale = 0.55f + 0.45f * animCurve

        when (positionMode.value) {
            PositionMode.STATIC -> {
                val px = (hudX.value * sw).toInt()
                val py = (hudY.value * sh).toInt()
                renderAbsX = px + hudWidth() * sc * (1f - animScale) * 0.5f
                renderAbsY = py + hudHeight() * sc * (1f - animScale) * 0.5f
                renderScale = sc * animScale
                if (design.value == Design.CAMEL) {
                    Font.withRenderScale(renderScale) {
                        renderHudElement(extractor)
                    }
                    return
                }
                extractor.pose().pushMatrix()
                extractor.pose().translate(px + hudWidth() * sc * 0.5f, py + hudHeight() * sc * 0.5f)
                extractor.pose().scale(sc * animScale, sc * animScale)
                extractor.pose().translate(-hudWidth() * 0.5f, -hudHeight() * 0.5f)
                Font.withRenderScale(renderScale) {
                    renderHudElement(extractor)
                }
                extractor.pose().popMatrix()
            }
            PositionMode.FLOATING -> {
                val sp = projectToScreen(tgt, mc, 0.58)
                val w  = hudWidth()
                val h  = hudHeight()
                val swf = sw.toFloat()
                val shf = sh.toFloat()
                val margin = 6f

                if (sp != null) {
                    val scaledW = w * sc
                    val scaledH = h * sc
                    val sideGap = 12f
                    val hitboxPad = tgt.bbWidth.toDouble() * 0.55 + 0.08
                    val rightEdge = projectToScreen(tgt, mc, 0.58, hitboxPad)?.first ?: sp.first
                    val leftEdge = projectToScreen(tgt, mc, 0.58, -hitboxPad)?.first ?: sp.first
                    val rightX = maxOf(rightEdge, leftEdge) + sideGap
                    val leftX = minOf(rightEdge, leftEdge) - scaledW - sideGap
                    val canUseRight = rightX + scaledW <= swf - margin
                    val canUseLeft = leftX >= margin
                    if (floatingSide == 0) {
                        floatingSide = if (sp.first < swf * 0.5f) 1 else -1
                    }
                    if (floatingSide > 0 && !canUseRight && canUseLeft) floatingSide = -1
                    if (floatingSide < 0 && !canUseLeft && canUseRight) floatingSide = 1

                    var tx = when {
                        floatingSide > 0 && canUseRight -> rightX
                        floatingSide < 0 && canUseLeft -> leftX
                        canUseRight -> rightX
                        canUseLeft -> leftX
                        else -> sp.first - scaledW * 0.5f
                    }
                    var ty = sp.second - scaledH * 0.5f
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
                renderAbsX = smoothX + hudWidth() * sc * (1f - animScale) * 0.5f
                renderAbsY = smoothY + hudHeight() * sc * (1f - animScale) * 0.5f
                renderScale = sc * animScale
                if (design.value == Design.CAMEL) {
                    Font.withRenderScale(renderScale) {
                        renderHudElement(extractor)
                    }
                    return
                }
                extractor.pose().pushMatrix()
                extractor.pose().translate(smoothX + hudWidth() * sc * 0.5f, smoothY + hudHeight() * sc * 0.5f)
                extractor.pose().scale(sc * animScale, sc * animScale)
                extractor.pose().translate(-hudWidth() * 0.5f, -hudHeight() * 0.5f)
                Font.withRenderScale(renderScale) {
                    renderHudElement(extractor)
                }
                extractor.pose().popMatrix()
            }
        }
    }

    private fun projectToScreen(
        entity: LivingEntity,
        mc: Minecraft,
        heightFactor: Double,
        cameraRightOffset: Double = 0.0,
    ): Pair<Float, Float>? {
        val camera = mc.gameRenderer.mainCamera()
        val camPos = camera.position()
        val yawRad = Math.toRadians(camera.yRot().toDouble())
        val rightX = -Math.cos(yawRad) * cameraRightOffset
        val rightZ = -Math.sin(yawRad) * cameraRightOffset

        val wx = entity.x + rightX - camPos.x
        val wy = entity.y + entity.bbHeight.toDouble() * heightFactor - camPos.y
        val wz = entity.z + rightZ - camPos.z

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
        Design.CAMEL    -> 122
        Design.GRIZZLY -> 150
    }

    override fun hudHeight(): Int {
        val lh = Font.getFont().lineHeight
        return when (design.value) {
            Design.CAMEL    -> 58
            Design.GRIZZLY -> {
                var rows = 3  // HP + dist always
                if (displayTarget?.absorptionAmount ?: target?.absorptionAmount ?: 0f > 0f) rows++
                (2 * lh + 11) + (rows - 1) * (lh + 1)
            }
        }
    }

    override fun renderHudElement(g: GuiGraphicsExtractor) {
        val tgt  = displayTarget ?: target ?: return
        val document = buildHudDocument(tgt)
        val runtime = UiRuntime(MinecraftUiRenderer(g)) { node ->
            when (node.type) {
                "target-face" -> {
                    renderPlayerFace(
                        g,
                        tgt,
                        node.bounds.x.roundToInt(),
                        node.bounds.y.roundToInt(),
                        node.bounds.width.roundToInt(),
                        damagePulse(tgt),
                    )
                    true
                }
                "target-entity" -> {
                    g.pose().popMatrix()
                    renderCamelAvatar(g, tgt, node.bounds)
                    g.pose().pushMatrix()
                    g.pose().translate(renderAbsX, renderAbsY)
                    if (renderScale != 1f) g.pose().scale(renderScale, renderScale)
                    true
                }
                else -> false
            }
        }

        if (design.value == Design.CAMEL) {
            g.pose().pushMatrix()
            g.pose().translate(renderAbsX, renderAbsY)
            if (renderScale != 1f) g.pose().scale(renderScale, renderScale)
        }
        runtime.layout(document, UiRect(0f, 0f, hudWidth().toFloat(), hudHeight().toFloat()))
        runtime.render(document, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY)
        if (design.value == Design.CAMEL) g.pose().popMatrix()
    }

    private fun renderCamelAvatar(g: GuiGraphicsExtractor, tgt: LivingEntity, bounds: UiRect) {
        val sx0 = (renderAbsX + bounds.x * renderScale).roundToInt()
        val sy0 = (renderAbsY + bounds.y * renderScale).roundToInt()
        val sx1 = (renderAbsX + bounds.right * renderScale).roundToInt()
        val sy1 = (renderAbsY + bounds.bottom * renderScale).roundToInt()
        val state = Minecraft.getInstance().entityRenderDispatcher.extractEntity(tgt, 1f)
        state.shadowPieces.clear()
        state.outlineColor = 0
        state.nameTag = null
        state.scoreText = null
        state.displayFireAnimation = false

        if (state is LivingEntityRenderState) {
            state.bodyRot = 180f
            state.yRot = 0f
            state.xRot = 0f
            if (state.scale != 0f) {
                state.boundingBoxWidth /= state.scale
                state.boundingBoxHeight /= state.scale
                state.scale = 1f
            }
        }

        g.entity(
            state,
            26f * renderScale,
            Vector3f(0f, state.boundingBoxHeight / 2f, 0f),
            Quaternionf().rotateZ(Math.PI.toFloat()),
            Quaternionf(),
            sx0,
            sy0,
            sx1,
            sy1,
        )
    }

    private fun buildHudDocument(tgt: LivingEntity): UiDocument {
        val templates = HudUiResources.templates()
        val font = Font.getFont()
        val bg = bgColor.liveColor(bgColor.value).argb
        val text = readableTextColor(bg)
        val muted = mutedTextColor(bg)
        val barBack = barBackColor(bg)
        val shadow = textShadow.value.toString()
        val hpFraction = (tgt.health / tgt.maxHealth).coerceIn(0f, 1f)

        if (design.value == Design.CAMEL) {
            val nameScale = 1.33f
            val name = fitName(tgt.name.string, ((122f - 36f - 5f) / nameScale).toInt(), font)
            return UiDocument(templates.instantiate(
                "target-hud-camel",
                mapOf(
                    "hud.background" to bg.xmlColor(),
                    "hud.text" to text.xmlColor(),
                    "hud.barBack" to barBack.xmlColor(),
                    "hud.shadow" to shadow,
                    "target.name" to name,
                    "hp.fillW" to (77f * hpFraction).toInt().toString(),
                    "hp.whole" to kotlin.math.ceil(tgt.health.toDouble()).toInt().coerceAtLeast(0).toString(),
                ),
            ))
        }

        val h = hudHeight()
        val rowStep = font.lineHeight + 1
        val mc = Minecraft.getInstance()
        val player = mc.player
        val hpValStr  = "%.1f".format(tgt.health)
        val hpValComp = Font.styledText(hpValStr)
        val hpValW    = font.width(hpValComp) + 4
        val hpLabelComp = Font.styledText("HP")
        val hpLabelW = font.width(hpLabelComp)
        val hpBarX = 54 + hpLabelW + 3
        val statRows = 2 +
                (if (tgt.absorptionAmount > 0f) 1 else 0) +
                (if (player != null) 1 else 0)
        var row = ((h - statRows * rowStep) / 2).coerceAtLeast(5 + font.lineHeight + 2)
        val hpBarW = (140 - hpBarX - hpValW).coerceAtLeast(0)
        val hpColor = hpColor(tgt)
        val faceSize = 34
        val faceY = ((h - faceSize) / 2).coerceAtLeast(5)
        val faceDrawSize = (faceSize * (1f + 0.1f * damagePulse(tgt))).roundToInt()
        val values = mutableMapOf(
            "hud.height" to h.toString(),
            "hud.background" to bg.xmlColor(),
            "hud.text" to text.xmlColor(),
            "hud.muted" to muted.xmlColor(),
            "hud.barBack" to barBack.xmlColor(),
            "hud.shadow" to shadow,
            "target.name" to tgt.name.string,
            "target.hurt" to damagePulse(tgt).toString(),
            "face.x" to (10 - (faceDrawSize - faceSize) / 2).toString(),
            "face.y" to (faceY - (faceDrawSize - faceSize) / 2).toString(),
            "face.size" to faceDrawSize.toString(),
            "hp.y" to row.toString(),
            "hp.labelW" to hpLabelW.toString(),
            "hp.barX" to hpBarX.toString(),
            "hp.barY" to (row + 1).toString(),
            "hp.barW" to hpBarW.toString(),
            "hp.fillW" to (hpBarW * hpFraction).toInt().toString(),
            "hp.value" to hpValStr,
            "hp.valueX" to (143 - hpValW).toString(),
            "hp.valueW" to hpValW.toString(),
            "hp.color" to hpColor.xmlColor(),
        )
        row += rowStep

        val absorptionNodes = if (tgt.absorptionAmount > 0f) {
            val absLabelW = font.width(Font.styledText("Abs"))
            val absBarX = 54 + absLabelW + 3
            val absBarW = (140 - absBarX).coerceAtLeast(0)
            val absValues = values + mapOf(
                "abs.y" to row.toString(),
                "abs.labelW" to absLabelW.toString(),
                "abs.barX" to absBarX.toString(),
                "abs.barY" to (row + 1).toString(),
                "abs.barW" to absBarW.toString(),
                "abs.fillW" to (absBarW * (tgt.absorptionAmount / 20f).coerceIn(0f, 1f)).toInt().toString(),
            )
            row += rowStep
            listOf(templates.instantiate("target-hud-absorption", absValues))
        } else {
            emptyList()
        }

        val distanceText = player?.let { "Dist: ${"%.1fm".format(it.distanceTo(tgt))}" }.orEmpty()
        values["distance.y"] = row.toString()
        values["distance.text"] = distanceText
        if (player != null) row += rowStep

        val chance = player?.let { calcWinChance(it, tgt) } ?: -1f
        values["win.y"] = row.toString()
        values["win.text"] = if (player == null) "" else if (chance >= 0) "Win: ${"%.0f".format(chance)}%" else "Win: ?"
        values["win.color"] = when {
            chance < 0 -> muted
            chance >= 60 -> 0xFF55FF55.toInt()
            chance >= 40 -> 0xFFFFAA00.toInt()
            else -> 0xFFFF5555.toInt()
        }.xmlColor()

        return UiDocument(templates.instantiate(
            "target-hud-grizzly",
            values,
            mapOf("absorption" to absorptionNodes),
        ))
    }

    private fun Int.xmlColor(): String = "#%08X".format(this)

    private fun renderPlayerFace(
        g: GuiGraphicsExtractor,
        tgt: LivingEntity,
        x: Int,
        y: Int,
        size: Int,
        hurtPulse: Float,
    ) {
        val texture = when (tgt) {
            is AbstractClientPlayer -> tgt.skin.body().texturePath()
            is Player -> DefaultPlayerSkin.get(tgt.gameProfile).body().texturePath()
            else -> DefaultPlayerSkin.getDefaultTexture()
        }
        renderRoundedFaceLayer(g, texture, x, y, size, 5, 8f, 8f)
        renderRoundedFaceLayer(g, texture, x, y, size, 5, 40f, 8f)
        if (hurtPulse > 0f) {
            val alpha = (75 * hurtPulse).roundToInt().coerceIn(0, 90)
            renderRoundedFill(g, x, y, size, 5, (alpha shl 24) or 0x00FF3030)
        }
    }

    private fun renderRoundedFaceLayer(
        g: GuiGraphicsExtractor,
        texture: net.minecraft.resources.Identifier,
        x: Int,
        y: Int,
        size: Int,
        radius: Int,
        u: Float,
        v: Float,
    ) {
        for (row in 0 until size) {
            val inset = roundedRowInset(row, size, radius)
            g.enableScissor(x + inset, y + row, x + size - inset, y + row + 1)
            g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, size, size, 8, 8, 64, 64)
            g.disableScissor()
            renderRoundedFaceEdgePixel(g, texture, x, y, size, radius, row, u, v)
        }
    }

    private fun roundedRowInset(row: Int, size: Int, radius: Int): Int {
        val edgeDistance = when {
            row < radius -> radius - row - 0.5
            row >= size - radius -> row - (size - radius) + 0.5
            else -> return 0
        }
        val keepWidth = Math.sqrt(radius * radius - edgeDistance * edgeDistance)
        return Math.ceil(radius - keepWidth).toInt().coerceIn(0, radius)
    }

    private fun roundedEdgeCoverage(row: Int, size: Int, radius: Int): Pair<Int, Float>? {
        val edgeDistance = when {
            row < radius -> radius - row - 0.5
            row >= size - radius -> row - (size - radius) + 0.5
            else -> return null
        }
        val boundary = radius - Math.sqrt(radius * radius - edgeDistance * edgeDistance)
        val edgePixel = kotlin.math.floor(boundary).toInt().coerceIn(0, radius - 1)
        val skinCoverage = (edgePixel + 1 - boundary).toFloat().coerceIn(0f, 1f)
        return edgePixel to skinCoverage
    }

    private fun renderRoundedFaceEdgePixel(
        g: GuiGraphicsExtractor,
        texture: net.minecraft.resources.Identifier,
        x: Int,
        y: Int,
        size: Int,
        radius: Int,
        row: Int,
        u: Float,
        v: Float,
    ) {
        val (edgePixel, coverage) = roundedEdgeCoverage(row, size, radius) ?: return
        val alpha = (255 * coverage).roundToInt().coerceIn(0, 255)
        if (alpha <= 0 || alpha >= 255) return
        val tint = (alpha shl 24) or 0x00FFFFFF
        g.enableScissor(x + edgePixel, y + row, x + edgePixel + 1, y + row + 1)
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, size, size, 8, 8, 64, 64, tint)
        g.disableScissor()
        g.enableScissor(x + size - edgePixel - 1, y + row, x + size - edgePixel, y + row + 1)
        g.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, size, size, 8, 8, 64, 64, tint)
        g.disableScissor()
    }

    private fun renderRoundedFill(g: GuiGraphicsExtractor, x: Int, y: Int, size: Int, radius: Int, color: Int) {
        for (row in 0 until size) {
            val inset = roundedRowInset(row, size, radius)
            g.fill(x + inset, y + row, x + size - inset, y + row + 1, color)
        }
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

    private fun damagePulse(entity: LivingEntity): Float {
        if (entity.hurtTime <= 0) return 0f
        return (entity.hurtTime / entity.hurtDuration.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    }

    private fun easeOutBack(value: Float): Float {
        val t = value.coerceIn(0f, 1f) - 1f
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * t * t * t + c1 * t * t
    }

    private fun easeInCubic(value: Float): Float {
        val t = value.coerceIn(0f, 1f)
        return t * t * t
    }

    private fun readableTextColor(argb: Int): Int =
        if (isLightColor(argb)) 0xFF171717.toInt() else 0xFFD7D7E4.toInt()

    private fun mutedTextColor(argb: Int): Int =
        if (isLightColor(argb)) 0xFF565656.toInt() else 0xFFAAAAAA.toInt()

    private fun barBackColor(argb: Int): Int =
        if (isLightColor(argb)) 0xFF1B1B1B.toInt() else 0xFF202020.toInt()

    private fun isLightColor(argb: Int): Boolean {
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        return (0.2126 * r + 0.7152 * g + 0.0722 * b) > 150.0
    }

    init {
        hudX.value = 0.5f
        hudX.defaultValue = .5f
        hudY.value = 0.7f
        hudY.defaultValue = .7f
    }
}
