package io.nekohasekai.sagernet.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.SystemClock
import android.provider.OpenableColumns
import android.text.SpannableStringBuilder
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.format.Formatter
import android.text.style.ForegroundColorSpan
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.size
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceDataStore
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.nekohasekai.sagernet.GroupOrder
import io.nekohasekai.sagernet.GroupType
import io.nekohasekai.sagernet.Key
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.aidl.TrafficData
import io.nekohasekai.sagernet.bg.BaseService
import io.nekohasekai.sagernet.bg.proto.UrlTest
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.ProxyEntity
import io.nekohasekai.sagernet.database.ProxyGroup
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.preference.OnPreferenceDataStoreChangeListener
import io.nekohasekai.sagernet.databinding.LayoutProfileListBinding
import io.nekohasekai.sagernet.databinding.LayoutProgressListBinding
import io.nekohasekai.sagernet.fmt.AbstractBean
import io.nekohasekai.sagernet.fmt.toUniversalLink
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.group.RawUpdater
import io.nekohasekai.sagernet.ktx.FixedLinearLayoutManager
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.SubscriptionFoundException
import io.nekohasekai.sagernet.ktx.alert
import io.nekohasekai.sagernet.ktx.app
import io.nekohasekai.sagernet.ktx.dp2px
import io.nekohasekai.sagernet.ktx.getColorAttr
import io.nekohasekai.sagernet.ktx.getColour
import io.nekohasekai.sagernet.ktx.isIpAddress
import io.nekohasekai.sagernet.ktx.onMainDispatcher
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.ktx.runOnLifecycleDispatcher
import io.nekohasekai.sagernet.ktx.runOnMainDispatcher
import io.nekohasekai.sagernet.ktx.scrollTo
import io.nekohasekai.sagernet.ktx.showAllowingStateLoss
import io.nekohasekai.sagernet.ktx.snackbar
import io.nekohasekai.sagernet.ktx.startFilesForResult
import io.nekohasekai.sagernet.ktx.tryToShow
import io.nekohasekai.sagernet.plugin.PluginManager
import io.nekohasekai.sagernet.ui.profile.ChainSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HttpSettingsActivity
import io.nekohasekai.sagernet.ui.profile.HysteriaSettingsActivity
import io.nekohasekai.sagernet.ui.profile.MieruSettingsActivity
import io.nekohasekai.sagernet.ui.profile.NaiveSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SSHSettingsActivity
import io.nekohasekai.sagernet.ui.profile.ShadowsocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.SocksSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanGoSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TrojanSettingsActivity
import io.nekohasekai.sagernet.ui.profile.TuicSettingsActivity
import io.nekohasekai.sagernet.ui.profile.VMessSettingsActivity
import io.nekohasekai.sagernet.ui.profile.WireGuardSettingsActivity
import io.nekohasekai.sagernet.widget.QRCodeDialog
import io.nekohasekai.sagernet.widget.UndoSnackbarManager
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import moe.matsuri.nb4a.Protocols
import moe.matsuri.nb4a.Protocols.getProtocolColor
import moe.matsuri.nb4a.proxy.anytls.AnyTLSSettingsActivity
import moe.matsuri.nb4a.proxy.config.ConfigSettingActivity
import moe.matsuri.nb4a.proxy.shadowtls.ShadowTLSSettingsActivity
import moe.matsuri.nb4a.ui.ConnectionTestNotification
import okhttp3.internal.closeQuietly
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipInputStream

