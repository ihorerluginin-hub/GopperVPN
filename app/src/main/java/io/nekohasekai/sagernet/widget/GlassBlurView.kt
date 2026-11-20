package io.nekohasekai.sagernet.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import kotlin.math.max

/**
 * Liquid Glass: размывает содержимое окна позади себя — то, что видно
 * сквозь стеклянную поверхность. Именно это делает «стекло» стеклом,
 * как на iPhone: под панелью скроллится список, и его очертания
 * просвечивают размытыми.
 *
 * Как работает: корневой view окна рисуется в уменьшенную копию
 * (downsample ×4 — cheaper), копия блюрится [RenderEffect] и рисуется
 * в этом view. Всё это — Android 12+ ([RenderNode], [RenderEffect]);
 * на более старых версиях view ничего не рисует, и стеклянная панель
 * остаётся просто полупрозрачной.
 *
 * Перерисовка подписана на preDraw корневого дерева — каждый кадр
 * содержимого обновляет размытие. Панель трафика при прокрутке
 * прячется целиком, так что размытие активно в основном в покое.
 */
class GlassBlurView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Радиус размытия: применяется к уменьшенной копии, поэтому физически в 4 раза больше. */
    var blurRadiusDp = 14f

    /** Во сколько раз уменьшается снимок перед размытием. */
    var downsample = 4f

    private val drawPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    /** Лениво: класс RenderNode отсутствует на API < 29, поле должно
     *  инициализироваться только после проверки версии. */
    private val node by lazy { RenderNode("glassBlur") }

    private var snapshot: Bitmap? = null

    /** true, пока корень рисуется в снимок: собственную отрисовку
     *  в этот момент пропускаем, иначе — бесконечная рекурсия. */
    private var snapshotting = false

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        // Каждый кадр содержимого = новый снимок. Панель прячется
        // целиком при прокрутке, так что активна в основном в покое.
        if (isShown && width > 0 && height > 0) {
            postInvalidateOnAnimation()
        }
        true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (Build.VERSION.SDK_INT >= 31) {
            viewTreeObserver.addOnPreDrawListener(preDrawListener)
        }
    }

    override fun onDetachedFromWindow() {
        if (Build.VERSION.SDK_INT >= 31) {
            viewTreeObserver.removeOnPreDrawListener(preDrawListener)
        }
        super.onDetachedFromWindow()
    }

    override fun draw(canvas: Canvas) {
        // Снимок корня рисует всё дерево, включая этот view; пропускаем
        // себя, чтобы размытие не рисовало само себя (рекурсия).
        if (snapshotting) return
        super.draw(canvas)
    }

    override fun onDraw(canvas: Canvas) {
        if (Build.VERSION.SDK_INT < 31) return
        val root = rootView ?: return
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

        rootLocationAndMine.let { (rootLoc, myLoc) ->
            root.getLocationInWindow(rootLoc)
            getLocationInWindow(myLoc)
            snapshotting = true
            try {
                val c = Canvas(bmp)
                c.scale(1f / downsample, 1f / downsample)
                c.translate(-(myLoc[0] - rootLoc[0]).toFloat(), -(myLoc[1] - rootLoc[1]).toFloat())
                root.draw(c)
            } finally {
                snapshotting = false
            }
        }

        // Размытие через RenderNode: лимит радиуса RenderEffect — 25px,
        // поэтому блюрится уменьшенная копия (физический радиус ×4).
        node.setPosition(0, 0, bw, bh)
        val rc = node.beginRecording()
        rc.drawBitmap(bmp, 0f, 0f, drawPaint)
        node.endRecording()
        node.setRenderEffect(
            RenderEffect.createBlurEffect(
                max(1f, blurRadiusDp * context.resources.displayMetrics.density / downsample),
                max(1f, blurRadiusDp * context.resources.displayMetrics.density / downsample),
                Shader.TileMode.CLAMP,
            ),
        )
        canvas.drawRenderNode(node)
    }

    /** Пару массивов для координат — заводятся один раз, чтобы не
     *  аллоцировать на каждом кадре. */
    private val rootLocationAndMine = Pair(IntArray(2), IntArray(2))
}
