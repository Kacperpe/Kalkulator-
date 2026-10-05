package pl.kacper.kalkulator

import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.FileProvider
import java.io.File
import kotlin.math.abs

/** Krzyżak nawigacyjny: lewo/prawo przesuwa kursor, góra/dół przegląda historię. */
class DPadView(context: Context, private val onDir: (Int) -> Unit) : View(context) {
    private val d = resources.displayMetrics.density
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF4E4D54.toInt() }
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF66656D.toInt()
        style = Paint.Style.STROKE
        strokeWidth = d
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF0C0C0E.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF0C0C0E.toInt() }
    private val arrow = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFB9B9C0.toInt() }
    private val rect = RectF()
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val r = 26 * d
        rect.set(d, d, w - d, h - d)
        canvas.drawRoundRect(rect, r, r, fill)
        canvas.drawRoundRect(rect, r, r, edge)
        val inset = 10 * d
        canvas.drawLine(inset, inset, w - inset, h - inset, line)
        canvas.drawLine(w - inset, inset, inset, h - inset, line)
        canvas.drawCircle(w / 2, h / 2, 9 * d, dot)
        val t = 5.5f * d
        tri(canvas, w / 2, h * 0.15f, 0f, -1f, t)
        tri(canvas, w / 2, h * 0.85f, 0f, 1f, t)
        tri(canvas, w * 0.13f, h / 2, -1f, 0f, t)
        tri(canvas, w * 0.87f, h / 2, 1f, 0f, t)
    }

    private fun tri(canvas: Canvas, cx: Float, cy: Float, dx: Float, dy: Float, t: Float) {
        val bx = cx - dx * t
        val by = cy - dy * t
        path.reset()
        path.moveTo(cx + dx * t, cy + dy * t)
        path.lineTo(bx + dy * t, by + dx * t)
        path.lineTo(bx - dy * t, by - dx * t)
        path.close()
        canvas.drawPath(path, arrow)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val nx = (event.x - width / 2f) / width
            val ny = (event.y - height / 2f) / height
            val dir = if (abs(nx) > abs(ny)) (if (nx < 0) 0 else 1) else (if (ny < 0) 2 else 3)
            onDir(dir)
        }
        return true
    }
}

class MainActivity : Activity() {

    private companion object {
        const val REQ_CAMERA = 71
        const val REQ_GALLERY = 72
    }

    private class Tok(val d: String, val c: String)
    private class Hist(val toks: List<Tok>, val value: Double)
    private class Style(
        val top: Int, val bottom: Int, val stroke: Int, val radius: Int,
        val text: Int, val size: Float, val btnWeight: Float
    )

    // ---- stan ----
    private val tokens = ArrayList<Tok>()
    private var cursor = 0
    private var angle = AngleMode.DEG
    private var shift = false
    private var alpha = false
    private var hyp = false
    private var fracMode = false
    private var engMode = false
    private var pending = 0 // 0 brak, 1 STO, 2 RCL
    private var lastAns = 0.0
    private var preAns = 0.0
    private var lastResult: Double? = null
    private var justEvaluated = false
    private var message: String? = null
    private var isError = false
    private val vars = HashMap<Char, Double>()
    private val history = ArrayList<Hist>()
    private var histPos = -1
    private val fnNames = arrayOf("f", "g", "h")
    private val fnDisp = arrayOf("", "", "")
    private val fnCode = arrayOf("", "", "")
    private var statsText = ""
    private var matA = ""
    private var matB = ""
    private var drawer: View? = null
    private var scrim: View? = null
    private var drawerOpen = false
    private var drawerWidth = 0
    private var recognizer: FormulaRecognizer? = null

    // ---- widoki ----
    private lateinit var statusView: TextView
    private lateinit var exprView: TextView
    private lateinit var resultView: TextView
    private lateinit var angleBtn: Button
    private lateinit var lastBtn: Button

    // ---- kolory i style (ustawiane przez applyTheme) ----
    private var theme = 0
    private var cBg = 0
    private var cDot = 0
    private var cText = 0
    private var cShift = 0
    private var cAlpha = 0
    private var cDim = 0
    private var cPanel = 0
    private lateinit var fnStyle: Style
    private lateinit var numStyle: Style
    private lateinit var pillStyle: Style
    private val orangeStyle = Style(0xFFF0A068.toInt(), 0xFFE08850.toInt(), 0xFFF5B98C.toInt(), 16, Color.BLACK, 22f, 110f)
    private val shiftStyle = Style(0xFFFFD04A.toInt(), 0xFFF5B81F.toInt(), 0xFFFFE08A.toInt(), 14, Color.BLACK, 15f, 95f)
    private val alphaStyle = Style(0xFF8B7BD6.toInt(), 0xFF6D5DBB.toInt(), 0xFFA596E6.toInt(), 14, Color.WHITE, 15f, 95f)
    private val themeNames = arrayOf<CharSequence>("Ciemna", "Jasna", "Granatowa")

    private fun col(v: Long): Int = v.toInt()

