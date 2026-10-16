package app.ritela.data.sync

import android.content.Context
import androidx.core.content.edit
import app.ritela.domain.sync.ForegroundSyncBudget
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Only invoked by a RESUMED profile or explicit Send; never creates relay keys. */
class ForegroundSync(
    context: Context,
    private val profile: String,
    private val runtime: RelayRuntime,
    private val clock: () -> Long = System::currentTimeMillis
) {
    companion object {
        fun clear(context: Context, profile: String) {
            context.getSharedPreferences(
                "sync-foreground-budget",
                Context.MODE_PRIVATE
            ).edit(commit = true) {
                remove(profile)
                remove("activity-$profile")
                remove("burst-$profile")
            }
        }
    }
    private val preferences = context.getSharedPreferences(
        "sync-foreground-budget",
        Context.MODE_PRIVATE
    )
    private val mutex = Mutex()
    suspend fun send(): RelayRun = runtime.runOnce(fetch = false)

    fun interacted() {
        val now = clock()
        preferences.edit {
            putLong("activity-$profile", now)
            if (now - preferences.getLong("burst-$profile", 0) >= 300000L) {
                putLong("burst-$profile", now)
            }
        }
    }

    fun interval(chat: Boolean): Long {
        val now = clock()
        return when {
            !chat || now - preferences.getLong("activity-$profile", 0) >= 120000L -> 60000L
            now - preferences.getLong("burst-$profile", 0) < 120000L -> 5000L
            else -> 15000L
        }
    }

    suspend fun check(): RelayRun = mutex.withLock {
        if (!runtime.foregroundReady()) return@withLock RelayRun.IDLE
        val now = clock()
        val attempts = preferences.getString(profile, "").orEmpty().split(',')
            .mapNotNull(String::toLongOrNull).filter { now - it < 3600000L }
        if (!ForegroundSyncBudget.permits(attempts, now)) return@withLock RelayRun.IDLE
        preferences.edit(commit = true) {
            putString(profile, (attempts + now).joinToString(","))
        }
        runtime.runOnce()
    }
}
