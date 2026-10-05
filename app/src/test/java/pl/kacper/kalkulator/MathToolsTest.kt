package pl.kacper.kalkulator

import org.junit.Assert.assertEquals
import org.junit.Test

class MathToolsTest {
    private fun fn(code: String): (Double) -> Double =
        { x -> Evaluator(AngleMode.RAD, 0.0, mapOf('x' to x)).evaluate(code) }

    private fun assertRoots(expected: List<Double>, code: String) {
        val r = MathTools.solve(fn(code))
        assertEquals(code + " -> " + r, expected.size.toLong(), r.size.toLong())
        for (i in expected.indices) assertEquals(code, expected[i], r[i], 1e-6)
    }

    @Test fun solving() {
        assertRoots(listOf(-3.0, 0.5, 2.0), "2\$x^3+\$x^2-13\$x+6")
        assertRoots(listOf(0.0), "\$x^2")
        assertRoots(listOf(1.0 / 3), "(\$x-1/3)^2")
        assertRoots(emptyList(), "\$x^2+1")
        assertRoots(emptyList(), "1/\$x")
        assertRoots(listOf(Math.E), "ln(\$x)-1")
    }

    @Test fun numberTheory() {
        assertEquals("2^5 × 5^5", MathTools.factorString(100000))
        assertEquals("97", MathTools.factorString(97))
        assertEquals(listOf(1L, 2L, 3L, 4L, 6L, 9L, 12L, 18L, 36L), MathTools.divisors(36))
        assertEquals("XXXVI", MathTools.toRoman(36))
        assertEquals("MCMXCIV", MathTools.toRoman(1994))
    }

    @Test fun numberForms() {
        assertEquals("0.(27)", MathTools.repeating(3, 11))
        assertEquals("0.25", MathTools.repeating(1, 4))
        assertEquals("0.1(6)", MathTools.repeating(1, 6))
        assertEquals("3", MathTools.repeating(6, 2))
        assertEquals("0°16'21.82\"", MathTools.dms(3.0 / 11))
        assertEquals("1.5×10^3", MathTools.sci(1500.0))
    }

    @Test fun matrices() {
        val a = MathTools.parseMatrix("4 1\n2 -1")!!
        assertEquals(-6.0, MathTools.det(a), 1e-9)
        assertEquals(2, MathTools.rank(a))
        val inv = MathTools.inverse(a)!!
        assertEquals(1.0 / 6, inv[0][0], 1e-9)
        assertEquals(-2.0 / 3, inv[1][1], 1e-9)
        val p = MathTools.mul(a, inv)
        assertEquals(1.0, p[0][0], 1e-9)
        assertEquals(0.0, p[0][1], 1e-9)
        assertEquals(1, MathTools.rank(MathTools.parseMatrix("1 2;2 4")!!))
        assertEquals(null, MathTools.inverse(MathTools.parseMatrix("1 2;2 4")!!))
        assertEquals(null, MathTools.parseMatrix("1 2\n3"))
    }

    @Test fun statistics() {
        val r = MathTools.statsReport("2 4 4 4 5 5 7 9")
        assertEquals(true, r.contains("średnia x= 5\n"))
        assertEquals(true, r.contains("σx       = 2\n"))
        val two = MathTools.statsReport("1;3 2;5 3;7")
        assertEquals(true, two.contains("a        = 1\n"))
        assertEquals(true, two.contains("b        = 2\n"))
        assertEquals(true, MathTools.statsReport("1 abc").startsWith("Nie rozumiem"))
    }

    @Test fun userFunctions() {
        val fns = mapOf("u1" to "\$x^2+1", "u2" to "u1(\$x)×2")
        assertEquals(10.0, Evaluator(AngleMode.DEG, 0.0, emptyMap(), fns).evaluate("u1(3)"), 1e-9)
        assertEquals(20.0, Evaluator(AngleMode.DEG, 0.0, emptyMap(), fns).evaluate("u2(3)"), 1e-9)
    }
}
