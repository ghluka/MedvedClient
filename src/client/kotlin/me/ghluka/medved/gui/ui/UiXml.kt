package me.ghluka.medved.gui.ui

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

object UiXml {
    fun parse(source: String): UiDocument {
        val document = parseDom(source)
        return UiDocument(parseElement(document.documentElement))
    }

    fun parseNode(source: String): UiNode {
        val document = parseDom(source)
        return parseElement(document.documentElement)
    }

    fun parseTemplates(source: String): UiTemplateSet {
        val document = parseDom(source)
        val root = document.documentElement
        val templates = mutableMapOf<String, UiNode>()
        collectTemplates(root, templates)
        return UiTemplateSet(templates)
    }

    private fun parseDom(source: String): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isIgnoringComments = true
            isCoalescing = true
        }
        return factory
            .newDocumentBuilder()
            .parse(ByteArrayInputStream(source.trimIndent().toByteArray(Charsets.UTF_8)))
    }

    private fun collectTemplates(element: Element, templates: MutableMap<String, UiNode>) {
        if (element.tagName == "template") {
            val name = element.getAttribute("name").takeIf { it.isNotBlank() }
                ?: error("Template is missing a name")
            if (name in templates) error("Duplicate UI template '$name'")
            val childElements = element.childElements()
            templates[name] = when (childElements.size) {
                0 -> UiNode("box")
                1 -> parseElement(childElements.single())
                else -> UiNode(
                    type = "box",
                    axis = UiAxis.VERTICAL,
                    style = UiStyle(gap = 4f),
                    children = childElements.map(::parseElement),
                )
            }
            return
        }

        element.childElements().forEach { collectTemplates(it, templates) }
    }

    internal fun parseElement(element: Element): UiNode {
        val attrs = buildMap {
            val raw = element.attributes
            for (i in 0 until raw.length) {
                val attr = raw.item(i)
                put(attr.nodeName, attr.nodeValue)
            }
        }

        val children = buildList {
            val nodes = element.childNodes
            for (i in 0 until nodes.length) {
                val child = nodes.item(i)
                if (child is Element) add(parseElement(child))
            }
        }

        val textAttr = attrs["text"]
        val inlineText = element.childNodes.run {
            (0 until length)
                .map { item(it) }
                .firstOrNull { it.nodeType == org.w3c.dom.Node.TEXT_NODE }
                ?.nodeValue
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }

        return UiNode(
            type = element.tagName,
            id = attrs["id"],
            text = textAttr ?: inlineText,
            axis = parseAxis(attrs["axis"] ?: attrs["direction"]),
            width = parseSize(attrs["w"] ?: attrs["width"]),
            height = parseSize(attrs["h"] ?: attrs["height"]),
            x = attrs["x"]?.toFloatOrNull(),
            y = attrs["y"]?.toFloatOrNull(),
            style = parseStyle(attrs, element.tagName),
            attributes = attrs,
            children = children,
        )
    }

    private fun parseAxis(value: String?): UiAxis =
        when (value?.lowercase()) {
            "x", "row", "horizontal" -> UiAxis.HORIZONTAL
            else -> UiAxis.VERTICAL
        }

    private fun parseSize(value: String?): UiSize =
        when (value?.lowercase()) {
            null, "", "wrap", "auto" -> UiSize.Wrap
            "fill", "match", "*" -> UiSize.Fill
            else -> value
                .takeUnless { it.contains("{{") }
                ?.removeSuffix("px")
                ?.toFloatOrNull()
                ?.let(UiSize::Px)
                ?: UiSize.Wrap
        }

    private fun parseStyle(attrs: Map<String, String>, tag: String): UiStyle {
        val base = when (tag.lowercase()) {
            "panel" -> UiTheme.panel
            "row" -> UiTheme.row
            "button" -> UiTheme.button
            else -> UiStyle()
        }

        val parsed = UiStyle(
            background = parseStyleColor(attrs["bg"]),
            foreground = parseStyleColor(attrs["fg"]),
            hoverBackground = parseStyleColor(attrs["hoverBg"]),
            activeBackground = parseStyleColor(attrs["activeBg"]),
            borderColor = parseStyleColor(attrs["border"]),
            borderWidth = attrs["borderWidth"]?.toFloatOrNull() ?: 0f,
            radius = attrs["radius"]?.toFloatOrNull() ?: 0f,
            padding = attrs["padding"]?.let(::parseInsets) ?: UiInsets.Zero,
            gap = attrs["gap"]?.toFloatOrNull() ?: 0f,
            align = when (attrs["align"]?.lowercase()) {
                "center" -> UiTextAlign.CENTER
                "right", "end" -> UiTextAlign.RIGHT
                else -> UiTextAlign.LEFT
            },
            clip = attrs["clip"]?.toBooleanStrictOrNull() ?: false,
        )

        return base.merge(parsed)
    }

    private fun parseStyleColor(value: String?): Int? =
        value?.takeUnless { it.contains("{{") }?.let(::parseColor)

    private fun parseInsets(value: String): UiInsets {
        val parts = value.split(',', ' ').filter { it.isNotBlank() }.map { it.toFloat() }
        return when (parts.size) {
            1 -> UiInsets.all(parts[0])
            2 -> UiInsets.xy(parts[0], parts[1])
            4 -> UiInsets(parts[3], parts[0], parts[1], parts[2])
            else -> UiInsets.Zero
        }
    }

    private fun parseColor(value: String): Int {
        val clean = value.removePrefix("#")
        val argb = when (clean.length) {
            6 -> "FF$clean"
            8 -> clean
            else -> error("Expected #RRGGBB or #AARRGGBB, got $value")
        }
        return argb.toLong(16).toInt()
    }

    private fun Element.childElements(): List<Element> {
        val result = mutableListOf<Element>()
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val child = nodes.item(i)
            if (child is Element) result += child
        }
        return result
    }
}
