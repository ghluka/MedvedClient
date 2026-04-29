package me.ghluka.medved.gui.modes

import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.config.ConfigManager
import me.ghluka.medved.config.entry.*
import me.ghluka.medved.gui.HudEditorScreen
import me.ghluka.medved.gui.components.*
import me.ghluka.medved.module.HudModule
import me.ghluka.medved.module.Module
import me.ghluka.medved.module.ModuleManager
import me.ghluka.medved.util.CORNERS_BOT
import me.ghluka.medved.util.CORNERS_TOP
import me.ghluka.medved.util.NotificationManager
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.Minecraft

internal object DropdownMode {
    private data class PanelLayout(
        val key: Module.Category?,
        val x: Int,
        val y: Int,
        val height: Int,
    )

    fun render(gui: ClickGui, g: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (me.ghluka.medved.module.modules.other.ClickGui.showBackground.value) {
            g.fill(0, 0, gui.width, gui.height, gui.BG)
        }
        val layouts = buildLayouts(gui, gui.dropdownScroll)
        for (cat in gui.renderOrder) {
            val layout = layouts[cat] ?: continue
            if (cat == null) drawConfigBar(gui, g, mx, my, layout.x, layout.y)
            else drawCategoryPanel(gui, g, cat, mx, my, layout.x, layout.y)
        }
    }

    private fun buildLayouts(gui: ClickGui, scrollOffset: Int): Map<Module.Category?, PanelLayout> {
        for (cat in gui.renderOrder) {
            if (cat == null) {
                // kept separate from module panels
            } else {
                // handled below
            }
        }

        val result = mutableMapOf<Module.Category?, PanelLayout>()
        val cfgY = gui.cfgPanelY - scrollOffset
        result[null] = PanelLayout(null, gui.cfgPanelX, cfgY, if (gui.cfgPanelCollapsed) gui.HDR_H else gui.HDR_H + cfgPanelBodyH(gui))
        for (cat in Module.Category.entries) {
            val pos = gui.positions[cat] ?: continue
            result[cat] = PanelLayout(cat, pos.first, pos.second - scrollOffset, if (cat in gui.collapsed) gui.HDR_H else gui.fullPanelHeight(cat))
        }
        return result
    }

