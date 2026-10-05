package pl.kacper.kalkulator

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class LatexTest {
    private fun code(latex: String) = Latex.convert(latex).joinToString("") { it.c }
    private fun value(latex: String, x: Double = 0.0, mode: AngleMode = AngleMode.RAD) =
        Evaluator(mode, 0.0, mapOf('x' to x)).evaluate(code(latex))

    private fun assertFails(latex: String) {
        try {
            Latex.convert(latex)
            fail("Oczekiwano błędu dla: $latex")
        } catch (e: CalcException) {
            // ok
        }
    }

    @Test fun modelOutputs() {
        assertEquals("2\$x^3+\$x^2−13\$x+6=0", code("2 x ^ { 3 } + x ^ { 2 } - 1 3 x + 6 = 0"))
        assertEquals("((\$x^2+3\$x−1)÷(\$x+1))", code("\\frac { x ^ { 2 } + 3 x - 1 } { x + 1 }"))
        assertEquals("sqrt(5−2\$x)=3", code("\\sqrt { 5 - 2 x } = 3"))
        assertEquals("sin(2\$x+1)+cos(1−\$x)", code("\\operatorname { s i n } ( 2 x + 1 ) + \\operatorname { c o s } ( 1 - x )"))
        assertEquals("2\$x+5=11", code("2 x + 5 = 1 1"))
        assertEquals("logb(2,8)+e^(2\$x)", code("\\log _ { 2 } 8 + e ^ { 2 x }"))
        assertEquals("(3)~(27)+abs(\$x−2)", code("\\sqrt [ 3 ] { 2 7 } + | x - 2 |"))
    }

    @Test fun values() {
        assertEquals(32.5, value("3 \\cdot ( 4 + 7 ) - \\frac { 1 } { 2 }"), 1e-9)
        assertEquals(3.0, value("\\sqrt [ 3 ] { 2 7 } + | x - 2 |", 2.0), 1e-9)
        assertEquals(1.0, value("\\sin ^ { 2 } x + \\cos ^ { 2 } x", 0.7), 1e-9)
        assertEquals(9.0, value("\\left( x + 1 \\right) ^ { 2 }", 2.0), 1e-9)
        assertEquals(2.5, value("2 , 5"), 1e-9)
        assertEquals(Math.sin(2.0) * Math.cos(1.0), value("\\sin ( 2 x ) \\cos x", 1.0), 1e-9)
        assertEquals(Math.sin(2.0), value("\\sin 2 x", 1.0), 1e-9)
        assertEquals(30.0, value("\\sin ^ { - 1 } \\frac { 1 } { 2 }", 0.0, AngleMode.DEG), 1e-9)
        assertEquals(0.5, value("\\sin 3 0 ^ { \\circ }", 0.0, AngleMode.DEG), 1e-9)
        assertEquals(1.0, value("\\mathrm { t g } \\frac { \\pi } { 4 }"), 1e-9)
        assertEquals(5.0, value("\\left| 2 - 7 \\right|"), 1e-9)
        assertEquals(Math.E * Math.E, value("\\exp ( 2 )"), 1e-9)
        assertEquals(1.5, value("\\frac { 1 } { 2 } x", 3.0), 1e-9)
    }

    @Test fun variables() {
        assertEquals("2\$x+5=11", code("2 a + 5 = 1 1"))
        assertEquals("\$a\$x+\$b", code("a x + b"))
        assertFails("z + k")
    }

    @Test fun unsupported() {
        assertFails("\\int _ { 0 } ^ { 1 } x d x")
        assertFails("x = \\frac { - b \\pm \\sqrt { b ^ { 2 } - 4 a c } } { 2 a }")
        assertFails("\\left\\{ \\begin{array} { l } { a = 1 } \\\\ \\end{array} \\right.")
        assertFails("")
    }

    @Test fun compacting() {
        assertEquals("\\frac{x^{2}+1}{2}", Latex.compact("\\frac { x ^ { 2 } + 1 } { 2 }"))
        assertEquals("\\sin x+\\pi", Latex.compact("\\sin x + \\pi"))
    }
}
