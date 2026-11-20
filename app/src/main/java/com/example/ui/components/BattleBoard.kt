package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SportsKabaddi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.engine.PriorityHolder
import com.example.engine.SpellStackEntry
import com.example.model.CardCatalog
import com.example.model.CardType
import com.example.model.Keyword
import com.example.model.UnitOnBoard
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.ParchmentWhite

enum class BattleBoardDropHighlight {
    NONE,
    VALID,
    INVALID
}

/**
 * Seamless LoR Path of Champions three-tier landscape battlefield:
 * 1. Top: AI Bench anchored along top carved stone rim (up to 6 units, no empty slot boxes)
 * 2. Center: Frosted ice arena over the golden centerline for active combat lanes & floating circular spell stack
 * 3. Bottom: Player Bench anchored along bottom carved stone rim (up to 6 units)
 */
@Composable
fun BattleBoard(
    combat: CombatState,
    modifier: Modifier = Modifier,
    playerBenchHighlight: BattleBoardDropHighlight = BattleBoardDropHighlight.NONE,
    battlefieldHighlight: BattleBoardDropHighlight = BattleBoardDropHighlight.NONE,
    draggingBenchUnitId: String? = null,
    draggingCombatUnitId: String? = null,
    isDraggingBenchBlocker: Boolean = false,
    draggedBenchUnit: UnitOnBoard? = null,
    draggedCombatUnit: UnitOnBoard? = null,
    tileWidthOverride: Dp? = null,
    tileHeightOverride: Dp? = null,
    unitGapOverride: Dp? = null,
    statFontSizeOverride: TextUnit? = null,
    descFontSizeOverride: TextUnit? = null,
    highlightedTargetEnemyIds: Set<String> = emptySet(),
    highlightedTargetAllyIds: Set<String> = emptySet(),
    onPlayerBenchBoundsChanged: (Rect) -> Unit = {},
    onSharedBattlefieldBoundsChanged: (Rect) -> Unit = {},
    onEnemyAttackerBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onEnemyBenchUnitBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onPlayerUnitBoundsChanged: (String, Rect) -> Unit = { _, _ -> },
    onPlayerUnitClick: (UnitOnBoard) -> Unit = {},
    onEnemyUnitClick: (UnitOnBoard) -> Unit = {},
    onUnitLongClick: (UnitOnBoard) -> Unit = {},
    onBenchUnitDragStart: (UnitOnBoard, Offset) -> Unit = { _, _ -> },
    onBenchUnitDrag: (UnitOnBoard, Offset) -> Unit = { _, _ -> },
    onBenchUnitDragEnd: (UnitOnBoard, Offset) -> Unit = { _, _ -> },
    onCombatUnitDragStart: (UnitOnBoard, Offset) -> Unit = { _, _ -> },
    onCombatUnitDrag: (UnitOnBoard, Offset) -> Unit = { _, _ -> },
    onCombatUnitDragEnd: (UnitOnBoard, Offset) -> Unit = { _, _ -> },
    onResolveStack: () -> Unit = {},
    onInspectStackEntry: (SpellStackEntry) -> Unit = {}
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .testTag("battle_board")
    ) {
        val boardWidth = maxWidth
        val zoneSpacing = (maxHeight * 0.010f).coerceIn(2.dp, 4.dp)
        val usableHeight = (maxHeight - zoneSpacing * 2).coerceAtLeast(156.dp)

        val benchWeight = 0.262f
        val fieldWeight = 0.476f
        val benchZoneHeight = usableHeight * benchWeight
        val fieldZoneHeight = usableHeight * fieldWeight

        val unitGap = unitGapOverride ?: (boardWidth * 0.014f).coerceIn(5.dp, 14.dp)
        val maxSlotWidth = ((boardWidth - 16.dp - unitGap * 5) / 6f).coerceAtLeast(52.dp)
        val computedTileHeight = (benchZoneHeight * 0.94f).coerceAtLeast(54.dp)
        val computedTileWidth = minOf(maxSlotWidth, computedTileHeight * 0.82f).coerceAtLeast(52.dp)

        val tileWidth = tileWidthOverride ?: computedTileWidth
        val tileHeight = tileHeightOverride ?: computedTileHeight
        val statFontSize = statFontSizeOverride ?: (usableHeight.value * 0.036f).coerceIn(11f, 17f).sp
        val descFontSize = descFontSizeOverride ?: (usableHeight.value * 0.025f).coerceIn(9.5f, 13f).sp

        // Partition units so no unit renders simultaneously on Bench and Shared Battlefield
        val enemyCombatIds = when {
            combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && combat.playerInitiatedCombat ->
                combat.challengedPairings.values.toSet()
            combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && !combat.playerInitiatedCombat ->
                combat.attackingUnitIds.toSet()
            combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && combat.playerInitiatedCombat ->
                combat.blockPairings.values.toSet()
            else -> emptySet()
        }
        val enemyBenchUnits = combat.enemyBoard.filterNot { enemyCombatIds.contains(it.instanceId) }

        val playerAttackingIds = if (combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            (combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && combat.playerInitiatedCombat)
        ) {
            combat.attackingUnitIds.toSet()
        } else {
            emptySet()
        }
        val playerBlockingIds = if (combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && !combat.playerInitiatedCombat) {
            combat.blockPairings.values.toSet()
        } else {
            emptySet()
        }
        val playerBenchUnits = combat.playerBoard.filterNot {
            playerAttackingIds.contains(it.instanceId) || playerBlockingIds.contains(it.instanceId)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(zoneSpacing)
        ) {
            // 1. TOP RIM: Seamless AI Bench (up to 6 units, auto-centered)
            SeamlessBenchZone(
                units = enemyBenchUnits,
                isEnemy = true,
                combat = combat,
                availableWidth = boardWidth,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                unitGap = unitGap,
                statFontSize = statFontSize,
                dropHighlight = BattleBoardDropHighlight.NONE,
                draggingUnitId = null,
                highlightedTargetIds = highlightedTargetEnemyIds,
                zoneTestTag = "enemy_bench_zone",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(benchWeight),
                onUnitBoundsChanged = onEnemyBenchUnitBoundsChanged,
                onUnitClick = onEnemyUnitClick,
                onUnitLongClick = onUnitLongClick,
                onUnitDragStart = null,
                onUnitDrag = null,
                onUnitDragEnd = null
            )

            // 2. CENTER ARENA: Open Frosted Ice Battlefield + Floating Center-Left Circular Spell Stack
            SeamlessIceBattlefieldZone(
                combat = combat,
                availableWidth = boardWidth,
                zoneHeight = fieldZoneHeight,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                unitGap = unitGap,
                statFontSize = statFontSize,
                descFontSize = descFontSize,
                dropHighlight = battlefieldHighlight,
                draggingUnitId = draggingCombatUnitId,
                isDraggingBenchBlocker = isDraggingBenchBlocker,
                draggedBenchUnit = draggedBenchUnit,
                draggedCombatUnit = draggedCombatUnit,
                highlightedTargetEnemyIds = highlightedTargetEnemyIds,
                highlightedTargetAllyIds = highlightedTargetAllyIds,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(fieldWeight),
                onBoundsChanged = onSharedBattlefieldBoundsChanged,
                onEnemyAttackerBoundsChanged = onEnemyAttackerBoundsChanged,
                onEnemyUnitBoundsChanged = onEnemyBenchUnitBoundsChanged,
                onPlayerUnitBoundsChanged = onPlayerUnitBoundsChanged,
                onPlayerUnitClick = onPlayerUnitClick,
                onEnemyUnitClick = onEnemyUnitClick,
                onUnitLongClick = onUnitLongClick,
                onCombatUnitDragStart = onCombatUnitDragStart,
                onCombatUnitDrag = onCombatUnitDrag,
                onCombatUnitDragEnd = onCombatUnitDragEnd,
                onResolveStack = onResolveStack,
                onInspectStackEntry = onInspectStackEntry
            )

            // 3. BOTTOM RIM: Seamless Player Bench (up to 6 units, auto-centered)
            SeamlessBenchZone(
                units = playerBenchUnits,
                isEnemy = false,
                combat = combat,
                availableWidth = boardWidth,
                tileWidth = tileWidth,
                tileHeight = tileHeight,
                unitGap = unitGap,
                statFontSize = statFontSize,
                dropHighlight = playerBenchHighlight,
                draggingUnitId = draggingBenchUnitId,
                highlightedTargetIds = highlightedTargetAllyIds,
                zoneTestTag = "player_bench_zone",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(benchWeight)
                    .onGloballyPositioned { onPlayerBenchBoundsChanged(it.boundsInRoot()) },
                onUnitBoundsChanged = onPlayerUnitBoundsChanged,
                onUnitClick = onPlayerUnitClick,
                onUnitLongClick = onUnitLongClick,
                onUnitDragStart = onBenchUnitDragStart,
                onUnitDrag = onBenchUnitDrag,
                onUnitDragEnd = onBenchUnitDragEnd
            )
        }
    }
}

