package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.ui.components.ChampionAvatarBadge
import com.example.ui.components.KeywordGlossaryDialog
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.LorSecondaryMetalButton
import com.example.ui.components.championPortraitRes
import com.example.ui.components.regionAccentColor
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SemanticAttack
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticHeal
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle
import com.example.ui.theme.titleHeadingStyle
import com.example.ui.theme.uiBodyStyle
import com.example.viewmodel.GameUiState

@Composable
fun HomeHubScreen(
    uiState: GameUiState,
    onSelectChampion: (String) -> Unit,
    onUnlockChampion: (String) -> Unit,
    onOpenChampionDetail: (String) -> Unit,
    onOpenAdventures: () -> Unit,
    onQuickBattle: () -> Unit = {},
    onResumeRun: () -> Unit,
    onAbandonRun: () -> Unit,
    onGrantUnlimitedTestResources: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showGlossary by remember { mutableStateOf(false) }

    val selectedChamp = CardCatalog.findChampionOrNull(uiState.selectedChampionId)
        ?: CardCatalog.champions.firstOrNull()
        ?: return
    val selectedLoadout = uiState.championLoadouts[selectedChamp.id]
    val selectedAccent = regionAccentColor(selectedChamp.region)
    val selectedPortrait = championPortraitRes(selectedChamp.id)

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = selectedAccent
    ) {
        // Restrained static Obsidian Slate scrim over portrait backdrop (no neon glow)
        if (selectedPortrait != null) {
            Image(
                painter = painterResource(id = selectedPortrait),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.18f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(BgDeep.copy(alpha = 0.85f), BgDeep.copy(alpha = 0.72f), BgDeep)
                        )
                    )
            )
        }

        val topCompactBar: @Composable () -> Unit = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = GameSpacing.lg, vertical = GameSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                ) {
                    Text(
                        text = "Con đường Anh hùng",
                        style = titleHeadingStyle(16.sp, AccentGold),
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                    ) {
                        Text(
                            text = "Huyền thoại ${uiState.legendProfile.legendLevel} • ${uiState.legendProfile.wildFragments} Vạn năng • ${uiState.legendProfile.starCrystals} Tinh thể sao",
                            style = statNumericStyle(11.sp, AccentIce),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        LinearProgressIndicator(
                            progress = {
                                (uiState.legendProfile.legendPoints.toFloat() /
                                    uiState.legendProfile.pointsToNextLevel.coerceAtLeast(1)).coerceIn(0f, 1f)
                            },
                            modifier = Modifier
                                .width(64.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(GameRadius.badge)),
                            color = AccentGold,
                            trackColor = SurfaceElevated
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CompactUtilityPill(
                        label = "∞",
                        tint = SemanticHeal,
                        testTag = "grant_unlimited_resources_button",
                        onClick = onGrantUnlimitedTestResources
                    )
                    CompactUtilityPill(
                        icon = Icons.Outlined.AutoStories,
                        contentDescription = "Sổ tay từ khóa",
                        tint = AccentIce,
                        testTag = "open_glossary_button",
                        onClick = { showGlossary = true }
                    )
                }
            }
        }

        val savedRunBar: @Composable () -> Unit = {
            if (uiState.savedRun != null) {
                val savedChampName = CardCatalog.findChampionOrNull(uiState.savedRun.championId)?.name
                    ?: uiState.savedRun.championId
                val savedAdvName = AdventureCatalog.findAdventureOrNull(uiState.savedRun.adventureId)?.name
                    ?: uiState.savedRun.adventureId
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GameRadius.card),
                    color = SurfaceBase,
                    border = BorderStroke(1.dp, AccentIce.copy(alpha = 0.55f)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Hành trình đang chơi • $savedChampName ($savedAdvName)",
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = AccentIce,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Máu ${uiState.savedRun.currentNexusHp}/${uiState.savedRun.maxNexusHp} • ${uiState.savedRun.gold} Vàng • ${uiState.savedRun.battlesWon} Thắng",
                                style = statNumericStyle(11.sp, TextSecondary)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("abandon_saved_run_button")
                                .clickable(onClick = onAbandonRun)
                                .padding(horizontal = GameSpacing.sm, vertical = GameSpacing.sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Bỏ",
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = SemanticDanger
                            )
                        }
                        LorBrassButton(
                            text = "Tiếp tục",
                            onClick = onResumeRun,
                            isCyanVariant = true,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("resume_run_button"),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.PlayArrow,
                                    contentDescription = "Tiếp tục",
                                    tint = BgDeep,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        val rosterStrip: @Composable () -> Unit = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.md),
                contentPadding = PaddingValues(horizontal = GameSpacing.xs)
            ) {
                items(CardCatalog.champions, key = { it.id }) { champ ->
                    val loadout = uiState.championLoadouts[champ.id]
                    val isSelected = champ.id == uiState.selectedChampionId
                    val unlocked = loadout?.isUnlocked == true
                    val stars = loadout?.starLevel ?: 0

                    Column(
                        modifier = Modifier
                            .width(76.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("roster_champ_${champ.id}")
                            .clickable { onSelectChampion(champ.id) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                    ) {
                        Box(contentAlignment = Alignment.BottomCenter) {
                            ChampionAvatarBadge(
                                championId = champ.id,
                                name = champ.name,
                                region = champ.region,
                                size = if (isSelected) 56.dp else 48.dp
                            )
                            Surface(
                                modifier = Modifier.offset(y = 4.dp),
                                shape = RoundedCornerShape(GameRadius.badge),
                                color = SurfaceBase,
                                border = BorderStroke(
                                    1.dp,
                                    when {
                                        isSelected -> AccentGold
                                        unlocked -> BorderSubtle
                                        else -> SemanticDanger.copy(alpha = 0.7f)
                                    }
                                )
                            ) {
                                if (unlocked) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(
                                            text = "$stars",
                                            style = statNumericStyle(
                                                10.sp,
                                                if (isSelected) AccentGold else SemanticAttack
                                            )
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.Star,
                                            contentDescription = "Cấp sao",
                                            tint = if (isSelected) AccentGold else SemanticAttack,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.Lock,
                                        contentDescription = "Chưa mở khóa",
                                        tint = SemanticDanger,
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .size(11.dp)
                                    )
                                }
                            }
                        }
                        Text(
                            text = champ.name,
                            fontFamily = CinzelFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isSelected) AccentGold else TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        val championShowcase: @Composable (Boolean) -> Unit = { compactWide ->
            if (selectedLoadout != null) {
                val activeStarPower = selectedChamp.starPowers.lastOrNull { it.starLevel <= selectedLoadout.starLevel }
                val equippedCount = selectedLoadout.equippedRelics.count { it != null }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                    ) {
                        ChampionAvatarBadge(
                            championId = selectedChamp.id,
                            name = selectedChamp.name,
                            region = selectedChamp.region,
                            size = 52.dp
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedChamp.name,
                                style = titleHeadingStyle(20.sp, AccentGold),
                                maxLines = 1
                            )
                            Text(
                                text = "${selectedChamp.title} • Cấp ${selectedLoadout.championLevel}",
                                style = uiBodyStyle(12.sp, selectedAccent),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                                for (star in 1..6) {
                                    val filled = star <= selectedLoadout.starLevel
                                    Icon(
                                        imageVector = if (filled) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        contentDescription = null,
                                        tint = if (filled) SemanticAttack else TextMuted,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                        if (compactWide) {
                            LorSecondaryMetalButton(
                                text = "Sao & Bộ bài",
                                onClick = { onOpenChampionDetail(selectedChamp.id) },
                                accentColor = AccentGold,
                                modifier = Modifier.testTag("champion_mastery_button"),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Shield,
                                        contentDescription = null,
                                        tint = AccentGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = BorderSubtle)

                    Text(
                        text = "Máu ${selectedLoadout.maxNexusHp} • +${selectedLoadout.victoryHeal}/Thắng • ${selectedLoadout.startingGold} Vàng • ${selectedLoadout.startingRerolls} Đổi lại • Cổ vật $equippedCount/${selectedLoadout.unlockedRelicSlots}",
                        style = statNumericStyle(11.sp, TextPrimary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (activeStarPower != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(GameRadius.control),
                            color = SurfaceBase,
                            border = BorderStroke(1.dp, BorderGoldMuted),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm),
                                verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                            ) {
                                Text(
                                    text = "Cấp ${activeStarPower.starLevel} • ${activeStarPower.title}",
                                    fontFamily = CinzelFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = AccentGold
                                )
                                Text(
                                    text = activeStarPower.description,
                                    style = uiBodyStyle(11.5.sp, TextSecondary),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    if (!compactWide) {
                        LorSecondaryMetalButton(
                            text = "Chòm sao, Bộ bài & Cổ vật ($equippedCount/${selectedLoadout.unlockedRelicSlots})",
                            onClick = { onOpenChampionDetail(selectedChamp.id) },
                            accentColor = AccentGold,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("champion_mastery_button"),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Shield,
                                    contentDescription = null,
                                    tint = AccentGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }

                    if (selectedLoadout.isUnlocked) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                        ) {
                            LorBrassButton(
                                text = "Phiêu lưu",
                                onClick = onOpenAdventures,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .testTag("embark_adventure_button"),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Explore,
                                        contentDescription = "Phiêu lưu",
                                        tint = BgDeep,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                            LorBrassButton(
                                text = "Đấu nhanh",
                                onClick = onQuickBattle,
                                isCyanVariant = true,
                                fontSize = 13.sp,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("quick_battle_button"),
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.SportsKabaddi,
                                        contentDescription = "Đấu nhanh",
                                        tint = BgDeep,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            )
                        }
                    } else {
                        LorBrassButton(
                            text = "Mở khóa tướng (${selectedChamp.unlockFragmentCost} mảnh)",
                            onClick = { onUnlockChampion(selectedChamp.id) },
                            isCyanVariant = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("unlock_champion_button"),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Mở khóa",
                                    tint = BgDeep,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (maxWidth >= 600.dp) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1080.dp)
                ) {
                    topCompactBar()
                    HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = GameSpacing.lg, vertical = GameSpacing.sm),
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.lg),
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(0.46f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                        ) {
                            savedRunBar()
                            Text(
                                text = "Danh sách tướng",
                                style = titleHeadingStyle(13.sp, AccentGold)
                            )
                            rosterStrip()
                        }
                        Column(
                            modifier = Modifier
                                .weight(0.54f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Top
                        ) {
                            championShowcase(true)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 680.dp),
                    contentPadding = PaddingValues(bottom = GameSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    item {
                        topCompactBar()
                        HorizontalDivider(thickness = 1.dp, color = BorderSubtle)
                    }
                    if (uiState.savedRun != null) {
                        item {
                            Box(modifier = Modifier.padding(horizontal = GameSpacing.lg)) {
                                savedRunBar()
                            }
                        }
                    }
                    item {
                        Column(
                            modifier = Modifier.padding(horizontal = GameSpacing.lg),
                            verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                        ) {
                            Text(
                                text = "Danh sách tướng",
                                style = titleHeadingStyle(13.sp, AccentGold)
                            )
                            rosterStrip()
                        }
                    }
                    item {
                        Box(modifier = Modifier.padding(horizontal = GameSpacing.lg)) {
                            championShowcase(false)
                        }
                    }
                }
            }
        }
    }

    if (showGlossary) {
        KeywordGlossaryDialog(onDismiss = { showGlossary = false })
    }
}

@Composable
private fun CompactUtilityPill(
    tint: Color,
    testTag: String,
    onClick: () -> Unit,
    label: String? = null,
    icon: ImageVector? = null,
    contentDescription: String? = null
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .testTag(testTag)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(36.dp),
            shape = CircleShape,
            color = SurfaceElevated,
            border = BorderStroke(1.dp, tint.copy(alpha = 0.55f)),
            shadowElevation = 0.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                } else if (label != null) {
                    Text(
                        text = label,
                        style = statNumericStyle(14.sp, tint)
                    )
                }
            }
        }
    }
}
