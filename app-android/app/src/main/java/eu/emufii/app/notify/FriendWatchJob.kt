package eu.emufii.app.notify

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import eu.emufii.app.network.CoordinatorClient
import eu.emufii.app.profile.FriendStatus
import eu.emufii.app.profile.FriendStore
import eu.emufii.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FriendWatchJob : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var work: Job? = null

    override fun onStartJob(params: JobParameters?): Boolean {
        work = scope.launch {
            runCatching { sweep(applicationContext) }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        work?.cancel()
        return false
    }

    companion object {
        private const val JOB_ID = 7301
        private const val PERIOD_MS = 15 * 60 * 1000L

        fun sync(context: Context, wantFriends: Boolean) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val hasFriends = FriendStore.get(context).friends.value.isNotEmpty()
            val wanted = wantFriends && hasFriends

            if (!wanted || !Notifications.allowed(context)) {
                scheduler.cancel(JOB_ID)
                return
            }

            scheduler.schedule(
                JobInfo.Builder(JOB_ID, ComponentName(context, FriendWatchJob::class.java))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(PERIOD_MS)
                    .setPersisted(true)
                    .build()
            )
        }

        suspend fun sweep(context: Context) {
            val settings = SettingsStore.get(context)
            val state = WatchState(context)

            if (!settings.notifyFriends.value) return

            val store = FriendStore.get(context)
            val friends = store.friends.value
            if (friends.isEmpty()) return

            val codes = friends.map { it.code }
            val fresh = CoordinatorClient().friendStatuses(codes).getOrNull()?.present ?: return

            val current = codes.associateWith { code ->
                fresh[code]?.let {
                    FriendStatus(
                        online = true,
                        sessionCode = it.sessionCode,
                        romTitle = it.romTitle,
                        romTitleId = it.romTitleId,
                        players = it.players,
                        ready = it.ready
                    )
                } ?: FriendStatus.Offline
            }

            val names = friends.associate { it.code to (fresh[it.code]?.name ?: it.name) }
            friendEvents(state.seen(), current, names).forEach { Notifications.friendEvent(context, it) }

            state.setSeen(seenFrom(current))
            store.noteNames(fresh.mapNotNull { (c, p) -> p.name?.let { c to it } }.toMap())
        }
    }
}
