package pl.kacper.kalkulator

import java.math.BigDecimal
import java.math.MathContext
import java.util.Locale
import kotlin.math.E
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

enum class AngleMode { DEG, RAD, GRAD }

class CalcException(message: String) : Exception(message)

/**
 * Parser rekurencyjny wyrażeń: + - × ÷ ^ ! % mod, nPr (P), nCr (C), pierwiastek n-tego stopnia (~),
 * funkcje trygonometryczne i hiperboliczne, log/ln, stałe π i e, Ans, notacja E (5E3),
 * mnożenie domyślne (2π, 3sin(30)), zmienne ($a, $x ...) oraz
 * int(f,a,b), diff(f,a), sum(f,a,b), prod(f,a,b) liczone po zmiennej $x.
 */
class Evaluator(
    private val mode: AngleMode = AngleMode.DEG,
    private val ans: Double = 0.0,
    private val vars: Map<Char, Double> = emptyMap()
) {
    private var s = ""
    private var p = 0

    private val names = listOf(
        "asinh", "acosh", "atanh", "ranint", "floor", "sinh", "cosh", "tanh",
        "asin", "acos", "atan", "acot", "sqrt", "cbrt", "ceil", "logb", "diff", "prod", "rand",
        "sin", "cos", "tan", "cot", "log", "abs", "gcd", "lcm", "int", "sum", "ln", "pi", "e"
    ).sortedByDescending { it.length }

    private val lazyNames = setOf("int", "diff", "sum", "prod")
    private val twoArgNames = setOf("logb", "gcd", "lcm", "ranint")

    fun evaluate(expr: String): Double {
        s = expr.replace(" ", "")
            .replace('×', '*').replace('÷', '/').replace('−', '-')
            .replace("π", "pi")
        p = 0
        if (s.isEmpty()) throw CalcException("Błąd składni")
        val v = parseExpr()
        if (p < s.length) throw CalcException("Błąd składni")
        if (v.isNaN() || v.isInfinite()) throw CalcException("Błąd matematyczny")
        return v
    }

    private fun peek(): Char = if (p < s.length) s[p] else '\u0000'

    private fun mathError(): Nothing = throw CalcException("Błąd matematyczny")
    private fun syntaxError(): Nothing = throw CalcException("Błąd składni")

    private fun parseExpr(): Double {
        var v = parseTerm()
        while (true) {
            when (peek()) {
                '+' -> { p++; v += parseTerm() }
                '-' -> { p++; v -= parseTerm() }
                else -> return v
            }
        }
    }

    private fun startsPrimary(): Boolean {
        val c = peek()
        return c.isDigit() || c == '.' || c == '(' || c == '√' || c == '$' || c in 'a'..'z' || c == 'A'
    }

    private fun parseTerm(): Double {
        var v = parseUnary()
        while (true) {
            val c = peek()
            when {
                c == '*' -> { p++; v *= parseUnary() }
                c == '/' -> {
                    p++
                    val d = parseUnary()
                    if (d == 0.0) mathError()
                    v /= d
                }
                s.startsWith("mod", p) -> {
                    p += 3
                    val d = parseUnary()
                    if (d == 0.0) mathError()
                    v -= d * floor(v / d)
                }
                startsPrimary() -> v *= parseUnary()
                else -> return v
            }
        }
    }

    private fun parseUnary(): Double = when (peek()) {
        '-' -> { p++; -parseUnary() }
        '+' -> { p++; parseUnary() }
        else -> parsePerm()
    }

    private fun parsePerm(): Double {
        var v = parsePower()
        while (peek() == 'P' || peek() == 'C') {
            val op = s[p++]
            val r = parsePower()
            v = if (op == 'P') nPr(v, r) else nCr(v, r)
        }
        return v
    }

    private fun parsePower(): Double {
        val base = parsePostfix()
        if (peek() == '^') {
            p++
            val exp = parsePowerOperand()
            return base.pow(exp)
        }
        if (peek() == '~') {
            p++
            val radicand = parsePowerOperand()
            return root(radicand, base)
        }
        return base
    }

    private fun parsePowerOperand(): Double = when (peek()) {
        '-' -> { p++; -parsePowerOperand() }
        '+' -> { p++; parsePowerOperand() }
        else -> parsePower()
    }

    private fun root(x: Double, n: Double): Double {
        if (n == 0.0) mathError()
        if (x < 0) {
            if (isInt(n) && abs(n) % 2.0 == 1.0) return -((-x).pow(1.0 / n))
            mathError()
        }
        return x.pow(1.0 / n)
    }

    private fun parsePostfix(): Double {
        var v = parsePrimary()
        while (true) {
            when (peek()) {
                '!' -> { p++; v = factorial(v) }
                '%' -> { p++; v /= 100.0 }
                else -> return v
            }
        }
    }

    private fun parsePrimary(): Double {
        val c = peek()
        when {
            c.isDigit() || c == '.' -> return parseNumber()
            c == '(' -> {
                p++
                val v = parseExpr()
                closeParen()
                return v
            }
            c == '√' -> {
                p++
                val x = parsePostfix()
                if (x < 0) mathError()
                return sqrt(x)
            }
            c == '$' -> {
                p++
                if (p >= s.length) syntaxError()
                val name = s[p++]
                return vars[name] ?: 0.0
            }
            c == 'A' -> {
                if (s.startsWith("Ans", p)) { p += 3; return ans }
                syntaxError()
            }
            c in 'a'..'z' -> return parseNamed()
        }
        syntaxError()
    }

    private fun closeParen() {
        if (peek() == ')') p++ else if (p < s.length) syntaxError()
    }

    private fun comma() {
        if (peek() == ',') p++ else syntaxError()
    }

    private fun parseNumber(): Double {
        val start = p
        while (peek().isDigit()) p++
        if (peek() == '.') {
            p++
            while (peek().isDigit()) p++
        }
        if (p - start == 1 && s[start] == '.') syntaxError()
        if (peek() == 'E') {
            val save = p
            p++
            if (peek() == '-' || peek() == '+') p++
            val digitsStart = p
            while (peek().isDigit()) p++
            if (p == digitsStart) p = save
        }
        return s.substring(start, p).toDoubleOrNull() ?: syntaxError()
    }

    private fun parseNamed(): Double {
        val name = names.firstOrNull { s.startsWith(it, p) } ?: syntaxError()
        p += name.length
        when (name) {
            "pi" -> return PI
            "e" -> return E
            "rand" -> return Math.random()
        }
        if (peek() != '(') syntaxError()
        p++
        if (name in lazyNames) return parseLazy(name)
        val x = parseExpr()
        val result = if (name in twoArgNames) {
            comma()
            val y = parseExpr()
            applyFunc2(name, x, y)
        } else {
            applyFunc(name, x)
        }
        closeParen()
        return result
    }

    /** Funkcje, których pierwszy argument jest wyrażeniem zależnym od $x. */
    private fun parseLazy(name: String): Double {
        val start = p
        var depth = 0
        while (p < s.length) {
            val ch = s[p]
            if (ch == '(') depth++
            else if (ch == ')') { if (depth == 0) break; depth-- }
            else if (ch == ',' && depth == 0) break
            p++
        }
        if (peek() != ',') syntaxError()
        val body = s.substring(start, p)
        p++
        if (body.isEmpty()) syntaxError()
        val a = parseExpr()
        var b = 0.0
        if (name != "diff") {
            comma()
            b = parseExpr()
        }
        closeParen()
        val f = { x: Double -> Evaluator(mode, ans, vars + ('x' to x)).evaluate(body) }
        return when (name) {
            "int" -> simpson(f, a, b)
            "diff" -> derivative(f, a)
            "sum" -> series(f, a, b, false)
            else -> series(f, a, b, true)
        }
    }

    private fun simpson(f: (Double) -> Double, a: Double, b: Double): Double {
        if (a == b) return 0.0
        val n = 2000
        val h = (b - a) / n
        var sum = f(a) + f(b)
        for (i in 1 until n) sum += f(a + i * h) * (if (i % 2 == 1) 4.0 else 2.0)
        return clean(sum * h / 3.0)
    }

    private fun derivative(f: (Double) -> Double, a: Double): Double {
        val h = 1e-3 * max(1.0, abs(a))
        val d1 = (f(a + h) - f(a - h)) / (2 * h)
        val d2 = (f(a + h / 2) - f(a - h / 2)) / h
        return clean((4 * d2 - d1) / 3.0)
    }

    private fun series(f: (Double) -> Double, a: Double, b: Double, product: Boolean): Double {
        if (!isInt(a) || !isInt(b) || a > b || b - a > 100000) mathError()
        var acc = if (product) 1.0 else 0.0
        var i = a
        while (i <= b) {
            val v = f(i)
            if (product) acc *= v else acc += v
            i += 1.0
        }
        return acc
    }

    private fun toRad(x: Double): Double = when (mode) {
        AngleMode.DEG -> Math.toRadians(x)
        AngleMode.RAD -> x
        AngleMode.GRAD -> x * PI / 200.0
    }

    private fun fromRad(x: Double): Double = when (mode) {
        AngleMode.DEG -> Math.toDegrees(x)
        AngleMode.RAD -> x
        AngleMode.GRAD -> x * 200.0 / PI
    }

    private fun clean(v: Double): Double = if (abs(v) < 1e-14) 0.0 else v

    private fun applyFunc(name: String, x: Double): Double = when (name) {
        "sin" -> clean(sin(toRad(x)))
        "cos" -> clean(cos(toRad(x)))
        "tan" -> {
            val r = toRad(x)
            if (abs(cos(r)) < 1e-14) mathError()
            clean(tan(r))
        }
        "cot" -> {
            val r = toRad(x)
            if (abs(sin(r)) < 1e-14) mathError()
            clean(cos(r) / sin(r))
        }
        "asin" -> { if (abs(x) > 1) mathError(); clean(fromRad(asin(x))) }
        "acos" -> { if (abs(x) > 1) mathError(); clean(fromRad(acos(x))) }
        "atan" -> clean(fromRad(atan(x)))
        "acot" -> if (x == 0.0) fromRad(PI / 2) else clean(fromRad(atan(1.0 / x)))
        "sinh" -> Math.sinh(x)
        "cosh" -> Math.cosh(x)
        "tanh" -> Math.tanh(x)
        "asinh" -> ln(x + sqrt(x * x + 1.0))
        "acosh" -> { if (x < 1) mathError(); ln(x + sqrt(x * x - 1.0)) }
        "atanh" -> { if (abs(x) >= 1) mathError(); 0.5 * ln((1 + x) / (1 - x)) }
        "ln" -> { if (x <= 0) mathError(); ln(x) }
        "log" -> { if (x <= 0) mathError(); clean(log10(x)) }
        "sqrt" -> { if (x < 0) mathError(); sqrt(x) }
        "cbrt" -> Math.cbrt(x)
        "abs" -> abs(x)
        "ceil" -> ceil(x)
        "floor" -> floor(x)
        else -> syntaxError()
    }

    private fun applyFunc2(name: String, x: Double, y: Double): Double = when (name) {
        "logb" -> {
            if (x <= 0 || x == 1.0 || y <= 0) mathError()
            clean(ln(y) / ln(x))
        }
        "gcd" -> gcd(x, y)
        "lcm" -> {
            val g = gcd(x, y)
            if (g == 0.0) 0.0 else abs(x * y) / g
        }
        "ranint" -> {
            if (!isInt(x) || !isInt(y) || x > y) mathError()
            x + floor(Math.random() * (y - x + 1))
        }
        else -> syntaxError()
    }

    private fun gcd(a: Double, b: Double): Double {
        if (!isInt(a) || !isInt(b) || abs(a) > 9e15 || abs(b) > 9e15) mathError()
        var x = abs(a).toLong()
        var y = abs(b).toLong()
        while (y != 0L) {
            val t = x % y
            x = y
            y = t
        }
        return x.toDouble()
    }

    private fun isInt(x: Double) = x == floor(x) && !x.isInfinite()

    private fun factorial(v: Double): Double {
        if (v < 0 || !isInt(v) || v > 170) mathError()
        var r = 1.0
        var i = 2
        while (i <= v.toInt()) { r *= i; i++ }
        return r
    }

    private fun nPr(n: Double, r: Double): Double {
        if (!isInt(n) || !isInt(r) || n < 0 || r < 0 || r > n) mathError()
        var res = 1.0
        var i = n - r + 1
        while (i <= n) { res *= i; i += 1 }
        return res
    }

    private fun nCr(n: Double, r: Double): Double {
        if (!isInt(n) || !isInt(r) || n < 0 || r < 0 || r > n) mathError()
        val k = min(r, n - r)
        var res = 1.0
        var i = 1.0
        while (i <= k) {
            res = res * (n - k + i) / i
            i += 1
        }
        return Math.round(res).toDouble().let { if (res < 9e15) it else res }
    }
}

