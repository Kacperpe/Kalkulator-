package pl.kacper.kalkulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathLayoutTest {
    private val meter = object : MathLayout.Meter {
        override fun width(text: String, size: Float, italic: Boolean) = text.length * size * 0.6f
    }

    /** Żetony jak z klawiatury: cyfry osobno, x jako $x, funkcje z nawiasem. */
    private fun toks(vararg parts: String): List<MathLayout.Tk> = parts.map {
        when (it) {
            "x" -> MathLayout.Tk("x", "\$x")
            "√(" -> MathLayout.Tk("√(", "sqrt(")
            "²" -> MathLayout.Tk("²", "^2")
            else -> MathLayout.Tk(it, it)
        }
    }

    private fun dump(t: List<MathLayout.Tk>, cursor: Int = -1) = MathLayout.dump(MathLayout.parse(t, cursor))

    @Test fun fractions() {
        assertEquals("{1|2}", dump(toks("1", "/", "2")))
        assertEquals("3+{4|5}", dump(toks("3", "+", "4", "/", "5")))
        assertEquals("{12|345}", dump(toks("1", "2", "/", "3", "4", "5")))
        assertEquals("{x+1|x−1}", dump(toks("(", "x", "+", "1", ")", "/", "(", "x", "−", "1", ")")))
        assertEquals("{□|□}", dump(toks("/")))
        assertEquals("{1|□}", dump(toks("1", "/")))
        assertEquals("{1|2^[2]}", dump(toks("1", "/", "2", "^", "2")))
        assertEquals("{{1|2}|3}", dump(toks("1", "/", "2", "/", "3")))
        assertEquals("{1|−2}", dump(toks("1", "/", "−", "2")))
        assertEquals("2−{3|4}", dump(toks("2", "−", "3", "/", "4")))
        assertEquals("{sin(30)|2}", dump(toks("sin(", "3", "0", ")", "/", "2")))
    }

    @Test fun templatesForPowerRootAndFraction() {
        // potęga z działaniem w wykładniku, kursor może stać w środku
        val pow = toks("2", "^(", "3", "+", "1", ")", "−", "1")
        assertEquals("2^[3+1]−1", dump(pow, 4))
        assertEquals("2^[□]", dump(toks("2", "^(", ")")))
        // pierwiastek z działaniem
        assertEquals("√[9+16]+1", dump(toks("√(", "9", "+", "1", "6", ")", "+", "1")))
        assertEquals("√[□]", dump(toks("√(", ")")))
        // pusty ułamek-szablon i ułamek z działaniami w liczniku i mianowniku
        assertEquals("{□|□}", dump(toks("(", ")", "/", "(", ")")))
        assertEquals("{x+1|x−1}", dump(toks("(", "x", "+", "1", ")", "/", "(", "x", "−", "1", ")"), 2))
        // każde miejsce kursora w szablonach jest widoczne
        val cases = listOf(pow, toks("2", "^(", ")"), toks("(", ")", "/", "(", ")"), toks("cbrt(", "8", ")", "+", "1"))
        for (t in cases) for (cur in 0..t.size) {
            val r = MathLayout.layout(MathLayout.parse(t, cur), 40f, meter)
            assertTrue("brak kursora $cur w ${t.joinToString(" ") { it.c }}", r.carets.containsKey(cur))
        }
        // kursor w pustym wykładniku stoi wyżej niż przed nim (w indeksie górnym)
        val r = MathLayout.layout(MathLayout.parse(toks("2", "^(", ")"), 2), 40f, meter)
        assertTrue(r.carets.getValue(2).bottom < r.carets.getValue(1).bottom)
    }

    @Test fun powersAndRoots() {
        assertEquals("2^[3]", dump(toks("2", "^", "3")))
        assertEquals("2^[3+1]", dump(toks("2", "^", "(", "3", "+", "1", ")")))
        assertEquals("x^[2]+1", dump(toks("x", "²", "+", "1")))
        assertEquals("√[2]+1", dump(toks("√(", "2", ")", "+", "1")))
        assertEquals("√[5−2x]", dump(toks("√(", "5", "−", "2", "x")))
        assertEquals("2^[□]", dump(toks("2", "^")))
    }

    @Test fun results() {
        assertEquals("−{3|4}", MathLayout.dump(MathLayout.parse(MathLayout.resultTokens("-3/4"))))
        assertEquals("1.5×10^[−7]", MathLayout.dump(MathLayout.parse(MathLayout.resultTokens("1.5×10^-7"))))
    }

    /** Każda pozycja kursora musi mieć swoje miejsce na ekranie. */
    @Test fun everyCursorPositionIsDrawn() {
        val cases = listOf(
            toks("1", "/", "2"),
            toks("/"),
            toks("3", "+", "4", "/", "5", "×", "x"),
            toks("(", "x", "+", "1", ")", "/", "(", "x", "−", "1", ")"),
            toks("2", "^", "(", "3", "+", "1", ")", "−", "x", "²"),
            toks("√(", "2", ")", "+", "1", "/", "√(", "3"),
            toks("sin(", "3", "0", ")", "/", "2", "^", "2"),
            toks("1", "/", "2", "/", "3", "/")
        )
        for (t in cases) {
            for (cur in 0..t.size) {
                val r = MathLayout.layout(MathLayout.parse(t, cur), 40f, meter)
                assertTrue("brak kursora $cur w ${t.joinToString(" ") { it.d }}", r.carets.containsKey(cur))
            }
        }
    }

    @Test fun fractionIsTallerAndNarrowerThanInline() {
        val inline = MathLayout.layout(MathLayout.parse(toks("1", "2", "3", "÷", "4", "5", "6")), 40f, meter)
        val frac = MathLayout.layout(MathLayout.parse(toks("1", "2", "3", "/", "4", "5", "6")), 40f, meter)
        assertTrue(frac.w < inline.w)
        assertTrue(frac.asc + frac.desc > 1.8f * (inline.asc + inline.desc))
        // licznik nad kreską, mianownik pod nią
        val texts = frac.ops.filterIsInstance<MathLayout.TextOp>()
        val bar = frac.ops.filterIsInstance<MathLayout.LineOp>().single()
        assertTrue(texts.filter { it.text in listOf("1", "2", "3") }.all { it.y < bar.y1 })
        assertTrue(texts.filter { it.text in listOf("4", "5", "6") }.all { it.y > bar.y1 })
    }

    @Test fun logarithmWithBase() {
        val t = listOf(
            MathLayout.Tk("log(", "logb("), MathLayout.Tk("2", "2"), MathLayout.Tk(",", ","),
            MathLayout.Tk("8", "8"), MathLayout.Tk("×", "×"), MathLayout.Tk("4", "4"), MathLayout.Tk(")", ")"),
            MathLayout.Tk("+", "+"), MathLayout.Tk("1", "1")
        )
        assertEquals("log_[2](8×4)+1", dump(t))
        val empty = listOf(MathLayout.Tk("log(", "logb("), MathLayout.Tk(",", ","), MathLayout.Tk(")", ")"))
        assertEquals("log_[□](□)", dump(empty))
        for (tt in listOf(t, empty)) for (cur in 0..tt.size) {
            val r = MathLayout.layout(MathLayout.parse(tt, cur), 40f, meter)
            assertTrue("brak kursora $cur", r.carets.containsKey(cur))
        }
        // podstawa niżej niż argument (indeks dolny)
        val r = MathLayout.layout(MathLayout.parse(empty, 1), 40f, meter)
        assertTrue(r.carets.getValue(1).bottom > r.carets.getValue(2).bottom)
    }
}
