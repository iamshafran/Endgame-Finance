package com.endgamefinance.ui.theme

import androidx.compose.ui.graphics.Color

// Brand identity: deep evergreen primary, restrained gold accent (tertiary).
// Semantic gain/loss colors are reserved here so red/green amounts never
// collide with the brand green.

// Light scheme
val GreenPrimaryLight = Color(0xFF16624F)
val OnGreenPrimaryLight = Color(0xFFFFFFFF)
val GreenPrimaryContainerLight = Color(0xFFA4F1DA)
val OnGreenPrimaryContainerLight = Color(0xFF002019)

val SageSecondaryLight = Color(0xFF4B635A)
val OnSageSecondaryLight = Color(0xFFFFFFFF)
val SageSecondaryContainerLight = Color(0xFFCDE9DC)
val OnSageSecondaryContainerLight = Color(0xFF07201A)

val GoldTertiaryLight = Color(0xFF8A6C1F)
val OnGoldTertiaryLight = Color(0xFFFFFFFF)
val GoldTertiaryContainerLight = Color(0xFFF8E3A1)
val OnGoldTertiaryContainerLight = Color(0xFF2A2000)

val ErrorLight = Color(0xFFBA1A1A)
val OnErrorLight = Color(0xFFFFFFFF)
val ErrorContainerLight = Color(0xFFFFDAD6)
val OnErrorContainerLight = Color(0xFF410002)

// Pure-white canvas (owner request 2026-07-14); the tint lives in the
// surfaceContainer roles that cards/app bars use, not the page background.
val BackgroundLight = Color(0xFFFFFFFF)
val OnBackgroundLight = Color(0xFF171D1A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF171D1A)
val SurfaceVariantLight = Color(0xFFDBE5DE)
val OnSurfaceVariantLight = Color(0xFF404944)
val OutlineLight = Color(0xFF707974)
// Expanded surface roles — neutral with a faint evergreen tint so cards,
// elevation, and scrolled app bars read as part of the brand.
val OutlineVariantLight = Color(0xFFBFC9C2)
val SurfaceDimLight = Color(0xFFD6DBD6)
val SurfaceBrightLight = Color(0xFFFFFFFF)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF0F5F1)
val SurfaceContainerLight = Color(0xFFEAF0EB)
val SurfaceContainerHighLight = Color(0xFFE5EBE6)
val SurfaceContainerHighestLight = Color(0xFFDFE4E0)
val InverseSurfaceLight = Color(0xFF2B322E)
val InverseOnSurfaceLight = Color(0xFFECF2ED)
val InversePrimaryLight = Color(0xFF88D6BE)
val ScrimLight = Color(0xFF000000)

// Dark scheme
val GreenPrimaryDark = Color(0xFF88D6BE)
val OnGreenPrimaryDark = Color(0xFF00382C)
val GreenPrimaryContainerDark = Color(0xFF00513F)
val OnGreenPrimaryContainerDark = Color(0xFFA4F1DA)

val SageSecondaryDark = Color(0xFFB2CCC0)
val OnSageSecondaryDark = Color(0xFF1D352C)
val SageSecondaryContainerDark = Color(0xFF344C42)
val OnSageSecondaryContainerDark = Color(0xFFCDE9DC)

val GoldTertiaryDark = Color(0xFFE5C36C)
val OnGoldTertiaryDark = Color(0xFF3B2F00)
val GoldTertiaryContainerDark = Color(0xFF554500)
val OnGoldTertiaryContainerDark = Color(0xFFFFE8A3)

val ErrorDark = Color(0xFFFFB4AB)
val OnErrorDark = Color(0xFF690005)
val ErrorContainerDark = Color(0xFF93000A)
val OnErrorContainerDark = Color(0xFFFFDAD6)

val BackgroundDark = Color(0xFF0F1512)
val OnBackgroundDark = Color(0xFFDEE4DF)
val SurfaceDark = Color(0xFF0F1512)
val OnSurfaceDark = Color(0xFFDEE4DF)
val SurfaceVariantDark = Color(0xFF404944)
val OnSurfaceVariantDark = Color(0xFFBFC9C2)
val OutlineDark = Color(0xFF89938D)
val OutlineVariantDark = Color(0xFF404944)
val SurfaceDimDark = Color(0xFF0F1512)
val SurfaceBrightDark = Color(0xFF353B37)
val SurfaceContainerLowestDark = Color(0xFF0A100D)
val SurfaceContainerLowDark = Color(0xFF171D1A)
val SurfaceContainerDark = Color(0xFF1B211E)
val SurfaceContainerHighDark = Color(0xFF262C28)
val SurfaceContainerHighestDark = Color(0xFF303733)
val InverseSurfaceDark = Color(0xFFDEE4DF)
val InverseOnSurfaceDark = Color(0xFF2B322E)
val InversePrimaryDark = Color(0xFF16624F)
val ScrimDark = Color(0xFF000000)

// Semantic money colors (used from Milestone 1 onward for amount rendering)
val GainLight = Color(0xFF2E7D32)
val LossLight = Color(0xFFC62828)
val GainDark = Color(0xFF81C995)
val LossDark = Color(0xFFF28B82)
