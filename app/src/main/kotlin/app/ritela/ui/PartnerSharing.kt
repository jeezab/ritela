package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ritela.R
import app.ritela.domain.JournalLayout
import app.ritela.domain.PartnerSharing
import app.ritela.domain.ShareCategory
import app.ritela.domain.ShareScope
import app.ritela.domain.historySections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun PartnerScopeDialog(
    layout: JournalLayout,
    allowed: ShareScope?,
    initial: ShareScope,
    granting: Boolean,
    dismiss: () -> Unit,
    recipient: String = "",
    confirm: (ShareScope) -> Unit
) {
    val repository = LocalProfileSession.current?.partner ?: return
    val formatter = PartnerFormatter(LocalResources.current)
    val sections = layout.historySections()
    val maximum = allowed ?: ShareScope(
        setOf(
            ShareCategory.PERIODS,
            ShareCategory.FORECASTS,
            ShareCategory.SETTINGS,
            ShareCategory.SECTIONS
        ),
        sections.map { it.id }.toSet() + "note"
    )
    val permitted = PartnerSharing.explicit(maximum, layout)
    var selected by remember(initial, allowed) {
        mutableStateOf(PartnerSharing.explicit(initial, layout))
    }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    var summary by remember { mutableStateOf("") }
    var ready by remember { mutableStateOf(false) }
    LaunchedEffect(selected) {
        ready = false
        val data = withContext(Dispatchers.IO) { repository.preview(selected) }
        summary = formatter.preview(data)
        ready = true
    }
    fun toggle(id: String) {
        expanded = if (id in expanded) expanded - id else expanded + id
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
                    stringResource(
                        if (granting) R.string.partner_access else R.string.partner_selection
                    ),
                    style = MaterialTheme.typography.titleLarge
                )
                if (recipient.isNotBlank()) {
                    Text(
                        stringResource(R.string.partner_preview_recipient, recipient)
                    )
                }
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FlowRow {
                        TextButton({
                            selected = permitted
                        }) { Text(stringResource(R.string.partner_everything)) }
                        TextButton({
                            selected = ShareScope()
                        }) { Text(stringResource(R.string.partner_clear_selection)) }
                    }
                    if (!granting) {
                        FlowRow {
                            TextButton({
                                selected =
                                    ShareScope(
                                        setOf(ShareCategory.PERIODS).intersect(maximum.categories)
                                    )
                            }) {
                                Text(stringResource(R.string.partner_cycle_only))
                            }
                            TextButton({
                                selected =
                                    ShareScope(
                                        setOf(ShareCategory.PERIODS, ShareCategory.FORECASTS)
                                            .intersect(maximum.categories)
                                    )
                            }) { Text(stringResource(R.string.partner_cycle_forecasts)) }
                            TextButton({
                                expanded = expanded + "diary"
                            }) { Text(stringResource(R.string.partner_manual_selection)) }
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
                        ),
                        Triple(
                            ShareCategory.SETTINGS,
                            R.string.partner_app_settings,
                            R.string.partner_settings_hint
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
                    val diary = selected.sections.isNotEmpty() || selected.tags.isNotEmpty()
                    val full =
                        permitted.sections.isNotEmpty() &&
                            selected.sections.containsAll(permitted.sections) &&
                            selected.tags == permitted.tags
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TriStateCheckbox(
                            if (full) {
                                ToggleableState.On
                            } else if (diary) {
                                ToggleableState.Indeterminate
                            } else {
                                ToggleableState.Off
                            },
                            {
                                selected =
                                    if (diary) {
                                        PartnerSharing.diary(selected, emptySet(), emptyMap())
                                    } else {
                                        PartnerSharing.defaultDiary(selected, permitted)
                                    }
                            },
                            enabled = permitted.sections.isNotEmpty() || permitted.tags.isNotEmpty()
                        )
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.partner_diary))
                            Text(
                                stringResource(R.string.partner_diary_hint),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        TextButton({
                            toggle("diary")
                        }) { Text(stringResource(R.string.partner_category_details)) }
                    }
                    if ("diary" in expanded) {
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
                                TextButton({
                                    toggle(section.id)
                                }) { Text(stringResource(R.string.partner_category_details)) }
                            }
                            if (section.id in expanded) {
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
                    if (!granting) {
                        Text(
                            stringResource(R.string.partner_preview),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(summary)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(dismiss) { Text(stringResource(R.string.cancel)) }
                    Button(
                        { confirm(selected.normalized()) },
                        enabled =
                            ready && maximum.permits(selected) &&
                                (granting || selected.categories.isNotEmpty())
                    ) {
                        Text(
                            stringResource(
                                if (granting) R.string.save else R.string.partner_prepare
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PartnerAccessSettings() {
    val session = LocalProfileSession.current ?: return
    val model = session.partnerModel
    val directory by model.directory.collectAsStateWithLifecycle()
    val state by session.model.uiState.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    TextButton({ choosing = true }) { Text(stringResource(R.string.partner_access)) }
    if (choosing &&
        selected == null
    ) {
        PartnerModal(stringResource(R.string.partner_access), { choosing = false }) {
            if (directory.identities.isEmpty()) Text(stringResource(R.string.partner_no_access))
            directory.identities.forEach { source ->
                TextButton(
                    { selected = source.id },
                    enabled = source.devices.any {
                        it.enabled &&
                            it.mutualConfirmed
                    }
                ) {
                    Text(source.name)
                }
            }
        }
    }
    directory.identities.firstOrNull { it.id == selected }?.let { source ->
        PartnerScopeDialog(
            state.journalLayout,
            null,
            source.grants,
            true,
            {
                selected = null
                choosing = false
            },
            source.name,
            {
                model.run { model.repository.grant(source.id, it) }
                selected = null
                choosing = false
            }
        )
    }
}
