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
            // при включении — немедленная проверка и наведение порядка:
            // основной на контакт, запасной держится наготове
            runProbe()
            while (isActive) {
                delay(INTERVAL_MS)
                if (!DataStore.balancerActive) break
                runProbe()
            }
        }
    }

    private fun stop() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun runProbe() {
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

            maintain(profiles)
        } finally {
            DataStore.runningTest = false
            probeAccess.unlock()
        }
    }

    /**
     * Гоппер VPN: горячий запасной.
     *
     * Балансёр держит пару: основной сервер (выбранный) и второй рабочий
     * «на контакте». Пока основной отвечает, подключение не трогается —
     * следим, чтобы запасной оставался рабочим, и при его смерти берём
     * новый запасной. Когда основной отключается (не отвечает на пинг),
     * подключение переключается на тот запасной, что держится, и вместо
     * него выбирается новый.
     */
    private suspend fun maintain(profiles: List<io.nekohasekai.sagernet.database.ProxyEntity>) {
        if (!DataStore.balancerActive) return

        val mainId = DataStore.selectedProxy
        val main = profiles.find { it.id == mainId }
        var backupId = DataStore.balancerBackupId
        if (backupId == mainId) {
            DataStore.balancerBackupId = 0
            backupId = 0
        }
        val backup = profiles.find { it.id == backupId }

        val mainAlive = main != null && main.status == 1

        if (!mainAlive) {
            // Основной отключился — переключаемся на держимый запасной.
            val target = backup?.takeIf { it.status == 1 }
                ?: profiles.filter { it.status == 1 && it.id != mainId }
                    .minByOrNull { it.ping }
                ?: return
            switchTo(target, previousId = mainId)
            // Основным стал бывший запасной — выбираем новый запасной.
            pickBackup(profiles, excludeId = target.id)
            return
        }

        // Основной жив — держим второй рабочий на контакте.
        if (backup == null || backup.status != 1) {
            pickBackup(profiles, excludeId = mainId)
        }
    }

    /** Выбрать новый запасной: лучший рабочий, не основной. */
    private suspend fun pickBackup(
        profiles: List<io.nekohasekai.sagernet.database.ProxyEntity>,
        excludeId: Long,
    ) {
        val mainId = DataStore.selectedProxy
        val best = profiles.filter {
            it.status == 1 && it.id != mainId && it.id != excludeId
        }.minByOrNull { it.ping } ?: return
        DataStore.balancerBackupId = best.id
        Logs.d("Balancer: backup is ${best.displayName()}")
    }

    /** Переключить подключение на сервер (перезапуск сервиса при работе). */
    private suspend fun switchTo(profile: io.nekohasekai.sagernet.database.ProxyEntity, previousId: Long) {
        DataStore.selectedProxy = profile.id
        DataStore.balancerBackupId = 0
        if (previousId != profile.id) ProfileManager.postUpdate(previousId)
        ProfileManager.postUpdate(profile, true)
        if (DataStore.serviceState.canStop) {
            SagerNet.reloadService()
        }
        Logs.d("Balancer: main down, switched to ${profile.displayName()}")
    }

    /**
     * Общий вход для балансировки из диалоговых проверок («Пинг всех»).
     * Работает, только когда карточка «Выбери за меня» включена.
     */
    suspend fun rebalanceFrom(tested: List<io.nekohasekai.sagernet.database.ProxyEntity>) {
        if (!DataStore.balancerActive) return
        maintain(tested)
    }

}
