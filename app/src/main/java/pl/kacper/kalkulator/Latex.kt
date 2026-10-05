package pl.kacper.kalkulator

/** Zamiana wzoru w LaTeX (wynik rozpoznawania zdjęcia) na wyrażenie kalkulatora. */
object Latex {
    class Item(val d: String, val c: String)

    private const val VAR = '\u0001'

    private val funcs = mapOf(
        "sin" to "sin", "cos" to "cos", "tan" to "tan", "tg" to "tan", "cot" to "cot", "ctg" to "cot",
        "ln" to "ln", "log" to "log", "lg" to "log", "exp" to "exp",
        "arcsin" to "asin", "arccos" to "acos", "arctan" to "atan", "arctg" to "atan", "arcctg" to "acot", "arccot" to "acot",
        "sinh" to "sinh", "cosh" to "cosh", "tanh" to "tanh"
    )
    private val display = mapOf(
        "asin" to "sin⁻¹", "acos" to "cos⁻¹", "atan" to "tan⁻¹", "acot" to "cot⁻¹"
    )
    private val wrappers = setOf("\\operatorname", "\\mathrm", "\\text", "\\mathit", "\\mathbf", "\\textrm", "\\mathop", "\\rm")
    private val sizers = setOf(
        "\\left", "\\right", "\\big", "\\Big", "\\bigg", "\\Bigg", "\\bigl", "\\bigr", "\\Bigl", "\\Bigr",
        "\\biggl", "\\biggr", "\\Biggl", "\\Biggr"
    )
    private val skipped = setOf(
        "\\,", "\\;", "\\:", "\\!", "\\ ", "\\quad", "\\qquad", "~", "\\displaystyle", "\\textstyle",
        "\\limits", "\\nolimits", "\\enspace", "\\thinspace"
    )
    private val opens = setOf("(", "[", "\\{", "\\lbrack", "\\lbrace")
    private val closes = setOf(")", "]", "\\}", "\\rbrack", "\\rbrace")
    private val supportedVars = "abcdfxytm"

