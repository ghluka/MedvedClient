package me.ghluka.medved.module.modules.render

import me.ghluka.medved.config.entry.Color
import me.ghluka.medved.config.entry.ColorEntry
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.modules.other.Font
import me.ghluka.medved.module.modules.other.TargetFilter
import me.ghluka.medved.util.Text
import me.ghluka.medved.util.roundedFill
import net.minecraft.core.Holder
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.EquipmentSlot
import net.minecraft.world.entity.player.Player
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.item.enchantment.Enchantment
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f
import java.util.Locale
import kotlin.math.roundToInt

object Nametags : Module(
    name = "Nametags",
    description = "Replaces vanilla player nametags with custom ones",
    category = Category.RENDER
) {
    enum class Mode { CLEAN, EXPLICIT_B9 }
    enum class HealthMode { HEARTS, PERCENTAGE }

    private val mode = enum("mode", Mode.CLEAN)
    private val maxDistance = double("max distance", 64.0, 8.0, 256.0)
    private val scale = double("scale", 1.0, 0.65, 1.75)
    private val yOffset = double("y offset", 0.35, 0.0, 1.25)
    private val showHealth = boolean("health", true)
    private val showDistance = boolean("distance", true)
    private val healthMode = enum("health mode", HealthMode.HEARTS).also {
        it.visibleWhen = { mode.value == Mode.EXPLICIT_B9 && showHealth.value }
    }
    private val showArmor = boolean("armor", true).also {
        it.visibleWhen = { mode.value == Mode.EXPLICIT_B9 }
    }
    private val showDurability = boolean("durability", false).also {
        it.visibleWhen = { mode.value == Mode.EXPLICIT_B9 && showArmor.value }
    }
    private val showEnchantments = boolean("enchantments", true).also {
        it.visibleWhen = { mode.value == Mode.EXPLICIT_B9 && showArmor.value }
    }
    private val background = boolean("background", true)
    private val bgColor = color("bg color", Color(20, 20, 20, 180), allowAlpha = true).also {
        it.visibleWhen = { background.value }
    }
    private val textColor = color("text color", Color(235, 235, 245, 255), allowAlpha = true)
    private val textShadow = boolean("text shadow", false)

    @JvmStatic
    fun shouldHideVanilla(entity: Entity): Boolean {
        val target = entity as? Player ?: return false
        if (!isEnabled()) return false

        val viewer = Minecraft.getInstance().player ?: return false
        if (target === viewer) return false
        return TargetFilter.isValidTarget(viewer, target)
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
            .filter { TargetFilter.isValidTarget(viewer, it) }
            .mapNotNull { target ->
                val pos = tagPosition(target, partialTick)
                val screen = projectToScreen(pos, projection) ?: return@mapNotNull null
                NametagEntry(target, screen, viewer.distanceTo(target))
            }
            .sortedByDescending { it.distance }
            .toList()

        Font.withRenderScale(scale.value.toFloat()) {
            for (entry in entries) {
                when (mode.value) {
                    Mode.CLEAN -> drawClean(extractor, entry)
                    Mode.EXPLICIT_B9 -> drawExplicitB9(extractor, entry)
                }
            }
        }
    }

    private fun drawClean(g: GuiGraphicsExtractor, entry: NametagEntry) {
        val font = Font.getFont()
        val sc = scale.value.toFloat()
        val name = entry.target.name.string
        val healthText = if (showHealth.value) " ${"%.1f".format(entry.target.health)}" else ""
        val distanceText = if (showDistance.value) " ${"%.1f".format(entry.distance)}m" else ""
        val text = "$name$healthText$distanceText"
        val component = Font.styledText(text)
        val textW = (font.width(component) * sc).roundToInt()
        val textH = (font.lineHeight * sc).roundToInt().coerceAtLeast(1)
        val padX = (5 * sc).roundToInt().coerceAtLeast(3)
        val padY = (3 * sc).roundToInt().coerceAtLeast(2)
        val w = textW + padX * 2
        val h = textH + padY * 2
        val x = (entry.screen.first - w / 2f).roundToInt()
        val y = (entry.screen.second - h).roundToInt()

        if (background.value) {
            g.roundedFill(x, y, w, h, (4 * sc).roundToInt().coerceAtLeast(2), bgColor.liveColor(bgColor.value).argb)
        }

        g.pose().pushMatrix()
        g.pose().translate((x + padX).toFloat(), (y + padY).toFloat())
        if (sc != 1f) g.pose().scale(sc, sc)
        g.Text(font, component, 0, 0, textColor.liveColor(textColor.value).argb, textShadow.value)
        g.pose().popMatrix()
    }

    private fun drawExplicitB9(g: GuiGraphicsExtractor, entry: NametagEntry) {
        val font = Font.getFont()
        val sc = scale.value.toFloat()
        val name = explicitB9Name(entry)
        val health = if (showHealth.value) explicitB9Health(entry.target) else ""
        val nameComponent = Font.styledText(name)
        val healthComponent = Font.styledText(health)
        val healthGap = if (health.isNotEmpty()) 4 else 0
        val textW = font.width(nameComponent) + if (health.isNotEmpty()) healthGap + font.width(healthComponent) else 0
        val textH = font.lineHeight
        val padX = 5
        val padY = 3
        val w = ((textW + padX * 2) * sc).roundToInt().coerceAtLeast(24)
        val h = ((textH + padY * 2) * sc).roundToInt().coerceAtLeast(12)
        val x = (entry.screen.first - w / 2f).roundToInt()
        val y = (entry.screen.second - h).roundToInt()

        if (showArmor.value) {
            drawExplicitB9Equipment(g, entry.target, x + w / 2, y, sc)
        }

        if (background.value) {
            drawBorderedFill(
                g,
                x,
                y,
                w,
                h,
                0xC80A0A0A.toInt(),
                bgColor.liveColor(bgColor.value).argb,
            )
        }

        g.pose().pushMatrix()
        g.pose().translate((x + (padX * sc).roundToInt()).toFloat(), (y + (padY * sc).roundToInt()).toFloat())
        if (sc != 1f) g.pose().scale(sc, sc)
        g.Text(font, nameComponent, 0, 0, textColor.liveColor(textColor.value).argb, true)
        if (health.isNotEmpty()) {
            g.Text(font, healthComponent, font.width(nameComponent) + healthGap, 0, explicitB9HealthColor(entry.target), true)
        }
        g.pose().popMatrix()
    }

    private fun drawExplicitB9Equipment(g: GuiGraphicsExtractor, target: Player, centerX: Int, tagY: Int, sc: Float) {
        val stacks = explicitB9Equipment(target)
        if (stacks.isEmpty()) return

        val icon = (16 * sc).roundToInt().coerceAtLeast(8)
        val gap = (2 * sc).roundToInt().coerceAtLeast(1)
        val width = stacks.size * icon + (stacks.size - 1) * gap
        val x0 = centerX - width / 2
        val itemY = tagY - (22 * sc).roundToInt()

        for ((index, stack) in stacks.withIndex()) {
            val itemX = x0 + index * (icon + gap)
            drawScaledItem(g, stack, itemX, itemY, sc)
            if (showEnchantments.value || showDurability.value) {
                drawExplicitB9ItemText(g, stack, itemX, itemY, sc)
            }
        }
    }

    private fun drawScaledItem(g: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int, sc: Float) {
        g.pose().pushMatrix()
        g.pose().translate(x.toFloat(), y.toFloat())
        if (sc != 1f) g.pose().scale(sc, sc)
        g.item(stack, 0, 0)
        g.itemDecorations(Font.getFont(), stack, 0, 0)
        g.pose().popMatrix()
    }

    private fun drawExplicitB9ItemText(g: GuiGraphicsExtractor, stack: ItemStack, x: Int, y: Int, sc: Float) {
        val labels = mutableListOf<MiniLabel>()
        if (showDurability.value && stack.isDamageableItem) {
            labels += MiniLabel((stack.maxDamage - stack.damageValue).coerceAtLeast(0).toString(), 0xFFFFFFFF.toInt())
        }
        if (showEnchantments.value) {
            labels += explicitB9EnchantLabels(stack)
        }
        if (labels.isEmpty()) return

        val textScale = (0.5f * sc).coerceAtLeast(0.35f)
        val font = Font.getFont()
        val lineH = font.lineHeight
        val startY = y - (labels.size * lineH * textScale).roundToInt() - 2

        Font.withRenderScale(textScale) {
            g.pose().pushMatrix()
            g.pose().translate(x.toFloat(), startY.toFloat())
            g.pose().scale(textScale, textScale)
            for ((i, label) in labels.withIndex()) {
                g.Text(font, Font.styledText(label.text), 0, i * lineH, label.color, true)
            }
            g.pose().popMatrix()
        }
    }

    private fun explicitB9Equipment(target: Player): List<ItemStack> {
        val result = mutableListOf<ItemStack>()
        if (!target.mainHandItem.isEmpty) result += target.mainHandItem.copy()
        for (slot in listOf(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            val stack = target.getItemBySlot(slot)
            if (!stack.isEmpty) result += stack.copy()
        }
        return result
    }

    private fun explicitB9EnchantLabels(stack: ItemStack): List<MiniLabel> {
        if (stack.`is`(Items.ENCHANTED_GOLDEN_APPLE)) {
            return listOf(MiniLabel("god", 0xFFFFFFFF.toInt()))
        }

        val enchantments = stack.enchantments.entrySet().toList()
        if (enchantments.size >= 6) {
            return listOf(MiniLabel("god", 0xFFFF5555.toInt()))
        }

        return enchantments
            .asSequence()
            .sortedBy { enchantmentName(it.key) }
            .mapNotNull { entry ->
                val level = entry.intValue
                if (level <= 0) return@mapNotNull null
                MiniLabel("${enchantmentAbbreviation(entry.key)}$level", 0xFFFFFFFF.toInt())
            }
            .toList()
    }

    private fun enchantmentAbbreviation(enchantment: Holder<Enchantment>): String {
        val name = enchantmentName(enchantment)
        return name
            .filter { it.isLetterOrDigit() }
            .take(3)
            .lowercase(Locale.ROOT)
            .ifBlank { "ench" }
    }

    private fun enchantmentName(enchantment: Holder<Enchantment>): String =
        enchantment.value().description().string

    private fun explicitB9Name(entry: NametagEntry): String {
        val distance = if (showDistance.value) "${shortNumber(entry.distance)}m " else ""
        return distance + entry.target.displayName.string.ifBlank { entry.target.name.string }
    }

    private fun explicitB9Health(target: Player): String {
        val value = when (healthMode.value) {
            HealthMode.HEARTS -> (target.health + target.absorptionAmount) / 2f
            HealthMode.PERCENTAGE -> (target.health + target.absorptionAmount) * 5f
        }
        val suffix = if (healthMode.value == HealthMode.PERCENTAGE) "%" else ""
        return shortNumber(value) + suffix
    }

    private fun explicitB9HealthColor(target: Player): Int {
        val ratio = (target.health / target.maxHealth).coerceIn(0f, 1f)
        val red: Int
        val green: Int
        if (ratio > 0.5f) {
            red = ((1f - ratio) * 2f * 255f).roundToInt().coerceIn(0, 255)
            green = 255
        } else {
            red = 255
            green = (ratio * 2f * 255f).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (red shl 16) or (green shl 8)
    }

    private fun drawBorderedFill(g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int, border: Int, fill: Int) {
        if (w > 2 && h > 2) {
            g.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill)
        }
        g.fill(x, y, x + w, y + 1, border)
        g.fill(x, y + h - 1, x + w, y + h, border)
        g.fill(x, y + 1, x + 1, y + h - 1, border)
        g.fill(x + w - 1, y + 1, x + w, y + h - 1, border)
    }

    private fun shortNumber(value: Float): String {
        val rounded = (value * 10f).roundToInt() / 10f
        return if (rounded == rounded.toInt().toFloat()) {
            rounded.toInt().toString()
        } else {
            String.format(Locale.ROOT, "%.1f", rounded)
        }
    }

    private fun tagPosition(target: Player, partialTick: Float): Vec3 {
        val x = target.xOld + (target.x - target.xOld) * partialTick
        val y = target.yOld + (target.y - target.yOld) * partialTick + target.bbHeight + yOffset.value
        val z = target.zOld + (target.z - target.zOld) * partialTick
        return Vec3(x, y, z)
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
        if (ndcX < -1.4f || ndcX > 1.4f || ndcY < -1.4f || ndcY > 1.4f) return null

        val sx = (ndcX + 1f) * 0.5f * projection.screenW
        val sy = (1f - ndcY) * 0.5f * projection.screenH
        return sx to sy
    }

    private data class Projection(
        val cameraPos: Vec3,
        val matrix: Matrix4f,
        val screenW: Float,
        val screenH: Float,
    )

    private data class NametagEntry(
        val target: Player,
        val screen: Pair<Float, Float>,
        val distance: Float,
    )

    private data class MiniLabel(
        val text: String,
        val color: Int,
    )
}
