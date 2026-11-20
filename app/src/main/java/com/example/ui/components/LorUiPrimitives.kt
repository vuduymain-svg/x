package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

enum class LorMetalTone {
    GOLD,
    SILVER,
    CYAN,
    CUSTOM
}

/**
 * Tactile Obsidian Slate container with 1.dp hairline border (no rainbow gradients or neon glow).
 */
@Composable
fun LorMetallicPanel(
    modifier: Modifier = Modifier,
    tone: LorMetalTone = LorMetalTone.GOLD,
    customBorderColor: Color = AccentGold,
    cornerRadius: Dp = GameRadius.panel,
    borderWidth: Dp = 1.dp,
    filigreeAlpha: Float = 0f,
    showCornerFiligree: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedBorderColor = when (tone) {
        LorMetalTone.GOLD -> BorderGoldMuted
        LorMetalTone.SILVER -> BorderSubtle
        LorMetalTone.CYAN -> AccentIce.copy(alpha = 0.55f)
        LorMetalTone.CUSTOM -> customBorderColor.copy(alpha = 0.65f)
    }

    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClick)
                else Modifier
            ),
        shape = shape,
        color = SurfaceBase,
        border = BorderStroke(borderWidth.coerceAtMost(1.5.dp), resolvedBorderColor),
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .background(SurfaceBase)
                .padding(contentPadding),
            content = content
        )
    }
}

/**
 * Primary Antique Brass / Glacial Cyan tactical button on 4.dp grid (no gradient or shadow glow).
 */
@Composable
fun LorBrassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isCyanVariant: Boolean = false,
    fontSize: TextUnit = 14.sp,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "lor_brass_btn_scale"
    )

    val targetContainer = when {
        !enabled -> SurfaceElevated
        isCyanVariant -> AccentIce
        else -> AccentGold
    }
    val containerColor by animateColorAsState(
        targetValue = targetContainer,
        animationSpec = tween(durationMillis = 150),
        label = "lor_brass_btn_bg"
    )
    val borderColor = when {
        !enabled -> BorderSubtle
        pressed -> TextPrimary.copy(alpha = 0.45f)
        else -> Color(0x33FFFFFF)
    }
    val inkColor = if (enabled) BgDeep else TextMuted

    Surface(
        modifier = modifier
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(GameRadius.control),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GameSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(GameSpacing.sm))
            }
            Text(
                text = text,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize,
                color = inkColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Secondary recessed Obsidian Slate control button with 1.dp hairline border.
 */
@Composable
fun LorSecondaryMetalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accentColor: Color = AccentGold,
    leadingIcon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "lor_sec_btn_scale"
    )

    val borderColor = when {
        !enabled -> BorderSubtle
        accentColor == AccentGold -> BorderGoldMuted
        else -> accentColor.copy(alpha = 0.5f)
    }

    Surface(
        modifier = modifier
            .height(48.dp)
            .minimumInteractiveComponentSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(GameRadius.control),
        color = if (pressed && enabled) SurfaceBase else SurfaceElevated,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = GameSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(GameSpacing.xs))
            }
            Text(
                text = text,
                fontFamily = PlusJakartaSansFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = if (enabled) TextPrimary else TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Clean Obsidian Slate top header bar with 1.dp hairline bottom divider and Cinzel title.
 */
@Composable
fun LorCarvedTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    backTestTag: String? = null,
    backDescription: String = "Quay lại",
    leadingSlot: (@Composable () -> Unit)? = null,
    trailingSlot: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(SurfaceBase)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(if (backTestTag != null) Modifier.testTag(backTestTag) else Modifier)
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderGoldMuted)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = backDescription,
                                tint = AccentGold,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(GameSpacing.xs))
            }

            if (leadingSlot != null) {
                leadingSlot()
                Spacer(modifier = Modifier.width(GameSpacing.sm))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (trailingSlot != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm),
                    content = trailingSlot
                )
            }
        }

        HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
    }
}
