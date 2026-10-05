package pl.kacper.kalkulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/** Obliczenia dla narzędzi: równania, teoria liczb, postacie liczby, statystyka, macierze. */
object MathTools {

    // ---------- równania ----------
    fun solve(f: (Double) -> Double, lo: Double = -100.0, hi: Double = 100.0): List<Double> {
        val roots = ArrayList<Double>()
        fun safe(x: Double): Double = try { f(x) } catch (e: Exception) { Double.NaN }
        fun add(r0: Double) {
            var r = if (abs(r0) < 1e-11) 0.0 else BigDecimal(r0).round(MathContext(12)).toDouble()
            if (r == -0.0) r = 0.0
            if (roots.none { abs(it - r) < 1e-6 * max(1.0, abs(r)) }) roots.add(r)
        }
        val n = 4000
        val h = (hi - lo) / n
        val xs = DoubleArray(n + 1) { lo + it * h }
        val ys = DoubleArray(n + 1) { safe(xs[it]) }
        for (i in 0..n) {
            val y = ys[i]
            if (y.isNaN()) continue
            if (y == 0.0) { add(xs[i]); continue }
            if (i < n && !ys[i + 1].isNaN() && y * ys[i + 1] < 0) {
                var a = xs[i]
                var b = xs[i + 1]
                var fa = y
                for (k in 0 until 80) {
                    val m = (a + b) / 2
                    val fm = safe(m)
                    if (fm.isNaN()) break
                    if (fa * fm <= 0) { b = m } else { a = m; fa = fm }
                }
                val r = (a + b) / 2
                val fr = safe(r)
                if (!fr.isNaN() && abs(fr) < 1e-6 * max(1.0, max(abs(y), abs(ys[i + 1])))) add(r)
            }
            // pierwiastek parzystej krotności: lokalne minimum |f| dotykające zera
            if (i in 1 until n && !ys[i - 1].isNaN() && !ys[i + 1].isNaN() &&
                abs(y) < abs(ys[i - 1]) && abs(y) < abs(ys[i + 1]) && y * ys[i - 1] > 0 && y * ys[i + 1] > 0
            ) {
                var a = xs[i - 1]
                var b = xs[i + 1]
                for (k in 0 until 120) {
                    val m1 = a + (b - a) / 3
                    val m2 = b - (b - a) / 3
                    if (abs(safe(m1)) < abs(safe(m2))) b = m2 else a = m1
                }
                val r = (a + b) / 2
                val fr = safe(r)
                if (!fr.isNaN() && abs(fr) < 1e-9) add(r)
            }
        }
        roots.sort()
        return roots
    }

    // ---------- teoria liczb ----------
    fun primeFactors(n0: Long): List<Pair<Long, Int>> {
        val out = ArrayList<Pair<Long, Int>>()
        var n = n0
        var pr = 2L
        while (pr * pr <= n) {
            if (n % pr == 0L) {
                var e = 0
                while (n % pr == 0L) { n /= pr; e++ }
                out.add(Pair(pr, e))
            }
            pr += if (pr == 2L) 1 else 2
        }
        if (n > 1) out.add(Pair(n, 1))
        return out
    }

    fun divisors(n: Long): List<Long> {
        var list = listOf(1L)
        for ((pr, e) in primeFactors(n)) {
            val next = ArrayList<Long>()
            var mult = 1L
            for (k in 0..e) {
                for (d in list) next.add(d * mult)
                mult *= pr
            }
            list = next
        }
        return list.sorted()
    }

    fun toRoman(n0: Int): String {
        val vals = intArrayOf(1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1)
        val syms = arrayOf("M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I")
        var n = n0
        val sb = StringBuilder()
        for (i in vals.indices) while (n >= vals[i]) { sb.append(syms[i]); n -= vals[i] }
        return sb.toString()
    }

    fun factorString(n: Long): String {
        if (n == 1L) return "1"
        return primeFactors(n).joinToString(" × ") { if (it.second == 1) it.first.toString() else "${it.first}^${it.second}" }
    }

