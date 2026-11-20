package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.abs

/**
 * Two-state Player Hand Overlay for the battle screen:
 * - Collapsed (isExpanded = false): cards shrink (scale = 0.65f) and stack tightly at BottomEnd,
 *   exposing only the left mana edge of stacked cards and the full face of the rightmost card,
 *   leaving the central battlefield unobstructed. Tapping anywhere on the stack expands it.
 * - Expanded (isExpanded = true): cards scale up and fan out in a curved arch at BottomCenter
 *   so stats/descriptions are clearly readable and cards can be dragged onto the battlefield.
 *   Tapping the empty field scrim or playing a card automatically collapses the hand back to BottomEnd.
 */
@Composable
fun <T> PlayerHandOverlay(
    cards: List<T>,
    cardKey: (T) -> Any,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    canDragCards: Boolean,
    draggingCardKey: Any? = null,
    rightCollapsedMargin: Dp = 32.dp,
    bottomCollapsedMargin: Dp = 4.dp,
    onCardDragStart: (T, Offset) -> Unit = { _, _ -> },
    onCardDrag: (T, Offset) -> Unit = { _, _ -> },
    onCardDragEnd: (T, Offset) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    cardContent: @Composable (card: T, isExpanded: Boolean, isDragging: Boolean, cardWidth: Dp, cardHeight: Dp) -> Unit
) {
    val currentCards by rememberUpdatedState(cards)
    val currentCanDrag by rememberUpdatedState(canDragCards)
    val currentIsExpanded by rememberUpdatedState(isExpanded)

    LaunchedEffect(cards.size) {
        if (cards.isEmpty() && isExpanded) {
            onExpandedChange(false)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("player_hand_overlay")
    ) {
        val containerWidth = maxWidth
        val containerHeight = maxHeight

        // Full readable card size (~15% larger when expanded/dragged); collapsed state scales to 0.60f to stay compact
        val cardFullHeight = (containerHeight * 0.368f).coerceIn(134.dp, 184.dp)
        val cardFullWidth = cardFullHeight * 0.6875f

        // 1. Invisible field scrim when expanded: tapping outside the hand collapses it to BottomEnd
        if (isExpanded && draggingCardKey == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("player_hand_dismiss_scrim")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onExpandedChange(false)
                    }
            )
        }

        // 2. Player hand card fan / collapsed stack
        val count = cards.size
        if (count > 0) {
            val collapsedScale = 0.60f
            val expandedScale = 0.96f
            val collapsedWidth = cardFullWidth * collapsedScale

            val maxCollapsedSpan = minOf(containerWidth * 0.24f, 160.dp)
            val collapsedStep = (maxCollapsedSpan / count.coerceAtLeast(1)).coerceIn(13.dp, 22.dp)
            val expandedStep = ((containerWidth * 0.62f) / count.coerceAtLeast(1)).coerceIn(40.dp, 76.dp)

            cards.forEachIndexed { index, card ->
                val key = cardKey(card)
                val isThisDragging = draggingCardKey != null && draggingCardKey == key
                val centerIndex = (count - 1) / 2f
                val offsetFromCenter = index - centerIndex

                val targetScale = when {
                    isThisDragging -> 1.06f
                    isExpanded -> expandedScale
                    else -> collapsedScale
                }
                val targetCardWidth = cardFullWidth * targetScale
                val targetCardHeight = cardFullHeight * targetScale

                // Target X from BottomCenter:
                // - Collapsed: anchored at bottom-right corner with only left mana edge exposed per card
                // - Expanded: centered curved fan at BottomCenter
                val targetX: Dp = if (!isExpanded) {
                    val reverseIndex = (count - 1) - index
                    val rightmostCenterX = (containerWidth / 2f) - (collapsedWidth / 2f) - rightCollapsedMargin
                    rightmostCenterX - (collapsedStep * reverseIndex)
                } else {
                    expandedStep * offsetFromCenter
                }

                // Target Y from BottomCenter:
                // - Collapsed: tucked snugly at bottom edge
                // - Expanded: lifted up with subtle arch curve (center cards slightly higher than outer cards)
                val archDrop = (abs(offsetFromCenter) * abs(offsetFromCenter) * 0.9f).dp.coerceAtMost(10.dp)
                val targetY: Dp = if (!isExpanded) {
                    -bottomCollapsedMargin
                } else {
                    (-16).dp + archDrop
                }

                // Fan rotation angle
                val targetRotation = if (!isExpanded) {
                    -4f + (index * (6f / count.coerceAtLeast(1)))
                } else {
                    (offsetFromCenter * 3.2f).coerceIn(-14f, 14f)
                }

                val springSpecDp = spring<Dp>(stiffness = Spring.StiffnessMediumLow)
                val springSpecFloat = spring<Float>(stiffness = Spring.StiffnessMediumLow)

                val animX by animateDpAsState(targetValue = targetX, animationSpec = springSpecDp, label = "hand_x")
                val animY by animateDpAsState(targetValue = targetY, animationSpec = springSpecDp, label = "hand_y")
                val animWidth by animateDpAsState(targetValue = targetCardWidth, animationSpec = springSpecDp, label = "hand_w")
                val animHeight by animateDpAsState(targetValue = targetCardHeight, animationSpec = springSpecDp, label = "hand_h")
                val animRotation by animateFloatAsState(targetValue = targetRotation, animationSpec = springSpecFloat, label = "hand_rot")

                var cardCoords by remember(key) { mutableStateOf<LayoutCoordinates?>(null) }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(if (isThisDragging) 100f else index.toFloat())
                        .offset {
                            IntOffset(
                                x = animX.roundToPx(),
                                y = animY.roundToPx()
                            )
                        }
                        .graphicsLayer {
                            transformOrigin = TransformOrigin.Center
                            rotationZ = if (isThisDragging) 0f else animRotation
                        }
                        .size(width = animWidth, height = animHeight)
                        .onGloballyPositioned { cardCoords = it }
                        .pointerInput(key, canDragCards) {
                            // Dragging onto battlefield works seamlessly from expanded hand and directly via swipe-up from collapsed hand
                            if (currentCanDrag) {
                                var latestRootPos = Offset.Zero
                                detectDragGestures(
                                    onDragStart = { localOffset ->
                                        val latestCard = currentCards.getOrNull(index) ?: card
                                        latestRootPos = cardCoords?.localToRoot(localOffset) ?: localOffset
                                        if (!currentIsExpanded) {
                                            onExpandedChange(true)
                                        }
                                        onCardDragStart(latestCard, latestRootPos)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val latestCard = currentCards.getOrNull(index) ?: card
                                        latestRootPos = cardCoords?.localToRoot(change.position) ?: change.position
                                        onCardDrag(latestCard, latestRootPos)
                                    },
                                    onDragEnd = {
                                        val latestCard = currentCards.getOrNull(index) ?: card
                                        onCardDragEnd(latestCard, latestRootPos)
                                    },
                                    onDragCancel = {
                                        val latestCard = currentCards.getOrNull(index) ?: card
                                        onCardDragEnd(latestCard, Offset(-9999f, -9999f))
                                    }
                                )
                            }
                        }
                ) {
                    cardContent(card, isExpanded, isThisDragging, animWidth, animHeight)
                }
            }
        }
    }
}
