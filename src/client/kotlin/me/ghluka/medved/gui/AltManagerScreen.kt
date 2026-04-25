package me.ghluka.medved.gui
import me.ghluka.medved.alt.AltAccount
import me.ghluka.medved.alt.AltManager
import me.ghluka.medved.alt.AltType
import me.ghluka.medved.module.modules.other.Colour
import me.ghluka.medved.module.modules.other.Font
import net.minecraft.client.Minecraft
import net.minecraft.util.Util
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.CharacterEvent
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.FontDescription
import net.minecraft.network.chat.Style
import net.minecraft.resources.Identifier
import org.lwjgl.glfw.GLFW

class AltManagerScreen(private val parent: Screen?) : Screen(Component.literal("Alt Manager")) {

    private enum class State { LIST, CHOOSE_TYPE, ADD_CRACKED, ADD_TOKEN, ADD_MICROSOFT }
    private var state = State.LIST

    private var listScroll = 0
    private var loginMsg: String? = null
    private var loginMsgColor = 0
    private var loginMsgTimer = 0L

    private val searchField = TextField()
    private var searchActive = false

    private var dragIdx      = -1
    private var dragStartX   = 0
    private var dragStartY   = 0
    private var dragCurrentY = 0
    private var isDragging   = false
    private val DRAG_THRESHOLD = 7

    private var lastClickIdx  = -1
    private var lastClickTime = 0L
    private val DOUBLE_CLICK_MS = 400L

    private var uiSf = 1f
    private var ssCx = 0
    private var ssCy = 0

    private val fields      = Array(3) { TextField() }
    private var activeInput = -1
    private var formError   = ""

    private enum class MsState { REQUESTING, SHOWING_CODE, WAITING, SUCCESS, FAILED }
    @Volatile private var msState: MsState? = null
    @Volatile private var msDeviceInfo: AltManager.DeviceCodeInfo? = null
    @Volatile private var msProfile: AltManager.McProfile? = null
    @Volatile private var msErrorMsg = ""
    private var msPollThread: Thread? = null

    companion object {
        private const val PNL_W     = 400
        private const val PNL_H     = 290
        private const val TITLE_H   = 20
        private const val BOTTOM_H  = 30
        private const val CONTENT_H = PNL_H - TITLE_H - BOTTOM_H
        private const val ROW_H     = 30
        private const val FIELD_H   = 16
        private const val BTN_H     = 16
        private const val BTN_W     = 60
        private const val BTN_WIDE  = 92
        private const val BTN_SMALL = 50
        private const val ARROW_W   = 14
        private const val SEARCH_H  = 14
        private const val LIST_OFFSET = SEARCH_H + 6
    }

    private val maxRows get() = (CONTENT_H - LIST_OFFSET) / ROW_H
    private val panelX  get() = (width  - PNL_W) / 2
    private val panelY  get() = (height - PNL_H) / 2

    private fun argb(a: Int, r: Int, g: Int, b: Int) = (a shl 24) or (r shl 16) or (g shl 8) or b
    private val ACCENT    get() = Colour.accent.value.argb
    private val FONT      get() = Font.getFont()
    private fun styled(s: String) = Font.styledText(s)
    private val cursorVisible get() = (System.currentTimeMillis() / 530) % 2 == 0L

    private fun shade(base: Int, mix: Float, alpha: Int = 255): Int {
        val c = Colour.bg.value
        val r = (base + (c.r - base) * mix).toInt().coerceIn(0, 255)
        val g = (base + (c.g - base) * mix).toInt().coerceIn(0, 255)
        val b = (base + (c.b - base) * mix).toInt().coerceIn(0, 255)
        return argb(alpha, r, g, b)
    }

    private fun emx(mx: Int) = ssCx + ((mx - ssCx) / uiSf).toInt()
    private fun emy(my: Int) = ssCy + ((my - ssCy) / uiSf).toInt()

    private val PNL_BG   get() = shade(8, 0.05f, 100)
    private val HDR_BG   get() = shade(20, 0.22f)
    private val ROW_BG   get() = shade(24, 0.07f, 200)
    private val BTN_BG   get() = shade(18, 0.10f, 255)
    private val BTN_HOV  get() = shade(32, 0.28f, 230)
    private val FLD_BAGH get() = shade(35, 0.12f, 245)
    private val FLD_BAG  get() = shade(45, 0.12f, 245)

    private val jbMonoStyle: Style
        get() = Style.EMPTY.withFont(FontDescription.Resource(
            Identifier.fromNamespaceAndPath("medved", "jetbrains_mono")))
    private fun jbMono(text: String): Component = Component.literal(text).withStyle(jbMonoStyle)

    private fun filteredAccounts(): List<AltAccount> {
        val q = searchField.text.trim().lowercase()
        return if (q.isEmpty()) AltManager.accounts
        else AltManager.accounts.filter { it.username.lowercase().contains(q) }
    }

