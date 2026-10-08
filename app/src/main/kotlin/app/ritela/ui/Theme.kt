package app.ritela.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

object Spacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val section = 32.dp
    val actionHeight = 56.dp
    val calendarMinimumWidth = 336.dp
    val calendarCellHeight = 64.dp
    val calendarBorder = 2.dp
    val heroOrbitHeight = 132.dp
    val heroCompactOrbitHeight = 112.dp
    val articleWidth = 228.dp
    val tileWidth = 140.dp
}

object CalendarColors {
    val period = Color(0xFFD38A92)
    val estimatedPeriod = Color(0xFFF5D8DC)
    val ink = Color(0xFF352E32)
}

val RitelaShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

val RitelaTypography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = FontFamily.SansSerif),
        displayMedium = it.displayMedium.copy(fontFamily = FontFamily.SansSerif),
        displaySmall = it.displaySmall.copy(fontFamily = FontFamily.SansSerif),
        headlineLarge = it.headlineLarge.copy(fontFamily = FontFamily.SansSerif),
        headlineMedium = it.headlineMedium.copy(fontFamily = FontFamily.SansSerif),
        headlineSmall = it.headlineSmall.copy(fontFamily = FontFamily.SansSerif),
        titleLarge = it.titleLarge.copy(fontFamily = FontFamily.SansSerif),
        titleMedium = it.titleMedium.copy(fontFamily = FontFamily.SansSerif),
        titleSmall = it.titleSmall.copy(fontFamily = FontFamily.SansSerif),
        bodyLarge = it.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
        bodyMedium = it.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
        bodySmall = it.bodySmall.copy(fontFamily = FontFamily.SansSerif),
        labelLarge = it.labelLarge.copy(fontFamily = FontFamily.SansSerif),
        labelMedium = it.labelMedium.copy(fontFamily = FontFamily.SansSerif),
        labelSmall = it.labelSmall.copy(fontFamily = FontFamily.SansSerif)
    )
}

object RitelaMotion {
    const val ORBIT_HALF_BREATH_MILLIS = 2600
    const val MARKER_HALF_BREATH_MILLIS = 2100
}

object RitelaColors {
    val light = lightColorScheme(
        primary = Color(0xFF685067),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEBDCE8),
        onPrimaryContainer = Color(0xFF392B3B),
        secondary = Color(0xFF975463),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFF3E2E4),
        onSecondaryContainer = Color(0xFF522C36),
        tertiary = Color(0xFF59664F),
        tertiaryContainer = Color(0xFFE5E9DE),
        onTertiaryContainer = Color(0xFF303C28),
        background = Color(0xFFF8F4EF),
        surface = Color(0xFFF8F4EF),
        surfaceContainer = Color(0xFFF0E9E5),
        surfaceContainerLow = Color(0xFFFCF9F5),
        surfaceContainerHigh = Color(0xFFEDE3E7),
        onSurface = Color(0xFF2D2231),
        onSurfaceVariant = Color(0xFF6B626B),
        outline = Color(0xFF897A85),
        outlineVariant = Color(0xFFDFD4DA)
    )

    val dark = darkColorScheme(
        primary = Color(0xFFDCC0D6),
        onPrimary = Color(0xFF392B3B),
        primaryContainer = Color(0xFF513C50),
        onPrimaryContainer = Color(0xFFF0DDED),
        secondary = Color(0xFFE5A8B5),
        onSecondary = Color(0xFF492731),
        secondaryContainer = Color(0xFF452F38),
        onSecondaryContainer = Color(0xFFF6DEE3),
        tertiary = Color(0xFFBBC8AA),
        tertiaryContainer = Color(0xFF37412F),
        onTertiaryContainer = Color(0xFFE5E9DE),
        background = Color(0xFF1E191F),
        surface = Color(0xFF1E191F),
        surfaceContainer = Color(0xFF302730),
        surfaceContainerLow = Color(0xFF282128),
        surfaceContainerHigh = Color(0xFF3B303A),
        onSurface = Color(0xFFF2E8EE),
        onSurfaceVariant = Color(0xFFCDBFC9),
        outline = Color(0xFF9B8795),
        outlineVariant = Color(0xFF554450)
    )
}

@Composable
fun RitelaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> RitelaColors.dark

        else -> RitelaColors.light
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = RitelaShapes,
        typography = RitelaTypography,
        content = content
    )
}
