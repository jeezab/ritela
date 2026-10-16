package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.domain.DataShare
import app.ritela.domain.Doodle
import app.ritela.domain.JournalLayout
import app.ritela.domain.PartnerSharing
import app.ritela.domain.SavedDoodles
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PartnerScopeDialog(
    layout: JournalLayout,
    initial: ShareScope,
    dismiss: () -> Unit,
    recipient: String = "",
    targetIdentity: String? = null,
    targetDevice: String? = null,
    confirm: (DataShare) -> Unit
) {
    val session = LocalProfileSession.current ?: return
    val repository = session.partner
    val formatter = PartnerFormatter(LocalResources.current)
    val sections = layout.sections
    val maximum = ShareScope(
        setOf(
            ShareCategory.PERIODS,
            ShareCategory.FORECASTS,
            ShareCategory.SECTIONS
        ),
        sections.map { it.id }.toSet() + "note"
    )
    val permitted = PartnerSharing.explicit(maximum, layout)
    var selected by remember(initial) {
        mutableStateOf(PartnerSharing.current(initial, layout))
    }
    val chosen = PartnerSharing.current(selected, layout)
    var diaryExpanded by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }
    var attached by remember { mutableStateOf<Doodle?>(null) }
    var attachedId by remember { mutableStateOf<String?>(null) }
    var choosing by remember { mutableStateOf(false) }
    var drawing by remember { mutableStateOf(false) }
    var automatic by remember { mutableStateOf(false) }
    var internetReady by remember { mutableStateOf(false) }
    LaunchedEffect(targetIdentity, targetDevice) {
        internetReady = targetIdentity != null && targetDevice != null &&
            withContext(Dispatchers.IO) { session.relay.linked(targetIdentity, targetDevice) }
    }
    val library by session.savedDoodles.items.collectAsStateWithLifecycle()
    LaunchedEffect(chosen) {
        ready = false
        val data =
            withContext(Dispatchers.IO) {
                repository.preview(chosen)
            }
        summary = formatter.preview(data)
        ready = true
    }
    val height = with(LocalDensity.current) {
        LocalWindowInfo.current.containerSize.height.toDp() *
            .92f
    }
    JournalDialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().heightIn(max = height),
            color = HomeColors.top,
            shape = MaterialTheme.shapes.large
        ) {
            Column(Modifier.safeDrawingPadding().imePadding().padding(16.dp)) {
                Text(
                    stringResource(R.string.partner_selection),
                    style = MaterialTheme.typography.titleLarge
                )
                if (recipient.isNotBlank()) {
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(
                        stringResource(R.string.partner_preview_recipient, recipient)
                    )
                    androidx.compose.material3.HorizontalDivider(Modifier.padding(vertical = 8.dp))
                }
                Column(
                    Modifier.weight(
                        1f,
                        fill = false
                    ).verticalScroll(rememberScrollState()).testTag("partner-share-options"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        { choosing = true },
                        Modifier.fillMaxWidth().testTag("partner-attach-doodle")
                    ) { Text(stringResource(R.string.partner_attach_doodle)) }
                    attached?.let { doodle ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            DoodleThumbnail(doodle.strokes, Modifier.size(72.dp))
                            TextButton({
                                attached = null
                                attachedId = null
                            }) { Text(stringResource(R.string.partner_remove_doodle)) }
                        }
                    }
                    listOf(
                        Triple(
                            ShareCategory.PERIODS,
                            R.string.partner_cycle_category,
                            R.string.partner_periods_hint
                        ),
                        Triple(
                            ShareCategory.FORECASTS,
                            R.string.partner_forecast_category,
                            R.string.partner_forecasts_hint
                        )
                    ).forEach { (category, title, hint) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(category in selected.categories, { checked ->
                                selected =
                                    selected.copy(
                                        categories = if (checked) {
                                            selected.categories +
                                                category
                                        } else {
                                            selected.categories - category
                                        }
                                    )
                            }, enabled = category in maximum.categories)
                            Column(Modifier.weight(1f).padding(start = 8.dp)) {
                                Text(stringResource(title))
                                Text(
                                    stringResource(hint),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                    TextButton(
                        { diaryExpanded = !diaryExpanded },
                        Modifier.fillMaxWidth().testTag("partner-diary-fields")
                    ) {
                        Text(
                            stringResource(
                                R.string.partner_current_diary_fields,
                                chosen.tags.values.sumOf { it.size } + chosen.sections.size
                            )
                        )
                    }
                    if (diaryExpanded) {
                        Text(
                            stringResource(R.string.partner_current_diary_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                        sections.forEach { section ->
                            val fullSection = section.id in selected.sections
                            val tags = selected.tags[section.id].orEmpty()
                            val canFull = section.id in permitted.sections
                            val allSelected = fullSection || (
                                section.tags.isNotEmpty() &&
                                    tags.containsAll(section.tags.map { it.id })
                                )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TriStateCheckbox(
                                    if (fullSection ||
                                        (
                                            section.tags.isNotEmpty() &&
                                                tags.containsAll(section.tags.map { it.id })
                                            )
                                    ) {
                                        ToggleableState.On
                                    } else if (tags.isNotEmpty()) {
                                        ToggleableState.Indeterminate
                                    } else {
                                        ToggleableState.Off
                                    },
                                    {
                                        selected =
                                            if (canFull) {
                                                PartnerSharing.section(
                                                    selected,
                                                    section.id,
                                                    !allSelected
                                                )
                                            } else {
                                                PartnerSharing.diary(
                                                    selected,
                                                    selected.sections,
                                                    selected.tags + (
                                                        section.id to if (tags.isEmpty()) {
                                                            permitted.tags[section.id].orEmpty()
                                                        } else {
                                                            emptySet()
                                                        }
                                                        )
                                                )
                                            }
                                    },
                                    enabled =
                                        canFull || permitted.tags[section.id].orEmpty().isNotEmpty()
                                )
                                Text(journalSectionLabel(section), Modifier.weight(1f))
                            }
                            run {
                                section.tags.forEach { tag ->
                                    Row(
                                        Modifier.padding(start = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            fullSection || tag.id in tags,
                                            {
                                                selected =
                                                    PartnerSharing.tag(
                                                        selected,
                                                        section,
                                                        tag.id,
                                                        it
                                                    )
                                            },
                                            enabled =
                                                canFull ||
                                                    tag.id in permitted.tags[section.id].orEmpty()
                                        )
                                        Text(journalTagLabel(section, tag))
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox("note" in selected.sections, {
                                selected = PartnerSharing.section(selected, "note", it)
                            }, enabled = "note" in permitted.sections)
                            Text(stringResource(R.string.partner_note_category))
                        }
                    }
                    OutlinedTextField(
                        note,
                        { if (it.length <= SavedDoodles.NOTE_LIMIT) note = it },
                        Modifier.fillMaxWidth().testTag("partner-share-note"),
                        label = { Text(stringResource(R.string.partner_share_note)) }
                    )
                    run {
                        Text(
                            stringResource(R.string.partner_preview),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(summary)
                    }
                    if (internetReady) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                automatic,
                                {
                                    automatic = it
                                },
                                Modifier.testTag("sync-auto-enable"),
                                enabled = selected.categories.isNotEmpty()
                            )
                            Text(stringResource(R.string.sync_auto_enable), Modifier.weight(1f))
                        }
                        Text(
                            stringResource(R.string.sync_auto_consent),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(dismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        {
                            confirm(
                                DataShare(
                                    chosen,
                                    note,
                                    attached,
                                    attachedId,
                                    automatic && selected.categories.isNotEmpty()
                                )
                            )
                        },
                        enabled =
                            ready && maximum.permits(selected) && selected.categories.isNotEmpty()
                    ) {
                        Text(stringResource(R.string.partner_prepare))
                    }
                }
            }
        }
    }
    if (choosing) {
        SavedDoodleChooser(library, { choosing = false }, { item ->
            attached = item.doodle
            attachedId = item.id
            choosing = false
        }) { drawing = true }
    }
    if (drawing) {
        DoodleEditorWithLibrary({ drawing = false }, { doodle ->
            attached = doodle
            attachedId = null
            drawing = false
            choosing = false
        }) { drawing = false }
    }
}
