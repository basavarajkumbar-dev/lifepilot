package com.lifepilot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lifepilot.app.domain.model.Priority
import com.lifepilot.app.domain.model.ThemeMode

// ---------------------------------------------------------------- palette

private val Ink950 = Color(0xFF0E1120)
private val Ink900 = Color(0xFF13172A)
private val Ink850 = Color(0xFF171C31)
private val Ink800 = Color(0xFF1C2238)
private val Ink750 = Color(0xFF222942)
private val Ink700 = Color(0xFF2A314D)
private val Ink600 = Color(0xFF3A4263)
private val Violet300 = Color(0xFFA99BFF)
private val Violet400 = Color(0xFF8F80FF)
private val Violet600 = Color(0xFF5847E0)

private val DarkColors = darkColorScheme(
    primary = Violet400,
    onPrimary = Color(0xFF140E3D),
    primaryContainer = Color(0xFF2D2670),
    onPrimaryContainer = Color(0xFFE1DCFF),
    secondary = Color(0xFF6ED3C0),
    onSecondary = Color(0xFF00382F),
    secondaryContainer = Color(0xFF16413B),
    onSecondaryContainer = Color(0xFFB9F2E6),
    tertiary = Color(0xFFF4B870),
    onTertiary = Color(0xFF452800),
    tertiaryContainer = Color(0xFF4A3414),
    onTertiaryContainer = Color(0xFFFFDDB4),
    error = Color(0xFFFF7B7B),
    onError = Color(0xFF4A0008),
    errorContainer = Color(0xFF4D1C22),
    onErrorContainer = Color(0xFFFFDADA),
    background = Ink950,
    onBackground = Color(0xFFE7E9F5),
    surface = Ink950,
    onSurface = Color(0xFFE7E9F5),
    surfaceVariant = Ink800,
    onSurfaceVariant = Color(0xFFA2A8C6),
    surfaceTint = Violet400,
    inverseSurface = Color(0xFFE7E9F5),
    inverseOnSurface = Ink900,
    inversePrimary = Violet600,
    outline = Ink600,
    outlineVariant = Ink700,
    scrim = Color.Black,
    surfaceBright = Ink750,
    surfaceDim = Ink950,
    surfaceContainerLowest = Color(0xFF0B0E1A),
    surfaceContainerLow = Ink900,
    surfaceContainer = Ink850,
    surfaceContainerHigh = Ink800,
    surfaceContainerHighest = Ink750,
)

private val LightColors = lightColorScheme(
    primary = Violet600,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE7E3FF),
    onPrimaryContainer = Color(0xFF1D1570),
    secondary = Color(0xFF12806F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDF2EA),
    onSecondaryContainer = Color(0xFF00382F),
    tertiary = Color(0xFFA9620E),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6C7),
    onTertiaryContainer = Color(0xFF3A2100),
    error = Color(0xFFC4323B),
    onError = Color.White,
    errorContainer = Color(0xFFFFDADB),
    onErrorContainer = Color(0xFF410006),
    background = Color(0xFFF5F6FB),
    onBackground = Color(0xFF151A2E),
    surface = Color(0xFFF5F6FB),
    onSurface = Color(0xFF151A2E),
    surfaceVariant = Color(0xFFE6E8F2),
    onSurfaceVariant = Color(0xFF575D78),
    surfaceTint = Violet600,
    inverseSurface = Color(0xFF262B40),
    inverseOnSurface = Color(0xFFF0F1F8),
    inversePrimary = Violet300,
    outline = Color(0xFFBFC3D6),
    outlineVariant = Color(0xFFDDE0EC),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE4E6F0),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF0F1F8),
    surfaceContainerHighest = Color(0xFFE8EAF4),
)

