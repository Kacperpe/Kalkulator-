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
 * Parser rekurencyjny wyrażeń: + - × ÷ ^ ! % nPr (P) nCr (C), funkcje trygonometryczne,
 * hiperboliczne, log/ln, pierwiastki, stałe π i e, Ans, notacja E (np. 5E3), mnożenie domyślne (2π, 3sin(30)).
 */
class Evaluator(private val mode: AngleMode = AngleMode.DEG, private val ans: Double = 0.0) {
    private var s = ""
    private var p = 0

    private val names = listOf(
        "asinh", "acosh", "atanh", "sinh", "cosh", "tanh",
        "asin", "acos", "atan", "sqrt", "cbrt",
        "sin", "cos", "tan", "log", "abs", "rand", "ln", "pi", "e"
    )

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
        return c.isDigit() || c == '.' || c == '(' || c == '√' || c in 'a'..'z' || c == 'A'
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
        return base
    }

    private fun parsePowerOperand(): Double = when (peek()) {
        '-' -> { p++; -parsePowerOperand() }
        '+' -> { p++; parsePowerOperand() }
        else -> parsePower()
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
        val x = parseExpr()
        closeParen()
        return applyFunc(name, x)
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
        "asin" -> { if (abs(x) > 1) mathError(); clean(fromRad(asin(x))) }
        "acos" -> { if (abs(x) > 1) mathError(); clean(fromRad(acos(x))) }
        "atan" -> clean(fromRad(atan(x)))
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
        else -> syntaxError()
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
