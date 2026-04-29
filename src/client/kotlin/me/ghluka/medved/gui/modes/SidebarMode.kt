package me.ghluka.medved.gui.modes

import me.ghluka.medved.config.entry.DoubleEntry
import me.ghluka.medved.config.entry.FloatEntry
import me.ghluka.medved.config.entry.FloatRangeEntry
import me.ghluka.medved.config.entry.IntEntry
import me.ghluka.medved.config.entry.IntRangeEntry
import me.ghluka.medved.gui.ClickGui
import me.ghluka.medved.gui.components.*
import me.ghluka.medved.util.roundedFill
import net.minecraft.client.gui.GuiGraphicsExtractor

internal object SidebarMode {
    fun render(gui: ClickGui, g: GuiGraphicsExtractor, mx: Int, my: Int) {
        if (me.ghluka.medved.module.modules.other.ClickGui.showBackground.value) g.fill(0, 0, gui.width, gui.height, gui.BG)

        val pw = 480
        val ph = 320
        val px = gui.sidebarPaneX
        val py = gui.sidebarPaneY
        val tabH = 24
        val catW = 110
        val contentX = px + catW
        val contentW = pw - catW
        val smallFont = gui.guiFont
        val WHITE = gui.argb(255, 230, 230, 240)
        val GREY = gui.argb(255, 130, 130, 150)

        val logoH = 24
        g.roundedFill(px, py, pw, ph, 4, gui.PNL_BG)

        g.fill(px, py, px + pw, py + logoH, gui.shade(20, 0.15f))
        g.centeredText(smallFont, gui.styled("GRIZZLY"), px + pw / 2, py + (logoH - 8) / 2, gui.TEXT_DIM)

        val tabW = pw / 2
        val tabs = listOf("Modules", "Config")
        val tabBarY = py + logoH
        for ((i, tabName) in tabs.withIndex()) {
            val tx = px + i * tabW
            val tabSel = gui.sidebarTab == i
            val tabHov = mx in tx until tx + tabW && my in tabBarY until tabBarY + tabH
            g.fill(
                tx,
                tabBarY,
                tx + tabW,
                tabBarY + tabH,
                if (tabSel) gui.shade(30, 0.18f) else if (tabHov) gui.shade(25, 0.12f) else gui.shade(18, 0.08f),
            )
            if (tabSel) g.fill(tx, tabBarY + tabH - 2, tx + tabW, tabBarY + tabH, gui.ACCENT)
            g.centeredText(smallFont, gui.styled(tabName), tx + tabW / 2, tabBarY + (tabH - 8) / 2, if (tabSel) gui.TEXT else gui.TEXT_DIM)
        }

        if (gui.sidebarTab == 0) {
            val catAreaY = py + logoH + tabH
            g.fill(px, catAreaY, contentX, py + ph, gui.shade(14, 0.06f))
            var cy = catAreaY + 6
            val catPad = 2
            for (cat in me.ghluka.medved.module.Module.Category.entries) {
                val rowH = 18
                val catHov = mx in px until contentX && my in cy until cy + rowH
                val catSel = gui.selectedCategory == cat
                g.fill(
                    px,
                    cy,
                    contentX,
                    cy + rowH,
                    if (catSel) gui.shade(35, 0.22f) else if (catHov) gui.shade(28, 0.14f) else gui.shade(14, 0.06f),
                )
                g.centeredText(smallFont, gui.styled(cat.name), px + catW / 2, cy + (rowH - 8) / 2, if (catSel) WHITE else GREY)
                cy += rowH + catPad
            }
        }

        val bodyX = if (gui.sidebarTab == 0) contentX else px
        val bodyY = py + logoH + tabH
        val bodyW = if (gui.sidebarTab == 0) contentW else pw
        val bodyH = ph - logoH - tabH - 12
        g.enableScissor(bodyX, bodyY + 6, bodyX + bodyW, bodyY + 6 + bodyH)

        if (gui.sidebarTab == 0) {
            val detailMod = gui.sidebarDetailMod
            if (detailMod != null) {
                val headerY = bodyY + 6
                val backBtnH = 18
                val nameH = smallFont.lineHeight + 4
                val descMaxW = bodyW - 80
                val descLines = if (detailMod.description.isBlank()) emptyList() else {
                    val words = detailMod.description.split(" ")
                    val lines = mutableListOf<String>()
                    var cur = ""
                    for (word in words) {
                        val candidate = if (cur.isEmpty()) word else "$cur $word"
                        if (smallFont.width(gui.styled(candidate)) <= descMaxW) cur = candidate
                        else {
                            if (cur.isNotEmpty()) lines += cur
                            cur = word
                        }
                    }
                    if (cur.isNotEmpty()) lines += cur
                    lines
                }
                val descLineH = (smallFont.lineHeight * 1.3).toInt()
                val descH = descLines.size * descLineH
                val tgH = 18
                val toggleH = tgH + 8
                val staticH = backBtnH + nameH + descH + 8 + toggleH
                val entries = gui.configEntries(detailMod)
                var entriesH = 0
                for (entry in entries) {
                    entriesH += gui.ENT_H
                    if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) entriesH += gui.ENT_H
                    if (entry is IntRangeEntry || entry is FloatRangeEntry) entriesH += gui.ENT_H
                }
                gui.sidebarConfigScrollMax = (entriesH - (bodyH - staticH)).coerceAtLeast(0).toFloat()
                if (gui.sidebarConfigScroll > gui.sidebarConfigScrollMax) gui.sidebarConfigScroll = gui.sidebarConfigScrollMax
                if (gui.sidebarConfigScroll < 0f) gui.sidebarConfigScroll = 0f
                var ey = headerY
                g.text(smallFont, gui.styled("< Back"), bodyX + 8, ey + 3, gui.TEXT_DIM)
                ey += backBtnH
                g.text(smallFont, gui.styled(detailMod.name), bodyX + 8, ey, WHITE)
                ey += nameH
                descLines.forEachIndexed { i, line ->
                    g.text(smallFont, gui.styled(line), bodyX + 8, ey + i * descLineH, GREY)
                }
                ey += descH + 8
                val tgX = bodyX + 8
                val tgY = ey
                val tgLabel = if (detailMod.isEnabled()) "ON" else "OFF"
                val tgCol = if (detailMod.isEnabled()) gui.BTN_ON else gui.BTN_OFF
                g.fill(tgX, tgY, tgX + 60, tgY + tgH, tgCol)
                g.centeredText(smallFont, gui.styled(tgLabel), tgX + 30, tgY + (tgH - 8) / 2, gui.TEXT)
                ey += toggleH

                val configRegionTop = ey
                val configRegionBottom = bodyY + 6 + bodyH
                var entryY = configRegionTop - gui.sidebarConfigScroll.toInt()
                if (entries.isEmpty()) {
                    g.text(smallFont, gui.styled("No settings."), bodyX + 6, entryY, GREY)
                } else {
                    for (entry in entries) {
                        if (entryY + gui.ENT_H > configRegionBottom) break
                        gui.drawEntry(g, entry, bodyX + 4, entryY, bodyW - 8, mx, my)
                        entryY += gui.ENT_H
                        if (entry is IntEntry) {
                            val bounded = entry.min != Int.MIN_VALUE && entry.max != Int.MAX_VALUE
                            gui.drawNumericSliderBar(g, bounded, entry.value.toFloat(), if (bounded) entry.min.toFloat() else 0f, if (bounded) entry.max.toFloat() else 1f, bodyX + 4, entryY, bodyW - 8)
                            entryY += gui.ENT_H
                        }
                        if (entry is FloatEntry) {
                            val bounded = entry.min != -Float.MAX_VALUE && entry.max != Float.MAX_VALUE
                            gui.drawNumericSliderBar(g, bounded, entry.value, entry.min.takeIf { it != -Float.MAX_VALUE } ?: 0f, entry.max.takeIf { it != Float.MAX_VALUE } ?: 1f, bodyX + 4, entryY, bodyW - 8)
                            entryY += gui.ENT_H
                        }
                        if (entry is DoubleEntry) {
                            val bounded = entry.min != -Double.MAX_VALUE && entry.max != Double.MAX_VALUE
                            gui.drawNumericSliderBar(g, bounded, entry.value.toFloat(), entry.min.takeIf { it != -Double.MAX_VALUE }?.toFloat() ?: 0f, entry.max.takeIf { it != Double.MAX_VALUE }?.toFloat() ?: 1f, bodyX + 4, entryY, bodyW - 8)
                            entryY += gui.ENT_H
                        }
                        if (entry is IntRangeEntry) {
                            gui.drawRangeSliders(g, entry, bodyX + 6, entryY, bodyW - 8)
                            entryY += gui.ENT_H
                        }
                        if (entry is FloatRangeEntry) {
                            gui.drawFloatRangeSliders(g, entry, bodyX + 6, entryY, bodyW - 8)
                            entryY += gui.ENT_H
                        }
                        if (entry == gui.expandedColorEntry && entry is me.ghluka.medved.config.entry.ColorEntry) {
                            gui.drawColorPicker(g, entry, bodyX + 6, entryY, bodyW - 8, mx, my)
                            entryY += gui.colorPickerH
                        }
                        if (entry == gui.expandedEnum && entry is me.ghluka.medved.config.entry.EnumEntry<*>) {
                            val ew = gui.enumDropdownWidth(entry)
                            gui.enumDropdownX = bodyX + bodyW - ew
                            gui.enumDropdownY = entryY
                            gui.enumDropdownW = ew
                        }
                    }
                }
            } else {
                val mods = gui.selectedCategory?.let { me.ghluka.medved.module.ModuleManager.getByCategory(it) } ?: emptyList()
                if (mods.isEmpty()) {
                    g.centeredText(smallFont, gui.styled("Select a category"), bodyX + bodyW / 2, bodyY + 6 + bodyH / 2 - 4, GREY)
                    gui.sidebarScroll = 0f
                    gui.sidebarScrollMax = 0f
                } else {
                    val lineH = gui.guiFont.lineHeight
                    val cardPad = 4
                    val cardList = mods.map { mod ->
                        val descMaxW = bodyW - 80
                        val descLines = if (mod.description.isBlank()) emptyList() else {
                            val words = mod.description.split(" ")
                            val lines = mutableListOf<String>()
                            var cur = ""
                            for (word in words) {
                                val candidate = if (cur.isEmpty()) word else "$cur $word"
                                if (gui.guiFont.width(gui.styled(candidate)) <= descMaxW) {
                                    cur = candidate
                                } else {
                                    if (cur.isNotEmpty()) lines += cur
                                    cur = word
                                }
                            }
                            if (cur.isNotEmpty()) lines += cur
                            lines
                        }
                        val nameTopPad = 8
                        val nameBottomPad = 8
                        val descLineSpacing = (lineH * 1.3).toInt()
                        val cardH = nameTopPad + lineH + (if (descLines.isEmpty()) 0 else descLines.size * descLineSpacing + nameBottomPad) + 8
                        Triple(mod, descLines, cardH)
                    }
                    val totalH = cardList.sumOf { it.third + cardPad } - cardPad
                    gui.sidebarScrollMax = (totalH - bodyH).coerceAtLeast(0).toFloat()
                    if (gui.sidebarScroll > gui.sidebarScrollMax) gui.sidebarScroll = gui.sidebarScrollMax
                    if (gui.sidebarScroll < 0f) gui.sidebarScroll = 0f
                    var my2 = bodyY + 6 - gui.sidebarScroll.toInt()
                    for ((mod, descLines, cardH) in cardList) {
                        if (my2 + cardH < bodyY + 6) {
                            my2 += cardH + cardPad
                            continue
                        }
                        if (my2 > bodyY + 6 + bodyH) break
                        val mHov = mx in bodyX until bodyX + bodyW && my in my2 until my2 + cardH
                        if (mHov) gui.hoveredMod = mod
                        g.roundedFill(bodyX + 4, my2, bodyW - 8, cardH, 3, if (mHov) gui.shade(38, 0.14f) else gui.shade(32, 0.09f))
                        if (mod.isEnabled()) g.roundedFill(bodyX + 4, my2, 3, cardH, 3, gui.ACCENT, me.ghluka.medved.util.CORNERS_LEFT)
                        val nameTopPad = 8
                        val nameBottomPad = 8
                        val descLineSpacing = (lineH * 1.3).toInt()
                        g.text(smallFont, gui.styled(mod.name), bodyX + 12, my2 + nameTopPad, WHITE)
                        descLines.forEachIndexed { i, line ->
                            g.text(smallFont, gui.styled(line), bodyX + 12, my2 + nameTopPad + lineH + nameBottomPad + i * descLineSpacing, GREY)
                        }
                        val tgX = bodyX + bodyW - 44
                        val tgY = my2 + cardH / 2 - 6
                        g.fill(tgX, tgY, tgX + 32, tgY + 12, if (mod.isEnabled()) gui.BTN_ON else gui.BTN_OFF)
                        g.centeredText(smallFont, gui.styled(if (mod.isEnabled()) "ON" else "OFF"), tgX + 16, tgY + 2, gui.TEXT)
                        if (gui.configEntries(mod).isNotEmpty()) {
                            g.text(smallFont, gui.plain("⋮"), tgX - 12, my2 + cardH / 2 - 4, gui.TEXT_DIM)
                        }
                        my2 += cardH + cardPad
                    }
                }
            }
        } else {
            var ey = bodyY + 6
            val visW = bodyW - 10
            val textX2 = bodyX + 5
            g.fill(bodyX, ey, bodyX + bodyW, ey + gui.ENT_H, if (gui.presetFieldActive) gui.shade(40, 0.25f) else gui.ENT_BG)
            g.fill(bodyX, ey, bodyX + bodyW, ey + 1, if (gui.presetFieldActive) gui.ACCENT else gui.shade(10, 0.05f))
            g.enableScissor(textX2, ey, textX2 + visW, ey + gui.ENT_H)
            if (gui.presetFieldActive && gui.presetField.hasSelection) {
                val sx2 = textX2 - gui.presetField.scrollPx + gui.guiFont.width(gui.styled(gui.presetField.text.substring(0, gui.presetField.selMin)))
                val ex2 = textX2 - gui.presetField.scrollPx + gui.guiFont.width(gui.styled(gui.presetField.text.substring(0, gui.presetField.selMax)))
                g.fill(sx2, ey + 1, ex2, ey + gui.ENT_H - 1, gui.argb(170, 60, 110, 210))
            }
            if (gui.presetField.text.isEmpty() && !gui.presetFieldActive) g.text(gui.guiFont, gui.styled("preset name..."), textX2, ey + (gui.ENT_H - 8) / 2, gui.TEXT_DIM)
            else g.text(gui.guiFont, gui.styled(gui.presetField.text), textX2 - gui.presetField.scrollPx, ey + (gui.ENT_H - 8) / 2, gui.TEXT)
            if (gui.presetFieldActive && gui.cursorVisible) {
                val cx2 = textX2 - gui.presetField.scrollPx + gui.guiFont.width(gui.styled(gui.presetField.text.substring(0, gui.presetField.cursor)))
                g.fill(cx2, ey + 1, cx2 + 1, ey + gui.ENT_H - 1, gui.argb(230, 220, 220, 255))
            }
            g.disableScissor()
            ey += gui.ENT_H
            val btnW2 = bodyW / 3
            for ((i, label2) in listOf("Save", "Load", "Folder").withIndex()) {
                val bx2 = bodyX + i * btnW2
                val bHov = mx in bx2 until bx2 + btnW2 && my in ey until ey + gui.MOD_H
                g.fill(bx2, ey, bx2 + btnW2, ey + gui.MOD_H, if (bHov) gui.shade(50, 0.20f) else gui.BTN_BG)
                if (i < 2) g.fill(bx2 + btnW2 - 1, ey, bx2 + btnW2, ey + gui.MOD_H, gui.shade(10, 0.05f))
                g.centeredText(gui.guiFont, gui.styled(label2), bx2 + btnW2 / 2, ey + (gui.MOD_H - 8) / 2, gui.TEXT)
            }
            ey += gui.MOD_H
            val presets = me.ghluka.medved.config.ConfigManager.listPresets()
            if (presets.isEmpty()) {
                g.fill(bodyX, ey, bodyX + bodyW, ey + gui.ENT_H, gui.ENT_BG)
                g.text(gui.guiFont, gui.styled("(no presets saved)"), bodyX + 5, ey + (gui.ENT_H - 8) / 2, gui.TEXT_DIM)
            } else {
                for (preset in presets) {
                    if (ey + gui.ENT_H > bodyY + bodyH) break
                    val pHov = mx in bodyX until bodyX + bodyW && my in ey until ey + gui.ENT_H
                    val pSel = preset == gui.presetField.text
                    g.fill(bodyX, ey, bodyX + bodyW, ey + gui.ENT_H, if (pHov) gui.MOD_HOV else gui.ENT_BG)
                    if (pSel) g.fill(bodyX, ey, bodyX + 3, ey + gui.ENT_H, gui.ACCENT)
                    g.text(gui.guiFont, gui.styled(preset), bodyX + 7, ey + (gui.ENT_H - 8) / 2, if (pSel) gui.TEXT else gui.TEXT_DIM)
                    ey += gui.ENT_H
                }
            }
        }

