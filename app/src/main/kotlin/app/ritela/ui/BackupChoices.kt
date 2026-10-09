package app.ritela.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import app.ritela.R
import app.ritela.data.BackupCategory
import app.ritela.data.BackupData
import app.ritela.data.BackupSelection

@Composable
fun BackupChoices(
    exporting: Boolean,
    user: String,
    available: Set<BackupCategory>,
    data: BackupData? = null,
    busy: Boolean = false,
    error: Int? = null,
    onDismiss: () -> Unit,
    onConfirm: (BackupSelection) -> Unit
) {
    var selected by rememberSaveable { mutableStateOf(available.map { it.name }.toTypedArray()) }
    fun toggle(category: BackupCategory, checked: Boolean) {
        selected = if (checked) {
            (selected.toList() + category.name).distinct().toTypedArray()
        } else {
            selected.filterNot { it == category.name }.toTypedArray()
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = Modifier.testTag("backup-choices"),
        title = {
            Text(stringResource(if (exporting) R.string.backup_export else R.string.backup_import))
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.backup_user, user))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(selected.size == available.size, {
                        selected =
                            if (it) {
                                available.map { value ->
                                    value.name
                                }.toTypedArray()
                            } else {
                                emptyArray()
                            }
                    }, enabled = !busy, modifier = Modifier.testTag("backup-choice-all"))
                    Text(stringResource(R.string.backup_all))
                }
                BackupCategory.entries.filter { it in available }.forEach { category ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            category.name in selected,
                            {
                                toggle(category, it)
                            },
                            enabled = !busy,
                            modifier = Modifier.testTag("backup-choice-${category.name}")
                        )
                        Text(
                            stringResource(
                                when (category) {
                                    BackupCategory.PERIODS -> R.string.backup_category_periods
                                    BackupCategory.DAYS -> R.string.backup_category_days
                                    BackupCategory.JOURNAL -> R.string.backup_category_journal
                                    BackupCategory.SETTINGS -> R.string.backup_category_settings
                                }
                            )
                        )
                    }
                }
                if (data != null) {
                    Text(stringResource(R.string.backup_preview, data.periods.size, data.logs.size))
                    Text(stringResource(R.string.backup_merge_hint))
                    if (BackupCategory.JOURNAL.name in selected ||
                        BackupCategory.SETTINGS.name in selected
                    ) {
                        Text(stringResource(R.string.backup_settings_replace))
                    }
                }
                error?.let { Text(stringResource(it)) }
            }
        },
        confirmButton = {
            TextButton(
                {
                    onConfirm(BackupSelection(selected.map(BackupCategory::valueOf).toSet()))
                },
                enabled = !busy && selected.isNotEmpty(),
                modifier = Modifier.testTag("backup-confirm")
            ) {
                Text(stringResource(if (exporting) R.string.done else R.string.backup_confirm))
            }
        },
        dismissButton = {
            TextButton(onDismiss, enabled = !busy) { Text(stringResource(R.string.cancel)) }
        }
    )
}
