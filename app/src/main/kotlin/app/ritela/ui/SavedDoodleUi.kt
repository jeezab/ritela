package app.ritela.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.domain.Doodle
import app.ritela.domain.SavedDoodle
import kotlinx.coroutines.launch

@Composable
internal fun DoodleEditorWithLibrary(
    dismiss: () -> Unit,
    send: (Doodle) -> Unit,
    onSaved: () -> Unit = {}
) {
    val session = LocalProfileSession.current ?: return
    val scope = rememberCoroutineScope()
    var overflow by remember { mutableStateOf<Doodle?>(null) }
    val library by session.savedDoodles.items.collectAsStateWithLifecycle()
    DoodleEditor(dismiss, send) { doodle ->
        scope.launch {
            if (session.savedDoodles.save(doodle)) {
                onSaved()
            } else {
                overflow = doodle
            }
        }
    }
    overflow?.let { pending ->
        SavedDoodleGallery(library, { overflow = null }) { id ->
            scope.launch {
                session.savedDoodles.delete(id)
                if (session.savedDoodles.save(pending)) {
                    overflow = null
                    onSaved()
                }
            }
        }
    }
}

@Composable
internal fun SavedDoodleChooser(
    items: List<SavedDoodle>,
    dismiss: () -> Unit,
    select: (SavedDoodle) -> Unit,
    draw: () -> Unit
) {
    val label = stringResource(R.string.partner_doodle)
    PartnerModal(stringResource(R.string.partner_saved_doodles), dismiss) {
        if (items.isEmpty()) {
            Text(stringResource(R.string.partner_no_saved_doodles))
        }
        LazyRow(
            Modifier.fillMaxWidth().height(112.dp).testTag("partner-saved-doodles"),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items, key = { it.id }) { item ->
                DoodleThumbnail(
                    item.doodle.strokes,
                    Modifier.size(96.dp).testTag("saved-doodle-${item.id}").semantics {
                        contentDescription = label
                    }.clickable { select(item) }
                )
            }
        }
        Button(draw, Modifier.testTag("partner-draw-doodle")) {
            Text(stringResource(R.string.partner_draw_doodle))
        }
    }
}

@Composable
internal fun SavedDoodleGallery(
    items: List<SavedDoodle>,
    dismiss: () -> Unit,
    delete: (String) -> Unit
) {
    var pending by remember { mutableStateOf<String?>(null) }
    JournalDialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(16.dp),
            shape = MaterialTheme.shapes.large,
            color = HomeColors.card
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    stringResource(R.string.partner_saved_doodles),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(stringResource(R.string.partner_doodle_full))
                LazyVerticalGrid(
                    GridCells.Adaptive(108.dp),
                    Modifier.fillMaxWidth().height(360.dp).testTag("partner-doodle-gallery"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    gridItems(items, key = { it.id }) { item ->
                        Box {
                            DoodleThumbnail(
                                item.doodle.strokes,
                                Modifier.fillMaxWidth().aspectRatio(1f)
                            )
                            IconButton(
                                { pending = item.id },
                                Modifier.align(Alignment.TopEnd).size(32.dp)
                                    .testTag("partner-remove-doodle-${item.id}")
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_minus),
                                    stringResource(R.string.partner_delete_doodle),
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                TextButton(dismiss) { Text(stringResource(R.string.partner_close)) }
            }
        }
    }
    pending?.let { id ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.partner_delete_doodle)) },
            text = { Text(stringResource(R.string.partner_delete_doodle_confirm)) },
            confirmButton = {
                TextButton({
                    delete(id)
                    pending = null
                }, Modifier.testTag("partner-confirm-delete-doodle")) {
                    Text(stringResource(R.string.delete))
                }
            },
            dismissButton = {
                TextButton({ pending = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}