    fun numberInfo(n: Long): String {
        val f = primeFactors(n)
        val prime = f.size == 1 && f[0].second == 1
        val divs = divisors(n)
        val sb = StringBuilder()
        sb.append("Rozkład na czynniki:\n  ").append(factorString(n)).append("\n\n")
        sb.append("Liczba pierwsza: ").append(if (prime) "tak" else "nie").append("\n\n")
        sb.append("Dzielniki (").append(divs.size).append("):\n  ")
        sb.append(divs.take(200).joinToString(" "))
        if (divs.size > 200) sb.append(" …")
        sb.append("\n\nBinarnie:\n  ").append(java.lang.Long.toString(n, 2))
        sb.append("\nÓsemkowo:\n  ").append(java.lang.Long.toString(n, 8))
        sb.append("\nSzesnastkowo:\n  ").append(java.lang.Long.toString(n, 16).uppercase(Locale.US))
        if (n <= 3999) sb.append("\nRzymskie:\n  ").append(toRoman(n.toInt()))
        return sb.toString()
    }

    // ---------- postacie liczby ----------
    fun repeating(num: Long, den: Long): String {
        val sb = StringBuilder()
        sb.append(num / den)
        var rem = num % den
        if (rem == 0L) return sb.toString()
        sb.append('.')
        val seen = HashMap<Long, Int>()
        var count = 0
        while (rem != 0L && count < 300) {
            val at = seen[rem]
            if (at != null) {
                sb.insert(at, "(")
                sb.append(")")
                return sb.toString()
            }
            seen[rem] = sb.length
            rem *= 10
            sb.append(rem / den)
            rem %= den
            count++
        }
        if (rem != 0L) sb.append("…")
        return sb.toString()
    }

    fun dms(v: Double): String {
        val a = abs(v)
        var d = floor(a).toLong()
        var m = floor((a - d) * 60).toLong()
        var s = Math.round(((a - d) * 60 - m) * 60 * 100) / 100.0
        if (s >= 60.0) { s -= 60.0; m += 1 }
        if (m >= 60) { m -= 60; d += 1 }
        return (if (v < 0) "-" else "") + d + "°" + m + "'" + Formatter.format(s) + "\""
    }

    fun sci(v: Double): String {
        if (v == 0.0) return "0"
        val parts = String.format(Locale.US, "%.9E", v).split("E")
        var mant = parts[0]
        if (mant.contains('.')) mant = mant.trimEnd('0').trimEnd('.')
        return mant + "×10^" + parts[1].toInt()
    }

    fun forms(v: Double): String {
        val sb = StringBuilder()
        sb.append("Dziesiętnie:\n  ").append(Formatter.format(v))
        val fr = Formatter.toFraction(v)
        if (fr != null) {
            val neg = fr.startsWith("-")
            val parts = fr.removePrefix("-").split("/")
            val h = parts[0].toLong()
            val k = parts[1].toLong()
            sb.append("\nUłamek:\n  ").append(fr)
            if (h > k) sb.append("\nLiczba mieszana:\n  ").append(if (neg) "-" else "").append(h / k).append(" ").append(h % k).append("/").append(k)
            sb.append("\nRozwinięcie okresowe:\n  ").append(if (neg) "-" else "").append(repeating(h, k))
        }
        sb.append("\nProcent:\n  ").append(Formatter.format(v * 100)).append(" %")
        sb.append("\nStopnie, minuty, sekundy:\n  ").append(dms(v))
        sb.append("\nNotacja naukowa:\n  ").append(sci(v))
        sb.append("\nNotacja inżynierska:\n  ").append(Formatter.eng(v))
        return sb.toString()
    }

    // ---------- statystyka ----------
    private fun line(sb: StringBuilder, name: String, v: Double) {
        sb.append(name.padEnd(9)).append("= ").append(Formatter.format(v)).append("\n")
    }