object Formatter {
    /** Zwykły zapis dziesiętny do ~10 cyfr znaczących; bardzo duże/małe liczby jako notacja naukowa. */
    fun format(v: Double): String {
        if (v == 0.0) return "0"
        val a = abs(v)
        if (a >= 1e10 || a < 1e-4) {
            val parts = String.format(Locale.US, "%.9E", v).split("E")
            var mant = parts[0]
            if (mant.contains('.')) mant = mant.trimEnd('0').trimEnd('.')
            val exp = parts[1].toInt()
            return "${mant}×10^$exp"
        }
        return BigDecimal(v).round(MathContext(10)).stripTrailingZeros().toPlainString()
    }

    /** Notacja inżynierska: wykładnik będący wielokrotnością 3. */
    fun eng(v: Double): String {
        if (v == 0.0) return "0"
        val exp = floor(log10(abs(v)))
        val e3 = (floor(exp / 3.0) * 3).toInt()
        val mant = v / Math.pow(10.0, e3.toDouble())
        val m = BigDecimal(mant).round(MathContext(10)).stripTrailingZeros().toPlainString()
        return "${m}×10^$e3"
    }

    /** Ułamek zwykły (ułamki łańcuchowe), albo null gdy liczba jest całkowita / nie da się jej ładnie zapisać. */
    fun toFraction(v: Double): String? {
        val a = abs(v)
        if (a == floor(a) || a > 1e9) return null
        var h1 = 1L
        var h0 = 0L
        var k1 = 0L
        var k0 = 1L
        var x = a
        for (i in 0 until 25) {
            val whole = floor(x).toLong()
            val h2 = whole * h1 + h0
            val k2 = whole * k1 + k0
            if (k2 > 10000L) return null
            h0 = h1; h1 = h2
            k0 = k1; k1 = k2
            if (abs(a - h1.toDouble() / k1.toDouble()) < 1e-11 * max(1.0, a)) {
                if (k1 == 1L) return null
                return (if (v < 0) "-" else "") + "$h1/$k1"
            }
            val frac = x - whole
            if (frac < 1e-12) return null
            x = 1.0 / frac
        }
        return null
    }
}