/** Colors outside Material roles: categories, priorities and status. */
@Immutable
data class ExtendedColors(
    val categories: List<Color>,
    val success: Color,
    val warning: Color,
    val priorityLow: Color,
    val priorityMedium: Color,
    val priorityHigh: Color,
    val priorityCritical: Color,
    val cardBorder: Color,
    val heroGradient: List<Color>,
) {
    fun category(index: Int): Color = categories[Math.floorMod(index, categories.size)]
    fun priority(p: Priority): Color = when (p) {
        Priority.LOW -> priorityLow
        Priority.MEDIUM -> priorityMedium
        Priority.HIGH -> priorityHigh
        Priority.CRITICAL -> priorityCritical
    }
}

private val DarkExtended = ExtendedColors(
    categories = listOf(
        Color(0xFF8C9BFF), Color(0xFF4FC8B4), Color(0xFFF07AA0), Color(0xFFF2B451), Color(0xFF5DB7F5),
        Color(0xFFB792F7), Color(0xFF76C979), Color(0xFFF2925E), Color(0xFF959DBB), Color(0xFFE586D6),
    ),
    success = Color(0xFF6ED3C0),
    warning = Color(0xFFF4B870),
    priorityLow = Color(0xFF8E96B5),
    priorityMedium = Color(0xFF5DB7F5),
    priorityHigh = Color(0xFFF2A93B),
    priorityCritical = Color(0xFFFF6E6E),
    cardBorder = Color(0x14FFFFFF),
    heroGradient = listOf(Color(0xFF241F5C), Color(0xFF17193A)),
)

private val LightExtended = ExtendedColors(
    categories = listOf(
        Color(0xFF4F5FE0), Color(0xFF119C86), Color(0xFFD5457A), Color(0xFFC58411), Color(0xFF1E85CC),
        Color(0xFF8457D6), Color(0xFF3C9A40), Color(0xFFD7642A), Color(0xFF6A7290), Color(0xFFBF4FAF),
    ),
    success = Color(0xFF12806F),
    warning = Color(0xFFB36B10),
    priorityLow = Color(0xFF6A7290),
    priorityMedium = Color(0xFF1E85CC),
    priorityHigh = Color(0xFFC77A00),
    priorityCritical = Color(0xFFD12F3A),
    cardBorder = Color(0x0F151A2E),
    heroGradient = listOf(Color(0xFF5B4BE2), Color(0xFF7B5CE8)),
)

val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

// ---------------------------------------------------------------- type & shape

private val Base = Typography()
private val Sans = FontFamily.SansSerif

private val AppTypography = Typography(
    displaySmall = Base.displaySmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineLarge = Base.headlineLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = Base.headlineSmall.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleLarge = Base.titleLarge.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleMedium = Base.titleMedium.copy(fontFamily = Sans, fontWeight = FontWeight.SemiBold),
    titleSmall = Base.titleSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    bodyLarge = Base.bodyLarge.copy(fontFamily = Sans, letterSpacing = 0.1.sp),
    bodyMedium = Base.bodyMedium.copy(fontFamily = Sans, letterSpacing = 0.1.sp),
    bodySmall = Base.bodySmall.copy(fontFamily = Sans, letterSpacing = 0.15.sp),
    labelLarge = Base.labelLarge.copy(fontFamily = Sans, fontWeight = FontWeight.Medium),
    labelMedium = Base.labelMedium.copy(fontFamily = Sans, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    labelSmall = Base.labelSmall.copy(fontFamily = Sans, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Uppercase eyebrow label style used for section headers. */
val EyebrowStyle: TextStyle @Composable get() = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.1.sp, fontWeight = FontWeight.SemiBold)

object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 28.dp
    val screen = 20.dp
}

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
}

@Composable
fun LifePilotTheme(themeMode: ThemeMode = ThemeMode.DARK, content: @Composable () -> Unit) {
    val dark = isAppInDarkTheme(themeMode)
    val colors: ColorScheme = if (dark) DarkColors else LightColors
    CompositionLocalProvider(LocalExtendedColors provides if (dark) DarkExtended else LightExtended) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

object LpTheme {
    val extended: ExtendedColors @Composable get() = LocalExtendedColors.current
}
