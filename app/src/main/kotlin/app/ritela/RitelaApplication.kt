package app.ritela

import android.app.Application
import app.ritela.data.ProfileManager

class RitelaApplication :
    Application(),
    androidx.work.Configuration.Provider {
    override val workManagerConfiguration get() = androidx.work.Configuration.Builder()
        .setMinimumLoggingLevel(android.util.Log.ERROR).build()
    val profiles by lazy { ProfileManager(this) }
    val periods get() = profiles.session.value.periods
    val settings get() = profiles.session.value.settings
    val days get() = profiles.session.value.days
    val journal get() = profiles.session.value.journal
    val backups get() = profiles.session.value.backups
}
