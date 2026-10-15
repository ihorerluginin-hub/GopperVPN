package io.nekohasekai.sagernet.widget

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout

/**
 * Гоппер VPN: круглая кнопка подключения.
 *
 * Раньше это был голый FrameLayout 190 × 190dp с овалом, нарисованным в
 * фоне, и `?attr/selectableItemBackground` во фронте. Выглядело круглым,
 * но по факту оставалось квадратом:
 *
 *  - касание в углу квадрата попадало в кнопку, хотя мимо овала;
 *  - android:elevation рисовал тень по контуру вьюхи, то есть
 *    прямоугольную, и при взгляде сбоку кнопка читалась как плитка;
 *  - отклик на нажатие был прямоугольным, а не круглым.
 *
 * Здесь всё три закрыто:
 *
 *  - [dispatchTouchEvent] и [onTouchEvent] отбрасывают события вне
 *    вписанной окружности, поэтому в углу нажатия нет;
 *  - [ViewOutlineProvider] задаёт контур-окружность, и тень от
 *    elevation становится круглой;
 *  - фронт — [RippleDrawable] с круглой маской, отклик круглый.
 *
 * Маска берётся из формы текущего фона, а не рисуется отдельно: фон и
 * так GradientDrawable.OVAL, так что отклик всегда сходится с заливкой.
 *
 * Про анимацию: постоянной пульсации на время подключения здесь нет,
 * по запросу осталась только реакция на смену состояния — [pop].
 */
class CircularConnectButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val rippleMask = GradientDrawable().apply { shape = GradientDrawable.OVAL }

    init {
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = view.radius()
                val cx = view.width / 2f
                val cy = view.height / 2f
                // Пять аргументов, а не шесть: вариант с отдельными rx и ry
                // появился только в API 30, а minSdk в приложении 21.
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

    /**
     * Радиус, внутри которого касание считается попаданием в кнопку.
     * Считается от меньшей из сторон: овал занимает квадрат по короткой
     * стороне, длинная выходит за края и в расчёт не идёт.
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
     * подключения, а меняется она по кадрам анимации перехода. Здесь
     * после каждой смены фона пересобирается круглый отклик.
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
            invalidateOutline()
        }
    }

    /**
     * Точка вне окружности — не попадание в кнопку. Событие уходит дальше
     * по дереву необработанным: под кнопкой ничего нажиматься не должно,
     * но и кнопка его не съест.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !isHit(event.x, event.y)) {
            return false
        }
        return super.dispatchTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && !isHit(event.x, event.y)) {
            return false
        }
        return super.onTouchEvent(event)
    }

    /**
     * Короткий хлопок на смену состояния: кнопка сжимается и
     * расправляется. Идёт в паре с плавной сменой цвета, которую
     * ведёт ConfigurationFragment, — вместе они и дают переход.
     */
    fun pop() {
        animate().cancel()
        ObjectAnimator.ofPropertyValuesHolder(
            this,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.06f, 1f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.06f, 1f),
        ).apply {
            duration = 300L
            interpolator = EASE
            start()
        }
    }

    override fun onDetachedFromWindow() {
        // Анимация не должна жить дальше фрагмента.
        animate().cancel()
        super.onDetachedFromWindow()
    }

    companion object {
        /** Мягкий разгон и торможение, чтобы не было линейной растяжки. */
        val EASE = PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}