    private inner class TextField {
        var text      = ""
        var cursor    = 0
        var selAnchor = -1
        var scrollPx  = 0

        val selMin get() = if (selAnchor < 0) cursor else minOf(cursor, selAnchor)
        val selMax get() = if (selAnchor < 0) cursor else maxOf(cursor, selAnchor)
        val hasSelection get() = selAnchor >= 0 && selAnchor != cursor

        fun get()  = text
        fun set(s: String) { text = s; cursor = s.length; selAnchor = -1; clampScroll(PNL_W - 40) }
        fun clear() { text = ""; cursor = 0; selAnchor = -1; scrollPx = 0 }

        fun insert(s: String) {
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            text = text.substring(0, cursor) + s + text.substring(cursor)
            cursor += s.length
        }
        fun backspace() {
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            else if (cursor > 0) { text = text.removeRange(cursor - 1, cursor); cursor-- }
        }
        fun deleteForward() {
            if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 }
            else if (cursor < text.length) { text = text.removeRange(cursor, cursor + 1) }
        }
        fun backspaceWord() {
            if (hasSelection) { backspace(); return }
            var start = cursor
            while (start > 0 && text[start - 1] == ' ') start--
            while (start > 0 && text[start - 1] != ' ') start--
            text = text.removeRange(start, cursor); cursor = start; selAnchor = -1
        }
        fun move(delta: Int, selecting: Boolean) {
            if (!selecting && hasSelection) { cursor = if (delta < 0) selMin else selMax; selAnchor = -1; return }
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = (cursor + delta).coerceIn(0, text.length)
        }
        fun wordMove(forward: Boolean, selecting: Boolean) {
            var i = cursor
            if (forward) { while (i < text.length && text[i] == ' ') i++; while (i < text.length && text[i] != ' ') i++ }
            else         { while (i > 0 && text[i - 1] == ' ') i--;  while (i > 0 && text[i - 1] != ' ') i-- }
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = i
        }
        fun home(selecting: Boolean) {
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = 0
        }
        fun end(selecting: Boolean) {
            if (selecting && selAnchor < 0) selAnchor = cursor else if (!selecting) selAnchor = -1
            cursor = text.length
        }
        fun selectAll() { selAnchor = 0; cursor = text.length }
        fun copy() = if (hasSelection) text.substring(selMin, selMax) else ""
        fun cut()  = copy().also { if (hasSelection) { text = text.removeRange(selMin, selMax); cursor = selMin; selAnchor = -1 } }

