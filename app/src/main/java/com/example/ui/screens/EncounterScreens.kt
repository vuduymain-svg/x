package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.ContentCut
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardCatalog
import com.example.model.DeckCardEntry
import com.example.model.PowerTemplate
import com.example.model.RunState
import com.example.ui.components.CardTile
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorMetalTone
import com.example.ui.components.LorMetallicPanel
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.LorSecondaryMetalButton
import com.example.ui.components.rarityColor
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticEpic
import com.example.ui.theme.SemanticHeal
import com.example.ui.theme.SurfaceBase
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle
import com.example.ui.theme.titleHeadingStyle
import com.example.ui.theme.uiBodyStyle
import com.example.viewmodel.CardRewardOffer
import com.example.viewmodel.GameUiState
import com.example.viewmodel.RunRewardsSummary

@Composable
fun CardDraftScreen(
    run: RunState,
    offers: List<CardRewardOffer>,
    onPick: (CardRewardOffer) -> Unit,
    onReroll: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onSkip)

    EncounterFrame(
        title = "Chọn bài tiếp viện",
        subtitle = "Mỗi lá bài đều được trang bị sẵn 1 vật phẩm cộng dồn",
        run = run,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= 560.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    offers.forEachIndexed { idx, offer ->
                        CardTile(
                            template = offer.card,
                            effectiveCost = maxOf(0, offer.card.cost - offer.attachedItem.costReduction),
                            bonusPower = offer.attachedItem.bonusPower,
                            bonusHealth = offer.attachedItem.bonusHealth,
                            attachedItems = listOf(offer.attachedItem),
                            testTag = "draft_card_option_$idx",
                            onClick = { onPick(offer) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    itemsIndexed(offers) { idx, offer ->
                        CardTile(
                            template = offer.card,
                            effectiveCost = maxOf(0, offer.card.cost - offer.attachedItem.costReduction),
                            bonusPower = offer.attachedItem.bonusPower,
                            bonusHealth = offer.attachedItem.bonusHealth,
                            attachedItems = listOf(offer.attachedItem),
                            testTag = "draft_card_option_$idx",
                            onClick = { onPick(offer) }
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(GameSpacing.md))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
        ) {
            LorSecondaryMetalButton(
                text = "Đổi lại (${run.rerollsRemaining})",
                onClick = onReroll,
                enabled = run.rerollsRemaining > 0,
                modifier = Modifier
                    .weight(1f)
                    .testTag("reroll_card_draft_button"),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Casino,
                        contentDescription = null,
                        tint = AccentGold,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
            TextButton(
                onClick = onSkip,
                modifier = Modifier.height(48.dp)
            ) {
                Text(
                    text = "Bỏ qua",
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun PowerDraftScreen(
    run: RunState,
    powers: List<PowerTemplate>,
    onPick: (PowerTemplate) -> Unit,
    onReroll: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { /* Must choose a Power to proceed on Power Shrine */ }

    EncounterFrame(
        title = "Đền sức mạnh",
        subtitle = "Chọn 1 sức mạnh nội tại cho phần còn lại của hành trình",
        run = run,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= 560.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    powers.forEach { power ->
                        val accent = rarityColor(power.rarity)
                        LorMetallicPanel(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 160.dp)
                                .testTag("draft_power_${power.id}"),
                            tone = LorMetalTone.CUSTOM,
                            customBorderColor = accent,
                            cornerRadius = GameRadius.card,
                            borderWidth = 1.5.dp,
                            onClick = { onPick(power) },
                            contentPadding = PaddingValues(GameSpacing.lg)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Bolt,
                                        contentDescription = power.name,
                                        tint = accent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = power.rarity.displayName,
                                        fontFamily = PlusJakartaSansFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = accent
                                    )
                                }
                                Text(
                                    text = power.name,
                                    style = titleHeadingStyle(15.sp, accent)
                                )
                                Text(
                                    text = power.description,
                                    style = uiBodyStyle(12.sp, TextPrimary)
                                )
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    items(powers, key = { it.id }) { power ->
                        val accent = rarityColor(power.rarity)
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("draft_power_${power.id}")
                                .clickable { onPick(power) },
                            shape = RoundedCornerShape(GameRadius.card),
                            color = SurfaceBase,
                            border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(GameSpacing.lg),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Bolt,
                                    contentDescription = power.name,
                                    tint = accent,
                                    modifier = Modifier.size(24.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                                ) {
                                    Text(
                                        text = "${power.name} (${power.rarity.displayName})",
                                        style = titleHeadingStyle(15.sp, accent)
                                    )
                                    Text(
                                        text = power.description,
                                        style = uiBodyStyle(12.5.sp, TextPrimary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(GameSpacing.md))
        LorSecondaryMetalButton(
            text = "Đổi lại sức mạnh (${run.rerollsRemaining})",
            onClick = onReroll,
            enabled = run.rerollsRemaining > 0,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("reroll_power_draft_button"),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Casino,
                    contentDescription = null,
                    tint = AccentGold,
                    modifier = Modifier.size(18.dp)
                )
            }
        )
    }
}

@Composable
fun ShopScreen(
    uiState: GameUiState,
    onBuyCard: (Int) -> Unit,
    onSelectItem: (Int?) -> Unit,
    onAttachItemToCard: (String) -> Unit,
    onBuyPower: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onLeave)
    val run = uiState.activeRun ?: return

    EncounterFrame(
        title = "Thương nhân nhạc công",
        subtitle = "Dùng vàng để mua bài, vật phẩm cộng dồn và sức mạnh",
        run = run,
        modifier = modifier
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
        ) {
            uiState.shopPower?.let { powOffer ->
                item {
                    Text(
                        text = "Sức mạnh nổi bật",
                        style = titleHeadingStyle(13.sp, SemanticEpic)
                    )
                    Spacer(modifier = Modifier.height(GameSpacing.xs))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(GameRadius.card),
                        color = SurfaceBase,
                        border = BorderStroke(1.dp, rarityColor(powOffer.power.rarity).copy(alpha = 0.7f)),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(GameSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                            ) {
                                Text(
                                    text = powOffer.power.name,
                                    fontFamily = CinzelFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = AccentGold
                                )
                                Text(
                                    text = powOffer.power.description,
                                    style = uiBodyStyle(12.sp, TextSecondary)
                                )
                            }
                            LorBrassButton(
                                text = if (powOffer.purchased) "Đã sở hữu" else "${powOffer.goldCost} Vàng",
                                onClick = onBuyPower,
                                enabled = !powOffer.purchased && run.gold >= powOffer.goldCost,
                                fontSize = 12.sp,
                                modifier = Modifier.testTag("buy_shop_power_button")
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Vật phẩm cộng dồn (gắn vào bộ bài)",
                    style = titleHeadingStyle(13.sp, AccentIce)
                )
            }
            itemsIndexed(uiState.shopItems) { idx, itemOffer ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GameRadius.card),
                    color = SurfaceBase,
                    border = BorderStroke(1.dp, rarityColor(itemOffer.item.rarity).copy(alpha = 0.7f)),
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier.padding(GameSpacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                        ) {
                            Text(
                                text = itemOffer.item.name,
                                fontFamily = CinzelFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            Text(
                                text = itemOffer.item.description,
                                style = uiBodyStyle(12.sp, TextSecondary)
                            )
                        }
                        LorBrassButton(
                            text = if (itemOffer.purchased) "Đã mua" else "${itemOffer.goldCost} Vàng",
                            onClick = { onSelectItem(idx) },
                            enabled = !itemOffer.purchased && run.gold >= itemOffer.goldCost,
                            isCyanVariant = true,
                            fontSize = 12.sp,
                            modifier = Modifier.testTag("buy_shop_item_$idx")
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Bài đang bán",
                    style = titleHeadingStyle(13.sp, AccentGold)
                )
                Spacer(modifier = Modifier.height(GameSpacing.sm))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth >= 560.dp) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                        ) {
                            uiState.shopCards.forEachIndexed { idx, cardOffer ->
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                                ) {
                                    CardTile(
                                        template = cardOffer.card,
                                        attachedItems = listOf(cardOffer.attachedItem),
                                        playable = !cardOffer.purchased && run.gold >= cardOffer.goldCost
                                    )
                                    LorBrassButton(
                                        text = if (cardOffer.purchased) "Đã mua" else "${cardOffer.goldCost} Vàng",
                                        onClick = { onBuyCard(idx) },
                                        enabled = !cardOffer.purchased && run.gold >= cardOffer.goldCost,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("buy_shop_card_$idx"),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.ShoppingBag,
                                                contentDescription = null,
                                                tint = com.example.ui.theme.BgDeep,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.md)) {
                            uiState.shopCards.forEachIndexed { idx, cardOffer ->
                                Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                                    CardTile(
                                        template = cardOffer.card,
                                        attachedItems = listOf(cardOffer.attachedItem),
                                        playable = !cardOffer.purchased && run.gold >= cardOffer.goldCost
                                    )
                                    LorBrassButton(
                                        text = if (cardOffer.purchased) "Đã mua" else "Mua bài (${cardOffer.goldCost} Vàng)",
                                        onClick = { onBuyCard(idx) },
                                        enabled = !cardOffer.purchased && run.gold >= cardOffer.goldCost,
                                        fontSize = 12.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("buy_shop_card_$idx"),
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.ShoppingBag,
                                                contentDescription = null,
                                                tint = com.example.ui.theme.BgDeep,
                                                modifier = Modifier.size(16.dp)
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

        Spacer(modifier = Modifier.height(GameSpacing.md))
        LorSecondaryMetalButton(
            text = "Tiếp tục hành trình",
            onClick = onLeave,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("leave_shop_button")
        )
    }

    if (uiState.pendingShopItemIndex != null) {
        DeckCardTargetDialog(
            title = "Gắn vật phẩm vào bài",
            deck = run.runDeck,
            onSelectCard = onAttachItemToCard,
            onDismiss = { onSelectItem(null) }
        )
    }
}

@Composable
fun HealerScreen(
    run: RunState,
    onHeal: () -> Unit,
    onCutCard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { /* Choose Heal or Cut Card */ }
    var showCutDialog by remember { mutableStateOf(false) }

    EncounterFrame(
        title = "Thánh đường trị liệu",
        subtitle = "Hồi máu Nhà Chính hoặc loại bỏ 1 lá bài yếu khỏi bộ bài",
        run = run,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= 560.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HealerOptionCard(
                        icon = Icons.Outlined.FavoriteBorder,
                        iconDesc = "Hồi máu",
                        accent = SemanticHeal,
                        title = "Hồi 40% máu Nhà Chính",
                        subtitle = "Hồi tối đa +${maxOf(6, (run.maxNexusHp * 0.4f).toInt())} Máu (Hiện tại: ${run.currentNexusHp}/${run.maxNexusHp})",
                        testTag = "healer_restore_button",
                        onClick = onHeal,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 140.dp)
                    )
                    HealerOptionCard(
                        icon = Icons.Outlined.ContentCut,
                        iconDesc = "Loại bỏ bài",
                        accent = SemanticDanger,
                        title = "Cắt 1 lá khỏi bộ bài",
                        subtitle = "Tăng độ ổn định khi rút bài bằng cách bỏ 1 bản sao.",
                        testTag = "healer_cut_button",
                        onClick = { showCutDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 140.dp)
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    HealerOptionCard(
                        icon = Icons.Outlined.FavoriteBorder,
                        iconDesc = "Hồi máu",
                        accent = SemanticHeal,
                        title = "Hồi 40% máu Nhà Chính",
                        subtitle = "Hồi tối đa +${maxOf(6, (run.maxNexusHp * 0.4f).toInt())} Máu (Hiện tại: ${run.currentNexusHp}/${run.maxNexusHp})",
                        testTag = "healer_restore_button",
                        onClick = onHeal,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HealerOptionCard(
                        icon = Icons.Outlined.ContentCut,
                        iconDesc = "Loại bỏ bài",
                        accent = SemanticDanger,
                        title = "Cắt 1 lá khỏi bộ bài",
                        subtitle = "Tăng độ ổn định khi rút bài bằng cách bỏ 1 bản sao.",
                        testTag = "healer_cut_button",
                        onClick = { showCutDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }

    if (showCutDialog) {
        DeckCardTargetDialog(
            title = "Chọn lá bài để loại bỏ",
            deck = run.runDeck,
            onSelectCard = {
                showCutDialog = false
                onCutCard(it)
            },
            onDismiss = { showCutDialog = false }
        )
    }
}

@Composable
private fun HealerOptionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconDesc: String,
    accent: androidx.compose.ui.graphics.Color,
    title: String,
    subtitle: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(GameRadius.card),
        color = SurfaceBase,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.7f)),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(GameSpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = iconDesc,
                tint = accent,
                modifier = Modifier.size(28.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                Text(
                    text = title,
                    style = titleHeadingStyle(15.sp, accent)
                )
                Text(
                    text = subtitle,
                    style = uiBodyStyle(12.sp, TextSecondary)
                )
            }
        }
    }
}

@Composable
fun ReinforcementScreen(
    run: RunState,
    packages: List<CardCatalog.ReinforcementPackage>,
    onPick: (CardCatalog.ReinforcementPackage) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler { packages.firstOrNull()?.let(onPick) }

    EncounterFrame(
        title = "Tướng hỗ trợ tiếp viện",
        subtitle = "Chiêu mộ 1 gói Tướng phụ vào bộ bài của bạn",
        run = run,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= 560.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    packages.forEach { pkg ->
                        ReinforcementCard(
                            pkg = pkg,
                            onClick = { onPick(pkg) },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 160.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    items(packages, key = { it.championId }) { pkg ->
                        ReinforcementCard(
                            pkg = pkg,
                            onClick = { onPick(pkg) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReinforcementCard(
    pkg: CardCatalog.ReinforcementPackage,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("reinf_pkg_${pkg.championId}")
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(GameRadius.card),
        color = SurfaceBase,
        border = BorderStroke(1.dp, AccentIce.copy(alpha = 0.65f)),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(GameSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                Icon(
                    imageVector = Icons.Outlined.GroupAdd,
                    contentDescription = null,
                    tint = AccentIce,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = pkg.title,
                    style = titleHeadingStyle(15.sp, AccentGold)
                )
            }
            Text(pkg.subtitle, style = uiBodyStyle(12.sp, TextSecondary))
            Text(
                text = "Bao gồm: ${pkg.cardIds.joinToString { CardCatalog.getCard(it).name }}",
                style = uiBodyStyle(11.5.sp, TextPrimary)
            )
        }
    }
}

@Composable
fun ItemChestScreen(
    uiState: GameUiState,
    onAttachToCard: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val run = uiState.activeRun ?: return
    val item = uiState.chestOfferedItem ?: return
    BackHandler { run.runDeck.firstOrNull()?.let { onAttachToCard(it.cardId) } }

    EncounterFrame(
        title = "Kho vật phẩm",
        subtitle = "Chọn 1 lá bài trong bộ bài để gắn vĩnh viễn vật phẩm này",
        run = run,
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(GameRadius.card),
            color = SurfaceElevated,
            border = BorderStroke(1.dp, rarityColor(item.rarity).copy(alpha = 0.7f)),
            shadowElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(GameSpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Inventory2,
                    contentDescription = item.name,
                    tint = rarityColor(item.rarity),
                    modifier = Modifier.size(26.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                    Text(
                        text = "${item.name} (${item.rarity.displayName})",
                        style = titleHeadingStyle(15.sp, AccentGold)
                    )
                    Text(
                        text = item.description,
                        style = uiBodyStyle(12.5.sp, TextPrimary)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(GameSpacing.md))
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
        ) {
            items(run.runDeck, key = { it.cardId }) { entry ->
                val card = CardCatalog.getCard(entry.cardId)
                CardTile(
                    template = card,
                    attachedItems = entry.attachedItemIds.map { CardCatalog.getItem(it) },
                    testTag = "chest_target_${entry.cardId}",
                    onClick = { onAttachToCard(entry.cardId) }
                )
            }
        }
    }
}

@Composable
fun NarrativeEventScreen(
    uiState: GameUiState,
    onChooseOption: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val run = uiState.activeRun ?: return
    val event = uiState.narrativeEvent ?: return
    BackHandler { /* Mandatory encounter choice: player must explicitly select an option */ }

    EncounterFrame(
        title = event.title,
        subtitle = event.flavor,
        run = run,
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.weight(1f)) {
            if (maxWidth >= 560.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    event.options.forEach { opt ->
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 140.dp)
                                .minimumInteractiveComponentSize()
                                .testTag("event_opt_${opt.id}")
                                .clickable { onChooseOption(opt.id) },
                            shape = RoundedCornerShape(GameRadius.card),
                            color = SurfaceBase,
                            border = BorderStroke(1.dp, BorderGoldMuted),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(GameSpacing.lg),
                                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                            ) {
                                Text(opt.title, style = titleHeadingStyle(15.sp, AccentGold))
                                Text(opt.description, style = uiBodyStyle(12.5.sp, TextPrimary))
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    event.options.forEach { opt ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("event_opt_${opt.id}")
                                .clickable { onChooseOption(opt.id) },
                            shape = RoundedCornerShape(GameRadius.card),
                            color = SurfaceBase,
                            border = BorderStroke(1.dp, BorderGoldMuted),
                            shadowElevation = 0.dp
                        ) {
                            Column(
                                modifier = Modifier.padding(GameSpacing.lg),
                                verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                            ) {
                                Text(opt.title, style = titleHeadingStyle(15.sp, AccentGold))
                                Text(opt.description, style = uiBodyStyle(12.5.sp, TextPrimary))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RunSummaryScreen(
    summary: RunRewardsSummary,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onDone)

    LorScreenBackdrop(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(GameSpacing.lg),
            contentAlignment = Alignment.Center
        ) {
            LorMetallicPanel(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth(),
                tone = if (summary.isVictory) LorMetalTone.GOLD else LorMetalTone.CUSTOM,
                customBorderColor = SemanticDanger,
                cornerRadius = GameRadius.panel,
                borderWidth = 1.dp,
                contentPadding = PaddingValues(GameSpacing.lg)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EmojiEvents,
                        contentDescription = "Hoàn thành hành trình",
                        tint = if (summary.isVictory) AccentGold else SemanticDanger,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = if (summary.isVictory) "Chinh phục thành công" else "Kết thúc hành trình",
                        style = titleHeadingStyle(20.sp, if (summary.isVictory) AccentGold else SemanticDanger)
                    )
                    Text(
                        text = "${summary.championName} • ${summary.adventureName}",
                        style = uiBodyStyle(14.sp, TextPrimary)
                    )

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(GameRadius.card),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 0.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(GameSpacing.md),
                            verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                        ) {
                            SummaryRow("Trận thắng", "${summary.battlesWon}")
                            SummaryRow("XP tướng nhận được", "+${summary.xpGained} XP")
                            if (summary.leveledUpTo != null) {
                                SummaryRow("Tăng cấp tướng", "Cấp ${summary.leveledUpTo}")
                            }
                            SummaryRow("Mảnh tướng", "+${summary.fragmentsGained}")
                            SummaryRow("Mảnh vạn năng", "+${summary.wildFragmentsGained}")
                            SummaryRow("Điểm Huyền thoại", "+${summary.legendPointsGained}")
                        }
                    }

                    LorBrassButton(
                        text = "Quay về sảnh",
                        onClick = onDone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("run_summary_done_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = uiBodyStyle(12.5.sp, TextSecondary))
        Text(value, style = statNumericStyle(12.5.sp, AccentIce))
    }
}

@Composable
private fun EncounterFrame(
    title: String,
    subtitle: String,
    run: RunState,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    LorScreenBackdrop(modifier = modifier) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
                    .padding(horizontal = GameSpacing.lg, vertical = GameSpacing.md)
            ) {
                LorMetallicPanel(
                    modifier = Modifier.fillMaxWidth(),
                    tone = LorMetalTone.GOLD,
                    cornerRadius = GameRadius.card,
                    borderWidth = 1.dp,
                    contentPadding = PaddingValues(horizontal = GameSpacing.md, vertical = GameSpacing.md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = title,
                                style = titleHeadingStyle(16.sp, AccentGold),
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Máu ${run.currentNexusHp}/${run.maxNexusHp} • ${run.gold} Vàng",
                                style = statNumericStyle(12.sp, AccentIce)
                            )
                        }
                        Text(subtitle, style = uiBodyStyle(12.sp, TextSecondary))
                    }
                }
                Spacer(modifier = Modifier.height(GameSpacing.md))
                content()
            }
        }
    }
}

@Composable
private fun DeckCardTargetDialog(
    title: String,
    deck: List<DeckCardEntry>,
    onSelectCard: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        title = {
            Text(title, style = titleHeadingStyle(17.sp, AccentGold))
        },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                items(deck, key = { it.cardId }) { entry ->
                    val card = CardCatalog.getCard(entry.cardId)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .clickable { onSelectCard(entry.cardId) },
                        shape = RoundedCornerShape(GameRadius.control),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, BorderSubtle),
                        shadowElevation = 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(GameSpacing.md),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = "${entry.copies}x ${card.name}",
                                    style = titleHeadingStyle(13.sp, TextPrimary)
                                )
                                Text(
                                    text = card.description,
                                    style = uiBodyStyle(11.5.sp, TextSecondary)
                                )
                            }
                            Text(
                                text = "${card.cost} NL",
                                style = statNumericStyle(11.5.sp, AccentIce)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy", style = uiBodyStyle(13.sp, TextSecondary))
            }
        }
    )
}
