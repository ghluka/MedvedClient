package me.ghluka.medved.module

import me.ghluka.medved.config.entry.HudEditEntry
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

abstract class HudModule(name: String, description: String) :
    Module(name, description, Module.Category.HUD) {

    val editButton = register(HudEditEntry())
    val hudX = float("hud_x", 0.0f, 0.0f, 1.0f).also { it.visibleWhen = { false } }
    val hudY = float("hud_y", 0.0f, 0.0f, 1.0f).also { it.visibleWhen = { false } }
    val hudScale = float("hud_scale", 1.0f, 0.25f, 4.0f).also { it.visibleWhen = { false } }

    abstract fun renderHudElement(g: GuiGraphicsExtractor)
    abstract fun hudWidth(): Int
    abstract fun hudHeight(): Int

    override fun onHudRender(extractor: GuiGraphicsExtractor, delta: DeltaTracker) {
        val mc = Minecraft.getInstance()
        val px = (hudX.value * mc.window.guiScaledWidth).toInt()
        val py = (hudY.value * mc.window.guiScaledHeight).toInt()
        val sc = hudScale.value
        extractor.pose().pushMatrix()
        extractor.pose().translate(px.toFloat(), py.toFloat())
        if (sc != 1.0f) extractor.pose().scale(sc, sc)
        renderHudElement(extractor)
        extractor.pose().popMatrix()
    }
}