    private fun applyTheme(i: Int) {
        theme = i.coerceIn(0, 2)
        when (theme) {
            1 -> {
                cBg = col(0xFFE9EAEE); cDot = col(0xFFD3D5DC); cText = col(0xFF1B1C20)
                cShift = col(0xFFA86F00); cAlpha = col(0xFF5B45C9); cDim = col(0xFFC3C5CD)
                fnStyle = Style(col(0xFFFFFFFF), col(0xFFE2E3E9), col(0xFFC4C6CE), 14, cText, 15f, 95f)
                numStyle = Style(col(0xFF3C3D44), col(0xFF2A2B30), col(0xFF4C4D55), 16, Color.WHITE, 27f, 110f)
                pillStyle = Style(col(0xFFFFFFFF), col(0xFFE2E3E9), col(0xFFC4C6CE), 22, cText, 13f, 1f)
            }
            2 -> {
                cBg = col(0xFF0F1B2D); cDot = col(0xFF1B2C46); cText = col(0xFFE8EEF8)
                cShift = col(0xFFFFC629); cAlpha = col(0xFF9DB4FF); cDim = col(0xFF2A3B57)
                fnStyle = Style(col(0xFF2F4466), col(0xFF22344F), col(0xFF3F5A85), 14, cText, 15f, 95f)
                numStyle = Style(col(0xFF18263C), col(0xFF101B2D), col(0xFF2A3D5C), 16, Color.WHITE, 27f, 110f)
                pillStyle = Style(col(0xFF1A2940), col(0xFF121E31), col(0xFF34496B), 22, cText, 13f, 1f)
            }
            else -> {
                cBg = col(0xFF15161A); cDot = col(0xFF22242A); cText = col(0xFFE8E8EC)
                cShift = col(0xFFFFC629); cAlpha = col(0xFFA08CFF); cDim = col(0xFF30333A)
                fnStyle = Style(col(0xFF4B4A51), col(0xFF38373D), col(0xFF5C5B63), 14, cText, 15f, 95f)
                numStyle = Style(col(0xFF2C2C31), col(0xFF1F1F23), col(0xFF3B3B42), 16, Color.WHITE, 27f, 110f)
                pillStyle = Style(col(0xFF2A2B30), col(0xFF1E1F23), col(0xFF4A4B52), 22, cText, 13f, 1f)
            }
        }
        cPanel = when (theme) {
            1 -> col(0xFFFFFFFF)
            2 -> col(0xFF14233A)
            else -> col(0xFF1E1F24)
        }
    }

