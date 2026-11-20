package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.ui.components.ChampionAvatarBadge
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorCarvedTopBar
import com.example.ui.components.LorMetalTone
import com.example.ui.components.LorMetallicPanel
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.regionAccentColor
import com.example.ui.theme.AccentGold
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticHeal
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle
import com.example.ui.theme.titleHeadingStyle
import com.example.ui.theme.uiBodyStyle
import com.example.viewmodel.GameUiState

@Composable
fun AdventureSelectScreen(
    uiState: GameUiState,
    onStartAdventure: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val champ = CardCatalog.getChampion(uiState.selectedChampionId)

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = regionAccentColor(champ.region)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
            ) {
                LorCarvedTopBar(
                    title = "Phiêu lưu thế giới",
                    subtitle = "Xuất quân cùng ${champ.name}",
                    onBack = onBack,
                    backTestTag = "adv_select_back_button",
                    leadingSlot = {
                        ChampionAvatarBadge(
                            championId = champ.id,
                            name = champ.name,
                            region = champ.region,
                            size = 40.dp
                        )
                    }
                )

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(GameSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    items(AdventureCatalog.adventures, key = { it.id }) { adv ->
                        val completed = uiState.legendProfile.completedAdventureIds.contains(adv.id)
                        val accent = regionAccentColor(adv.region)

                        LorMetallicPanel(
                            modifier = Modifier.fillMaxWidth(),
                            tone = if (completed) LorMetalTone.CUSTOM else LorMetalTone.GOLD,
                            customBorderColor = if (completed) SemanticHeal else accent,
                            cornerRadius = GameRadius.panel,
                            borderWidth = 1.dp,
                            contentPadding = PaddingValues(GameSpacing.lg)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(GameRadius.badge),
                                        color = SurfaceElevated,
                                        border = BorderStroke(1.dp, accent.copy(alpha = 0.65f))
                                    ) {
                                        Text(
                                            text = "${adv.difficultyLabel} • ${adv.region.displayName}",
                                            style = statNumericStyle(11.sp, accent),
                                            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs)
                                        )
                                    }
                                    if (completed) {
                                        Surface(
                                            shape = RoundedCornerShape(GameRadius.badge),
                                            color = SemanticHeal.copy(alpha = 0.14f),
                                            border = BorderStroke(1.dp, SemanticHeal.copy(alpha = 0.7f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = GameSpacing.xs),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.CheckCircle,
                                                    contentDescription = "Đã hoàn thành",
                                                    tint = SemanticHeal,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                                Text(
                                                    text = "Đã vượt ải",
                                                    style = uiBodyStyle(11.sp, SemanticHeal)
                                                )
                                            }
                                        }
                                    }
                                }

                                Text(
                                    text = adv.name,
                                    style = titleHeadingStyle(18.sp, AccentGold)
                                )
                                Text(
                                    text = adv.description,
                                    style = uiBodyStyle(13.sp, TextSecondary)
                                )

                                HorizontalDivider(thickness = 1.dp, color = BorderSubtle)

                                Text(
                                    text = "Trùm cuối: ${adv.bossName} • Thưởng: ${adv.rewardXp} XP, ${adv.rewardFragments} Mảnh",
                                    style = statNumericStyle(11.5.sp, TextPrimary)
                                )

                                if (adv.mutators.isNotEmpty()) {
                                    Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                                        adv.mutators.forEach { mut ->
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(GameRadius.control),
                                                color = SurfaceElevated,
                                                border = BorderStroke(1.dp, SemanticDanger.copy(alpha = 0.45f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(GameSpacing.sm),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Warning,
                                                        contentDescription = "Hiệu ứng biến đổi",
                                                        tint = SemanticDanger,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = "${mut.name}: ${mut.description}",
                                                        style = uiBodyStyle(11.5.sp, TextPrimary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                LorBrassButton(
                                    text = "Bắt đầu phiêu lưu",
                                    onClick = { onStartAdventure(adv.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("start_adv_${adv.id}"),
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.PlayArrow,
                                            contentDescription = null,
                                            tint = BgDeep,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
