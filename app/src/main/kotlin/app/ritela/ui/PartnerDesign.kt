package app.ritela.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.ritela.R

@Composable
internal fun PartnerWelcome(add: () -> Unit) {
    SettingsGroup {
        Canvas(Modifier.size(96.dp, 64.dp)) {
            fun heart(x: Float, y: Float, scale: Float, color: androidx.compose.ui.graphics.Color) {
                val p = Path().apply {
                    moveTo(x, y + scale * .9f)
                    cubicTo(x - scale, y + scale * .3f, x - scale * .8f, y - scale * .5f, x, y)
                    cubicTo(
                        x + scale * .8f,
                        y - scale * .5f,
                        x + scale,
                        y + scale * .3f,
                        x,
                        y + scale * .9f
                    )
                    close()
                }
                drawPath(p, color)
            }
            heart(size.width * .35f, size.height * .28f, size.height * .5f, HomeColors.rose)
            heart(size.width * .72f, size.height * .45f, size.height * .32f, HomeColors.blush)
        }
        Text(
            stringResource(R.string.partner_welcome_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(stringResource(R.string.partner_welcome_body), color = HomeColors.muted)
        Button(add, Modifier.fillMaxWidth()) { Text(stringResource(R.string.partner_add_close)) }
    }
}

@Composable
internal fun PartnerConnectionCard(enabled: Boolean, invite: () -> Unit, scan: () -> Unit) {
    SettingsGroup {
        Text(
            stringResource(R.string.partner_add_close),
            style = MaterialTheme.typography.titleMedium
        )
        PartnerCardActions(
            stringResource(R.string.partner_invite),
            stringResource(R.string.partner_scan),
            enabled,
            invite,
            scan
        )
        Text(
            stringResource(R.string.partner_empty),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
    }
}

@Composable
internal fun PartnerReceiveCard(enabled: Boolean, openFile: () -> Unit, bluetooth: () -> Unit) {
    SettingsGroup {
        Text(
            stringResource(R.string.partner_exchange_card),
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            stringResource(R.string.partner_exchange_hint),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
        PartnerCardActions(
            stringResource(R.string.partner_import),
            stringResource(R.string.partner_receive),
            enabled,
            openFile,
            bluetooth
        )
        Text(
            stringResource(R.string.partner_manual_exchange),
            style = MaterialTheme.typography.bodySmall,
            color = HomeColors.muted
        )
    }
}

@Composable
private fun PartnerCardActions(
    first: String,
    second: String,
    enabled: Boolean,
    firstAction: () -> Unit,
    secondAction: () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(firstAction, enabled = enabled) { Text(first) }
        Button(secondAction, enabled = enabled) { Text(second) }
    }
}

@Composable
internal fun PartnerActionCard(
    title: String,
    description: String,
    enabled: Boolean = true,
    action: () -> Unit
) {
    Surface(
        onClick = action,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = HomeColors.card,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            HomeColors.border.copy(alpha = .45f)
        )
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (enabled) HomeColors.text else HomeColors.muted
            )
            Text(description, style = MaterialTheme.typography.bodySmall, color = HomeColors.muted)
        }
    }
}

@Composable
internal fun PartnerContactHeading(name: String, status: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            Modifier.size(48.dp).background(HomeColors.rose.copy(alpha = .25f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(name.take(1), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(status, color = HomeColors.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
internal fun PartnerVerificationContent(
    name: String,
    code: String,
    identity: String,
    enabled: Boolean,
    confirm: () -> Unit,
    reject: () -> Unit
) {
    var technical by remember { mutableStateOf(false) }
    Text(name, style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.partner_code_label))
    Text(
        code,
        style = MaterialTheme.typography.headlineLarge,
        modifier = Modifier.testTag("partner-verification-code")
    )
    Text(stringResource(R.string.partner_verify))
    Button(confirm, enabled = enabled, modifier = Modifier.testTag("partner-confirm-code")) {
        Text(stringResource(R.string.partner_verified))
    }
    TextButton(reject, Modifier.testTag("partner-reject-code")) {
        Text(stringResource(R.string.partner_codes_differ))
    }
    TextButton({ technical = !technical }) { Text(stringResource(R.string.partner_technical)) }
    if (technical) {
        Text(
            identity.chunked(8).joinToString(" "),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Preview(name = "Partner welcome", locale = "ru", widthDp = 360)
@Composable
private fun PartnerWelcomePreview() {
    HomeTheme { PartnerWelcome {} }
}

@Preview(name = "Partner connected EN", locale = "en", widthDp = 320, fontScale = 2f)
@Composable
private fun PartnerConnectedPreview() {
    HomeTheme {
        SettingsGroup {
            PartnerContactHeading("Alex", stringResource(R.string.partner_linked))
            PartnerActionCard(
                stringResource(R.string.partner_doodle),
                stringResource(R.string.partner_doodle_hint)
            ) {
            }
        }
    }
}

@Preview(name = "Partner verification", locale = "ru", widthDp = 320)
@Composable
private fun PartnerVerificationPreview() {
    HomeTheme {
        SettingsGroup {
            PartnerVerificationContent("Alex", "4821 \u00b7 7053", "a".repeat(64), true, {}, {})
        }
    }
}
