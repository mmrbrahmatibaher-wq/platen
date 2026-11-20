package com.mahdi.platen.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mahdi.platen.R
import com.mahdi.platen.data.PaperTheme

/** Special Elite (Apache 2.0, Astigmatic): a worn, inked typewriter face for the sheet and titles. */
val SpecialElite = FontFamily(Font(R.font.special_elite, FontWeight.Normal))

/** Courier Prime (SIL OFL), for small UI text. Glyphs it lacks (e.g. Persian) fall back to the system fonts. */
val CourierPrime = FontFamily(
    Font(R.font.courier_prime_regular, FontWeight.Normal),
    Font(R.font.courier_prime_bold, FontWeight.Bold),
)

private val base = Typography()

private val PlatenTypography = Typography(
    headlineLarge = base.headlineLarge.copy(fontFamily = SpecialElite),
    headlineMedium = base.headlineMedium.copy(fontFamily = SpecialElite),
    headlineSmall = base.headlineSmall.copy(fontFamily = SpecialElite),
    titleLarge = base.titleLarge.copy(fontFamily = SpecialElite),
    titleMedium = base.titleMedium.copy(fontFamily = SpecialElite),
    titleSmall = base.titleSmall.copy(fontFamily = CourierPrime, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = CourierPrime),
    bodyMedium = base.bodyMedium.copy(fontFamily = CourierPrime),
    bodySmall = base.bodySmall.copy(fontFamily = CourierPrime),
    labelLarge = base.labelLarge.copy(fontFamily = CourierPrime, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = CourierPrime),
    labelSmall = base.labelSmall.copy(fontFamily = CourierPrime),
)

/** The sheet's type. Persian/Arabic and emoji fall back to the system fonts (Special Elite is Latin-only). */
val TypewriterText = TextStyle(fontFamily = SpecialElite, fontSize = 18.sp, lineHeight = 27.sp)

@Composable
fun PlatenTheme(theme: PaperTheme, content: @Composable () -> Unit) {
    val p = paletteFor(theme)
    val scheme = if (p.isDark) {
        darkColorScheme(
            primary = p.ink, onPrimary = p.background, secondary = p.indicator, background = p.background,
            onBackground = p.onBackground, surface = p.card, onSurface = p.onBackground,
            surfaceVariant = p.body, onSurfaceVariant = p.muted, surfaceContainerHigh = p.card,
            surfaceContainer = p.card, surfaceContainerHighest = p.body,
            secondaryContainer = p.body, onSecondaryContainer = p.onBackground, outline = p.muted,
        )
    } else {
        lightColorScheme(
            primary = p.ink, onPrimary = p.paper, secondary = p.indicator, background = p.background,
            onBackground = p.onBackground, surface = p.card, onSurface = p.onBackground,
            surfaceVariant = p.bodyShade, onSurfaceVariant = p.muted, surfaceContainerHigh = p.card,
            surfaceContainer = p.card, surfaceContainerHighest = p.body,
            secondaryContainer = p.body, onSecondaryContainer = p.onBackground, outline = p.muted,
        )
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = PlatenTypography, content = content)
    }
}
