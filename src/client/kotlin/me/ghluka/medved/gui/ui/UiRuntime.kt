package me.ghluka.medved.gui.ui

class UiRuntime(private val renderer: UiRenderer) {
    fun layout(document: UiDocument, viewport: UiRect) {
        measure(document.root, viewport.width, viewport.height)
        place(document.root, viewport.x, viewport.y, viewport.width, viewport.height)
    }

    fun render(document: UiDocument, mouseX: Float, mouseY: Float) {
        renderNode(document.root, mouseX, mouseY, emptyList())
    }

    fun mouseClicked(document: UiDocument, x: Float, y: Float, button: Int): Boolean {
        val hit = hitTest(document.root, x, y) ?: return false
        val id = hit.attributes["click"] ?: hit.id ?: return false
        return document.click(id, UiPointerEvent(x, y, button, hit))
    }

    fun mouseScrolled(
        document: UiDocument,
        x: Float,
        y: Float,
        scrollY: Float,
        onScroll: (key: String, value: Float, max: Float) -> Unit,
    ): Boolean {
        val hit = scrollHitTest(document.root, x, y) ?: return false
        val key = hit.attributes["scrollKey"] ?: hit.id ?: return false
        val amount = hit.attributes["scrollAmount"]?.toFloatOrNull() ?: 32f
        val current = hit.scrollOffset()
        val next = (current - scrollY * amount).coerceIn(0f, hit.scrollMax)
        onScroll(key, next, hit.scrollMax)
        return hit.scrollMax > 0f
    }

    private fun measure(node: UiNode, availableW: Float, availableH: Float): UiRect {
        val pad = node.style.padding
        val childAvailableW = when (val size = node.width) {
            UiSize.Fill -> availableW
            is UiSize.Px -> size.value
            UiSize.Wrap -> availableW
        }
        val childAvailableH = when (val size = node.height) {
            UiSize.Fill -> availableH
            is UiSize.Px -> size.value
            UiSize.Wrap -> availableH
        }
        val contentW = (childAvailableW - pad.left - pad.right).coerceAtLeast(0f)
        val contentH = (childAvailableH - pad.top - pad.bottom).coerceAtLeast(0f)

        val measuredChildren = node.children.map { measure(it, contentW, contentH) }
        val flowChildren = node.children.zip(measuredChildren)
            .filterNot { (child, _) -> child.attributes["overlay"] == "true" }
        val nodeFont = node.attributes["font"]
        val textW = node.text?.let { renderer.textWidth(it, nodeFont) } ?: 0f
        val textH = if (node.text != null) renderer.fontHeight(nodeFont) else 0f
        val gapTotal = node.style.gap * (flowChildren.size - 1).coerceAtLeast(0)

        val childrenW = when (node.axis) {
            UiAxis.VERTICAL -> flowChildren.maxOfOrNull { it.second.width } ?: 0f
            UiAxis.HORIZONTAL -> flowChildren.sumOf { it.second.width.toDouble() }.toFloat() + gapTotal
        }
        val childrenH = when (node.axis) {
            UiAxis.VERTICAL -> flowChildren.sumOf { it.second.height.toDouble() }.toFloat() + gapTotal
            UiAxis.HORIZONTAL -> flowChildren.maxOfOrNull { it.second.height } ?: 0f
        }
        val absoluteChildrenH = flowChildren
            .maxOfOrNull { (child, bounds) -> (child.y ?: 0f) + bounds.height }
            ?: 0f

        val wrapW = maxOf(textW, childrenW) + pad.left + pad.right
        val wrapH = maxOf(textH, childrenH) + pad.top + pad.bottom

        val w = when (val size = node.width) {
            UiSize.Fill -> availableW
            is UiSize.Px -> size.value
            UiSize.Wrap -> wrapW
        }
        val h = when (val size = node.height) {
            UiSize.Fill -> availableH
            is UiSize.Px -> size.value
            UiSize.Wrap -> wrapH
        }

        node.bounds = UiRect(0f, 0f, w.coerceAtLeast(0f), h.coerceAtLeast(0f))
        node.scrollContentHeight = maxOf(childrenH, absoluteChildrenH) + pad.top + pad.bottom
        node.scrollMax = if (node.type == "scroll") {
            (node.scrollContentHeight - node.bounds.height).coerceAtLeast(0f)
        } else {
            0f
        }
        return node.bounds
    }

