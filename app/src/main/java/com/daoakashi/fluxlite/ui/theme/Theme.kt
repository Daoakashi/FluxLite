package com.daoakashi.fluxlite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.sp

/** Couleurs de Flux (mêmes valeurs que l'application PC), en sombre et en clair. */
@Immutable
data class PaletteFlux(
    val sombre: Boolean,
    val fond: Color,
    val panneau: Color,
    val surface: Color,
    val surface2: Color,
    val trait: Color,
    val texte: Color,
    val texte2: Color,
    val texte3: Color,
    val video: Color = Color(0xFF050B15),
    val succes: Color,
    val danger: Color,
    val attention: Color,
    val listeNoire: Color,
    val personne: Color = Color(0xFF22D3EE),
    val visage: Color = Color(0xFFB79CFF),
    val accent: Color = Color(0xFF2F7BFF),
    val accentVif: Color,
    val accentDoux: Color,
    val surAccent: Color = Color.White,
)

val PaletteSombre = PaletteFlux(
    sombre = true,
    fond = Color(0xFF0A1220), panneau = Color(0xFF0E1829), surface = Color(0xFF132137), surface2 = Color(0xFF1A2B47),
    trait = Color(0xFF233957), texte = Color(0xFFE7EEF8), texte2 = Color(0xFF93A6C2), texte3 = Color(0xFF5F7596),
    succes = Color(0xFF2BD07A), danger = Color(0xFFF2554A), attention = Color(0xFFF5A524), listeNoire = Color(0xFFFF2E7A),
    accentVif = Color(0xFF5A97FF), accentDoux = Color(0xFF162E57),
)

val PaletteClaire = PaletteFlux(
    sombre = false,
    fond = Color(0xFFEEF3FA), panneau = Color(0xFFF7FAFE), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFE7EEF8),
    trait = Color(0xFFD2DDEB), texte = Color(0xFF0E1A2B), texte2 = Color(0xFF51627B), texte3 = Color(0xFF8695AB),
    video = Color(0xFF0B1424),
    succes = Color(0xFF12A150), danger = Color(0xFFD92D20), attention = Color(0xFFC77700), listeNoire = Color(0xFFC2185B),
    accentVif = Color(0xFF296CE0), accentDoux = Color(0xFFE1EBFC),
)

val LocalFlux = staticCompositionLocalOf { PaletteSombre }

object Flux {
    val c: PaletteFlux @Composable get() = LocalFlux.current
}

private val Police = FontFamily.Default

private val Typo = Typography(
    displaySmall = TextStyle(fontFamily = Police, fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Police, fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontFamily = Police, fontWeight = FontWeight.SemiBold, fontSize = 21.sp),
    titleLarge = TextStyle(fontFamily = Police, fontWeight = FontWeight.SemiBold, fontSize = 19.sp),
    titleMedium = TextStyle(fontFamily = Police, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = Police, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = Police, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = Police, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Police, fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = Police, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Police, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, letterSpacing = 0.3.sp),
    labelSmall = TextStyle(fontFamily = Police, fontWeight = FontWeight.Bold, fontSize = 10.5.sp, letterSpacing = 0.8.sp),
)

@Composable
fun ThemeFlux(sombre: Boolean = isSystemInDarkTheme(), contenu: @Composable () -> Unit) {
    val p = if (sombre) PaletteSombre else PaletteClaire
    val schema = if (sombre) darkColorScheme(
        primary = p.accent, onPrimary = p.surAccent, secondary = p.personne, background = p.fond, onBackground = p.texte,
        surface = p.surface, onSurface = p.texte, surfaceVariant = p.surface2, onSurfaceVariant = p.texte2,
        outline = p.trait, outlineVariant = p.trait, error = p.danger, surfaceContainer = p.panneau,
        surfaceContainerHigh = p.surface, surfaceContainerLow = p.panneau, surfaceContainerHighest = p.surface2,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.surAccent, secondary = p.personne, background = p.fond, onBackground = p.texte,
        surface = p.surface, onSurface = p.texte, surfaceVariant = p.surface2, onSurfaceVariant = p.texte2,
        outline = p.trait, outlineVariant = p.trait, error = p.danger, surfaceContainer = p.panneau,
        surfaceContainerHigh = p.surface, surfaceContainerLow = p.panneau, surfaceContainerHighest = p.surface2,
    )
    MaterialTheme(colorScheme = schema, typography = Typo) {
        CompositionLocalProvider(LocalFlux provides p, content = contenu)
    }
}