    private fun oneVar(sb: StringBuilder, xs: List<Double>, tag: String) {
        val n = xs.size
        val sum = xs.sum()
        val mean = sum / n
        val sumSq = xs.sumOf { it * it }
        val ss = xs.sumOf { (it - mean) * (it - mean) }
        val sorted = xs.sorted()
        val median = if (n % 2 == 1) sorted[n / 2] else (sorted[n / 2 - 1] + sorted[n / 2]) / 2
        line(sb, "n", n.toDouble())
        line(sb, "średnia $tag", mean)
        line(sb, "Σ$tag", sum)
        line(sb, "Σ$tag²", sumSq)
        line(sb, "σ$tag", sqrt(ss / n))
        if (n > 1) line(sb, "S$tag", sqrt(ss / (n - 1)))
        line(sb, "min", sorted[0])
        line(sb, "mediana", median)
        line(sb, "max", sorted[n - 1])
    }

    fun statsReport(text: String): String {
        val toks = text.replace(',', '.').trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (toks.isEmpty()) return "Brak danych."
        val sb = StringBuilder()
        if (toks.any { it.contains(';') }) {
            val xs = ArrayList<Double>()
            val ys = ArrayList<Double>()
            for (t in toks) {
                val p = t.split(";")
                val x = if (p.size == 2) p[0].toDoubleOrNull() else null
                val y = if (p.size == 2) p[1].toDoubleOrNull() else null
                if (x == null || y == null) return "Nie rozumiem: $t\nPary wpisuj jako x;y"
                xs.add(x)
                ys.add(y)
            }
            sb.append("— zmienna x —\n")
            oneVar(sb, xs, "x")
            sb.append("\n— zmienna y —\n")
            oneVar(sb, ys, "y")
            val n = xs.size
            val mx = xs.sum() / n
            val my = ys.sum() / n
            var sxy = 0.0
            var sxx = 0.0
            var syy = 0.0
            for (i in 0 until n) {
                sxy += (xs[i] - mx) * (ys[i] - my)
                sxx += (xs[i] - mx) * (xs[i] - mx)
                syy += (ys[i] - my) * (ys[i] - my)
            }
            sb.append("\n— regresja liniowa y = a + b·x —\n")
            if (n < 2 || sxx == 0.0) {
                sb.append("Za mało różnych wartości x.\n")
            } else {
                val b = sxy / sxx
                line(sb, "a", my - b * mx)
                line(sb, "b", b)
                if (syy > 0) {
                    val r = sxy / sqrt(sxx * syy)
                    line(sb, "r", r)
                    line(sb, "r²", r * r)
                }
            }
        } else {
            val xs = ArrayList<Double>()
            for (t in toks) xs.add(t.toDoubleOrNull() ?: return "Nie rozumiem: $t")
            oneVar(sb, xs, "x")
        }
        return sb.toString().trimEnd()
    }

    // ---------- macierze ----------
    fun parseMatrix(text: String): Array<DoubleArray>? {
        val rows = text.replace(',', '.').split('\n', ';').map { it.trim() }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return null
        val out = ArrayList<DoubleArray>()
        for (r in rows) {
            val vals = r.split(Regex("\\s+")).map { it.toDoubleOrNull() ?: return null }
            if (out.isNotEmpty() && out[0].size != vals.size) return null
            out.add(vals.toDoubleArray())
        }
        return out.toTypedArray()
    }

