package com.prismgram.ui.theme

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.prismgram.R

// brand color
val BrandCoral = Color(0xFFED5564)

val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
)

// everything is round
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

private fun typographyFor(family: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)

private val AppTypography = typographyFor(Poppins)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3243B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDADD),
    onPrimaryContainer = Color(0xFF400009),
    secondary = Color(0xFF765659),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDADD),
    onSecondaryContainer = Color(0xFF2C1517),
    tertiary = Color(0xFF6C5577),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF4D8FF),
    onTertiaryContainer = Color(0xFF27132F),
    background = Color(0xFFFFF8F7),
    onBackground = Color(0xFF23191A),
    surface = Color(0xFFFFF8F7),
    onSurface = Color(0xFF23191A),
    surfaceVariant = Color(0xFFD8C2C4),
    onSurfaceVariant = Color(0xFF524345),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF0F0),
    surfaceContainer = Color(0xFFFCEAEC),
    surfaceContainerHigh = Color(0xFFF6E3E5),
    surfaceContainerHighest = Color(0xFFF0DEE0),
    outline = Color(0xFF847375),
    outlineVariant = Color(0xFFD8C2C4),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB1BA),
    onPrimary = Color(0xFF5F1120),
    primaryContainer = Color(0xFF9E2A3A),
    onPrimaryContainer = Color(0xFFFFDADD),
    secondary = Color(0xFFE7BDC1),
    onSecondary = Color(0xFF442A2C),
    secondaryContainer = Color(0xFF5C4043),
    onSecondaryContainer = Color(0xFFFFDADD),
    tertiary = Color(0xFFD9BDE4),
    onTertiary = Color(0xFF3D2846),
    tertiaryContainer = Color(0xFF543E5E),
    onTertiaryContainer = Color(0xFFF4D8FF),
    background = Color(0xFF191113),
    onBackground = Color(0xFFF0DEE0),
    surface = Color(0xFF191113),
    onSurface = Color(0xFFF0DEE0),
    surfaceVariant = Color(0xFF524345),
    onSurfaceVariant = Color(0xFFD6C2C4),
    surfaceContainerLowest = Color(0xFF120B0D),
    surfaceContainerLow = Color(0xFF21181A),
    surfaceContainer = Color(0xFF251C1E),
    surfaceContainerHigh = Color(0xFF302628),
    surfaceContainerHighest = Color(0xFF3B3033),
    outline = Color(0xFF9E8C8E),
    outlineVariant = Color(0xFF524345),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

// coral by default, can also follow the wallpaper on android 12+
@Composable
fun PrismGramTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    pureBlack: Boolean = false,
    content: @Composable () -> Unit,
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    val colorScheme = if (darkTheme && pureBlack) {
        baseScheme.copy(
            background = Color.Black,
            surface = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0A),
        )
    } else {
        baseScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
