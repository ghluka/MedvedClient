package me.ghluka.medved.gui.ui

class UiNode(
    val type: String,
    val id: String? = null,
    val text: String? = null,
    val axis: UiAxis = UiAxis.VERTICAL,
    val width: UiSize = UiSize.Wrap,
    val height: UiSize = UiSize.Wrap,
    val x: Float? = null,
    val y: Float? = null,
    val style: UiStyle = UiStyle(),
    val attributes: Map<String, String> = emptyMap(),
    children: List<UiNode> = emptyList(),
) {
    val children: MutableList<UiNode> = children.toMutableList()
    var bounds: UiRect = UiRect(0f, 0f, 0f, 0f)
        internal set
    var scrollContentHeight: Float = 0f
        internal set
    var scrollMax: Float = 0f
        internal set

    val interactive: Boolean get() = id != null || type == "button" || type == "scroll" || attributes["click"] != null

    fun child(node: UiNode): UiNode = apply { children += node }
}

sealed class UiSize {
    data object Wrap : UiSize()
    data object Fill : UiSize()
    data class Px(val value: Float) : UiSize()
}

class UiDocument(val root: UiNode) {
    private val handlers = mutableMapOf<String, UiEventHandler>()

    fun onClick(id: String, handler: UiEventHandler): UiDocument = apply {
        handlers[id] = handler
    }

    fun validate(name: String): UiDocument = apply {
        UiXmlValidator.validateDocument(name, this)
    }

    fun click(id: String, event: UiPointerEvent): Boolean =
        handlers[id]?.invoke(event) == true
}

data class UiPointerEvent(
    val x: Float,
    val y: Float,
    val button: Int,
    val node: UiNode,
)

typealias UiEventHandler = (UiPointerEvent) -> Boolean