@Composable
private fun SeamlessBenchZone(
    units: List<UnitOnBoard>,
    isEnemy: Boolean,
    combat: CombatState,
    availableWidth: Dp,
    tileWidth: Dp,
    tileHeight: Dp,
    unitGap: Dp,
    statFontSize: TextUnit,
    dropHighlight: BattleBoardDropHighlight,
    draggingUnitId: String?,
    highlightedTargetIds: Set<String>,
    zoneTestTag: String,
    modifier: Modifier = Modifier,
    onUnitBoundsChanged: (String, Rect) -> Unit,
    onUnitClick: (UnitOnBoard) -> Unit,
    onUnitLongClick: (UnitOnBoard) -> Unit,
    onUnitDragStart: ((UnitOnBoard, Offset) -> Unit)?,
    onUnitDrag: ((UnitOnBoard, Offset) -> Unit)?,
    onUnitDragEnd: ((UnitOnBoard, Offset) -> Unit)?
) {
    val dropModifier = when (dropHighlight) {
        BattleBoardDropHighlight.VALID -> Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ArcaneCyan.copy(alpha = 0.14f), ArcaneCyan.copy(alpha = 0.05f))
                )
            )
            .border(1.6.dp, ArcaneCyan.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
        BattleBoardDropHighlight.INVALID -> Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(CrimsonNexus.copy(alpha = 0.12f))
            .border(1.4.dp, CrimsonNexus.copy(alpha = 0.65f), RoundedCornerShape(14.dp))
        BattleBoardDropHighlight.NONE -> Modifier
    }

    Box(
        modifier = modifier
            .testTag(zoneTestTag)
            .then(dropModifier),
        contentAlignment = if (isEnemy) Alignment.TopCenter else Alignment.BottomCenter
    ) {
        if (units.isNotEmpty()) {
            val isBlockCandidatePhase = !isEnemy &&
                combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                !combat.playerInitiatedCombat &&
                combat.selectedEnemyAttackerForBlock != null
            val targetEnemy = if (isBlockCandidatePhase) {
                combat.enemyBoard.find { it.instanceId == combat.selectedEnemyAttackerForBlock }
            } else {
                null
            }

            val visibleUnits = units.take(6)
            val totalRowWidth = tileWidth * visibleUnits.size + unitGap * (visibleUnits.size - 1).coerceAtLeast(0)
            val needsScroll = totalRowWidth > (availableWidth - 8.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                    .then(if (needsScroll) Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp) else Modifier),
                horizontalArrangement = Arrangement.spacedBy(unitGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                visibleUnits.forEach { unit ->
                    val canDragUnit = !isEnemy &&
                        onUnitDragStart != null &&
                        combat.priorityHolder == PriorityHolder.PLAYER &&
                        ((combat.phase == CombatPhase.MAIN_ACTION_PHASE &&
                            combat.playerHasAttackToken &&
                            combat.spellStack.isEmpty() &&
                            unit.canAttack) ||
                            (combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                                combat.playerHasAttackToken &&
                                combat.playerInitiatedCombat &&
                                combat.spellStack.isEmpty() &&
                                unit.canAttack) ||
                            (combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                                !combat.playerInitiatedCombat &&
                                !combat.blockCommitted &&
                                unit.canBlock))

                    var itemCoords by remember(unit.instanceId) { mutableStateOf<LayoutCoordinates?>(null) }
                    val latestUnit by rememberUpdatedState(unit)
                    val latestOnDragStart by rememberUpdatedState(onUnitDragStart)
                    val latestOnDrag by rememberUpdatedState(onUnitDrag)
                    val latestOnDragEnd by rememberUpdatedState(onUnitDragEnd)

                    Box(
                        modifier = Modifier
                            .onGloballyPositioned {
                                itemCoords = it
                                onUnitBoundsChanged(unit.instanceId, it.boundsInRoot())
                            }
                            .then(
                                if (canDragUnit) {
                                    Modifier.pointerInput(unit.instanceId, canDragUnit) {
                                        var latestRootPos = Offset.Zero
                                        detectDragGestures(
                                            onDragStart = { localOffset ->
                                                latestRootPos = itemCoords?.localToRoot(localOffset) ?: localOffset
                                                latestOnDragStart?.invoke(latestUnit, latestRootPos)
                                            },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                latestRootPos = itemCoords?.localToRoot(change.position) ?: change.position
                                                latestOnDrag?.invoke(latestUnit, latestRootPos)
                                            },
                                            onDragEnd = { latestOnDragEnd?.invoke(latestUnit, latestRootPos) },
                                            onDragCancel = { latestOnDragEnd?.invoke(latestUnit, Offset(-9999f, -9999f)) }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        val selectedChallengerUnit = combat.selectedAttackerForChallenger?.let { cid ->
                            combat.playerBoard.find { it.instanceId == cid }
                        }
                        val isChallengerEligible = isEnemy &&
                            selectedChallengerUnit != null &&
                            unit.template.type != CardType.LANDMARK &&
                            !unit.isStunned &&
                            !unit.isShackled &&
                            (selectedChallengerUnit.keywords.contains(Keyword.CHALLENGER) ||
                                unit.keywords.contains(Keyword.VULNERABLE))
                        val isTargetHighlighted = highlightedTargetIds.contains(unit.instanceId)
                        UnitToken(
                            unit = unit,
                            isAttacking = combat.attackingUnitIds.contains(unit.instanceId),
                            isSelected = (isEnemy && combat.selectedEnemyAttackerForBlock == unit.instanceId) ||
                                isTargetHighlighted ||
                                isChallengerEligible,
                            isValidBlockCandidate = targetEnemy != null && unit.canBlockAttacker(targetEnemy),
                            isDragging = draggingUnitId == unit.instanceId,
                            tileWidth = tileWidth,
                            tileHeight = tileHeight,
                            statFontSize = statFontSize,
                            testTag = "board_unit_${unit.instanceId}",
                            onClick = { onUnitClick(unit) },
                            onLongClick = { onUnitLongClick(unit) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeamlessIceBattlefieldZone(
    combat: CombatState,
    availableWidth: Dp,
    zoneHeight: Dp,
    tileWidth: Dp,
    tileHeight: Dp,
    unitGap: Dp,
    statFontSize: TextUnit,
    descFontSize: TextUnit,
    dropHighlight: BattleBoardDropHighlight,
    draggingUnitId: String?,
    isDraggingBenchBlocker: Boolean,
    draggedBenchUnit: UnitOnBoard?,
    draggedCombatUnit: UnitOnBoard? = null,
    highlightedTargetEnemyIds: Set<String>,
    highlightedTargetAllyIds: Set<String>,
    modifier: Modifier = Modifier,
    onBoundsChanged: (Rect) -> Unit,
    onEnemyAttackerBoundsChanged: (String, Rect) -> Unit,
    onEnemyUnitBoundsChanged: (String, Rect) -> Unit,
    onPlayerUnitBoundsChanged: (String, Rect) -> Unit,
    onPlayerUnitClick: (UnitOnBoard) -> Unit,
    onEnemyUnitClick: (UnitOnBoard) -> Unit,
    onUnitLongClick: (UnitOnBoard) -> Unit,
    onCombatUnitDragStart: (UnitOnBoard, Offset) -> Unit,
    onCombatUnitDrag: (UnitOnBoard, Offset) -> Unit,
    onCombatUnitDragEnd: (UnitOnBoard, Offset) -> Unit,
    onResolveStack: () -> Unit,
    onInspectStackEntry: (SpellStackEntry) -> Unit
) {
    val isPlayerPriority = combat.priorityHolder == PriorityHolder.PLAYER
    val hasActiveCombatUnits = combat.attackingUnitIds.isNotEmpty() &&
        (combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE || combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE)

    val dropModifier = when (dropHighlight) {
        BattleBoardDropHighlight.VALID -> Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    listOf(ArcaneCyan.copy(alpha = 0.14f), ArcaneCyan.copy(alpha = 0.06f))
                )
            )
            .border(1.6.dp, ArcaneCyan.copy(alpha = 0.72f), RoundedCornerShape(16.dp))
        BattleBoardDropHighlight.INVALID -> Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CrimsonNexus.copy(alpha = 0.12f))
            .border(1.4.dp, CrimsonNexus.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
        BattleBoardDropHighlight.NONE -> Modifier
    }

    Box(
        modifier = modifier
            .testTag("shared_battlefield_zone")
            .onGloballyPositioned { onBoundsChanged(it.boundsInRoot()) }
            .then(dropModifier),
        contentAlignment = Alignment.Center
    ) {
        // Active Combat Lanes across the horizontal golden centerline
        if (hasActiveCombatUnits) {
            val pairTileHeight = minOf(tileHeight, (zoneHeight * 0.468f).coerceAtLeast(54.dp))
            val pairTileWidth = minOf(tileWidth, pairTileHeight * 0.88f).coerceAtLeast(52.dp)
            val connectorHeight = (zoneHeight - pairTileHeight * 2 - 3.dp).coerceIn(3.dp, 7.dp)

            if (combat.playerInitiatedCombat) {
                val attackers = combat.attackingUnitIds.mapNotNull { id ->
                    combat.playerBoard.find { it.instanceId == id }
                }.take(6)
                val totalRowWidth = pairTileWidth * attackers.size + unitGap * (attackers.size - 1).coerceAtLeast(0)
                val needsScroll = totalRowWidth > (availableWidth - 8.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .then(if (needsScroll) Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(unitGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attackers.forEach { atk ->
                        var atkCoords by remember(atk.instanceId) { mutableStateOf<LayoutCoordinates?>(null) }
                        val latestAtk by rememberUpdatedState(atk)
                        val aiBlockerId = combat.blockPairings[atk.instanceId] ?: combat.challengedPairings[atk.instanceId]
                        val aiBlocker = aiBlockerId?.let { id -> combat.enemyBoard.find { it.instanceId == id } }
                        val isGhostBlocked = aiBlocker == null && combat.confirmedBlockedAttackerIds.contains(atk.instanceId)
                        val isChallengerSelected = combat.selectedAttackerForChallenger == atk.instanceId
                        val isChallengedPreview = combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                            combat.challengedPairings[atk.instanceId] != null

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            // Opposing AI blocker (challenged / declared blocker) or Ghost-Blocked / Open Attack Lane
                            if (aiBlocker != null) {
                                Box(
                                    modifier = Modifier.onGloballyPositioned {
                                        onEnemyUnitBoundsChanged(aiBlocker.instanceId, it.boundsInRoot())
                                    }
                                ) {
                                    UnitToken(
                                        unit = aiBlocker,
                                        isBlockingLabel = if (isChallengedPreview) "BỊ KÉO" else "CHẶN",
                                        isSelected = highlightedTargetEnemyIds.contains(aiBlocker.instanceId),
                                        tileWidth = pairTileWidth,
                                        tileHeight = pairTileHeight,
                                        statFontSize = (statFontSize.value * 0.85f).coerceAtLeast(10f).sp,
                                        testTag = "board_unit_${aiBlocker.instanceId}",
                                        onClick = { onEnemyUnitClick(aiBlocker) },
                                        onLongClick = { onUnitLongClick(aiBlocker) }
                                    )
                                }
                            } else if (isGhostBlocked) {
                                Box(
                                    modifier = Modifier
                                        .size(width = pairTileWidth, height = (pairTileHeight * 0.64f).coerceAtLeast(28.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ArcaneCyan.copy(alpha = 0.14f))
                                        .border(1.3.dp, ArcaneCyan.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                        .testTag("ghost_block_${atk.instanceId}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Đã Bị Chặn",
                                            tint = ArcaneCyan,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "ĐÃ CHẶN",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ArcaneCyan
                                        )
                                    }
                                }
                            } else {
                                val hasPullableEnemy = if (atk.keywords.contains(Keyword.CHALLENGER)) {
                                    combat.enemyBoard.any { it.template.type != CardType.LANDMARK && !it.isStunned && !it.isShackled }
                                } else {
                                    combat.enemyBoard.any {
                                        it.keywords.contains(Keyword.VULNERABLE) &&
                                            it.template.type != CardType.LANDMARK &&
                                            !it.isStunned &&
                                            !it.isShackled
                                    }
                                }
                                val canPullHint = combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && hasPullableEnemy
                                Box(
                                    modifier = Modifier
                                        .size(width = pairTileWidth, height = (pairTileHeight * 0.64f).coerceAtLeast(28.dp))
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isChallengerSelected) ArcaneCyan.copy(alpha = 0.22f)
                                            else LegendaryAmber.copy(alpha = 0.10f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isChallengerSelected) ArcaneCyan else LegendaryAmber.copy(alpha = 0.45f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable(enabled = canPullHint) { onPlayerUnitClick(atk) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.SportsKabaddi,
                                            contentDescription = "Làn Tấn Công",
                                            tint = if (isChallengerSelected) ArcaneCyan else LegendaryAmber,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        if (canPullHint) {
                                            Text(
                                                text = if (isChallengerSelected) "CHỌN ĐỊCH" else "KÉO",
                                                fontSize = 7.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isChallengerSelected) ArcaneCyan else HextechGoldBright
                                            )
                                        }
                                    }
                                }
                            }

                            Canvas(modifier = Modifier.size(width = 8.dp, height = connectorHeight)) {
                                if (size.width <= 1f || size.height <= 1f) return@Canvas
                                drawLine(
                                    color = when {
                                        aiBlocker != null -> CrimsonNexus
                                        isGhostBlocked -> ArcaneCyan
                                        else -> LegendaryAmber
                                    },
                                    start = Offset(size.width / 2f, size.height),
                                    end = Offset(size.width / 2f, 0f),
                                    strokeWidth = 3.5f,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Bottom Player Attacker Token
                            val canDragAttacker = combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                                isPlayerPriority &&
                                combat.playerHasAttackToken &&
                                combat.spellStack.isEmpty()
                            val latestDragStart by rememberUpdatedState(onCombatUnitDragStart)
                            val latestDrag by rememberUpdatedState(onCombatUnitDrag)
                            val latestDragEnd by rememberUpdatedState(onCombatUnitDragEnd)
                            Box(
                                modifier = Modifier
                                    .onGloballyPositioned {
                                        atkCoords = it
                                        onPlayerUnitBoundsChanged(atk.instanceId, it.boundsInRoot())
                                    }
                                    .then(
                                        if (canDragAttacker) {
                                            Modifier.pointerInput(atk.instanceId, canDragAttacker) {
                                                var latestRoot = Offset.Zero
                                                detectDragGestures(
                                                    onDragStart = { local ->
                                                        latestRoot = atkCoords?.localToRoot(local) ?: local
                                                        latestDragStart(latestAtk, latestRoot)
                                                    },
                                                    onDrag = { change, _ ->
                                                        change.consume()
                                                        latestRoot = atkCoords?.localToRoot(change.position) ?: change.position
                                                        latestDrag(latestAtk, latestRoot)
                                                    },
                                                    onDragEnd = { latestDragEnd(latestAtk, latestRoot) },
                                                    onDragCancel = { latestDragEnd(latestAtk, Offset(-9999f, -9999f)) }
                                                )
                                            }
                                        } else {
                                            Modifier
                                        }
                                    )
                            ) {
                                UnitToken(
                                    unit = atk,
                                    isAttacking = true,
                                    isSelected = isChallengerSelected || highlightedTargetAllyIds.contains(atk.instanceId) ||
                                        (combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && highlightedTargetAllyIds.isEmpty() && highlightedTargetEnemyIds.isEmpty()),
                                    isDragging = draggingUnitId == atk.instanceId,
                                    tileWidth = pairTileWidth,
                                    tileHeight = pairTileHeight,
                                    statFontSize = (statFontSize.value * 0.88f).coerceAtLeast(10f).sp,
                                    testTag = "board_unit_${atk.instanceId}",
                                    onClick = { onPlayerUnitClick(atk) },
                                    onLongClick = { onUnitLongClick(atk) }
                                )
                            }
                        }
                    }
                }
            } else {
                val enemyAttackers = combat.attackingUnitIds.mapNotNull { id ->
                    combat.enemyBoard.find { it.instanceId == id }
                }.take(6)
                val totalRowWidth = pairTileWidth * enemyAttackers.size + unitGap * (enemyAttackers.size - 1).coerceAtLeast(0)
                val needsScroll = totalRowWidth > (availableWidth - 8.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(spring(stiffness = Spring.StiffnessMediumLow))
                        .then(if (needsScroll) Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp) else Modifier),
                    horizontalArrangement = Arrangement.spacedBy(unitGap, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeBlockDragUnit = when {
                        isDraggingBenchBlocker -> draggedBenchUnit
                        combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                            !combat.playerInitiatedCombat &&
                            !combat.blockCommitted -> draggedCombatUnit
                        else -> null
                    }
                    enemyAttackers.forEach { enemyAtk ->
                        val blockerId = combat.blockPairings[enemyAtk.instanceId]
                        val blocker = blockerId?.let { id -> combat.playerBoard.find { it.instanceId == id } }
                        val isGhostBlocked = blocker == null && combat.confirmedBlockedAttackerIds.contains(enemyAtk.instanceId)
                        val isLockedBlock = combat.lockedBlockAttackerIds.contains(enemyAtk.instanceId)
                        val isTargeted = combat.selectedEnemyAttackerForBlock == enemyAtk.instanceId
                        val canDraggedBlockThis = activeBlockDragUnit != null &&
                            !isLockedBlock &&
                            activeBlockDragUnit.canBlockAttacker(enemyAtk)

                        Column(
                            modifier = Modifier.onGloballyPositioned {
                                onEnemyAttackerBoundsChanged(enemyAtk.instanceId, it.boundsInRoot())
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            // Top Enemy Attacker Token
                            Box(
                                modifier = Modifier.onGloballyPositioned {
                                    onEnemyUnitBoundsChanged(enemyAtk.instanceId, it.boundsInRoot())
                                }
                            ) {
                                UnitToken(
                                    unit = enemyAtk,
                                    isAttacking = true,
                                    isSelected = isTargeted || canDraggedBlockThis || highlightedTargetEnemyIds.contains(enemyAtk.instanceId),
                                    tileWidth = pairTileWidth,
                                    tileHeight = pairTileHeight,
                                    statFontSize = (statFontSize.value * 0.85f).coerceAtLeast(10f).sp,
                                    testTag = "board_unit_${enemyAtk.instanceId}",
                                    onClick = { onEnemyUnitClick(enemyAtk) },
                                    onLongClick = { onUnitLongClick(enemyAtk) }
                                )
                            }

                            // Clash Connector
                            Canvas(modifier = Modifier.size(width = 8.dp, height = connectorHeight)) {
                                if (size.width <= 1f || size.height <= 1f) return@Canvas
                                drawLine(
                                    color = when {
                                        blocker != null -> EmeraldHeal
                                        isGhostBlocked -> ArcaneCyan
                                        else -> CrimsonNexus.copy(alpha = 0.8f)
                                    },
                                    start = Offset(size.width / 2f, 0f),
                                    end = Offset(size.width / 2f, size.height),
                                    strokeWidth = if (blocker != null || isGhostBlocked) 4f else 2.5f,
                                    cap = StrokeCap.Round
                                )
                            }

                            // Bottom Player Blocker, Ghost-Block Indicator, or Blocker Target Slot
                            if (blocker != null) {
                                var blkCoords by remember(blocker.instanceId) { mutableStateOf<LayoutCoordinates?>(null) }
                                val latestBlocker by rememberUpdatedState(blocker)
                                val canDragBlocker = isPlayerPriority && !combat.blockCommitted && !isLockedBlock
                                val latestBlockerDragStart by rememberUpdatedState(onCombatUnitDragStart)
                                val latestBlockerDrag by rememberUpdatedState(onCombatUnitDrag)
                                val latestBlockerDragEnd by rememberUpdatedState(onCombatUnitDragEnd)
                                Box(
                                    modifier = Modifier
                                        .onGloballyPositioned {
                                            blkCoords = it
                                            onPlayerUnitBoundsChanged(blocker.instanceId, it.boundsInRoot())
                                        }
                                        .then(
                                            if (canDragBlocker) {
                                                Modifier.pointerInput(blocker.instanceId, canDragBlocker) {
                                                    var latestRoot = Offset.Zero
                                                    detectDragGestures(
                                                        onDragStart = { local ->
                                                            latestRoot = blkCoords?.localToRoot(local) ?: local
                                                            latestBlockerDragStart(latestBlocker, latestRoot)
                                                        },
                                                        onDrag = { change, _ ->
                                                            change.consume()
                                                            latestRoot = blkCoords?.localToRoot(change.position) ?: change.position
                                                            latestBlockerDrag(latestBlocker, latestRoot)
                                                        },
                                                        onDragEnd = { latestBlockerDragEnd(latestBlocker, latestRoot) },
                                                        onDragCancel = { latestBlockerDragEnd(latestBlocker, Offset(-9999f, -9999f)) }
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    UnitToken(
                                        unit = blocker,
                                        isBlockingLabel = if (isLockedBlock) "BỊ KÉO" else "CHẶN",
                                        isSelected = (canDraggedBlockThis && blocker.instanceId != activeBlockDragUnit.instanceId) ||
                                            highlightedTargetAllyIds.contains(blocker.instanceId),
                                        isDragging = draggingUnitId == blocker.instanceId,
                                        tileWidth = pairTileWidth,
                                        tileHeight = pairTileHeight,
                                        statFontSize = (statFontSize.value * 0.85f).coerceAtLeast(10f).sp,
                                        testTag = "board_unit_${blocker.instanceId}",
                                        onClick = { onPlayerUnitClick(blocker) },
                                        onLongClick = { onUnitLongClick(blocker) }
                                    )
                                }
                            } else if (isGhostBlocked) {
                                Box(
                                    modifier = Modifier
                                        .size(width = pairTileWidth, height = pairTileHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ArcaneCyan.copy(alpha = 0.14f))
                                        .border(1.3.dp, ArcaneCyan.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                                        .testTag("ghost_block_${enemyAtk.instanceId}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(
                                            imageVector = Icons.Default.Shield,
                                            contentDescription = "Đã Bị Chặn",
                                            tint = ArcaneCyan,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "ĐÃ CHẶN",
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = ArcaneCyan
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(width = pairTileWidth, height = pairTileHeight)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (canDraggedBlockThis) ArcaneCyan.copy(alpha = 0.22f) else Color(0x361C3454)
                                        )
                                        .border(
                                            if (isTargeted || canDraggedBlockThis) 1.8.dp else 1.2.dp,
                                            if (isTargeted || canDraggedBlockThis) ArcaneCyan else CrimsonNexus.copy(alpha = 0.65f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable(enabled = isPlayerPriority && !combat.blockCommitted) {
                                            onEnemyUnitClick(enemyAtk)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Chọn Bài Chặn",
                                        tint = if (isTargeted || canDraggedBlockThis) ArcaneCyan else ParchmentWhite.copy(alpha = 0.65f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Circular Spell/Skill Stack Medallions (Center-Left Lane, Auto-Hidden When Empty)
        val previewAttackSkills = if (combat.spellStack.isEmpty() &&
            combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
            combat.playerInitiatedCombat
        ) {
            combat.attackingUnitIds.mapNotNull { atkId ->
                val atk = combat.playerBoard.find { it.instanceId == atkId }
                if (atk != null && atk.template.id == "card_anivia" && !atk.isSilenced && !atk.isStunned) {
                    val dmg = if (atk.isLeveledUp) 2 else 1
                    SpellStackEntry(
                        id = "preview_${atk.instanceId}",
                        sourceIsPlayer = true,
                        card = CardCatalog.getCard("skill_glacial_storm").copy(effectValue = dmg),
                        targetUnitId = null,
                        damageBonus = 0
                    )
                } else null
            }
        } else {
            emptyList()
        }
        val displayedStack = if (combat.spellStack.isNotEmpty()) combat.spellStack else previewAttackSkills
        val isPreviewStack = combat.spellStack.isEmpty() && previewAttackSkills.isNotEmpty()

        AnimatedVisibility(
            visible = displayedStack.isNotEmpty(),
            enter = fadeIn() + scaleIn(initialScale = 0.82f),
            exit = fadeOut() + scaleOut(targetScale = 0.82f),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 6.dp)
        ) {
            FloatingSpellStackLane(
                stack = displayedStack,
                isPreview = isPreviewStack,
                isConfirmingPlayerAttack = combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    combat.playerInitiatedCombat &&
                    combat.blockCommitted,
                isPlayerPriority = isPlayerPriority,
                medallionSize = (zoneHeight * 0.34f).coerceIn(38.dp, 54.dp),
                descFontSize = descFontSize,
                onInspectEntry = onInspectStackEntry,
                onResolveStack = onResolveStack
            )
        }
    }
}

@Composable
private fun FloatingSpellStackLane(
    stack: List<SpellStackEntry>,
    isPreview: Boolean,
    isConfirmingPlayerAttack: Boolean,
    isPlayerPriority: Boolean,
    medallionSize: Dp,
    descFontSize: TextUnit,
    onInspectEntry: (SpellStackEntry) -> Unit,
    onResolveStack: () -> Unit
) {
    val stackAnim = rememberInfiniteTransition(label = "stack_medallion_orbit")
    val orbitAngle by stackAnim.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "stack_orbit_angle"
    )
    val pulseScale by stackAnim.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "stack_pulse_scale"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        stack.take(5).forEach { item ->
            val ringColor = if (item.sourceIsPlayer) ArcaneCyan else CrimsonNexus
            val portraitRes = cardPortraitRes(item.card.id)
            val entryScale = remember(item.id) { Animatable(0.55f) }
            LaunchedEffect(item.id) {
                entryScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(medallionSize)
                    .graphicsLayer {
                        val combinedScale = entryScale.value * if (isPreview) 1f else pulseScale
                        scaleX = combinedScale
                        scaleY = combinedScale
                    }
                    .testTag("spell_stack_entry_${item.card.id}")
                    .clip(CircleShape)
                    .background(Color(0xEE223656))
                    .border(2.2.dp, if (isPreview) HextechGoldBright else ringColor, CircleShape)
                    .border(3.5.dp, HextechGold.copy(alpha = 0.65f), CircleShape)
                    .clickable { onInspectEntry(item) },
                contentAlignment = Alignment.Center
            ) {
                if (portraitRes != null) {
                    Image(
                        painter = painterResource(id = portraitRes),
                        contentDescription = item.card.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = item.card.name,
                        tint = if (item.sourceIsPlayer) ArcaneCyan else CrimsonNexus,
                        modifier = Modifier.size(medallionSize * 0.48f)
                    )
                }

                // Rotating glowing arcane rune arc around each queued spell/skill medallion
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeW = 2.dp.toPx()
                    val arcColor = if (isPreview) HextechGoldBright else ringColor
                    drawArc(
                        color = arcColor.copy(alpha = 0.85f),
                        startAngle = orbitAngle,
                        sweepAngle = 72f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = HextechGoldBright.copy(alpha = 0.72f),
                        startAngle = orbitAngle + 180f,
                        sweepAngle = 54f,
                        useCenter = false,
                        style = Stroke(width = strokeW, cap = StrokeCap.Round)
                    )
                }

                // Speed / Owner mini badge
                Surface(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xE81B2C47),
                    border = BorderStroke(0.8.dp, HextechGoldBright)
                ) {
                    Text(
                        text = if (isPreview) "KỸ NĂNG" else item.card.spellSpeed.shortBadge,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (isPreview) HextechGoldBright else ParchmentWhite,
                        modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp)
                    )
                }
            }
        }

        if (isPlayerPriority && !isPreview) {
            Button(
                onClick = onResolveStack,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("resolve_stack_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = HextechGoldBright,
                    contentColor = DarkButtonInk
                )
            ) {
                Text(
                    text = if (isConfirmingPlayerAttack) "Xác Nhận" else "Dùng",
                    fontSize = descFontSize,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
