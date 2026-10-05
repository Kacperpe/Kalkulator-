package pl.kacper.kalkulator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.media.ExifInterface
import android.view.MotionEvent
import android.view.View
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object PhotoUtil {
    /** Wczytuje zdjęcie pomniejszone do rozsądnego rozmiaru i obrócone zgodnie z EXIF. */
    fun decode(open: () -> InputStream?, maxDim: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options()
        bounds.inJustDecodeBounds = true
        open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options()
        opts.inSampleSize = sample
        var bmp = open()?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val rotation = try {
            open()?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f
        } catch (e: Exception) {
            0f
        }
        if (rotation != 0f) {
            val m = Matrix()
            m.postRotate(rotation)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        return bmp
    }

    /** Wycinek zdjęcia jako wejście modelu (384×384, białe tło pod przezroczystością). */
    fun toInput(crop: Bitmap): FloatArray {
        val s = Pre.SIZE
        val target = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(target)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(crop, null, RectF(0f, 0f, s.toFloat(), s.toFloat()), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
        val px = IntArray(s * s)
        target.getPixels(px, 0, s, 0, 0, s, s)
        return Pre.toChw(px)
    }
}

/** Podgląd zdjęcia z ramką do zaznaczenia jednego wzoru. */
class CropView(context: Context, private val bmp: Bitmap) : View(context) {
    private val d = resources.displayMetrics.density
    private val img = RectF()
    private val crop = RectF()
    private var mode = 0 // 0 nic, 1 przesuwanie, 2 LG, 3 PG, 4 LD, 5 PD
    private var lastX = 0f
    private var lastY = 0f
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val shade = Paint().apply { color = 0xAA000000.toInt() }
    private val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2 * d
    }
    private val handle = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFFFC629.toInt() }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val pad = 14 * d
        val sc = min((w - 2 * pad) / bmp.width, (h - 2 * pad) / bmp.height)
        val bw = bmp.width * sc
        val bh = bmp.height * sc
        img.set((w - bw) / 2, (h - bh) / 2, (w + bw) / 2, (h + bh) / 2)
        val cw = img.width() * 0.86f
        val ch = min(img.height() * 0.9f, max(img.height() * 0.28f, 90 * d))
        crop.set(img.centerX() - cw / 2, img.centerY() - ch / 2, img.centerX() + cw / 2, img.centerY() + ch / 2)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(bmp, null, img, bitmapPaint)
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, crop.top, shade)
        canvas.drawRect(0f, crop.bottom, w, h, shade)
        canvas.drawRect(0f, crop.top, crop.left, crop.bottom, shade)
        canvas.drawRect(crop.right, crop.top, w, crop.bottom, shade)
        canvas.drawRect(crop, border)
        val r = 9 * d
        canvas.drawCircle(crop.left, crop.top, r, handle)
        canvas.drawCircle(crop.right, crop.top, r, handle)
        canvas.drawCircle(crop.left, crop.bottom, r, handle)
        canvas.drawCircle(crop.right, crop.bottom, r, handle)
    }

    private fun near(x: Float, y: Float, cx: Float, cy: Float): Boolean = abs(x - cx) < 34 * d && abs(y - cy) < 34 * d

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mode = when {
                    near(x, y, crop.left, crop.top) -> 2
                    near(x, y, crop.right, crop.top) -> 3
                    near(x, y, crop.left, crop.bottom) -> 4
                    near(x, y, crop.right, crop.bottom) -> 5
                    crop.contains(x, y) -> 1
                    else -> 0
                }
                lastX = x
                lastY = y
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = x - lastX
                val dy = y - lastY
                val minSize = 44 * d
                when (mode) {
                    1 -> {
                        val mx = dx.coerceIn(img.left - crop.left, img.right - crop.right)
                        val my = dy.coerceIn(img.top - crop.top, img.bottom - crop.bottom)
                        crop.offset(mx, my)
                    }
                    2, 4 -> crop.left = (crop.left + dx).coerceIn(img.left, crop.right - minSize)
                    3, 5 -> crop.right = (crop.right + dx).coerceIn(crop.left + minSize, img.right)
                }
                when (mode) {
                    2, 3 -> crop.top = (crop.top + dy).coerceIn(img.top, crop.bottom - minSize)
                    4, 5 -> crop.bottom = (crop.bottom + dy).coerceIn(crop.top + minSize, img.bottom)
                }
                lastX = x
                lastY = y
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> mode = 0
        }
        return true
    }

    fun result(): Bitmap {
        val sx = bmp.width / img.width()
        val sy = bmp.height / img.height()
        val x = ((crop.left - img.left) * sx).toInt().coerceIn(0, bmp.width - 1)
        val y = ((crop.top - img.top) * sy).toInt().coerceIn(0, bmp.height - 1)
        val w = (crop.width() * sx).toInt().coerceIn(1, bmp.width - x)
        val h = (crop.height() * sy).toInt().coerceIn(1, bmp.height - y)
        return Bitmap.createBitmap(bmp, x, y, w, h)
    }
}
