package pl.kacper.kalkulator

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.widget.OverScroller
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Wyświetlacz wzoru: ułamki piętrowe, potęgi, pierwiastki, migający kursor; stuknięcie przesuwa kursor.
 * Długi wzór nie jest zmniejszany — przewija się w poziomie (za kursorem albo palcem).
 */
class MathView(context: Context, private val alignEnd: Boolean, bold: Boolean) : View(context) {
    private val d = resources.displayMetrics.density
    @Suppress("DEPRECATION")
    private val sp = resources.displayMetrics.scaledDensity
    private val regular = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
    private val italic = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD_ITALIC else Typeface.ITALIC)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val caretPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    var textSizeSp = 26f
    var minScale = 0.5f
    var color = 0xFF1B2A2A.toInt()
    var caretColor = 0xFF2E7D6B.toInt()
    var onTapCaret: ((Int) -> Unit)? = null

    private var toks: List<MathLayout.Tk> = emptyList()
    private var cursor = -1
    private var plain: String? = null
    private var res: MathLayout.Result? = null
    private var scale = 1f
    private var offX = 0f
    private var offY = 0f
    private var blinkOn = true

    // przewijanie w poziomie
    private var contentW = 0f
    private var lastToks: List<MathLayout.Tk>? = null
    private var lastPlain: String? = null
    private var hasScrollPos = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFling = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val scroller = OverScroller(context)
    private var velocity: VelocityTracker? = null
    private var downX = 0f
    private var lastX = 0f
    private var dragging = false

    init {
        isHorizontalFadingEdgeEnabled = true
        setFadingEdgeLength((20 * d).toInt())
    }

    private val meter = object : MathLayout.Meter {
        override fun width(text: String, size: Float, italic: Boolean): Float {
            textPaint.typeface = if (italic) this@MathView.italic else regular
            textPaint.textSize = size
            return textPaint.measureText(text)
        }
    }

    private val blink = object : Runnable {
        override fun run() {
            blinkOn = !blinkOn
            invalidate()
            postDelayed(this, 530)
        }
    }

    /** Wzór z kursorem (cursor = -1: bez kursora). */
    fun show(tokens: List<MathLayout.Tk>, cursorPos: Int) {
        toks = tokens
        cursor = cursorPos
        plain = null
        blinkOn = true
        relayout()
        restartBlink()
    }

    /** Zwykły tekst (np. komunikat błędu). */
    fun showText(text: String) {
        plain = text
        toks = emptyList()
        cursor = -1
        relayout()
        restartBlink()
    }

    private fun restartBlink() {
        removeCallbacks(blink)
        if (cursor >= 0 && isAttachedToWindow) postDelayed(blink, 530)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        restartBlink()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blink)
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        relayout()
    }

    private fun relayout() {
        val size = textSizeSp * sp
        val row = if (plain != null) {
            MathLayout.Row(listOf(MathLayout.Glyph(plain!!, -1)), 0, 0, false)
        } else {
            MathLayout.parse(toks, cursor)
        }
        val r = MathLayout.layout(row, size, meter)
        res = r
        val padX = 4 * d
        val availW = max(1f, width - 2 * padX)
        val availH = max(1f, height.toFloat() - 2 * d)
        val h = r.asc + r.desc
        // zmniejszamy tylko gdy wzór jest za wysoki (piętrowe ułamki), nigdy z powodu długości
        scale = max(min(1f, availH / max(1f, h)), minScale)
        val sw = r.w * scale
        val sh = h * scale
        contentW = sw
        offY = max(0f, (height - sh) / 2)
        if (!alignEnd) offY = min(offY, 6 * d)

        val changed = toks != lastToks || plain != lastPlain
        lastToks = toks
        lastPlain = plain
        if (sw <= availW) {
            scroller.forceFinished(true)
            offX = if (alignEnd) width - padX - sw else padX
            hasScrollPos = false
        } else {
            if (!hasScrollPos || (changed && cursor < 0)) {
                // nowy długi wynik / wzór bez kursora: pokazujemy początek
                offX = padX
                hasScrollPos = true
            }
            if (cursor >= 0) {
                scroller.forceFinished(true)
                // przesuwamy tylko tyle, żeby kursor był widoczny (jak w polu tekstowym)
                val cx = (r.carets[cursor]?.x ?: r.w) * scale
                val margin = 12 * d
                if (offX + cx > width - padX - margin) offX = width - padX - margin - cx
                if (offX + cx < padX + margin) offX = padX + margin - cx
            }
            offX = clampOff(offX)
        }
        invalidate()
    }

    private fun minOff(): Float = min(4 * d, width - 4 * d - contentW)

    private fun clampOff(x: Float): Float = x.coerceIn(minOff(), 4 * d)

    private fun canScroll(): Boolean = contentW > width - 8 * d

    override fun getLeftFadingEdgeStrength(): Float {
        if (!canScroll()) return 0f
        return ((4 * d - offX) / (20 * d)).coerceIn(0f, 1f)
    }

    override fun getRightFadingEdgeStrength(): Float {
        if (!canScroll()) return 0f
        return ((offX - minOff()) / (20 * d)).coerceIn(0f, 1f)
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            offX = clampOff(scroller.currX.toFloat())
            postInvalidateOnAnimation()
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = res ?: return
        canvas.save()
        canvas.translate(offX, offY)
        canvas.scale(scale, scale)
        textPaint.color = color
        linePaint.color = color
        for (op in r.ops) {
            when (op) {
                is MathLayout.TextOp -> {
                    textPaint.typeface = if (op.italic) italic else regular
                    textPaint.textSize = op.size
                    canvas.drawText(op.text, op.x, op.y, textPaint)
                }
                is MathLayout.LineOp -> {
                    linePaint.strokeWidth = op.w
                    linePaint.strokeCap = Paint.Cap.BUTT
                    canvas.drawLine(op.x1, op.y1, op.x2, op.y2, linePaint)
                }
                is MathLayout.BoxOp -> {
                    linePaint.strokeWidth = op.w
                    linePaint.alpha = 140
                    canvas.drawRect(op.l, op.t, op.r, op.b, linePaint)
                    linePaint.alpha = 255
                }
                is MathLayout.PathOp -> {
                    linePaint.strokeWidth = op.w
                    linePaint.strokeCap = Paint.Cap.ROUND
                    path.reset()
                    path.moveTo(op.pts[0], op.pts[1])
                    var k = 2
                    while (k + 1 < op.pts.size) {
                        path.lineTo(op.pts[k], op.pts[k + 1])
                        k += 2
                    }
                    canvas.drawPath(path, linePaint)
                }
            }
        }
        if (cursor >= 0 && blinkOn) {
            val c = r.carets[cursor] ?: r.carets[r.carets.keys.filter { it <= cursor }.maxOrNull() ?: -1]
            if (c != null) {
                caretPaint.color = caretColor
                val w = max(2f * d / scale, 1.5f)
                canvas.drawRect(c.x - w / 2, c.top, c.x + w / 2, c.bottom, caretPaint)
            }
        }
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val cb = onTapCaret
        if (cb == null && !canScroll()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                scroller.forceFinished(true)
                downX = event.x
                lastX = event.x
                dragging = false
                velocity?.recycle()
                velocity = VelocityTracker.obtain()
                velocity?.addMovement(event)
            }
            MotionEvent.ACTION_MOVE -> {
                velocity?.addMovement(event)
                if (!dragging && canScroll() && abs(event.x - downX) > touchSlop) {
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    lastX = event.x
                }
                if (dragging) {
                    offX = clampOff(offX + event.x - lastX)
                    lastX = event.x
                    hasScrollPos = true
                    invalidate()
                }
            }
            MotionEvent.ACTION_UP -> {
                velocity?.addMovement(event)
                if (dragging) {
                    val v = velocity
                    if (v != null) {
                        v.computeCurrentVelocity(1000)
                        val vx = v.xVelocity
                        if (abs(vx) > minFling) {
                            scroller.fling(offX.toInt(), 0, vx.toInt(), 0, minOff().toInt(), (4 * d).toInt(), 0, 0)
                            postInvalidateOnAnimation()
                        }
                    }
                } else if (cb != null) {
                    tapCaret(event.x, event.y, cb)
                }
                velocity?.recycle()
                velocity = null
                dragging = false
            }
            MotionEvent.ACTION_CANCEL -> {
                velocity?.recycle()
                velocity = null
                dragging = false
            }
        }
        return true
    }

    private fun tapCaret(ex: Float, ey: Float, cb: (Int) -> Unit) {
        val r = res ?: return
        val x = (ex - offX) / scale
        val y = (ey - offY) / scale
        var best = -1
        var bestD = Float.MAX_VALUE
        for ((i, c) in r.carets) {
            val dy = if (y < c.top) c.top - y else if (y > c.bottom) y - c.bottom else 0f
            val dist = abs(c.x - x) + dy * 1.5f
            if (dist < bestD) {
                bestD = dist
                best = i
            }
        }
        if (best >= 0) cb(best)
    }
}
