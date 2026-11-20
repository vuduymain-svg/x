package com.example.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.engine.CombatPhase
import com.example.engine.CombatState
import com.example.model.HandCardInstance
import com.example.model.UnitOnBoard
import com.example.ui.components.CardTile
import com.example.ui.components.unitPortraitRes
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.AccentSpellMana
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SemanticAttack
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticHeal
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceOverlay
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle

data class CombatEmoteOption(
    val id: String,
    val label: String,
    val icon: ImageVector
)

val defaultCombatEmotes = listOf(
    CombatEmoteOption("gg", "Đánh hay lắm", Icons.Outlined.ThumbUp),
    CombatEmoteOption("watch", "Xem này", Icons.Outlined.Bolt),
    CombatEmoteOption("shield", "Giữ vững đội hình", Icons.Outlined.Shield),
    CombatEmoteOption("star", "Tính toán cả đấy", Icons.Outlined.StarOutline),
    CombatEmoteOption("spark", "Rực cháy", Icons.Outlined.AutoAwesome),
    CombatEmoteOption("cheer", "Trận hay quá", Icons.Outlined.EmojiEmotions)
)

@Composable
fun HandCardInspectOverlay(
    card: HandCardInstance,
    canPlay: Boolean,
    effectiveCost: Int,
    detailOnly: Boolean,
    minWidth: Dp,
    maxWidth: Dp,
    buttonHeight: Dp,
    offsetY: Dp,
    padH: Dp,
    padV: Dp,
    descFontSize: TextUnit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(60f)
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = offsetY)
                .widthIn(min = minWidth, max = maxWidth)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(GameRadius.panel),
            color = SurfaceOverlay,
            border = BorderStroke(1.dp, if (canPlay) AccentIce else BorderGoldMuted),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(GameSpacing.md),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
            ) {
                CardTile(
                    template = card.template,
                    effectiveCost = effectiveCost,
                    bonusPower = card.bonusPower,
                    bonusHealth = card.bonusHealth,
                    attachedItems = card.attachedItems,
                    extraKeywords = card.extraKeywords,
                    isFleeting = card.isFleeting,
                    playable = canPlay
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    if (!detailOnly) {
                        Button(
                            enabled = canPlay,
                            onClick = onPlay,
                            modifier = Modifier
                                .weight(1f)
                                .height(maxOf(48.dp, buttonHeight))
                                .testTag("confirm_play_card_button"),
                            shape = RoundedCornerShape(GameRadius.control),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canPlay) AccentGold else SurfaceElevated,
                                contentColor = BgDeep,
                                disabledContainerColor = SurfaceElevated,
                                disabledContentColor = TextSecondary
                            )
                        ) {
                            Text(
                                text = if (canPlay) "Ra bài ($effectiveCost NL)" else "Không thể ra bài",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = descFontSize,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(maxOf(48.dp, buttonHeight))
                    ) {
                        Text(
                            "Đóng",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            fontSize = descFontSize
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun UnitInspectOverlay(
    unit: UnitOnBoard,
    sidePadding: Dp,
    minWidth: Dp,
    maxWidth: Dp,
    padH: Dp,
    padV: Dp,
    descFontSize: TextUnit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(65f)
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(if (unit.ownerIsPlayer) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = sidePadding)
                .widthIn(min = minWidth, max = maxWidth)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(GameRadius.panel),
            color = SurfaceOverlay,
            border = BorderStroke(1.dp, BorderGoldMuted),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(GameSpacing.md),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                CardTile(
                    template = unit.template,
                    effectiveCost = unit.template.cost,
                    bonusPower = unit.strikePower - unit.template.basePower,
                    bonusHealth = unit.health - unit.template.baseHealth,
                    attachedItems = unit.attachedItems,
                    extraKeywords = unit.keywords,
                    playable = true,
                    isLeveledUp = unit.isLeveledUp
                )
                if (unit.equippedGearName != null) {
                    Text(
                        text = "Trang bị: ${unit.equippedGearName}",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = descFontSize,
                        color = AccentGold
                    )
                }
                if (unit.isStunned || unit.isFrostbitten || unit.hasBarrier || unit.hasSpellShield) {
                    val statuses = buildList {
                        if (unit.hasBarrier) add("Lá chắn")
                        if (unit.hasSpellShield) add("Khiên phép")
                        if (unit.isFrostbitten) add("Đóng băng")
                        if (unit.isStunned) add("Choáng")
                    }
                    Text(
                        text = "Trạng thái: ${statuses.joinToString()}",
                        fontFamily = PlusJakartaSansFamily,
                        fontSize = descFontSize,
                        color = AccentIce
                    )
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(
                        "Đóng",
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        color = AccentGold,
                        fontSize = descFontSize
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EmotePickerOverlay(
    safeStartPadding: Dp,
    bottomPadding: Dp,
    descFontSize: TextUnit,
    onSelectEmote: (CombatEmoteOption) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(62f)
            .clickable(onClick = onDismiss)
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = safeStartPadding, bottom = bottomPadding)
                .widthIn(max = 320.dp)
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(GameRadius.panel),
            color = SurfaceOverlay,
            border = BorderStroke(1.dp, BorderGoldMuted),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(GameSpacing.md),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                Text(
                    text = "Gửi biểu cảm",
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = descFontSize,
                    fontWeight = FontWeight.SemiBold,
                    color = AccentGold
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    defaultCombatEmotes.forEach { emote ->
                        Surface(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .clickable { onSelectEmote(emote) },
                            shape = RoundedCornerShape(GameRadius.control),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                            ) {
                                Icon(
                                    imageVector = emote.icon,
                                    contentDescription = emote.label,
                                    tint = AccentGold,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = emote.label,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = descFontSize,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GraveyardDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                text = "Nghĩa địa (${combat.playerGraveyard.size + combat.enemyGraveyard.size})",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AccentGold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                item {
                    Text(
                        text = "Nghĩa địa của bạn (${combat.playerGraveyard.size})",
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = AccentIce
                    )
                }
                if (combat.playerGraveyard.isEmpty()) {
                    item {
                        Text(
                            text = "Chưa có đồng minh tử trận hoặc phép được thi triển.",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(combat.playerGraveyard.reversed()) { card ->
                        Text(
                            text = "• ${card.name} (${card.type.displayName}, ${card.cost} NL)",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(GameSpacing.xs))
                    HorizontalDivider(color = BorderSubtle)
                    Spacer(modifier = Modifier.height(GameSpacing.xs))
                    Text(
                        text = "Nghĩa địa kẻ địch (${combat.enemyGraveyard.size})",
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = SemanticDanger
                    )
                }
                if (combat.enemyGraveyard.isEmpty()) {
                    item {
                        Text(
                            text = "Chưa có kẻ địch bị tiêu diệt hoặc phép được thi triển.",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextSecondary
                        )
                    }
                } else {
                    items(combat.enemyGraveyard.reversed()) { card ->
                        Text(
                            text = "• ${card.name} (${card.type.displayName}, ${card.cost} NL)",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = AccentGold)
            }
        }
    )
}

@Composable
fun MatchInfoDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                text = "Trạng thái trận đấu — Vòng ${combat.round}",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AccentGold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                item {
                    Text(
                        text = "${combat.encounterNode.enemyName} — ${combat.encounterNode.enemyPowerTitle}",
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = SemanticDanger
                    )
                    Text(
                        text = combat.encounterNode.enemyPowerDescription.ifBlank { combat.encounterNode.subtitle },
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
                if (combat.activePowers.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = BorderSubtle)
                        Spacer(modifier = Modifier.height(GameSpacing.xs))
                        Text(
                            text = "Sức mạnh đang kích hoạt",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AccentIce
                        )
                    }
                    items(combat.activePowers) { power ->
                        Text(
                            text = "• ${power.name}: ${power.description}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
                if (combat.equippedRelics.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = BorderSubtle)
                        Spacer(modifier = Modifier.height(GameSpacing.xs))
                        Text(
                            text = "Cổ vật đã trang bị",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = SemanticAttack
                        )
                    }
                    items(combat.equippedRelics) { relic ->
                        Text(
                            text = "• ${relic.name}: ${relic.description}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
                if (combat.mutators.isNotEmpty()) {
                    item {
                        HorizontalDivider(color = BorderSubtle)
                        Spacer(modifier = Modifier.height(GameSpacing.xs))
                        Text(
                            text = "Hiệu ứng cuộc phiêu lưu",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AccentSpellMana
                        )
                    }
                    items(combat.mutators) { mut ->
                        Text(
                            text = "• ${mut.name}: ${mut.description}",
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp,
                            color = TextPrimary
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = AccentGold)
            }
        }
    )
}

@Composable
fun EnemyPowerDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                text = combat.encounterNode.enemyPowerTitle.ifBlank { combat.encounterNode.enemyName },
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = SemanticDanger
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)) {
                Text(
                    text = combat.encounterNode.enemyPowerDescription.ifBlank { combat.encounterNode.subtitle },
                    color = TextPrimary,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp
                )
                if (combat.mutators.isNotEmpty()) {
                    HorizontalDivider(color = BorderSubtle)
                    combat.mutators.forEach { mut ->
                        Text(
                            text = "${mut.name}: ${mut.description}",
                            color = AccentIce,
                            fontFamily = InterFontFamily,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = AccentGold)
            }
        }
    )
}

@Composable
fun CombatLogDialog(
    combat: CombatState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                "Nhật ký giao tranh — Vòng ${combat.round}",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AccentGold
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
            ) {
                items(combat.combatLog.takeLast(16).reversed()) { entry ->
                    Text(
                        "• $entry",
                        fontFamily = InterFontFamily,
                        fontSize = 12.sp,
                        color = TextPrimary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Đóng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = AccentGold)
            }
        }
    )
}

@Composable
fun ForfeitConfirmDialog(
    onConfirmForfeit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                "Từ bỏ cuộc phiêu lưu?",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = SemanticDanger
            )
        },
        text = {
            Text(
                "Rời trận giữa chừng sẽ hủy bỏ hành trình hiện tại.",
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp,
                color = TextPrimary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmForfeit,
                shape = RoundedCornerShape(GameRadius.control),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SemanticDanger,
                    contentColor = TextPrimary
                )
            ) {
                Text("Đầu hàng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Ở lại", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = AccentIce)
            }
        }
    )
}

@Composable
fun CombatGameOverDialog(
    combat: CombatState,
    revivesRemaining: Int,
    onVictoryContinue: () -> Unit,
    onUseRevive: () -> Unit,
    onAcceptDefeat: () -> Unit
) {
    if (combat.phase != CombatPhase.GAME_OVER) return
    val won = combat.playerWon == true
    AlertDialog(
        onDismissRequest = {},
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                text = if (won) "Chiến thắng" else "Nhà Chính bị phá hủy",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = if (won) AccentGold else SemanticDanger
            )
        },
        text = {
            Text(
                text = if (won) {
                    "Bạn đã đánh bại ${combat.encounterNode.enemyName} và nhận +${combat.encounterNode.goldReward} Vàng."
                } else if (revivesRemaining > 0) {
                    "Nhà Chính đã sụp đổ, nhưng bạn còn $revivesRemaining lần hồi sinh. Khôi phục 50% máu Nhà Chính và đấu lại trận này."
                } else {
                    "Nhà Chính đã sụp đổ và bạn không còn lần hồi sinh nào."
                },
                color = TextPrimary,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 13.sp
            )
        },
        confirmButton = {
            if (won) {
                Button(
                    onClick = onVictoryContinue,
                    modifier = Modifier.testTag("combat_victory_continue_button"),
                    shape = RoundedCornerShape(GameRadius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold, contentColor = BgDeep)
                ) {
                    Text("Nhận phần thưởng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold)
                }
            } else if (revivesRemaining > 0) {
                Button(
                    onClick = onUseRevive,
                    modifier = Modifier.testTag("use_revive_button"),
                    shape = RoundedCornerShape(GameRadius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticHeal, contentColor = BgDeep)
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(GameSpacing.xs))
                    Text("Hồi sinh (50% máu)", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = onAcceptDefeat,
                    modifier = Modifier.testTag("accept_defeat_button"),
                    shape = RoundedCornerShape(GameRadius.control),
                    colors = ButtonDefaults.buttonColors(containerColor = SemanticDanger, contentColor = TextPrimary)
                ) {
                    Text("Kết thúc", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            if (!won && revivesRemaining > 0) {
                TextButton(onClick = onAcceptDefeat) {
                    Text("Chấp nhận thua", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = TextSecondary)
                }
            }
        }
    )
}

@Composable
fun ChampionLevelUpBannerOverlay(
    unit: UnitOnBoard,
    onDismiss: () -> Unit
) {
    val revealScale = remember(unit.instanceId) { Animatable(0.85f) }

    LaunchedEffect(unit.instanceId) {
        revealScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 200)
        )
    }

    val portraitRes = unitPortraitRes(unit)
    val subtitleText = when (unit.template.id) {
        "card_anivia" -> "Khai Sáng — Phượng Hoàng Băng Thăng Hoa"
        "card_jinx" -> "Siêu Quậy Bùng Nổ — Tên Lửa Khổng Lồ"
        "card_lux" -> "Ánh Sáng Tối Thượng — Cầu Vồng Rực Rỡ"
        "card_yasuo" -> "Tuyệt Kỹ Kiếm Gió — Bão Thép"
        else -> "Sức mạnh anh hùng thức tỉnh (+${unit.template.levelUpPowerBonus}/+${unit.template.levelUpHealthBonus})"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(88f)
            .background(BgDeep.copy(alpha = 0.72f))
            .testTag("champion_level_up_overlay")
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 240.dp, max = 328.dp)
                .graphicsLayer {
                    scaleX = revealScale.value
                    scaleY = revealScale.value
                },
            shape = RoundedCornerShape(GameRadius.panel),
            color = SurfaceBase,
            border = BorderStroke(1.5.dp, AccentGold),
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(GameSpacing.lg),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                Surface(
                    shape = RoundedCornerShape(GameRadius.badge),
                    color = SurfaceElevated,
                    border = BorderStroke(1.dp, BorderGoldMuted)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = null,
                            tint = AccentGold,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Thăng cấp • Cấp 2",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = AccentGold
                        )
                    }
                }

                if (portraitRes != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(128.dp)
                            .clip(RoundedCornerShape(GameRadius.card))
                            .border(1.5.dp, AccentIce, RoundedCornerShape(GameRadius.card))
                    ) {
                        Image(
                            painter = painterResource(id = portraitRes),
                            contentDescription = "${unit.template.name} Cấp 2",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(GameSpacing.sm),
                            shape = RoundedCornerShape(GameRadius.badge),
                            color = SurfaceOverlay,
                            border = BorderStroke(1.dp, BorderGoldMuted)
                        ) {
                            Text(
                                text = "Lv 2",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentIce,
                                modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = unit.template.name,
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = subtitleText,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = AccentIce,
                    textAlign = TextAlign.Center
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(GameRadius.badge),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, SemanticAttack.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "${unit.strikePower} Công",
                            style = statNumericStyle(12.sp, AccentGold),
                            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs)
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(GameRadius.badge),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, SemanticDanger.copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "${unit.health} Máu",
                            style = statNumericStyle(12.sp, TextPrimary),
                            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs)
                        )
                    }
                }
            }
        }
    }
}