    private fun drawConfigBar(gui: ClickGui, g: GuiGraphicsExtractor, mx: Int, my: Int, px: Int, py: Int) {
        val expanded = !gui.cfgPanelCollapsed
        if (expanded) {
            g.roundedFill(px, py, gui.PNL_W, gui.HDR_H, 3, gui.HDR_BG, CORNERS_TOP)
            g.roundedFill(px, py + gui.HDR_H, gui.PNL_W, cfgPanelBodyH(gui), 3, gui.PNL_BG, CORNERS_BOT)
        } else {
            g.roundedFill(px, py, gui.PNL_W, gui.HDR_H, 3, gui.HDR_BG)
        }
        g.centeredText(gui.guiFont, gui.styled("CONFIGS"), px + gui.PNL_W / 2, py + (gui.HDR_H - 8) / 2, -1)
        g.text(gui.guiFont, gui.jbMono(if (expanded) "-" else "+"), px + gui.PNL_W - 12, py + (gui.HDR_H - 8) / 2, gui.TEXT_DIM)
        if (!expanded) return

        var y = py + gui.HDR_H

        val visW = gui.PNL_W - 10
        val textX = px + 5
        g.fill(px, y, px + gui.PNL_W, y + gui.ENT_H, if (gui.presetFieldActive) gui.shade(40, 0.25f) else gui.ENT_BG)
        g.fill(px, y, px + gui.PNL_W, y + 1, if (gui.presetFieldActive) gui.ACCENT else gui.shade(10, 0.05f))
        g.enableScissor(textX, y, textX + visW, y + gui.ENT_H)
        if (gui.presetFieldActive && gui.presetField.hasSelection) {
            val sx = textX - gui.presetField.scrollPx + gui.guiFont.width(gui.styled(gui.presetField.text.substring(0, gui.presetField.selMin)))
            val ex = textX - gui.presetField.scrollPx + gui.guiFont.width(gui.styled(gui.presetField.text.substring(0, gui.presetField.selMax)))
            g.fill(sx, y + 1, ex, y + gui.ENT_H - 1, gui.argb(170, 60, 110, 210))
        }
        if (gui.presetField.text.isEmpty() && !gui.presetFieldActive) {
            g.text(gui.guiFont, gui.styled("preset name..."), textX, y + (gui.ENT_H - 8) / 2, gui.TEXT_DIM)
        } else {
            g.text(gui.guiFont, gui.styled(gui.presetField.text), textX - gui.presetField.scrollPx, y + (gui.ENT_H - 8) / 2, gui.TEXT)
        }
        if (gui.presetFieldActive && gui.cursorVisible) {
            val cx = textX - gui.presetField.scrollPx + gui.guiFont.width(gui.styled(gui.presetField.text.substring(0, gui.presetField.cursor)))
            g.fill(cx, y + 1, cx + 1, y + gui.ENT_H - 1, gui.argb(230, 220, 220, 255))
        }
        g.disableScissor()
        y += gui.ENT_H

        val btnW = gui.PNL_W / 3
        val saveHov = mx in px until px + btnW && my in y until y + gui.MOD_H
        val loadHov = mx in px + btnW until px + btnW * 2 && my in y until y + gui.MOD_H
        val foldHov = mx in px + btnW * 2 until px + gui.PNL_W && my in y until y + gui.MOD_H
        g.fill(px,            y, px + btnW,     y + gui.MOD_H, if (saveHov) gui.shade(50, 0.20f) else gui.BTN_BG)
        g.fill(px + btnW,     y, px + btnW * 2, y + gui.MOD_H, if (loadHov) gui.shade(50, 0.20f) else gui.BTN_BG)
        g.fill(px + btnW * 2, y, px + gui.PNL_W,   y + gui.MOD_H, if (foldHov) gui.shade(50, 0.20f) else gui.BTN_BG)
        g.fill(px + btnW - 1,     y, px + btnW,     y + gui.MOD_H, gui.shade(10, 0.05f))
        g.fill(px + btnW * 2 - 1, y, px + btnW * 2, y + gui.MOD_H, gui.shade(10, 0.05f))
        g.centeredText(gui.guiFont, gui.styled("Save"),   px + btnW / 2,            y + (gui.MOD_H - 8) / 2, gui.TEXT)
        g.centeredText(gui.guiFont, gui.styled("Load"),   px + btnW + btnW / 2,     y + (gui.MOD_H - 8) / 2, gui.TEXT)
        g.centeredText(gui.guiFont, gui.styled("Folder"), px + btnW * 2 + btnW / 2, y + (gui.MOD_H - 8) / 2, gui.TEXT)
        y += gui.MOD_H

        val presets = ConfigManager.listPresets()
        if (presets.isEmpty()) {
            g.fill(px, y, px + gui.PNL_W, y + gui.ENT_H, gui.ENT_BG)
            g.text(gui.guiFont, gui.styled("(no presets saved)"), px + 5, y + (gui.ENT_H - 8) / 2, gui.TEXT_DIM)
            y += gui.ENT_H
        } else {
            for (preset in presets) {
                val hov = mx in px until px + gui.PNL_W && my in y until y + gui.ENT_H
                val selected = preset == gui.presetField.text
                g.fill(px, y, px + gui.PNL_W, y + gui.ENT_H, if (hov) gui.MOD_HOV else gui.ENT_BG)
                if (selected) g.fill(px, y, px + 3, y + gui.ENT_H, gui.ACCENT)
                g.text(gui.guiFont, gui.styled(preset), px + 7, y + (gui.ENT_H - 8) / 2, if (selected) gui.TEXT else gui.TEXT_DIM)
                y += gui.ENT_H
            }
        }
    }