class ConfigurationFragment @JvmOverloads constructor(
    val select: Boolean = false, val selectedItem: ProxyEntity? = null, val titleRes: Int = 0
) : ToolbarFragment(R.layout.layout_group_list),
    PopupMenu.OnMenuItemClickListener,
    Toolbar.OnMenuItemClickListener,
    OnPreferenceDataStoreChangeListener {

    interface SelectCallback {
        fun returnProfile(profileId: Long)
    }

    /**
     * Гоппер VPN: серверы одной подписки — их порядок и сами объекты.
     *
     * Объявлено здесь, а не внутри AccordionAdapter: вложенные в класс
     * с `inner` классы Kotlin запрещает, а заголовку аккордеона тоже
     * нужно знать число серверов подписки.
     */
    class GroupData {
        val ids = mutableListOf<Long>()
        val map = HashMap<Long, ProxyEntity>()
    }

    /** Строка списка аккордеона: заголовок подписки (profileId == null) или сервер. */
    class Row(val group: ProxyGroup, val profileId: Long?)

    // Гоппер VPN: вертикальный аккордеон подписок. Один RecyclerView на
    // весь экран: в нём чередуются заголовки подписок и их серверы.
    // Раньше здесь были вкладки и ViewPager2 с отдельным фрагментом на
    // каждую подписку — они разбирают, поэтому и GroupFragment, и
    // GroupPagerAdapter больше не нужны.
    lateinit var adapter: AccordionAdapter
    lateinit var accordion: RecyclerView
    lateinit var undoManager: UndoSnackbarManager<ProxyEntity>

    val accordionLayoutManager by lazy { FixedLinearLayoutManager(accordion) }

    val alwaysShowAddress by lazy { DataStore.alwaysShowAddress }

    /**
     * Гоппер VPN: подписка, которая сейчас раскрыта.
     *
     * Раскрыта ровно одна — та, на которую указывает
     * [DataStore.selectedGroup]. Остальные свёрнуты до названия.
     */
    fun expandedGroupOrNull(): ProxyGroup? =
        if (::adapter.isInitialized) {
            adapter.groupList.firstOrNull { it.id == DataStore.selectedGroup }
        } else null

    fun expandedGroupIdOrZero(): Long = DataStore.selectedGroup

    @SuppressLint("DetachAndAttachSameFragment")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            parentFragmentManager.beginTransaction()
                .setReorderingAllowed(false)
                .detach(this)
                .attach(this)
                .commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!select) {
            toolbar.inflateMenu(R.menu.add_profile_menu)
            toolbar.setOnMenuItemClickListener(this)
        } else {
            toolbar.setTitle(titleRes)
            toolbar.setNavigationIcon(R.drawable.ic_navigation_close)
            toolbar.setNavigationOnClickListener {
                requireActivity().finish()
            }
        }

        // Гоппер VPN: поиск по серверам убран из панели, вместе с ним
        // исчез весь обвязывающий его код — слушатель, отмена и колбэки.

        // Гоппер VPN: список аккордеона. В нём и заголовки подписок, и их
        // серверы, поэтому сворачивание верхних кнопок вешается на него
        // напрямую — отдельных списков больше нет.
        accordion = view.findViewById(R.id.accordion_list)
        accordion.layoutManager = accordionLayoutManager
        adapter = AccordionAdapter()
        accordion.adapter = adapter
        accordion.setItemViewCacheSize(20)
        attachCollapseOnScroll(accordion)
        ProfileManager.addListener(adapter)
        GroupManager.addListener(adapter)

        // Гоппер VPN: круглая кнопка подключения сверху. Это FrameLayout,
        // а не MaterialButton: объёмный вид требует градиентной заливки.
        val connectButton = view.findViewById<View>(R.id.connect_button)
        connectButton.isVisible = !select
        if (!select) {
            connectButton.setOnClickListener { (activity as? MainActivity)?.toggleService() }
        }

        // Ряд кнопок под кнопкой подключения: обновить все, пинг всех,
        // самый быстрый. Отдельного «Пинга» для одного сервера больше нет.
        val quickTestBar = view.findViewById<View>(R.id.quick_test_bar)
        if (select) {
            quickTestBar.isVisible = false
        } else {
            view.findViewById<MaterialButton>(R.id.refresh_all_button).setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                    .setMessage(R.string.update_all_subscription)
                    .setPositiveButton(R.string.yes) { _, _ ->
                        runOnDefaultDispatcher {
                            SagerDatabase.groupDao.allGroups()
                                .filter { it.type == GroupType.SUBSCRIPTION }
                                .forEach { GroupUpdater.startUpdate(it, true) }
                        }
                    }
                    .setNegativeButton(R.string.no, null)
                    .show()
            }
            view.findViewById<MaterialButton>(R.id.ping_all_button).setOnClickListener {
                pingAllServers()
            }
            view.findViewById<MaterialButton>(R.id.fastest_button).setOnClickListener {
                selectFastestServer()
            }
        }

        // Гоппер VPN: перетаскивание серверов внутри подписки. Заголовки
        // не двигаются, и сервер нельзя перетащить в чужую подписку —
        // иначе перемешаются их порядки. В режиме выбора профиля
        // перетаскивание не нужно вовсе.
        if (!select) {
            undoManager = UndoSnackbarManager(activity as MainActivity, adapter)

            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, ItemTouchHelper.START
            ) {
                override fun getSwipeDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ): Int = 0

                override fun getDragDirs(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) = if (isEnabled) super.getDragDirs(recyclerView, viewHolder) else 0

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                }

                override fun canDropOver(
                    recyclerView: RecyclerView,
                    current: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder,
                ): Boolean = adapter.canMove(current.bindingAdapterPosition, target.bindingAdapterPosition)

                override fun onMove(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder,
                ): Boolean {
                    adapter.moveProfile(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
                    return true
                }

                override fun clearView(
                    recyclerView: RecyclerView,
                    viewHolder: RecyclerView.ViewHolder,
                ) {
                    super.clearView(recyclerView, viewHolder)
                    adapter.commitMove()
                }
            }).attachToRecyclerView(accordion)
        }

        // Гоппер VPN: состояние подключения живёт в MainActivity, поэтому
        // подписываемся на его statusListener и отписываемся в onDestroyView —
        // иначе фрагмент остался бы висеть в памяти.
        (activity as? MainActivity)?.statusListener = { updateConnectionUi() }
        updateConnectionUi()

        // порядок серверов в меню «⋮» относится к раскрытой подписке,
        // поэтому пересчитываем его при каждой смене подписки
        adapter.onGroupChanged = {
            resetCollapse()
            checkOrderMenu()
        }

        toolbar.setOnClickListener {
            val index = adapter.positionOfProfile(selectedItem?.id ?: DataStore.selectedProxy)
            if (index >= 0) {
                accordion.scrollToPosition(index)
            } else {
                accordion.scrollToPosition(0)
            }
        }

        DataStore.profileCacheStore.registerChangeListener(this)
    }

    /** Гоппер VPN: перетаскивание доступно только когда сервис не работает. */
    private val isEnabled: Boolean
        get() = DataStore.serviceState.let { it.canStop || it == BaseService.State.Stopped }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        runOnMainDispatcher {
            // editingGroup
            if (key == Key.PROFILE_GROUP) {
                val targetId = DataStore.editingGroup
                if (targetId > 0 && targetId != DataStore.selectedGroup) {
                    DataStore.selectedGroup = targetId
                    if (adapter.groupList.none { it.id == targetId }) {
                        adapter.reload()
                    } else {
                        adapter.expand(targetId)
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)

        if (::adapter.isInitialized) {
            GroupManager.removeListener(adapter)
            ProfileManager.removeListener(adapter)
        }

        (activity as? MainActivity)?.statusListener = null

        detachCollapseOnScroll()

        if (::undoManager.isInitialized) undoManager.flush()

        super.onDestroy()
    }

    override fun onKeyDown(ketCode: Int, event: KeyEvent): Boolean {
        if (::accordion.isInitialized && !accordion.hasFocus()) {
            accordion.requestFocus()
        }
        return super.onKeyDown(ketCode, event)
    }

    private val importFile =
        registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file != null) runOnDefaultDispatcher {
                try {
                    val fileName =
                        requireContext().contentResolver.query(file, null, null, null, null)
                            ?.use { cursor ->
                                cursor.moveToFirst()
                                cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)
                                    .let(cursor::getString)
                            }
                    val proxies = mutableListOf<AbstractBean>()
                    if (fileName != null && fileName.endsWith(".zip")) {
                        // try parse wireguard zip
                        val zip =
                            ZipInputStream(requireContext().contentResolver.openInputStream(file)!!)
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) continue
                            val fileText = zip.bufferedReader().readText()
                            RawUpdater.parseRaw(fileText, entry.name)
                                ?.let { pl -> proxies.addAll(pl) }
                            zip.closeEntry()
                        }
                        zip.closeQuietly()
                    } else {
                        val fileText =
                            requireContext().contentResolver.openInputStream(file)!!.use {
                                it.bufferedReader().readText()
                            }
                        RawUpdater.parseRaw(fileText, fileName ?: "")
                            ?.let { pl -> proxies.addAll(pl) }
                    }
                    if (proxies.isEmpty()) onMainDispatcher {
                        snackbar(getString(R.string.no_proxies_found_in_file)).show()
                    } else import(proxies)
                } catch (e: SubscriptionFoundException) {
                    (requireActivity() as MainActivity).importSubscription(e.link.toUri())
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        snackbar(e.readableMessage).show()
                    }
                }
            }
        }

    private val importQrImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { file ->
            if (file == null) return@registerForActivityResult
            runOnDefaultDispatcher {
                val text = try {
                    QrImageReader.readText(requireContext().applicationContext, file)
                } catch (e: Exception) {
                    Logs.w(e)
                    null
                }
                if (text.isNullOrBlank()) {
                    onMainDispatcher {
                        snackbar(getString(R.string.qr_image_no_code)).show()
                    }
                } else {
                    handleImportText(text)
                }
            }
        }

    /**
     * NekoBox Fast: shared pipeline for "text that contains links" - used by the
     * clipboard import and by the QR-image import.
     */
    private suspend fun handleImportText(text: String) {
        try {
            val proxies = RawUpdater.parseRaw(text)
            if (proxies.isNullOrEmpty()) {
                onMainDispatcher {
                    snackbar(getString(R.string.no_proxies_found_in_clipboard)).show()
                }
            } else {
                import(proxies)
            }
        } catch (e: SubscriptionFoundException) {
            (requireActivity() as MainActivity).importSubscription(e.link.toUri())
        } catch (e: Exception) {
            Logs.w(e)
            onMainDispatcher {
                snackbar(e.readableMessage).show()
            }
        }
    }

    suspend fun import(proxies: List<AbstractBean>) {
        val targetId = DataStore.selectedGroupForImport()
        for (proxy in proxies) {
            ProfileManager.createProfile(targetId, proxy)
        }
        onMainDispatcher {
            DataStore.editingGroup = targetId
            snackbar(
                requireContext().resources.getQuantityString(
                    R.plurals.added, proxies.size, proxies.size
                )
            ).show()
        }

    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_import_qr_image -> {
                importQrImage.launch("image/*")
            }

            R.id.action_import_clipboard -> {
                val text = SagerNet.getClipboardText()
                if (text.isBlank()) {
                    snackbar(getString(R.string.clipboard_empty)).show()
                } else runOnDefaultDispatcher {
                    handleImportText(text)
                }
            }

            R.id.action_import_file -> {
                startFilesForResult(importFile, "*/*")
            }

            R.id.action_new_socks -> {
                startActivity(Intent(requireActivity(), SocksSettingsActivity::class.java))
            }

            R.id.action_new_http -> {
                startActivity(Intent(requireActivity(), HttpSettingsActivity::class.java))
            }

            R.id.action_new_ss -> {
                startActivity(Intent(requireActivity(), ShadowsocksSettingsActivity::class.java))
            }

            R.id.action_new_vmess -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java))
            }

            R.id.action_new_vless -> {
                startActivity(Intent(requireActivity(), VMessSettingsActivity::class.java).apply {
                    putExtra("vless", true)
                })
            }

            R.id.action_new_trojan -> {
                startActivity(Intent(requireActivity(), TrojanSettingsActivity::class.java))
            }

            R.id.action_new_trojan_go -> {
                startActivity(Intent(requireActivity(), TrojanGoSettingsActivity::class.java))
            }

            R.id.action_new_mieru -> {
                startActivity(Intent(requireActivity(), MieruSettingsActivity::class.java))
            }

            R.id.action_new_naive -> {
                startActivity(Intent(requireActivity(), NaiveSettingsActivity::class.java))
            }

            R.id.action_new_hysteria -> {
                startActivity(Intent(requireActivity(), HysteriaSettingsActivity::class.java))
            }

            R.id.action_new_tuic -> {
                startActivity(Intent(requireActivity(), TuicSettingsActivity::class.java))
            }

            R.id.action_new_ssh -> {
                startActivity(Intent(requireActivity(), SSHSettingsActivity::class.java))
            }

            R.id.action_new_wg -> {
                startActivity(Intent(requireActivity(), WireGuardSettingsActivity::class.java))
            }

            R.id.action_new_shadowtls -> {
                startActivity(Intent(requireActivity(), ShadowTLSSettingsActivity::class.java))
            }

            R.id.action_new_anytls -> {
                startActivity(Intent(requireActivity(), AnyTLSSettingsActivity::class.java))
            }

            R.id.action_new_config -> {
                startActivity(Intent(requireActivity(), ConfigSettingActivity::class.java))
            }

            R.id.action_new_chain -> {
                startActivity(Intent(requireActivity(), ChainSettingsActivity::class.java))
            }

            R.id.action_update_subscription -> {
                val group = DataStore.currentGroup()
                if (group.type != GroupType.SUBSCRIPTION) {
                    snackbar(R.string.group_not_subscription).show()
                    Logs.e("onMenuItemClick: Group(${group.displayName()}) is not subscription")
                } else {
                    runOnLifecycleDispatcher {
                        GroupUpdater.startUpdate(group, true)
                    }
                }
            }

            R.id.action_clear_traffic_statistics -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.tx != 0L || profile.rx != 0L) {
                            profile.tx = 0
                            profile.rx = 0
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }

            R.id.action_connection_test_clear_results -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0) {
                            profile.status = 0
                            profile.ping = 0
                            profile.error = null
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        ProfileManager.updateProfile(toClear)
                    }
                }
            }

            R.id.action_connection_test_delete_unavailable -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    if (profiles.isNotEmpty()) for (profile in profiles) {
                        if (profile.status != 0 && profile.status != 1) {
                            toClear.add(profile)
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(R.string.delete_confirm_prompt)
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.removeProfile(profile.groupId, profile.id)
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        }
                    }
                }
            }

            R.id.action_remove_duplicate -> {
                runOnDefaultDispatcher {
                    val profiles = SagerDatabase.proxyDao.getByGroup(DataStore.currentGroupId())
                    val toClear = mutableListOf<ProxyEntity>()
                    val uniqueProxies = LinkedHashSet<Protocols.Deduplication>()
                    for (pf in profiles) {
                        val proxy = Protocols.Deduplication(pf.requireBean(), pf.displayType())
                        if (!uniqueProxies.add(proxy)) {
                            toClear += pf
                        }
                    }
                    if (toClear.isNotEmpty()) {
                        onMainDispatcher {
                            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                                .setMessage(
                                    getString(R.string.delete_confirm_prompt) + "\n" +
                                            toClear.mapIndexedNotNull { index, proxyEntity ->
                                                if (index < 20) {
                                                    proxyEntity.displayName()
                                                } else if (index == 20) {
                                                    "......"
                                                } else {
                                                    null
                                                }
                                            }.joinToString("\n")
                                )
                                .setPositiveButton(R.string.yes) { _, _ ->
                                    for (profile in toClear) {
                                        adapter.removeProfile(profile.groupId, profile.id)
                                    }
                                    runOnDefaultDispatcher {
                                        for (profile in toClear) {
                                            ProfileManager.deleteProfile2(
                                                profile.groupId, profile.id
                                            )
                                        }
                                    }
                                }
                                .setNegativeButton(R.string.no, null)
                                .show()
                        }
                    }
                }
            }

            R.id.action_ping_all -> {
                pingAllServers()
            }

            R.id.action_fastest -> {
                selectFastestServer()
            }

            R.id.action_connection_tcp_ping -> {
                pingTest(false)
            }

            R.id.action_connection_url_test -> {
                urlTest()
            }
        }
        return true
    }

    inner class TestDialog {
        val binding = LayoutProgressListBinding.inflate(layoutInflater)
        val builder = MaterialAlertDialogBuilder(requireContext()).setView(binding.root)
            .setPositiveButton(R.string.minimize) { _, _ ->
                minimize()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ ->
                cancel()
            }
            .setCancelable(false)

        lateinit var cancel: () -> Unit
        lateinit var minimize: () -> Unit

        val dialogStatus = AtomicInteger(0) // 1: hidden 2: cancelled
        var notification: ConnectionTestNotification? = null

        val results: MutableSet<ProxyEntity> = ConcurrentHashMap.newKeySet()
        var proxyN = 0
        val finishedN = AtomicInteger(0)

        fun update(profile: ProxyEntity) {
            if (dialogStatus.get() != 2) {
                results.add(profile)
            }
            runOnMainDispatcher {
                val context = context ?: return@runOnMainDispatcher
                val progress = finishedN.addAndGet(1)
                val status = dialogStatus.get()
                notification?.updateNotification(
                    progress,
                    proxyN,
                    progress >= proxyN || status == 2
                )
                if (status >= 1) return@runOnMainDispatcher
                if (!isAdded) return@runOnMainDispatcher

                // refresh dialog

                var profileStatusText: String? = null
                var profileStatusColor = 0

                when (profile.status) {
                    -1 -> {
                        profileStatusText = profile.error
                        profileStatusColor = context.getColorAttr(android.R.attr.textColorSecondary)
                    }

                    0 -> {
                        profileStatusText = getString(R.string.connection_test_testing)
                        profileStatusColor = context.getColorAttr(android.R.attr.textColorSecondary)
                    }

                    1 -> {
                        profileStatusText = getString(R.string.available, profile.ping)
                        profileStatusColor = context.getColour(R.color.material_green_500)
                    }

                    2 -> {
                        profileStatusText = profile.error
                        profileStatusColor = context.getColour(R.color.material_red_500)
                    }

                    3 -> {
                        val err = profile.error ?: ""
                        val msg = Protocols.genFriendlyMsg(err)
                        profileStatusText = if (msg != err) msg else getString(R.string.unavailable)
                        profileStatusColor = context.getColour(R.color.material_red_500)
                    }
                }

                val text = SpannableStringBuilder().apply {
                    append("\n" + profile.displayName())
                    append("\n")
                    append(
                        profile.displayType(),
                        ForegroundColorSpan(context.getProtocolColor(profile.type)),
                        SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append(" ")
                    append(
                        profileStatusText,
                        ForegroundColorSpan(profileStatusColor),
                        SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    append("\n")
                }

                binding.nowTesting.text = text
                binding.progress.text = "$progress / $proxyN"
            }
        }

    }

    @OptIn(DelicateCoroutinesApi::class)
    @Suppress("EXPERIMENTAL_API_USAGE")
    fun pingTest(icmpPing: Boolean) {
        if (DataStore.runningTest) return else DataStore.runningTest = true
        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        val group = DataStore.currentGroup()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getByGroup(group.id).filter {
                if (icmpPing) {
                    if (it.requireBean().canICMPing()) {
                        return@filter true
                    }
                } else {
                    if (it.requireBean().canTCPing()) {
                        return@filter true
                    }
                }
                return@filter false
            }
            test.proxyN = profilesList.size
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent) {
                testJobs.add(launch(Dispatchers.IO) {
                    while (isActive) {
                        val profile = profiles.poll() ?: break

                        profile.status = 0
                        var address = profile.requireBean().serverAddress
                        if (!address.isIpAddress()) {
                            try {
                                SagerNet.underlyingNetwork!!.getAllByName(address).apply {
                                    if (isNotEmpty()) {
                                        address = this[0].hostAddress
                                    }
                                }
                            } catch (ignored: UnknownHostException) {
                            }
                        }
                        if (!isActive) break
                        if (!address.isIpAddress()) {
                            profile.status = 2
                            profile.error = app.getString(R.string.connection_test_domain_not_found)
                            test.update(profile)
                            continue
                        }
                        try {
                            if (icmpPing) {
                                // removed
                            } else {
                                val socket =
                                    SagerNet.underlyingNetwork?.socketFactory?.createSocket()
                                        ?: Socket()
                                try {
                                    socket.soTimeout = 3000
                                    socket.bind(InetSocketAddress(0))
                                    val start = SystemClock.elapsedRealtime()
                                    socket.connect(
                                        InetSocketAddress(
                                            address, profile.requireBean().serverPort
                                        ), 3000
                                    )
                                    if (!isActive) break
                                    profile.status = 1
                                    profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
                                    test.update(profile)
                                } finally {
                                    socket.closeQuietly()
                                }
                            }
                        } catch (e: Exception) {
                            if (!isActive) break
                            val message = e.readableMessage

                            if (icmpPing) {
                                profile.status = 2
                                profile.error = getString(R.string.connection_test_unreachable)
                            } else {
                                profile.status = 2
                                when {
                                    !message.contains("failed:") -> profile.error =
                                        getString(R.string.connection_test_timeout)

                                    else -> when {
                                        message.contains("ECONNREFUSED") -> {
                                            profile.error =
                                                getString(R.string.connection_test_refused)
                                        }

                                        message.contains("ENETUNREACH") -> {
                                            profile.error =
                                                getString(R.string.connection_test_unreachable)
                                        }

                                        else -> {
                                            profile.status = 3
                                            profile.error = message
                                        }
                                    }
                                }
                            }
                            test.update(profile)
                        }
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }
        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                GroupManager.postReload(DataStore.currentGroupId())
                DataStore.runningTest = false
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[${group.displayName()}] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun urlTest() {
        if (DataStore.runningTest) return else DataStore.runningTest = true
        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        val group = DataStore.currentGroup()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getByGroup(group.id)
            test.proxyN = profilesList.size
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent) {
                testJobs.add(launch(Dispatchers.IO) {
                    val urlTest = UrlTest() // note: this is NOT in bg process
                    while (isActive) {
                        val profile = profiles.poll() ?: break
                        profile.status = 0

                        try {
                            val result = urlTest.doTest(profile)
                            profile.status = 1
                            profile.ping = result
                        } catch (e: PluginManager.PluginNotFoundException) {
                            profile.status = 2
                            profile.error = e.readableMessage
                        } catch (e: Exception) {
                            profile.status = 3
                            profile.error = e.readableMessage
                        }

                        test.update(profile)
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }
        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                GroupManager.postReload(DataStore.currentGroupId())
                DataStore.runningTest = false
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[${group.displayName()}] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    /**
     * NekoBox Fast: runs a URL delay test against **every** profile of **every** group
     * (i.e. every node of every subscription), then invokes [onFinished] on a background
     * dispatcher with the tested entities.
     */
    @OptIn(DelicateCoroutinesApi::class)
    private fun testAllProfiles(label: String, onFinished: suspend (List<ProxyEntity>) -> Unit) {
        if (DataStore.runningTest) {
            runOnMainDispatcher { snackbar(R.string.connection_test_running).show() }
            return
        }
        DataStore.runningTest = true

        val test = TestDialog()
        val dialog = test.builder.show()
        val testJobs = mutableListOf<Job>()
        var tested = listOf<ProxyEntity>()

        val mainJob = runOnDefaultDispatcher {
            val profilesList = SagerDatabase.proxyDao.getAll()
            tested = profilesList
            test.proxyN = profilesList.size
            // Drop results of previous runs, so a cancelled/interrupted test can never
            // make "fastest" pick a server using a stale delay.
            profilesList.forEach {
                it.status = 0
                it.ping = 0
                it.error = null
            }
            val profiles = ConcurrentLinkedQueue(profilesList)
            repeat(DataStore.connectionTestConcurrent.coerceAtLeast(1)) {
                testJobs.add(launch(Dispatchers.IO) {
                    val urlTest = UrlTest()
                    while (isActive) {
                        val profile = profiles.poll() ?: break
                        profile.status = 0
                        try {
                            profile.ping = urlTest.doTest(profile)
                            profile.status = 1
                        } catch (e: PluginManager.PluginNotFoundException) {
                            profile.status = 2
                            profile.error = e.readableMessage
                        } catch (e: Exception) {
                            profile.status = 3
                            profile.error = e.readableMessage
                        }
                        test.update(profile)
                    }
                })
            }

            testJobs.joinAll()

            runOnMainDispatcher {
                test.cancel()
            }
        }

        test.cancel = {
            test.dialogStatus.set(2)
            dialog.dismiss()
            runOnDefaultDispatcher {
                mainJob.cancel()
                testJobs.forEach { it.cancel() }
                test.results.forEach {
                    try {
                        ProfileManager.updateProfile(it)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
                // перечитываем подписки, где что-то изменилось: postReload
                // сам дойдёт до адаптера через groupUpdated()
                tested.map { it.groupId }.distinct().forEach { groupId ->
                    GroupManager.postReload(groupId)
                }
                DataStore.runningTest = false
                onFinished(tested)
            }
        }
        test.minimize = {
            test.dialogStatus.set(1)
            test.notification = ConnectionTestNotification(
                dialog.context,
                "[$label] ${getString(R.string.connection_test)}"
            )
            dialog.hide()
        }
    }

    /**
     * Гоппер VPN: обновление верхних кнопок по состоянию подключения.
     *
     * Состояние берём из [DataStore.serviceState] — его туда кладёт
     * MainActivity на каждый чих сервиса.
     *
     * Раньше здесь же обновлялась карточка статуса под рядом кнопок.
     * Она удалена: состояние подключения и выбранный сервер с пингом
     * показывает нижняя панель (StatsBar), так что карточка была дублем.
     */
    fun updateConnectionUi() {
        val v = view ?: return
        val ctx = context ?: return
        // В режиме выбора профиля (ProfileSelectActivity) круглой кнопки
        // подключения и строки действий нет — обновлять нечего.
        if (select) return

        val state = DataStore.serviceState

        // Цвет круглой кнопки подключения: пока VPN не подключён — красный,
        // подключён — зелёный. Обычная логика «зелёный значит можно
        // подключать» тут вводила в заблуждение: кнопка меняла цвет
        // нажатием, а не состоянием.
        v.findViewById<View>(R.id.connect_button)?.let { connectButton ->
            val buttonColor = when (state) {
                BaseService.State.Connected ->
                    ctx.getColorAttr(R.attr.colorPrimary)
                BaseService.State.Connecting ->
                    ContextCompat.getColor(ctx, R.color.gopper_400)
                else ->
                    ContextCompat.getColor(ctx, R.color.material_red_500)
            }

            // Объём: заливка светлее сверху и темнее снизу, плюс тень
            // из android:elevation в разметке. Конструктор с двумя
            // цветами, а не setGradient(): тот появился только в API 24,
            // а minSdk в приложении 21.
            connectButton.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(
                    ColorUtils.blendARGB(buttonColor, Color.WHITE, 0.22f),
                    ColorUtils.blendARGB(buttonColor, Color.BLACK, 0.16f),
                ),
            ).apply { shape = GradientDrawable.OVAL }

            // Стрелка на знаке лежит поверх щита, поэтому её видно даже
            // когда она того же цвета, что кнопка: красим её так же,
            // чтобы знак менялся вместе с кнопкой.
            (v.findViewById<ImageView>(R.id.connect_icon)?.drawable as? LayerDrawable)?.let { layers ->
                if (layers.numberOfLayers > 1) {
                    layers.getDrawable(1)?.mutate()?.setTint(buttonColor)
                }
            }

            connectButton.contentDescription =
                ctx.getText(if (state.canStop) R.string.stop else R.string.connect)
        }
    }

    /**
     * Гоппер VPN: увести верхние кнопки при прокрутке списка.
     *
     * Штатный AppBarLayout не годится: вложенный скролл перехватывал
     * ViewPager2, до CoordinatorLayout он не доходил, а ручной вызов
     * AppBarLayout.Behavior.onNestedScroll ответа не дал — проверено на
     * устройстве. Поэтому смещение считаем сами: блок кнопок и всё, что под
     * ним, едут вверх на одну и ту же величину, поэтому список подтягивается
     * без дыры, а панель с шестерёнкой остаётся на месте (у неё elevation).
     */
    private var collapseListener: RecyclerView.OnScrollListener? = null

    private fun attachCollapseOnScroll(listView: RecyclerView) {
        collapseListener?.let { listView.removeOnScrollListener(it) }
        val listener = object : RecyclerView.OnScrollListener() {
            override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) {
                if (dy == 0) return
                val root = view ?: return
                val block = root.findViewById<View>(R.id.top_buttons) ?: return
                val below = root.findViewById<View>(R.id.bottom_block) ?: return
                val max = block.height
                if (max <= 0) return
                // dy > 0 — список прокрутился вверх, блок сворачивается
                if (baseBottomHeight == 0) baseBottomHeight = below.height
                val offset = (collapseOffset + dy).coerceIn(0, max)
                if (offset == collapseOffset) return
                collapseOffset = offset

                // Блок кнопок уезжает сдвигом и уходит под панель. Нижний
                // блок растёт вверх: высоту задаём явно, отступ сдвигает верх.
                // Одного отрицательного отступа мало — проверено на
                // устройстве: верх вставал на место, а снизу оставалась дыра,
                // потому что weight высоту не пересчитывает.
                block.translationY = -offset.toFloat()
                val lp = below.layoutParams as LinearLayout.LayoutParams
                lp.weight = 0f
                lp.topMargin = -offset
                lp.height = baseBottomHeight + offset
                below.layoutParams = lp
            }
        }
        listView.addOnScrollListener(listener)
        collapseListener = listener
    }

    private var collapseOffset = 0

    /** Высота нижнего блока при полностью развёрнутом блоке кнопок. */
    private var baseBottomHeight = 0

    private fun detachCollapseOnScroll() {
        collapseListener?.let {
            if (::accordion.isInitialized) accordion.removeOnScrollListener(it)
        }
        collapseListener = null
    }

    /** Гоппер VPN: вернуть блок кнопок на место, например при смене подписки. */
    private fun resetCollapse() {
        collapseOffset = 0
        val root = view ?: return
        root.findViewById<View>(R.id.top_buttons)?.translationY = 0f
        root.findViewById<View>(R.id.bottom_block)?.let { below ->
            val lp = below.layoutParams as? LinearLayout.LayoutParams ?: return@let
            lp.topMargin = 0
            if (baseBottomHeight != 0) {
                lp.height = baseBottomHeight
            } else {
                // ещё ни разу не сворачивали — возвращаем исходную развёртку
                lp.weight = 1f
                lp.height = 0
            }
            below.layoutParams = lp
        }
    }

    /**
     * Гоппер VPN: отметки в меню «⋮ → Порядок серверов» относятся к
     * раскрытой подписке. В режиме выбора профиля меню с порядком нет,
     * поэтому и настраивать нечего.
     */
    fun checkOrderMenu() {
        if (select) return

        val menu = toolbar.menu
        val origin = menu.findItem(R.id.action_order_origin) ?: return
        val byName = menu.findItem(R.id.action_order_by_name)
        val byDelay = menu.findItem(R.id.action_order_by_delay)

        val group = expandedGroupOrNull() ?: return
        when (group.order) {
            GroupOrder.ORIGIN -> origin.isChecked = true
            GroupOrder.BY_NAME -> byName.isChecked = true
            GroupOrder.BY_DELAY -> byDelay.isChecked = true
        }

        fun updateTo(order: Int) {
            if (group.order == order) return
            runOnDefaultDispatcher {
                group.order = order
                GroupManager.updateGroup(group)
            }
        }

        origin.setOnMenuItemClickListener {
            it.isChecked = true
            updateTo(GroupOrder.ORIGIN)
            true
        }
        byName.setOnMenuItemClickListener {
            it.isChecked = true
            updateTo(GroupOrder.BY_NAME)
            true
        }
        byDelay.setOnMenuItemClickListener {
            it.isChecked = true
            updateTo(GroupOrder.BY_DELAY)
            true
        }
    }

    /**
     * NekoBox Fast: "Ping all" - tests every server of every subscription.
     */
    fun pingAllServers() {
        val label = getString(R.string.ping_all_servers)
        testAllProfiles(label) { tested ->
            val available = tested.count { it.status == 1 }
            onMainDispatcher {
                snackbar(
                    getString(
                        R.string.ping_all_summary, tested.size, available, tested.size - available
                    )
                ).show()
                // пинг серверов обновился — нижняя панель покажет новые значения
                (activity as? MainActivity)?.updateStatsBar()
                updateConnectionUi()
            }
        }
    }

    /**
     * NekoBox Fast: "Fastest" - tests every server of every subscription and switches
     * to the one with the lowest measured delay.
     */
    fun selectFastestServer() {
        val label = getString(R.string.fastest_server)
        testAllProfiles(label) { tested ->
            val best = tested.filter { it.status == 1 }.minByOrNull { it.ping }
            if (best == null) {
                onMainDispatcher {
                    snackbar(R.string.fastest_none).show()
                }
                return@testAllProfiles
            }

            val previous = DataStore.selectedProxy
            if (previous != best.id) {
                DataStore.selectedProxy = best.id
                ProfileManager.postUpdate(previous)
                ProfileManager.postUpdate(best, true)
                if (DataStore.serviceState.canStop) {
                    SagerNet.reloadService()
                }
            }

            onMainDispatcher {
                // показываем подписку, в которой оказался самый быстрый сервер
                adapter.expand(best.groupId)
                snackbar(
                    getString(R.string.fastest_switched, best.displayName(), best.ping)
                ).show()
                // выбрали другой сервер — нижняя панель покажет новое имя и пинг
                (activity as? MainActivity)?.updateStatsBar()
                updateConnectionUi()
            }
        }
    }

/**
     * Гоппер VPN: адаптер вертикального аккордеона подписок.
     *
     * В одном списке живут и заголовки подписок, и их серверы. Плоский
     * список строк собирается в [rows]: на каждую подписку идёт
     * заголовок, а под ним — её профили, если подписка раскрыта.
     *
     * Раскрыта не более одной подписки за раз; её id лежит в
     * [expandedId]. [DataStore.selectedGroup] хранит то же значение,
     * потому что остальной код приложения (URL-тест, «текущая подписка»,
     * настройки) смотрит именно туда. Когда все свёрнуты, expandedId
     * равен нулю, а в DataStore остаётся последняя раскрытая — иначе
     * «текущая подписка» была бы потеряна.
     */
    inner class AccordionAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
        ProfileManager.Listener,
        GroupManager.Listener,
        UndoSnackbarManager.Interface<ProxyEntity> {

        init {
            setHasStableIds(true)
            // подписки и их серверы читаем сразу: список должен быть
            // готов к первому кадру, а не после первого события
            reload()
        }

        var groupList: ArrayList<ProxyGroup> = ArrayList()

        /** Профили каждой подписки, по её id. */
        val groupData = HashMap<Long, GroupData>()

        private var rows: List<Row> = emptyList()

        /** Раскрытая подписка; 0 — свёрнуты все. */
        var expandedId = 0L

        /** Сработало, когда раскрытая подписка сменилась: обновить меню и сбросить сворачивание. */
        var onGroupChanged: (() -> Unit)? = null

        private fun rebuildRows() {
            val out = ArrayList<Row>(groupList.size + 8)
            for (group in groupList) {
                out.add(Row(group, null))
                if (expandedId == group.id) {
                    groupData[group.id]?.ids?.forEach { out.add(Row(group, it)) }
                }
            }
            rows = out
        }

        private fun rowAt(position: Int): Row? = rows.getOrNull(position)

        fun positionOfProfile(profileId: Long): Int =
            rows.indexOfFirst { it.profileId == profileId }

        fun positionOfHeader(groupId: Long): Int =
            rows.indexOfFirst { it.profileId == null && it.group.id == groupId }

        fun expand(groupId: Long) {
            if (groupId <= 0 || expandedId == groupId) return
            if (groupList.none { it.id == groupId }) return
            expandedId = groupId
            DataStore.selectedGroup = groupId
            rebuildRows()
            notifyDataSetChanged()
            onGroupChanged?.invoke()
        }

        fun collapse(groupId: Long) {
            if (expandedId != groupId) return
            expandedId = 0L
            rebuildRows()
            notifyDataSetChanged()
            onGroupChanged?.invoke()
        }

        fun toggle(groupId: Long) {
            if (expandedId == groupId) collapse(groupId) else expand(groupId)
        }

        fun reload(now: Boolean = false) {
            runOnDefaultDispatcher {
                var newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                if (newGroupList.isEmpty()) {
                    SagerDatabase.groupDao.createGroup(ProxyGroup(ungrouped = true))
                    newGroupList = ArrayList(SagerDatabase.groupDao.allGroups())
                }
                newGroupList.find { it.ungrouped }?.let {
                    if (SagerDatabase.proxyDao.countByGroup(it.id) == 0L) {
                        newGroupList.remove(it)
                    }
                }

                // Раскрываем ту подписку, к которой уже привязан выбор
                // сервера. Если её больше нет — первую попавшуюся.
                var target = selectedItem?.groupId ?: DataStore.currentGroupId()
                if (newGroupList.none { it.id == target }) {
                    target = newGroupList.firstOrNull()?.id ?: 0L
                }
                if (target > 0) DataStore.selectedGroup = target

                // Профили всех подписок читаем заранее: иначе при каждом
                // сворачивании пришлось бы ходить в базу заново.
                val loaded = HashMap<Long, GroupData>()
                for (group in newGroupList) {
                    loaded[group.id] = loadGroup(group)
                }

                // обновление адаптера всегда идёт через post: и при первом
                // заполнении, и при перечитывании после правок
                accordion.post {
                    groupList = newGroupList
                    groupData.clear()
                    groupData.putAll(loaded)
                    expandedId = target
                    rebuildRows()
                    notifyDataSetChanged()
                    onGroupChanged?.invoke()
                }
            }
        }

        private fun loadGroup(group: ProxyGroup): GroupData {
            val data = GroupData()
            val profiles = sortedProfiles(group)
            data.map.putAll(profiles.associateBy { it.id })
            data.ids.addAll(profiles.map { it.id })
            return data
        }

        /**
         * Гоппер VPN: порядок серверов в подписке.
         *
         * GroupOrder.ORIGIN означает «следовать общей настройке
         * sortServersByDelay», а BY_NAME и BY_DELAY остаются
         * переопределением для конкретной подписки. Выключенная сортировка
         * по пингу оставляет исходный порядок.
         */
        private fun sortedProfiles(group: ProxyGroup): List<ProxyEntity> {
            val profiles = SagerDatabase.proxyDao.getByGroup(group.id).toMutableList()
            val sortByDelay = when (group.order) {
                GroupOrder.BY_DELAY -> true
                GroupOrder.BY_NAME -> false
                else -> DataStore.sortServersByDelay
            }
            if (sortByDelay) {
                // 114514 — заглушка: недоступные серверы уходят в конец,
                // иначе нулевой пинг встал бы первым.
                return profiles.sortedBy { if (it.status == 1) it.ping else 114514 }
            }
            return profiles
        }

        private val TYPE_HEADER = 0
        private val TYPE_PROFILE = 1

        override fun getItemViewType(position: Int): Int =
            if (rowAt(position)?.profileId == null) TYPE_HEADER else TYPE_PROFILE

        override fun getItemId(position: Int): Long {
            val row = rows[position]
            // Заголовок и сервер различаются младшим битом: id подписки и
            // id профиля из разных таблиц и вполне могут совпасть.
            return if (row.profileId == null) row.group.id shl 1
            else (row.profileId shl 1) or 1L
        }

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return if (viewType == TYPE_HEADER) {
                AccordionHeaderHolder(
                    inflater.inflate(R.layout.layout_accordion_header, parent, false)
                )
            } else {
                ConfigurationHolder(inflater.inflate(R.layout.layout_profile, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val row = rowAt(position) ?: return
            val profileId = row.profileId
            if (profileId == null) {
                (holder as? AccordionHeaderHolder)?.bind(row.group)
            } else {
                val entity = groupData[row.group.id]?.map?.get(profileId)
                if (entity != null) {
                    (holder as? ConfigurationHolder)?.bind(entity, row.group)
                }
            }
        }

        override fun getItemCount(): Int = rows.size

        // --- перетаскивание серверов внутри одной подписки ---

        private val updated = HashSet<ProxyEntity>()

        /** Сервер можно тащить только в пределах своей подписки. */
        fun canMove(from: Int, to: Int): Boolean {
            val a = rowAt(from) ?: return false
            val b = rowAt(to) ?: return false
            return a.profileId != null && b.profileId != null && a.group.id == b.group.id
        }

        fun moveProfile(from: Int, to: Int) {
            if (!canMove(from, to)) return
            val firstId = rows[from].profileId ?: return
            val targetId = rows[to].profileId ?: return
            val data = groupData[rows[from].group.id] ?: return
            val first = data.map[firstId] ?: return
            val firstIndex = data.ids.indexOf(firstId)
            val targetIndex = data.ids.indexOf(targetId)
            if (firstIndex < 0 || targetIndex < 0) return

            var previousOrder = first.userOrder
            val (step, range) = if (firstIndex < targetIndex) {
                Pair(1, firstIndex until targetIndex)
            } else {
                Pair(-1, targetIndex + 1 downTo firstIndex)
            }
            for (i in range) {
                val next = data.map[data.ids[i + step]] ?: continue
                val order = next.userOrder
                next.userOrder = previousOrder
                previousOrder = order
                data.ids[i] = next.id
                updated.add(next)
            }
            first.userOrder = previousOrder
            data.ids[targetIndex] = first.id
            updated.add(first)
            rebuildRows()
            notifyItemMoved(from, to)
        }

        fun commitMove() = runOnDefaultDispatcher {
            updated.forEach { SagerDatabase.proxyDao.updateProxy(it) }
            updated.clear()
        }

        // --- удаление сервера ---

        fun removeProfile(groupId: Long, profileId: Long) {
            val data = groupData[groupId] ?: return
            if (!data.ids.remove(profileId)) return
            data.map.remove(profileId)
            rebuildRows()
            notifyDataSetChanged()
        }

        override fun undo(actions: List<Pair<Int, ProxyEntity>>) {
            if (actions.isEmpty()) return
            for ((_, item) in actions) {
                val data = groupData[item.groupId] ?: continue
                data.map[item.id] = item
                data.ids.add(item.id)
            }
            rebuildRows()
            notifyDataSetChanged()
        }

        override fun commit(actions: List<Pair<Int, ProxyEntity>>) {
            if (actions.isEmpty()) return
            runOnDefaultDispatcher {
                for ((_, entity) in actions) {
                    try {
                        ProfileManager.deleteProfile(entity.groupId, entity.id)
                    } catch (e: Exception) {
                        Logs.w(e)
                    }
                }
            }
        }

        // --- события менеджеров ---

        override suspend fun onAdd(profile: ProxyEntity) {
            val data = groupData[profile.groupId]
            if (data == null) {
                // подписки ещё не было в списке — перечитать всё
                reload()
                return
            }
            onMainDispatcher {
                if (::undoManager.isInitialized) undoManager.flush()
                data.map[profile.id] = profile
                data.ids.add(profile.id)
                rebuildRows()
                notifyDataSetChanged()
            }
        }

        override suspend fun onUpdated(profile: ProxyEntity, noTraffic: Boolean) {
            val data = groupData[profile.groupId] ?: return
            if (data.ids.indexOf(profile.id) < 0) return
            onMainDispatcher {
                if (::undoManager.isInitialized) undoManager.flush()
                data.map[profile.id] = profile
                rebuildRows()
                val position = positionOfProfile(profile.id)
                if (position >= 0) notifyItemChanged(position) else notifyDataSetChanged()
                if (noTraffic) {
                    runOnDefaultDispatcher {
                        onUpdated(TrafficData(id = profile.id, rx = profile.rx, tx = profile.tx))
                    }
                }
            }
        }

        override suspend fun onUpdated(data: TrafficData) {
            val position = positionOfProfile(data.id)
            if (position < 0) return
            onMainDispatcher {
                val holder = accordion.findViewHolderForAdapterPosition(position)
                if (holder is ConfigurationHolder) {
                    holder.bind(holder.entity, holder.group, data)
                }
            }
        }

        override suspend fun onRemoved(groupId: Long, profileId: Long) {
            val data = groupData[groupId] ?: return
            if (!data.ids.remove(profileId)) return
            data.map.remove(profileId)
            val empty = data.ids.isEmpty()
            onMainDispatcher {
                rebuildRows()
                notifyDataSetChanged()
            }
            // подписка «Без группы» без серверов пропадает совсем
            if (empty && groupList.firstOrNull { it.id == groupId }?.ungrouped == true) {
                reload()
            }
        }

        override suspend fun groupAdd(group: ProxyGroup) {
            reload()
        }

        override suspend fun groupRemoved(groupId: Long) {
            reload()
        }

        override suspend fun groupUpdated(group: ProxyGroup) {
            val index = groupList.indexOfFirst { it.id == group.id }
            if (index < 0) {
                reload()
                return
            }
            groupList[index] = group
            runOnDefaultDispatcher {
                val data = loadGroup(group)
                onMainDispatcher {
                    groupData[group.id] = data
                    val header = positionOfHeader(group.id)
                    rebuildRows()
                    if (header >= 0) notifyItemChanged(header) else notifyDataSetChanged()
                }
            }
        }

        override suspend fun groupUpdated(groupId: Long) {
            val group = SagerDatabase.groupDao.getById(groupId) ?: return
            groupUpdated(group)
        }
    }

    /**
     * Гоппер VPN: заголовок подписки в аккордеоне.
     *
     * Тап по строке сворачивает или разворачивает подписку. Три кнопки
     * справа — настройки, URL-тест и обновить подписку. В режиме выбора
     * профиля они прячутся: там подписку не настраивают и не обновляют.
     */
    inner class AccordionHeaderHolder(val view: View) : RecyclerView.ViewHolder(view) {

        val triangle: AppCompatImageView = view.findViewById(R.id.accordion_triangle)
        val name: TextView = view.findViewById(R.id.accordion_name)
        val count: TextView = view.findViewById(R.id.accordion_count)
        val settingsButton: View = view.findViewById(R.id.accordion_settings)
        val urlTestButton: View = view.findViewById(R.id.accordion_url_test)
        val updateButton: View = view.findViewById(R.id.accordion_update)

        fun bind(group: ProxyGroup) {
            val total = adapter.groupData[group.id]?.ids?.size ?: 0
            val expanded = adapter.expandedId == group.id

            name.text = runCatching { group.displayName() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: getString(R.string.menu_configuration)
            count.text = view.resources.getQuantityString(R.plurals.servers_count, total, total)
            triangle.setImageResource(
                if (expanded) R.drawable.ic_baseline_expand_less_24
                else R.drawable.ic_baseline_expand_more_24
            )

            val real = group.ungrouped == false && !select
            settingsButton.isVisible = real
            urlTestButton.isVisible = real
            updateButton.isVisible = real && group.type == GroupType.SUBSCRIPTION

            settingsButton.setOnClickListener {
                startActivity(Intent(requireContext(), GroupSettingsActivity::class.java).apply {
                    putExtra(GroupSettingsActivity.EXTRA_GROUP_ID, group.id)
                })
            }
            urlTestButton.setOnClickListener {
                // URL-тест всегда идёт по раскрытой подписке
                adapter.expand(group.id)
                urlTest()
            }
            updateButton.setOnClickListener {
                runOnDefaultDispatcher { GroupUpdater.startUpdate(group, true) }
            }

            view.setOnClickListener { adapter.toggle(group.id) }
        }
    }

    val profileAccess = Mutex()
    val reloadAccess = Mutex()

    inner class ConfigurationHolder(val view: View) : RecyclerView.ViewHolder(view),
    PopupMenu.OnMenuItemClickListener {

    lateinit var entity: ProxyEntity

    /** Подписка, которой принадлежит сервер: нужна для настроек и удаления. */
    lateinit var group: ProxyGroup

    val profileName: TextView = view.findViewById(R.id.profile_name)
    val profileType: TextView = view.findViewById(R.id.profile_type)
    val profileAddress: TextView = view.findViewById(R.id.profile_address)
    val profileStatus: TextView = view.findViewById(R.id.profile_status)

    val trafficText: TextView = view.findViewById(R.id.traffic_text)
    val selectedView: LinearLayout = view.findViewById(R.id.selected_view)
    val editButton: ImageView = view.findViewById(R.id.edit)
    val shareLayout: LinearLayout = view.findViewById(R.id.share)
    val shareLayer: LinearLayout = view.findViewById(R.id.share_layer)
    val shareButton: ImageView = view.findViewById(R.id.shareIcon)
    val removeButton: ImageView = view.findViewById(R.id.remove)

    /**
     * [proxyEntity] — сервер, [group] — его подписка. Раньше подписка
     * хранилась в фрагменте на страницу, теперь каждый сервер
     * принадлежит конкретной подписке аккордеона.
     */
    fun bind(
        proxyEntity: ProxyEntity,
        group: ProxyGroup,
        trafficData: TrafficData? = null,
    ) {
        val pf = this@ConfigurationFragment

        entity = proxyEntity
        this.group = group

        if (select) {
            view.setOnClickListener {
                (requireActivity() as SelectCallback).returnProfile(proxyEntity.id)
            }
        } else {
            view.setOnClickListener {
                runOnDefaultDispatcher {
                        var update: Boolean
                        var lastSelected: Long
                        profileAccess.withLock {
                            update = DataStore.selectedProxy != proxyEntity.id
                            lastSelected = DataStore.selectedProxy
                            DataStore.selectedProxy = proxyEntity.id
                            onMainDispatcher {
                                selectedView.visibility = View.VISIBLE
                            }
                        }

                        if (update) {
                            ProfileManager.postUpdate(lastSelected)
                            if (DataStore.serviceState.canStop && reloadAccess.tryLock()) {
                                SagerNet.reloadService()
                                reloadAccess.unlock()
                            }
                        } else if (SagerNet.isTv) {
                            if (DataStore.serviceState.started) {
                                SagerNet.stopService()
                            } else {
                                SagerNet.startService()
                            }
                        }
                    }

                }
            }

            profileName.text = proxyEntity.displayName()
            profileType.text = proxyEntity.displayType()
            profileType.setTextColor(requireContext().getProtocolColor(proxyEntity.type))

            var rx = proxyEntity.rx
            var tx = proxyEntity.tx
            if (trafficData != null) {
                // use new data
                tx = trafficData.tx
                rx = trafficData.rx
            }

            val showTraffic = rx + tx != 0L
            trafficText.isVisible = showTraffic
            if (showTraffic) {
                trafficText.text = view.context.getString(
                    R.string.traffic,
                    Formatter.formatFileSize(view.context, tx),
                    Formatter.formatFileSize(view.context, rx)
                )
            }

            var address = proxyEntity.displayAddress()
            if (showTraffic && address.length >= 30) {
                address = address.substring(0, 27) + "..."
            }

            if (proxyEntity.requireBean().name.isBlank() || !pf.alwaysShowAddress) {
                address = ""
            }

            profileAddress.text = address
            (trafficText.parent as View).isGone =
                (!showTraffic || proxyEntity.status <= 0) && address.isBlank()

            if (proxyEntity.status <= 0) {
                if (showTraffic) {
                    profileStatus.text = trafficText.text
                    profileStatus.setTextColor(requireContext().getColorAttr(android.R.attr.textColorSecondary))
                    trafficText.text = ""
                } else {
                    profileStatus.text = ""
                }
            } else if (proxyEntity.status == 1) {
                profileStatus.text = getString(R.string.available, proxyEntity.ping)
                profileStatus.setTextColor(requireContext().getColour(R.color.material_green_500))
            } else {
                profileStatus.setTextColor(requireContext().getColour(R.color.material_red_500))
                if (proxyEntity.status == 2) {
                    profileStatus.text = proxyEntity.error
                }
            }

            if (proxyEntity.status == 3) {
                val err = proxyEntity.error ?: "<?>"
                val msg = Protocols.genFriendlyMsg(err)
                profileStatus.text = if (msg != err) msg else getString(R.string.unavailable)
                profileStatus.setOnClickListener {
                    alert(err).tryToShow()
                }
            } else {
                profileStatus.setOnClickListener(null)
            }

            editButton.setOnClickListener {
                it.context.startActivity(
                    proxyEntity.settingIntent(
                        it.context, group.type == GroupType.SUBSCRIPTION
                    )
                )
            }

            removeButton.setOnClickListener {
                // Отмену удаления хранит undoManager, ему нужен индекс
                // строки в общем списке аккордеона.
                val index = adapter.positionOfProfile(proxyEntity.id)
                if (index < 0) return@setOnClickListener
                adapter.removeProfile(group.id, proxyEntity.id)
                undoManager.remove(index to proxyEntity)
            }

            val selectOrChain = select || proxyEntity.type == ProxyEntity.TYPE_CHAIN
            shareLayout.isGone = selectOrChain
            editButton.isGone = select
            removeButton.isGone = select

            proxyEntity.nekoBean?.apply {
                shareLayout.isGone = true
            }

            runOnDefaultDispatcher {
                val selected = (selectedItem?.id ?: DataStore.selectedProxy) == proxyEntity.id
                val started =
                    selected && DataStore.serviceState.started && DataStore.currentProfile == proxyEntity.id
                onMainDispatcher {
                    editButton.isEnabled = !started
                    removeButton.isEnabled = !started
                    selectedView.visibility = if (selected) View.VISIBLE else View.INVISIBLE
                }

                fun showShare(anchor: View) {
                    val popup = PopupMenu(requireContext(), anchor)
                    popup.menuInflater.inflate(R.menu.profile_share_menu, popup.menu)

                    when {
                        !proxyEntity.haveStandardLink() -> {
                            popup.menu.findItem(R.id.action_group_qr).subMenu?.removeItem(R.id.action_standard_qr)
                            popup.menu.findItem(R.id.action_group_clipboard).subMenu?.removeItem(
                                R.id.action_standard_clipboard
                            )
                        }

                        !proxyEntity.haveLink() -> {
                            popup.menu.removeItem(R.id.action_group_qr)
                            popup.menu.removeItem(R.id.action_group_clipboard)
                        }
                    }

                    if (proxyEntity.nekoBean != null) {
                        popup.menu.removeItem(R.id.action_group_configuration)
                    }

                    popup.setOnMenuItemClickListener(this@ConfigurationHolder)
                    popup.show()
                }

                if (!(select || proxyEntity.type == ProxyEntity.TYPE_CHAIN)) {
                    onMainDispatcher {
                        shareLayer.setBackgroundColor(Color.TRANSPARENT)
                        shareButton.setImageResource(R.drawable.ic_social_share)
                        shareButton.setColorFilter(Color.GRAY)
                        shareButton.isVisible = true

                        shareLayout.setOnClickListener {
                            showShare(it)
                        }
                    }
                }
            }

        }

        var currentName = ""
        fun showCode(link: String) {
            QRCodeDialog(link, currentName).showAllowingStateLoss(parentFragmentManager)
        }

        fun export(link: String) {
            val success = SagerNet.trySetPrimaryClip(link)
            (activity as MainActivity).snackbar(if (success) R.string.action_export_msg else R.string.action_export_err)
                .show()
        }

        override fun onMenuItemClick(item: MenuItem): Boolean {
            try {
                currentName = entity.displayName()!!
                when (item.itemId) {
                    R.id.action_standard_qr -> showCode(entity.toStdLink())
                    R.id.action_standard_clipboard -> export(entity.toStdLink())
                    R.id.action_universal_qr -> showCode(entity.requireBean().toUniversalLink())
                    R.id.action_universal_clipboard -> export(
                        entity.requireBean().toUniversalLink()
                    )

                    R.id.action_config_export_clipboard -> export(entity.exportConfig().first)
                    R.id.action_config_export_file -> {
                        val cfg = entity.exportConfig()
                        DataStore.serverConfig = cfg.first
                        startFilesForResult(
                            this@ConfigurationFragment.exportConfig, cfg.second
                        )
                    }
                }
            } catch (e: Exception) {
                Logs.w(e)
                (activity as MainActivity).snackbar(e.readableMessage).show()
                return true
            }
            return true
        }
    }

    private val exportConfig =
        registerForActivityResult(ActivityResultContracts.CreateDocument()) { data ->
            if (data != null) {
                runOnDefaultDispatcher {
                    try {
                        (requireActivity() as MainActivity).contentResolver.openOutputStream(data)!!
                            .bufferedWriter()
                            .use {
                                it.write(DataStore.serverConfig)
                            }
                        onMainDispatcher {
                            snackbar(getString(R.string.action_export_msg)).show()
                        }
                    } catch (e: Exception) {
                        Logs.w(e)
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                    }

                }
            }
        }

}
