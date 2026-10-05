package app.ritela.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import app.ritela.R

@Composable
fun RitelaApp() {
    RitelaTheme {
        Scaffold { contentPadding -> HomeScreen(contentPadding) }
    }
}

@Composable
fun HomeScreen(contentPadding: PaddingValues = PaddingValues()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.section)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.small)) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                stringResource(R.string.home_title),
                style = MaterialTheme.typography.headlineLarge
            )
            Text(
                stringResource(R.string.home_subtitle),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
            Text(stringResource(R.string.cycle_title), style = MaterialTheme.typography.titleLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(Spacing.large),
                    verticalArrangement = Arrangement.spacedBy(Spacing.small)
                ) {
                    Text(
                        stringResource(R.string.empty_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(R.string.empty_description),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.large),
                verticalArrangement = Arrangement.spacedBy(Spacing.small)
            ) {
                Text(
                    stringResource(R.string.privacy_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(stringResource(R.string.privacy_description))
                Text(
                    stringResource(R.string.privacy_note),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HomePreview() {
    RitelaTheme(dynamicColor = false) { HomeScreen() }
}
