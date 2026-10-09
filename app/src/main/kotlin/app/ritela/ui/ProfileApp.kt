package app.ritela.ui

import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.RitelaApplication
import app.ritela.data.ProfileSession
import java.util.Locale

val LocalProfileSession = staticCompositionLocalOf<ProfileSession?> { null }

@Composable
fun ProfileApp() {
    val context = LocalContext.current
    val manager = (context.applicationContext as RitelaApplication).profiles
    val session by manager.session.collectAsStateWithLifecycle()
    val ready by session.settings.ready.collectAsStateWithLifecycle()
    val failed by session.settings.failed.collectAsStateWithLifecycle()
    val language by session.settings.language.collectAsStateWithLifecycle()
    val original = LocalConfiguration.current
    val configured = remember(context, original, language) {
        val locales = if (language == "system") {
            Resources.getSystem().configuration.locales
        } else {
            LocaleList(Locale.forLanguageTag(language))
        }
        android.view.ContextThemeWrapper(context, 0).apply {
            applyOverrideConfiguration(Configuration(original).apply { setLocales(locales) })
        }
    }
    key(session.token) {
        CompositionLocalProvider(
            LocalProfileSession provides session,
            LocalContext provides configured,
            LocalConfiguration provides configured.resources.configuration,
            LocalResources provides configured.resources
        ) {
            if (failed) {
                Text(androidx.compose.ui.res.stringResource(app.ritela.R.string.storage_error))
            } else if (!ready) {
                Text(androidx.compose.ui.res.stringResource(app.ritela.R.string.loading))
            } else {
                RitelaApp(session.model)
            }
        }
    }
}