        fun posFromPixel(relPx: Int): Int {
            val adjusted = relPx + scrollPx
            if (adjusted <= 0 || text.isEmpty()) return 0
            for (i in 1..text.length) {
                val half = (FONT.width(styled(text.substring(0, i - 1))) + FONT.width(styled(text.substring(0, i)))) / 2
                if (adjusted <= half) return i - 1
            }
            return text.length
        }
        fun clampScroll(visWidth: Int) {
            val curPx = FONT.width(styled(text.substring(0, cursor)))
            if (curPx - scrollPx < 0)        scrollPx = curPx
            if (curPx - scrollPx > visWidth) scrollPx = curPx - visWidth
            scrollPx = scrollPx.coerceAtLeast(0)
        }
    }

    override fun isPauseScreen() = false

    override fun onClose() {
        cancelMsAuth()
        minecraft.setScreen(parent)
    }

    private fun cancelMsAuth() {
        msPollThread?.interrupt()
        msPollThread = null
    }

    override fun extractRenderState(g: GuiGraphicsExtractor, mx: Int, my: Int, delta: Float) {
        g.fill(0, 0, width, height, argb(180, 0, 0, 0))

        val gs = Minecraft.getInstance().window.guiScale.toFloat().coerceAtLeast(1f)
        uiSf = 2f / gs
        ssCx = width  / 2
        ssCy = height / 2
        g.pose().pushMatrix()
        g.pose().scaleAround(uiSf, uiSf, ssCx.toFloat(), ssCy.toFloat())

        val dmx = emx(mx)
        val dmy = emy(my)

        val px = panelX
        val py = panelY

        g.fill(px, py, px + PNL_W, py + PNL_H, PNL_BG)

        g.fill(px, py, px + PNL_W, py + TITLE_H, HDR_BG)
        g.centeredText(FONT, styled("ALT MANAGER"), px + PNL_W / 2, py + (TITLE_H - 8) / 2, argb(255, 220, 220, 235))

        when (state) {
            State.LIST          -> drawList(g, px, py, dmx, dmy)
            State.CHOOSE_TYPE   -> drawChooseType(g, px, py, dmx, dmy)
            State.ADD_CRACKED,
            State.ADD_TOKEN     -> drawAddForm(g, px, py, dmx, dmy)
            State.ADD_MICROSOFT -> drawMicrosoft(g, px, py, dmx, dmy)
        }

        g.pose().popMatrix()
    }

    private fun drawList(g: GuiGraphicsExtractor, px: Int, py: Int, mx: Int, my: Int) {
        val cx = px + 1
        val cy = py + TITLE_H
        val cw = PNL_W - 2

        val sx = cx + 4
        val sy = cy + 4
        val sw = cw - 8
        drawField(g, sx, sy, sw, SEARCH_H, searchField, searchActive, "Search...")

        val listY   = cy + LIST_OFFSET + 4
        val accounts = filteredAccounts()
        val total   = accounts.size
        listScroll  = listScroll.coerceIn(0, (total - maxRows).coerceAtLeast(0))

        g.enableScissor(cx, cy + LIST_OFFSET, cx + cw, cy + CONTENT_H)

        for (i in 0 until minOf(total - listScroll, maxRows)) {
            val acc = accounts[listScroll + i]
            val ry  = listY + i * ROW_H
            val absIdx = AltManager.accounts.indexOf(acc)

            if (isDragging && absIdx == dragIdx) {
                val ghostY = dragCurrentY - ROW_H / 2
                g.fill(cx + 4, ghostY, cx + 4 + cw - 8, ghostY + ROW_H - 2, argb(110, 80, 80, 110))
            } else {
                drawAccountRow(g, acc, cx + 4, ry, cw - 8, mx, my)
            }
        }

        if (isDragging) {
            val ins = dragInsertIndex()
            val indY = listY + (ins - listScroll) * ROW_H
            g.fill(cx + 4, indY - 1, cx + 4 + cw - 8, indY + 1, ACCENT)
        }

        if (total == 0) {
            g.centeredText(FONT, styled(if (searchField.text.isNotBlank()) "No results" else "No accounts saved"),
                px + PNL_W / 2, cy + LIST_OFFSET + (CONTENT_H - LIST_OFFSET) / 2 - 4, argb(160, 160, 160, 180))
        }

        g.disableScissor()

        if (total > maxRows) {
            val trackX = px + PNL_W - 6
            val trackY = cy + LIST_OFFSET + 2
            val trackH = CONTENT_H - LIST_OFFSET - 4
            g.fill(trackX, trackY, trackX + 3, trackY + trackH, argb(80, 255, 255, 255))
            val thumbH = (trackH * maxRows / total).coerceAtLeast(8)
            val thumbY = trackY + (trackH - thumbH) * listScroll / (total - maxRows)
            g.fill(trackX, thumbY, trackX + 3, thumbY + thumbH, ACCENT)
        }

        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        drawBtn(g, px + 8, bby, BTN_WIDE, BTN_H, "Add Account", mx, my)
        drawBtn(g, px + PNL_W - BTN_WIDE - 8, bby, BTN_WIDE, BTN_H, "Close", mx, my)

        val msg = loginMsg
        if (msg != null && System.currentTimeMillis() < loginMsgTimer) {
            g.centeredText(FONT, styled(msg), px + PNL_W / 2, bby + 3, loginMsgColor)
        } else if (msg != null) {
            loginMsg = null
        }
    }

    private fun drawAccountRow(
        g: GuiGraphicsExtractor, acc: AltAccount,
        rx: Int, ry: Int, rw: Int, mx: Int, my: Int
    ) {
        val rowHovered = mx in rx until rx + rw && my in ry until ry + ROW_H - 2
        val rowBg = if (rowHovered) shade(28, 0.12f, 210) else ROW_BG
        g.fill(rx, ry, rx + rw, ry + ROW_H - 2, rowBg)

        val dispName = if (acc.username.isNotBlank()) acc.username else "(unnamed)"
        g.text(FONT, styled(dispName), rx + 6, ry + 4, argb(255, 215, 215, 228))
        val (typeLabel, typeColor) = when (acc.type) {
            AltType.CRACKED      -> "Cracked"   to argb(180, 150, 150, 160)
            AltType.ACCESS_TOKEN -> "Premium"   to argb(200, 80, 200, 120)
            AltType.MICROSOFT    -> "Microsoft" to argb(200, 80, 140, 220)
        }
        g.text(FONT, styled(typeLabel), rx + 6, ry + 15, typeColor)

        val bby      = ry + (ROW_H - 2 - BTN_H) / 2
        val removeBx = rx + rw - BTN_SMALL - 2
        val loginBx  = removeBx - BTN_SMALL - 2
        val downBx   = loginBx  - ARROW_W   - 2
        val upBx     = downBx   - ARROW_W   - 2

        drawBtn(g, upBx,   bby, ARROW_W, BTN_H, "\u25b2", mx, my, labelComp = jbMono("\u25b2"), ghost = true)
        drawBtn(g, downBx, bby, ARROW_W, BTN_H, "\u25bc", mx, my, labelComp = jbMono("\u25bc"), ghost = true)
        drawBtn(g, loginBx,  bby, BTN_SMALL, BTN_H, "Login",  mx, my)
        drawBtn(g, removeBx, bby, BTN_SMALL, BTN_H, "Remove", mx, my, danger = true)
    }

    private fun drawChooseType(g: GuiGraphicsExtractor, px: Int, py: Int, mx: Int, my: Int) {
        val cy  = py + TITLE_H + 20
        val cx  = px + PNL_W / 2
        g.centeredText(FONT, styled("Select account type"), cx, cy, argb(200, 180, 180, 200))

        val btnW   = 180
        val btnGap = 6
        var by     = cy + 18
        for (label in listOf("Cracked (Offline)", "Access Token", "Microsoft")) {
            drawBtn(g, cx - btnW / 2, by, btnW, BTN_H + 4, label, mx, my)
            by += BTN_H + 4 + btnGap
        }

        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        drawBtn(g, px + 8, bby, BTN_W, BTN_H, "Back", mx, my)
    }

    private fun drawAddForm(g: GuiGraphicsExtractor, px: Int, py: Int, mx: Int, my: Int) {
        val cx = px + 16
        val fw = PNL_W - 32
        var fy = py + TITLE_H + 16

        val header = when (state) {
            State.ADD_CRACKED -> "Add Cracked Account"
            State.ADD_TOKEN   -> "Add MC Token Account"
            else              -> ""
        }
        g.centeredText(FONT, styled(header), px + PNL_W / 2, fy, argb(220, 200, 200, 220))
        fy += 16

        val fieldDefs = when (state) {
            State.ADD_CRACKED -> listOf("Username")
            State.ADD_TOKEN   -> listOf("Username (optional)", "UUID (optional)", "MC Access Token (mctoken)")
            else              -> emptyList()
        }
        for ((idx, label) in fieldDefs.withIndex()) {
            g.text(FONT, styled(label), cx, fy, argb(160, 160, 160, 180))
            fy += 10
            drawField(g, cx, fy, fw, FIELD_H, fields[idx], activeInput == idx)
            fy += FIELD_H + 10
        }

        if (formError.isNotBlank()) {
            g.centeredText(FONT, styled(formError), px + PNL_W / 2, fy, argb(255, 220, 80, 80))
        }

        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        drawBtn(g, px + 8, bby, BTN_W, BTN_H, "Back", mx, my)
        drawBtn(g, px + PNL_W - BTN_W - 8, bby, BTN_W, BTN_H, "Add", mx, my)
    }

    private fun drawMicrosoft(g: GuiGraphicsExtractor, px: Int, py: Int, mx: Int, my: Int) {
        val cx  = px + PNL_W / 2
        var fy  = py + TITLE_H + 18

        when (msState) {
            MsState.REQUESTING -> {
                g.centeredText(FONT, styled("Requesting device code..."), cx, fy, argb(180, 180, 180, 200))
            }
            MsState.SHOWING_CODE, MsState.WAITING -> {
                val info = msDeviceInfo
                if (info != null) {
                    g.centeredText(FONT, styled("1. Open your browser and go to:"), cx, fy, argb(180, 160, 160, 180)); fy += 14
                    val uriComp = Component.literal(info.verificationUri).withStyle(
                        Style.EMPTY.withUnderlined(true).withColor(0x6496FF))
                    val uriW2 = FONT.width(uriComp)
                    val uriHovered = isOver(mx, my, cx - uriW2 / 2, fy, uriW2, 9)
                    val uriColor = if (uriHovered) argb(255, 140, 210, 255) else argb(255, 100, 180, 255)
                    g.centeredText(FONT, uriComp, cx, fy, uriColor); fy += 18
                    g.centeredText(FONT, styled("2. Enter this code:"),             cx, fy, argb(180, 160, 160, 180)); fy += 14

                    val codeFont = Font.getFont()
                    val codeW    = codeFont.width(styled(info.userCode))
                    val codeX    = cx - codeW / 2 - 10
                    g.fill(codeX, fy - 4, codeX + codeW + 20, fy + 18, argb(200, 28, 28, 38))
                    g.text(codeFont, styled(info.userCode), codeX + 10, fy + 4, argb(255, 100, 230, 130))
                    fy += 26

                    drawBtn(g, cx - BTN_WIDE / 2, fy, BTN_WIDE, BTN_H, "Copy Code", mx, my); fy += BTN_H + 12
                    if (msState == MsState.WAITING) {
                        g.centeredText(FONT, styled("Waiting for login..."), cx, fy, argb(180, 160, 160, 180))
                    }
                }
            }
            MsState.SUCCESS -> {
                val prof = msProfile
                if (prof != null) {
                    g.centeredText(FONT, styled("Logged in as ${prof.username}!"),
                        cx, py + TITLE_H + CONTENT_H / 2 - 16, argb(255, 100, 220, 100))
                }
            }
            MsState.FAILED -> {
                var errY = py + TITLE_H + 20
                g.centeredText(FONT, styled("Auth failed."), cx, errY, argb(255, 220, 80, 80))
                errY += 14
                val err = msErrorMsg
                if (err.isNotBlank()) {
                    for (line in wrapText(err, PNL_W - 40)) {
                        g.centeredText(FONT, styled(line), cx, errY, argb(180, 180, 100, 100))
                        errY += 11
                    }
                }
            }
            null -> {}
        }

        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        when (msState) {
            MsState.SUCCESS -> {
                drawBtn(g, px + 8,                  bby, BTN_W, BTN_H, "Back",   mx, my)
                drawBtn(g, px + PNL_W - BTN_W - 8,  bby, BTN_W, BTN_H, "Save",   mx, my)
            }
            MsState.FAILED -> {
                drawBtn(g, px + 8,                  bby, BTN_W, BTN_H, "Back",   mx, my)
                drawBtn(g, px + PNL_W - BTN_W - 8,  bby, BTN_W, BTN_H, "Retry",  mx, my)
            }
            else -> drawBtn(g, cx - BTN_W / 2, bby, BTN_W, BTN_H, "Cancel", mx, my)
        }
    }

    private fun drawBtn(
        g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int,
        label: String, mx: Int, my: Int, danger: Boolean = false,
        labelComp: Component? = null, ghost: Boolean = false
    ) {
        val hovered = mx in x until x + w && my in y until y + h
        if (ghost) {
            val textColor = if (hovered) ACCENT else argb(160, 160, 160, 180)
            g.centeredText(FONT, labelComp ?: styled(label), x + w / 2, y + (h - 8) / 2, textColor)
            return
        }
        val bg = when {
            danger && hovered -> argb(220, 90, 20, 20)
            danger            -> argb(180, 60, 12, 12)
            hovered           -> BTN_HOV
            else              -> BTN_BG
        }
        val textColor = if (danger) argb(255, 220, 120, 120) else argb(255, 215, 215, 228)
        g.fill(x, y, x + w, y + h, bg)
        g.centeredText(FONT, labelComp ?: styled(label), x + w / 2, y + (h - 8) / 2, textColor)
    }

    private fun drawField(
        g: GuiGraphicsExtractor, x: Int, y: Int, w: Int, h: Int,
        field: TextField, active: Boolean, placeholder: String = ""
    ) {
        val visW  = w - 8
        val textX = x + 4
        val textY = y + (h - 8) / 2

        g.fill(x, y, x + w, y + h, if (active) FLD_BAGH else FLD_BAG)
        g.fill(x, y, x + w, y + 1, if (active) ACCENT else argb(80, 160, 160, 160))

        g.enableScissor(textX, y, textX + visW, y + h)

        if (active && field.hasSelection) {
            val sx = textX - field.scrollPx + FONT.width(styled(field.text.substring(0, field.selMin)))
            val ex = textX - field.scrollPx + FONT.width(styled(field.text.substring(0, field.selMax)))
            g.fill(sx, y + 2, ex, y + h - 2, argb(170, 60, 110, 210))
        }

        if (field.text.isEmpty() && placeholder.isNotEmpty() && !active) {
            g.text(FONT, styled(placeholder), textX, textY, argb(100, 160, 160, 170))
        } else {
            g.text(FONT, styled(field.text), textX - field.scrollPx, textY, argb(255, 220, 220, 235))
        }

        if (active && cursorVisible) {
            val cx = textX - field.scrollPx + FONT.width(styled(field.text.substring(0, field.cursor)))
            g.fill(cx, y + 2, cx + 1, y + h - 2, argb(230, 220, 220, 255))
        }

        g.disableScissor()
    }

    private fun badgeColor(type: AltType) = when (type) {
        AltType.CRACKED      -> argb(220, 60,  60,  70)
        AltType.ACCESS_TOKEN -> argb(220, 80,  70,  20)
        AltType.MICROSOFT    -> argb(220, 20,  80, 100)
    }
    private fun badgeLetter(type: AltType) = when (type) {
        AltType.CRACKED      -> "C"
        AltType.ACCESS_TOKEN -> "T"
        AltType.MICROSOFT    -> "M"
    }

    private fun dragInsertIndex(): Int {
        val listY = panelY + TITLE_H + LIST_OFFSET + 4
        return ((dragCurrentY - ROW_H / 2 - listY) / ROW_H + listScroll)
            .coerceIn(0, AltManager.accounts.size)
    }

    override fun mouseClicked(event: MouseButtonEvent, inBounds: Boolean): Boolean {
        val mx = emx(event.x().toInt())
        val my = emy(event.y().toInt())
        val px = panelX
        val py = panelY
        when (state) {
            State.LIST          -> handleListClick(mx, my, px, py)
            State.CHOOSE_TYPE   -> handleChooseTypeClick(mx, my, px, py)
            State.ADD_CRACKED,
            State.ADD_TOKEN     -> handleFormClick(mx, my, px, py)
            State.ADD_MICROSOFT -> handleMicrosoftClick(mx, my, px, py)
        }
        return true
    }

    private fun handleListClick(mx: Int, my: Int, px: Int, py: Int) {
        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2

        if (isOver(mx, my, px + 8, bby, BTN_WIDE, BTN_H)) { clearForm(); state = State.CHOOSE_TYPE; return }
        if (isOver(mx, my, px + PNL_W - BTN_WIDE - 8, bby, BTN_WIDE, BTN_H)) { onClose(); return }

        val cx = px + 1
        val cy = py + TITLE_H
        val cw = PNL_W - 2
        val sx = cx + 4
        val sy = cy + 4
        val sw = cw - 8

        if (isOver(mx, my, sx, sy, sw, SEARCH_H)) {
            searchActive = true
            val relX = mx - sx - 4
            searchField.apply { cursor = posFromPixel(relX); selAnchor = cursor; clampScroll(sw - 8) }
            return
        }
        searchActive = false

        val listY    = cy + LIST_OFFSET + 4
        val rx       = cx + 4
        val rw       = cw - 8
        val accounts = filteredAccounts()

        for (i in 0 until minOf(accounts.size - listScroll, maxRows)) {
            val acc    = accounts[listScroll + i]
            val absIdx = AltManager.accounts.indexOf(acc)
            val ry     = listY + i * ROW_H
            if (my !in ry until ry + ROW_H - 2) continue

            val bby2     = ry + (ROW_H - 2 - BTN_H) / 2
            val removeBx = rx + rw - BTN_SMALL - 2
            val loginBx  = removeBx - BTN_SMALL - 2
            val downBx   = loginBx  - ARROW_W   - 2
            val upBx     = downBx   - ARROW_W   - 2

            when {
                isOver(mx, my, upBx,     bby2, ARROW_W,   BTN_H) -> {
                    if (absIdx > 0) { AltManager.accounts.swap(absIdx, absIdx - 1); AltManager.save() }
                }
                isOver(mx, my, downBx,   bby2, ARROW_W,   BTN_H) -> {
                    if (absIdx < AltManager.accounts.size - 1) { AltManager.accounts.swap(absIdx, absIdx + 1); AltManager.save() }
                }
                isOver(mx, my, loginBx,  bby2, BTN_SMALL, BTN_H) -> doLogin(acc)
                isOver(mx, my, removeBx, bby2, BTN_SMALL, BTN_H) -> {
                    AltManager.removeAccount(acc.id)
                    listScroll = listScroll.coerceAtMost((AltManager.accounts.size - maxRows).coerceAtLeast(0))
                }
                else -> {
                    val now = System.currentTimeMillis()
                    if (lastClickIdx == absIdx && now - lastClickTime < DOUBLE_CLICK_MS) {
                        doLogin(acc)
                        lastClickIdx = -1
                    } else {
                        lastClickIdx  = absIdx
                        lastClickTime = now
                    }

                    dragIdx      = absIdx
                    dragStartX   = mx
                    dragStartY   = my
                    dragCurrentY = my
                    isDragging   = false
                }
            }
            return
        }
    }

    private fun doLogin(acc: AltAccount) {
        val ok = AltManager.login(acc)
        loginMsg      = if (ok) "Logged in as ${acc.username}" else "Login failed"
        loginMsgColor = if (ok) argb(255, 100, 220, 100) else argb(255, 220, 80, 80)
        loginMsgTimer = System.currentTimeMillis() + 2_500L
    }

    private fun handleChooseTypeClick(mx: Int, my: Int, px: Int, py: Int) {
        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        if (isOver(mx, my, px + 8, bby, BTN_W, BTN_H)) { state = State.LIST; return }

        val cx   = px + PNL_W / 2
        val btnW = 180
        val btnH = BTN_H + 4
        val gap  = 6
        var by   = py + TITLE_H + 20 + 18
        for (nextState in listOf(State.ADD_CRACKED, State.ADD_TOKEN, State.ADD_MICROSOFT)) {
            if (isOver(mx, my, cx - btnW / 2, by, btnW, btnH)) {
                state = nextState
                if (nextState == State.ADD_MICROSOFT) startMicrosoftAuth()
                return
            }
            by += btnH + gap
        }
    }

    private fun handleFormClick(mx: Int, my: Int, px: Int, py: Int) {
        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        val tx  = px + 16
        val fw  = PNL_W - 32
        if (isOver(mx, my, px + 8, bby, BTN_W, BTN_H))                   { state = State.CHOOSE_TYPE; clearForm(); return }
        if (isOver(mx, my, px + PNL_W - BTN_W - 8, bby, BTN_W, BTN_H))   { submitForm(); return }

        val fieldCount = when (state) { State.ADD_CRACKED -> 1; State.ADD_TOKEN -> 3; else -> 0 }
        var fy = py + TITLE_H + 16 + 16
        for (i in 0 until fieldCount) {
            fy += 10
            if (isOver(mx, my, tx, fy, fw, FIELD_H)) {
                activeInput = i
                fields[i].apply { cursor = posFromPixel(mx - tx - 4); selAnchor = cursor; clampScroll(fw - 8) }
                return
            }
            fy += FIELD_H + 10
        }
        activeInput = -1
    }

    private fun handleMicrosoftClick(mx: Int, my: Int, px: Int, py: Int) {
        val bby = py + TITLE_H + CONTENT_H + (BOTTOM_H - BTN_H) / 2
        val cx  = px + PNL_W / 2
        when (msState) {
            MsState.SHOWING_CODE, MsState.WAITING -> {
                val info = msDeviceInfo
                if (info != null) {
                    val uriY = py + TITLE_H + 18 + 14
                    val uriW = FONT.width(Component.literal(info.verificationUri))
                    if (isOver(mx, my, cx - uriW / 2, uriY, uriW, 9)) {
                        try { Util.getPlatform().openUri(java.net.URI.create(info.verificationUri)) } catch (_: Exception) {}
                        return
                    }
                    val fy = py + TITLE_H + 18 + 14 + 18 + 14 + 26
                    if (isOver(mx, my, cx - BTN_W / 2, fy, BTN_W, BTN_H)) {
                        minecraft.keyboardHandler.setClipboard(info.userCode); return
                    }
                }
                if (isOver(mx, my, cx - BTN_W / 2, bby, BTN_W, BTN_H)) { cancelMsAuth(); state = State.CHOOSE_TYPE }
            }
            MsState.REQUESTING -> {
                if (isOver(mx, my, cx - BTN_W / 2, bby, BTN_W, BTN_H)) { cancelMsAuth(); state = State.CHOOSE_TYPE }
            }
            MsState.SUCCESS -> {
                if (isOver(mx, my, px + 8, bby, BTN_W, BTN_H)) { state = State.LIST; return }
                if (isOver(mx, my, px + PNL_W - BTN_W - 8, bby, BTN_W, BTN_H)) {
                    val prof = msProfile
                    if (prof != null) {
                        val acc = AltAccount(type = AltType.MICROSOFT, username = prof.username,
                            token = prof.accessToken, uuid = prof.uuid.toString())
                        AltManager.addAccount(acc)
                        AltManager.login(acc)
                    }
                    state = State.LIST
                }
            }
            MsState.FAILED -> {
                if (isOver(mx, my, px + 8, bby, BTN_W, BTN_H)) { state = State.CHOOSE_TYPE; return }
                if (isOver(mx, my, px + PNL_W - BTN_W - 8, bby, BTN_W, BTN_H)) { startMicrosoftAuth() }
            }
            null -> {}
        }
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        val mx = emx(event.x().toInt())
        val my = emy(event.y().toInt())

        if (state == State.LIST && searchActive) {
            val relX = mx - (panelX + 1 + 4) - 4
            searchField.apply { cursor = posFromPixel(relX); clampScroll(PNL_W - 16) }
            return true
        }

        if (activeInput >= 0 && event.button() == 0) {
            val count = when (state) { State.ADD_CRACKED -> 1; State.ADD_TOKEN -> 3; else -> 0 }
            if (activeInput < count) {
                val relX = mx - panelX - 16 - 4
                fields[activeInput].apply { cursor = posFromPixel(relX); clampScroll(PNL_W - 40) }
                return true
            }
        }

        if (state == State.LIST && dragIdx >= 0 && event.button() == 0) {
            val dist = Math.abs(my - dragStartY) + Math.abs(mx - dragStartX)
            if (!isDragging && dist >= DRAG_THRESHOLD) isDragging = true
            if (isDragging) {
                dragCurrentY = my
                return true
            }
        }

        return super.mouseDragged(event, dragX, dragY)
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (state == State.LIST && isDragging && dragIdx >= 0) {
            val targetIdx = dragInsertIndex().coerceIn(0, AltManager.accounts.size - 1)
            if (targetIdx != dragIdx) {
                val acc      = AltManager.accounts.removeAt(dragIdx)
                val insertAt = if (targetIdx > dragIdx) targetIdx - 1 else targetIdx
                AltManager.accounts.add(insertAt, acc)
                AltManager.save()
            }
        }
        isDragging = false
        dragIdx    = -1
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(x: Double, y: Double, deltaH: Double, deltaV: Double): Boolean {
        if (state == State.LIST) {
            val maxScroll = (filteredAccounts().size - maxRows).coerceAtLeast(0)
            listScroll = (listScroll - deltaV.toInt()).coerceIn(0, maxScroll)
        }
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        val key   = event.key()
        val mods  = event.modifiers()
        val ctrl  = (mods and GLFW.GLFW_MOD_CONTROL) != 0
        val shift = (mods and GLFW.GLFW_MOD_SHIFT)   != 0

        if (key == GLFW.GLFW_KEY_ESCAPE) {
            when {
                searchActive && searchField.text.isNotBlank() -> { searchField.clear(); listScroll = 0 }
                searchActive -> searchActive = false
                state == State.LIST -> onClose()
                state == State.ADD_MICROSOFT -> { cancelMsAuth(); state = State.CHOOSE_TYPE }
                else -> state = State.LIST
            }
            return true
        }

        if (searchActive) {
            val f = searchField
            when {
                ctrl && key == GLFW.GLFW_KEY_A -> f.selectAll()
                ctrl && key == GLFW.GLFW_KEY_C -> { val s = f.copy(); if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
                ctrl && key == GLFW.GLFW_KEY_X -> { val s = f.cut();  if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
                ctrl && key == GLFW.GLFW_KEY_V -> { f.insert(minecraft.keyboardHandler.clipboard ?: ""); listScroll = 0 }
                key == GLFW.GLFW_KEY_BACKSPACE  -> if (ctrl) f.backspaceWord() else f.backspace()
                key == GLFW.GLFW_KEY_DELETE     -> f.deleteForward()
                key == GLFW.GLFW_KEY_LEFT       -> if (ctrl) f.wordMove(false, shift) else f.move(-1, shift)
                key == GLFW.GLFW_KEY_RIGHT      -> if (ctrl) f.wordMove(true,  shift) else f.move( 1, shift)
                key == GLFW.GLFW_KEY_HOME       -> f.home(shift)
                key == GLFW.GLFW_KEY_END        -> f.end(shift)
                else -> return super.keyPressed(event)
            }
            f.clampScroll(PNL_W - 40)
            listScroll = 0
            return true
        }

        if (activeInput < 0) return super.keyPressed(event)

        val f    = fields[activeInput]
        val visW = PNL_W - 40
        when {
            ctrl && key == GLFW.GLFW_KEY_A -> f.selectAll()
            ctrl && key == GLFW.GLFW_KEY_C -> { val s = f.copy(); if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
            ctrl && key == GLFW.GLFW_KEY_X -> { val s = f.cut();  if (s.isNotEmpty()) minecraft.keyboardHandler.clipboard = s }
            ctrl && key == GLFW.GLFW_KEY_V -> {
                val clip  = minecraft.keyboardHandler.clipboard ?: ""
                val value = if (state == State.ADD_TOKEN && activeInput == 2) parseMcToken(clip) else clip
                f.insert(value)
            }
            key == GLFW.GLFW_KEY_BACKSPACE -> if (ctrl) f.backspaceWord() else f.backspace()
            key == GLFW.GLFW_KEY_DELETE    -> f.deleteForward()
            key == GLFW.GLFW_KEY_LEFT      -> if (ctrl) f.wordMove(false, shift) else f.move(-1, shift)
            key == GLFW.GLFW_KEY_RIGHT     -> if (ctrl) f.wordMove(true,  shift) else f.move( 1, shift)
            key == GLFW.GLFW_KEY_HOME      -> f.home(shift)
            key == GLFW.GLFW_KEY_END       -> f.end(shift)
            key == GLFW.GLFW_KEY_TAB -> {
                val maxInputs = when (state) { State.ADD_CRACKED -> 1; else -> 3 }
                activeInput = (activeInput + if (shift) maxInputs - 1 else 1) % maxInputs
                return true
            }
            key == GLFW.GLFW_KEY_ENTER -> { submitForm(); return true }
            else -> return super.keyPressed(event)
        }
        f.clampScroll(visW)
        return true
    }

    override fun charTyped(event: CharacterEvent): Boolean {
        if (searchActive) {
            searchField.insert(event.codepointAsString())
            searchField.clampScroll(PNL_W - 40)
            listScroll = 0
            return true
        }
        if (activeInput >= 0) {
            fields[activeInput].insert(event.codepointAsString())
            fields[activeInput].clampScroll(PNL_W - 40)
            formError = ""
            return true
        }
        return false
    }

    private fun parseMcToken(raw: String): String {
        val s     = raw.trim()
        val match = Regex("mctoken\\s*[=:]\\s*", RegexOption.IGNORE_CASE).find(s)
        return if (match != null) s.substring(match.range.last + 1).trim() else s
    }

    private fun submitForm() {
        formError = ""
        val username = fields[0].get().trim()
        if (username.isBlank() && state != State.ADD_TOKEN) { formError = "Username cannot be empty"; return }

        when (state) {
            State.ADD_CRACKED -> {
                AltManager.addAccount(AltAccount(type = AltType.CRACKED, username = username))
                clearForm(); state = State.LIST
            }
            State.ADD_TOKEN -> {
                val token     = fields[2].get().trim()
                val uuidInput = fields[1].get().trim()
                if (token.isBlank()) { formError = "Access token cannot be empty"; return }
                formError = "Fetching profile\u2026"
                Thread {
                    try {
                        val resolvedName: String
                        val resolvedUuid: String
                        if (username.isBlank()) {
                            val fetched = AltManager.fetchMcProfile(token)
                            if (fetched == null) { formError = "Cannot fetch profile \u2013 enter username manually"; return@Thread }
                            resolvedName = fetched.first
                            resolvedUuid = if (uuidInput.isBlank()) fetched.second.toString() else uuidInput
                        } else {
                            resolvedName = username
                            resolvedUuid = uuidInput
                        }
                        val acc = AltAccount(type = AltType.ACCESS_TOKEN, username = resolvedName,
                            uuid = resolvedUuid, token = token)
                        AltManager.addAccount(acc)
                        minecraft!!.execute { clearForm(); state = State.LIST }
                    } catch (e: Exception) {
                        formError = "Error: ${e.message?.take(60) ?: "unknown"}"
                    }
                }.also { it.isDaemon = true }.start()
            }
            else -> {}
        }
    }

    private fun clearForm() {
        fields.forEach { it.clear() }
        activeInput = -1
        formError   = ""
    }

    private fun startMicrosoftAuth() {
        cancelMsAuth()
        msState     = MsState.REQUESTING
        msDeviceInfo = null
        msProfile    = null
        msErrorMsg   = ""
        val t = Thread {
            try {
                val info = AltManager.requestDeviceCode()
                Minecraft.getInstance().execute { msDeviceInfo = info; msState = MsState.SHOWING_CODE }
                Thread.sleep(1_500L)
                Minecraft.getInstance().execute { msState = MsState.WAITING }
                msPollThread = AltManager.pollForAuth(info.deviceCode, info.interval) { profile ->
                    if (profile != null) { msProfile = profile; msState = MsState.SUCCESS }
                    else { msErrorMsg = "Login cancelled or timed out"; msState = MsState.FAILED }
                }
            } catch (e: Exception) {
                Minecraft.getInstance().execute { msErrorMsg = e.message ?: "Unknown error"; msState = MsState.FAILED }
            }
        }
        t.isDaemon = true
        t.name     = "medved-ms-device-code"
        t.start()
    }

    private fun isOver(mx: Int, my: Int, x: Int, y: Int, w: Int, h: Int) =
        mx in x until x + w && my in y until y + h

    private fun wrapText(text: String, maxWidthPx: Int): List<String> {
        val lines = mutableListOf<String>()
        for (rawLine in text.replace("\r\n", "\n").split("\n")) {
            var line = ""
            for (word in rawLine.split(" ")) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (FONT.width(styled(candidate)) <= maxWidthPx) line = candidate
                else { if (line.isNotEmpty()) lines.add(line); line = word }
            }
            if (line.isNotEmpty()) lines.add(line)
        }
        return lines
    }

    private fun <T> MutableList<T>.swap(i: Int, j: Int) {
        val tmp = this[i]; this[i] = this[j]; this[j] = tmp
           msState = MsState.FAILED
                }
            }

