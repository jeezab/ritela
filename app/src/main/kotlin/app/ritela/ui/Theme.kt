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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Spacing {
    val small = 8.dp
    val medium = 16.dp
    val large = 24.dp
    val section = 32.dp
    val actionHeight = 56.dp
}

private val AppShapes = Shapes(
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private val AppTypography = Typography().let {
    it.copy(
        headlineLarge = it.headlineLarge.copy(letterSpacing = (-0.5).sp),
        headlineMedium = it.headlineMedium.copy(letterSpacing = (-0.5).sp)
    )
}

private val LightColors = lightColorScheme(
    primary = Color(0xFF315D50),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5EBDF),
    onPrimaryContainer = Color(0xFF143B30),
    background = Color(0xFFFAFAF5),
    surface = Color(0xFFFAFAF5),
    surfaceContainer = Color(0xFFEFEEE6),
    surfaceContainerLow = Color(0xFFF3F3ED),
    onSurface = Color(0xFF202823),
    onSurfaceVariant = Color(0xFF535F57),
    outlineVariant = Color(0xFFD7DED6)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9D0BC),
    onPrimary = Color(0xFF10382A),
    primaryContainer = Color(0xFF284F40),
    onPrimaryContainer = Color(0xFFD5EBDF),
    background = Color(0xFF141714),
    surface = Color(0xFF141714),
    surfaceContainer = Color(0xFF232923),
    surfaceContainerLow = Color(0xFF1B211C),
    onSurface = Color(0xFFE4EAE3),
    onSurfaceVariant = Color(0xFFB8C5BA),
    outlineVariant = Color(0xFF3D493F)
)

@Composable
fun RitelaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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
