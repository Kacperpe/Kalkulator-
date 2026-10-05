package pl.kacper.kalkulator

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume
import org.junit.Test

/**
 * Test całej ścieżki „obrazek → LaTeX → wyrażenie → wynik" na prawdziwym modelu.
 * Model i obrazki przygotowuje tools/prepare_model.py; bez nich test jest pomijany.
 */
class RecognizerTest {
    private val assets = File("src/main/assets")
    private val images = File("src/test/resources/mfr")

    /** Plik .rgb to 384×384 pikseli, po 3 bajty (R, G, B). */
    private fun input(name: String): FloatArray {
        val raw = File(images, name).readBytes()
        val n = Pre.SIZE * Pre.SIZE
        assertEquals(n * 3, raw.size)
        val px = IntArray(n) { k ->
            ((raw[3 * k].toInt() and 255) shl 16) or ((raw[3 * k + 1].toInt() and 255) shl 8) or (raw[3 * k + 2].toInt() and 255)
        }
        return Pre.toChw(px)
    }

    @Test fun recognizesAndSolves() {
        val enc = File(assets, "mfr_encoder.onnx")
        val dec = File(assets, "mfr_decoder.onnx")
        val voc = File(assets, "mfr_vocab.txt")
        Assume.assumeTrue(enc.exists() && dec.exists() && voc.exists() && File(images, "t0.rgb").exists())
        val rec = FormulaRecognizer(enc.path, dec.path, voc.readLines(Charsets.UTF_8))

        fun code(name: String): String {
            val latex = rec.recognize(input(name))
            println("$name -> $latex")
            return Latex.convert(latex).joinToString("") { it.c }
        }
        fun fn(c: String): (Double) -> Double = { x -> Evaluator(AngleMode.RAD, 0.0, mapOf('x' to x)).evaluate(c) }
        fun solve(eq: String): List<Double> {
            val parts = eq.split("=")
            return MathTools.solve(fn("(" + parts[0] + ")-(" + parts[1] + ")"))
        }

        val c0 = code("t0.rgb")
        assertEquals("2\$x+5=11", c0)
        assertEquals(listOf(3.0), solve(c0))

        assertEquals(1.5, fn(code("t1.rgb"))(1.0), 1e-9)

        val r2 = solve(code("t2.rgb"))
        assertEquals(1, r2.size)
        assertEquals(-2.0, r2[0], 1e-6)

        assertEquals(32.5, fn(code("t3.rgb"))(0.0), 1e-9)

        val r4 = solve(code("t4.rgb"))
        assertEquals(3, r4.size)
        assertEquals(-3.0, r4[0], 1e-6)
        assertEquals(0.5, r4[1], 1e-6)
        assertEquals(2.0, r4[2], 1e-6)
    }
}
