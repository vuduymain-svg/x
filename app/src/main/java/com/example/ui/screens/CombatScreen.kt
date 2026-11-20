package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.engine.PriorityHolder
import com.example.engine.SpellStackEntry
import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardType
import com.example.model.HandCardInstance
import com.example.model.Keyword
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard
import com.example.ui.components.ArenaBoardNexusOverlay
import com.example.ui.components.BattleBoard
import com.example.ui.components.BattleBoardDropHighlight
import com.example.ui.components.CenterlineScrollButton
import com.example.ui.components.FilmGrainOverlay
import com.example.ui.components.FreljordBattlefieldBackground
import com.example.ui.components.HandCardView
import com.example.ui.components.PlayerHandOverlay
import com.example.ui.components.RightManaActionDial
import com.example.ui.components.SpellSkillEffectOverlay
import com.example.ui.components.SpellSkillVfxEvent
import com.example.ui.components.StackedDeckPile
import com.example.ui.components.UnitToken
import com.example.ui.components.fantasyHeadingStyle
import com.example.ui.components.tabularStatStyle
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ParchmentWhite
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface ActiveDragState {
    val pointerInRoot: Offset

    data class HandCardDrag(
        val card: HandCardInstance,
        override val pointerInRoot: Offset,
        val startPointerInRoot: Offset = pointerInRoot
    ) : ActiveDragState

    data class BenchUnitDrag(
        val unit: UnitOnBoard,
        override val pointerInRoot: Offset
    ) : ActiveDragState

    data class CombatUnitDrag(
        val unit: UnitOnBoard,
        override val pointerInRoot: Offset
    ) : ActiveDragState
}

