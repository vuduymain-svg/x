package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

// 4.dp Grid Spacing System
object GameSpacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
}

// Nested Corner Radius System
object GameRadius {
    val badge = 4.dp
    val control = 8.dp
    val card = 12.dp
    val panel = 16.dp
}

private val RuneterraDarkScheme = darkColorScheme(
    primary = AccentGold,
    onPrimary = BgDeep,
    primaryContainer = HextechGoldDim,
    onPrimaryContainer = AccentGold,
    secondary = AccentIce,
    onSecondary = BgDeep,
    secondaryContainer = ArcaneCyanDim,
    onSecondaryContainer = AccentIce,
    tertiary = AccentSpellMana,
    onTertiary = TextPrimary,
    tertiaryContainer = EpicVioletDim,
    onTertiaryContainer = AccentSpellMana,
    background = BgDeep,
    onBackground = TextPrimary,
    surface = SurfaceBase,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle,
    outlineVariant = BorderGoldMuted,
    error = SemanticDanger,
    onError = TextPrimary
)

@Composable
fun PathOfChampionsTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = RuneterraDarkScheme,
        typography = Typography,
        content = content
    )
}
