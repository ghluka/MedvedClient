package me.ghluka.medved.gui.ui

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

object HudUiResources {
    private val resourcePaths = listOf(
        "ui/hud/targethud.xml",
    )
    private var cached: UiTemplateSet? = null

    fun templates(): UiTemplateSet {
        cached?.let { return it }
        return resourcePaths
            .map(::loadTemplates)
            .reduce { combined, templates -> combined.mergedWith(templates) }
            .also { it.validate("HUD XML resources") }
            .also { cached = it }
    }

    fun reload() {
        cached = null
    }

    private fun loadTemplates(path: String): UiTemplateSet {
        val id = Identifier.fromNamespaceAndPath("medved", path)
        try {
            val resource = Minecraft.getInstance().resourceManager.getResource(id).orElseThrow {
                IllegalStateException("Missing HUD XML resource: $id")
            }
            return resource.open().bufferedReader(Charsets.UTF_8).use {
                UiXml.parseTemplates(it.readText())
            }
        } catch (error: Exception) {
            throw IllegalStateException("Failed to load HUD XML resource '$path'", error)
        }
    }
}
