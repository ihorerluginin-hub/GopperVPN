package io.nekohasekai.sagernet.widget

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import androidx.core.graphics.ColorUtils
import io.nekohasekai.sagernet.R
import kotlin.math.min

/**
 * Гоппер VPN: круглая кнопка подключения.
 *
 * ## Почему не FrameLayout
 *
 * Кнопка была голым FrameLayout 190 × 190dp с овалом в фоне. Выглядела
 * круглым, но оставалась квадратом: касание в углу попадало в кнопку,
 * тень от android:elevation шла по прямоугольному контуру, отклик на
 * нажатие был прямоугольным. Здесь всё три закрыто:
 *
 *  - [dispatchTouchEvent] и [onTouchEvent] отбрасывают события вне
 *    вписанной окружности;
 *  - [ViewOutlineProvider] задаёт контур-окружность, тень круглая;
 *  - фронт — [RippleDrawable] с круглой маской, отклик круглый.
 *
 * ## Анимация подключения
 *
 * Луч идёт по кругу, на полном обороте VPN подключается, а всё
 * пространство, которое луч прошёл, перекрашивается. Рисуется
 * прямо здесь, в [onDraw]: фон-овальный GradientDrawable частичную дугу
 * показать не может, а менять фон на ходу — значит пересоздавать его
 * каждый кадр.
 *
 * Дуга стартует сверху (-90°) и идёт по часовой. Впереди — короткий
 * более яркий отрезок, это и есть сам луч; за ним тянется закрашенный
 * сектор.
 *
 * Заливка сектора идёт от центра ко всей площади кнопки, а не по
 * кольцу: так «всё пространство после луча перекрашивается» читается
 * буквально.
 *
 * Пока идёт обход, кнопка залита нейтральным тёмным — иначе поверх
 * красного фона зелёный сектор выглядит грязным. Окраска возвращается
 * штатная, когда обход закончен.
 *
 * Постоянной пульсации на время подключения нет: по запросу осталась
 * только реакция на действия.
 */
class CircularConnectButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rippleMask = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    private val sweepBounds = RectF()

    private val idlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val sectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    /** Насколько круг уже закрашен лучом: 0 — ничего, 1 — полный оборот. */
    private var sweep = 0f

    private var sweepAnimator: ValueAnimator? = null

    /** Цвет, в который перекрашивается пространство после луча. */
    private val sweepColor = ContextCompatColor(R.color.gopper_500)

    /**
     * Нейтральная заливка на время обхода. Берётся тёмный оттенок того
     * же зелёного, а не серый: серый рядом с зелёным сектором даёт
     * грязный стык.
     */
    private val sweepIdle = ColorUtils.blendARGB(Color.BLACK, sweepColor, 0.18f)

    init {
        // Без этого ViewGroup не зовёт onDraw вовсе.
        setWillNotDraw(false)

        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val r = view.radius()
                val cx = view.width / 2f
                val cy = view.height / 2f
                // Пять аргументов, а не шесть: вариант с отдельными rx и
                // ry появился только в API 30, а minSdk в приложении 21.
                outline.setRoundRect(
                    (cx - r).toInt(),
                    (cy - r).toInt(),
                    (cx + r).toInt(),
                    (cy + r).toInt(),
                    r,
                )
            }
        }
        clipToOutline = true
    }

    private fun ContextCompatColor(res: Int): Int =
        androidx.core.content.ContextCompat.getColor(context, res)

    /**
     * Радиус, внутри которого касание считается попаданием в кнопку.
     * От меньшей из сторон: овал занимает квадрат по короткой стороне,
     * длинная выходит за края и в расчёт не идёт.
     */
    private fun View.radius() = minOf(width, height) / 2f

    private fun View.cx() = width / 2f

    private fun View.cy() = height / 2f

    private fun isHit(x: Float, y: Float): Boolean {
        val dx = x - cx()
        val dy = y - cy()
        val r = radius()
        return dx * dx + dy * dy <= r * r
    }

    /**
     * Фон ставится снаружи, из кода: заливка зависит от состояния
     * подключения. После каждой смены пересобирается круглый отклик.
     */
    override fun setBackgroundDrawable(background: android.graphics.drawable.Drawable?) {
        super.setBackgroundDrawable(background)
        if (background is GradientDrawable) {
            rippleMask.setBounds(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
            foreground = RippleDrawable(ColorStateList.valueOf(highlightColor()), null, rippleMask)
        }
    }

    private fun highlightColor(): Int {
        val typed = TypedValue()
        context.theme.resolveAttribute(android.R.attr.colorControlHighlight, typed, true)
        return typed.data
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            rippleMask.setBounds(0, 0, w, h)
            val r = radius()
            val cx = cx()
            val cy = cy()
            sweepBounds.set(cx - r, cy - r, cx + r, cy + r)
            invalidateOutline()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (sweep <= 0f || sweepBounds.isEmpty) return

        val r = radius()
        val cx = cx()
        val cy = cy()

        // Пока идёт обход, фон нейтральный: зелёный сектор поверх
        // красного фона читается грязью.
        idlePaint.color = sweepIdle
        canvas.drawCircle(cx, cy, r, idlePaint)

        val done = 360f * sweep

        // Всё, что луч прошёл, закрашено.
        sectorPaint.color = sweepColor
        canvas.drawArc(sweepBounds, -90f, done, true, sectorPaint)

        // Луч — короткий более яркий отрезок на переднем крае.
        //
        // Раньше поверх него рисовалась ещё и тонкая линия от центра к
        // краю, и на переднем крае оказывалось два световых элемента
        // сразу: двойная вспышка вместо одного луча. Линия убрана,
        // осталась одна дуга.
        val headDegrees = HEAD_DEGREES
        if (done >= headDegrees) {
            headPaint.strokeWidth = r * 0.18f
            headPaint.color = ColorUtils.blendARGB(sweepColor, Color.WHITE, 0.65f)
            canvas.drawArc(sweepBounds, -90f + done - headDegrees, headDegrees, true, headPaint)
        }
    }

    /**
     * Пускает луч по кругу. [onDone] вызывается на полном обороте —
     * там и запускается подключение.
     */
    fun startSweep(durationMs: Long, onDone: () -> Unit) {
        sweepAnimator?.cancel()
        sweepAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            // Линейно: луч должен идти равномерно, PathInterpolator тут
            // разгоняет начало и замедляет конец, и обход перестаёт
            // читаться как круговое движение.
            interpolator = null
            addUpdateListener {
                sweep = it.animatedValue as Float
                invalidate()
            }
            doOnEnd {
                sweep = 0f
                invalidate()
                onDone()
            }
            start()
        }
    }

    fun cancelSweep() {
        sweepAnimator?.cancel()
        sweepAnimator = null
        sweep = 0f
        invalidate()
    }

    /**
     * Короткий хлопок на смену состояния: кнопка сжимается и
     * расправляется. Идёт в паре с плавной сменой цвета, которую
     * ведёт ConfigurationFragment.
     */
    fun pop() {
        animate().cancel()
        ObjectAnimator.ofPropertyValuesHolder(
            this,
            android.animation.PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f, 1f),
        ).apply {
            duration = 300L
            interpolator = EASE
            start()
        }
    }

    override fun onDetachedFromWindow() {
        // Анимации не должны жить дальше фрагмента.
        sweepAnimator?.cancel()
        animate().cancel()
        super.onDetachedFromWindow()
    }

    private fun ValueAnimator.doOnEnd(block: () -> Unit) {
        addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    companion object {
        /** Мягкий разгон и торможение, чтобы не было линейной растяжки. */
        val EASE = PathInterpolator(0.4f, 0f, 0.2f, 1f)

        /** Длина светового отрезка на переднем крае, в градусах. */
        private const val HEAD_DEGREES = 18f
    }
}