    private fun cfgPanelBodyH(gui: ClickGui): Int {
        val presets = ConfigManager.listPresets()
        return gui.ENT_H + gui.MOD_H + presets.size.coerceAtLeast(1) * gui.ENT_H
    }
    private fun drawCategoryPanel(gui: ClickGui, g: GuiGraphicsExtractor, cat: Module.Category, mx: Int, my: Int, px: Int, py: Int) {
        val expanded = cat !in gui.collapsed
        val panelH = if (expanded) gui.fullPanelHeight(cat) else gui.HDR_H

        if (expanded) {
            g.roundedFill(px, py, gui.PNL_W, gui.HDR_H, 3, gui.HDR_BG, CORNERS_TOP)
            g.roundedFill(px, py + gui.HDR_H, gui.PNL_W, panelH - gui.HDR_H, 3, gui.PNL_BG, CORNERS_BOT)
        } else {
            g.roundedFill(px, py, gui.PNL_W, gui.HDR_H, 3, gui.HDR_BG)
        }
        g.centeredText(gui.guiFont, gui.styled(cat.name), px + gui.PNL_W / 2, py + (gui.HDR_H - 8) / 2, -1)
        g.text(gui.guiFont, gui.jbMono(if (expanded) "-" else "+"), px + gui.PNL_W - 12, py + (gui.HDR_H - 8) / 2, gui.TEXT_DIM)

        if (!expanded) return

        g.enableScissor(px, py + gui.HDR_H, px + gui.PNL_W, py + panelH)
        var y = py + gui.HDR_H

        for (mod in ModuleManager.getByCategory(cat)) {
            val hovMod = mx in px until px + gui.PNL_W && my in y until y + gui.MOD_H
            if (hovMod) gui.hoveredMod = mod
            g.fill(px, y, px + gui.PNL_W, y + gui.MOD_H, if (hovMod) gui.MOD_HOV else gui.MOD_NORM)
            if (mod.isEnabled()) g.fill(px, y, px + 3, y + gui.MOD_H, gui.ACCENT)
            val entries = gui.configEntries(mod)
            val nameAvailW = gui.PNL_W - 7 - (if (entries.isNotEmpty()) 14 else 4)
            gui.drawModuleName(g, mod, px + 7, y, nameAvailW, if (mod.isEnabled()) gui.TEXT else gui.TEXT_DIM)
            if (entries.isNotEmpty()) {
                g.text(gui.guiFont, gui.jbMono(if (mod in gui.expandedModules) "-" else "+"), px + gui.PNL_W - 11, y + (gui.MOD_H - 8) / 2, gui.TEXT_DIM)
            }
            y += gui.MOD_H

            if (mod in gui.expandedModules) {
                for (entry in entries) {
                    gui.drawEntry(g, entry, px + 3, y, gui.PNL_W - 3, mx, my)
                    y += gui.ENT_H
                    if (entry is IntEntry) {
                        val bounded = entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE
                        gui.drawNumericSliderBar(
                            g,
                            bounded,
                            entry.value.toFloat(),
                            if (bounded) entry.min.toFloat() else 0f,
                            if (bounded) entry.max.toFloat() else 1f,
                            px + 3,
                            y,
                            gui.PNL_W - 3
                        )
                        y += gui.ENT_H
                    }
                    if (entry is FloatEntry) {
                        val bounded = entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE
                        gui.drawNumericSliderBar(
                            g,
                            bounded,
                            entry.value,
                            entry.min.takeIf { it != -Float.MAX_VALUE } ?: 0f,
                            entry.max.takeIf { it != Float.MAX_VALUE } ?: 1f,
                            px + 3,
                            y,
                            gui.PNL_W - 3
                        )
                        y += gui.ENT_H
                    }
                    if (entry is DoubleEntry) {
                        val bounded = entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE
                        gui.drawNumericSliderBar(
                            g,
                            bounded,
                            entry.value.toFloat(),
                            entry.min.takeIf { it != -Double.MAX_VALUE }?.toFloat() ?: 0f,
                            entry.max.takeIf { it != Double.MAX_VALUE }?.toFloat() ?: 1f,
                            px + 3,
                            y,
                            gui.PNL_W - 3
                        )
                        y += gui.ENT_H
                    }
                    if (entry is IntRangeEntry) {
                        gui.drawRangeSliders(g, entry, px + 6, y, gui.PNL_W - 6)
                        y += gui.ENT_H
                    }
                    if (entry is FloatRangeEntry) {
                        gui.drawFloatRangeSliders(g, entry, px + 6, y, gui.PNL_W - 6)
                        y += gui.ENT_H
                    }
                    if (entry == gui.expandedColorEntry && entry is ColorEntry) {
                        gui.colorPickerX = px + 6
                        gui.colorPickerY = y
                        gui.colorPickerW = gui.PNL_W - 6
                    }
                    if (entry == gui.expandedEnum && entry is EnumEntry<*>) {
                        val ew = gui.enumDropdownWidth(entry)
                        gui.enumDropdownX = px + gui.PNL_W - ew
                        gui.enumDropdownY = y
                        gui.enumDropdownW = ew
                    }
                }
            }
        }

        g.disableScissor()
    }

