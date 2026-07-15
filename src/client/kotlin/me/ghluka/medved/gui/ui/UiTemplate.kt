package me.ghluka.medved.gui.ui

class UiTemplateSet(private val templates: Map<String, UiNode>) {
    internal val all: Map<String, UiNode> get() = templates

    fun template(name: String): UiNode =
        templates[name] ?: error("Unknown UI template '$name'")

    fun mergedWith(other: UiTemplateSet): UiTemplateSet {
        val duplicates = templates.keys.intersect(other.templates.keys)
        if (duplicates.isNotEmpty()) error("Duplicate UI template(s): ${duplicates.sorted().joinToString()}")
        return UiTemplateSet(templates + other.templates)
    }

    fun instantiate(
        name: String,
        values: Map<String, String> = emptyMap(),
        slots: Map<String, List<UiNode>> = emptyMap(),
    ): UiNode {
        return template(name).expand(values, slots)
    }

    operator fun contains(name: String): Boolean = name in templates

    fun validate(name: String = "UI templates") {
        UiXmlValidator.validateTemplates(name, this)
    }
}

private fun UiNode.expand(values: Map<String, String>, slots: Map<String, List<UiNode>>): UiNode {
    if (type == "slot") {
        val name = attributes["name"] ?: "default"
        return UiNode(
            type = "box",
            axis = axis,
            width = width,
            height = height,
            style = style,
            attributes = attributes,
            children = slots[name] ?: emptyList(),
        )
    }

    val resolvedAttributes = attributes.mapValues { (_, value) -> value.replaceTokens(values) }
    return UiNode(
        type = type,
        id = id?.replaceTokens(values),
        text = text?.replaceTokens(values),
        axis = axis,
        width = resolvedAttributes["w"]?.toUiSizeOrNull() ?: resolvedAttributes["width"]?.toUiSizeOrNull() ?: width,
        height = resolvedAttributes["h"]?.toUiSizeOrNull() ?: resolvedAttributes["height"]?.toUiSizeOrNull() ?: height,
        x = resolvedAttributes["x"]?.toFloatOrNull() ?: x,
        y = resolvedAttributes["y"]?.toFloatOrNull() ?: y,
        style = style,
        attributes = resolvedAttributes,
        children = children.flatMap { child ->
            if (child.type == "slot") {
                val slotName = child.attributes["name"] ?: "default"
                slots[slotName] ?: emptyList()
            } else {
                listOf(child.expand(values, slots))
            }
        },
    )
}

private fun String.replaceTokens(values: Map<String, String>): String =
    Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*}}""").replace(this) { match ->
        values[match.groupValues[1]] ?: ""
    }

private fun String.toUiSizeOrNull(): UiSize? =
    when (lowercase()) {
        "", "wrap", "auto" -> UiSize.Wrap
        "fill", "match", "*" -> UiSize.Fill
        else -> removeSuffix("px").toFloatOrNull()?.let(UiSize::Px)
    }