    private fun place(
        node: UiNode,
        x: Float,
        y: Float,
        availableW: Float,
        availableH: Float,
        translateX: Float = 0f,
        translateY: Float = 0f,
        forceTranslate: Boolean = false,
    ) {
        val w = when (val size = node.width) {
            UiSize.Fill -> availableW
            is UiSize.Px -> size.value
            UiSize.Wrap -> node.bounds.width.coerceAtMost(availableW)
        }
        val h = when (val size = node.height) {
            UiSize.Fill -> availableH
            is UiSize.Px -> size.value
            UiSize.Wrap -> node.bounds.height
        }

        val relative = node.attributes["relative"] == "true"
        val placedX = if (relative) x + (node.x ?: 0f) else node.x ?: x
        val placedY = if (relative) y + (node.y ?: 0f) else node.y ?: y
        val usesAbsolutePosition = !relative && (node.x != null || node.y != null)
        val translateThisNode = forceTranslate || usesAbsolutePosition
        node.bounds = UiRect(
            placedX + if (translateThisNode) translateX else 0f,
            placedY + if (translateThisNode) translateY else 0f,
            w,
            h,
        )
        node.scrollMax = if (node.type == "scroll") {
            (node.scrollContentHeight - node.bounds.height).coerceAtLeast(0f)
        } else {
            0f
        }
        val content = node.bounds.inset(node.style.padding)
        var cursorX = content.x
        var cursorY = content.y
        val childTranslateY = translateY + if (node.type == "scroll") {
            -node.scrollOffset().coerceAtLeast(0f)
        } else {
            0f
        }
        val horizontalFillW = if (node.axis == UiAxis.HORIZONTAL) {
            val flowChildren = node.children.filterNot { it.attributes["overlay"] == "true" }
            val gapTotal = node.style.gap * (flowChildren.size - 1).coerceAtLeast(0)
            val fixedW = flowChildren.sumOf { child ->
                when (val size = child.width) {
                    UiSize.Fill -> 0.0
                    is UiSize.Px -> size.value.toDouble()
                    UiSize.Wrap -> child.bounds.width.toDouble()
                }
            }.toFloat()
            val fillCount = flowChildren.count { it.width == UiSize.Fill }.coerceAtLeast(1)
            ((content.width - fixedW - gapTotal) / fillCount).coerceAtLeast(0f)
        } else {
            content.width
        }
        val verticalFillH = if (node.axis == UiAxis.VERTICAL) {
            val flowChildren = node.children.filterNot { it.attributes["overlay"] == "true" }
            val gapTotal = node.style.gap * (flowChildren.size - 1).coerceAtLeast(0)
            val fixedH = flowChildren.sumOf { child ->
                when (val size = child.height) {
                    UiSize.Fill -> 0.0
                    is UiSize.Px -> size.value.toDouble()
                    UiSize.Wrap -> child.bounds.height.toDouble()
                }
            }.toFloat()
            val fillCount = flowChildren.count { it.height == UiSize.Fill }.coerceAtLeast(1)
            ((content.height - fixedH - gapTotal) / fillCount).coerceAtLeast(0f)
        } else {
            content.height
        }

        for (child in node.children) {
            val overlay = child.attributes["overlay"] == "true"
            val childW = when (val size = child.width) {
                UiSize.Fill -> if (node.axis == UiAxis.HORIZONTAL) horizontalFillW else content.width
                is UiSize.Px -> size.value
                UiSize.Wrap -> if (node.axis == UiAxis.VERTICAL) {
                    child.bounds.width.coerceAtMost(content.width)
                } else {
                    child.bounds.width
                }
            }
            val childH = when (val size = child.height) {
                UiSize.Fill -> if (node.axis == UiAxis.VERTICAL) verticalFillH else content.height
                is UiSize.Px -> size.value
                UiSize.Wrap -> child.bounds.height
            }
            val childX = if (overlay) content.x else cursorX
            val childY = if (overlay) content.y else cursorY
            place(
                child,
                childX,
                childY,
                childW,
                childH,
                translateX = translateX,
                translateY = childTranslateY,
                forceTranslate = node.type == "scroll",
            )
            if (!overlay) {
                if (node.axis == UiAxis.VERTICAL) cursorY += child.bounds.height + node.style.gap
                else cursorX += child.bounds.width + node.style.gap
            }
        }

        if (node.type == "scroll") {
            val scrollOffset = node.scrollOffset().coerceAtLeast(0f)
            val contentTop = node.bounds.y + node.style.padding.top
            val contentBottom = node.children
                .filterNot { it.attributes["overlay"] == "true" }
                .mapNotNull { placedContentBottom(it)?.plus(scrollOffset) }
                .maxOrNull()
                ?: contentTop
            node.scrollContentHeight = (contentBottom - contentTop + node.style.padding.bottom).coerceAtLeast(0f)
            node.scrollMax = (node.scrollContentHeight - node.bounds.height).coerceAtLeast(0f)
        }
    }

