package pl.kacper.kalkulator

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Przygotowanie obrazu 384×384 (piksele ARGB) do postaci wejścia modelu. */
object Pre {
    const val SIZE = 384

    /**
     * Szarość i rozciągnięcie jasności: 2% najciemniejszych punktów → czerń, jasne tło (mediana) → biel.
     * Zdjęcia kartek są szarawe i nierówno oświetlone; model lepiej czyta czarne na białym.
     */
    fun stretchContrast(px: IntArray) {
        val hist = IntArray(256)
        val g = IntArray(px.size)
        for (k in px.indices) {
            val p = px[k]
            val v = (((p shr 16) and 255) * 299 + ((p shr 8) and 255) * 587 + (p and 255) * 114) / 1000
            g[k] = v
            hist[v]++
        }
        fun pct(f: Double): Int {
            val target = (px.size * f).toLong()
            var acc = 0L
            for (v in 0..255) {
                acc += hist[v]
                if (acc > target) return v
            }
            return 255
        }
        val lo = pct(0.02)
        val hi = pct(0.5)
        if (hi - lo < 24) {
            // prawie jednolity obraz - zostawiamy w szarości bez wzmacniania szumu
            for (k in px.indices) { val v = g[k]; px[k] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
            return
        }
        val range = (hi - lo).toFloat()
        for (k in px.indices) {
            val v = (((g[k] - lo) / range) * 255f).toInt().coerceIn(0, 255)
            px[k] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
    }

    fun toChw(px: IntArray): FloatArray {
        val n = SIZE * SIZE
        val out = FloatArray(3 * n)
        for (k in 0 until n) {
            val p = px[k]
            out[k] = ((p shr 16) and 255) / 127.5f - 1f
            out[n + k] = ((p shr 8) and 255) / 127.5f - 1f
            out[2 * n + k] = (p and 255) / 127.5f - 1f
        }
        return out
    }
}

/**
 * Rozpoznawanie wzoru z obrazu: model Pix2Text-MFR (koder obrazu + dekoder tekstu) uruchamiany
 * lokalnie przez ONNX Runtime. Wynikiem jest zapis LaTeX.
 */
class FormulaRecognizer(encoderPath: String, decoderPath: String, private val vocab: List<String>) {
    private val env = OrtEnvironment.getEnvironment()
    private val encoder = env.createSession(encoderPath, OrtSession.SessionOptions())
    private val decoder = env.createSession(decoderPath, OrtSession.SessionOptions())

    @Volatile var cancelled = false

    fun recognize(chw: FloatArray, maxTokens: Int = 200): String {
        cancelled = false
        val ids = ArrayList<Long>()
        ids.add(START)
        OnnxTensor.createTensor(env, FloatBuffer.wrap(chw), longArrayOf(1, 3, Pre.SIZE.toLong(), Pre.SIZE.toLong())).use { image ->
            encoder.run(mapOf("pixel_values" to image)).use { encoded ->
                val hidden = encoded.get(0) as OnnxTensor
                for (step in 0 until maxTokens) {
                    val arr = LongArray(ids.size) { ids[it] }
                    var best = 0
                    OnnxTensor.createTensor(env, LongBuffer.wrap(arr), longArrayOf(1, arr.size.toLong())).use { idTensor ->
                        decoder.run(mapOf("input_ids" to idTensor, "encoder_hidden_states" to hidden)).use { res ->
                            val logits = res.get(0) as OnnxTensor
                            val v = logits.info.shape[2].toInt()
                            val buf = logits.floatBuffer
                            val off = (arr.size - 1) * v
                            var bestVal = Float.NEGATIVE_INFINITY
                            for (k in 0 until v) {
                                val x = buf.get(off + k)
                                if (x > bestVal) { bestVal = x; best = k }
                            }
                        }
                    }
                    if (best.toLong() == END || cancelled) break
                    ids.add(best.toLong())
                    if (looping(ids)) break
                }
            }
        }
        val sb = StringBuilder()
        for (k in 1 until ids.size) {
            val id = ids[k].toInt()
            if (id > 4 && id < vocab.size) sb.append(vocab[id])
        }
        return sb.toString().replace('Ġ', ' ').trim()
    }

    /** Model czasem zapętla się na nieczytelnym obrazie (powtarza ten sam fragment) - wtedy przerywamy. */
    private fun looping(ids: List<Long>): Boolean {
        val n = ids.size
        for (len in 1..12) {
            val reps = if (len <= 2) 8 else 4
            if (n < len * reps) continue
            var same = true
            loop@ for (r in 1 until reps) {
                for (k in 0 until len) {
                    if (ids[n - 1 - k] != ids[n - 1 - k - r * len]) { same = false; break@loop }
                }
            }
            if (same) return true
        }
        return false
    }

    companion object {
        private const val START = 2L
        private const val END = 2L
    }
}