    /** Zwarty zapis do pokazania użytkownikowi. */
    fun compact(latex: String): String {
        val s = latex.trim()
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == ' ') {
                var j = i
                while (j < s.length && s[j] == ' ') j++
                if (endsWithCommand(sb) && j < s.length && s[j].isLetter()) sb.append(' ')
                i = j
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun endsWithCommand(sb: CharSequence): Boolean {
        var k = sb.length - 1
        if (k < 0 || !sb[k].isLetter()) return false
        while (k >= 0 && sb[k].isLetter()) k--
        return k >= 0 && sb[k] == '\\'
    }

    private fun lex(src: String): List<String> {
        val out = ArrayList<String>()
        var i = 0
        while (i < src.length) {
            val ch = src[i]
            if (ch.isWhitespace()) { i++; continue }
            if (ch == '\\' && i + 1 < src.length) {
                var j = i + 1
                if (src[j].isLetter()) {
                    while (j < src.length && src[j].isLetter()) j++
                } else {
                    j++
                }
                out.add(src.substring(i, j))
                i = j
            } else {
                out.add(ch.toString())
                i++
            }
        }
        while (out.isNotEmpty() && (out.last() == "." || out.last() == "," || out.last() in skipped)) out.removeAt(out.size - 1)
        return out
    }

    fun convert(latex: String): List<Item> {
        val p = Parser(lex(latex))
        val out = ArrayList<Item>()
        while (p.i < p.lx.size) p.item(out)
        if (out.isEmpty()) throw CalcException("Nie rozpoznano wzoru")
        return p.resolveVars(out)
    }

    private class Parser(val lx: List<String>) {
        var i = 0
        var absOpen = 0
        val letters = LinkedHashSet<Char>()

        fun fail(msg: String): Nothing = throw CalcException(msg)
        fun peek(k: Int = 0): String = if (i + k < lx.size) lx[i + k] else ""

        fun add(out: MutableList<Item>, d: String, c: String = d) { out.add(Item(d, c)) }

        fun endsOperand(out: List<Item>): Boolean {
            if (out.isEmpty()) return false
            val c = out.last().c
            return c == ")" || c == "π" || c == "e" || c == "!" || c == "%" || c[0] == VAR || (c.length == 1 && (c[0].isDigit() || c[0] == '.'))
        }

        fun group(stop: String): ArrayList<Item> {
            val sub = ArrayList<Item>()
            while (i < lx.size && lx[i] != stop) item(sub)
            if (i < lx.size) i++
            return sub
        }

        fun arg(): ArrayList<Item> {
            if (peek() == "{") {
                i++
                return group("}")
            }
            val sub = ArrayList<Item>()
            if (i < lx.size) item(sub)
            return sub
        }

        fun addWrapped(out: MutableList<Item>, sub: List<Item>) {
            if (sub.size == 1 && !sub[0].c.endsWith("(")) {
                out.addAll(sub)
            } else {
                add(out, "(")
                out.addAll(sub)
                add(out, ")")
            }
        }

        fun isDegree(): Boolean {
            if (peek() == "\\circ") { i++; return true }
            if (peek() == "{" && peek(1) == "\\circ" && peek(2) == "}") { i += 3; return true }
            return false
        }

        fun item(out: MutableList<Item>) {
            val t = lx[i++]
            when {
                t.length == 1 && t[0].isDigit() -> add(out, t)
                t == "." -> add(out, ".")
                t == "," -> {
                    val prevDigit = out.isNotEmpty() && out.last().c.length == 1 && out.last().c[0].isDigit()
                    val nextDigit = peek().length == 1 && peek()[0].isDigit()
                    if (prevDigit && nextDigit) add(out, ".") else fail("Nieobsługiwany przecinek we wzorze")
                }
                t == "+" -> add(out, "+")
                t == "-" || t == "−" -> add(out, "−")
                t == "*" || t == "\\cdot" || t == "\\times" || t == "\\ast" || t == "×" -> add(out, "×")
                t == "/" || t == "\\div" || t == ":" || t == "÷" -> add(out, "÷")
                t == "=" -> add(out, "=")
                t in opens -> add(out, "(")
                t in closes -> add(out, ")")
                t in sizers -> {
                    val dl = peek()
                    if (dl == ".") {
                        i++
                    } else if (dl == "|" || dl == "\\vert" || dl == "\\lvert" || dl == "\\rvert") {
                        i++
                        if (t == "\\left" || t.endsWith("l")) openAbs(out)
                        else if (t == "\\right" || t.endsWith("r")) closeAbs(out)
                        else toggleAbs(out)
                    }
                }
                t == "|" || t == "\\vert" -> toggleAbs(out)
                t == "\\lvert" -> openAbs(out)
                t == "\\rvert" -> closeAbs(out)
                t == "{" -> out.addAll(group("}"))
                t == "}" -> {}
                t == "^" -> {
                    if (!isDegree()) {
                        val sub = arg()
                        if (sub.isEmpty()) fail("Pusty wykładnik")
                        add(out, "^")
                        addWrapped(out, sub)
                    }
                }
                t == "_" -> arg()
                t == "!" -> add(out, "!")
                t == "%" || t == "\\%" -> add(out, "%")
                t == "\\frac" || t == "\\dfrac" || t == "\\tfrac" || t == "\\cfrac" -> {
                    val a = arg()
                    val b = arg()
                    if (a.isEmpty() || b.isEmpty()) fail("Niepełny ułamek")
                    add(out, "("); add(out, "("); out.addAll(a); add(out, ")")
                    add(out, "÷")
                    add(out, "("); out.addAll(b); add(out, ")"); add(out, ")")
                }
                t == "\\sqrt" -> {
                    var n: List<Item>? = null
                    if (peek() == "[") {
                        i++
                        n = group("]")
                    }
                    val a = arg()
                    if (a.isEmpty()) fail("Pusty pierwiastek")
                    if (n != null && n.isNotEmpty()) {
                        add(out, "("); out.addAll(n); add(out, ")")
                        add(out, "ˣ√", "~")
                        add(out, "("); out.addAll(a); add(out, ")")
                    } else {
                        add(out, "√(", "sqrt(")
                        out.addAll(a)
                        add(out, ")")
                    }
                }
                t == "\\pi" || t == "π" -> add(out, "π")
                t in wrappers -> {
                    val save = i
                    var name: String? = null
                    if (peek() == "{") {
                        var j = i + 1
                        val sb = StringBuilder()
                        while (j < lx.size && lx[j] != "}" && lx[j].length == 1 && lx[j][0].isLetter()) { sb.append(lx[j]); j++ }
                        if (j < lx.size && lx[j] == "}" && funcs.containsKey(sb.toString())) {
                            name = sb.toString()
                            i = j + 1
                        }
                    }
                    if (name != null) func(out, name) else i = save
                }
                t.startsWith("\\") && funcs.containsKey(t.substring(1)) -> func(out, t.substring(1))
                t in skipped -> {}
                t.length == 1 && t[0].isLetter() -> {
                    if (t == "e") {
                        add(out, "e")
                    } else {
                        letters.add(t[0])
                        add(out, t, VAR.toString() + t)
                    }
                }
                t == "\\int" || t == "\\iint" || t == "\\oint" -> fail("Całki ze zdjęcia nie są obsługiwane")
                t == "\\sum" || t == "\\prod" -> fail("Sumy i iloczyny ze zdjęcia nie są obsługiwane")
                t == "\\lim" -> fail("Granice nie są obsługiwane")
                t == "\\begin" || t == "\\\\" || t == "&" -> fail("Macierze i układy równań ze zdjęcia nie są obsługiwane")
                t == "\\pm" || t == "\\mp" -> fail("Znak ± nie jest obsługiwany")
                t == "<" || t == ">" || t == "\\le" || t == "\\ge" || t == "\\leq" || t == "\\geq" || t == "\\neq" || t == "\\ne" ->
                    fail("Nierówności nie są obsługiwane")
                else -> fail("Nieobsługiwany symbol: $t")
            }
        }

        fun openAbs(out: MutableList<Item>) {
            absOpen++
            add(out, "|", "abs(")
        }

        fun closeAbs(out: MutableList<Item>) {
            if (absOpen > 0) absOpen--
            add(out, "|", ")")
        }

        fun toggleAbs(out: MutableList<Item>) {
            if (absOpen > 0 && endsOperand(out)) closeAbs(out) else openAbs(out)
        }

        fun isTermEnd(t: String): Boolean =
            t == "" || t == "+" || t == "-" || t == "−" || t == "=" || t == "," || t == "}" || t == "|" ||
                t in closes || t == "\\right" || (t.startsWith("\\") && funcs.containsKey(t.substring(1))) || t in wrappers

        fun func(out: MutableList<Item>, name: String) {
            var code = funcs[name] ?: fail("Nieobsługiwana funkcja: $name")
            var exp: List<Item>? = null
            var base: List<Item>? = null
            for (k in 0 until 2) {
                if (peek() == "^") { i++; exp = arg() } else if (peek() == "_") { i++; base = arg() }
            }
            if (exp != null && exp.joinToString("") { it.c } == "−1" && (code == "sin" || code == "cos" || code == "tan" || code == "cot")) {
                code = "a$code"
                exp = null
            }
            val a = ArrayList<Item>()
            var j = i
            if (peek() in sizers) j++
            if (j < lx.size && lx[j] in opens) {
                i = j + 1
                var depth = 1
                while (i < lx.size) {
                    val t = lx[i]
                    if (t in opens) depth++
                    if (t in closes) {
                        depth--
                        if (depth == 0) { i++; break }
                    }
                    item(a)
                }
            } else {
                var first = true
                while (i < lx.size && (first || !isTermEnd(lx[i]))) {
                    val before = a.size
                    item(a)
                    if (a.size > before) first = false
                }
            }
            if (a.isEmpty()) fail("Brak argumentu funkcji $name")
            if (code == "exp") {
                add(out, "e"); add(out, "^"); add(out, "("); out.addAll(a); add(out, ")")
            } else if (code == "log" && base != null && base.isNotEmpty()) {
                add(out, "logxy(", "logb(")
                out.addAll(base)
                add(out, ",")
                out.addAll(a)
                add(out, ")")
            } else {
                add(out, (display[code] ?: code) + "(", "$code(")
                out.addAll(a)
                add(out, ")")
            }
            if (exp != null && exp.isNotEmpty()) {
                add(out, "^")
                addWrapped(out, exp)
            }
        }

        fun resolveVars(items: List<Item>): List<Item> {
            val single = letters.size == 1
            return items.map { it ->
                if (it.c.isNotEmpty() && it.c[0] == VAR) {
                    val ch = it.c[1]
                    if (single) {
                        Item("x", "\$x")
                    } else {
                        val lc = ch.lowercaseChar()
                        if (supportedVars.indexOf(lc) < 0) fail("Nieobsługiwana zmienna: $ch (dostępne: a b c d f x y t m)")
                        Item(lc.toString(), "\$$lc")
                    }
                } else it
            }
        }
    }
}
