package pl.kacper.kalkulator

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import java.nio.LongBuffer

/** Przygotowanie obrazu 384×384 (piksele ARGB) do postaci wejścia modelu. */
object Pre {
    const val SIZE = 384

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

    fun recognize(chw: FloatArray, maxTokens: Int = 300): String {
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
                    if (best.toLong() == END) break
                    ids.add(best.toLong())
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

    companion object {
        private const val START = 2L
        private const val END = 2L
    }
}
