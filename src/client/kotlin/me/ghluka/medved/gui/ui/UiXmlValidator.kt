package me.ghluka.medved.gui.ui

internal object UiXmlValidator {
    private val tokenPattern = Regex("""\{\{\s*([A-Za-z0-9_.-]+)\s*}}""")
    private val primitiveRequirements = mapOf(
        "for" to listOf("each"),
        "if" to listOf("condition"),
        "component" to listOf("name"),
        "slot" to listOf("name"),
        "toggle" to listOf("on"),
        "slider" to listOf("progress"),
        "range-slider" to listOf("low", "high"),
        "text-input" to listOf("text"),
        "swatch" to listOf("color"),
        "color-map" to listOf("hue", "saturation", "value"),
        "hue-bar" to listOf("hue"),
        "alpha-bar" to listOf("color", "alpha"),
        "item-icon" to listOf("item"),
    )

    fun validateTemplates(sourceName: String, templates: UiTemplateSet) {
        val errors = mutableListOf<String>()
        for ((name, node) in templates.all) {
            validateTemplateNode(
                node = node,
                path = "template '$name'",
                templates = templates,
                errors = errors,
            )
        }
        throwIfNeeded(sourceName, errors)
    }

    fun validateDocument(sourceName: String, document: UiDocument) {
        val errors = mutableListOf<String>()
        validateExpandedNode(document.root, "document root", mutableMapOf(), errors)
        throwIfNeeded(sourceName, errors)
    }

    private fun validateTemplateNode(
        node: UiNode,
        path: String,
        templates: UiTemplateSet,
        errors: MutableList<String>,
    ) {
        validateRequiredAttributes(node, path, errors)
        validateTemplateReferences(node, path, templates, errors)
        validateSizeAttributes(node, path, errors)
        validateInlineTokens(node, path, errors, allowTokens = true)

        node.children.forEachIndexed { index, child ->
            validateTemplateNode(child, "$path > ${child.type}[$index]", templates, errors)
        }
    }

    private fun validateExpandedNode(
        node: UiNode,
        path: String,
        ids: MutableMap<String, String>,
        errors: MutableList<String>,
    ) {
        validateRequiredAttributes(node, path, errors)
        validateSizeAttributes(node, path, errors)
        validateInlineTokens(node, path, errors, allowTokens = false)

        val clickId = node.attributes["click"] ?: node.id
        if (clickId != null) {
            val previous = ids.putIfAbsent(clickId, path)
            if (previous != null) errors += "Duplicate interactive id '$clickId' at $path, already used at $previous"
        }

        node.children.forEachIndexed { index, child ->
            validateExpandedNode(child, "$path > ${child.type}[$index]", ids, errors)
        }
    }

    private fun validateRequiredAttributes(node: UiNode, path: String, errors: MutableList<String>) {
        primitiveRequirements[node.type].orEmpty().forEach { attr ->
            val value = node.attributes[attr]
            if (value == null || (attr != "text" && value.isBlank())) {
                errors += "$path <${node.type}> is missing required '$attr'"
            }
        }
        if (node.type == "scroll" && node.attributes["scrollKey"].isNullOrBlank() && node.id.isNullOrBlank()) {
            errors += "$path <scroll> needs either 'scrollKey' or 'id'"
        }
    }

    private fun validateTemplateReferences(
        node: UiNode,
        path: String,
        templates: UiTemplateSet,
        errors: MutableList<String>,
    ) {
        if (node.type != "component") return
        val name = node.attributes["name"] ?: return
        if (!containsToken(name) && name !in templates) {
            errors += "$path references unknown component template '$name'"
        }
    }

    private fun validateSizeAttributes(node: UiNode, path: String, errors: MutableList<String>) {
        listOf("w", "width", "h", "height").forEach { attr ->
            val value = node.attributes[attr] ?: return@forEach
            if (!containsToken(value) && !isValidSize(value)) {
                errors += "$path has invalid size '$attr=$value'"
            }
        }
        listOf("x", "y", "gap", "radius", "borderWidth", "scrollAmount", "scrollY", "textScale").forEach { attr ->
            val value = node.attributes[attr] ?: return@forEach
            if (!containsToken(value) && value.toFloatOrNull() == null) {
                errors += "$path has invalid number '$attr=$value'"
            }
        }
        node.attributes["corners"]?.let { value ->
            if (!containsToken(value) && !isValidCorners(value)) {
                errors += "$path has invalid corners='$value'"
            }
        }
    }

    private fun validateInlineTokens(
        node: UiNode,
        path: String,
        errors: MutableList<String>,
        allowTokens: Boolean,
    ) {
        fun check(label: String, value: String?) {
            if (!allowTokens && value != null && containsToken(value)) {
                errors += "$path has unresolved token in $label='$value'"
            }
        }

        check("id", node.id)
        check("text", node.text)
        node.attributes.forEach { (key, value) -> check(key, value) }
    }

    private fun isValidSize(value: String): Boolean =
        when (value.removeSuffix("px").lowercase()) {
            "", "wrap", "auto", "fill", "match", "*" -> true
            else -> value.removeSuffix("px").toFloatOrNull() != null
        }

    private fun isValidCorners(value: String): Boolean {
        val normalized = value.lowercase()
        if (normalized in setOf("", "all", "none", "top", "bottom", "bot", "left", "right")) return true
        return normalized.split(',', ' ', '|')
            .filter { it.isNotBlank() }
            .all { it in setOf("tl", "tr", "bl", "br", "top-left", "top-right", "bottom-left", "bottom-right") }
    }

    private fun containsToken(value: String): Boolean =
        tokenPattern.containsMatchIn(value)

    private fun throwIfNeeded(sourceName: String, errors: List<String>) {
        if (errors.isNotEmpty()) {
            error(buildString {
                appendLine("$sourceName has ${errors.size} UI validation error(s):")
                errors.forEach { appendLine("- $it") }
            })
        }
    }
}
