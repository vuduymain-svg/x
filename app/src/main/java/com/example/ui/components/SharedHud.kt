package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Diamond
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MonetizationOn
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.engine.PriorityHolder
import com.example.model.CardCatalog
import com.example.model.LegendProfile
import com.example.model.PowerTemplate
import com.example.model.Rarity
import com.example.model.Region
import com.example.model.RunState
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.AccentSpellMana
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CommonIron
import com.example.ui.theme.CurseMagenta
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SemanticAttack
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticEpic
import com.example.ui.theme.SemanticHeal
import com.example.ui.theme.SemanticRare
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceOverlay
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle

fun rarityColor(rarity: Rarity): Color = when (rarity) {
    Rarity.COMMON -> CommonIron
    Rarity.RARE -> SemanticRare
    Rarity.EPIC -> SemanticEpic
    Rarity.LEGENDARY -> SemanticAttack
}

fun regionAccentColor(region: Region): Color = when (region) {
    Region.PILTOVER_ZAUN -> AccentIce
    Region.DEMACIA -> AccentGold
    Region.IONIA -> CurseMagenta
    Region.FRELJORD -> AccentIce
    Region.SHADOW_ISLES -> SemanticHeal
    Region.TARGON -> AccentSpellMana
    Region.NOXUS -> SemanticDanger
    Region.RUNETERRA -> SemanticAttack
}

@DrawableRes
fun championPortraitRes(championId: String): Int? = when (championId) {
    "champ_jinx" -> R.drawable.img_champ_jinx
    "champ_lux" -> R.drawable.img_champ_lux
    "champ_yasuo" -> R.drawable.img_champ_yasuo
    "champ_anivia" -> R.drawable.img_champ_anivia
    else -> null
}

@Composable
fun ChampionAvatarBadge(
    championId: String,
    name: String,
    region: Region,
    size: Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val res = championPortraitRes(championId)
    val accent = regionAccentColor(region)
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceBase)
            .border(1.5.dp, AccentGold, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (res != null) {
            Image(
                painter = painterResource(id = res),
                contentDescription = "Ảnh $name",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = name.take(2).uppercase(),
                fontFamily = PlusJakartaSansFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
    }
}

@Composable
fun MetaCurrencyBar(
    profile: LegendProfile,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CurrencyPill(
            icon = Icons.Outlined.StarOutline,
            label = "Huyền thoại ${profile.legendLevel}",
            tint = AccentGold
        )
        CurrencyPill(
            icon = Icons.Outlined.AutoAwesome,
            label = "${profile.wildFragments} Vạn năng",
            tint = AccentIce
        )
        CurrencyPill(
            icon = Icons.Outlined.Bolt,
            label = "${profile.starCrystals} Tinh thể sao",
            tint = SemanticAttack
        )
        CurrencyPill(
            icon = Icons.Outlined.Shield,
            label = "${profile.novaCrystals} Tinh thể Nova",
            tint = AccentSpellMana
        )
        CurrencyPill(
            icon = Icons.Outlined.Diamond,
            label = "${profile.gemstones} Đá quý",
            tint = SemanticHeal
        )
    }
}

@Composable
fun CurrencyPill(
    icon: ImageVector,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(GameRadius.control),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, BorderSubtle),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = label,
                style = statNumericStyle(11.sp, TextPrimary),
                maxLines = 1
            )
        }
    }
}

@Composable
fun RunResourceHud(
    run: RunState,
    onOpenDeck: () -> Unit,
    onAbandon: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LorMetallicPanel(
        modifier = modifier.fillMaxWidth(),
        tone = LorMetalTone.GOLD,
        cornerRadius = GameRadius.card,
        borderWidth = 1.dp,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = GameSpacing.md,
            vertical = GameSpacing.sm
        )
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CurrencyPill(
                        icon = Icons.Outlined.FavoriteBorder,
                        label = "${run.currentNexusHp}/${run.maxNexusHp}",
                        tint = SemanticDanger
                    )
                    CurrencyPill(
                        icon = Icons.Outlined.MonetizationOn,
                        label = "${run.gold} Vàng",
                        tint = AccentGold
                    )
                    CurrencyPill(
                        icon = Icons.Outlined.Casino,
                        label = "${run.rerollsRemaining}",
                        tint = AccentIce
                    )
                    CurrencyPill(
                        icon = Icons.Outlined.Refresh,
                        label = "${run.revivesRemaining}",
                        tint = SemanticHeal
                    )
                }

                Spacer(modifier = Modifier.width(GameSpacing.sm))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                ) {
                    Surface(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("run_deck_button")
                            .clickable(onClick = onOpenDeck),
                        shape = RoundedCornerShape(GameRadius.control),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderGoldMuted)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Style,
                                contentDescription = "Xem bộ bài",
                                tint = AccentGold,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "${run.runDeck.sumOf { it.copies }}",
                                style = statNumericStyle(11.sp, TextPrimary)
                            )
                        }
                    }

                    if (onAbandon != null) {
                        IconButton(
                            onClick = onAbandon,
                            modifier = Modifier.testTag("abandon_run_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Từ bỏ hành trình",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            if (run.activePowerIds.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                ) {
                    run.activePowerIds.forEach { powerId ->
                        PowerChip(power = CardCatalog.getPower(powerId))
                    }
                }
            }
        }
    }
}