    private fun placedContentBottom(node: UiNode): Float? {
        val childBottom = node.children
            .filterNot { it.attributes["overlay"] == "true" }
            .mapNotNull(::placedContentBottom)
            .maxOrNull()
        val ownBottom = node.bounds.bottom.takeIf { node.contributesOwnBoundsToScroll() }
        return listOfNotNull(ownBottom, childBottom).maxOrNull()
    }

    private fun UiNode.contributesOwnBoundsToScroll(): Boolean =
        attributes["each"] == null && attributes["condition"] == null

    private fun renderNode(node: UiNode, mouseX: Float, mouseY: Float, roundedClips: List<RoundedClip>) {
        val hovered = node.bounds.contains(mouseX, mouseY)
        val bg = backgroundColor(node, hovered)
        if (bg != null) {
            val inheritedCorners = inheritedCornerMask(node, roundedClips)
            val ownRadius = node.style.radius
            val radius = if (inheritedCorners != 0 && ownRadius <= 0f) inheritedRadius(node, roundedClips) else ownRadius
            val corners = if (ownRadius > 0f) cornerMask(node) or inheritedCorners else inheritedCorners
            renderer.roundedFill(node.bounds, bg, radius, corners)
        }
        val border = borderColor(node)
        if (border != null && node.style.borderWidth > 0f) {
            renderer.border(node.bounds, border, node.style.borderWidth, node.style.radius)
        }

        val clips = node.style.clip || node.type == "scroll"
        if (clips) renderer.clip(node.bounds)

        when (node.type) {
            "toggle" -> renderToggle(node)
            "slider" -> renderSlider(node)
            "range-slider" -> renderRangeSlider(node)
            "text-input" -> renderTextInput(node)
            "swatch" -> renderSwatch(node)
            "color-map" -> renderColorMap(node)
            "hue-bar" -> renderHueBar(node)
            "alpha-bar" -> renderAlphaBar(node)
            "item-icon" -> renderItemIcon(node)
            "dropdown-arrow" -> renderDropdownArrow(node)
        }

        if (node.type != "text-input") node.text?.let { text ->
            val nodeFont = node.attributes["font"]
            val color = foregroundColor(node)
            val textX = when (node.style.align) {
                UiTextAlign.LEFT -> node.bounds.x + node.style.padding.left
                UiTextAlign.CENTER -> node.bounds.x + (node.bounds.width - renderer.textWidth(text, nodeFont)) / 2f
                UiTextAlign.RIGHT -> node.bounds.right - node.style.padding.right - renderer.textWidth(text, nodeFont)
            }
            val textY = node.bounds.y + (node.bounds.height - renderer.fontHeight(nodeFont)) / 2f
            renderer.text(text, textX, textY, color, nodeFont)
        }

        val childRoundedClips = if (node.style.radius > 0f) {
            roundedClips + RoundedClip(node.bounds, node.style.radius, cornerMask(node))
        } else {
            roundedClips
        }
        node.children.forEach { renderNode(it, mouseX, mouseY, childRoundedClips) }

        if (node.type == "scroll") renderScrollBar(node)

        if (clips) renderer.unclip()
    }

