package com.phequals7.muesli.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Muesli design tokens, ported from muesli-ios Shared/MuesliTheme.swift.
 * Keep hex values in sync with the iOS app.
 */
@Immutable
data class MuesliColors(
  val backgroundDeep: Color,
  val backgroundBase: Color,
  val backgroundRaised: Color,
  val backgroundHover: Color,
  val surfacePrimary: Color,
  val surfaceSelected: Color,
  val surfaceBorder: Color,
  val textPrimary: Color,
  val textSecondary: Color,
  val textTertiary: Color,
  val accent: Color,
  val brandBlue: Color,
  val brandBlueSubtle: Color,
  val syncGreen: Color,
  val syncGreenSubtle: Color,
  val recording: Color,
  val transcribing: Color,
  val success: Color,
  val destructive: Color,
) {
  val accentSubtle: Color get() = accent.copy(alpha = 0.14f)
  val destructiveSubtle: Color get() = destructive.copy(alpha = 0.16f)

  /** Returns a copy with the accent swapped (MuesliAccentTheme variants). */
  fun withAccent(newAccent: Color): MuesliColors = copy(accent = newAccent)
}

/** Accent variants, hex values from muesli-ios MuesliAccentTheme. */
enum class MuesliAccentTheme(val id: String, val label: String, val darkHex: Long, val lightHex: Long) {
  BLUE("blue", "Blue", 0x6BA3F7, 0x2563EB),
  GREEN("green", "Green", 0x34D399, 0x059669),
  SLATE("slate", "Slate", 0xCBD5E1, 0x475569);

  fun color(dark: Boolean): Color = Color(0xFF000000L or (if (dark) darkHex else lightHex))

  companion object {
    fun fromId(id: String?): MuesliAccentTheme = entries.firstOrNull { it.id == id } ?: BLUE
  }
}

val MuesliDarkColors =
  MuesliColors(
    backgroundDeep = Color(0xFF111214),
    backgroundBase = Color(0xFF15171B),
    backgroundRaised = Color(0xFF1B1D22),
    backgroundHover = Color(0xFF252A32),
    surfacePrimary = Color(0xFF252934),
    surfaceSelected = Color(0xFF2A3243),
    surfaceBorder = Color.White.copy(alpha = 0.12f),
    textPrimary = Color.White.copy(alpha = 0.92f),
    textSecondary = Color.White.copy(alpha = 0.62f),
    textTertiary = Color.White.copy(alpha = 0.40f),
    accent = Color(0xFF6BA3F7), // MuesliAccentTheme.blue darkHex
    brandBlue = Color(0xFF69A1FF),
    brandBlueSubtle = Color(0xFF18253A),
    syncGreen = Color(0xFF34D8C3),
    syncGreenSubtle = Color(0xFF173633),
    recording = Color(0xFF69A1FF),
    transcribing = Color(0xFF6BA3F7),
    success = Color(0xFF34D8C3),
    destructive = Color(0xFFFF453A),
  )

val MuesliLightColors =
  MuesliColors(
    backgroundDeep = Color(0xFFF0F4FA),
    backgroundBase = Color(0xFFF7F9FC),
    backgroundRaised = Color(0xFFFFFFFF),
    backgroundHover = Color(0xFFE8EEF8),
    surfacePrimary = Color(0xFFEEF3FB),
    surfaceSelected = Color(0xFFE4EEFF),
    surfaceBorder = Color.Black.copy(alpha = 0.10f),
    textPrimary = Color.Black.copy(alpha = 0.88f),
    textSecondary = Color.Black.copy(alpha = 0.55f),
    textTertiary = Color.Black.copy(alpha = 0.33f),
    accent = Color(0xFF2563EB), // MuesliAccentTheme.blue lightHex
    brandBlue = Color(0xFF69A1FF),
    brandBlueSubtle = Color(0xFFE6F0FF),
    syncGreen = Color(0xFF34D8C3),
    syncGreenSubtle = Color(0xFFDCFDF6),
    recording = Color(0xFF69A1FF),
    transcribing = Color(0xFF6BA3F7),
    success = Color(0xFF34D8C3),
    destructive = Color(0xFFD70015),
  )

/** Spacing scale from MuesliTheme.swift. */
object MuesliSpacing {
  val s4: Dp = 4.dp
  val s8: Dp = 8.dp
  val s12: Dp = 12.dp
  val s16: Dp = 16.dp
  val s20: Dp = 20.dp
  val s24: Dp = 24.dp
  val s32: Dp = 32.dp
}

/** Corner radii from MuesliTheme.swift. */
object MuesliCorners {
  val small: Dp = 8.dp
  val medium: Dp = 12.dp
  val large: Dp = 16.dp
  val xl: Dp = 22.dp

  /** Floating navigation container (iOS liquid-glass nav). */
  val navContainer: Dp = 24.dp
  val navSelection: Dp = 23.dp
}

val LocalMuesliColors = staticCompositionLocalOf { MuesliDarkColors }

/** Access brand tokens via `MuesliTheme.colors` from any @Composable. */
object MuesliTheme {
  val colors: MuesliColors
    @Composable get() = LocalMuesliColors.current
}

@Composable
fun rememberMuesliColors(darkTheme: Boolean = isSystemInDarkTheme()): MuesliColors =
  if (darkTheme) MuesliDarkColors else MuesliLightColors
