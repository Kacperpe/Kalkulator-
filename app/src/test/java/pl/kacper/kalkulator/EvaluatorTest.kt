package pl.kacper.kalkulator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class EvaluatorTest {
    private fun ev(expr: String, mode: AngleMode = AngleMode.DEG, ans: Double = 0.0) =
        Evaluator(mode, ans).evaluate(expr)

    private fun assertEv(expected: Double, expr: String, mode: AngleMode = AngleMode.DEG) =
        assertEquals(expr, expected, ev(expr, mode), 1e-9)

    private fun assertFails(expr: String) {
        try {
            ev(expr)
            fail("Oczekiwano błędu dla: $expr")
        } catch (e: CalcException) {
            // ok
        }
    }

    @Test fun basicArithmetic() {
        assertEv(14.0, "2+3×4")
        assertEv(20.0, "(2+3)×4")
        assertEv(2.5, "5÷2")
        assertEv(-1.0, "2−3")
        assertEv(6.0, "2×−3×−1")
    }

    @Test fun powersAndRoots() {
        assertEv(512.0, "2^3^2")
        assertEv(-4.0, "−2^2")
        assertEv(3.0, "√(9)")
        assertEv(2.0, "8^(1/3")
        assertEv(2.0, "cbrt(8)")
        assertEv(0.5, "2^(-1)")
    }

    @Test fun trigDegrees() {
        assertEv(0.5, "sin(30)")
        assertEv(0.0, "sin(180)")
        assertEv(0.0, "cos(90)")
        assertEv(1.0, "tan(45)")
        assertEv(30.0, "asin(0.5)")
        assertFails("tan(90)")
    }

    @Test fun trigRadiansAndGrad() {
        assertEv(1.0, "sin(π÷2)", AngleMode.RAD)
        assertEv(1.0, "sin(100)", AngleMode.GRAD)
    }

    @Test fun logsAndConstants() {
        assertEv(3.0, "log(1000)")
        assertEv(1.0, "ln(e)")
        assertEv(2 * Math.PI, "2π")
        assertEv(2.0, "2sin(30)×2")
        assertEv(Math.E * 2, "2e")
    }

    @Test fun combinatorics() {
        assertEv(120.0, "5!")
        assertEv(720.0, "10P3")
        assertEv(120.0, "10C3")
        assertEv(1.0, "0!")
        assertFails("2.5!")
        assertFails("3P5")
    }

    @Test fun percentAndExponent() {
        assertEv(0.5, "50%")
        assertEv(5000.0, "5E3")
        assertEv(0.005, "5E−3")
    }

    @Test fun ansAndErrors() {
        assertEquals(10.0, ev("Ans×2", ans = 5.0), 1e-9)
        assertFails("1÷0")
        assertFails("2+")
        assertFails("")
        assertFails("√(−1)")
        assertFails("ln(0)")
    }

    @Test fun formatting() {
        assertEquals("0.3", Formatter.format(0.1 + 0.2))
        assertEquals("14", Formatter.format(14.0))
        assertEquals("0.3333333333", Formatter.format(1.0 / 3))
        assertEquals("1.5×10^12", Formatter.format(1.5e12))
        assertEquals("-2.5×10^-7", Formatter.format(-2.5e-7))
        assertEquals("1000000", Formatter.format(1e6))
    }

    @Test fun fractions() {
        assertEquals("1/3", Formatter.toFraction(1.0 / 3))
        assertEquals("-7/8", Formatter.toFraction(-0.875))
        assertEquals("22/7", Formatter.toFraction(22.0 / 7))
        assertNull(Formatter.toFraction(4.0))
        assertNull(Formatter.toFraction(Math.PI))
    }
}
