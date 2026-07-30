package com.phequals7.muesli.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

// Material3 mapping of the Muesli brand tokens (see MuesliColors.kt).
// Dynamic (Material You) color is intentionally NOT used: brand consistency
// with muesli-ios takes priority over wallpaper-derived colors.

private val MuesliMaterialDarkScheme =
  darkColorScheme(
    primary = Color(0xFF6BA3F7),
    onPrimary = Color(0xFF0A1628),
    primaryContainer = Color(0xFF18253A),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = Color(0xFF34D8C3),
    onSecondary = Color(0xFF06201C),
    secondaryContainer = Color(0xFF173633),
    onSecondaryContainer = Color(0xFFC8FBEF),
    background = Color(0xFF111214),
    onBackground = Color.White.copy(alpha = 0.92f),
    surface = Color(0xFF15171B),
    onSurface = Color.White.copy(alpha = 0.92f),
    surfaceVariant = Color(0xFF252934),
    onSurfaceVariant = Color.White.copy(alpha = 0.62f),
    outline = Color.White.copy(alpha = 0.12f),
    error = Color(0xFFFF453A),
    onError = Color.White,
  )

private val MuesliMaterialLightScheme =
  lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6F0FF),
    onPrimaryContainer = Color(0xFF0A2A6B),
    secondary = Color(0xFF059669),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCFDF6),
    onSecondaryContainer = Color(0xFF06402F),
    background = Color(0xFFF0F4FA),
    onBackground = Color.Black.copy(alpha = 0.88f),
    surface = Color(0xFFF7F9FC),
    onSurface = Color.Black.copy(alpha = 0.88f),
    surfaceVariant = Color(0xFFEEF3FB),
    onSurfaceVariant = Color.Black.copy(alpha = 0.55f),
    outline = Color.Black.copy(alpha = 0.10f),
    error = Color(0xFFD70015),
    onError = Color.White,
  )

@Composable
fun MuesliTheme(
  darkTheme: Boolean = resolveDarkTheme(),
  content: @Composable () -> Unit,
) {
  val baseColors = if (darkTheme) MuesliDarkColors else MuesliLightColors
  val accentTheme = MuesliAccentTheme.fromId(AppearanceController.accentThemeId)
  val muesliColors = baseColors.withAccent(accentTheme.color(darkTheme))

  CompositionLocalProvider(LocalMuesliColors provides muesliColors) {
    MaterialTheme(
      colorScheme = if (darkTheme) MuesliMaterialDarkScheme else MuesliMaterialLightScheme,
      typography = Typography,
      content = content,
    )
  }
}

/** Resolves the effective dark mode from the user's appearance preference. */
@Composable
private fun resolveDarkTheme(): Boolean = when (AppearanceController.appearanceMode) {
  "light" -> false
  "dark" -> true
  else -> isSystemInDarkTheme()
}
