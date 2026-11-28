package io.nekohasekai.sagernet.bg

import io.nekohasekai.sagernet.SagerNet
import io.nekohasekai.sagernet.bg.proto.UrlTest
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.GroupManager
import io.nekohasekai.sagernet.database.ProfileManager
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.readableMessage
import io.nekohasekai.sagernet.ktx.runOnDefaultDispatcher
import io.nekohasekai.sagernet.plugin.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Гоппер VPN: фоновый балансёр — механика «Выберите за меня» из
 * GemFloppe. Пока карточка «Балансёр» включена, балансёр:
 *
 *  1. при включении сразу проверяет все серверы и берёт рабочий;
 *  2. раз в [INTERVAL_MS] повторяет проверку и поддерживает выбор —
 *     если текущий сервер перестал отвечать, переключает подключение
 *     на лучший доступный (перезапуск сервиса).
 *
 * Проверка беззвучная (без диалога), чтобы не вылезала на фоне.
 */
object Balancer {

    /** Период поддержки выбранного сервера. */
    private const val INTERVAL_MS = 5 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null
    private val probeAccess = Mutex()

    /**
     * Приводит тикер в состояние, соответствующее настройке.
     * Вызывается при включении/выключении карточки и при старте приложения.
     */
    fun refreshState() {
        runOnDefaultDispatcher {
            if (DataStore.balancerActive) start() else stop()
        }
    }

    private fun start() {
        if (tickerJob?.isActive == true) return
        tickerJob = scope.launch {
            // при включении — немедленная проверка И выбор лучшего
            // рабочего (механика «Выберите за меня» из GemFloppe)
            runProbe(pickBestAlways = true)
            while (isActive) {
                delay(INTERVAL_MS)
                if (!DataStore.balancerActive) break
                // поддержка: подключение не трогаем, пока основной жив
                runProbe(pickBestAlways = false)
            }
        }
    }

    private fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun runProbe(pickBestAlways: Boolean) {
        if (DataStore.runningTest) return
        if (!probeAccess.tryLock()) return
        try {
            DataStore.runningTest = true

            val profiles = SagerDatabase.proxyDao.getAll()
            val queue = ConcurrentLinkedQueue(profiles)
            val jobs = mutableListOf<Job>()
            repeat(DataStore.connectionTestConcurrent.coerceAtLeast(1)) {
                jobs += scope.launch(Dispatchers.IO) {
                    val urlTest = UrlTest()
                    while (isActive) {
                        val profile = queue.poll() ?: break
                        profile.status = 0
                        try {
                            profile.ping = urlTest.doTest(profile)
                            profile.status = 1
                            profile.error = null
                        } catch (e: PluginManager.PluginNotFoundException) {
                            profile.status = 2
                            profile.error = e.readableMessage
                        } catch (e: Exception) {
                            profile.status = 3
                            profile.error = e.readableMessage
                        }
                        try {
                            ProfileManager.updateProfile(profile)
                        } catch (e: Exception) {
                            Logs.w(e)
                        }
                    }
                }
            }
            jobs.joinAll()

            // карточки серверов перечитают статусы из базы
            profiles.map { it.groupId }.distinct().forEach { GroupManager.postReload(it) }

            pickServer(profiles, pickBestAlways)
        } finally {
            DataStore.runningTest = false
            probeAccess.unlock()
        }
    }

    /**
     * Выбор сервера по результатам проверки.
     *
     * pickBestAlways = true — «Выберите за меня» при включении: берём
     * лучший доступный всегда. false — поддержка: подключение держим на
     * текущем сервере и переключаемся, только если он не прошёл проверку.
     */
    private suspend fun pickServer(
        profiles: List<io.nekohasekai.sagernet.database.ProxyEntity>,
        pickBestAlways: Boolean,
    ) {
        val selectedId = DataStore.selectedProxy
        val selected = profiles.find { it.id == selectedId }
        if (!pickBestAlways && selected != null && selected.status == 1) return

        val best = profiles.filter { it.status == 1 }.minByOrNull { it.ping }
        if (best == null || best.id == selectedId) return

        DataStore.selectedProxy = best.id
        selectedId.let { ProfileManager.postUpdate(it) }
        ProfileManager.postUpdate(best, true)
        if (DataStore.serviceState.canStop) {
            SagerNet.reloadService()
        }
        Logs.d("Balancer: picked ${best.displayName()}")
    }

}