@Composable
fun CombatScreen(
    combat: CombatState,
    revivesRemaining: Int,
    onPlayCard: (String) -> Unit,
    onPlayTargetedCard: (String, String?) -> Unit = { cardId, _ -> onPlayCard(cardId) },
    onEnterAttack: () -> Unit,
    onCancelAttack: () -> Unit,
    onToggleAttacker: (String) -> Unit,
    onSelectChallengerAttacker: (String?) -> Unit = {},
    onChallengeEnemyUnit: (String, String) -> Unit = { _, _ -> },
    onClearChallenge: (String) -> Unit = {},
    onDragBenchUnitToAttack: (String) -> Unit = {},
    onRemoveAttacker: (String) -> Unit = {},
    onCommitAttack: () -> Unit,
    onSelectEnemyAttackerForBlock: (String?) -> Unit,
    onAssignBlocker: (String, String) -> Unit,
    onRemoveBlocker: (String) -> Unit,
    onCommitBlock: () -> Unit,
    onResolveStack: () -> Unit,
    onPassTurn: () -> Unit,
    onVictoryContinue: () -> Unit,
    onUseRevive: () -> Unit,
    onAcceptDefeat: () -> Unit,
    onAbandonRun: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inspectedHandCard by remember { mutableStateOf<HandCardInstance?>(null) }
    var inspectHandDetailOnly by remember { mutableStateOf(false) }
    var isHandExpanded by remember { mutableStateOf(false) }
    var inspectedUnit by remember { mutableStateOf<UnitOnBoard?>(null) }
    var showLogDialog by remember { mutableStateOf(false) }
    var showPowerInfo by remember { mutableStateOf(false) }
    var showGraveyardDialog by remember { mutableStateOf(false) }
    var showMatchInfoDialog by remember { mutableStateOf(false) }
    var showEmotePicker by remember { mutableStateOf(false) }
    var activeEmote by remember { mutableStateOf<CombatEmoteOption?>(null) }
    var showForfeitConfirm by remember { mutableStateOf(false) }

    var pendingTargetCard by remember { mutableStateOf<HandCardInstance?>(null) }
    var pendingFirstUnitId by remember { mutableStateOf<String?>(null) }

    var activeDrag by remember { mutableStateOf<ActiveDragState?>(null) }
    var dragWarningText by remember { mutableStateOf<String?>(null) }
    var activeLevelUpBanner by remember { mutableStateOf<UnitOnBoard?>(null) }
    val knownChampionLevels = remember { mutableStateMapOf<String, Boolean>() }
    val shownLevelUpTemplates = remember { mutableStateMapOf<String, Boolean>() }
    var champTrackingInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(activeEmote) {
        if (activeEmote != null) {
            kotlinx.coroutines.delay(1800)
            activeEmote = null
        }
    }
    LaunchedEffect(dragWarningText) {
        if (dragWarningText != null) {
            kotlinx.coroutines.delay(1400)
            dragWarningText = null
        }
    }
    LaunchedEffect(activeLevelUpBanner) {
        if (activeLevelUpBanner != null) {
            kotlinx.coroutines.delay(2100)
            activeLevelUpBanner = null
        }
    }
    LaunchedEffect(combat.playerHand, combat.priorityHolder, combat.phase) {
        val current = inspectedHandCard
        if (current != null && combat.playerHand.none { it.instanceId == current.instanceId }) {
            inspectedHandCard = null
        }
        val currentTarget = pendingTargetCard
        if (currentTarget != null &&
            (combat.priorityHolder != PriorityHolder.PLAYER ||
                combat.phase == CombatPhase.GAME_OVER ||
                combat.playerHand.none { it.instanceId == currentTarget.instanceId })
        ) {
            pendingTargetCard = null
            pendingFirstUnitId = null
        }
        if (combat.phase == CombatPhase.GAME_OVER || combat.playerHand.isEmpty()) {
            isHandExpanded = false
        }
    }
    LaunchedEffect(combat.playerBoard, combat.enemyBoard) {
        val current = inspectedUnit
        if (current != null &&
            combat.playerBoard.none { it.instanceId == current.instanceId } &&
            combat.enemyBoard.none { it.instanceId == current.instanceId }
        ) {
            inspectedUnit = null
        }

        val allChamps = (combat.playerBoard + combat.enemyBoard).filter { it.template.isChampion }
        if (!champTrackingInitialized) {
            allChamps.forEach {
                knownChampionLevels[it.instanceId] = it.isLeveledUp
                if (it.isLeveledUp) {
                    shownLevelUpTemplates["${it.ownerIsPlayer}_${it.template.id}"] = true
                }
            }
            champTrackingInitialized = true
        } else {
            val newlyLeveled = allChamps.firstOrNull { u ->
                val champKey = "${u.ownerIsPlayer}_${u.template.id}"
                u.isLeveledUp &&
                    knownChampionLevels[u.instanceId] != true &&
                    shownLevelUpTemplates[champKey] != true
            }
            if (newlyLeveled != null) {
                shownLevelUpTemplates["${newlyLeveled.ownerIsPlayer}_${newlyLeveled.template.id}"] = true
                activeLevelUpBanner = newlyLeveled
            }
            allChamps.forEach { knownChampionLevels[it.instanceId] = it.isLeveledUp }
        }
    }

    // Track Nexus HP deltas for hit reaction
    var prevPlayerHp by remember { mutableIntStateOf(combat.playerNexusHp) }
    var playerNexusDelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(combat.playerNexusHp) {
        if (combat.playerNexusHp < prevPlayerHp) {
            playerNexusDelta = prevPlayerHp - combat.playerNexusHp
            prevPlayerHp = combat.playerNexusHp
            kotlinx.coroutines.delay(650)
            playerNexusDelta = 0
        } else {
            prevPlayerHp = combat.playerNexusHp
        }
    }

    var prevEnemyHp by remember { mutableIntStateOf(combat.enemyNexusHp) }
    var enemyNexusDelta by remember { mutableIntStateOf(0) }
    LaunchedEffect(combat.enemyNexusHp) {
        if (combat.enemyNexusHp < prevEnemyHp) {
            enemyNexusDelta = prevEnemyHp - combat.enemyNexusHp
            prevEnemyHp = combat.enemyNexusHp
            kotlinx.coroutines.delay(650)
            enemyNexusDelta = 0
        } else {
            prevEnemyHp = combat.enemyNexusHp
        }
    }

    // Drop target bounds in root coordinates
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var battleBoardBounds by remember { mutableStateOf(Rect.Zero) }
    var playerBenchBounds by remember { mutableStateOf(Rect.Zero) }
    var sharedBattlefieldBounds by remember { mutableStateOf(Rect.Zero) }
    val enemyAttackerBounds = remember { mutableStateMapOf<String, Rect>() }
    val enemyUnitBounds = remember { mutableStateMapOf<String, Rect>() }
    val playerUnitBounds = remember { mutableStateMapOf<String, Rect>() }
    var enemyNexusBounds by remember { mutableStateOf(Rect.Zero) }

    val density = LocalDensity.current
    val boardDropPadPx = with(density) { 56.dp.toPx() }
    val benchDropPadPx = with(density) { 48.dp.toPx() }
    val targetHitPadPx = with(density) { 30.dp.toPx() }
    val cancelDragThresholdPx = with(density) { 44.dp.toPx() }
    val upwardPlayThresholdPx = with(density) { 52.dp.toPx() }
    val magneticLanePadPx = with(density) { 56.dp.toPx() }

    // Spell & Skill VFX event queue + snapshots
    val activeSpellVfx = remember { mutableStateListOf<SpellSkillVfxEvent>() }
    var vfxSeq by remember { mutableIntStateOf(0) }
    var lastPlayerTargetHint by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var vfxTrackingInitialized by remember { mutableStateOf(false) }
    var prevSpellStackSnapshot by remember { mutableStateOf<List<SpellStackEntry>>(emptyList()) }
    var prevPlayerGraveyardSize by remember { mutableIntStateOf(0) }
    var prevEnemyGraveyardSize by remember { mutableIntStateOf(0) }
    var prevPlayerBoardIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var prevEnemyBoardIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    fun resolveTargetNorm(targetUnitId: String?): Offset? {
        if (targetUnitId == null || rootSize.width <= 0 || rootSize.height <= 0) return null
        val rawId = if (targetUnitId.contains("|")) targetUnitId.substringAfter("|") else targetUnitId
        val rect = when (rawId) {
            "ENEMY_NEXUS", "NEXUS" -> enemyNexusBounds.takeIf { it != Rect.Zero }
            else -> enemyUnitBounds[rawId] ?: playerUnitBounds[rawId]
        } ?: return null
        val localCenter = rect.center - rootOrigin
        return Offset(
            x = (localCenter.x / rootSize.width.toFloat()).coerceIn(0.05f, 0.95f),
            y = (localCenter.y / rootSize.height.toFloat()).coerceIn(0.05f, 0.95f)
        )
    }

    LaunchedEffect(
        combat.spellStack,
        combat.playerGraveyard.size,
        combat.enemyGraveyard.size,
        combat.playerBoard,
        combat.enemyBoard
    ) {
        val currentPlayerIds = combat.playerBoard.map { it.instanceId }.toSet()
        val currentEnemyIds = combat.enemyBoard.map { it.instanceId }.toSet()

        if (!vfxTrackingInitialized) {
            prevSpellStackSnapshot = combat.spellStack
            prevPlayerGraveyardSize = combat.playerGraveyard.size
            prevEnemyGraveyardSize = combat.enemyGraveyard.size
            prevPlayerBoardIds = currentPlayerIds
            prevEnemyBoardIds = currentEnemyIds
            vfxTrackingInitialized = true
            return@LaunchedEffect
        }

        val currentStackIds = combat.spellStack.map { it.id }.toSet()
        val resolvedFromStack = prevSpellStackSnapshot
            .filterNot { it.id in currentStackIds }
            .reversed()

        val handledPlayerIds = mutableMapOf<String, Int>()
        val handledEnemyIds = mutableMapOf<String, Int>()
        val newEvents = mutableListOf<SpellSkillVfxEvent>()

        resolvedFromStack.forEach { entry ->
            val targetMap = if (entry.sourceIsPlayer) handledPlayerIds else handledEnemyIds
            targetMap[entry.card.id] = (targetMap[entry.card.id] ?: 0) + 1
            vfxSeq++
            newEvents += SpellSkillVfxEvent(
                id = "vfx_${vfxSeq}_${entry.card.id}",
                card = entry.card,
                sourceIsPlayer = entry.sourceIsPlayer,
                targetCenterNorm = resolveTargetNorm(entry.targetUnitId),
                isSkill = entry.card.spellSpeed == SpellSpeed.SKILL
            )
        }

        val safePlayerStart = prevPlayerGraveyardSize.coerceAtMost(combat.playerGraveyard.size)
        if (combat.playerGraveyard.size > safePlayerStart) {
            combat.playerGraveyard.subList(safePlayerStart, combat.playerGraveyard.size).forEach { card ->
                if (card.type == CardType.SPELL || card.type == CardType.CURSE) {
                    val remainingSkip = handledPlayerIds[card.id] ?: 0
                    if (remainingSkip > 0) {
                        handledPlayerIds[card.id] = remainingSkip - 1
                    } else {
                        val hintTarget = lastPlayerTargetHint?.takeIf { it.first == card.id }?.second
                        if (lastPlayerTargetHint?.first == card.id) {
                            lastPlayerTargetHint = null
                        }
                        vfxSeq++
                        newEvents += SpellSkillVfxEvent(
                            id = "vfx_${vfxSeq}_${card.id}",
                            card = card,
                            sourceIsPlayer = true,
                            targetCenterNorm = resolveTargetNorm(hintTarget),
                            isSkill = card.spellSpeed == SpellSpeed.SKILL
                        )
                    }
                }
            }
        }

        val safeEnemyStart = prevEnemyGraveyardSize.coerceAtMost(combat.enemyGraveyard.size)
        if (combat.enemyGraveyard.size > safeEnemyStart) {
            combat.enemyGraveyard.subList(safeEnemyStart, combat.enemyGraveyard.size).forEach { card ->
                if (card.type == CardType.SPELL || card.type == CardType.CURSE) {
                    val remainingSkip = handledEnemyIds[card.id] ?: 0
                    if (remainingSkip > 0) {
                        handledEnemyIds[card.id] = remainingSkip - 1
                    } else {
                        vfxSeq++
                        newEvents += SpellSkillVfxEvent(
                            id = "vfx_${vfxSeq}_${card.id}",
                            card = card,
                            sourceIsPlayer = false,
                            targetCenterNorm = null,
                            isSkill = card.spellSpeed == SpellSpeed.SKILL
                        )
                    }
                }
            }
        }

        val onSummonSkillCardIds = setOf(
            "card_crystal_stormbird",
            "card_flame_eyed_caprine",
            "card_rhasa",
            "card_rumul",
            "card_icevale_archer"
        )
        combat.playerBoard
            .filter { it.instanceId !in prevPlayerBoardIds && it.template.id in onSummonSkillCardIds }
            .forEach { u ->
                vfxSeq++
                newEvents += SpellSkillVfxEvent(
                    id = "vfx_${vfxSeq}_${u.template.id}",
                    card = u.template,
                    sourceIsPlayer = true,
                    targetCenterNorm = resolveTargetNorm(u.instanceId),
                    isSkill = true
                )
            }
        combat.enemyBoard
            .filter { it.instanceId !in prevEnemyBoardIds && it.template.id in onSummonSkillCardIds }
            .forEach { u ->
                vfxSeq++
                newEvents += SpellSkillVfxEvent(
                    id = "vfx_${vfxSeq}_${u.template.id}",
                    card = u.template,
                    sourceIsPlayer = false,
                    targetCenterNorm = resolveTargetNorm(u.instanceId),
                    isSkill = true
                )
            }

        if (newEvents.isNotEmpty()) {
            activeSpellVfx.addAll(newEvents)
            while (activeSpellVfx.size > 3) {
                activeSpellVfx.removeAt(0)
            }
        }

        prevSpellStackSnapshot = combat.spellStack
        prevPlayerGraveyardSize = combat.playerGraveyard.size
        prevEnemyGraveyardSize = combat.enemyGraveyard.size
        prevPlayerBoardIds = currentPlayerIds
        prevEnemyBoardIds = currentEnemyIds
    }

    LaunchedEffect(combat.attackingUnitIds) {
        val activeSet = combat.attackingUnitIds.toSet()
        enemyAttackerBounds.keys.retainAll(activeSet)
    }

    BackHandler {
        when {
            activeDrag != null -> activeDrag = null
            pendingTargetCard != null -> {
                pendingTargetCard = null
                pendingFirstUnitId = null
            }
            combat.selectedAttackerForChallenger != null -> onSelectChallengerAttacker(null)
            showEmotePicker -> showEmotePicker = false
            showGraveyardDialog -> showGraveyardDialog = false
            showMatchInfoDialog -> showMatchInfoDialog = false
            inspectedHandCard != null -> inspectedHandCard = null
            isHandExpanded -> isHandExpanded = false
            inspectedUnit != null -> inspectedUnit = null
            showPowerInfo -> showPowerInfo = false
            else -> showForfeitConfirm = true
        }
    }

    val latestCombat by rememberUpdatedState(combat)

    val dispatchTargetedCard: (HandCardInstance, String?) -> Unit = { card, targetId ->
        lastPlayerTargetHint = card.template.id to targetId
        onPlayTargetedCard(card.instanceId, targetId)
    }

    val triggerCardPlayOrTargeting: (HandCardInstance, Offset?) -> Unit = { card, pointer ->
        val c = latestCombat
        val enemyTargets = c.enemyBoard.filter { it.template.type != CardType.LANDMARK }
        val allyTargets = c.playerBoard.filter { it.template.type != CardType.LANDMARK }
        val hitEnemyId = pointer?.let { ptr ->
            enemyUnitBounds.entries.firstOrNull { (id, rect) ->
                enemyTargets.any { it.instanceId == id } && rect.inflate(targetHitPadPx).contains(ptr)
            }?.key
        }
        val hitAllyId = pointer?.let { ptr ->
            playerUnitBounds.entries.firstOrNull { (id, rect) ->
                allyTargets.any { it.instanceId == id } && rect.inflate(targetHitPadPx).contains(ptr)
            }?.key
        }
        val hitEnemyNexus = pointer != null && enemyNexusBounds.inflate(targetHitPadPx).contains(pointer)

        when {
            card.template.type == CardType.EQUIPMENT || card.template.id == "card_brightsteel_protector" -> {
                when {
                    hitAllyId != null -> dispatchTargetedCard(card, hitAllyId)
                    allyTargets.isEmpty() -> {
                        if (card.template.type == CardType.EQUIPMENT) {
                            dragWarningText = "Cần có đồng minh trên bàn để trang bị!"
                        } else {
                            dispatchTargetedCard(card, null)
                        }
                    }
                    else -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                card.template.effectType == CardEffectType.DRAIN -> {
                when {
                    hitEnemyNexus -> dispatchTargetedCard(card, "ENEMY_NEXUS")
                    hitEnemyId != null -> dispatchTargetedCard(card, hitEnemyId)
                    hitAllyId != null -> dispatchTargetedCard(card, hitAllyId)
                    else -> {
                        // Luôn mở chế độ chọn mục tiêu để người chơi tự chọn quân địch hoặc Nhà Chính địch
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.STUN_TARGET ||
                card.template.effectType == CardEffectType.FROSTBITE_TARGET ||
                card.template.effectType == CardEffectType.FROSTBITE_TWO ||
                card.template.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL ||
                card.template.effectType == CardEffectType.RECALL_OR_STUN -> {
                when {
                    enemyTargets.isEmpty() -> {
                        if (card.template.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL) {
                            dispatchTargetedCard(card, null)
                        } else {
                            dragWarningText = "Không có kẻ địch hợp lệ để chọn mục tiêu!"
                        }
                    }
                    hitEnemyId != null -> dispatchTargetedCard(card, hitEnemyId)
                    else -> {
                        // Bắt buộc người chơi bấm chọn mục tiêu kể cả khi chỉ có 1 kẻ địch
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.GIVE_BARRIER_AND_DRAW ||
                card.template.effectType == CardEffectType.RALLY_AND_SHIELD ||
                card.template.effectType == CardEffectType.FREE_ATTACK -> {
                val validAllies = if (card.template.effectType == CardEffectType.FREE_ATTACK) {
                    allyTargets.filter { it.canAttack }
                } else {
                    allyTargets
                }
                when {
                    validAllies.isEmpty() -> {
                        dragWarningText = "Không có đồng minh hợp lệ để chọn mục tiêu!"
                    }
                    hitAllyId != null && validAllies.any { it.instanceId == hitAllyId } ->
                        dispatchTargetedCard(card, hitAllyId)
                    else -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            card.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE -> {
                when {
                    allyTargets.isEmpty() || enemyTargets.isEmpty() -> {
                        dragWarningText = "Cần ít nhất 1 đồng minh và 1 kẻ địch để Đấu Tay Đôi!"
                    }
                    hitAllyId != null -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = hitAllyId
                    }
                    else -> {
                        pendingTargetCard = card
                        pendingFirstUnitId = null
                    }
                }
            }
            else -> dispatchTargetedCard(card, null)
        }
    }

    val handleHandCardDrop: (HandCardInstance, Offset) -> Unit = { card, pointer ->
        if (pointer.x < -5000f || pointer.y < -5000f) {
            activeDrag = null
        } else {
            val c = latestCombat
            val startPtr = (activeDrag as? ActiveDragState.HandCardDrag)?.startPointerInRoot ?: pointer
            val dragDx = pointer.x - startPtr.x
            val dragDyUp = startPtr.y - pointer.y
            val dragDistSq = dragDx * dragDx + dragDyUp * dragDyUp
            val handZoneTopY = if (sharedBattlefieldBounds != Rect.Zero) {
                maxOf(
                    sharedBattlefieldBounds.bottom + with(density) { 12.dp.toPx() },
                    rootOrigin.y + rootSize.height * 0.74f
                )
            } else if (rootSize.height > 0) {
                rootOrigin.y + rootSize.height * 0.78f
            } else {
                Float.MAX_VALUE
            }
            val inBottomHandDock = pointer.y >= handZoneTopY
            val droppedBackInHand = inBottomHandDock && (
                (dragDistSq < cancelDragThresholdPx * cancelDragThresholdPx) ||
                    (rootSize.height > 0 &&
                        pointer.y > rootOrigin.y + rootSize.height * 0.88f &&
                        dragDyUp < cancelDragThresholdPx)
                )

            if (droppedBackInHand) {
                activeDrag = null
            } else {
                val isUnitLike = card.template.type == CardType.UNIT ||
                    card.template.type == CardType.CHAMPION ||
                    card.template.type == CardType.LANDMARK
                val isInCombat = c.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
                    c.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
                val onTargetRect = enemyUnitBounds.values.any { it.inflate(targetHitPadPx).contains(pointer) } ||
                    playerUnitBounds.values.any { it.inflate(targetHitPadPx).contains(pointer) } ||
                    enemyNexusBounds.inflate(targetHitPadPx).contains(pointer)
                val expandedArenaRect = if (battleBoardBounds != Rect.Zero) {
                    Rect(
                        left = maxOf(rootOrigin.x, battleBoardBounds.left - boardDropPadPx),
                        top = rootOrigin.y,
                        right = minOf(rootOrigin.x + rootSize.width.toFloat(), battleBoardBounds.right + boardDropPadPx),
                        bottom = battleBoardBounds.bottom + benchDropPadPx
                    )
                } else {
                    sharedBattlefieldBounds.inflate(boardDropPadPx)
                }
                val draggedUpwardInPlayArea = dragDyUp >= upwardPlayThresholdPx &&
                    (rootSize.width <= 0 || pointer.x in (rootOrigin.x + rootSize.width * 0.06f)..(rootOrigin.x + rootSize.width * 0.94f))
                val validZone = expandedArenaRect.contains(pointer) ||
                    playerBenchBounds.inflate(benchDropPadPx).contains(pointer) ||
                    sharedBattlefieldBounds.inflate(boardDropPadPx).contains(pointer) ||
                    onTargetRect ||
                    draggedUpwardInPlayArea

                if (!c.canPlayCard(card)) {
                    dragWarningText = when {
                        c.priorityHolder != PriorityHolder.PLAYER -> "Chờ lượt của đối thủ"
                        isUnitLike && c.playerBoard.size >= 6 -> "Hàng chờ đã đầy (6/6)"
                        card.template.spellSpeed == SpellSpeed.FAST && isInCombat && c.playerFastSpellUsedInCombat ->
                            "Mỗi đợt giao tranh chỉ dùng được 1 bài phép Nhanh!"
                        card.template.spellSpeed == SpellSpeed.FOCUS && (isInCombat || c.spellStack.isNotEmpty()) ->
                            "Phép Tập Trung chỉ dùng ngoài giao tranh khi hàng chờ trống!"
                        card.template.spellSpeed == SpellSpeed.SLOW && (isInCombat || c.spellStack.isNotEmpty()) ->
                            "Phép Chậm chỉ dùng ngoài giao tranh khi hàng chờ trống!"
                        else -> "Không thể ra lá ${card.template.name}"
                    }
                } else if (validZone) {
                    triggerCardPlayOrTargeting(card, pointer)
                    inspectedHandCard = null
                    isHandExpanded = false
                } else {
                    dragWarningText = if (isUnitLike) "Kéo lên bàn đấu để triệu hồi"
                    else "Kéo vào sân băng hoặc mục tiêu để dùng phép"
                }
                activeDrag = null
            }
        }
    }

    val blockRejectionMessage: (UnitOnBoard, UnitOnBoard) -> String = { blocker, attacker ->
        when {
            attacker.keywords.contains(Keyword.FEARSOME) && blocker.effectivePower < 3 ->
                "Địch có Đáng Sợ — cần quân thủ có ít nhất 3 Công!"
            attacker.keywords.contains(Keyword.ELUSIVE) &&
                !blocker.keywords.contains(Keyword.ELUSIVE) &&
                !blocker.keywords.contains(Keyword.SHARPSIGHT) ->
                "Địch có Lẩn Tránh — cần quân có Lẩn Tránh hoặc Tinh Mắt!"
            attacker.keywords.contains(Keyword.BRASH) && blocker.health < 3 ->
                "Địch có Hung Hãn — cần quân thủ có ít nhất 3 Máu!"
            else -> "${blocker.template.name} không thể chặn ${attacker.template.name}"
        }
    }

    val findClosestHitAttackerId: (CombatState, Offset, Boolean) -> String? = { c, pointer, allowColumnFallback ->
        val activeIds = c.attackingUnitIds.toSet()
        val directHit = enemyAttackerBounds.entries
            .filter { (id, rect) -> activeIds.contains(id) && rect.inflate(targetHitPadPx).contains(pointer) }
            .minByOrNull { (_, rect) ->
                val dx = pointer.x - rect.center.x
                val dy = pointer.y - rect.center.y
                dx * dx + dy * dy
            }?.key
        directHit ?: if (allowColumnFallback) {
            enemyAttackerBounds.entries
                .filter { (id, rect) -> activeIds.contains(id) && abs(pointer.x - rect.center.x) <= magneticLanePadPx }
                .minByOrNull { (_, rect) -> abs(pointer.x - rect.center.x) }
                ?.key
        } else {
            null
        }
    }

    val handleBenchUnitDrop: (UnitOnBoard, Offset) -> Unit = { unit, pointer ->
        if (pointer.x < -5000f || pointer.y < -5000f) {
            activeDrag = null
        } else {
            val c = latestCombat
            val benchTopThreshold = if (playerBenchBounds != Rect.Zero) {
                playerBenchBounds.top + with(density) { 14.dp.toPx() }
            } else {
                sharedBattlefieldBounds.bottom + benchDropPadPx
            }
            val expandedFieldRect = if (battleBoardBounds != Rect.Zero) {
                Rect(
                    left = battleBoardBounds.left - boardDropPadPx,
                    top = rootOrigin.y,
                    right = battleBoardBounds.right + boardDropPadPx,
                    bottom = benchTopThreshold
                )
            } else {
                sharedBattlefieldBounds.inflate(benchDropPadPx)
            }
            val inField = expandedFieldRect.contains(pointer) ||
                sharedBattlefieldBounds.inflate(benchDropPadPx).contains(pointer)
            if (c.priorityHolder != PriorityHolder.PLAYER) {
                dragWarningText = "Hãy chờ tới lượt của bạn"
            } else if (c.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && !c.playerInitiatedCombat) {
                if (c.blockCommitted) {
                    dragWarningText = "Đã chốt đội hình chặn"
                } else if (!unit.canBlock) {
                    dragWarningText = "${unit.template.name} không thể chặn"
                } else {
                    val directHitId = findClosestHitAttackerId(c, pointer, inField)
                    val targetAttackerId = directHitId ?: if (inField) {
                        c.selectedEnemyAttackerForBlock
                            ?: c.attackingUnitIds.firstOrNull { atkId ->
                                val atk = c.enemyBoard.find { it.instanceId == atkId }
                                !c.lockedBlockAttackerIds.contains(atkId) &&
                                    !c.blockPairings.containsKey(atkId) &&
                                    atk != null &&
                                    unit.canBlockAttacker(atk)
                            }
                    } else null
                    val targetEnemy = targetAttackerId?.let { id -> c.enemyBoard.find { it.instanceId == id } }
                    when {
                        targetEnemy == null -> if (inField) {
                            dragWarningText = "Kéo vào làn quân địch tấn công để chặn"
                        }
                        c.lockedBlockAttackerIds.contains(targetEnemy.instanceId) -> {
                            dragWarningText = "Kẻ địch này đã khóa chặn (Thách Đấu)"
                        }
                        !unit.canBlockAttacker(targetEnemy) -> {
                            dragWarningText = blockRejectionMessage(unit, targetEnemy)
                        }
                        else -> {
                            onAssignBlocker(targetEnemy.instanceId, unit.instanceId)
                        }
                    }
                }
            } else if (((c.phase == CombatPhase.MAIN_ACTION_PHASE && c.playerHasAttackToken && c.spellStack.isEmpty()) ||
                    (c.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && c.playerHasAttackToken && c.playerInitiatedCombat && c.spellStack.isEmpty()))
            ) {
                if (!unit.canAttack) {
                    dragWarningText = "${unit.template.name} không thể tấn công"
                } else if (inField) {
                    onDragBenchUnitToAttack(unit.instanceId)
                } else {
                    dragWarningText = "Kéo lên sân đấu để tấn công"
                }
            } else {
                dragWarningText = if (!c.playerHasAttackToken) "Chờ Thẻ Tấn Công của bạn" else "Không thể di chuyển bài quân lúc này"
            }
            activeDrag = null
        }
    }

    val handleCombatUnitDrop: (UnitOnBoard, Offset) -> Unit = { unit, pointer ->
        if (pointer.x < -5000f || pointer.y < -5000f) {
            activeDrag = null
        } else {
            val c = latestCombat
            val backToBench = playerBenchBounds.inflate(benchDropPadPx).contains(pointer) ||
                (sharedBattlefieldBounds != Rect.Zero && pointer.y >= sharedBattlefieldBounds.bottom - with(density) { 12.dp.toPx() })
            if (c.priorityHolder == PriorityHolder.PLAYER) {
                when (c.phase) {
                    CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> {
                        if (backToBench && c.playerHasAttackToken && c.spellStack.isEmpty()) {
                            onRemoveAttacker(unit.instanceId)
                        }
                    }
                    CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                        if (!c.playerInitiatedCombat && !c.blockCommitted) {
                            if (backToBench) {
                                val attackerId = c.blockPairings.entries.firstOrNull { it.value == unit.instanceId }?.key
                                if (attackerId != null) onRemoveBlocker(attackerId)
                            } else {
                                val directHitId = findClosestHitAttackerId(c, pointer, true)
                                val targetEnemy = directHitId?.let { id -> c.enemyBoard.find { it.instanceId == id } }
                                if (targetEnemy != null) {
                                    when {
                                        c.lockedBlockAttackerIds.contains(targetEnemy.instanceId) -> {
                                            dragWarningText = "Kẻ địch này đã khóa chặn (Thách Đấu)"
                                        }
                                        !unit.canBlockAttacker(targetEnemy) -> {
                                            dragWarningText = blockRejectionMessage(unit, targetEnemy)
                                        }
                                        else -> {
                                            onAssignBlocker(targetEnemy.instanceId, unit.instanceId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    else -> {}
                }
            }
            activeDrag = null
        }
    }

    val safeInsets = WindowInsets.safeDrawing.asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val safeStart = safeInsets.calculateLeftPadding(layoutDirection)
    val safeEnd = safeInsets.calculateRightPadding(layoutDirection)
    val safeTop = safeInsets.calculateTopPadding()
    val safeBottom = safeInsets.calculateBottomPadding()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned {
                rootOrigin = it.positionInRoot()
                rootSize = it.size
            }
            .clipToBounds()
    ) {
        val vw = { percent: Float -> maxWidth * (percent / 100f) }
        val vh = { percent: Float -> maxHeight * (percent / 100f) }

        val descFontSize = (maxHeight.value * 0.022f).coerceIn(10f, 13.5f).sp
        val statFontSize = (maxHeight.value * 0.028f).coerceIn(12f, 17f).sp

        // Board token sizing (+~16-19% larger so card artwork on battlefield is clear and crisp)
        val unitTileWidth = minOf(vw(11.0f), vh(21.6f) * 0.82f).coerceAtLeast(55.dp)
        val unitTileHeight = minOf(vh(22.4f), unitTileWidth * 1.24f).coerceAtLeast(60.dp)
        val unitGap = vw(1.05f)

        // Floating drag preview card sizing (~15% larger to match expanded hand cards)
        val handCardHeight = vh(36.8f).coerceIn(134.dp, 184.dp)
        val handCardWidth = handCardHeight * 0.6875f

        val championName = remember(combat.championId) {
            CardCatalog.getChampion(combat.championId).name
        }

        val activePendingCard = pendingTargetCard
        val highlightedTargetEnemyIds: Set<String> = when {
            activePendingCard == null -> emptySet()
            activePendingCard.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                activePendingCard.template.effectType == CardEffectType.DRAIN ||
                activePendingCard.template.effectType == CardEffectType.STUN_TARGET ||
                activePendingCard.template.effectType == CardEffectType.FROSTBITE_TARGET ||
                activePendingCard.template.effectType == CardEffectType.FROSTBITE_TWO ||
                activePendingCard.template.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL ||
                activePendingCard.template.effectType == CardEffectType.RECALL_OR_STUN ->
                combat.enemyBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE && pendingFirstUnitId != null ->
                combat.enemyBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            else -> emptySet()
        }
        val highlightedTargetAllyIds: Set<String> = when {
            activePendingCard == null -> emptySet()
            activePendingCard.template.type == CardType.EQUIPMENT ||
                activePendingCard.template.id == "card_brightsteel_protector" ||
                activePendingCard.template.effectType == CardEffectType.GIVE_BARRIER_AND_DRAW ||
                activePendingCard.template.effectType == CardEffectType.RALLY_AND_SHIELD ->
                combat.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            activePendingCard.template.effectType == CardEffectType.FREE_ATTACK ->
                combat.playerBoard.filter { it.template.type != CardType.LANDMARK && it.canAttack }.map { it.instanceId }.toSet()
            activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE && pendingFirstUnitId == null ->
                combat.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
            else -> emptySet()
        }

        val onEnemyUnitClick: (UnitOnBoard) -> Unit = { unit ->
            inspectedHandCard = null
            if (activePendingCard != null && highlightedTargetEnemyIds.contains(unit.instanceId)) {
                if (activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE) {
                    val allyId = pendingFirstUnitId
                    if (allyId != null) {
                        dispatchTargetedCard(activePendingCard, "$allyId|${unit.instanceId}")
                        pendingTargetCard = null
                        pendingFirstUnitId = null
                    }
                } else {
                    dispatchTargetedCard(activePendingCard, unit.instanceId)
                    pendingTargetCard = null
                    pendingFirstUnitId = null
                }
            } else if (combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                combat.selectedAttackerForChallenger != null
            ) {
                val activeChallengerId = combat.selectedAttackerForChallenger
                val activeChallengerUnit = combat.playerBoard.find { it.instanceId == activeChallengerId }
                val canPullTarget = activeChallengerUnit != null &&
                    (activeChallengerUnit.keywords.contains(Keyword.CHALLENGER) || unit.keywords.contains(Keyword.VULNERABLE))
                if (unit.template.type != CardType.LANDMARK && !unit.isStunned && !unit.isShackled && canPullTarget) {
                    onChallengeEnemyUnit(activeChallengerId, unit.instanceId)
                } else {
                    dragWarningText = "Không thể Thách Đấu mục tiêu này"
                }
            } else if (combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE &&
                combat.challengedPairings.containsValue(unit.instanceId)
            ) {
                val pairedAtk = combat.challengedPairings.entries.firstOrNull { it.value == unit.instanceId }?.key
                if (pairedAtk != null) onClearChallenge(pairedAtk)
            } else if (combat.priorityHolder == PriorityHolder.PLAYER &&
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                !combat.playerInitiatedCombat &&
                !combat.blockCommitted &&
                combat.attackingUnitIds.contains(unit.instanceId)
            ) {
                if (combat.lockedBlockAttackerIds.contains(unit.instanceId)) {
                    dragWarningText = "Đơn vị bị Thách Đấu bắt buộc phải chặn"
                } else if (combat.blockPairings.containsKey(unit.instanceId)) {
                    onRemoveBlocker(unit.instanceId)
                } else {
                    onSelectEnemyAttackerForBlock(unit.instanceId)
                }
            } else {
                inspectedUnit = unit
            }
        }

        val onPlayerUnitClick: (UnitOnBoard) -> Unit = { unit ->
            inspectedHandCard = null
            if (activePendingCard != null && highlightedTargetAllyIds.contains(unit.instanceId)) {
                if (activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE) {
                    pendingFirstUnitId = unit.instanceId
                } else {
                    dispatchTargetedCard(activePendingCard, unit.instanceId)
                    pendingTargetCard = null
                    pendingFirstUnitId = null
                }
            } else if (combat.priorityHolder == PriorityHolder.PLAYER) {
                when (combat.phase) {
                    CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> {
                        if (combat.playerHasAttackToken && combat.playerInitiatedCombat && combat.spellStack.isEmpty()) {
                            val isAttacking = combat.attackingUnitIds.contains(unit.instanceId)
                            val hasVulnerableTarget = combat.enemyBoard.any {
                                it.keywords.contains(Keyword.VULNERABLE) &&
                                    it.template.type != CardType.LANDMARK &&
                                    !it.isStunned &&
                                    !it.isShackled
                            }
                            val canChallenge = (unit.keywords.contains(Keyword.CHALLENGER) &&
                                combat.enemyBoard.any { it.template.type != CardType.LANDMARK && !it.isStunned && !it.isShackled }) ||
                                hasVulnerableTarget
                            when {
                                isAttacking && combat.challengedPairings.containsKey(unit.instanceId) ->
                                    onClearChallenge(unit.instanceId)
                                isAttacking && canChallenge && combat.selectedAttackerForChallenger != unit.instanceId ->
                                    onSelectChallengerAttacker(unit.instanceId)
                                else -> onToggleAttacker(unit.instanceId)
                            }
                        } else {
                            inspectedUnit = unit
                        }
                    }
                    CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                        if (!combat.playerInitiatedCombat && !combat.blockCommitted) {
                            val targetEnemy = combat.selectedEnemyAttackerForBlock
                                ?: combat.attackingUnitIds.firstOrNull { atkId ->
                                    val atk = combat.enemyBoard.find { it.instanceId == atkId }
                                    !combat.lockedBlockAttackerIds.contains(atkId) &&
                                        !combat.blockPairings.containsKey(atkId) &&
                                        atk != null &&
                                        unit.canBlockAttacker(atk)
                                }
                            if (targetEnemy != null && !combat.lockedBlockAttackerIds.contains(targetEnemy)) {
                                onAssignBlocker(targetEnemy, unit.instanceId)
                            } else {
                                inspectedUnit = unit
                            }
                        } else {
                            inspectedUnit = unit
                        }
                    }
                    else -> inspectedUnit = unit
                }
            } else {
                inspectedUnit = unit
            }
        }

        val currentDrag = activeDrag
        val playerBenchHighlight = when (currentDrag) {
            is ActiveDragState.HandCardDrag -> {
                val c = currentDrag.card
                val isUnit = c.template.type == CardType.UNIT || c.template.type == CardType.CHAMPION || c.template.type == CardType.LANDMARK
                if (isUnit) {
                    if (combat.canPlayCard(c)) BattleBoardDropHighlight.VALID else BattleBoardDropHighlight.INVALID
                } else BattleBoardDropHighlight.NONE
            }
            is ActiveDragState.CombatUnitDrag -> BattleBoardDropHighlight.VALID
            else -> BattleBoardDropHighlight.NONE
        }
        val battlefieldHighlight = when (currentDrag) {
            is ActiveDragState.HandCardDrag -> {
                val c = currentDrag.card
                if (combat.canPlayCard(c)) BattleBoardDropHighlight.VALID else BattleBoardDropHighlight.INVALID
            }
            is ActiveDragState.BenchUnitDrag -> {
                val canMoveToField = combat.priorityHolder == PriorityHolder.PLAYER && when (combat.phase) {
                    CombatPhase.MAIN_ACTION_PHASE ->
                        combat.playerHasAttackToken && combat.spellStack.isEmpty() && currentDrag.unit.canAttack
                    CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ->
                        combat.playerHasAttackToken && combat.playerInitiatedCombat && combat.spellStack.isEmpty() && currentDrag.unit.canAttack
                    CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ->
                        !combat.playerInitiatedCombat && !combat.blockCommitted && currentDrag.unit.canBlock
                    else -> false
                }
                if (canMoveToField) BattleBoardDropHighlight.VALID else BattleBoardDropHighlight.INVALID
            }
            else -> BattleBoardDropHighlight.NONE
        }

        // LAYER 1: Full-Bleed Freljord Frosted Ice & Carved Stone Battlefield + Glowing Metallic-Gold Centerline
        FreljordBattlefieldBackground(modifier = Modifier.fillMaxSize())

        // LAYER 2: Central Seamless BattleBoard (AI Bench on top rim, Ice Combat Arena centered on centerline, Player Bench on bottom rim)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = safeStart + vw(14.5f),
                    end = safeEnd + vw(14.5f),
                    top = safeTop + vh(6.0f),
                    bottom = safeBottom + vh(1.5f)
                )
                .onGloballyPositioned { battleBoardBounds = it.boundsInRoot() }
        ) {
            BattleBoard(
                combat = combat,
                modifier = Modifier.fillMaxSize(),
                playerBenchHighlight = playerBenchHighlight,
                battlefieldHighlight = battlefieldHighlight,
                draggingBenchUnitId = (currentDrag as? ActiveDragState.BenchUnitDrag)?.unit?.instanceId,
                draggingCombatUnitId = (currentDrag as? ActiveDragState.CombatUnitDrag)?.unit?.instanceId,
                isDraggingBenchBlocker = currentDrag is ActiveDragState.BenchUnitDrag &&
                    combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    !combat.playerInitiatedCombat,
                draggedBenchUnit = (currentDrag as? ActiveDragState.BenchUnitDrag)?.unit,
                draggedCombatUnit = (currentDrag as? ActiveDragState.CombatUnitDrag)?.unit,
                tileWidthOverride = unitTileWidth,
                tileHeightOverride = unitTileHeight,
                unitGapOverride = unitGap,
                statFontSizeOverride = statFontSize,
                descFontSizeOverride = descFontSize,
                highlightedTargetEnemyIds = highlightedTargetEnemyIds,
                highlightedTargetAllyIds = highlightedTargetAllyIds,
                onPlayerBenchBoundsChanged = { playerBenchBounds = it },
                onSharedBattlefieldBoundsChanged = { sharedBattlefieldBounds = it },
                onEnemyAttackerBoundsChanged = { id, rect -> enemyAttackerBounds[id] = rect },
                onEnemyBenchUnitBoundsChanged = { id, rect -> enemyUnitBounds[id] = rect },
                onPlayerUnitBoundsChanged = { id, rect -> playerUnitBounds[id] = rect },
                onPlayerUnitClick = onPlayerUnitClick,
                onEnemyUnitClick = onEnemyUnitClick,
                onUnitLongClick = { inspectedUnit = it },
                onBenchUnitDragStart = { unit, ptr ->
                    inspectedHandCard = null
                    activeDrag = ActiveDragState.BenchUnitDrag(unit, ptr)
                },
                onBenchUnitDrag = { unit, ptr -> activeDrag = ActiveDragState.BenchUnitDrag(unit, ptr) },
                onBenchUnitDragEnd = { unit, ptr -> handleBenchUnitDrop(unit, ptr) },
                onCombatUnitDragStart = { unit, ptr -> activeDrag = ActiveDragState.CombatUnitDrag(unit, ptr) },
                onCombatUnitDrag = { unit, ptr -> activeDrag = ActiveDragState.CombatUnitDrag(unit, ptr) },
                onCombatUnitDragEnd = { unit, ptr -> handleCombatUnitDrop(unit, ptr) },
                onResolveStack = onResolveStack
            )
        }

        // LAYER 3: Top-Left Opponent Nameplate + Hand Count + Match Info
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = safeStart + 8.dp, top = safeTop + 6.dp)
                .zIndex(20f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier
                    .testTag("enemy_hand_count")
                    .clickable(onClick = { showPowerInfo = true }),
                shape = RoundedCornerShape(6.dp),
                color = Color(0xE8243854),
                border = BorderStroke(1.3.dp, HextechGold.copy(alpha = 0.85f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = combat.encounterNode.enemyName,
                        style = fantasyHeadingStyle(
                            fontSize = (descFontSize.value * 0.96f).coerceIn(10f, 13.5f).sp,
                            color = ParchmentWhite
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = vw(13f))
                    )
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFF203A5C),
                        border = BorderStroke(0.8.dp, ArcaneCyan.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "Bài:${combat.enemyHandCount}",
                            style = tabularStatStyle(
                                fontSize = (descFontSize.value * 0.84f).coerceAtLeast(9f).sp,
                                color = ArcaneCyan
                            ),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Match Info circular badge
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .minimumInteractiveComponentSize()
                    .testTag("match_info_button")
                    .clickable { showMatchInfoDialog = true },
                shape = CircleShape,
                color = Color(0xD9273D5E),
                border = BorderStroke(1.2.dp, HextechGold.copy(alpha = 0.8f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Thông Tin Trận Đấu",
                        tint = ArcaneCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // LAYER 3B: Top-Center Opponent Fanned Hand Card Backs + Enemy Deck Pile
        if (combat.enemyHandCount > 0 || combat.enemyDrawPool.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = safeTop + 4.dp)
                    .zIndex(20f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (combat.enemyHandCount > 0) {
                    EnemyFannedCardBacks(
                        count = minOf(combat.enemyHandCount, 6),
                        cardWidth = vh(5.0f).coerceIn(18.dp, 28.dp),
                        cardHeight = vh(6.8f).coerceIn(24.dp, 36.dp)
                    )
                }
                if (combat.enemyDrawPool.isNotEmpty()) {
                    StackedDeckPile(
                        cardCount = combat.enemyDrawPool.size,
                        deckWidth = vh(4.6f).coerceIn(18.dp, 26.dp),
                        deckHeight = vh(6.4f).coerceIn(24.dp, 34.dp),
                        fontSize = (descFontSize.value * 0.74f).coerceAtLeast(8f).sp
                    )
                }
            }
        }

        // LAYER 4: Top-Right Compact PoC Status Pill (⚡ Power, Foe Mana, Round, Forfeit)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = safeEnd + 8.dp, top = safeTop + 6.dp)
                .zIndex(20f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xE8243854),
                border = BorderStroke(1.2.dp, HextechGold.copy(alpha = 0.75f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (combat.encounterNode.enemyPowerTitle.isNotBlank()) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showPowerInfo = true }
                                .padding(horizontal = 3.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Sức Mạnh Địch",
                                tint = LegendaryAmber,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = combat.encounterNode.enemyPowerTitle,
                                fontSize = (descFontSize.value * 0.88f).coerceAtLeast(9.5f).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = ParchmentWhite,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = vw(16f))
                            )
                        }
                    }

                    Text(
                        text = "Địch ${combat.enemyMana}/${combat.enemyMaxMana}",
                        style = tabularStatStyle(
                            fontSize = (descFontSize.value * 0.88f).coerceAtLeast(9.5f).sp,
                            color = Color(0xFF93C5FD)
                        )
                    )

                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = HextechGold.copy(alpha = 0.22f),
                        border = BorderStroke(0.8.dp, HextechGoldBright)
                    ) {
                        Text(
                            text = "V${combat.round}",
                            style = tabularStatStyle(
                                fontSize = (descFontSize.value * 0.86f).coerceAtLeast(9.5f).sp,
                                color = HextechGoldBright
                            ),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            // Compact Forfeit / Surrender button
            Surface(
                modifier = Modifier
                    .size(34.dp)
                    .minimumInteractiveComponentSize()
                    .clickable { showForfeitConfirm = true },
                shape = CircleShape,
                color = Color(0xD92B4368),
                border = BorderStroke(1.2.dp, CrimsonNexus.copy(alpha = 0.7f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = "Đầu Hàng",
                        tint = MutedSilver,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // LAYER 5: Built-in Arena Nexuses (Top-Left Red Enemy Nexus & Bottom-Left Blue Player Nexus) + Centerline Scroll Log Button
        ArenaBoardNexusOverlay(
            enemyHp = combat.enemyNexusHp,
            enemyMaxHp = combat.enemyMaxHp,
            enemyHitDelta = enemyNexusDelta,
            playerHp = combat.playerNexusHp,
            playerMaxHp = combat.playerMaxHp,
            playerHitDelta = playerNexusDelta,
            onEnemyNexusBoundsChanged = { enemyNexusBounds = it },
            onEnemyNexusClick = {
                val pending = pendingTargetCard
                if (pending != null &&
                    (pending.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                        pending.template.effectType == CardEffectType.DRAIN)
                ) {
                    dispatchTargetedCard(pending, "ENEMY_NEXUS")
                    pendingTargetCard = null
                    pendingFirstUnitId = null
                } else {
                    showPowerInfo = true
                }
            },
            onPlayerNexusClick = { showMatchInfoDialog = true },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(21f)
        )

        // Square Carved-Gold Scroll Log Button directly on left end of golden centerline
        CenterlineScrollButton(
            size = vh(10.5f).coerceIn(38.dp, 48.dp),
            onClick = { showLogDialog = true },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = safeStart + vw(1.2f))
                .zIndex(22f)
        )

        // LAYER 6: Bottom-Left Player Nameplate + PoC Powers + Emote + Stacked Deck Pile + Graveyard
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = safeStart + 8.dp, bottom = safeBottom + 6.dp)
                .zIndex(20f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Active Emote Speech Bubble
            AnimatedVisibility(
                visible = activeEmote != null,
                enter = fadeIn(tween(150)),
                exit = fadeOut(tween(250))
            ) {
                activeEmote?.let { emote ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = HextechGold.copy(alpha = 0.94f),
                        border = BorderStroke(1.dp, ParchmentWhite)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(emote.icon, contentDescription = emote.label, tint = DarkButtonInk, modifier = Modifier.size(13.dp))
                            Text(
                                text = emote.label,
                                fontSize = (descFontSize.value * 0.88f).coerceAtLeast(9f).sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkButtonInk,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Player Nameplate Banner + Active PoC Powers + Emote Button
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xE8243854),
                    border = BorderStroke(1.3.dp, HextechGold.copy(alpha = 0.85f)),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = championName,
                            style = fantasyHeadingStyle(
                                fontSize = (descFontSize.value * 0.95f).coerceIn(10f, 13.5f).sp,
                                color = HextechGoldBright
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (combat.activePowers.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showMatchInfoDialog = true }
                                    .padding(horizontal = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                combat.activePowers.take(3).forEach { _ ->
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "Sức Mạnh Đang Kích Hoạt",
                                        tint = ArcaneCyan,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }

                        Surface(
                            modifier = Modifier
                                .size(28.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("emote_button")
                                .clickable { showEmotePicker = !showEmotePicker },
                            shape = CircleShape,
                            color = Color(0xFF3D3428),
                            border = BorderStroke(1.dp, HextechGold.copy(alpha = 0.7f))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEmotions,
                                    contentDescription = "Biểu Cảm",
                                    tint = HextechGoldBright,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // Stacked Deck Pile beside lower-left stone border
                StackedDeckPile(
                    cardCount = combat.playerDrawPile.size,
                    deckWidth = vh(7.2f).coerceIn(26.dp, 38.dp),
                    deckHeight = vh(10.0f).coerceIn(36.dp, 52.dp),
                    fontSize = (descFontSize.value * 0.92f).coerceAtLeast(10f).sp
                )

                // Compact Graveyard Pill
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("graveyard_button")
                        .clickable { showGraveyardDialog = true },
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xD9273D5E),
                    border = BorderStroke(1.dp, HextechGold.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = "Nghĩa Địa",
                            tint = MutedSilver,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "${combat.playerGraveyard.size + combat.enemyGraveyard.size}",
                            style = tabularStatStyle(
                                fontSize = (descFontSize.value * 0.85f).coerceAtLeast(9.5f).sp,
                                color = ParchmentWhite
                            )
                        )
                    }
                }
            }
        }

        // LAYER 7: Right-Edge Iconic Circular Pass/Action & Mechanical Mana Dial (above hand layer)
        val dialHeight = vh(49f).coerceIn(148.dp, 240.dp)
        val dialWidth = (dialHeight * 0.98f).coerceIn(152.dp, 224.dp)
        val actionDiameter = (dialHeight * 0.44f).coerceIn(56.dp, 88.dp)

        RightManaActionDial(
            combat = combat,
            dialWidth = dialWidth,
            dialHeight = dialHeight,
            actionDiameter = actionDiameter,
            descFontSize = descFontSize,
            statFontSize = statFontSize,
            onEnterAttack = onEnterAttack,
            onCancelAttack = onCancelAttack,
            onCommitAttack = onCommitAttack,
            onCommitBlock = onCommitBlock,
            onPassTurn = onPassTurn,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = safeEnd + 4.dp)
                .zIndex(38f)
        )

        // LAYER 7B: Center-Screen Turn & Combat Phase Banner (Tới lượt bạn / Tới lượt đối thủ / Tấn công / Phòng thủ)
        var centerBannerLabel by remember { mutableStateOf<String?>(null) }
        var centerBannerSubtitle by remember { mutableStateOf<String?>(null) }
        var centerBannerIsPlayer by remember { mutableStateOf(true) }
        var centerBannerSeq by remember { mutableIntStateOf(0) }

        LaunchedEffect(
            combat.round,
            combat.phase,
            combat.priorityHolder,
            combat.playerInitiatedCombat,
            combat.blockCommitted
        ) {
            val bannerSpec: Triple<String, String?, Boolean>? = when {
                combat.phase == CombatPhase.GAME_OVER -> null
                combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && combat.playerInitiatedCombat ->
                    Triple("TẤN CÔNG", "Chọn quân xuất kích", true)
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    !combat.playerInitiatedCombat &&
                    combat.priorityHolder == PriorityHolder.PLAYER &&
                    !combat.blockCommitted ->
                    Triple("PHÒNG THỦ", "Xếp quân chặn đòn địch", false)
                combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
                    combat.playerInitiatedCombat &&
                    combat.priorityHolder == PriorityHolder.ENEMY ->
                    Triple("TẤN CÔNG", "Đối thủ đang chọn quân chặn", true)
                combat.phase == CombatPhase.MAIN_ACTION_PHASE && combat.priorityHolder == PriorityHolder.PLAYER ->
                    Triple("TỚI LƯỢT BẠN", if (combat.playerHasAttackToken) "Bạn đang giữ Kiếm Tấn Công" else null, true)
                combat.phase == CombatPhase.MAIN_ACTION_PHASE && combat.priorityHolder == PriorityHolder.ENEMY ->
                    Triple("TỚI LƯỢT ĐỐI THỦ", null, false)
                else -> null
            }
            if (bannerSpec != null && bannerSpec.first != centerBannerLabel) {
                centerBannerLabel = bannerSpec.first
                centerBannerSubtitle = bannerSpec.second
                centerBannerIsPlayer = bannerSpec.third
                centerBannerSeq++
            }
        }

        LaunchedEffect(centerBannerSeq) {
            if (centerBannerLabel != null) {
                kotlinx.coroutines.delay(1350L)
                centerBannerLabel = null
            }
        }

        AnimatedVisibility(
            visible = centerBannerLabel != null,
            enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.86f, animationSpec = tween(180)),
            exit = fadeOut(tween(240)) + scaleOut(targetScale = 0.94f, animationSpec = tween(220)),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(72f)
        ) {
            val accent = if (centerBannerIsPlayer) HextechGoldBright else CrimsonNexus
            Box(
                modifier = Modifier
                    .testTag("center_turn_phase_banner")
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xF20B1322))
                    .border(2.2.dp, accent, RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.26f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 32.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = centerBannerLabel.orEmpty(),
                        style = fantasyHeadingStyle(
                            fontSize = 19.sp,
                            color = if (centerBannerIsPlayer) HextechGoldBright else ParchmentWhite
                        ),
                        letterSpacing = 1.6.sp
                    )
                    centerBannerSubtitle?.let { sub ->
                        Text(
                            text = sub,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (centerBannerIsPlayer) ArcaneCyan else Color(0xFFFCA5A5)
                        )
                    }
                }
            }
        }

        // LAYER 8: Two-State Player Hand Overlay (Collapsed BottomEnd stack <-> Expanded BottomCenter fan)
        val isPlayerTurnForHand = combat.priorityHolder == PriorityHolder.PLAYER && combat.phase != CombatPhase.GAME_OVER
        PlayerHandOverlay(
            cards = combat.playerHand,
            cardKey = { it.instanceId },
            isExpanded = isHandExpanded,
            onExpandedChange = { expanded ->
                isHandExpanded = expanded
                if (!expanded) {
                    inspectedHandCard = null
                }
            },
            canDragCards = isPlayerTurnForHand,
            draggingCardKey = (currentDrag as? ActiveDragState.HandCardDrag)?.card?.instanceId,
            rightCollapsedMargin = maxOf(16.dp, dialWidth * 0.18f),
            bottomCollapsedMargin = 6.dp,
            onCardDragStart = { card, ptr ->
                inspectedHandCard = null
                activeDrag = ActiveDragState.HandCardDrag(card, ptr, ptr)
            },
            onCardDrag = { card, ptr ->
                val startPtr = (activeDrag as? ActiveDragState.HandCardDrag)?.startPointerInRoot ?: ptr
                activeDrag = ActiveDragState.HandCardDrag(card, ptr, startPtr)
            },
            onCardDragEnd = { card, ptr -> handleHandCardDrop(card, ptr) },
            modifier = Modifier
                .fillMaxSize()
                .padding(start = safeStart, end = safeEnd, bottom = safeBottom)
                .zIndex(if (isHandExpanded) 42f else 25f)
        ) { handCard, expanded, isDragging, cardW, cardH ->
            HandCardView(
                handCard = handCard,
                effectiveCost = combat.effectivePlayCost(handCard),
                playable = combat.canPlayCard(handCard),
                isFocused = expanded && inspectedHandCard?.instanceId == handCard.instanceId,
                cardWidth = cardW,
                cardHeight = cardH,
                descFontSize = descFontSize,
                statFontSize = statFontSize,
                fanAngle = 0f,
                fanLiftY = 0.dp,
                isDragging = isDragging,
                enablePressElevation = expanded,
                testTag = "hand_card_${handCard.template.id}",
                onClick = {
                    if (!expanded) {
                        inspectedUnit = null
                        isHandExpanded = true
                    } else {
                        inspectedUnit = null
                        inspectHandDetailOnly = combat.priorityHolder != PriorityHolder.PLAYER
                        inspectedHandCard = if (inspectedHandCard?.instanceId == handCard.instanceId) null else handCard
                    }
                },
                onLongClick = {
                    if (!expanded) {
                        inspectedUnit = null
                        isHandExpanded = true
                    } else {
                        inspectedUnit = null
                        inspectHandDetailOnly = true
                        inspectedHandCard = handCard
                    }
                }
            )
        }

        // Targeting / Challenger Selection Prompt Banner
        val activeChallenger = combat.selectedAttackerForChallenger?.let { cid ->
            combat.playerBoard.find { it.instanceId == cid }
        }
        val promptMessage = when {
            activePendingCard != null &&
                activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE &&
                pendingFirstUnitId == null ->
                "Chọn ĐỒNG MINH đánh cặp cho ${activePendingCard.template.name}"
            activePendingCard != null &&
                activePendingCard.template.effectType == CardEffectType.SINGLE_COMBAT_STRIKE ->
                "Chọn KẺ ĐỊCH đối đầu cho ${activePendingCard.template.name}"
            activePendingCard != null && (activePendingCard.template.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                activePendingCard.template.effectType == CardEffectType.DRAIN) ->
                "Chọn QUÂN ĐỊCH hoặc NHÀ CHÍNH ĐỊCH cho ${activePendingCard.template.name}"
            activePendingCard != null ->
                "Chọn mục tiêu cho ${activePendingCard.template.name}"
            activeChallenger != null ->
                if (activeChallenger.keywords.contains(Keyword.CHALLENGER)) {
                    "Chọn QUÂN ĐỊCH để ${activeChallenger.template.name} kéo vào Thách Đấu"
                } else {
                    "Chọn quân ĐỊCH Dễ Bị Tổn Thương để ${activeChallenger.template.name} kéo"
                }
            else -> null
        }

        AnimatedVisibility(
            visible = promptMessage != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = safeTop + vh(8.5f))
                .zIndex(75f)
        ) {
            Surface(
                modifier = Modifier.testTag("targeting_prompt_banner"),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xEE12243E),
                border = BorderStroke(1.5.dp, ArcaneCyan),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = promptMessage ?: "",
                        fontSize = descFontSize,
                        fontWeight = FontWeight.Bold,
                        color = ParchmentWhite
                    )
                    if (activePendingCard?.template?.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
                        activePendingCard?.template?.effectType == CardEffectType.DRAIN
                    ) {
                        Button(
                            onClick = {
                                dispatchTargetedCard(activePendingCard, "ENEMY_NEXUS")
                                pendingTargetCard = null
                                pendingFirstUnitId = null
                            },
                            modifier = Modifier
                                .height(36.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("target_enemy_nexus_button"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CrimsonNexus,
                                contentColor = ParchmentWhite
                            )
                        ) {
                            Text("Nhà Chính Địch", fontSize = (descFontSize.value * 0.9f).sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (activePendingCard != null) {
                                pendingTargetCard = null
                                pendingFirstUnitId = null
                            } else {
                                onSelectChallengerAttacker(null)
                            }
                        },
                        modifier = Modifier
                            .height(36.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("cancel_target_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2B3E5A),
                            contentColor = HextechGoldBright
                        )
                    ) {
                        Text("Hủy Chọn", fontSize = (descFontSize.value * 0.9f).sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Drag Warning Toast
        AnimatedVisibility(
            visible = dragWarningText != null,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(220)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = vh(24f))
                .zIndex(80f)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = CrimsonNexus.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, ParchmentWhite)
            ) {
                Text(
                    text = dragWarningText ?: "",
                    fontSize = descFontSize,
                    fontWeight = FontWeight.Bold,
                    color = ParchmentWhite,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Floating Drag Preview Overlay
        if (currentDrag != null) {
            val localOffset = currentDrag.pointerInRoot - rootOrigin
            when (currentDrag) {
                is ActiveDragState.HandCardDrag -> {
                    val card = currentDrag.card
                    Box(
                        modifier = Modifier
                            .zIndex(95f)
                            .offset {
                                IntOffset(
                                    (localOffset.x - handCardWidth.toPx() / 2f).roundToInt(),
                                    (localOffset.y - handCardHeight.toPx() * 0.56f).roundToInt()
                                )
                            }
                    ) {
                        HandCardView(
                            handCard = card,
                            effectiveCost = combat.effectivePlayCost(card),
                            playable = combat.canPlayCard(card),
                            isFocused = true,
                            cardWidth = handCardWidth,
                            cardHeight = handCardHeight,
                            descFontSize = descFontSize,
                            statFontSize = statFontSize,
                            onClick = {}
                        )
                    }
                }
                is ActiveDragState.BenchUnitDrag -> {
                    val unit = currentDrag.unit
                    Box(
                        modifier = Modifier
                            .zIndex(95f)
                            .offset {
                                IntOffset(
                                    (localOffset.x - unitTileWidth.toPx() / 2f).roundToInt(),
                                    (localOffset.y - unitTileHeight.toPx() * 0.65f).roundToInt()
                                )
                            }
                    ) {
                        UnitToken(
                            unit = unit,
                            isSelected = true,
                            tileWidth = unitTileWidth * 1.08f,
                            tileHeight = unitTileHeight * 1.08f,
                            statFontSize = statFontSize,
                            onClick = {}
                        )
                    }
                }
                is ActiveDragState.CombatUnitDrag -> {
                    val unit = currentDrag.unit
                    Box(
                        modifier = Modifier
                            .zIndex(95f)
                            .offset {
                                IntOffset(
                                    (localOffset.x - unitTileWidth.toPx() / 2f).roundToInt(),
                                    (localOffset.y - unitTileHeight.toPx() * 0.65f).roundToInt()
                                )
                            }
                    ) {
                        UnitToken(
                            unit = unit,
                            isSelected = true,
                            tileWidth = unitTileWidth * 1.05f,
                            tileHeight = unitTileHeight * 1.05f,
                            statFontSize = statFontSize,
                            onClick = {}
                        )
                    }
                }
            }
        }

        // Tactile 6.5% Film Grain Overlay across the battlefield & cards
        FilmGrainOverlay(
            alpha = 0.065f,
            modifier = Modifier
                .fillMaxSize()
                .zIndex(50f)
        )

        // Hand Card Inspect / Quick-Play Overlay
        inspectedHandCard?.let { card ->
            HandCardInspectOverlay(
                card = card,
                canPlay = combat.canPlayCard(card),
                effectiveCost = combat.effectivePlayCost(card),
                detailOnly = inspectHandDetailOnly,
                minWidth = vw(28f),
                maxWidth = vw(40f),
                buttonHeight = maxOf(48.dp, vh(7f)),
                offsetY = -vh(6f),
                padH = vw(1.2f),
                padV = vh(1f),
                descFontSize = descFontSize,
                onPlay = {
                    triggerCardPlayOrTargeting(card, null)
                    inspectedHandCard = null
                    isHandExpanded = false
                },
                onDismiss = { inspectedHandCard = null }
            )
        }

        // Board Unit Inspect Side Popup
        inspectedUnit?.let { unit ->
            UnitInspectOverlay(
                unit = unit,
                sidePadding = vw(16f),
                minWidth = vw(26f),
                maxWidth = vw(36f),
                padH = vw(1.2f),
                padV = vh(0.8f),
                descFontSize = descFontSize,
                onDismiss = { inspectedUnit = null }
            )
        }

        // Emote Picker Overlay
        if (showEmotePicker) {
            EmotePickerOverlay(
                safeStartPadding = safeStart + vw(3f),
                bottomPadding = safeBottom + vh(11f),
                descFontSize = descFontSize,
                onSelectEmote = { emote ->
                    activeEmote = emote
                    showEmotePicker = false
                },
                onDismiss = { showEmotePicker = false }
            )
        }

        // Spell & Skill Cast / Impact VFX Overlay (non-touch-blocking)
        SpellSkillEffectOverlay(
            events = activeSpellVfx,
            topBannerOffset = safeTop + vh(8.5f),
            onFinished = { finishedId ->
                activeSpellVfx.removeAll { it.id == finishedId }
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(74f)
        )

        // Champion Level-Up (Lv2) Showcase Overlay Animation
        activeLevelUpBanner?.let { leveledChamp ->
            ChampionLevelUpBannerOverlay(
                unit = leveledChamp,
                onDismiss = { activeLevelUpBanner = null }
            )
        }
    }

    if (showPowerInfo) {
        EnemyPowerDialog(combat = combat, onDismiss = { showPowerInfo = false })
    }
    if (showLogDialog) {
        CombatLogDialog(combat = combat, onDismiss = { showLogDialog = false })
    }
    if (showGraveyardDialog) {
        GraveyardDialog(combat = combat, onDismiss = { showGraveyardDialog = false })
    }
    if (showMatchInfoDialog) {
        MatchInfoDialog(combat = combat, onDismiss = { showMatchInfoDialog = false })
    }
    if (showForfeitConfirm) {
        ForfeitConfirmDialog(
            onConfirmForfeit = {
                showForfeitConfirm = false
                onAbandonRun()
            },
            onDismiss = { showForfeitConfirm = false }
        )
    }
    CombatGameOverDialog(
        combat = combat,
        revivesRemaining = revivesRemaining,
        onVictoryContinue = onVictoryContinue,
        onUseRevive = onUseRevive,
        onAcceptDefeat = onAcceptDefeat
    )
}

@Composable
private fun EnemyFannedCardBacks(
    count: Int,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((-cardWidth * 0.45f)),
        verticalAlignment = Alignment.Top
    ) {
        val mid = (count - 1) / 2f
        repeat(count) { idx ->
            val angle = ((idx - mid) * 4.5f).coerceIn(-12f, 12f)
            Box(
                modifier = Modifier
                    .size(width = cardWidth, height = cardHeight)
                    .offset(y = (abs(idx - mid) * 1.5f).dp)
                    .graphicsLayer { rotationZ = angle }
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xFF1C3359), Color(0xFF0C172B))
                        )
                    )
                    .border(1.1.dp, HextechGold.copy(alpha = 0.85f), RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(3.dp)) {
                    if (size.width <= 1f || size.height <= 1f) return@Canvas
                    drawCircle(
                        color = Color(0x5500BFFF),
                        radius = size.minDimension * 0.25f
                    )
                    drawCircle(
                        color = HextechGold.copy(alpha = 0.7f),
                        radius = size.minDimension * 0.28f,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.2f)
                    )
                }
            }
        }
    }
}
