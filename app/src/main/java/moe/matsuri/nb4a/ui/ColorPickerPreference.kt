package moe.matsuri.nb4a.ui

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.res.ResourcesCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.setPadding
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.ktx.getColorAttr
import kotlin.math.roundToInt

class ColorPickerPreference
@JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = TypedArrayUtils.getAttr(
        context,
        androidx.preference.R.attr.editTextPreferenceStyle,
        android.R.attr.editTextPreferenceStyle
    )
) : Preference(
    context, attrs, defStyle
) {

    var inited = false

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val widgetFrame = holder.findViewById(android.R.id.widget_frame) as LinearLayout

        if (!inited) {
            inited = true

            widgetFrame.addView(
                getNekoImageViewAtColor(
                    context.getColorAttr(R.attr.colorPrimary),
                    48,
                    0
                )
            )
            widgetFrame.visibility = View.VISIBLE
        }
    }

    fun getNekoImageViewAtColor(color: Int, sizeDp: Int, paddingDp: Int): ImageView {
        // dp to pixel
        val factor = context.resources.displayMetrics.density
        val size = (sizeDp * factor).roundToInt()
        val paddingSize = (paddingDp * factor).roundToInt()

        return ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(size, size)
            setPadding(paddingSize)
            setImageDrawable(getNekoAtColor(resources, color))
        }
    }

    fun getNekoAtColor(res: Resources, color: Int): Drawable {
        val neko = ResourcesCompat.getDrawable(
            res,
            R.drawable.ic_baseline_fiber_manual_record_24,
            null
        )!!
        DrawableCompat.setTint(neko.mutate(), color)
        return neko
    }

    override fun onClick() {
        super.onClick()

        lateinit var dialog: AlertDialog

        val grid = GridLayout(context).apply {
            columnCount = 4

            val colors = context.resources.getIntArray(R.array.material_colors)
            val selectedTheme = DataStore.appTheme
            val factor = context.resources.displayMetrics.density
            var i = 0

            for (color in colors) {
                i++ //Theme.kt

                val themeId = i
                val view = getNekoImageViewAtColor(color, 64, 8).apply {
                    setOnClickListener {
                        persistInt(themeId)
                        dialog.dismiss()
                        callChangeListener(themeId)
                    }
                    // Рамка вокруг текущей темы: без неё в сетке из
                    // 22 кружков не видно, какая тема выбрана сейчас.
                    if (themeId == selectedTheme) {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setStroke((3 * factor).roundToInt(), context.getColorAttr(R.attr.colorAccent))
                        }
                    }
                }
                addView(view)
            }

        }

        dialog = MaterialAlertDialogBuilder(context).setTitle(title)
            .setView(LinearLayout(context).apply {
                gravity = Gravity.CENTER
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
                addView(grid)
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show()

        applyGlassWindow(dialog)
    }

    /**
     * Liquid Glass: окно пикера становится стеклянным — размытие
     * содержимого под ним и полупрозрачная панель вместо непрозрачной
     * карточки. Только Android 12+ и только если система реально
     * включает кросс-оконное размытие; при любой неудаче диалог
     * остаётся обычным непрозрачным — это чистая косметика.
     */
    private fun applyGlassWindow(dialog: AlertDialog) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val window = dialog.window ?: return
        try {
            if (!window.windowManager.isCrossWindowBlurEnabled) return

            val factor = context.resources.displayMetrics.density
            // Селекторы с ?attr надуваются только с темой: getColorStateList
            // с темой контекста, затем defaultColor.
            val glass = androidx.core.content.ContextCompat
                .getColorStateList(context, R.color.glass_dialog)!!.defaultColor
            val edge = androidx.core.content.ContextCompat
                .getColorStateList(context, R.color.glass_edge)!!.defaultColor
            window.setBackgroundDrawable(GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 22f * factor
                setColor(glass)
                setStroke((1 * factor).roundToInt(), edge)
            })
            window.setBackgroundBlurRadius(30)
            window.setDimAmount(0.25f)
        } catch (_: Exception) {
            // Размытие не получилось — остаётся штатный непрозрачный диалог.
        }
    }
}
