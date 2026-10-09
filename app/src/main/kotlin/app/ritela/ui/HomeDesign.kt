package app.ritela.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.ritela.R
import app.ritela.domain.CycleAnalysis
import app.ritela.domain.JournalLayout
import app.ritela.domain.forSelection

/** Shared screen tokens: Home is the source of truth for Calendar and Settings. */
object HomeColors {
    val top = Color(0xFF523243)
    val bottom = Color(0xFF3C2533)
    val card = Color(0xFF603D50)
    val border = Color(0xFF986A80)
    val text = Color(0xFFFFF4ED)
    val muted = Color(0xFFE1C8D2)
    val rose = Color(0xFFDB8298)
    val pink = Color(0xFFF0A0AD)
    val peach = Color(0xFFF8CFA5)
    val mauve = Color(0xFFE6D4EE)
    val blush = Color(0xFFF4D2DA)
    val orbit = Color(0xFFD3A3B5)
    val background = Brush.verticalGradient(listOf(top, bottom))
    val action = Brush.horizontalGradient(listOf(rose, pink))
}

object HomeSpacing {
    val gutter = 20.dp
    val gap = 12.dp
    val orbit = 228.dp
    val compactOrbit = 184.dp
    val action = 56.dp
}

@Composable
fun HomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RitelaColors.dark.copy(
            background = HomeColors.bottom, surface = HomeColors.bottom,
            surfaceContainer = HomeColors.card, surfaceContainerLow = HomeColors.card,
            surfaceContainerHigh = HomeColors.card,
            primary = HomeColors.text, onPrimary = HomeColors.bottom,
            secondary = HomeColors.rose, onSecondary = HomeColors.bottom,
            primaryContainer = HomeColors.card, onPrimaryContainer = HomeColors.text,
            secondaryContainer = HomeColors.card, onSecondaryContainer = HomeColors.text,
            onSurface = HomeColors.text, onSurfaceVariant = HomeColors.muted,
            outline = HomeColors.border, outlineVariant = HomeColors.border.copy(alpha = 0.5f)
        ),
        typography = RitelaTypography.copy(
            headlineLarge = RitelaTypography.headlineLarge.copy(fontFamily = FontFamily.Serif),
            headlineMedium = RitelaTypography.headlineMedium.copy(fontFamily = FontFamily.Serif),
            titleLarge = RitelaTypography.titleLarge.copy(fontFamily = FontFamily.Serif)
        ),
        content = {
            CompositionLocalProvider(LocalContentColor provides HomeColors.text, content = content)
        }
    )
}

@Composable
fun HomeInsightTiles(analysis: CycleAnalysis) {
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("home-insight-tiles"),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val tiles = listOf(
            Triple(
                R.drawable.ic_chart,
                R.string.home_cycle_tile,
                pluralStringResource(
                    R.plurals.days_value,
                    analysis.forecasts.first().cycleMedian,
                    analysis.forecasts.first().cycleMedian
                )
            ),
            Triple(
                R.drawable.ic_calendar,
                R.string.home_duration_tile,
                pluralStringResource(
                    R.plurals.days_value,
                    analysis.periodDuration,
                    analysis.periodDuration
                )
            )
        )
        tiles.forEach { (icon, label, value) ->
            Card(
                Modifier.weight(1f).fillMaxHeight().testTag(
                    if (label == R.string.home_cycle_tile) {
                        "home-insight-cycle"
                    } else {
                        "home-insight-duration"
                    }
                ),
                shape = MaterialTheme.shapes.large,
                border = BorderStroke(1.dp, HomeColors.border.copy(alpha = 0.6f)),
                colors = CardDefaults.cardColors(
                    containerColor = HomeColors.card.copy(alpha = 0.5f)
                )
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(18.dp), tint = HomeColors.peach)
                    Text(stringResource(label), style = MaterialTheme.typography.bodySmall)
                    Text(
                        value,
                        style = MaterialTheme.typography.labelSmall,
                        color = HomeColors.muted,
                        modifier = if (label == R.string.home_duration_tile) {
                            Modifier.testTag("expected-period-duration")
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun HomeTodayActions(layout: JournalLayout, enabled: Boolean, onSelect: (String) -> Unit) {
    val ordered = listOf("mood", "discharge", "sex").mapNotNull { id ->
        layout.sections.firstOrNull { it.id == id }
    }
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
    ) {
        ordered.forEachIndexed { index, section ->
            TodayAction(
                if (section.title.isNotBlank()) {
                    section.title
                } else {
                    when (section.id) {
                        "headache" -> stringResource(R.string.home_symptoms)
                        "sex" -> stringResource(R.string.home_intimacy)
                        else -> journalSectionLabel(section)
                    }
                },
                journalIcon(section.forSelection().icon),
                "quick-${section.id}",
                listOf(
                    HomeColors.peach,
                    HomeColors.mauve,
                    HomeColors.mauve,
                    HomeColors.blush
                )[index],
                enabled,
                { onSelect(section.id) },
                Modifier.weight(1f)
            )
        }
        TodayAction(
            stringResource(R.string.day_note),
            R.drawable.ic_note,
            "quick-note",
            HomeColors.blush,
            enabled,
            { onSelect("note") },
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun TodayAction(
    label: String,
    icon: Int,
    tag: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = modifier.heightIn(min = 64.dp).testTag(tag)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(shape = CircleShape, color = color) {
                Icon(
                    painterResource(icon),
                    null,
                    Modifier.padding(12.dp).size(20.dp),
                    tint = HomeColors.bottom
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = HomeColors.muted,
                textAlign = TextAlign.Center
            )
        }
    }
}
