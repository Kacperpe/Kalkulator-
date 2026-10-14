package pl.kacper.kalkulator

import kotlin.math.max

/**
 * Układ wyrażenia w zapisie „matematycznym" (jak na Casio): ułamki piętrowe, potęgi w indeksie górnym,
 * pierwiastki z kreską. Wejściem jest ta sama lista żetonów, którą liczy kalkulator, więc wygląd
 * zawsze odpowiada temu, co zostanie policzone.
 *
 * Moduł nie zależy od Androida: tekst mierzy obiekt [Meter], a wynikiem jest lista poleceń rysowania
 * i pozycje kursora dla każdego miejsca w liście żetonów.
 */
object MathLayout {
    class Tk(val d: String, val c: String)

    // ---- drzewo ----
    sealed class Node
    class Glyph(val text: String, val index: Int, val op: Boolean = false, val italic: Boolean = false) : Node()
    class Row(val nodes: List<Node>, val start: Int, val end: Int, val box: Boolean) : Node()
    class Frac(val num: Row, val den: Row) : Node()
    class Sup(val exp: Row, val hat: Int) : Node()
    class Radical(val open: Int, val inner: Row, val close: Int) : Node()

    private val binaryOps = setOf("+", "−", "-", "×", "÷", "*", "=", "mod", "P", "C", "~")
    private val notOperand = binaryOps + setOf(",", ")", "/", "^", "!", "%", "^2", "^3", "^(-1)", ";")

    /** Buduje drzewo z listy żetonów; nawiasy wokół licznika, mianownika i wykładnika są ukrywane, gdy nie stoi w nich kursor. */
    fun parse(toks: List<Tk>, cursor: Int = -1): Row = Parser(toks, cursor).seq(0, toks.size, false)

    private class Parser(val t: List<Tk>, val cursor: Int) {
        fun c(i: Int): String = t[i].c

        fun isNum(i: Int, to: Int): Boolean {
            if (i >= to) return false
            val s = c(i)
            return (s.length == 1 && (s[0].isDigit() || s[0] == '.')) || s == "E"
        }

        fun matching(i: Int, to: Int): Int {
            var depth = 0
            for (k in i until to) {
                val s = c(k)
                if (s.endsWith("(")) depth++
                else if (s == ")") {
                    depth--
                    if (depth == 0) return k
                }
            }
            return -1
        }

        fun seq(from: Int, to: Int, box: Boolean): Row {
            val out = ArrayList<Node>()
            var lastOut = -1 // pozycja w out, od której zaczyna się ostatni operand
            var lastTok = -1 // indeks żetonu, od którego zaczyna się ostatni operand
            var i = from
            while (i < to) {
                val s = c(i)
                if (s == "/") {
                    val numNodes: List<Node>
                    val numStart: Int
                    if (lastOut >= 0) {
                        val taken = ArrayList(out.subList(lastOut, out.size))
                        while (out.size > lastOut) out.removeAt(out.size - 1)
                        numNodes = unwrap(taken)
                        numStart = lastTok
                    } else {
                        numNodes = emptyList()
                        numStart = i
                    }
                    val den = operand(i + 1, to, true)
                    val denRow = if (den != null) Row(unwrap(den.first), i + 1, den.second, true) else Row(emptyList(), i + 1, i + 1, true)
                    lastOut = out.size
                    lastTok = numStart
                    out.add(Frac(Row(numNodes, numStart, i, true), denRow))
                    i = den?.second ?: (i + 1)
                    continue
                }
                val r = operand(i, to, false)
                if (r != null) {
                    lastOut = out.size
                    lastTok = i
                    out.addAll(r.first)
                    i = r.second
                    continue
                }
                val prev = out.lastOrNull()
                val unary = (s == "−" || s == "-" || s == "+") && (prev == null || (prev is Glyph && prev.op))
                out.add(Glyph(t[i].d, i, op = s in binaryOps && !unary))
                lastOut = -1
                i++
            }
            return Row(out, from, to, box)
        }

        /** Zdejmuje nawiasy z grupy „( … )" jeśli kursor nie stoi w środku (licznik, mianownik, wykładnik). */
        fun unwrap(nodes: List<Node>): List<Node> {
            if (nodes.size != 1) return nodes
            val g = nodes[0]
            if (g is Row && g.nodes.size >= 2) {
                val first = g.nodes.first()
                val last = g.nodes.last()
                if (first is Glyph && last is Glyph && first.index >= 0 && last.index >= 0 && t[first.index].c == "(" && t[last.index].c == ")") {
                    val inside = cursor > first.index && cursor <= last.index
                    if (!inside) return g.nodes.subList(1, g.nodes.size - 1)
                }
            }
            return nodes
        }