        g.disableScissor()
    }

    fun handleScroll(gui: ClickGui, mouseX: Double, mouseY: Double, scrollY: Double): Boolean {
        if (gui.sidebarTab == 0 && gui.sidebarDetailMod != null) {
            val pw = 480
            val ph = 320
            val px = gui.sidebarPaneX
            val py = gui.sidebarPaneY
            val catW = 110
            val contentX = px + catW
            val contentW = pw - catW
            val logoH = 24
            val tabH = 24
            val bodyX = contentX
            val bodyY = py + logoH + tabH
            val bodyW = contentW
            val bodyH = ph - logoH - tabH - 12
            val detailMod = gui.sidebarDetailMod
            val backBtnH = 18
            val nameH = gui.guiFont.lineHeight + 4
            val descMaxW = bodyW - 80
            val descLines = if (detailMod != null && detailMod.description.isNotBlank()) {
                val words = detailMod.description.split(" ")
                val lines = mutableListOf<String>()
                var cur = ""
                for (word in words) {
                    val candidate = if (cur.isEmpty()) word else "$cur $word"
                    if (gui.guiFont.width(gui.styled(candidate)) <= descMaxW) cur = candidate
                    else {
                        if (cur.isNotEmpty()) lines += cur
                        cur = word
                    }
                }
                if (cur.isNotEmpty()) lines += cur
                lines
            } else {
                emptyList()
            }
            val descLineH = (gui.guiFont.lineHeight * 1.3).toInt()
            val descH = descLines.size * descLineH
            val tgH = 18
            val toggleH = tgH + 8
            val staticH = backBtnH + nameH + descH + 8 + toggleH
            val entries = if (detailMod != null) gui.configEntries(detailMod) else emptyList()
            var entriesH = 0
            for (entry in entries) {
                entriesH += gui.ENT_H
                if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) entriesH += gui.ENT_H
                if (entry is IntRangeEntry || entry is FloatRangeEntry) entriesH += gui.ENT_H
            }
            gui.sidebarConfigScrollMax = (entriesH - (bodyH - staticH)).coerceAtLeast(0).toFloat()
            if (mouseX in bodyX.toDouble()..(bodyX + bodyW).toDouble() && mouseY in bodyY.toDouble()..(bodyY + bodyH).toDouble()) {
                gui.sidebarConfigScroll = (gui.sidebarConfigScroll - scrollY * 32f)
                    .coerceIn(0.0, gui.sidebarConfigScrollMax.toDouble())
                    .toFloat()
                return true
            }
        }

        if (gui.sidebarTab == 0 && gui.sidebarDetailMod == null) {
            val pw = 480
            val ph = 320
            val px = gui.sidebarPaneX
            val py = gui.sidebarPaneY
            val catW = 110
            val contentX = px + catW
            val contentW = pw - catW
            val logoH = 24
            val tabH = 24
            val bodyX = contentX
            val bodyY = py + logoH + tabH
            val bodyW = contentW
            val bodyH = ph - logoH - tabH
            if (mouseX in bodyX.toDouble()..(bodyX + bodyW).toDouble() && mouseY in bodyY.toDouble()..(bodyY + bodyH).toDouble()) {
                gui.sidebarScroll = (gui.sidebarScroll - scrollY * 32f)
                    .coerceIn(0.0, gui.sidebarScrollMax.toDouble())
                    .toFloat()
                return true
            }
        }

        return false
    }

    fun handleMouseClick(gui: ClickGui, mx: Int, my: Int, btn: Int): Boolean {
        val pw = 480
        val ph = 320
        val px2 = gui.sidebarPaneX
        val py2 = gui.sidebarPaneY
        val tabH = 24
        val catW = 110
        val contentX2 = px2 + catW
        val contentW2 = pw - catW

        val logoH = 24
        if (mx in px2 until px2 + pw && my in py2 until py2 + logoH) {
            if (btn == 0) {
                gui.draggingSidebar = true
                gui.sidebarDragOffX = mx - px2
                gui.sidebarDragOffY = my - py2
            }
            return true
        }
        if (mx in px2 until px2 + pw && my in py2 + logoH until py2 + logoH + tabH) {
            if (btn == 0) {
                val tabW = pw / 2
                val ti = (mx - px2) / tabW
                gui.sidebarTab = ti.coerceIn(0, 1)
                gui.sidebarDetailMod = null
            }
            return true
        }
        if (gui.sidebarTab == 0 && mx in px2 until contentX2 && my in py2 + logoH + tabH until py2 + ph) {
            if (btn == 0) {
                var cy = py2 + logoH + tabH + 6
                for (cat in me.ghluka.medved.module.Module.Category.entries) {
                    if (my in cy until cy + 18) {
                        gui.selectedCategory = cat
                        gui.sidebarDetailMod = null
                        return true
                    }
                    cy += 20
                }
            }
            return true
        }
        val bodyX = if (gui.sidebarTab == 0) contentX2 else px2
        val bodyW2 = if (gui.sidebarTab == 0) contentW2 else pw
        if (mx in bodyX until px2 + pw && my in py2 + logoH + tabH until py2 + ph) {
            val bodyY = py2 + logoH + tabH
            val bodyH = ph - logoH - tabH - 12

            if (gui.sidebarTab == 0) {
                val detailMod = gui.sidebarDetailMod
                if (detailMod != null) {
                    val headerY = bodyY + 6
                    val backBtnH = 18
                    val nameH = gui.guiFont.lineHeight + 4
                    val descMaxW = bodyW2 - 80
                    val descLines = if (detailMod.description.isBlank()) emptyList() else {
                        val words = detailMod.description.split(" ")
                        val lines = mutableListOf<String>()
                        var cur = ""
                        for (word in words) {
                            val candidate = if (cur.isEmpty()) word else "$cur $word"
                            if (gui.guiFont.width(gui.styled(candidate)) <= descMaxW) cur = candidate
                            else {
                                if (cur.isNotEmpty()) lines += cur
                                cur = word
                            }
                        }
                        if (cur.isNotEmpty()) lines += cur
                        lines
                    }
                    val descLineH = (gui.guiFont.lineHeight * 1.3).toInt()
                    val descH = descLines.size * descLineH
                    val tgH = 18
                    val toggleH = tgH + 8
                    val staticH = backBtnH + nameH + descH + 8 + toggleH
                    val entries = gui.configEntries(detailMod)
                    var entriesH = 0
                    for (entry in entries) {
                        entriesH += gui.ENT_H
                        if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) entriesH += gui.ENT_H
                        if (entry is IntRangeEntry || entry is FloatRangeEntry) entriesH += gui.ENT_H
                    }
                    gui.sidebarConfigScrollMax = (entriesH - (bodyH - staticH)).coerceAtLeast(0).toFloat()
                    if (gui.sidebarConfigScroll > gui.sidebarConfigScrollMax) gui.sidebarConfigScroll = gui.sidebarConfigScrollMax
                    if (gui.sidebarConfigScroll < 0f) gui.sidebarConfigScroll = 0f
                    var ey = headerY
                    if (my in ey until ey + backBtnH && mx in bodyX + 4 until bodyX + 4 + 48) {
                        gui.sidebarDetailMod = null
                        return true
                    }
                    ey += backBtnH
                    ey += nameH
                    ey += descH + 8
                    val tgY = ey
                    if (my in tgY until tgY + tgH && mx in bodyX + 8 until bodyX + 8 + 60) {
                        if (btn == 0 && !detailMod.isProtected) detailMod.toggle()
                        return true
                    }
                    ey += toggleH
                    val configRegionTop = ey
                    val configRegionBottom = bodyY + 6 + (ph - logoH - tabH)
                    var entryY = configRegionTop - gui.sidebarConfigScroll.toInt()
                    for (entry in entries) {
                        if (entryY + gui.ENT_H > configRegionBottom) break
                        if (entryY + gui.ENT_H >= configRegionTop) {
                            if (my in entryY until entryY + gui.ENT_H) {
                                if (entry is me.ghluka.medved.config.entry.HudEditEntry && detailMod is me.ghluka.medved.module.HudModule) {
                                    net.minecraft.client.Minecraft.getInstance().gui.setScreen(me.ghluka.medved.gui.HudEditorScreen(detailMod, gui))
                                } else {
                                    gui.handleEntryClick(entry, bodyX + 4, entryY, bodyW2 - 8, mx, btn)
                                }
                                return true
                            }
                        }
                        entryY += gui.ENT_H
                        if (entry is IntEntry || entry is FloatEntry || entry is DoubleEntry) {
                            if (entryY + gui.ENT_H > configRegionBottom) break
                            if (entryY + gui.ENT_H >= configRegionTop) {
                                if (my in entryY until entryY + gui.ENT_H) {
                                    gui.handleNumericBarClick(entry, bodyX + 4, entryY, bodyW2 - 8, mx, btn)
                                    return true
                                }
                            }
                            entryY += gui.ENT_H
                        }
                        if (entry is IntRangeEntry) {
                            if (entryY + gui.ENT_H > configRegionBottom) break
                            if (entryY + gui.ENT_H >= configRegionTop) {
                                if (gui.handleRangeClick(entry, bodyX + 6, entryY, bodyW2 - 8, mx, my)) return true
                            }
                            entryY += gui.ENT_H
                        }
                        if (entry is FloatRangeEntry) {
                            if (entryY + gui.ENT_H > configRegionBottom) break
                            if (entryY + gui.ENT_H >= configRegionTop) {
                                if (gui.handleFloatRangeClick(entry, bodyX + 6, entryY, bodyW2 - 8, mx, my)) return true
                            }
                            entryY += gui.ENT_H
                        }
                        if (entry == gui.expandedColorEntry && entry is me.ghluka.medved.config.entry.ColorEntry) {
                            gui.colorPickerX = bodyX + 6
                            gui.colorPickerY = entryY
                            gui.colorPickerW = bodyW2 - 8
                        }
                    }
                } else {
                    val mods = gui.selectedCategory?.let { me.ghluka.medved.module.ModuleManager.getByCategory(it) } ?: emptyList()
                    val lineH = gui.guiFont.lineHeight
                    val cardPad = 4
                    val nameTopPad = 8
                    val nameBottomPad = 8
                    val descLineSpacingF = 1.3f
                    val cardList = mods.map { mod ->
                        val descMaxW = bodyW2 - 80
                        val descLines = if (mod.description.isBlank()) emptyList() else {
                            val words = mod.description.split(" ")
                            val lines = mutableListOf<String>()
                            var cur = ""
                            for (word in words) {
                                val candidate = if (cur.isEmpty()) word else "$cur $word"
                                if (gui.guiFont.width(gui.styled(candidate)) <= descMaxW) cur = candidate
                                else {
                                    if (cur.isNotEmpty()) lines += cur
                                    cur = word
                                }
                            }
                            if (cur.isNotEmpty()) lines += cur
                            lines
                        }
                        val descLineSpacing = (lineH * descLineSpacingF).toInt()
                        val cardH = nameTopPad + lineH + (if (descLines.isEmpty()) 0 else descLines.size * descLineSpacing + nameBottomPad) + 8
                        Triple(mod, descLines, cardH)
                    }
                    var my2 = bodyY + 6 - gui.sidebarScroll.toInt()
                    for ((mod, _, cardH) in cardList) {
                        if (my2 + cardH < bodyY + 6) {
                            my2 += cardH + cardPad
                            continue
                        }
                        if (my2 > bodyY + 6 + bodyH) break
                        if (my in my2 until my2 + cardH) {
                            val tgX = bodyX + bodyW2 - 44
                            val tgY = my2 + cardH / 2 - 6
                            if (btn == 0 && mx in tgX until tgX + 40 && my in tgY until tgY + 12) {
                                if (!mod.isProtected) mod.toggle()
                            } else if (btn == 0) {
                                gui.sidebarDetailMod = mod
                            }
                            return true
                        }
                        my2 += cardH + cardPad
                    }
                }
            } else {
                var ey = bodyY + 6
                if (my in ey until ey + gui.ENT_H) {
                    gui.presetFieldActive = true
                    gui.draggingPresetField = true
                    val relX = mx - bodyX - 5
                    gui.presetField.apply {
                        cursor = posFromPixel(relX)
                        selAnchor = cursor
                        clampScroll(bodyW2 - 10)
                    }
                    return true
                }
                ey += gui.ENT_H
                if (my in ey until ey + gui.MOD_H) {
                    val btnW2 = bodyW2 / 3
                    val name = gui.presetField.text.ifBlank { "default" }
                    when {
                        mx in bodyX until bodyX + btnW2 -> {
                            me.ghluka.medved.config.ConfigManager.savePreset(name)
                            me.ghluka.medved.util.NotificationManager.show("Config Saved", name)
                        }
                        mx in bodyX + btnW2 until bodyX + btnW2 * 2 -> {
                            val exists = name in me.ghluka.medved.config.ConfigManager.listPresets()
                            me.ghluka.medved.config.ConfigManager.loadPreset(name)
                            if (exists) me.ghluka.medved.util.NotificationManager.show("Config Loaded", name)
                            else me.ghluka.medved.util.NotificationManager.show("Not Found", name)
                        }
                        else -> me.ghluka.medved.config.ConfigManager.openPresetFolder()
                    }
                    return true
                }
                ey += gui.MOD_H
                for (preset in me.ghluka.medved.config.ConfigManager.listPresets()) {
                    if (my in ey until ey + gui.ENT_H) {
                        gui.presetField.set(preset)
                        gui.presetField.clampScroll(bodyW2 - 10)
                        gui.presetNameBuffer = preset
                        return true
                    }
                    ey += gui.ENT_H
                }
            }
            return true
        }

        return true
    }
}
