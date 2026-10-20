package io.nekohasekai.sagernet.bg

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy.UPDATE
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import io.nekohasekai.sagernet.R
import io.nekohasekai.sagernet.database.DataStore
import io.nekohasekai.sagernet.database.SagerDatabase
import io.nekohasekai.sagernet.database.SubscriptionBean
import io.nekohasekai.sagernet.group.GroupUpdater
import io.nekohasekai.sagernet.ktx.Logs
import io.nekohasekai.sagernet.ktx.app
import java.util.concurrent.TimeUnit

object SubscriptionUpdater {

    private const val WORK_NAME = "SubscriptionUpdater"

    // Со скольки минут может работать периодическая задача WorkManager.
    const val MIN_DELAY_MINUTES = 15

    // Фактический интервал подписки: свой, если включён лично у неё,
    // иначе глобальный. Глобальный переключатель перекрывает выключенный локальный.
    fun effectiveDelaySeconds(subscription: SubscriptionBean): Long {
        val delay = if (subscription.autoUpdate == true) {
            (subscription.autoUpdateDelay ?: 0).toLong()
        } else {
            DataStore.subscriptionGlobalAutoUpdateDelay.toLong()
        }
        return delay * 60L
    }

    fun effectiveAutoUpdate(subscription: SubscriptionBean): Boolean {
        return subscription.autoUpdate == true || DataStore.subscriptionGlobalAutoUpdate
    }

    suspend fun reconfigureUpdater() {
        RemoteWorkManager.getInstance(app).cancelUniqueWork(WORK_NAME)

        val subscriptions = SagerDatabase.groupDao.subscriptions()
            .filter { effectiveAutoUpdate(it.subscription!!) }
        if (subscriptions.isEmpty()) return

        // PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS
        var minDelay =
            subscriptions.minOf { effectiveDelaySeconds(it.subscription!!) } / 60L
        val now = System.currentTimeMillis() / 1000L
        var minInitDelay =
            subscriptions.minOf { now - it.subscription!!.lastUpdated - (minDelay * 60) }
        if (minDelay < MIN_DELAY_MINUTES) minDelay = MIN_DELAY_MINUTES.toLong()
        if (minInitDelay > 60) minInitDelay = 60

        // main process
        RemoteWorkManager.getInstance(app).enqueueUniquePeriodicWork(
            WORK_NAME,
            UPDATE,
            PeriodicWorkRequest.Builder(UpdateTask::class.java, minDelay, TimeUnit.MINUTES)
                .apply {
                    if (minInitDelay > 0) setInitialDelay(minInitDelay, TimeUnit.SECONDS)
                }
                .build()
        )
    }

    class UpdateTask(
        appContext: Context, params: WorkerParameters
    ) : CoroutineWorker(appContext, params) {

        val nm = NotificationManagerCompat.from(applicationContext)

        val notification = NotificationCompat.Builder(applicationContext, "service-subscription")
            .setWhen(0)
            .setTicker(applicationContext.getString(R.string.forward_success))
            .setContentTitle(applicationContext.getString(R.string.subscription_update))
            .setSmallIcon(R.drawable.ic_service_active)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)

        override suspend fun doWork(): Result {
            var subscriptions =
                SagerDatabase.groupDao.subscriptions().filter { effectiveAutoUpdate(it.subscription!!) }
            if (!DataStore.serviceState.connected) {
                Logs.d("work: not connected")
                subscriptions = subscriptions.filter { !it.subscription!!.updateWhenConnectedOnly }
            }

            if (subscriptions.isNotEmpty()) for (profile in subscriptions) {
                val subscription = profile.subscription!!

                if (((System.currentTimeMillis() / 1000).toInt() - subscription.lastUpdated) < effectiveDelaySeconds(subscription)) {
                    Logs.d("work: not updating " + profile.displayName())
                    continue
                }
                Logs.d("work: updating " + profile.displayName())

                notification.setContentText(
                    applicationContext.getString(
                        R.string.subscription_update_message, profile.displayName()
                    )
                )
                nm.notify(2, notification.build())

                GroupUpdater.executeUpdate(profile, false)
            }

            nm.cancel(2)

            return Result.success()
        }
    }

}