        /** Operand: liczba / zmienna / grupa w nawiasie / funkcja, z przyrostkami (potęgi, !, %). */
        fun operand(i0: Int, to: Int, allowSign: Boolean): Pair<List<Node>, Int>? {
            var i = i0
            val nodes = ArrayList<Node>()
            if (allowSign) {
                while (i < to && (c(i) == "−" || c(i) == "-" || c(i) == "+")) {
                    nodes.add(Glyph(t[i].d, i))
                    i++
                }
            }
            if (i >= to) return if (nodes.isEmpty()) null else Pair(nodes, i)
            val s = c(i)
            when {
                isNum(i, to) -> {
                    while (isNum(i, to)) {
                        nodes.add(Glyph(t[i].d, i))
                        i++
                    }
                }
                s.endsWith("(") -> {
                    val close = matching(i, to)
                    val end = if (close < 0) to else close
                    val inner = seq(i + 1, end, false)
                    if (s == "sqrt(") {
                        nodes.add(Radical(i, Row(inner.nodes, i + 1, end, true), close))
                    } else {
                        val g = ArrayList<Node>()
                        g.add(Glyph(t[i].d, i))
                        g.addAll(inner.nodes)
                        if (close >= 0) g.add(Glyph(t[close].d, close))
                        nodes.add(Row(g, i, if (close < 0) to else close + 1, false))
                    }
                    i = if (close < 0) to else close + 1
                }
                s !in notOperand -> {
                    nodes.add(Glyph(t[i].d, i, italic = s.startsWith("\$")))
                    i++
                }
                else -> return if (nodes.isEmpty()) null else Pair(nodes, i)
            }
            // przyrostki
            while (i < to) {
                val p = c(i)
                when (p) {
                    "!", "%" -> { nodes.add(Glyph(t[i].d, i)); i++ }
                    "^2" -> { nodes.add(Sup(Row(listOf(Glyph("2", -1)), -1, -1, false), i)); i++ }
                    "^3" -> { nodes.add(Sup(Row(listOf(Glyph("3", -1)), -1, -1, false), i)); i++ }
                    "^(-1)" -> { nodes.add(Sup(Row(listOf(Glyph("−1", -1)), -1, -1, false), i)); i++ }
                    "^" -> {
                        val e = operand(i + 1, to, true)
                        if (e != null) {
                            nodes.add(Sup(Row(unwrap(e.first), i + 1, e.second, true), i))
                            i = e.second
                        } else {
                            nodes.add(Sup(Row(emptyList(), i + 1, i + 1, true), i))
                            i++
                        }
                    }
                    else -> return Pair(nodes, i)
                }
            }
            return Pair(nodes, i)
        }
    }

    // ---- układ i rysowanie ----
    interface Meter {
        fun width(text: String, size: Float, italic: Boolean): Float
    }

    sealed class Op
    class TextOp(val text: String, val x: Float, val y: Float, val size: Float, val italic: Boolean) : Op()
    class LineOp(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val w: Float) : Op()
    class BoxOp(val l: Float, val t: Float, val r: Float, val b: Float, val w: Float) : Op()
    class PathOp(val pts: FloatArray, val w: Float) : Op()

    class Caret(val x: Float, val top: Float, val bottom: Float)

    class Result(val w: Float, val asc: Float, val desc: Float, val ops: List<Op>, val carets: Map<Int, Caret>)

    private class Box(val w: Float, val asc: Float, val desc: Float)

    private const val ASC = 0.74f
    private const val DESC = 0.24f

    fun layout(root: Row, size: Float, m: Meter): Result {
        val e = Engine(m)
        val b = e.measure(root, size, 0, null)
        e.place(root, 0f, b.asc, size, 0, null)
        return Result(b.w, b.asc, b.desc, e.ops, e.carets)
    }

    private class Engine(val m: Meter) {
        val ops = ArrayList<Op>()
        val carets = HashMap<Int, Caret>()

        fun childSize(size: Float, level: Int) = if (level == 0) size else max(size * 0.82f, 9f)
        fun supSize(size: Float) = max(size * 0.64f, 8f)
        fun opPad(size: Float) = size * 0.16f
        fun bar(size: Float) = max(1.5f, size * 0.06f)
        fun axis(size: Float) = size * 0.28f

        fun measure(n: Node, size: Float, level: Int, prev: Box?): Box = when (n) {
            is Glyph -> {
                val w = m.width(n.text, size, n.italic) + if (n.op) 2 * opPad(size) else 0f
                Box(w, size * ASC, size * DESC)
            }
            is Row -> {
                if (n.nodes.isEmpty()) {
                    if (n.box) Box(size * 0.62f, size * ASC, size * DESC) else Box(size * 0.08f, size * ASC, size * DESC)
                } else {
                    var w = 0f
                    var a = 0f
                    var d = 0f
                    var p: Box? = null
                    for (c in n.nodes) {
                        val b = measure(c, size, level, p)
                        w += b.w; a = max(a, b.asc); d = max(d, b.desc)
                        p = b
                    }
                    Box(w, a, d)
                }
            }
            is Frac -> {
                val cs = childSize(size, level)
                val nb = measure(n.num, cs, level + 1, null)
                val db = measure(n.den, cs, level + 1, null)
                val gap = size * 0.12f
                val t = bar(size)
                val w = max(nb.w, db.w) + size * 0.3f
                Box(w, axis(size) + t / 2 + gap + nb.desc + nb.asc, db.asc + db.desc + gap + t / 2 - axis(size))
            }
            is Sup -> {
                val ss = supSize(size)
                val eb = measure(n.exp, ss, level + 1, null)
                val raise = supRaise(size, prev, eb)
                Box(eb.w + size * 0.06f, raise + eb.asc, max(0f, eb.desc - raise))
            }
            is Radical -> {
                val ib = measure(n.inner, size, level, null)
                Box(ib.w + size * 0.62f, ib.asc + size * 0.12f + bar(size), ib.desc + size * 0.06f)
            }
        }

