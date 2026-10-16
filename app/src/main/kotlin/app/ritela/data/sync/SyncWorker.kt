package app.ritela.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import app.ritela.RitelaApplication
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("profile") ?: return Result.failure()
        val manager = (applicationContext as RitelaApplication).profiles
        val session = manager.session.value
        if (session.profileId != id) return Result.success()
        return try {
            when (session.relay.runOnce()) {
                RelayRun.RETRY -> Result.retry()

                RelayRun.MORE -> {
                    SyncScheduler(applicationContext, id).continueNow().await()
                    Result.success()
                }

                else -> Result.success()
            }
        } catch (
            cancelled: CancellationException
        ) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure()
        }
    }
}

class SyncScheduler(private val context: Context, private val profile: String) {
    init {
        require(UUID.fromString(profile).toString() == profile)
    }
    private val constraints = Constraints.Builder().setRequiredNetworkType(
        NetworkType.CONNECTED
    ).build()
    fun ensure(kick: Boolean) {
        val data = workDataOf("profile" to profile)
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork(
            "sync-periodic-$profile",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS, 15, TimeUnit.MINUTES)
                .setConstraints(constraints).setInputData(data)
                .setInitialDelay(kotlin.random.Random.nextLong(600), TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
        )
        if (kick) {
            manager.enqueueUniqueWork(
                "sync-now-$profile",
                ExistingWorkPolicy.KEEP,
                immediate()
            )
        }
    }

    /** A nonempty/byte-limited page is progress, not a network failure with exponential delay. */
    fun continueNow() = WorkManager.getInstance(context).enqueueUniqueWork(
        "sync-now-$profile",
        ExistingWorkPolicy.APPEND_OR_REPLACE,
        immediate()
    )
    private fun immediate() = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(constraints)
        .setInputData(workDataOf("profile" to profile))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork("sync-periodic-$profile")
        WorkManager.getInstance(context).cancelUniqueWork("sync-now-$profile")
    }
}
