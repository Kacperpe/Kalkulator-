package pl.kacper.kalkulator

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import android.widget.LinearLayout
import android.widget.TextView
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

    // ---- widoki ----
    private lateinit var statusView: TextView
    private lateinit var exprView: TextView
    private lateinit var resultView: TextView
    private lateinit var angleBtn: Button
    private lateinit var lastBtn: Button

    // ---- kolory i style ----
    private val cBg = 0xFF15161A.toInt()
    private val cText = 0xFFE8E8EC.toInt()
    private val cShift = 0xFFFFC629.toInt()
    private val cAlpha = 0xFFA08CFF.toInt()
    private val cDim = 0xFF30333A.toInt()

    private val fnStyle = Style(0xFF4B4A51.toInt(), 0xFF38373D.toInt(), 0xFF5C5B63.toInt(), 14, cText, 15f, 95f)
    private val numStyle = Style(0xFF2C2C31.toInt(), 0xFF1F1F23.toInt(), 0xFF3B3B42.toInt(), 16, Color.WHITE, 27f, 110f)
    private val orangeStyle = Style(0xFFF0A068.toInt(), 0xFFE08850.toInt(), 0xFFF5B98C.toInt(), 16, Color.BLACK, 22f, 110f)
    private val shiftStyle = Style(0xFFFFD04A.toInt(), 0xFFF5B81F.toInt(), 0xFFFFE08A.toInt(), 14, Color.BLACK, 15f, 95f)
    private val alphaStyle = Style(0xFF8B7BD6.toInt(), 0xFF6D5DBB.toInt(), 0xFFA596E6.toInt(), 14, Color.WHITE, 15f, 95f)
    private val pillStyle = Style(0xFF2A2B30.toInt(), 0xFF1E1F23.toInt(), 0xFF4A4B52.toInt(), 22, cText, 13f, 1f)

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
        paint.color = 0xFF22242A.toInt()
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
            val all = HashMap<Char, Double>(vars)
            all['p'] = preAns
            Evaluator(angle, lastAns, all).evaluate(tokens.joinToString("") { it.c })
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
        if (history.size > 50) history.removeAt(0)
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

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.background = dottedBg()
        root.setPadding(dp(6), dp(6), dp(6), dp(6))
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                dp(6) + insets.systemWindowInsetLeft,
                dp(6) + insets.systemWindowInsetTop,
                dp(6) + insets.systemWindowInsetRight,
                dp(6) + insets.systemWindowInsetBottom
            )
            insets
        }

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
        val pillHist = key("≡", st = pillStyle, labeled = false) { showHistory() }
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
                key("CALC", "SOLVE", "=") { na() },
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
                key("°'\"", "FACT", variable = 'b') { na() },
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
                num("4", "MATRIX"),
                num("5", "VECTOR"),
                num("6", "FUNC HELP"),
                num("×", "nPr", "GCD", s = { ins("P") }, a = { ins("gcd(") }),
                num("÷", "nCr", "LCM", s = { ins("C") }, a = { ins("lcm(") })
            )
        )
        root.addView(
            row(
                165f,
                num("1", "STAT"),
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

        setContentView(root)
        refresh()
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
