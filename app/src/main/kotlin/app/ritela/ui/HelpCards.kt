package app.ritela.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.DayLog
import app.ritela.domain.FlowLevel
import app.ritela.domain.ForecastUnavailable
import app.ritela.domain.Pain
import app.ritela.domain.Sex
import java.time.LocalDate

data class HelpArticle(
    val id: String,
    val title: Int,
    val body: Int,
    val url: String,
    val dose: Int? = null
)

val HelpLibrary = listOf(
    HelpArticle(
        "warmth",
        R.string.help_warmth_title,
        R.string.help_warmth_body,
        "https://www.nhs.uk/symptoms/period-pain/"
    ),
    HelpArticle(
        "movement",
        R.string.help_movement_title,
        R.string.help_movement_body,
        "https://www.nhs.uk/symptoms/period-pain/"
    ),
    HelpArticle(
        "pain-care",
        R.string.help_pain_care_title,
        R.string.help_pain_care_body,
        "https://www.nhs.uk/symptoms/period-pain/"
    ),
    HelpArticle(
        "pain-urgent",
        R.string.help_pain_urgent_title,
        R.string.help_pain_urgent_body,
        "https://www.nhs.uk/symptoms/period-pain/"
    ),
    HelpArticle(
        "headache",
        R.string.help_headache_title,
        R.string.help_headache_body,
        "https://www.nhs.uk/symptoms/headaches/"
    ),
    HelpArticle(
        "headache-urgent",
        R.string.help_headache_urgent_title,
        R.string.help_headache_urgent_body,
        "https://www.nhs.uk/symptoms/headaches/"
    ),
    HelpArticle(
        "headache-diary",
        R.string.help_headache_diary_title,
        R.string.help_headache_diary_body,
        "https://www.nhs.uk/symptoms/headaches/"
    ),
    HelpArticle(
        "ibuprofen",
        R.string.help_ibuprofen_title,
        R.string.help_ibuprofen_body,
        "https://www.nhs.uk/medicines/ibuprofen-for-adults/",
        R.string.help_ibuprofen_dose
    ),
    HelpArticle(
        "paracetamol",
        R.string.help_paracetamol_title,
        R.string.help_paracetamol_body,
        "https://www.nhs.uk/medicines/paracetamol-for-adults/",
        R.string.help_paracetamol_dose
    ),
    HelpArticle(
        "heavy",
        R.string.help_heavy_title,
        R.string.help_heavy_body,
        "https://www.nhs.uk/conditions/heavy-periods/"
    ),
    HelpArticle(
        "bleeding-care",
        R.string.help_bleeding_care_title,
        R.string.help_bleeding_care_body,
        "https://www.nhs.uk/conditions/heavy-periods/"
    ),
    HelpArticle(
        "pms",
        R.string.help_pms_title,
        R.string.help_pms_body,
        "https://www.nhs.uk/conditions/pre-menstrual-syndrome/"
    ),
    HelpArticle(
        "pms-diary",
        R.string.help_pms_diary_title,
        R.string.help_pms_diary_body,
        "https://www.nhs.uk/conditions/pre-menstrual-syndrome/"
    ),
    HelpArticle(
        "supplements",
        R.string.help_supplements_title,
        R.string.help_supplements_body,
        "https://www.nhs.uk/conditions/pre-menstrual-syndrome/"
    ),
    HelpArticle(
        "emergency",
        R.string.help_emergency_title,
        R.string.help_emergency_body,
        "https://www.nhs.uk/contraception/emergency-contraception/"
    ),
    HelpArticle(
        "condoms",
        R.string.help_condoms_title,
        R.string.help_condoms_body,
        "https://www.nhs.uk/contraception/methods-of-contraception/condoms/"
    ),
    HelpArticle(
        "test",
        R.string.help_test_title,
        R.string.help_test_body,
        "https://www.nhs.uk/pregnancy/trying-for-a-baby/doing-a-pregnancy-test/"
    ),
    HelpArticle(
        "late",
        R.string.help_late_title,
        R.string.help_late_body,
        "https://www.nhs.uk/symptoms/missed-or-late-periods/"
    )
)

fun relevantArticles(log: DayLog?, analysis: CycleAnalysis): List<HelpArticle> {
    val priority = buildList {
        if (log?.headache == Pain.SEVERE) add("headache-urgent")
        if (log?.cramps == Pain.SEVERE || log?.backache == Pain.SEVERE) add("pain-urgent")
        if (log?.flow == FlowLevel.HEAVY) add("heavy")
        if (log?.sex?.contains(Sex.NO_BARRIER) == true) add("emergency")
        if (analysis.unavailable == ForecastUnavailable.PAST_DUE) {
            add("test")
            add("late")
        }
        if (log?.headache != null &&
            log.headache != Pain.NONE
        ) {
            add("headache")
            add("headache-diary")
        }
        if (log?.cramps != null && log.cramps != Pain.NONE) {
            add("warmth")
            add("movement")
        }
        if (log?.mood != null) add("pms-diary")
        add("warmth")
        add("pms")
        add("condoms")
    }.distinct()
    return priority.map { id -> HelpLibrary.first { it.id == id } }.take(4)
}

@Composable
fun HelpCards(log: DayLog?, analysis: CycleAnalysis, date: LocalDate) {
    var libraryOpen by remember { mutableStateOf(false) }
    var article by remember { mutableStateOf<HelpArticle?>(null) }
    Column(
        modifier = Modifier.testTag("help-cards"),
        verticalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.help_title), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { libraryOpen = true }, modifier = Modifier.testTag("help-all")) {
                Text(stringResource(R.string.help_all))
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            items(relevantArticles(log, analysis), key = { it.id }) { item ->
                ArticleTile(item, { article = item }, Modifier.width(260.dp))
            }
        }
    }
    if (libraryOpen) {
        AlertDialog(
            onDismissRequest = { libraryOpen = false },
            title = { Text(stringResource(R.string.help_all)) },
            text = {
                LazyColumn(
                    modifier = Modifier.testTag("help-library"),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    items(HelpLibrary, key = { it.id }) { item ->
                        ArticleTile(item, { article = item }, Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    libraryOpen = false
                }) { Text(stringResource(R.string.done)) }
            }
        )
    }
    article?.let { item ->
        HelpArticleDialog(item, date, onDismiss = { article = null })
    }
}

@Composable
private fun ArticleTile(article: HelpArticle, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag("help-${article.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    ) {
        Column(
            Modifier.padding(Spacing.medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.small)
        ) {
            Icon(
                painterResource(
                    if (article.dose !=
                        null
                    ) {
                        R.drawable.ic_shield
                    } else {
                        R.drawable.ic_heart
                    }
                ),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary
            )
            Text(stringResource(article.title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(article.body),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "NHS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HelpArticleDialog(article: HelpArticle, date: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var acknowledged by remember(article.id) { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = {
        Text(stringResource(article.title))
    }, text = {
        Column(
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.medium)
        ) {
            Text(formattedDate(date), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(article.body))
            article.dose?.let { dose ->
                Text(stringResource(R.string.medicine_intro))
                Row {
                    Checkbox(
                        checked = acknowledged,
                        onCheckedChange = { acknowledged = it },
                        modifier = Modifier.testTag("medicine-ack")
                    )
                    Text(
                        stringResource(R.string.medicine_ack),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (acknowledged) {
                    Text(
                        stringResource(dose),
                        modifier = Modifier.testTag("medicine-dose")
                    )
                }
            }
            Text(stringResource(R.string.help_reviewed), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, article.url.toUri()))
            }) {
                Text(stringResource(R.string.help_source, "NHS"))
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}
