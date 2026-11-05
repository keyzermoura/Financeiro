package br.com.carteirainvest.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightColors = lightColorScheme(
    primary = Emerald700,
    onPrimary = Color.White,
    primaryContainer = Emerald100,
    onPrimaryContainer = Emerald950,
    secondary = Color(0xFF4C6357),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCFE9DA),
    onSecondaryContainer = Color(0xFF092017),
    tertiary = Color(0xFF44618B),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD7E3FF),
    onTertiaryContainer = Color(0xFF001B3D),
    error = NegativeLight,
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = CanvasLight,
    onBackground = InkLight,
    surface = SurfaceLight,
    onSurface = InkLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = Color(0xFF3F4943),
    outline = OutlineLight,
    outlineVariant = Color(0xFFBFCAC3),
    inverseSurface = Emerald900,
    inverseOnSurface = Emerald50,
    inversePrimary = Emerald300,
    surfaceTint = Emerald700,
    scrim = Color.Black,
)

private val DarkColors = darkColorScheme(
    primary = Emerald300,
    onPrimary = Color(0xFF003827),
    primaryContainer = Color(0xFF005139),
    onPrimaryContainer = Color(0xFF8CF8C5),
    secondary = Color(0xFFB3CDBD),
    onSecondary = Color(0xFF1F352A),
    secondaryContainer = Color(0xFF354B3F),
    onSecondaryContainer = Color(0xFFCFE9DA),
    tertiary = Color(0xFFAAC7F8),
    onTertiary = Color(0xFF0D2F59),
    tertiaryContainer = Color(0xFF2B4671),
    onTertiaryContainer = Color(0xFFD7E3FF),
    error = NegativeDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = CanvasDark,
    onBackground = InkDark,
    surface = SurfaceDark,
    onSurface = InkDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = Color(0xFFBFCAC3),
    outline = OutlineDark,
    outlineVariant = Color(0xFF3F4943),
    inverseSurface = Emerald100,
    inverseOnSurface = Emerald950,
    inversePrimary = Emerald700,
    surfaceTint = Emerald300,
    scrim = Color.Black,
)

@Immutable
data class ExtendedColors(
    val positive: Color,
    val negative: Color,
    val warning: Color,
    val chart: List<Color>,
    val heroStart: Color,
    val heroEnd: Color,
    val gold: Color,
)

private val LightExtended = ExtendedColors(
    positive = PositiveLight,
    negative = NegativeLight,
    warning = Warning,
    chart = ChartColors,
    heroStart = Emerald900,
    heroEnd = Color(0xFF0E7250),
    gold = Gold500,
)

private val DarkExtended = ExtendedColors(
    positive = PositiveDark,
    negative = NegativeDark,
    warning = Warning,
    chart = ChartColors,
    heroStart = Color(0xFF07301F),
    heroEnd = Color(0xFF0B5038),
    gold = Gold300,
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun CarteiraInvestTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) DarkColors else LightColors
    val extended = if (darkTheme) DarkExtended else LightExtended
    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
