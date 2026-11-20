package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Obsidian Slate, Antique Brass & Glacial Cyan Design Tokens
val BgDeep = Color(0xFF0E1116)
val SurfaceBase = Color(0xFF161B22)
val SurfaceElevated = Color(0xFF1F2630)
val SurfaceOverlay = Color(0xE612161D)

// Hairline Borders (1.dp)
val BorderSubtle = Color(0x1FFFFFFF)
val BorderGoldMuted = Color(0x66C89B3C)

// 3-Tier Text Hierarchy
val TextPrimary = Color(0xFFF0F4F8)
val TextSecondary = Color(0xFFA0AEC0)
val TextMuted = Color(0xFF64748B)

// Monochromatic Tactical Accents
val AccentGold = Color(0xFFD4AF37)
val AccentIce = Color(0xFF38BDF8)
val AccentSpellMana = Color(0xFF818CF8)

// Semantic Gameplay Colors (Crisp, non-neon)
val SemanticDanger = Color(0xFFEF4444)
val SemanticDangerDark = Color(0xFF7F1D1D)
val SemanticAttack = Color(0xFFF59E0B)
val SemanticAttackDark = Color(0xFF92400E)
val SemanticHeal = Color(0xFF10B981)
val SemanticRare = Color(0xFF60A5FA)
val SemanticEpic = Color(0xFFA78BFA)

// Backwards-compatible Token Aliases (for preserved Arena/Constellation components)
val ObsidianBg = BgDeep
val ObsidianSurface = SurfaceBase
val ObsidianSurfaceElevated = SurfaceElevated
val ObsidianCard = SurfaceBase
val SlateBorder = Color(0xFF2D3644)
val SlateBorderHighlight = TextMuted
val DarkButtonInk = BgDeep

val HextechGold = AccentGold
val HextechGoldBright = Color(0xFFE5C158)
val HextechGoldDeep = Color(0xFF8A6D29)
val HextechGoldDim = Color(0x28D4AF37)
val ParchmentWhite = TextPrimary
val MutedSilver = TextSecondary

val FollowerSilverBright = Color(0xFFCBD5E1)
val FollowerSilverDark = TextMuted
val FollowerBronze = Color(0xFF9A7B56)

val ArcaneCyan = AccentIce
val ArcaneCyanDim = Color(0x2438BDF8)
val ManaBlue = AccentIce
val SpellManaCyan = AccentSpellMana

val CrimsonNexus = SemanticDanger
val CrimsonDim = Color(0x28EF4444)
val EmeraldHeal = SemanticHeal
val EmeraldDim = Color(0x2410B981)
val EpicViolet = AccentSpellMana
val EpicVioletDim = Color(0x24818CF8)
val RareSapphire = SemanticRare
val CommonIron = Color(0xFF94A3B8)
val LegendaryAmber = SemanticAttack
val CurseMagenta = Color(0xFFEC4899)
