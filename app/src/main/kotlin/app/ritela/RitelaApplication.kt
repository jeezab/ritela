package app.ritela

import android.app.Application
import app.ritela.data.ProfileManager

class RitelaApplication : Application() {
    val profiles by lazy { ProfileManager(this) }
    val periods get() = profiles.session.value.periods
    val settings get() = profiles.session.value.settings
    val days get() = profiles.session.value.days
    val journal get() = profiles.session.value.journal
    val backups get() = profiles.session.value.backups
}