    fun handleScroll(gui: ClickGui, mouseY: Double, scrollY: Double): Boolean {
        val scrollAmount = (scrollY * 24).toInt()

        var maxExpandedY = 0
        val layouts = buildLayouts(gui, 0)
        for ((cat, layout) in layouts) {
            val unscrolledBottomY = layout.y + layout.height
            if (unscrolledBottomY > maxExpandedY) maxExpandedY = unscrolledBottomY
        }

        val viewHeight = gui.height - 50
        val maxScrollOffset = kotlin.math.max(0, maxExpandedY - viewHeight)

        val potentialNewScroll = gui.dropdownScroll - scrollAmount
        val newScroll = potentialNewScroll.coerceIn(0, maxScrollOffset)

        return if (newScroll != gui.dropdownScroll) {
            gui.dropdownScroll = newScroll
            true
        } else {
            false
        }
    }

    fun handleMouseClick(gui: ClickGui, mx: Int, my: Int, btn: Int): Boolean {
        if (gui.presetFieldActive) gui.presetFieldActive = false
        val layouts = buildLayouts(gui, gui.dropdownScroll)
        val cfgLayout = layouts[null] ?: return false
        val px = cfgLayout.x
        val py = cfgLayout.y
        val expanded = !gui.cfgPanelCollapsed

        if (my in py until py + gui.HDR_H && mx in px until px + gui.PNL_W) {
            gui.bringConfigToFront()
            if (btn == 0) {
                gui.draggingCfgPanel = true
                gui.cfgDragOffX = mx - px
                gui.cfgDragOffY = my - py
            } else if (btn == 1) {
                gui.cfgPanelCollapsed = !gui.cfgPanelCollapsed
            }
            return true
        }

        if (expanded) {
            val panelH = gui.HDR_H + gui.cfgPanelBodyH()
            if (mx in px until px + gui.PNL_W && my in py + gui.HDR_H until py + panelH) {
                var y = py + gui.HDR_H
                if (my in y until y + gui.ENT_H) {
                    gui.presetFieldActive = true
                    gui.draggingPresetField = true
                    val relX = mx - px - 5
                    gui.presetField.apply {
                        cursor = posFromPixel(relX)
                        selAnchor = cursor
                        clampScroll(gui.PNL_W - 10)
                    }
                    return true
                }
                y += gui.ENT_H
                if (my in y until y + gui.MOD_H) {
                    val btnW = gui.PNL_W / 3
                    val name = gui.presetField.text.ifBlank { "default" }
                    when {
                        mx in px until px + btnW -> {
                            ConfigManager.savePreset(name)
                            NotificationManager.show("Config Saved", name)
                        }
                        mx in px + btnW until px + btnW * 2 -> {
                            val exists = name in ConfigManager.listPresets()
                            ConfigManager.loadPreset(name)
                            if (exists) NotificationManager.show("Config Loaded", name)
                            else NotificationManager.show("Not Found", name)
                        }
                        else -> ConfigManager.openPresetFolder()
                    }
                    return true
                }
                y += gui.MOD_H
                for (preset in ConfigManager.listPresets()) {
                    if (my in y until y + gui.ENT_H) {
                        gui.presetField.set(preset)
                        gui.presetField.clampScroll(gui.PNL_W - 10)
                        gui.presetNameBuffer = preset
                        return true
                    }
                    y += gui.ENT_H
                }
                return true
            }
        }

        for (cat in gui.renderOrder.asReversed()) {
            val layout = layouts[cat] ?: continue
            val cpx = layout.x
            val cpy = layout.y
            val catValue = cat ?: continue
            val catExpanded = catValue !in gui.collapsed

            if (my in cpy until cpy + gui.HDR_H && mx in cpx until cpx + gui.PNL_W) {
                gui.bringToFront(catValue)
                if (btn == 0) {
                    gui.draggingCat = catValue
                    gui.dragOffX = mx - cpx
                    gui.dragOffY = my - cpy
                } else if (btn == 1) {
                    if (catValue in gui.collapsed) gui.collapsed.remove(catValue) else gui.collapsed.add(catValue)
                }
                return true
            }

            if (!catExpanded) continue

            val panelH = gui.fullPanelHeight(catValue)
            if (mx !in cpx until cpx + gui.PNL_W || my !in cpy + gui.HDR_H until cpy + panelH) continue

            gui.bringToFront(catValue)
            var y = cpy + gui.HDR_H
            for (mod in ModuleManager.getByCategory(catValue)) {
                if (my in y until y + gui.MOD_H) {
                    val entries = gui.configEntries(mod)
                    when {
                        btn == 0 && mx >= cpx + gui.PNL_W - 14 && entries.isNotEmpty() -> gui.toggleExpand(mod)
                        btn == 0 && !mod.isProtected -> mod.toggle()
                        btn == 0 && mod.isProtected && entries.isNotEmpty() -> gui.toggleExpand(mod)
                        btn == 1 && entries.isNotEmpty() -> gui.toggleExpand(mod)
                    }
                    return true
                }
                y += gui.MOD_H

                if (mod in gui.expandedModules) {
                    for (entry in gui.configEntries(mod)) {
                        if (my in y until y + gui.ENT_H) {
                            if (entry is HudEditEntry && mod is HudModule) {
                                Minecraft.getInstance().setScreen(HudEditorScreen(mod, gui))
                            } else {
                                gui.handleEntryClick(entry, cpx + 3, y, gui.PNL_W - 3, mx, btn)
                            }
                            return true
                        }
                        y += gui.ENT_H

                        if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) {
                            if (my in y until y + gui.ENT_H) {
                                gui.handleNumericBarClick(entry, cpx + 3, y, gui.PNL_W - 3, mx, btn)
                                return true
                            }
                            y += gui.ENT_H
                        }
                        if (entry is IntRangeEntry) {
                            if (gui.handleRangeClick(entry, cpx + 6, y, gui.PNL_W - 6, mx, my)) return true
                            y += gui.ENT_H
                        }
                        if (entry is FloatRangeEntry) {
                            if (gui.handleFloatRangeClick(entry, cpx + 6, y, gui.PNL_W - 6, mx, my)) return true
                            y += gui.ENT_H
                        }
                        if (entry == gui.expandedColorEntry && entry is ColorEntry) {
                            if (gui.handleColorClick(entry, cpx + 6, y, gui.PNL_W - 6, mx, my)) return true
                        }
                    }
                }
            }
            return true
        }

        return false
    }
}
