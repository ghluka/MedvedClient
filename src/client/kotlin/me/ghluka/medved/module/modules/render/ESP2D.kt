package me.ghluka.medved.module.modules.render

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.Text
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.phys.AABB
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ESP2D : Module(
    name = "2D ESP",
    description = "Draws customizable screen-space player boxes and stats",
    category = Category.RENDER
) {
    enum class BoxStyle { FULL, CORNERS }
    enum class BorderMode { CUSTOM, HEALTH, TEAM }
    enum class HealthSide { LEFT, RIGHT }
    enum class StatSide { LEFT, RIGHT, BELOW }

    private val maxDistance = double("max distance", 64.0, 8.0, 256.0)
    private val ignoreTargetFilter = boolean("ignore target filter", false)
    private val boxStyle = enum("box style", BoxStyle.FULL)
    private val borderMode = enum("border mode", BorderMode.CUSTOM)
    private val statSide = enum("stat side", StatSide.RIGHT)

    private val showBox = boolean("box", true)
    private val showHealthBar = boolean("health bar", true)
    private val healthSide = enum("health side", HealthSide.LEFT).also {
        it.visibleWhen = { showHealthBar.value }
    }
    private val showHealthText = boolean("health text", true)
    private val showAbsorption = boolean("absorption", true)
    private val showDistance = boolean("distance", true)
    private val showArmor = boolean("armor", true)
    private val showHeldItem = boolean("held item", true)
    private val showPing = boolean("ping", true)
    private val showEquipment = boolean("equipment icons", false)

    private val borderColor = color("border color", Color(255, 255, 255, 230), allowAlpha = true)
    private val infillColor = color("infill color", Color(0, 0, 0, 45), allowAlpha = true)
    private val textColor = color("text color", Color(235, 235, 245, 255), allowAlpha = true)
    private val healthBgColor = color("health bg", Color(0, 0, 0, 170), allowAlpha = true).also {
        it.visibleWhen = { showHealthBar.value }
    }

    private val lineWidth = int("line width", 1, 1, 4)
    private val boxPadding = int("box padding", 2, 0, 8)
    private val healthBarWidth = int("health width", 3, 2, 8).also {
        it.visibleWhen = { showHealthBar.value }
    }
    private val textScale = double("text scale", 1.0, 0.65, 1.5)
    private val textShadow = boolean("text shadow", true)

    private var renderTextScale = 1f

    init {
        borderColor.pickerMode = ColorEntry.PickerMode.THEME
    }

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val viewer = mc.player ?: return
        val level = mc.level ?: return
        val camera = mc.gameRenderer.mainCamera()
        val projection = Projection(
            camera.position(),
            camera.getViewRotationProjectionMatrix(Matrix4f()),
            mc.window.guiScaledWidth.toFloat(),
            mc.window.guiScaledHeight.toFloat(),
        )
        val partialTick = mc.deltaTracker.getGameTimeDeltaPartialTick(true)

        val entries = level.players()
            .asSequence()
            .filter { it !== viewer && !it.isRemoved && !it.isDeadOrDying }
            .filter { viewer.distanceTo(it) <= maxDistance.value }
            .filter { shouldRenderTarget(viewer, it) }
            .mapNotNull { target ->
                val bounds = screenBoundsFor(target, partialTick, projection) ?: return@mapNotNull null
                EspEntry(target, bounds, viewer.distanceTo(target))
            }
            .sortedByDescending { it.distance }
            .toList()

        for (entry in entries) {
            drawEntry(extractor, viewer, entry)
        }
    }

    private fun drawEntry(g: GuiGraphicsExtractor, viewer: Player, entry: EspEntry) {
        val target = entry.target
        val padded = entry.bounds.padded(boxPadding.value.toFloat())
        val x = floor(padded.left).toInt()
        val y = floor(padded.top).toInt()
        val w = ceil(padded.width).toInt().coerceAtLeast(2)
        val h = ceil(padded.height).toInt().coerceAtLeast(4)
        if (x + w < 0 || y + h < 0) return

        val border = resolveBorderColor(target)
        val infill = infillColor.liveColor(infillColor.value).argb
        if ((infill ushr 24) != 0) {
            g.fill(x, y, x + w, y + h, infill)
        }

        if (showBox.value) {
            when (boxStyle.value) {
                BoxStyle.FULL -> drawOutline(g, x, y, w, h, lineWidth.value, border)
                BoxStyle.CORNERS -> drawCornerOutline(g, x, y, w, h, lineWidth.value, border)
            }
        }

        if (showHealthBar.value) {
            drawHealthBar(g, target, x, y, w, h)
        }

        val previousTextScale = renderTextScale
        renderTextScale = textScale.value.toFloat()
        try {
            Font.withRenderScale(renderTextScale) {
                val stats = statsFor(target, entry.distance)
                if (stats.isNotEmpty()) {
                    drawStats(g, stats, padded)
                }
            }
        } finally {
            renderTextScale = previousTextScale
        }

        if (showEquipment.value) {
            drawEquipment(g, target, x, y + h + 4, w)
        }
    }

    private fun shouldRenderTarget(viewer: Player, target: Player): Boolean =
        ignoreTargetFilter.value || TargetFilter.isValidTarget(viewer, target)

    private fun drawStats(g: GuiGraphicsExtractor, stats: List<StatLine>, box: ScreenBox) {
        val font = Font.getFont()
        val scale = renderTextScale
        val lineH = lineHeight()
        val gap = 1
        val textW = stats.maxOf { (font.width(Font.styledText(it.text)) * scale).roundToInt() }
        val totalH = stats.size * lineH + (stats.size - 1) * gap

        val x = when (statSide.value) {
            StatSide.LEFT -> (box.left - textW - 5f - sideHealthOffset(StatSide.LEFT)).roundToInt()
            StatSide.RIGHT -> (box.right + 5f + sideHealthOffset(StatSide.RIGHT)).roundToInt()
            StatSide.BELOW -> (box.left + (box.width - textW) * 0.5f).roundToInt()
        }
        var y = when (statSide.value) {
            StatSide.LEFT, StatSide.RIGHT -> box.top.roundToInt()
            StatSide.BELOW -> (box.bottom + 4f).roundToInt()
        }
        if (statSide.value != StatSide.BELOW) {
            y = y.coerceAtMost((box.bottom - totalH).roundToInt().coerceAtLeast(0))
        }

        for (line in stats) {
            drawText(g, line.text, x, y, line.color)
            y += lineH + gap
        }
    }

    private fun drawHealthBar(g: GuiGraphicsExtractor, target: Player, x: Int, y: Int, boxW: Int, h: Int) {
        val barW = healthBarWidth.value
        val gap = 2
        val barX = when (healthSide.value) {
            HealthSide.LEFT -> x - gap - barW
            HealthSide.RIGHT -> x + boxW + gap
        }
        val bg = healthBgColor.liveColor(healthBgColor.value).argb
        g.fill(barX, y, barX + barW, y + h, bg)

        val healthRatio = (target.health / target.maxHealth).coerceIn(0f, 1f)
        val filled = (h * healthRatio).roundToInt().coerceIn(0, h)
        if (filled > 0) {
            g.fill(barX, y + h - filled, barX + barW, y + h, healthColor(target))
        }

        if (showAbsorption.value && target.absorptionAmount > 0f) {
            val absorptionRatio = (target.absorptionAmount / target.maxHealth).coerceIn(0f, 1f)
            val absorption = (h * absorptionRatio).roundToInt().coerceIn(1, h)
            g.fill(barX, y, barX + barW, y + min(absorption, h), 0xFFFFD447.toInt())
        }
    }

    private fun statsFor(target: Player, distance: Float): List<StatLine> {
        val result = mutableListOf<StatLine>()
        if (showHealthText.value) {
            result += StatLine("HP ${"%.1f".format(target.health)}", healthColor(target))
        }
        if (showAbsorption.value && target.absorptionAmount > 0f) {
            result += StatLine("Abs ${"%.1f".format(target.absorptionAmount)}", 0xFFFFD447.toInt())
        }
        if (showDistance.value) {
            result += StatLine("${"%.1f".format(distance)}m", textColor.liveColor(textColor.value).argb)
        }
        if (showArmor.value) {
            result += StatLine("Armor ${target.armorValue}", 0xFF72B7FF.toInt())
        }
        if (showHeldItem.value && !target.mainHandItem.isEmpty) {
            result += StatLine(fitText(target.mainHandItem.hoverName.string, 92), 0xFFE6E6F2.toInt())
        }
        if (showPing.value) {
            pingFor(target)?.let { result += StatLine("${it}ms", pingColor(it)) }
        }
        return result
    }

    private fun drawEquipment(g: GuiGraphicsExtractor, target: Player, boxX: Int, y: Int, boxW: Int) {
        val stacks = equipmentFor(target)
        if (stacks.isEmpty()) return
        val icon = 16
        val gap = 1
        val width = stacks.size * icon + (stacks.size - 1) * gap
        var x = boxX + (boxW - width) / 2
        for (stack in stacks) {
            g.item(stack, x, y)
            g.itemDecorations(Font.getFont(), stack, x, y)
            x += icon + gap
        }
    }

    private fun drawText(g: GuiGraphicsExtractor, text: String, x: Int, y: Int, color: Int) {
        val scale = renderTextScale
        val font = Font.getFont()
        val component = Font.styledText(text)
        if (scale == 1f) {
            g.Text(font, component, x, y, color, textShadow.value)
            return
        }

        g.pose().pushMatrix()
        g.pose().translate(x.toFloat(), y.toFloat())
        g.pose().scale(scale, scale)
        g.Text(font, component, 0, 0, color, textShadow.value)
        g.pose().popMatrix()
    }

    private fun drawOutline(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, t: Int, color: Int) {
        val thick = t.coerceAtLeast(1)
        g.fill(x, y, x + w, y + thick, color)
        g.fill(x, y + h - thick, x + w, y + h, color)
        g.fill(x, y, x + thick, y + h, color)
        g.fill(x + w - thick, y, x + w, y + h, color)
    }

    private fun drawCornerOutline(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, t: Int, color: Int) {
        val thick = t.coerceAtLeast(1)
        val len = (min(w, h) * 0.28f).roundToInt().coerceIn(5, max(5, min(w, h) / 2))

        g.fill(x, y, x + len, y + thick, color)
        g.fill(x, y, x + thick, y + len, color)
        g.fill(x + w - len, y, x + w, y + thick, color)
        g.fill(x + w - thick, y, x + w, y + len, color)

        g.fill(x, y + h - thick, x + len, y + h, color)
        g.fill(x, y + h - len, x + thick, y + h, color)
        g.fill(x + w - len, y + h - thick, x + w, y + h, color)
        g.fill(x + w - thick, y + h - len, x + w, y + h, color)
    }

    private fun screenBoundsFor(target: Player, partialTick: Float, projection: Projection): ScreenBox? {
        val box = interpolatedBox(target, partialTick)
        val points = arrayOf(
            Vec3(box.minX, box.minY, box.minZ),
            Vec3(box.minX, box.minY, box.maxZ),
            Vec3(box.minX, box.maxY, box.minZ),
            Vec3(box.minX, box.maxY, box.maxZ),
            Vec3(box.maxX, box.minY, box.minZ),
            Vec3(box.maxX, box.minY, box.maxZ),
            Vec3(box.maxX, box.maxY, box.minZ),
            Vec3(box.maxX, box.maxY, box.maxZ),
        ).mapNotNull { projectToScreen(it, projection) }

        if (points.size < 2) return null

        val minX = points.minOf { it.first }
        val minY = points.minOf { it.second }
        val maxX = points.maxOf { it.first }
        val maxY = points.maxOf { it.second }
        if (maxX < 0f || maxY < 0f || minX > projection.screenW || minY > projection.screenH) return null

        val left = minX.coerceIn(-32f, projection.screenW + 32f)
        val top = minY.coerceIn(-32f, projection.screenH + 32f)
        val right = maxX.coerceIn(-32f, projection.screenW + 32f)
        val bottom = maxY.coerceIn(-32f, projection.screenH + 32f)
        if (right - left < 2f || bottom - top < 4f) return null

        return ScreenBox(left, top, right, bottom)
    }

    private fun interpolatedBox(target: Player, partialTick: Float): AABB {
        val x = target.xOld + (target.x - target.xOld) * partialTick
        val y = target.yOld + (target.y - target.yOld) * partialTick
        val z = target.zOld + (target.z - target.zOld) * partialTick
        val halfW = target.bbWidth.toDouble() * 0.5
        return AABB(
            x - halfW,
            y,
            z - halfW,
            x + halfW,
            y + target.bbHeight.toDouble(),
            z + halfW,
        )
    }

    private fun projectToScreen(worldPos: Vec3, projection: Projection): Pair<Float, Float>? {
        val dx = (worldPos.x - projection.cameraPos.x).toFloat()
        val dy = (worldPos.y - projection.cameraPos.y).toFloat()
        val dz = (worldPos.z - projection.cameraPos.z).toFloat()
        val mat = projection.matrix

        val x = mat.m00() * dx + mat.m10() * dy + mat.m20() * dz + mat.m30()
        val y = mat.m01() * dx + mat.m11() * dy + mat.m21() * dz + mat.m31()
        val w = mat.m03() * dx + mat.m13() * dy + mat.m23() * dz + mat.m33()
        if (w <= 0.05f) return null

        val ndcX = x / w
        val ndcY = y / w
        val sx = (ndcX + 1f) * 0.5f * projection.screenW
        val sy = (1f - ndcY) * 0.5f * projection.screenH
        return sx to sy
    }

    private fun sideHealthOffset(side: StatSide): Float {
        if (!showHealthBar.value) return 0f
        val sameSide = when (side) {
            StatSide.LEFT -> healthSide.value == HealthSide.LEFT
            StatSide.RIGHT -> healthSide.value == HealthSide.RIGHT
            StatSide.BELOW -> false
        }
        return if (sameSide) healthBarWidth.value + 3f else 0f
    }

    private fun resolveBorderColor(target: Player): Int {
        return when (borderMode.value) {
            BorderMode.CUSTOM -> borderColor.liveColor(borderColor.value).argb
            BorderMode.HEALTH -> healthColor(target)
            BorderMode.TEAM -> {
                val teamColor = targetTeamColor(target)
                if (teamColor != null) (borderColor.liveColor(borderColor.value).a shl 24) or teamColor
                else borderColor.liveColor(borderColor.value).argb
            }
        }
    }

    private fun targetTeamColor(target: Player): Int? {
        return target.getItemBySlot(EquipmentSlot.HEAD)
            .get(net.minecraft.core.component.DataComponents.DYED_COLOR)
            ?.rgb()
            ?.and(0x00FFFFFF)
    }

    private fun healthColor(target: Player): Int {
        val pct = (target.health / target.maxHealth).coerceIn(0f, 1f)
        val red = if (pct < 0.5f) 255 else ((1f - pct) * 2f * 255f).roundToInt()
        val green = if (pct > 0.5f) 255 else (pct * 2f * 255f).roundToInt()
        return (0xFF shl 24) or (red.coerceIn(0, 255) shl 16) or (green.coerceIn(0, 255) shl 8)
    }

    private fun pingFor(target: Player): Int? {
        return Minecraft.getInstance().connection
            ?.getPlayerInfo(target.uuid)
            ?.latency
            ?.takeIf { it >= 0 }
    }

    private fun pingColor(ping: Int): Int {
        return when {
            ping <= 60 -> 0xFF55FF55.toInt()
            ping <= 130 -> 0xFFFFD447.toInt()
            else -> 0xFFFF5555.toInt()
        }
    }

    private fun equipmentFor(target: Player): List<ItemStack> {
        return listOf(
            target.getItemBySlot(EquipmentSlot.HEAD),
            target.getItemBySlot(EquipmentSlot.CHEST),
            target.getItemBySlot(EquipmentSlot.LEGS),
            target.getItemBySlot(EquipmentSlot.FEET),
            target.mainHandItem,
            target.offhandItem,
        ).filter { !it.isEmpty }
    }

    private fun fitText(text: String, maxWidth: Int): String {
        val font = Font.getFont()
        if (font.width(Font.styledText(text)) <= maxWidth) return text
        var fitted = text
        while (fitted.isNotEmpty() && font.width(Font.styledText("$fitted...")) > maxWidth) {
            fitted = fitted.dropLast(1)
        }
        return if (fitted.isEmpty()) "" else "$fitted..."
    }

    private fun lineHeight(): Int {
        return (Font.getFont().lineHeight * renderTextScale).roundToInt().coerceAtLeast(1)
    }

    private data class Projection(
        val cameraPos: Vec3,
        val matrix: Matrix4f,
        val screenW: Float,
        val screenH: Float,
    )

    private data class ScreenBox(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top

        fun padded(amount: Float): ScreenBox {
            return ScreenBox(left - amount, top - amount, right + amount, bottom + amount)
        }
    }

    private data class EspEntry(
        val target: Player,
        val bounds: ScreenBox,
        val distance: Float,
    )

    private data class StatLine(
        val text: String,
        val color: Int,
    )
}