    private fun renderToggle(node: UiNode) {
        val on = node.attributes["on"] == "true"
        val track = node.bounds
        val trackColor = color(node.attributes[if (on) "onBg" else "offBg"])
            ?: if (on) UiTheme.ACCENT else UiTheme.SURFACE
        val knobColor = color(node.attributes["knobBg"]) ?: UiTheme.TEXT
        renderer.fill(track, trackColor, track.height / 2f)

        val knobSize = (track.height - 4f).coerceAtLeast(2f)
        val knobX = if (on) track.right - knobSize - 2f else track.x + 2f
        renderer.fill(
            UiRect(knobX, track.y + 2f, knobSize, knobSize),
            knobColor,
            knobSize / 2f,
        )
    }

    private fun renderSlider(node: UiNode) {
        val progress = node.attributes["progress"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        val trackColor = color(node.attributes["trackBg"]) ?: UiTheme.SURFACE
        val fillColor = color(node.attributes["fillBg"]) ?: UiTheme.ACCENT
        val knobColor = color(node.attributes["knobBg"]) ?: UiTheme.TEXT
        val trackH = node.attributes["trackHeight"]?.toFloatOrNull() ?: 4f
        val knobSize = node.attributes["knobSize"]?.toFloatOrNull() ?: ClickGuiUiFactory.DEFAULT_SLIDER_KNOB_SIZE
        val trackInset = (node.attributes["trackInset"]?.toFloatOrNull() ?: ClickGuiUiFactory.DEFAULT_SLIDER_TRACK_INSET)
            .coerceAtLeast(0f)
        val track = UiRect(
            node.bounds.x + trackInset,
            node.bounds.y + (node.bounds.height - trackH) / 2f,
            (node.bounds.width - trackInset * 2f).coerceAtLeast(1f),
            trackH,
        )
        renderer.fill(track, trackColor, trackH / 2f)
        renderer.fill(track.copy(width = track.width * progress), fillColor, trackH / 2f)

        val knobX = track.x + track.width * progress - knobSize / 2f
        renderer.fill(
            UiRect(knobX, node.bounds.y + (node.bounds.height - knobSize) / 2f, knobSize, knobSize),
            knobColor,
            knobSize / 2f,
        )
    }

    private fun renderRangeSlider(node: UiNode) {
        val low = node.attributes["low"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        val high = node.attributes["high"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        val start = minOf(low, high)
        val end = maxOf(low, high)
        val trackColor = color(node.attributes["trackBg"]) ?: UiTheme.SURFACE
        val fillColor = color(node.attributes["fillBg"]) ?: UiTheme.ACCENT
        val knobColor = color(node.attributes["knobBg"]) ?: UiTheme.TEXT
        val trackH = node.attributes["trackHeight"]?.toFloatOrNull() ?: 4f
        val knobSize = node.attributes["knobSize"]?.toFloatOrNull() ?: ClickGuiUiFactory.DEFAULT_SLIDER_KNOB_SIZE
        val trackInset = (node.attributes["trackInset"]?.toFloatOrNull() ?: ClickGuiUiFactory.DEFAULT_SLIDER_TRACK_INSET)
            .coerceAtLeast(0f)
        val track = UiRect(
            node.bounds.x + trackInset,
            node.bounds.y + (node.bounds.height - trackH) / 2f,
            (node.bounds.width - trackInset * 2f).coerceAtLeast(1f),
            trackH,
        )
        renderer.fill(track, trackColor, trackH / 2f)
        renderer.fill(
            UiRect(track.x + track.width * start, track.y, track.width * (end - start), track.height),
            fillColor,
            trackH / 2f,
        )

        fun knob(progress: Float) {
            val knobX = track.x + track.width * progress - knobSize / 2f
            renderer.fill(
                UiRect(knobX, node.bounds.y + (node.bounds.height - knobSize) / 2f, knobSize, knobSize),
                knobColor,
                knobSize / 2f,
            )
        }
        knob(start)
        knob(end)
    }

    private fun renderTextInput(node: UiNode) {
        val text = node.text ?: ""
        val color = foregroundColor(node)
        val content = node.bounds.inset(node.style.padding)
        val scroll = node.attributes["scroll"]?.toFloatOrNull() ?: 0f
        val active = node.attributes["active"] == "true"
        renderer.clip(content)
        renderer.text(text, content.x - scroll, content.y + (content.height - renderer.fontHeight) / 2f, color)
        if (active) {
            val cursor = node.attributes["cursor"]?.toIntOrNull()?.coerceIn(0, text.length) ?: text.length
            val cursorX = content.x - scroll + renderer.textWidth(text.substring(0, cursor))
            renderer.fill(UiRect(cursorX, content.y + 1f, 1f, content.height - 2f), color)
        }
        renderer.unclip()
    }

    private fun renderSwatch(node: UiNode) {
        val swatchColor = color(node.attributes["color"]) ?: UiTheme.ACCENT
        renderer.fill(node.bounds, swatchColor, node.style.radius)
        borderColor(node)?.let { renderer.border(node.bounds, it, node.style.borderWidth.coerceAtLeast(1f), node.style.radius) }
    }

    private fun renderColorMap(node: UiNode) {
        val hue = node.attributes["hue"]?.toFloatOrNull() ?: 0f
        val selectedS = node.attributes["saturation"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 0f
        val selectedV = node.attributes["value"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        renderer.colorMap(node.bounds, hue)
        val selectorX = node.bounds.x + selectedS * (node.bounds.width - 1f)
        val selectorY = node.bounds.y + (1f - selectedV) * (node.bounds.height - 1f)
        val selector = UiRect(selectorX - 1f, selectorY - 1f, 3f, 3f)
        renderer.fill(selector, color(node.attributes["selectorBg"]) ?: UiTheme.TEXT)
    }

    private fun renderHueBar(node: UiNode) {
        val selectedHue = node.attributes["hue"]?.toFloatOrNull()?.coerceIn(0f, 360f) ?: 0f
        val height = node.bounds.height.toInt().coerceAtLeast(1)
        for (y in 0 until height) {
            val hue = y.toFloat() / (height - 1).coerceAtLeast(1) * 360f
            renderer.fill(UiRect(node.bounds.x, node.bounds.y + y, node.bounds.width, 1f), hsvToColor(hue, 1f, 1f))
        }
        val selectorY = node.bounds.y + selectedHue / 360f * (node.bounds.height - 1f)
        renderer.fill(UiRect(node.bounds.x - 1f, selectorY - 1f, node.bounds.width + 2f, 3f), color(node.attributes["selectorBg"]) ?: UiTheme.TEXT)
    }

    private fun renderAlphaBar(node: UiNode) {
        val alpha = node.attributes["alpha"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f
        val baseColor = color(node.attributes["color"]) ?: UiTheme.ACCENT
        renderChecker(node.bounds)
        val height = node.bounds.height.toInt().coerceAtLeast(1)
        for (y in 0 until height) {
            val t = y.toFloat() / (height - 1).coerceAtLeast(1)
            val argb = ((255f * (1f - t)).toInt().coerceIn(0, 255) shl 24) or (baseColor and 0x00FFFFFF)
            renderer.fill(UiRect(node.bounds.x, node.bounds.y + y, node.bounds.width, 1f), argb)
        }
        val selectorY = node.bounds.y + (1f - alpha) * (node.bounds.height - 1f)
        renderer.fill(UiRect(node.bounds.x - 1f, selectorY - 1f, node.bounds.width + 2f, 3f), color(node.attributes["selectorBg"]) ?: UiTheme.TEXT)
    }

    private fun renderItemIcon(node: UiNode) {
        renderer.item(node.attributes["item"].orEmpty(), node.bounds)
    }

    private fun renderDropdownArrow(node: UiNode) {
        val arrowColor = foregroundColor(node)
        val open = node.attributes["open"] == "true"
        val glyph = if (open) "\u25B4" else "\u25BE"
        val nodeFont = node.attributes["font"]
        val x = node.bounds.x + (node.bounds.width - renderer.textWidth(glyph, nodeFont)) / 2f
        val y = node.bounds.y + (node.bounds.height - renderer.fontHeight(nodeFont)) / 2f
        renderer.text(glyph, x, y, arrowColor, nodeFont)
    }

    private fun renderScrollBar(node: UiNode) {
        if (node.scrollMax <= 0f || node.attributes["scrollbar"] == "false") return
        val width = node.attributes["scrollbarWidth"]?.toFloatOrNull() ?: 3f
        val inset = node.attributes["scrollbarInset"]?.toFloatOrNull() ?: 2f
        val track = UiRect(
            node.bounds.right - width - inset,
            node.bounds.y + inset,
            width,
            (node.bounds.height - inset * 2f).coerceAtLeast(0f),
        )
        val thumbH = (track.height * (node.bounds.height / node.scrollContentHeight.coerceAtLeast(node.bounds.height)))
            .coerceIn(10f.coerceAtMost(track.height), track.height)
        val t = node.scrollOffset().coerceIn(0f, node.scrollMax) / node.scrollMax.coerceAtLeast(1f)
        val thumb = UiRect(track.x, track.y + (track.height - thumbH) * t, track.width, thumbH)
        renderer.fill(track, color(node.attributes["scrollbarTrackBg"]) ?: 0x33202331, width / 2f)
        renderer.fill(thumb, color(node.attributes["scrollbarThumbBg"]) ?: UiTheme.TEXT_DIM, width / 2f)
    }

    private fun color(value: String?): Int? {
        val raw = value?.removePrefix("#") ?: return null
        val argb = when (raw.length) {
            6 -> "FF$raw"
            8 -> raw
            else -> return null
        }
        return argb.toLongOrNull(16)?.toInt()
    }

    private fun backgroundColor(node: UiNode, hovered: Boolean): Int? {
        val bg = color(node.attributes["bg"]) ?: node.style.background
        val hoverBg = color(node.attributes["hoverBg"]) ?: node.style.hoverBackground
        return if (hovered) hoverBg ?: bg else bg
    }

    private fun foregroundColor(node: UiNode): Int =
        color(node.attributes["fg"]) ?: node.style.foreground ?: UiTheme.TEXT

    private fun borderColor(node: UiNode): Int? =
        color(node.attributes["border"]) ?: node.style.borderColor

    private fun cornerMask(node: UiNode): Int =
        when (node.attributes["corners"]?.lowercase()) {
            "top" -> 1 or 2
            "bottom", "bot" -> 4 or 8
            "left" -> 1 or 4
            "right" -> 2 or 8
            "none" -> 0
            null, "", "all" -> 15
            else -> node.attributes["corners"]
                ?.split(',', ' ', '|')
                ?.filter { it.isNotBlank() }
                ?.fold(0) { mask, part ->
                    mask or when (part.lowercase()) {
                        "tl", "top-left" -> 1
                        "tr", "top-right" -> 2
                        "bl", "bottom-left" -> 4
                        "br", "bottom-right" -> 8
                        else -> 0
                    }
                }
                ?: 15
        }

    private fun inheritedCornerMask(node: UiNode, roundedClips: List<RoundedClip>): Int {
        var mask = 0
        for (clip in roundedClips) {
            val epsilon = 0.5f
            val touchesLeft = node.bounds.x <= clip.bounds.x + epsilon
            val touchesRight = node.bounds.right >= clip.bounds.right - epsilon
            val touchesTop = node.bounds.y <= clip.bounds.y + epsilon
            val touchesBottom = node.bounds.bottom >= clip.bounds.bottom - epsilon

            if (touchesLeft && touchesTop && clip.corners and 1 != 0) mask = mask or 1
            if (touchesRight && touchesTop && clip.corners and 2 != 0) mask = mask or 2
            if (touchesLeft && touchesBottom && clip.corners and 4 != 0) mask = mask or 4
            if (touchesRight && touchesBottom && clip.corners and 8 != 0) mask = mask or 8
        }
        return mask
    }

    private fun inheritedRadius(node: UiNode, roundedClips: List<RoundedClip>): Float =
        roundedClips.firstOrNull { clip ->
            val epsilon = 0.5f
            node.bounds.x <= clip.bounds.x + epsilon ||
                node.bounds.right >= clip.bounds.right - epsilon ||
                node.bounds.y <= clip.bounds.y + epsilon ||
                node.bounds.bottom >= clip.bounds.bottom - epsilon
        }?.radius ?: 0f

    private data class RoundedClip(
        val bounds: UiRect,
        val radius: Float,
        val corners: Int,
    )

    private fun renderChecker(rect: UiRect) {
        val size = 4
        val cols = ((rect.width + size - 1) / size).toInt()
        val rows = ((rect.height + size - 1) / size).toInt()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val color = if ((row + col) % 2 == 0) 0xFFDCDCDC.toInt() else 0xFFC0C0C0.toInt()
                val x = rect.x + col * size
                val y = rect.y + row * size
                renderer.fill(UiRect(x, y, minOf(size.toFloat(), rect.right - x), minOf(size.toFloat(), rect.bottom - y)), color)
            }
        }
    }

    private fun hsvToColor(hue: Float, saturation: Float, value: Float): Int {
        val c = value * saturation
        val x = c * (1f - kotlin.math.abs((hue / 60f) % 2f - 1f))
        val m = value - c
        val (r1, g1, b1) = when {
            hue < 60f -> Triple(c, x, 0f)
            hue < 120f -> Triple(x, c, 0f)
            hue < 180f -> Triple(0f, c, x)
            hue < 240f -> Triple(0f, x, c)
            hue < 300f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val r = ((r1 + m) * 255f).toInt().coerceIn(0, 255)
        val g = ((g1 + m) * 255f).toInt().coerceIn(0, 255)
        val b = ((b1 + m) * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun lerpColor(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

    private fun hitTest(node: UiNode, x: Float, y: Float): UiNode? {
        val contains = node.bounds.contains(x, y)
        if (!contains && node.clipsChildren()) return null
        for (child in node.children.asReversed()) {
            val hit = hitTest(child, x, y)
            if (hit != null) return hit
        }
        return node.takeIf { contains && it.interactive }
    }

    private fun scrollHitTest(node: UiNode, x: Float, y: Float): UiNode? {
        val contains = node.bounds.contains(x, y)
        if (!contains && node.clipsChildren()) return null
        for (child in node.children.asReversed()) {
            val hit = scrollHitTest(child, x, y)
            if (hit != null && hit.scrollMax > 0f) return hit
        }
        return node.takeIf { contains && it.type == "scroll" && it.scrollMax > 0f }
    }

    private fun UiNode.clipsChildren(): Boolean =
        style.clip || type == "scroll"

    private fun UiNode.scrollOffset(): Float =
        attributes["scrollY"]?.toFloatOrNull() ?: attributes["scroll"]?.toFloatOrNull() ?: 0f
}