@Composable
fun PowerChip(
    power: PowerTemplate,
    modifier: Modifier = Modifier
) {
    val accent = rarityColor(power.rarity)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(GameRadius.badge),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            Icon(
                imageVector = Icons.Outlined.Bolt,
                contentDescription = power.name,
                tint = accent,
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = power.name,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun RelicSlotCard(
    slotIndex: Int,
    relicId: String?,
    unlocked: Boolean,
    isCosmicForged: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val relic = relicId?.let { CardCatalog.getRelic(it) }
    val borderColor = when {
        !unlocked -> BorderSubtle
        isCosmicForged -> AccentSpellMana.copy(alpha = 0.7f)
        relic != null -> rarityColor(relic.rarity).copy(alpha = 0.65f)
        else -> BorderGoldMuted
    }
    val iconTint = when {
        !unlocked -> TextMuted
        isCosmicForged -> AccentSpellMana
        relic != null -> rarityColor(relic.rarity)
        else -> AccentGold
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("relic_slot_$slotIndex")
            .then(
                if (unlocked) Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(GameRadius.card),
        color = SurfaceBase,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(GameSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceElevated)
                    .border(1.dp, iconTint.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (!unlocked) Icons.Outlined.Lock else Icons.Outlined.Shield,
                    contentDescription = if (unlocked) "Ô cổ vật ${slotIndex + 1}" else "Ô bị khóa",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    Text(
                        text = when {
                            !unlocked -> "Ô ${slotIndex + 1} (Đang khóa)"
                            relic != null -> relic.name
                            else -> "Ô cổ vật trống ${slotIndex + 1}"
                        },
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = if (unlocked) TextPrimary else TextSecondary
                    )
                    if (isCosmicForged) {
                        Text(
                            text = "Đã rèn sử thi",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = AccentSpellMana
                        )
                    }
                }
                Text(
                    text = when {
                        !unlocked -> "Tăng cấp Tướng hoặc Rèn Vũ Trụ để mở khóa."
                        relic != null -> relic.description
                        else -> "Chạm để trang bị Cổ Vật."
                    },
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

/**
 * Clean Obsidian Slate right-edge Mana & Turn Action HUD for CombatScreen.
 * - Replaces infiniteRepeatable pulsing rings with animateColorAsState(tween(150)).
 * - Visually separates Regular Mana (0..10) from the 3 Spell Mana gems (0..3).
 * - Highlights regularMana >= 10 (Enlightened / Khai Sang) in AccentIce (0xFF38BDF8).
 * - Uses tabular-nums JetBrains Mono for all counters and Sentence case for labels.
 */
@Composable
fun TacticalManaActionColumn(
    combat: CombatState,
    panelWidth: Dp,
    onEnterAttack: () -> Unit,
    onCancelAttack: () -> Unit,
    onCommitAttack: () -> Unit,
    onCommitBlock: () -> Unit,
    onPassTurn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlayerPriority = combat.priorityHolder == PriorityHolder.PLAYER

    // Decisive 150ms border color shift only on turn/priority transitions
    val hudBorderColor by animateColorAsState(
        targetValue = if (isPlayerPriority) AccentGold else BorderSubtle,
        animationSpec = tween(durationMillis = 150),
        label = "tactical_hud_border"
    )

    val (btnLabel, btnTag, btnAction) = when {
        !isPlayerPriority -> Triple(
            "Địch đang đi",
            when (combat.phase) {
                CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> "commit_attack_button"
                CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> "commit_block_button"
                else -> "end_round_button"
            },
            onPassTurn
        )
        combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> {
            if (combat.spellStack.isNotEmpty()) {
                Triple("Dùng", "end_round_button", onPassTurn)
            } else {
                Triple(
                    if (combat.attackingUnitIds.isNotEmpty()) "Tấn công (${combat.attackingUnitIds.size})" else "Tấn công",
                    "commit_attack_button",
                    onCommitAttack
                )
            }
        }
        combat.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> Triple(
            when {
                combat.playerInitiatedCombat -> if (combat.blockCommitted) "Xác nhận" else "Chờ địch"
                combat.blockPairings.isNotEmpty() -> "Chặn (${combat.blockPairings.size})"
                combat.spellStack.isNotEmpty() -> "Dùng"
                else -> "Xác nhận"
            },
            "commit_block_button",
            onCommitBlock
        )
        combat.phase == CombatPhase.SPELL_STACK_PHASE || combat.spellStack.isNotEmpty() -> Triple(
            "Dùng",
            "end_round_button",
            onPassTurn
        )
        else -> Triple(
            if (combat.consecutivePasses >= 1) "Hết vòng" else "Bỏ qua",
            "end_round_button",
            onPassTurn
        )
    }

    val btnEnabled = when {
        !isPlayerPriority -> false
        combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE && combat.spellStack.isEmpty() ->
            combat.attackingUnitIds.isNotEmpty()
        else -> true
    }

    val canInitiateAttack = combat.phase == CombatPhase.MAIN_ACTION_PHASE &&
        isPlayerPriority &&
        combat.playerHasAttackToken &&
        combat.spellStack.isEmpty() &&
        combat.playerBoard.any { it.canAttack }

    val playerEnlightened = combat.playerMana >= 10
    val enemyEnlightened = combat.enemyMana >= 10
    val playerManaColor by animateColorAsState(
        targetValue = if (playerEnlightened) AccentIce else TextPrimary,
        animationSpec = tween(durationMillis = 150),
        label = "player_mana_color"
    )
    val playerManaBorderColor by animateColorAsState(
        targetValue = if (playerEnlightened) AccentIce else BorderSubtle,
        animationSpec = tween(durationMillis = 150),
        label = "player_mana_border"
    )

    val turnBtnBg by animateColorAsState(
        targetValue = if (btnEnabled) AccentGold else SurfaceElevated,
        animationSpec = tween(durationMillis = 150),
        label = "turn_btn_bg"
    )

    Surface(
        modifier = modifier.width(panelWidth),
        shape = RoundedCornerShape(GameRadius.panel),
        color = SurfaceOverlay,
        border = BorderStroke(1.dp, hudBorderColor),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(GameSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(GameSpacing.xs),
            horizontalAlignment = Alignment.End
        ) {
            // 1. Enemy Mana + Spell Mana Row (Compact)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${combat.enemyMana}/10",
                        style = statNumericStyle(
                            11.sp,
                            if (enemyEnlightened) AccentIce else TextSecondary
                        ),
                        modifier = Modifier.testTag("enemy_mana_text")
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                        val maxE = combat.enemyMaxMana.coerceIn(1, 10)
                        val curE = combat.enemyMana.coerceIn(0, maxE)
                        repeat(10) { idx ->
                            val pipColor = when {
                                idx < curE -> if (enemyEnlightened) AccentIce else SemanticDanger
                                idx < maxE -> SurfaceElevated
                                else -> BgDeep
                            }
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(pipColor)
                            )
                        }
                    }
                }

                // Enemy 3 Spell Mana Gems
                val enemySpell = combat.enemySpellMana.coerceIn(0, 3)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$enemySpell",
                        style = statNumericStyle(10.sp, if (enemySpell > 0) AccentSpellMana else TextMuted),
                        modifier = Modifier.testTag("enemy_spell_mana_text")
                    )
                    repeat(3) { idx ->
                        val filled = idx < enemySpell
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (filled) AccentSpellMana else SurfaceElevated)
                                .border(1.dp, if (filled) AccentSpellMana else BorderSubtle, CircleShape)
                        )
                    }
                }
            }

            // 2. Attack Token / Cancel Control + Primary Turn Button (48.dp interactive row)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (combat.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .clickable(
                                enabled = isPlayerPriority && combat.spellStack.isEmpty(),
                                onClick = onCancelAttack
                            ),
                        shape = RoundedCornerShape(GameRadius.control),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, SemanticDanger.copy(alpha = 0.7f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "Hủy tấn công",
                                tint = SemanticDanger,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                } else {
                    val tokenTint = when {
                        combat.playerHasAttackToken -> AccentGold
                        combat.enemyHasAttackToken -> SemanticDanger
                        else -> TextMuted
                    }
                    val tokenBorder = when {
                        combat.playerHasAttackToken -> BorderGoldMuted
                        combat.enemyHasAttackToken -> SemanticDanger.copy(alpha = 0.55f)
                        else -> BorderSubtle
                    }
                    val tokenDesc = when {
                        combat.playerHasAttackToken -> "Lượt công"
                        combat.enemyHasAttackToken -> "Địch công"
                        else -> "Hết công"
                    }
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .then(
                                if (canInitiateAttack) {
                                    Modifier
                                        .testTag("enter_attack_button")
                                        .clickable(onClick = onEnterAttack)
                                } else Modifier
                            ),
                        shape = RoundedCornerShape(GameRadius.control),
                        color = if (canInitiateAttack) AccentGold.copy(alpha = 0.16f) else SurfaceElevated,
                        border = BorderStroke(1.dp, tokenBorder)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (combat.playerHasAttackToken || combat.enemyHasAttackToken) {
                                    Icons.Outlined.Gavel
                                } else {
                                    Icons.Outlined.Shield
                                },
                                contentDescription = tokenDesc,
                                tint = tokenTint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .minimumInteractiveComponentSize()
                        .testTag(btnTag)
                        .clickable(enabled = btnEnabled, onClick = btnAction),
                    shape = RoundedCornerShape(GameRadius.control),
                    color = turnBtnBg,
                    border = BorderStroke(1.dp, if (btnEnabled) BorderGoldMuted else BorderSubtle)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = GameSpacing.sm),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = btnLabel,
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = if (btnLabel.length > 9) 11.sp else 13.sp,
                            color = if (btnEnabled) BgDeep else TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 3. Player Regular Mana (0..10) — AccentIce highlight when Enlightened (>= 10)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(GameRadius.control),
                color = if (playerEnlightened) AccentIce.copy(alpha = 0.14f) else SurfaceBase,
                border = BorderStroke(1.dp, playerManaBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${combat.playerMana}/10",
                        style = statNumericStyle(13.sp, playerManaColor),
                        modifier = Modifier.testTag("player_mana_text")
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val maxP = combat.playerMaxMana.coerceIn(1, 10)
                        val curP = combat.playerMana.coerceIn(0, maxP)
                        repeat(10) { idx ->
                            val filled = idx < curP
                            val unlockedEmpty = idx in curP until maxP
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        when {
                                            filled -> AccentIce
                                            unlockedEmpty -> SurfaceElevated
                                            else -> BgDeep
                                        }
                                    )
                                    .border(
                                        0.5.dp,
                                        when {
                                            filled -> AccentIce
                                            unlockedEmpty -> TextMuted.copy(alpha = 0.5f)
                                            else -> BorderSubtle
                                        },
                                        RoundedCornerShape(1.dp)
                                    )
                            )
                        }
                    }
                }
            }

            // 4. Visually Separated Player Spell Mana Badge + 3 Gems (0..3)
            val spellMana = combat.playerSpellMana.coerceIn(0, 3)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("player_spell_mana_badge"),
                shape = RoundedCornerShape(GameRadius.badge),
                color = SurfaceBase,
                border = BorderStroke(1.dp, if (spellMana > 0) AccentSpellMana else BorderSubtle)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Phép",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "$spellMana",
                            style = statNumericStyle(
                                11.sp,
                                if (spellMana > 0) AccentSpellMana else TextMuted
                            ),
                            modifier = Modifier.testTag("player_spell_mana_text")
                        )
                        repeat(3) { idx ->
                            val filled = idx < spellMana
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (filled) AccentSpellMana else SurfaceElevated)
                                    .border(
                                        1.dp,
                                        if (filled) AccentSpellMana else BorderSubtle,
                                        CircleShape
                                    )
                                    .testTag("player_spell_mana_gem_$idx")
                            )
                        }
                    }
                }
            }
        }
    }
}