        fun supRaise(size: Float, prev: Box?, eb: Box): Float {
            val base = prev?.asc ?: (size * ASC)
            return max(size * 0.42f, base - eb.asc * 0.55f)
        }

        fun caret(i: Int, x: Float, base: Float, size: Float) {
            if (i >= 0 && !carets.containsKey(i)) carets[i] = Caret(x, base - size * 0.82f, base + size * 0.26f)
        }

        /** Rysuje węzeł z lewym brzegiem x i linią bazową base; zwraca jego rozmiar. */
        fun place(n: Node, x: Float, base: Float, size: Float, level: Int, prev: Box?): Box {
            return when (n) {
                is Glyph -> {
                    val b = measure(n, size, level, prev)
                    val pad = if (n.op) opPad(size) else 0f
                    ops.add(TextOp(n.text, x + pad, base, size, n.italic))
                    caret(n.index, x, base, size)
                    return b
                }
                is Row -> {
                    val b = measure(n, size, level, prev)
                    if (n.nodes.isEmpty()) {
                        if (n.box) {
                            val s = size
                            ops.add(BoxOp(x + s * 0.08f, base - s * 0.66f, x + s * 0.54f, base + s * 0.04f, max(1f, s * 0.045f)))
                            caret(n.start, x + s * 0.31f, base, size)
                        } else {
                            caret(n.start, x, base, size)
                        }
                        return b
                    }
                    var cx = x
                    var p: Box? = null
                    for (c in n.nodes) {
                        val cb = place(c, cx, base, size, level, p)
                        cx += cb.w
                        p = cb
                    }
                    caret(n.end, cx, base, size)
                    caret(n.start, x, base, size)
                    return b
                }
                is Frac -> {
                    val b = measure(n, size, level, prev)
                    val cs = childSize(size, level)
                    val nb = measure(n.num, cs, level + 1, null)
                    val db = measure(n.den, cs, level + 1, null)
                    val gap = size * 0.12f
                    val t = bar(size)
                    val barY = base - axis(size)
                    place(n.num, x + (b.w - nb.w) / 2, barY - t / 2 - gap - nb.desc, cs, level + 1, null)
                    place(n.den, x + (b.w - db.w) / 2, barY + t / 2 + gap + db.asc, cs, level + 1, null)
                    ops.add(LineOp(x + size * 0.08f, barY, x + b.w - size * 0.08f, barY, t))
                    return b
                }
                is Sup -> {
                    val b = measure(n, size, level, prev)
                    val ss = supSize(size)
                    val eb = measure(n.exp, ss, level + 1, null)
                    caret(n.hat, x, base, size)
                    place(n.exp, x + size * 0.03f, base - supRaise(size, prev, eb), ss, level + 1, null)
                    return b
                }
                is Radical -> {
                    val b = measure(n, size, level, prev)
                    val ib = measure(n.inner, size, level, null)
                    val t = bar(size)
                    val top = base - b.asc + t / 2
                    val bottom = base + ib.desc
                    val h = bottom - top
                    val sx = x + size * 0.04f
                    val pts = floatArrayOf(
                        sx, top + h * 0.6f,
                        sx + size * 0.12f, top + h * 0.52f,
                        sx + size * 0.28f, bottom,
                        sx + size * 0.48f, top,
                        x + b.w - size * 0.04f, top
                    )
                    ops.add(PathOp(pts, t * 1.1f))
                    caret(n.open, x, base, size)
                    place(n.inner, x + size * 0.56f, base, size, level, null)
                    if (n.close >= 0) caret(n.close + 1, x + b.w, base, size)
                    return b
                }
            }
        }
    }

    /** Zamienia napis wyniku (np. „-3/4", „1.5×10^-7") na żetony do ładnego wyświetlenia. */
    fun resultTokens(s: String): List<Tk> {
        val out = ArrayList<Tk>()
        for (ch in s) {
            when (ch) {
                '-' -> out.add(Tk("−", "−"))
                '/' -> out.add(Tk("/", "/"))
                '^' -> out.add(Tk("^", "^"))
                else -> out.add(Tk(ch.toString(), ch.toString()))
            }
        }
        return out
    }

    /** Pomocnicze dla testów: zapis drzewa w prostej postaci tekstowej. */
    fun dump(n: Node): String = when (n) {
        is Glyph -> n.text
        is Row -> if (n.nodes.isEmpty() && n.box) "□" else n.nodes.joinToString("") { dump(it) }
        is Frac -> "{" + dump(n.num) + "|" + dump(n.den) + "}"
        is Sup -> "^[" + dump(n.exp) + "]"
        is Radical -> "√[" + dump(n.inner) + "]"
    }

}
