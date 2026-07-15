package me.ghluka.medved.gui.ui

import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier

object ClickGuiUiResources {
    private val resourcePaths = listOf(
        "ui/clickgui/designs/dropdown.xml",
        "ui/clickgui/designs/sidebar.xml",
        "ui/clickgui/entries.xml",
        "ui/clickgui/enum.xml",
        "ui/clickgui/overlays.xml",
    )
    private val resourceIds = resourcePaths.map { Identifier.fromNamespaceAndPath("medved", it) }
    private var cached: UiTemplateSet? = null

    fun templates(): UiTemplateSet {
        cached?.let { return it }
        val loaded = loadTemplates()
        cached = loaded
        return loaded
    }

    fun reload() {
        cached = null
    }

    private fun loadTemplates(): UiTemplateSet {
        try {
            return resourceIds
                .map { resourceId ->
                    val resource = Minecraft.getInstance().resourceManager.getResource(resourceId).orElseThrow {
                        IllegalStateException("Missing Click GUI XML resource: $resourceId")
                    }
                    resource.open().bufferedReader(Charsets.UTF_8).use { UiXml.parseTemplates(it.readText()) }
                }
                .reduce { combined, templates -> combined.mergedWith(templates) }
                .also { it.validate("Click GUI XML resources") }
        } catch (error: Exception) {
            throw IllegalStateException(
                "Failed to load Click GUI XML resources (${resourcePaths.joinToString()}). " +
                    "The Click GUI no longer has embedded fallback templates; fix the XML resource error instead.",
                error,
            )
        }
    }
}
