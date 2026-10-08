package app.ritela.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

@Composable
fun SettingsGroup(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, HomeColors.border.copy(alpha = 0.6f)),
        colors = CardDefaults.cardColors(containerColor = HomeColors.card.copy(alpha = 0.5f))
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
fun SettingsRow(
    label: String,
    icon: Int,
    modifier: Modifier = Modifier,
    value: String? = null,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = androidx.compose.ui.graphics.Color.Transparent,
        modifier = modifier.fillMaxWidth().heightIn(min = HomeSpacing.action),
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            Modifier.padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = CircleShape, color = HomeColors.border.copy(alpha = 0.3f)) {
                Icon(
                    painterResource(icon),
                    null,
                    Modifier.padding(9.dp).size(18.dp),
                    tint = HomeColors.peach
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                value?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HomeColors.muted)
                }
            }
            Text(
                "›",
                modifier = Modifier.clearAndSetSemantics {},
                style = MaterialTheme.typography.titleLarge,
                color = HomeColors.muted
            )
        }
    }
}
