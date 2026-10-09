package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import kotlinx.coroutines.launch

@Composable
fun PartnerSettings() {
    val session = LocalProfileSession.current ?: return
    val mode by session.partnerPreferences.mode.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SettingsGroup {
        Text(stringResource(R.string.partner_mode), style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.partner_show), Modifier.weight(1f))
            Switch(mode.show, { scope.launch { session.partnerPreferences.show(it) } })
        }
        TextButton(onClick = {
            scope.launch { session.partnerPreferences.recipient(!mode.recipient) }
        }) {
            Text(
                stringResource(
                    if (mode.recipient) R.string.partner_normal else R.string.partner_login
                )
            )
        }
        Text(stringResource(R.string.partner_private), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PartnerScreen(padding: PaddingValues) {
    HomeTheme {
        Column(
            Modifier.fillMaxSize().padding(padding).padding(HomeSpacing.gutter),
            verticalArrangement = Arrangement.spacedBy(HomeSpacing.gap)
        ) {
            Text(
                stringResource(R.string.partner_title),
                style = MaterialTheme.typography.headlineMedium
            )
            Text(stringResource(R.string.partner_empty))
        }
    }
}
