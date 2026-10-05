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
import androidx.compose.ui.unit.sp

object Spacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val section = 32.dp
    val actionHeight = 56.dp
    val calendarMinimumWidth = 336.dp
    val calendarCellHeight = 64.dp
    val calendarBorder = 2.dp
}

private val AppShapes = Shapes(
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography().let {
    it.copy(
        displayLarge = it.displayLarge.copy(fontFamily = FontFamily.Serif, letterSpacing = (-1).sp),
        headlineLarge = it.headlineLarge.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = it.headlineMedium.copy(
            fontFamily = FontFamily.Serif,
            letterSpacing = (-0.5).sp
        ),
        headlineSmall = it.headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = it.titleLarge.copy(fontFamily = FontFamily.Serif)
    )
}

private val LightColors = lightColorScheme(
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

private val DarkColors = darkColorScheme(
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

        darkTheme -> DarkColors

        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content
    )
}