    fun det(m: Array<DoubleArray>): Double {
        val n = m.size
        val a = Array(n) { m[it].copyOf() }
        var d = 1.0
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            if (abs(a[piv][c]) < 1e-12) return 0.0
            if (piv != c) { val t = a[piv]; a[piv] = a[c]; a[c] = t; d = -d }
            d *= a[c][c]
            for (r in c + 1 until n) {
                val f = a[r][c] / a[c][c]
                for (k in c until n) a[r][k] -= f * a[c][k]
            }
        }
        return d
    }

    fun rank(m: Array<DoubleArray>): Int {
        val rows = m.size
        val cols = m[0].size
        val a = Array(rows) { m[it].copyOf() }
        var rank = 0
        for (c in 0 until cols) {
            if (rank >= rows) break
            var piv = rank
            for (r in rank + 1 until rows) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            if (abs(a[piv][c]) < 1e-10) continue
            val t = a[piv]; a[piv] = a[rank]; a[rank] = t
            for (r in rank + 1 until rows) {
                val f = a[r][c] / a[rank][c]
                for (k in c until cols) a[r][k] -= f * a[rank][k]
            }
            rank++
        }
        return rank
    }

    fun inverse(m: Array<DoubleArray>): Array<DoubleArray>? {
        val n = m.size
        val a = Array(n) { r -> DoubleArray(2 * n) { c -> if (c < n) m[r][c] else if (c - n == r) 1.0 else 0.0 } }
        for (c in 0 until n) {
            var piv = c
            for (r in c + 1 until n) if (abs(a[r][c]) > abs(a[piv][c])) piv = r
            if (abs(a[piv][c]) < 1e-12) return null
            val t = a[piv]; a[piv] = a[c]; a[c] = t
            val pv = a[c][c]
            for (k in 0 until 2 * n) a[c][k] /= pv
            for (r in 0 until n) {
                if (r == c) continue
                val f = a[r][c]
                if (f != 0.0) for (k in 0 until 2 * n) a[r][k] -= f * a[c][k]
            }
        }
        return Array(n) { r -> DoubleArray(n) { c -> a[r][n + c] } }
    }

    fun transpose(m: Array<DoubleArray>): Array<DoubleArray> =
        Array(m[0].size) { c -> DoubleArray(m.size) { r -> m[r][c] } }

    fun mul(a: Array<DoubleArray>, b: Array<DoubleArray>): Array<DoubleArray> =
        Array(a.size) { r -> DoubleArray(b[0].size) { c -> var s = 0.0; for (k in b.indices) s += a[r][k] * b[k][c]; s } }

    fun add(a: Array<DoubleArray>, b: Array<DoubleArray>, sign: Double): Array<DoubleArray> =
        Array(a.size) { r -> DoubleArray(a[0].size) { c -> a[r][c] + sign * b[r][c] } }

    fun show(m: Array<DoubleArray>): String {
        val cells = m.map { row -> row.map { v -> Formatter.format(if (abs(v) < 1e-10) 0.0 else BigDecimal(v).round(MathContext(8)).toDouble()) } }
        val width = cells.maxOf { row -> row.maxOf { it.length } }
        return cells.joinToString("\n") { row -> "  " + row.joinToString("  ") { it.padStart(width) } }
    }

    fun matrixReport(textA: String, textB: String): String {
        val a = parseMatrix(textA) ?: return "Macierz A jest pusta lub niepoprawna.\nWiersze w nowych liniach, liczby oddzielone spacją."
        val sb = StringBuilder()
        fun describe(name: String, m: Array<DoubleArray>) {
            sb.append(name).append(" (").append(m.size).append("×").append(m[0].size).append("):\n").append(show(m)).append("\n")
            if (m.size == m[0].size) sb.append("det(").append(name).append(") = ").append(Formatter.format(det(m))).append("\n")
            sb.append("rząd(").append(name).append(") = ").append(rank(m)).append("\n\n")
            sb.append(name).append("ᵀ:\n").append(show(transpose(m))).append("\n\n")
            if (m.size == m[0].size) {
                val inv = inverse(m)
                sb.append(name).append("⁻¹:\n").append(if (inv == null) "  nie istnieje (det = 0)" else show(inv)).append("\n\n")
            }
        }
        describe("A", a)
        if (textB.trim().isNotEmpty()) {
            val b = parseMatrix(textB) ?: return sb.toString() + "Macierz B jest niepoprawna."
            describe("B", b)
            if (a.size == b.size && a[0].size == b[0].size) {
                sb.append("A + B:\n").append(show(add(a, b, 1.0))).append("\n\n")
                sb.append("A − B:\n").append(show(add(a, b, -1.0))).append("\n\n")
            }
            if (a[0].size == b.size) sb.append("A · B:\n").append(show(mul(a, b))).append("\n\n")
            else sb.append("A · B: wymiary się nie zgadzają.\n\n")
            if (b[0].size == a.size) sb.append("B · A:\n").append(show(mul(b, a))).append("\n\n")
        }
        return sb.toString().trimEnd()
    }
}

