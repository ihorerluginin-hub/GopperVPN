package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import io.nekohasekai.sagernet.R
import kotlin.math.max

/**
 * Liquid Glass: размывает содержимое окна позади себя — то, что видно
 * сквозь стеклянную поверхность. Именно это делает «стекло» стеклом,
 * как на iPhone: под панелью скроллится список, и его очертания
 * просвечивают размытыми.
 *
 * Как работает (только Android 12+, [RenderEffect]):
 * 1. В [onPreDraw] — до записи кадра — содержимое позади панели
 *    ([R.id.fragment_holder], чтобы в снимок не попала сама панель)
 *    рисуется в уменьшенную копию.
 * 2. Копия рисуется этим view в [onDraw].
 * 3. На самом view стоит [View.setRenderEffect] с блюром — система
 *    размывает нарисованную копию, и панель читается как стекло.
 *
 * На Android 11 и ниже view ничего не рисует — панель остаётся просто
 * полупрозрачной.
 */
class GlassBlurView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Радиус размытия в dp (лимит системы — 25px, применяется к вью). */
    var blurRadiusDp = 20f

    /** Во сколько раз уменьшается снимок перед размытием. */
    var downsample = 6f

    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Что размываем: контент за панелью, без самой панели. */
    private var backdrop: View? = null

    private var snapshot: Bitmap? = null
    private var location = IntArray(2)
    private var backdropLocation = IntArray(2)

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        // Снимок делается ДО записи кадра — рисовать дерево в bitmap
        // во время onDraw рендерер считает битым кадром.
        //
        // Размытие только когда панель реально видима: спрятанная
        // (performHide уводит её за экран) не должна съедать кадры
        // скролла — иначе прокрутка лагает и дёргается.
        if (isShown && translationY < height * 0.5f && width > 0 && height > 0) {
            try {
                renderSnapshot()
            } catch (_: Exception) {
                // Косметика: не рисуем кадр с размытием.
            }
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT < 31) return

        backdrop = rootView.findViewById(R.id.fragment_holder)
        backdrop?.let {
            it.viewTreeObserver.addOnPreDrawListener(preDrawListener)
            setRenderEffect(
                RenderEffect.createBlurEffect(
                    max(1f, blurRadiusDp * resources.displayMetrics.density),
                    max(1f, blurRadiusDp * resources.displayMetrics.density),
                    Shader.TileMode.CLAMP,
                ),
            )
        }
    }

    /**
     * В измерении Toolbar не участвуем (высота 0), иначе панель
     * раздувается на весь экран. В разметке match_parent: после layout
     * занимаем всю площадь панели и рисуем размытие в её границах.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), 0)
    }

    override fun onDetachedFromWindow() {
        backdrop?.viewTreeObserver?.removeOnPreDrawListener(preDrawListener)
        backdrop = null
        if (Build.VERSION.SDK_INT >= 31) {
            setRenderEffect(null)
        }
        super.onDetachedFromWindow()
    }

    private fun renderSnapshot() {
        val source = backdrop ?: return
        val w = width
        val h = height
        if (w == 0 || h == 0) return

        val bw = max(1, (w / downsample).toInt())
        val bh = max(1, (h / downsample).toInt())
        var bmp = snapshot
        if (bmp == null || bmp.width != bw || bmp.height != bh) {
            bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
            snapshot = bmp
        }

        source.getLocationInWindow(backdropLocation)
        getLocationInWindow(location)
        val c = Canvas(bmp)
        c.scale(1f / downsample, 1f / downsample)
        c.translate(
            -(location[0] - backdropLocation[0]).toFloat(),
            -(location[1] - backdropLocation[1]).toFloat(),
        )
        source.draw(c)
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT < 31) return
        val bmp = snapshot ?: return
        // Снимок меньше вью (downsample) — растягиваем на всю область.
        canvas.drawBitmap(bmp, null, Rect(0, 0, width, height), drawPaint)
    }
}
