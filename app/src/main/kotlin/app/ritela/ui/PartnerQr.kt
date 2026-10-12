package app.ritela.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import app.ritela.R

@Composable
internal fun PartnerQr(bitmap: Bitmap) {
    var enlarged by remember(bitmap) { mutableStateOf(false) }
    Image(
        bitmap.asImageBitmap(),
        stringResource(R.string.partner_invite),
        Modifier.fillMaxWidth().clickable { enlarged = true }.testTag("partner-qr")
    )
    if (enlarged) {
        JournalDialog({ enlarged = false }, DialogProperties(usePlatformDefaultWidth = false)) {
            Column(
                Modifier.fillMaxSize().background(Color.White).safeDrawingPadding().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    bitmap.asImageBitmap(),
                    stringResource(R.string.partner_invite),
                    Modifier.fillMaxWidth().weight(1f).testTag("partner-qr-fullscreen")
                )
                TextButton({ enlarged = false }) { Text(stringResource(R.string.partner_close)) }
            }
        }
    }
}
