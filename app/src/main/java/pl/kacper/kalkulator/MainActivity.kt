package pl.kacper.kalkulator

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {

    // ---- stan kalkulatora ----
    private val tokens = ArrayList<String>()
    private var cursor = 0
    private var angle = AngleMode.DEG
    private var shift = false
    private var hyp = false
    private var fracMode = false
    private var lastAns = 0.0
    private var lastResult: Double? = null
    private var justEvaluated = false
    private var errorMsg: String? = null

    // ---- widoki ----
    private lateinit var statusView: TextView
    private lateinit var exprView: TextView
    private lateinit var resultView: TextView
    private lateinit var shiftBtn: Button
    private lateinit var hypBtn: Button
    private lateinit var angleBtn: Button

    // ---- kolory ----
    private val cBg = 0xFF121418.toInt()
    private val cFn = 0xFF2A2E35.toInt()
    private val cNum = 0xFF1E2127.toInt()
    private val cAccent = 0xFFE8935A.toInt()
    private val cShift = 0xFFFFC629.toInt()
    private val cShiftActive = 0xFFFFE9A6.toInt()
    private val cEquals = 0xFF3A4150.toInt()
    private val cText = 0xFFE6E6E6.toInt()

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    private fun lighten(c: Int): Int {
        val r = (Color.red(c) + (255 - Color.red(c)) * 0.22f).toInt()
        val g = (Color.green(c) + (255 - Color.green(c)) * 0.22f).toInt()
        val b = (Color.blue(c) + (255 - Color.blue(c)) * 0.22f).toInt()
        return Color.rgb(r, g, b)
    }

    private fun roundBg(color: Int): StateListDrawable {
        fun shape(c: Int) = GradientDrawable().apply {
            setColor(c)
            cornerRadius = dp(12).toFloat()
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(lighten(color)))
            addState(intArrayOf(), shape(color))
        }
    }

    // ---- logika edycji ----
    private fun insert(t: String) {
        errorMsg = null
        lastResult = null
        if (justEvaluated) {
            val continuesAns = t.startsWith("^") || t in setOf("+", "−", "×", "÷", "!", "%", "P", "C")
            tokens.clear()
            if (continuesAns) tokens.add("Ans")
            cursor = tokens.size
            justEvaluated = false
        }
        tokens.add(cursor, t)
        cursor++
    }

    private fun leaveResultMode() {
        if (justEvaluated) {
            justEvaluated = false
            lastResult = null
            cursor = tokens.size
        }
        errorMsg = null
    }

    private fun moveCursor(d: Int) {
        leaveResultMode()
        cursor = (cursor + d).coerceIn(0, tokens.size)
    }

    private fun deleteToken() {
        leaveResultMode()
        if (cursor > 0) {
            tokens.removeAt(cursor - 1)
            cursor--
        }
    }

    private fun clearAll() {
        tokens.clear()
        cursor = 0
        lastResult = null
        errorMsg = null
        justEvaluated = false
    }

    private fun calculate() {
        if (tokens.isEmpty() || justEvaluated) return
        try {
            val v = Evaluator(angle, lastAns).evaluate(tokens.joinToString(""))
            lastAns = v
            lastResult = v
            errorMsg = null
            justEvaluated = true
        } catch (e: CalcException) {
            errorMsg = e.message
            lastResult = null
        } catch (e: Exception) {
            errorMsg = "Błąd składni"
            lastResult = null
        }
    }

    // ---- budowanie UI ----
    private class KeyViews(val col: LinearLayout, val btn: Button)

    private fun makeKey(
        label: String,
        shiftLabel: String,
        color: Int,
        textColor: Int,
        size: Float,
        act: (Boolean) -> Unit
    ): KeyViews {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                setMargins(dp(3), 0, dp(3), dp(2))
            }
        }
        val top = TextView(this).apply {
            text = shiftLabel
            setTextColor(cShift)
            textSize = 11f
            gravity = Gravity.CENTER
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0.3f)
        }
        val btn = Button(this).apply {
            text = label
            isAllCaps = false
            textSize = size
            setTextColor(textColor)
            background = roundBg(color)
            stateListAnimator = null
            minHeight = 0
            minimumHeight = 0
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setOnClickListener {
                val s = shift
                shift = false
                act(s)
                refresh()
            }
        }
        col.addView(top)
        col.addView(btn)
        return KeyViews(col, btn)
    }

    private fun fn(label: String, text: String, shiftLabel: String = "", shiftText: String = ""): KeyViews =
        makeKey(label, shiftLabel, cFn, cText, 15f) { s ->
            if (s && shiftText.isNotEmpty()) insert(shiftText) else insert(text)
        }

    private fun trig(name: String, shiftLabel: String): KeyViews =
        makeKey(name, shiftLabel, cFn, cText, 15f) { s ->
            val base = (if (s) "a" else "") + name + (if (hyp) "h" else "")
            hyp = false
            insert("$base(")
        }

    private fun num(label: String, text: String = label): KeyViews =
        makeKey(label, "", cNum, Color.WHITE, 24f) { insert(text) }

    private fun addRow(parent: LinearLayout, vararg keys: KeyViews) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        keys.forEach { row.addView(it.col) }
        parent.addView(row)
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = cBg
        window.navigationBarColor = cBg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cBg)
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                dp(8) + insets.systemWindowInsetLeft,
                dp(8) + insets.systemWindowInsetTop,
                dp(8) + insets.systemWindowInsetRight,
                dp(8) + insets.systemWindowInsetBottom
            )
            insets
        }

        // wyświetlacz
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(0xFFD3E3E3.toInt())
                cornerRadius = dp(14).toFloat()
            }
            setPadding(dp(14), dp(8), dp(14), dp(8))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2.6f).apply {
                bottomMargin = dp(8)
            }
        }
        statusView = TextView(this).apply {
            textSize = 12f
            typeface = Typeface.MONOSPACE
            setTextColor(0xFF37474F.toInt())
        }
        exprView = TextView(this).apply {
            textSize = 26f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.END or Gravity.BOTTOM
            setTextColor(0xFF1B2A2A.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        resultView = TextView(this).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            maxLines = 1
            setTextColor(0xFF0F1F1F.toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            setAutoSizeTextTypeUniformWithConfiguration(16, 38, 1, TypedValue.COMPLEX_UNIT_SP)
        }
        panel.addView(statusView)
        panel.addView(exprView)
        panel.addView(resultView)
        root.addView(panel)

        // rząd 1: sterowanie
        val kShift = makeKey("SHIFT", "", cShift, Color.BLACK, 13f) { s -> shift = !s }
        shiftBtn = kShift.btn
        val kAngle = makeKey("DEG", "", cFn, cText, 14f) { _ ->
            angle = AngleMode.values()[(angle.ordinal + 1) % AngleMode.values().size]
        }
        angleBtn = kAngle.btn
        val kLeft = makeKey("◀", "", cFn, cText, 16f) { _ -> moveCursor(-1) }
        val kRight = makeKey("▶", "", cFn, cText, 16f) { _ -> moveCursor(1) }
        val kSd = makeKey("S⇔D", "", cFn, cText, 14f) { _ -> fracMode = !fracMode }
        addRow(root, kShift, kAngle, kLeft, kRight, kSd, fn("x⁻¹", "^(-1)", "x!", "!"))

        // rząd 2
        addRow(
            root,
            fn("a/b", "÷"),
            fn("√", "√(", "∛", "cbrt("),
            fn("x²", "^2", "x³", "^3"),
            fn("xʸ", "^", "ʸ√x", "^(1/"),
            fn("log", "log(", "10ˣ", "10^("),
            fn("ln", "ln(", "eˣ", "e^(")
        )

        // rząd 3
        addRow(
            root,
            fn("(−)", "−"),
            fn("π", "π", "e", "e"),
            fn("|x|", "abs("),
            trig("sin", "sin⁻¹"),
            trig("cos", "cos⁻¹"),
            trig("tan", "tan⁻¹")
        )

        // rząd 4
        val kHyp = makeKey("hyp", "", cFn, cText, 15f) { _ -> hyp = !hyp }
        hypBtn = kHyp.btn
        addRow(
            root,
            fn("nPr", "P", "nCr", "C"),
            kHyp,
            fn("(", "("),
            fn(")", ")"),
            fn("%", "%"),
            fn("Ran#", "rand")
        )

        // cyfry
        addRow(
            root,
            num("7"), num("8"), num("9"),
            makeKey("DEL", "", cAccent, Color.BLACK, 18f) { _ -> deleteToken() },
            makeKey("AC", "", cAccent, Color.BLACK, 20f) { _ -> clearAll() }
        )
        addRow(root, num("4"), num("5"), num("6"), num("×"), num("÷"))
        addRow(root, num("1"), num("2"), num("3"), num("+"), num("−"))
        addRow(
            root,
            num("0"), num("."), num("×10ˣ", "E"),
            makeKey("Ans", "", cNum, Color.WHITE, 20f) { _ -> insert("Ans") },
            makeKey("=", "", cEquals, Color.WHITE, 26f) { _ -> calculate() }
        )

        setContentView(root)
        refresh()
    }

    // ---- odświeżanie ekranu ----
    private fun refresh() {
        val sb = SpannableStringBuilder()
        if (justEvaluated) {
            sb.append(tokens.joinToString(""))
        } else {
            sb.append(tokens.subList(0, cursor).joinToString(""))
            val start = sb.length
            sb.append("|")
            sb.setSpan(
                ForegroundColorSpan(0xFF2E7D6B.toInt()),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append(tokens.subList(cursor, tokens.size).joinToString(""))
        }
        exprView.text = sb

        val err = errorMsg
        val res = lastResult
        if (err != null) {
            resultView.text = err
            resultView.setTextColor(0xFFB00020.toInt())
        } else {
            resultView.setTextColor(0xFF0F1F1F.toInt())
            resultView.text = when {
                res == null -> ""
                fracMode -> Formatter.toFraction(res) ?: Formatter.format(res)
                else -> Formatter.format(res)
            }
        }

        statusView.text = listOfNotNull(
            if (shift) "SHIFT" else null,
            if (hyp) "HYP" else null,
            angle.name,
            if (fracMode) "FRAC" else null
        ).joinToString("   ")

        shiftBtn.background = roundBg(if (shift) cShiftActive else cShift)
        hypBtn.background = roundBg(if (hyp) cShift else cFn)
        hypBtn.setTextColor(if (hyp) Color.BLACK else cText)
        angleBtn.text = angle.name
    }
}