/** Wykres funkcji: przeciąganie przesuwa, uszczypnięcie powiększa. */
class GraphView(context: Context, private val fns: List<(Double) -> Double>) : View(context) {
    private val d = resources.displayMetrics.density
    private var cx = 0.0
    private var cy = 0.0
    private var scale = 0.0
    private var lastX = 0f
    private var lastY = 0f
    private val colors = intArrayOf(0xFF1565C0.toInt(), 0xFFC62828.toInt(), 0xFF2E7D32.toInt(), 0xFF6A1B9A.toInt())
    private val grid = Paint().apply { color = 0xFFE3E6EA.toInt(); strokeWidth = d }
    private val axis = Paint().apply { color = 0xFF30343A.toInt(); strokeWidth = 1.5f * d }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF50555C.toInt(); textSize = 10 * d }
    private val curve = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2 * d }
    private val path = Path()
    private val detector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(det: ScaleGestureDetector): Boolean {
            scale = (scale * det.scaleFactor).coerceIn(1e-3, 1e7)
            invalidate()
            return true
        }
    })

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, w)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (scale == 0.0) scale = w / 16.0
        canvas.drawColor(Color.WHITE)
        val xMin = cx - w / 2 / scale
        val xMax = cx + w / 2 / scale
        val yMin = cy - h / 2 / scale
        val yMax = cy + h / 2 / scale
        val raw = 70 * d / scale
        val pow = Math.pow(10.0, floor(log10(raw)))
        val m = raw / pow
        val step = pow * (if (m < 1.5) 1.0 else if (m < 3.5) 2.0 else if (m < 7.5) 5.0 else 10.0)
        val axisX = (w / 2 + (0 - cx) * scale).toFloat()
        val axisY = (h / 2 - (0 - cy) * scale).toFloat()
        val labelY = (axisY + 12 * d).coerceIn(12 * d, h - 4 * d)
        val labelX = (axisX + 4 * d).coerceIn(2 * d, w - 40 * d)
        var k = ceil(xMin / step).toLong()
        while (k * step <= xMax) {
            val px = (w / 2 + (k * step - cx) * scale).toFloat()
            canvas.drawLine(px, 0f, px, h, grid)
            if (k != 0L) canvas.drawText(Formatter.format(BigDecimal(k * step).round(MathContext(6)).toDouble()), px + 2 * d, labelY, label)
            k++
        }
        k = ceil(yMin / step).toLong()
        while (k * step <= yMax) {
            val py = (h / 2 - (k * step - cy) * scale).toFloat()
            canvas.drawLine(0f, py, w, py, grid)
            if (k != 0L) canvas.drawText(Formatter.format(BigDecimal(k * step).round(MathContext(6)).toDouble()), labelX, py - 2 * d, label)
            k++
        }
        canvas.drawLine(axisX, 0f, axisX, h, axis)
        canvas.drawLine(0f, axisY, w, axisY, axis)

        for ((i, f) in fns.withIndex()) {
            curve.color = colors[i % colors.size]
            path.reset()
            var pen = false
            var prevY = 0f
            var px = 0f
            while (px <= w) {
                val x = cx + (px - w / 2) / scale
                val y = try { f(x) } catch (e: Exception) { Double.NaN }
                val py = (h / 2 - (y - cy) * scale).toFloat()
                if (y.isNaN() || y.isInfinite() || py.isNaN() || abs(py) > 1e6f) {
                    pen = false
                } else {
                    if (pen && abs(py - prevY) < h * 1.5f) path.lineTo(px, py) else path.moveTo(px, py)
                    pen = true
                    prevY = py
                }
                px += 1.5f * d / 2
            }
            canvas.drawPath(path, curve)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        detector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!detector.isInProgress && event.pointerCount == 1 && scale > 0) {
                    cx -= (event.x - lastX) / scale
                    cy += (event.y - lastY) / scale
                    invalidate()
                }
                lastX = event.x
                lastY = event.y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val keep = if (event.actionIndex == 0) 1 else 0
                if (keep < event.pointerCount) {
                    lastX = event.getX(keep)
                    lastY = event.getY(keep)
                }
            }
        }
        return true
    }
}