    private val contOps = setOf("+", "−", "×", "÷", "/", "!", "%", "P", "C", "^", "^2", "^3", "^(-1)", "~", "mod")

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun lighten(c: Int): Int {
        val r = (Color.red(c) + (255 - Color.red(c)) * 0.25f).toInt()
        val g = (Color.green(c) + (255 - Color.green(c)) * 0.25f).toInt()
        val b = (Color.blue(c) + (255 - Color.blue(c)) * 0.25f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun keyBg(st: Style): Drawable {
        fun shape(t: Int, b: Int): GradientDrawable {
            val g = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(t, b))
            g.cornerRadius = dp(st.radius).toFloat()
            g.setStroke(dp(1), st.stroke)
            return g
        }
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), shape(lighten(st.top), lighten(st.bottom)))
        sl.addState(intArrayOf(), shape(st.top, st.bottom))
        return sl
    }

    private fun dottedBg(): Drawable {
        val n = dp(6)
        val bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(cBg)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = cDot
        c.drawCircle(n / 2f, n / 2f, dp(1) * 0.6f, paint)
        val bd = BitmapDrawable(resources, bmp)
        bd.setTileModeXY(Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        return bd
    }

    /** Mini-znaczniki: ^{..} indeks górny, _{..} indeks dolny. */
    private fun mk(src: String): CharSequence {
        val sb = SpannableStringBuilder()
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            val end = if ((ch == '^' || ch == '_') && i + 1 < src.length && src[i + 1] == '{') src.indexOf('}', i) else -1
            if (end > 0) {
                val start = sb.length
                sb.append(src.substring(i + 2, end))
                val span: Any = if (ch == '^') SuperscriptSpan() else SubscriptSpan()
                sb.setSpan(span, start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.65f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i = end + 1
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb
    }

    // ---- logika ----
    private fun na() {
        message = "Funkcja niedostępna"
        isError = false
    }

    private fun note(text: String) {
        message = text
        isError = false
    }

    private fun ins(d: String, c: String = d) {
        lastResult = null
        if (justEvaluated) {
            tokens.clear()
            if (c in contOps) tokens.add(Tok("Ans", "Ans"))
            cursor = tokens.size
            justEvaluated = false
        }
        cursor = cursor.coerceIn(0, tokens.size)
        tokens.add(cursor, Tok(d, c))
        cursor++
        histPos = -1
    }

    private fun insVar(v: Char) = ins(v.toString(), "\$" + v)

    private fun trig(name: String, inv: Boolean) {
        val h = if (hyp) "h" else ""
        hyp = false
        if (inv) ins("${name}${h}⁻¹(", "a${name}${h}(") else ins("${name}${h}(")
    }

    private fun leaveResultMode() {
        if (justEvaluated) {
            justEvaluated = false
            lastResult = null
            cursor = tokens.size
        }
    }

    private fun moveCursor(d: Int) {
        leaveResultMode()
        cursor = (cursor + d).coerceIn(0, tokens.size)
    }

    private fun del() {
        leaveResultMode()
        lastResult = null
        if (cursor > 0 && cursor <= tokens.size) {
            tokens.removeAt(cursor - 1)
            cursor--
        }
    }

    private fun clearAll() {
        tokens.clear()
        cursor = 0
        lastResult = null
        justEvaluated = false
        histPos = -1
    }

    private fun clearEverything() {
        clearAll()
        vars.clear()
        history.clear()
        lastAns = 0.0
        preAns = 0.0
        note("Wyczyszczono wszystko")
    }

    private fun compute(): Double? {
        if (tokens.isEmpty()) return null
        return try {
            Evaluator(angle, lastAns, allVars(), fnBodies()).evaluate(currentCode())
        } catch (e: CalcException) {
            message = e.message
            isError = true
            null
        } catch (e: Exception) {
            message = "Błąd składni"
            isError = true
            null
        }
    }

    private fun calculate(): Double? {
        if (justEvaluated) return lastResult
        val v = compute()
        if (v == null) {
            lastResult = null
            return null
        }
        preAns = lastAns
        lastAns = v
        lastResult = v
        justEvaluated = true
        history.add(Hist(ArrayList(tokens), v))
        if (history.size > 5000) history.removeAt(0)
        histPos = -1
        return v
    }

    private fun store(name: Char) {
        val r = calculate() ?: return
        vars[name] = r
        note("→ " + name)
    }

    private fun mAdd(sign: Double) {
        val r = calculate() ?: return
        val m = (vars['m'] ?: 0.0) + sign * r
        vars['m'] = m
        note("M = " + Formatter.format(m))
    }

    private fun load(h: Hist) {
        tokens.clear()
        tokens.addAll(h.toks)
        cursor = tokens.size
        lastResult = h.value
        justEvaluated = false
    }

    private fun histMove(d: Int) {
        if (history.isEmpty()) return
        if (histPos < 0) {
            if (d > 0) return
            histPos = history.size - 1
        } else {
            histPos = (histPos + d).coerceIn(0, history.size - 1)
        }
        load(history[histPos])
    }

    private fun showHistory() {
        if (history.isEmpty()) {
            note("Brak historii")
            return
        }
        val items: Array<CharSequence> = Array(history.size) { i ->
            val h = history[history.size - 1 - i]
            h.toks.joinToString("") { it.d } + " = " + Formatter.format(h.value)
        }
        AlertDialog.Builder(this)
            .setTitle("Historia")
            .setItems(items) { _, i ->
                histPos = history.size - 1 - i
                load(history[histPos])
                refresh()
            }
            .show()
    }

    private fun copyResult() {
        val r = lastResult
        if (r == null) {
            note("Brak wyniku")
            return
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("wynik", Formatter.format(r).replace("×10^", "E")))
        note("Skopiowano")
    }

    private fun paste() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).text?.toString() else null
        val num = text?.trim()?.replace(',', '.')?.toDoubleOrNull()
        if (num == null || num.isNaN() || num.isInfinite()) {
            note("Schowek nie zawiera liczby")
            return
        }
        ins(Formatter.format(num), "(" + num.toString() + ")")
    }

    private fun cycleAngle() {
        angle = AngleMode.values()[(angle.ordinal + 1) % AngleMode.values().size]
    }

    // ---- budowanie klawiszy ----
    private fun key(
        label: CharSequence,
        sLab: String = "",
        aLab: String = "",
        st: Style = fnStyle,
        variable: Char? = null,
        labeled: Boolean = true,
        raw: Boolean = false,
        s: (() -> Unit)? = null,
        a: (() -> Unit)? = null,
        n: () -> Unit
    ): View {
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        lp.setMargins(dp(3), 0, dp(3), dp(3))
        col.layoutParams = lp

        if (labeled) {
            val aText = if (aLab.isEmpty() && variable != null) variable.toString() else aLab
            val lab = SpannableStringBuilder()
            if (sLab.isNotEmpty()) {
                lab.append(mk(sLab))
                lab.setSpan(ForegroundColorSpan(cShift), 0, lab.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (aText.isNotEmpty()) {
                if (lab.length > 0) lab.append("  ")
                val start = lab.length
                lab.append(mk(aText))
                lab.setSpan(ForegroundColorSpan(cAlpha), start, lab.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val top = TextView(this)
            top.text = lab
            top.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            top.gravity = Gravity.CENTER
            top.maxLines = 1
            top.setTextColor(cShift)
            top.setAutoSizeTextTypeUniformWithConfiguration(7, 13, 1, TypedValue.COMPLEX_UNIT_SP)
            top.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 55f)
            col.addView(top)
        }

        val btn = Button(this)
        btn.isAllCaps = false
        btn.text = label
        btn.typeface = Typeface.MONOSPACE
        btn.textSize = st.size
        btn.setTextColor(st.text)
        btn.background = keyBg(st)
        btn.stateListAnimator = null
        btn.minHeight = 0
        btn.minimumHeight = 0
        btn.minWidth = 0
        btn.minimumWidth = 0
        btn.setPadding(0, 0, 0, 0)
        btn.maxLines = 1
        btn.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, st.btnWeight)
        btn.setOnClickListener {
            message = null
            isError = false
            if (raw) {
                n()
            } else {
                val sh = shift
                val al = alpha
                val pend = pending
                shift = false
                alpha = false
                pending = 0
                if (pend != 0 && variable != null) {
                    if (pend == 1) store(variable) else insVar(variable)
                } else if (sh) {
                    if (s != null) s() else na()
                } else if (al) {
                    if (a != null) a() else if (variable != null) insVar(variable) else na()
                } else {
                    n()
                }
            }
            refresh()
        }
        col.addView(btn)
        lastBtn = btn
        return col
    }

    private fun num(
        label: String,
        sLab: String = "",
        aLab: String = "",
        s: (() -> Unit)? = null,
        a: (() -> Unit)? = null
    ): View = key(label, sLab, aLab, numStyle, s = s, a = a) { ins(label) }

    private fun weighted(v: View, w: Float): View {
        (v.layoutParams as LinearLayout.LayoutParams).weight = w
        return v
    }

    private fun row(weight: Float, vararg views: View): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, weight)
        for (v in views) r.addView(v)
        return r
    }

    private fun group(vararg rows: View): LinearLayout {
        val g = LinearLayout(this)
        g.orientation = LinearLayout.VERTICAL
        g.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
        for (v in rows) g.addView(v)
        return g
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getPreferences(Context.MODE_PRIVATE)
        for (i in 0..2) {
            fnDisp[i] = prefs.getString("fn" + i + "d", "") ?: ""
            fnCode[i] = prefs.getString("fn" + i + "c", "") ?: ""
        }
        applyTheme(prefs.getInt("theme", 0))
        buildUi()
    }

    private fun savePrefs() {
        val e = getPreferences(Context.MODE_PRIVATE).edit()
        e.putInt("theme", theme)
        for (i in 0..2) {
            e.putString("fn" + i + "d", fnDisp[i])
            e.putString("fn" + i + "c", fnCode[i])
        }
        e.apply()
    }

    @Suppress("DEPRECATION")
    private fun buildUi() {
        window.statusBarColor = cBg
        window.navigationBarColor = cBg
        window.decorView.systemUiVisibility =
            if (theme == 1) (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR) else 0

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = dottedBg()
        root.setPadding(dp(6), dp(6), dp(6), dp(6))

        // linia wskaźników
        statusView = TextView(this)
        statusView.typeface = Typeface.MONOSPACE
        statusView.textSize = 13f
        statusView.maxLines = 1
        statusView.setPadding(dp(8), dp(2), dp(8), dp(4))
        root.addView(statusView)

        // wyświetlacz
        val panel = LinearLayout(this)
        panel.orientation = LinearLayout.VERTICAL
        val panelBg = GradientDrawable()
        panelBg.setColor(0xFFD3E3E3.toInt())
        panelBg.cornerRadius = dp(14).toFloat()
        panel.background = panelBg
        panel.setPadding(dp(14), dp(10), dp(14), dp(8))
        val panelLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 400f)
        panelLp.setMargins(dp(3), 0, dp(3), dp(8))
        panel.layoutParams = panelLp

        exprView = TextView(this)
        exprView.textSize = 24f
        exprView.typeface = Typeface.MONOSPACE
        exprView.gravity = Gravity.START or Gravity.TOP
        exprView.setTextColor(0xFF1B2A2A.toInt())
        exprView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)

        resultView = TextView(this)
        resultView.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        resultView.gravity = Gravity.END or Gravity.CENTER_VERTICAL
        resultView.maxLines = 1
        resultView.setTextColor(0xFF0F1F1F.toInt())
        resultView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54))
        resultView.setAutoSizeTextTypeUniformWithConfiguration(16, 38, 1, TypedValue.COMPLEX_UNIT_SP)

        panel.addView(exprView)
        panel.addView(resultView)
        root.addView(panel)

        // pasek pigułek
        val spacer = View(this)
        spacer.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.6f)
        val pillHist = key("≡", st = pillStyle, labeled = false, raw = true) { openDrawer() }
        val pillCopy = weighted(key("COPY", st = pillStyle, labeled = false) { copyResult() }, 1.3f)
        val pillPaste = weighted(key("PASTE", st = pillStyle, labeled = false) { paste() }, 1.3f)
        val pillAngle = key("DEG", st = pillStyle, labeled = false) { cycleAngle() }
        angleBtn = lastBtn
        root.addView(row(95f, pillHist, pillCopy, pillPaste, spacer, pillAngle))

        // SHIFT / ALPHA + krzyżak + MODE / 2nd
        val leftGroup = group(
            row(
                95f,
                key("SHIFT", st = shiftStyle, labeled = false, raw = true) {
                    shift = !shift
                    alpha = false
                },
                key("ALPHA", st = alphaStyle, labeled = false, raw = true) {
                    alpha = !alpha
                    shift = false
                }
            ),
            row(
                150f,
                key("CALC", "SOLVE", "=", s = { solveDialog() }, a = { ins("=") }) { na() },
                key(
                    "∫dx", "d/dx", ";",
                    s = { ins("d/dx(", "diff(") },
                    a = { ins(",") }
                ) { ins("∫(", "int(") }
            )
        )
        val dpad = DPadView(this) { dir ->
            message = null
            isError = false
            pending = 0
            when (dir) {
                0 -> moveCursor(-1)
                1 -> moveCursor(1)
                2 -> histMove(-1)
                else -> histMove(1)
            }
            refresh()
        }
        val dpadLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2.2f)
        dpadLp.setMargins(dp(5), dp(2), dp(5), dp(6))
        dpad.layoutParams = dpadLp
        val rightGroup = group(
            row(
                95f,
                key("MODE", labeled = false) { cycleAngle() },
                key("2nd", labeled = false) { na() }
            ),
            row(
                150f,
                key(mk("x^{-1}"), "x!", s = { ins("!") }) { ins("⁻¹", "^(-1)") },
                key(
                    mk("Log_{x}y"), "Σ", "Π",
                    s = { ins("Σ(", "sum(") },
                    a = { ins("Π(", "prod(") }
                ) { ins("logxy(", "logb(") }
            )
        )
        root.addView(row(245f, leftGroup, dpad, rightGroup))

        // rzędy funkcji
        root.addView(
            row(
                150f,
                key("x/y", "", "÷R") { ins("/") },
                key(mk("√x"), "^{3}√x", "mod", s = { ins("³√(", "cbrt(") }, a = { ins("mod") }) { ins("√(", "sqrt(") },
                key(mk("x^{2}"), "x^{3}", s = { ins("³", "^3") }) { ins("²", "^2") },
                key(mk("x^{y}"), "^{x}√y", s = { ins("ˣ√", "~") }) { ins("^") },
                key("Log", "10^{x}", s = { ins("10^(") }) { ins("log(") },
                key("Ln", "e^{x}", variable = 't', s = { ins("e^(") }) { ins("ln(") }
            )
        )
        root.addView(
            row(
                150f,
                key("(-)", "∠", variable = 'a') { ins("-") },
                key("°'\"", "FACT", variable = 'b', s = { numberTheory() }) { na() },
                key("hyp", "|x|", variable = 'c', s = { ins("abs(") }) { hyp = !hyp },
                key("Sin", "Sin^{-1}", variable = 'd', s = { trig("sin", true) }) { trig("sin", false) },
                key("Cos", "Cos^{-1}", s = { trig("cos", true) }) { trig("cos", false) },
                key("Tan", "Tan^{-1}", variable = 'f', s = { trig("tan", true) }) { trig("tan", false) }
            )
        )
        root.addView(
            row(
                150f,
                key(
                    "RCL", "STO", "CLRv",
                    s = { pending = 1 },
                    a = {
                        vars.clear()
                        note("Wyczyszczono zmienne")
                    }
                ) { pending = 2 },
                key("ENG", "i", "Cot", a = { ins("cot(") }) { engMode = !engMode },
                key("(", "%", "Cot^{-1}", s = { ins("%") }, a = { ins("cot⁻¹(", "acot(") }) { ins("(") },
                key(")", ",", variable = 'x', s = { ins(",") }) { ins(")") },
                key("S⇔D", "", variable = 'y') { fracMode = !fracMode },
                key("M+", "M−", variable = 'm', s = { mAdd(-1.0) }) { mAdd(1.0) }
            )
        )

        // cyfry
        root.addView(
            row(
                165f,
                num("7", "CONST"),
                num("8", "CONV", "SI"),
                num("9", "Limit", "∞"),
                key("DEL", st = orangeStyle) { del() },
                key("AC", "CLR ALL", st = orangeStyle, s = { clearEverything() }) { clearAll() }
            )
        )
        root.addView(
            row(
                165f,
                num("4", "MATRIX", s = { matrixDialog() }),
                num("5", "VECTOR"),
                num("6", "FUNC HELP", s = { functionsDialog() }),
                num("×", "nPr", "GCD", s = { ins("P") }, a = { ins("gcd(") }),
                num("÷", "nCr", "LCM", s = { ins("C") }, a = { ins("lcm(") })
            )
        )
        root.addView(
            row(
                165f,
                num("1", "STAT", s = { statsDialog() }),
                num("2", "CMPLX"),
                num("3", "DISTR"),
                num("+", "Pol", "Ceil", a = { ins("ceil(") }),
                num("−", "Rec", "Floor", a = { ins("floor(") })
            )
        )
        root.addView(
            row(
                165f,
                num("0", "COPY", "PASTE", s = { copyResult() }, a = { paste() }),
                num(".", "Ran#", "RanInt", s = { ins("Ran#", "rand") }, a = { ins("RanInt(", "ranint(") }),
                key("Exp", "π", "e", numStyle, s = { ins("π") }, a = { ins("e") }) { ins("E") },
                key("Ans", "", "PreAns", numStyle, a = { ins("PreAns", "\$p") }) { ins("Ans") },
                key("=", "History", "", numStyle, s = { showHistory() }) { calculate() }
            )
        )

        // panel boczny wysuwany z lewej
        val frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val sc = View(this)
        sc.setBackgroundColor(0x99000000.toInt())
        sc.visibility = View.GONE
        sc.setOnClickListener { closeDrawer() }
        frame.addView(sc, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        drawerWidth = (resources.displayMetrics.widthPixels * 0.8f).toInt()
        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        drawerHeader(list, "ZE ZDJĘCIA")
        drawerItem(list, "Zrób zdjęcie równania") { takePhoto() }
        drawerItem(list, "Wybierz zdjęcie z galerii") { pickImage() }
        drawerHeader(list, "NARZĘDZIA")
        drawerItem(list, "Historia") { showHistory() }
        drawerItem(list, "Wykres funkcji") { graphDialog() }
        drawerItem(list, "Rozwiąż równanie") { solveDialog() }
        drawerItem(list, "Własne funkcje f, g, h") { functionsDialog() }
        drawerItem(list, "Teoria liczb") { numberTheory() }
        drawerItem(list, "Postacie liczby") { numberForms() }
        drawerItem(list, "Statystyka") { statsDialog() }
        drawerItem(list, "Macierze") { matrixDialog() }
        drawerHeader(list, "WYGLĄD")
        drawerItem(list, "Skórka") { themeDialog() }
        val sv = ScrollView(this)
        sv.setBackgroundColor(cPanel)
        sv.addView(list)
        sv.translationX = -drawerWidth.toFloat()
        sv.elevation = dp(16).toFloat()
        sv.isClickable = true
        frame.addView(sv, FrameLayout.LayoutParams(drawerWidth, ViewGroup.LayoutParams.MATCH_PARENT))
        drawer = sv
        scrim = sc
        drawerOpen = false
        frame.setOnApplyWindowInsetsListener { _, insets ->
            root.setPadding(
                dp(6) + insets.systemWindowInsetLeft,
                dp(6) + insets.systemWindowInsetTop,
                dp(6) + insets.systemWindowInsetRight,
                dp(6) + insets.systemWindowInsetBottom
            )
            list.setPadding(insets.systemWindowInsetLeft, dp(8) + insets.systemWindowInsetTop, 0, dp(12) + insets.systemWindowInsetBottom)
            insets
        }

        setContentView(frame)
        frame.requestApplyInsets()
        refresh()
    }

    // ---- narzędzia ----
    private fun allVars(): HashMap<Char, Double> {
        val all = HashMap<Char, Double>(vars)
        all['p'] = preAns
        return all
    }

    private fun fnBodies(): Map<String, String> {
        val m = HashMap<String, String>()
        for (i in 0..2) if (fnCode[i].isNotEmpty()) m["u" + (i + 1)] = fnCode[i]
        return m
    }

    private fun balance(code: String): String {
        val open = code.count { it == '(' } - code.count { it == ')' }
        return if (open > 0) code + ")".repeat(open) else code
    }

    private fun codeOf(list: List<Tok>): String = balance(list.joinToString("") { it.c })
    private fun currentCode(): String = tokens.joinToString("") { it.c }
    private fun currentText(): String = tokens.joinToString("") { it.d }

    private fun exprFn(code: String): (Double) -> Double {
        val base = allVars()
        val bodies = fnBodies()
        val mode = angle
        val a = lastAns
        return { x ->
            val v = HashMap<Char, Double>(base)
            v['x'] = x
            Evaluator(mode, a, v, bodies).evaluate(code)
        }
    }

    private fun textDialog(title: String, body: String) {
        val tv = TextView(this)
        tv.text = body
        tv.typeface = Typeface.MONOSPACE
        tv.textSize = 14f
        tv.setTextIsSelectable(true)
        tv.setPadding(dp(22), dp(12), dp(22), dp(8))
        val sv = ScrollView(this)
        sv.addView(tv)
        AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("OK", null).show()
    }

    // ---- panel boczny ----
    private fun drawerHeader(parent: LinearLayout, text: String) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 12f
        tv.typeface = Typeface.DEFAULT_BOLD
        tv.setTextColor(cAlpha)
        tv.setPadding(dp(20), dp(18), dp(20), dp(6))
        parent.addView(tv)
    }

    private fun drawerItem(parent: LinearLayout, text: String, action: () -> Unit) {
        val tv = TextView(this)
        tv.text = text
        tv.textSize = 16f
        tv.setTextColor(cText)
        tv.setPadding(dp(20), dp(14), dp(20), dp(14))
        val pressed = GradientDrawable()
        pressed.setColor(cDim)
        val normal = GradientDrawable()
        normal.setColor(Color.TRANSPARENT)
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_pressed), pressed)
        sl.addState(intArrayOf(), normal)
        tv.background = sl
        tv.setOnClickListener {
            closeDrawer()
            message = null
            isError = false
            action()
            refresh()
        }
        parent.addView(tv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun openDrawer() {
        val dv = drawer ?: return
        val sc = scrim ?: return
        drawerOpen = true
        sc.visibility = View.VISIBLE
        sc.alpha = 0f
        sc.animate().alpha(1f).setDuration(180).start()
        dv.animate().translationX(0f).setDuration(180).start()
    }

    private fun closeDrawer() {
        val dv = drawer ?: return
        val sc = scrim ?: return
        drawerOpen = false
        sc.animate().alpha(0f).setDuration(150).withEndAction { sc.visibility = View.GONE }.start()
        dv.animate().translationX(-drawerWidth.toFloat()).setDuration(150).start()
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerOpen) closeDrawer() else super.onBackPressed()
    }

    // ---- wzór ze zdjęcia ----
    private fun photoFile(): File {
        val dir = File(cacheDir, "photos")
        dir.mkdirs()
        return File(dir, "shot.jpg")
    }

    @Suppress("DEPRECATION")
    private fun takePhoto() {
        try {
            val f = photoFile()
            if (f.exists()) f.delete()
            val uri = FileProvider.getUriForFile(this, packageName + ".files", f)
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            intent.clipData = ClipData.newRawUri("photo", uri)
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, REQ_CAMERA)
        } catch (e: Exception) {
            note("Nie udało się otworzyć aparatu")
        }
    }

    @Suppress("DEPRECATION")
    private fun pickImage() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = "image/*"
            startActivityForResult(intent, REQ_GALLERY)
        } catch (e: Exception) {
            note("Nie udało się otworzyć galerii")
        }
    }

    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAMERA && requestCode != REQ_GALLERY) return
        if (resultCode != RESULT_OK) return
        val bmp = try {
            if (requestCode == REQ_CAMERA) {
                val f = photoFile()
                PhotoUtil.decode({ if (f.exists()) f.inputStream() else null })
            } else {
                val u = data?.data
                if (u == null) null else PhotoUtil.decode({ contentResolver.openInputStream(u) })
            }
        } catch (e: Exception) {
            null
        }
        if (bmp == null) {
            note("Nie udało się wczytać zdjęcia")
            refresh()
            return
        }
        cropDialog(bmp)
    }

    private fun cropDialog(bmp: Bitmap) {
        val dlg = Dialog(this, android.R.style.Theme_Black_NoTitleBar)
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.BLACK)
        val hint = TextView(this)
        hint.text = "Obejmij ramką jeden wzór"
        hint.setTextColor(Color.WHITE)
        hint.textSize = 16f
        hint.gravity = Gravity.CENTER
        hint.setPadding(dp(12), dp(14), dp(12), dp(10))
        box.addView(hint)
        val cv = CropView(this, bmp)
        box.addView(cv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val bar = LinearLayout(this)
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(10), dp(8), dp(10), dp(12))
        val cancel = Button(this)
        cancel.text = "Anuluj"
        cancel.setOnClickListener { dlg.dismiss() }
        val ok = Button(this)
        ok.text = "Rozpoznaj"
        ok.setOnClickListener {
            val crop = cv.result()
            dlg.dismiss()
            recognize(crop)
        }
        bar.addView(cancel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(ok, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        box.addView(bar)
        dlg.setContentView(box)
        dlg.show()
    }

    @Synchronized
    private fun getRecognizer(): FormulaRecognizer {
        val existing = recognizer
        if (existing != null) return existing
        val dir = File(filesDir, "mfr")
        dir.mkdirs()
        val stamp = packageManager.getPackageInfo(packageName, 0).lastUpdateTime.toString()
        val mark = File(dir, "stamp")
        val fresh = mark.exists() && mark.readText() == stamp
        for (name in arrayOf("mfr_encoder.onnx", "mfr_decoder.onnx")) {
            val out = File(dir, name)
            if (!fresh || !out.exists()) {
                assets.open(name).use { input -> out.outputStream().use { output -> input.copyTo(output) } }
            }
        }
        mark.writeText(stamp)
        val vocab = assets.open("mfr_vocab.txt").bufferedReader(Charsets.UTF_8).use { it.readLines() }
        val r = FormulaRecognizer(File(dir, "mfr_encoder.onnx").path, File(dir, "mfr_decoder.onnx").path, vocab)
        recognizer = r
        return r
    }

    private fun recognize(crop: Bitmap) {
        val progress = AlertDialog.Builder(this).setMessage("Rozpoznaję wzór…").setCancelable(false).show()
        Thread {
            val outcome: Pair<String?, String?> = try {
                Pair(getRecognizer().recognize(PhotoUtil.toInput(crop)), null)
            } catch (t: Throwable) {
                Pair(null, t.javaClass.simpleName + ": " + (t.message ?: ""))
            }
            runOnUiThread {
                if (!isFinishing && !isDestroyed) {
                    progress.dismiss()
                    val latex = outcome.first
                    if (latex != null) showRecognized(latex) else textDialog("Błąd rozpoznawania", outcome.second ?: "")
                }
            }
        }.start()
    }

    private fun showRecognized(latex: String) {
        val pretty = Latex.compact(latex)
        try {
            val items = Latex.convert(latex)
            val shown = items.joinToString("") { it.d }
            AlertDialog.Builder(this).setTitle("Rozpoznany wzór")
                .setMessage("Odczytano:\n" + shown + "\n\nLaTeX:\n" + pretty)
                .setPositiveButton("Wstaw") { _, _ ->
                    clearAll()
                    for (it in items) tokens.add(Tok(it.d, it.c))
                    cursor = tokens.size
                    message = null
                    isError = false
                    if (items.any { it.c == "=" }) solveDialog()
                    else if (items.none { it.c.startsWith("\$") }) calculate()
                    refresh()
                }
                .setNegativeButton("Anuluj", null)
                .show()
        } catch (e: CalcException) {
            AlertDialog.Builder(this).setTitle("Rozpoznany wzór")
                .setMessage("LaTeX:\n" + pretty + "\n\nNie umiem tego przeliczyć:\n" + (e.message ?: ""))
                .setPositiveButton("Kopiuj LaTeX") { _, _ ->
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("LaTeX", pretty))
                }
                .setNegativeButton("OK", null)
                .show()
        }
    }

    private fun graphDialog() {
        val list = ArrayList<(Double) -> Double>()
        val names = ArrayList<String>()
        if (tokens.isNotEmpty() && tokens.none { it.c == "=" }) {
            list.add(exprFn(codeOf(tokens)))
            names.add(currentText())
        } else {
            for (i in 0..2) if (fnCode[i].isNotEmpty()) {
                list.add(exprFn(fnCode[i]))
                names.add(fnNames[i] + "(x)")
            }
        }
        if (list.isEmpty()) {
            note("Wpisz wzór z x (ALPHA, potem klawisz ')')")
            return
        }
        AlertDialog.Builder(this)
            .setTitle("y = " + names.joinToString(", ") + "   [" + angle.name + "]")
            .setView(GraphView(this, list))
            .setPositiveButton("Zamknij", null)
            .show()
    }

    private fun solveDialog() {
        if (tokens.isEmpty()) {
            note("Wpisz równanie z x")
            return
        }
        val eq = tokens.indexOfFirst { it.c == "=" }
        val code = if (eq < 0) codeOf(tokens)
        else "(" + codeOf(tokens.subList(0, eq)) + ")-(" + codeOf(tokens.subList(eq + 1, tokens.size)) + ")"
        val f = exprFn(code)
        try {
            f(0.1234567)
        } catch (e: CalcException) {
            if (e.message == "Błąd składni") {
                message = e.message
                isError = true
                return
            }
        } catch (e: Exception) {
            message = "Błąd składni"
            isError = true
            return
        }
        val roots = MathTools.solve(f, -100.0, 100.0)
        val sb = StringBuilder()
        sb.append(currentText()).append(if (eq < 0) " = 0" else "").append("\n\n")
        if (roots.isEmpty()) {
            sb.append("Brak pierwiastków rzeczywistych\nw przedziale [-100, 100].")
        } else {
            for ((i, r) in roots.take(50).withIndex()) {
                sb.append("x").append(if (roots.size > 1) (i + 1).toString() else "").append(" = ").append(Formatter.format(r))
                val fr = Formatter.toFraction(r)
                if (fr != null) sb.append("   (").append(fr).append(")")
                sb.append("\n")
            }
            sb.append("\nMetoda numeryczna, przedział [-100, 100].")
        }
        textDialog("Rozwiązanie równania", sb.toString())
    }

    private fun functionsDialog() {
        val items: Array<CharSequence> = Array(6) { i ->
            if (i < 3) "Wstaw " + fnNames[i] + "(x) = " + (if (fnDisp[i].isEmpty()) "—" else fnDisp[i])
            else "Zapisz bieżące wyrażenie jako " + fnNames[i - 3] + "(x)"
        }
        AlertDialog.Builder(this).setTitle("Własne funkcje").setItems(items) { _, i ->
            message = null
            isError = false
            if (i < 3) {
                if (fnCode[i].isEmpty()) note("Najpierw zdefiniuj " + fnNames[i] + "(x)")
                else ins(fnNames[i] + "(", "u" + (i + 1) + "(")
            } else {
                val k = i - 3
                if (tokens.isEmpty() || tokens.any { it.c == "=" }) {
                    note("Wpisz wzór z x i spróbuj ponownie")
                } else {
                    fnDisp[k] = currentText()
                    fnCode[k] = codeOf(tokens)
                    savePrefs()
                    note("Zapisano " + fnNames[k] + "(x)")
                }
            }
            refresh()
        }.show()
    }

    private fun resultOrNote(): Double? {
        val r = calculate()
        if (r == null && !isError) note("Najpierw wpisz liczbę")
        return r
    }

    private fun numberTheory() {
        val r = resultOrNote() ?: return
        if (r != Math.floor(r) || Math.abs(r) < 1 || Math.abs(r) > 1e13) {
            note("Potrzebna liczba całkowita od 1 do 10^13")
            return
        }
        val n = Math.abs(r).toLong()
        textDialog("Teoria liczb: " + n, MathTools.numberInfo(n))
    }

    private fun numberForms() {
        val r = resultOrNote() ?: return
        textDialog("Postacie liczby", MathTools.forms(r))
    }

    private fun inputBox(vararg fields: EditText): LinearLayout {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        for (f in fields) {
            f.typeface = Typeface.MONOSPACE
            f.gravity = Gravity.TOP or Gravity.START
            f.minLines = 3
            f.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            box.addView(f)
        }
        return box
    }

    private fun statsDialog() {
        val input = EditText(this)
        input.hint = "Liczby oddzielone spacją, np. 2 4 4 5 7\nRegresja: pary x;y, np. 1;3 2;5 3;7"
        input.setText(statsText)
        AlertDialog.Builder(this).setTitle("Statystyka").setView(inputBox(input))
            .setPositiveButton("Oblicz") { _, _ ->
                statsText = input.text.toString()
                textDialog("Statystyka", MathTools.statsReport(statsText))
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun matrixDialog() {
        val a = EditText(this)
        a.hint = "Macierz A: wiersze w nowych liniach,\nliczby oddzielone spacją"
        a.setText(matA)
        val b = EditText(this)
        b.hint = "Macierz B (opcjonalnie)"
        b.setText(matB)
        AlertDialog.Builder(this).setTitle("Macierze").setView(inputBox(a, b))
            .setPositiveButton("Oblicz") { _, _ ->
                matA = a.text.toString()
                matB = b.text.toString()
                textDialog("Macierze", MathTools.matrixReport(matA, matB))
            }
            .setNegativeButton("Anuluj", null)
            .show()
    }

    private fun themeDialog() {
        AlertDialog.Builder(this).setTitle("Skórka").setItems(themeNames) { _, i ->
            applyTheme(i)
            savePrefs()
            buildUi()
        }.show()
    }

    // ---- odświeżanie ekranu ----
    private fun refresh() {
        val sb = SpannableStringBuilder()
        if (justEvaluated) {
            sb.append(tokens.joinToString("") { it.d })
        } else {
            cursor = cursor.coerceIn(0, tokens.size)
            sb.append(tokens.subList(0, cursor).joinToString("") { it.d })
            val start = sb.length
            sb.append("|")
            sb.setSpan(ForegroundColorSpan(0xFF2E7D6B.toInt()), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append(tokens.subList(cursor, tokens.size).joinToString("") { it.d })
        }
        exprView.text = sb

        val res = lastResult
        if (isError && message != null) {
            resultView.text = message
            resultView.setTextColor(0xFFB00020.toInt())
        } else {
            resultView.setTextColor(0xFF0F1F1F.toInt())
            resultView.text = when {
                res == null -> ""
                engMode -> Formatter.eng(res)
                fracMode -> Formatter.toFraction(res) ?: Formatter.format(res)
                else -> Formatter.format(res)
            }
        }

        val st = SpannableStringBuilder()
        fun flag(text: String, on: Boolean) {
            val start = st.length
            st.append(text)
            st.setSpan(ForegroundColorSpan(if (on) cText else cDim), start, st.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            st.append("  ")
        }
        flag("S", shift)
        flag("A", alpha)
        flag("STO", pending == 1)
        flag("RCL", pending == 2)
        flag("M", (vars['m'] ?: 0.0) != 0.0)
        flag("HYP", hyp)
        flag(angle.name, true)
        flag(if (engMode) "ENG" else "NORM", true)
        flag("FRAC", fracMode)
        val msg = message
        if (msg != null && !isError) {
            val start = st.length
            st.append(msg)
            st.setSpan(ForegroundColorSpan(cShift), start, st.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        statusView.text = st

        angleBtn.text = angle.name
    }
}
