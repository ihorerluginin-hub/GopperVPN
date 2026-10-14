package io.nekohasekai.sagernet.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import io.nekohasekai.sagernet.R

open class ToolbarFragment : Fragment {

    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    lateinit var toolbar: Toolbar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar = view.findViewById(R.id.toolbar)
        // Гоппер VPN: вместо бургера с выдвижным ящиком — шестерёнка.
        // Ящик удалён целиком, все его пункты живут в main_gear_menu.
        toolbar.setNavigationIcon(R.drawable.ic_action_settings)
        toolbar.setNavigationOnClickListener { anchor ->
            val activity = activity as? MainActivity ?: return@setNavigationOnClickListener
            PopupMenu(anchor.context, anchor).apply {
                menuInflater.inflate(R.menu.main_gear_menu, menu)
                activity.refreshGearMenu(menu)
                setOnMenuItemClickListener { item ->
                    activity.displayFragmentWithId(item.itemId)
                    true
                }
            }.show()
        }
    }

    open fun onKeyDown(ketCode: Int, event: KeyEvent) = false
    open fun onBackPressed(): Boolean = false
}
