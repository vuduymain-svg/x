package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardCatalog
import com.example.model.CardTemplate
import com.example.model.ChampionLoadout
import com.example.model.ChampionTemplate
import com.example.model.DeckCardEntry
import com.example.model.HandCardInstance
import com.example.model.ItemCategory
import com.example.model.ItemTemplate
import com.example.ui.components.ChampionAvatarBadge
import com.example.ui.components.HandCardView
import com.example.ui.components.LorBrassButton
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.LorSecondaryMetalButton
import com.example.ui.components.RelicSlotCard
import com.example.ui.components.cardPortraitRes
import com.example.ui.components.championPortraitRes
import com.example.ui.components.fantasyHeadingStyle
import com.example.ui.components.interBodyStyle
import com.example.ui.components.rarityColor
import com.example.ui.components.regionAccentColor
import com.example.ui.components.tabularStatStyle
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.DarkButtonInk
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.EpicViolet
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.HextechGoldDeep
import com.example.ui.theme.ManaBlue
import com.example.ui.theme.MutedSilver
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.ParchmentWhite
import com.example.ui.theme.SlateBorder
import com.example.viewmodel.GameUiState

private enum class ChampionSection(val label: String) {
    OVERVIEW("TỔNG QUAN"),
    CONSTELLATION("CHÒM SAO"),
    STARTER_DECK("BỘ BÀI KHỞI ĐẦU"),
    RELICS("CỔ VẬT")
}

private val HexagonShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.5f, 0f)
    lineTo(w * 0.933f, h * 0.25f)
    lineTo(w * 0.933f, h * 0.75f)
    lineTo(w * 0.5f, h)
    lineTo(w * 0.067f, h * 0.75f)
    lineTo(w * 0.067f, h * 0.25f)
    close()
}

private fun buildPreviewHandCard(
    card: CardTemplate,
    attachedItems: List<ItemTemplate>
): HandCardInstance {
    val costReduction = attachedItems.sumOf { it.costReduction }
    val bonusPower = attachedItems.sumOf { it.bonusPower }
    val bonusHealth = attachedItems.sumOf { it.bonusHealth }
    val extraKeywords = attachedItems.mapNotNull { it.grantedKeyword }.toSet()
    return HandCardInstance(
        instanceId = "preview_${card.id}",
        template = card,
        attachedItems = attachedItems,
        effectiveCost = maxOf(0, card.cost - costReduction),
        bonusPower = bonusPower,
        bonusHealth = bonusHealth,
        extraKeywords = extraKeywords
    )
}

@Composable
fun ChampionDetailScreen(
    championId: String,
    uiState: GameUiState,
    onUpgradeStar: (String, Int) -> Unit,
    onUnlockConstellation: (String, String) -> Unit,
    onEquipRelic: (String, Int, String?) -> Unit,
    onCosmicForge: (String) -> Unit,
    onMaxChampionForTesting: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val champion = CardCatalog.findChampionOrNull(championId)
        ?: CardCatalog.champions.firstOrNull()
        ?: return
    val loadout = uiState.championLoadouts[champion.id]
        ?: uiState.championLoadouts[championId]
        ?: return
    val regionAccent = regionAccentColor(champion.region)
    val splashRes = championPortraitRes(champion.id)

    var selectedSection by remember { mutableStateOf(ChampionSection.CONSTELLATION) }
    var constellationSubTab by remember { mutableIntStateOf(0) } // 0 = STAR POWERS, 1 = BONUS STARS
    var relicPickerSlot by remember { mutableStateOf<Int?>(null) }
    var selectedDeckCardId by remember(champion.id) { mutableStateOf(champion.cardId) }
    var compactPreviewEntry by remember { mutableStateOf<DeckCardEntry?>(null) }

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = regionAccent
    ) {
        if (splashRes != null) {
            Image(
                painter = painterResource(id = splashRes),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.24f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x9E09101E), Color(0x7809101E), Color(0xEB060B15))
                        )
                    )
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp

            if (isWide) {
                // TRUE LoR 3-COLUMN LAYOUT (Full Screen Height — No Currency Top Bar):
                // Left Column: Avatar + Level + 6 Stars + Back + Vertical Menu
                // Center Column: Minimalist Dark Star Powers Frame / Slim 34.dp Starter Deck Strips
                // Right Column: Enlarged Active Card + 3 Vertical Hexagonal Relic Sockets
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1140.dp)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ChampionLeftRailColumn(
                        champion = champion,
                        loadout = loadout,
                        selectedSection = selectedSection,
                        onSelectSection = { section ->
                            selectedSection = section
                            if (section != ChampionSection.STARTER_DECK) {
                                selectedDeckCardId = champion.cardId
                            }
                        },
                        onBack = onBack,
                        onMaxForTesting = { onMaxChampionForTesting(championId) },
                        modifier = Modifier
                            .width(214.dp)
                            .fillMaxHeight()
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        ChampionSectionContent(
                            section = selectedSection,
                            constellationSubTab = constellationSubTab,
                            onConstellationSubTabChange = { constellationSubTab = it },
                            champion = champion,
                            loadout = loadout,
                            selectedDeckCardId = selectedDeckCardId,
                            onUpgradeStar = { onUpgradeStar(championId, it) },
                            onUnlockConstellation = { onUnlockConstellation(championId, it) },
                            onOpenRelicPicker = { relicPickerSlot = it },
                            onCosmicForge = { onCosmicForge(championId) },
                            onSelectDeckEntry = { entry -> selectedDeckCardId = entry.cardId }
                        )
                    }

                    val activeDeckEntry = if (selectedSection == ChampionSection.STARTER_DECK) {
                        champion.baseDeck.find { it.cardId == selectedDeckCardId }
                    } else {
                        null
                    }
                    val showcaseCard = remember(activeDeckEntry, champion.cardId) {
                        CardCatalog.getCard(activeDeckEntry?.cardId ?: champion.cardId)
                    }
                    val showcaseItems = remember(activeDeckEntry) {
                        activeDeckEntry?.attachedItemIds?.mapNotNull { CardCatalog.findItemOrNull(it) }.orEmpty()
                    }

                    ChampionRightCardShowcase(
                        card = showcaseCard,
                        attachedItems = showcaseItems,
                        copies = activeDeckEntry?.copies,
                        loadout = loadout,
                        onRelicSlotClick = { relicPickerSlot = it },
                        modifier = Modifier
                            .width(242.dp)
                            .fillMaxHeight()
                    )
                }
            } else {
                // COMPACT PORTRAIT FALLBACK (No Currency Bar)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter)
                ) {
                    CompactChampionIdentityBar(
                        champion = champion,
                        loadout = loadout,
                        onBack = onBack,
                        onMaxForTesting = { onMaxChampionForTesting(championId) }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ChampionSection.entries.forEach { section ->
                            val active = selectedSection == section
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag("champ_section_${section.name.lowercase()}")
                                    .clickable { selectedSection = section }
                                    .padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = when (section) {
                                        ChampionSection.CONSTELLATION -> "CHÒM SAO"
                                        ChampionSection.STARTER_DECK -> "BỘ BÀI"
                                        else -> section.label
                                    },
                                    style = fantasyHeadingStyle(
                                        fontSize = 11.sp,
                                        color = if (active) HextechGoldBright else MutedSilver
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(5.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (active) 2.5.dp else 1.dp)
                                        .background(if (active) HextechGoldBright else SlateBorder.copy(alpha = 0.4f))
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        ChampionSectionContent(
                            section = selectedSection,
                            constellationSubTab = constellationSubTab,
                            onConstellationSubTabChange = { constellationSubTab = it },
                            champion = champion,
                            loadout = loadout,
                            selectedDeckCardId = null,
                            onUpgradeStar = { onUpgradeStar(championId, it) },
                            onUnlockConstellation = { onUnlockConstellation(championId, it) },
                            onOpenRelicPicker = { relicPickerSlot = it },
                            onCosmicForge = { onCosmicForge(championId) },
                            onSelectDeckEntry = { entry -> compactPreviewEntry = entry }
                        )
                    }
                }
            }
        }
    }

    compactPreviewEntry?.let { entry ->
        val card = CardCatalog.getCard(entry.cardId)
        val items = entry.attachedItemIds.mapNotNull { CardCatalog.findItemOrNull(it) }
        val handCard = remember(card, items) { buildPreviewHandCard(card, items) }
        AlertDialog(
            onDismissRequest = { compactPreviewEntry = null },
            containerColor = ObsidianSurface,
            title = null,
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HandCardView(
                        handCard = handCard,
                        effectiveCost = handCard.effectiveCost,
                        playable = true,
                        isFocused = false,
                        cardWidth = 176.dp,
                        cardHeight = 256.dp,
                        descFontSize = 12.sp,
                        statFontSize = 15.sp,
                        isShowcasePreview = true,
                        onClick = {}
                    )
                    if (items.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items.forEach { item ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = rarityColor(item.rarity).copy(alpha = 0.16f),
                                    border = BorderStroke(0.9.dp, rarityColor(item.rarity).copy(alpha = 0.7f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "${item.name} • ${item.description}",
                                        style = interBodyStyle(11.sp, ParchmentWhite),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { compactPreviewEntry = null }) {
                    Text("Đóng", color = HextechGoldBright)
                }
            }
        )
    }

    relicPickerSlot?.let { slot ->
        AlertDialog(
            onDismissRequest = { relicPickerSlot = null },
            containerColor = ObsidianSurface,
            title = {
                Text("Trang Bị Cổ Vật — Ô ${slot + 1}", style = fantasyHeadingStyle(16.sp, HextechGoldBright))
            },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedButton(
                            onClick = {
                                onEquipRelic(championId, slot, null)
                                relicPickerSlot = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Tháo Cổ Vật", color = MutedSilver)
                        }
                    }
                    items(CardCatalog.relics, key = { it.id }) { relic ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .clickable {
                                    onEquipRelic(championId, slot, relic.id)
                                    relicPickerSlot = null
                                },
                            shape = RoundedCornerShape(10.dp),
                            color = ObsidianCard,
                            border = BorderStroke(1.dp, rarityColor(relic.rarity))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "${relic.name} (${relic.rarity.displayName})",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = rarityColor(relic.rarity)
                                )
                                Text(
                                    text = relic.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MutedSilver
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { relicPickerSlot = null }) {
                    Text("Hủy", color = HextechGoldBright)
                }
            }
        )
    }
}

@Composable
private fun ChampionLeftRailColumn(
    champion: ChampionTemplate,
    loadout: ChampionLoadout,
    selectedSection: ChampionSection,
    onSelectSection: (ChampionSection) -> Unit,
    onBack: () -> Unit,
    onMaxForTesting: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Champion Identity Header inside Top of Left Column (No Currency Bar)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                modifier = Modifier
                    .size(48.dp)
                    .padding(5.dp)
                    .testTag("champ_detail_back_button")
                    .clickable(onClick = onBack),
                shape = CircleShape,
                color = Color(0xCC0D1626),
                border = BorderStroke(1.4.dp, HextechGold.copy(alpha = 0.85f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Quay lại",
                        tint = ParchmentWhite,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            Box(contentAlignment = Alignment.BottomCenter) {
                ChampionAvatarBadge(
                    championId = champion.id,
                    name = champion.name,
                    region = champion.region,
                    size = 46.dp
                )
                Surface(
                    modifier = Modifier.offset(y = 5.dp),
                    shape = CircleShape,
                    color = ObsidianBg,
                    border = BorderStroke(1.2.dp, HextechGold)
                ) {
                    Text(
                        text = "${loadout.championLevel}",
                        style = tabularStatStyle(9.sp, ParchmentWhite),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 0.5.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = champion.name.uppercase(),
                    style = fantasyHeadingStyle(14.sp, HextechGoldBright),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = champion.title,
                    style = interBodyStyle(10.sp, MutedSilver),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    for (star in 1..6) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = if (star <= loadout.starLevel) Color(0xFFFF7A45) else Color(0xFF474E5E),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        LorSecondaryMetalButton(
            text = "Tối Đa 30★",
            onClick = onMaxForTesting,
            accentColor = EmeraldHeal,
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .testTag("max_champ_test_button")
        )

        HorizontalDivider(
            thickness = 0.8.dp,
            color = HextechGoldDeep.copy(alpha = 0.45f),
            modifier = Modifier.padding(vertical = 2.dp)
        )

        // Vertical LoR Side Menu
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            ChampionSection.entries.forEach { section ->
                val active = selectedSection == section
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("champ_section_${section.name.lowercase()}")
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (active) {
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0x44C89B3C), Color.Transparent)
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Transparent)
                                )
                            }
                        )
                        .clickable { onSelectSection(section) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) HextechGoldBright else Color.Transparent)
                            .border(
                                1.1.dp,
                                if (active) HextechGoldBright else HextechGoldDeep.copy(alpha = 0.55f),
                                RoundedCornerShape(2.dp)
                            )
                    )
                    Text(
                        text = section.label,
                        style = fantasyHeadingStyle(
                            fontSize = 12.sp,
                            color = if (active) ParchmentWhite else Color(0xFFB3A17B)
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactChampionIdentityBar(
    champion: ChampionTemplate,
    loadout: ChampionLoadout,
    onBack: () -> Unit,
    onMaxForTesting: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .size(48.dp)
                .padding(4.dp)
                .testTag("champ_detail_back_button")
                .clickable(onClick = onBack),
            shape = CircleShape,
            color = Color(0xCC0D1626),
            border = BorderStroke(1.4.dp, HextechGold.copy(alpha = 0.8f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Quay lại",
                    tint = ParchmentWhite,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Box(contentAlignment = Alignment.BottomCenter) {
            ChampionAvatarBadge(
                championId = champion.id,
                name = champion.name,
                region = champion.region,
                size = 48.dp
            )
            Surface(
                modifier = Modifier.offset(y = 4.dp),
                shape = CircleShape,
                color = ObsidianBg,
                border = BorderStroke(1.2.dp, HextechGold)
            ) {
                Text(
                    text = "${loadout.championLevel}",
                    style = tabularStatStyle(9.5.sp, ParchmentWhite),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 0.5.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = champion.name.uppercase(),
                style = fantasyHeadingStyle(15.5.sp, HextechGoldBright),
                maxLines = 1
            )
            Text(
                text = champion.title.uppercase(),
                style = interBodyStyle(10.sp, MutedSilver),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                for (star in 1..6) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = null,
                        tint = if (star <= loadout.starLevel) Color(0xFFFF7A45) else Color(0xFF474E5E),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        LorSecondaryMetalButton(
            text = "Tối Đa 30★",
            onClick = onMaxForTesting,
            accentColor = EmeraldHeal,
            modifier = Modifier
                .height(36.dp)
                .testTag("max_champ_test_button")
        )
    }
}

@Composable
private fun ChampionSectionContent(
    section: ChampionSection,
    constellationSubTab: Int,
    onConstellationSubTabChange: (Int) -> Unit,
    champion: ChampionTemplate,
    loadout: ChampionLoadout,
    selectedDeckCardId: String?,
    onUpgradeStar: (Int) -> Unit,
    onUnlockConstellation: (String) -> Unit,
    onOpenRelicPicker: (Int) -> Unit,
    onCosmicForge: () -> Unit,
    onSelectDeckEntry: (DeckCardEntry) -> Unit
) {
    when (section) {
        ChampionSection.OVERVIEW -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = champion.archetypeSummary,
                    style = interBodyStyle(13.sp, ParchmentWhite)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xC209101E), RoundedCornerShape(8.dp))
                        .border(0.8.dp, Color(0x38C89B3C), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactStat("MÁU NHÀ CHÍNH", "${loadout.maxNexusHp}", ParchmentWhite)
                    CompactStat("HỒI / THẮNG", "+${loadout.victoryHeal}", EmeraldHeal)
                    CompactStat("VÀNG", "${loadout.startingGold}", HextechGoldBright)
                    CompactStat("ĐỔI LẠI", "${loadout.startingRerolls}", ArcaneCyan)
                }

                val odds = loadout.rarityOdds
                val pct = { v: Double -> String.format(java.util.Locale.US, "%.0f%%", v * 100.0) }
                Text(
                    text = "Tỉ Lệ Chọn — Thường ${pct(odds.common)} • Hiếm ${pct(odds.rare)} • Sử Thi ${pct(odds.epic)} • Huyền Thoại ${pct(odds.legendary)}",
                    style = interBodyStyle(11.sp, MutedSilver)
                )

                val unlockedPowers = champion.starPowers.filter { it.starLevel <= loadout.starLevel }
                if (unlockedPowers.isNotEmpty()) {
                    Text(
                        text = "SỨC MẠNH SAO ĐANG KÍCH HOẠT",
                        style = fantasyHeadingStyle(12.sp, HextechGoldBright),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xBA09101E),
                        border = BorderStroke(0.9.dp, Color(0x45C89B3C))
                    ) {
                        Column {
                            unlockedPowers.forEach { spec ->
                                StarPowerFlatRow(
                                    title = "${spec.starLevel}★ ${spec.title}",
                                    description = spec.description,
                                    unlocked = true,
                                    actionLabel = null,
                                    costText = null,
                                    actionTestTag = null,
                                    onAction = null
                                )
                            }
                        }
                    }
                }
            }
        }

        ChampionSection.CONSTELLATION -> {
            // Minimalist Semi-Transparent Dark Frame with 2 Gold-Underlined Tabs: STAR POWERS / BONUS STARS
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xBD09101E),
                border = BorderStroke(1.dp, Color(0x48C89B3C))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        listOf("STAR POWERS", "BONUS STARS").forEachIndexed { idx, label ->
                            val selected = constellationSubTab == idx
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .minimumInteractiveComponentSize()
                                    .testTag(if (idx == 0) "tab_star_powers" else "tab_bonus_stars")
                                    .clickable { onConstellationSubTabChange(idx) }
                                    .padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = label,
                                    style = fantasyHeadingStyle(
                                        fontSize = 11.5.sp,
                                        color = if (selected) HextechGoldBright else Color(0xFF9EA6B8)
                                    ),
                                    maxLines = 1
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.86f)
                                        .height(if (selected) 2.5.dp else 1.dp)
                                        .background(if (selected) HextechGoldBright else Color(0x338C9BB0))
                                )
                            }
                        }
                    }

                    HorizontalDivider(thickness = 0.7.dp, color = SlateBorder.copy(alpha = 0.45f))

                    if (constellationSubTab == 0) {
                        val nextStarLevel = champion.nextStarPower(loadout.starLevel)?.starLevel
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(champion.starPowers, key = { it.starLevel }) { spec ->
                                val unlocked = loadout.starLevel >= spec.starLevel
                                val isNext = loadout.isUnlocked && nextStarLevel == spec.starLevel
                                val costText = if (!unlocked) {
                                    buildString {
                                        append("Yêu cầu: ${spec.fragmentCost} Mảnh")
                                        if (spec.starCrystalCost > 0) append(" • ${spec.starCrystalCost} Tinh Thể Sao")
                                        if (spec.novaCrystalCost > 0) append(" • ${spec.novaCrystalCost} Tinh Thể Nova")
                                    }
                                } else null

                                StarPowerFlatRow(
                                    title = "${spec.starLevel}★ ${spec.title}",
                                    description = spec.description,
                                    unlocked = unlocked,
                                    actionLabel = if (isNext) "Nâng Cấp" else null,
                                    costText = costText,
                                    actionTestTag = if (isNext) "upgrade_star_${spec.starLevel}" else null,
                                    onAction = if (isNext) { { onUpgradeStar(spec.starLevel) } } else null
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(vertical = 2.dp)
                        ) {
                            items(champion.constellationNodes, key = { it.id }) { node ->
                                val unlocked = loadout.unlockedConstellationNodes.contains(node.id)
                                StarPowerFlatRow(
                                    title = node.title,
                                    description = node.description,
                                    unlocked = unlocked,
                                    actionLabel = if (!unlocked) "Mở Khóa" else null,
                                    costText = if (!unlocked) "Yêu cầu: ${node.fragmentCost} Mảnh • ${node.gemstoneCost} Đá Quý" else null,
                                    actionTestTag = if (!unlocked) "unlock_node_${node.id}" else null,
                                    onAction = if (!unlocked) { { onUnlockConstellation(node.id) } } else null
                                )
                            }
                        }
                    }
                }
            }
        }

        ChampionSection.STARTER_DECK -> {
            Column(modifier = Modifier.fillMaxSize()) {
                val totalCards = remember(champion.baseDeck) { champion.baseDeck.sumOf { it.copies } }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BỘ BÀI KHỞI ĐẦU",
                        style = fantasyHeadingStyle(12.5.sp, HextechGoldBright)
                    )
                    Text(
                        text = "$totalCards LÁ",
                        style = tabularStatStyle(11.sp, MutedSilver)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    contentPadding = PaddingValues(bottom = 6.dp)
                ) {
                    items(champion.baseDeck, key = { it.cardId }) { entry ->
                        val card = CardCatalog.getCard(entry.cardId)
                        SlimDeckCardStrip(
                            entry = entry,
                            card = card,
                            isSelected = selectedDeckCardId == entry.cardId,
                            onClick = { onSelectDeckEntry(entry) }
                        )
                    }
                }
            }
        }

        ChampionSection.RELICS -> {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { idx ->
                    RelicSlotCard(
                        slotIndex = idx,
                        relicId = loadout.equippedRelics.getOrNull(idx),
                        unlocked = idx < loadout.unlockedRelicSlots,
                        isCosmicForged = idx < loadout.cosmicForgedSlots,
                        onClick = { onOpenRelicPicker(idx) }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                LorBrassButton(
                    text = "RÈN VŨ TRỤ Ô SỬ THI (15 ĐÁ QUÝ)",
                    onClick = onCosmicForge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cosmic_forge_button"),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Rèn Vũ Trụ",
                            tint = DarkButtonInk,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun StarPowerFlatRow(
    title: String,
    description: String,
    unlocked: Boolean,
    actionLabel: String?,
    costText: String?,
    actionTestTag: String?,
    onAction: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = if (unlocked) {
                                listOf(ManaBlue.copy(alpha = 0.55f), Color(0xFF0E1B30))
                            } else {
                                listOf(Color(0xFF232C3D), Color(0xFF0B111C))
                            }
                        )
                    )
                    .border(
                        1.5.dp,
                        if (unlocked) HextechGoldBright else SlateBorder.copy(alpha = 0.75f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (unlocked) Icons.Default.Star else Icons.Default.Lock,
                    contentDescription = title,
                    tint = if (unlocked) HextechGoldBright else MutedSilver.copy(alpha = 0.75f),
                    modifier = Modifier.size(17.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = fantasyHeadingStyle(
                        fontSize = 12.sp,
                        color = if (unlocked) HextechGoldBright else ParchmentWhite
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = description,
                    style = interBodyStyle(11.5.sp, if (unlocked) ParchmentWhite else MutedSilver),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (costText != null) {
                    Text(
                        text = costText,
                        style = tabularStatStyle(10.sp, ArcaneCyan)
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Surface(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .then(if (actionTestTag != null) Modifier.testTag(actionTestTag) else Modifier)
                        .clickable(onClick = onAction),
                    shape = CircleShape,
                    color = Color(0xFF152946),
                    border = BorderStroke(1.3.dp, ArcaneCyan)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = actionLabel,
                            style = fantasyHeadingStyle(10.5.sp, ArcaneCyan)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = actionLabel,
                            tint = ArcaneCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.6.dp, color = SlateBorder.copy(alpha = 0.35f))
    }
}

/**
 * Slim ~34.dp Starter Deck Card Strip:
 * Glowing blue Mana gem + Card Name over horizontal cropped art + Item icons + xN copy badge.
 */
@Composable
private fun SlimDeckCardStrip(
    entry: DeckCardEntry,
    card: CardTemplate,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val artRes = cardPortraitRes(card.id)
    val attachedItems = remember(entry.attachedItemIds) {
        entry.attachedItemIds.mapNotNull { CardCatalog.findItemOrNull(it) }
    }
    val effectiveCost = remember(card.cost, attachedItems) {
        maxOf(0, card.cost - attachedItems.sumOf { it.costReduction })
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .testTag("deck_strip_${entry.cardId}")
            .clip(RoundedCornerShape(5.dp))
            .background(Color(0xDE10192B))
            .border(
                width = if (isSelected) 1.5.dp else 0.9.dp,
                color = when {
                    isSelected -> HextechGoldBright
                    card.isChampion -> HextechGold.copy(alpha = 0.85f)
                    else -> SlateBorder.copy(alpha = 0.75f)
                },
                shape = RoundedCornerShape(5.dp)
            )
            .clickable(onClick = onClick)
    ) {
        if (artRes != null) {
            Image(
                painter = painterResource(id = artRes),
                contentDescription = card.name,
                contentScale = ContentScale.Crop,
                alpha = 0.72f,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.58f)
                    .align(Alignment.CenterEnd)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color(0xFF0E1627), Color(0xE80E1627), Color(0x280E1627))
                        )
                    )
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(HextechGoldBright)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Glowing Blue Mana Gem
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFFCCF5FF), ManaBlue, Color(0xFF0A4A8A))
                        )
                    )
                    .border(1.1.dp, Color(0xFFE0F7FF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$effectiveCost",
                    style = tabularStatStyle(11.sp, ParchmentWhite)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = card.name,
                style = fantasyHeadingStyle(
                    fontSize = 11.5.sp,
                    color = if (card.isChampion) HextechGoldBright else ParchmentWhite
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Attached Item Badges along the right edge before copy count
            if (attachedItems.isNotEmpty()) {
                Row(
                    modifier = Modifier.padding(end = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    attachedItems.take(3).forEach { item ->
                        val itemTint = rarityColor(item.rarity)
                        Box(
                            modifier = Modifier
                                .size(19.dp)
                                .clip(CircleShape)
                                .background(Color(0xE60B1322))
                                .border(1.dp, itemTint, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (item.category) {
                                    ItemCategory.STAT_KEYWORD -> Icons.Default.Shield
                                    ItemCategory.ECONOMY -> Icons.Default.Bolt
                                    ItemCategory.TRIGGER -> Icons.Default.AutoAwesome
                                },
                                contentDescription = item.name,
                                tint = itemTint,
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xE6080E19),
                border = BorderStroke(0.8.dp, HextechGold.copy(alpha = 0.72f))
            ) {
                Text(
                    text = "x${entry.copies}",
                    style = tabularStatStyle(10.sp, HextechGoldBright),
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
    }
}

/**
 * Right Column Showcase:
 * Enlarged full card matching the selected deck entry (or Champion by default)
 * + 3 Hexagonal Relic Sockets docked vertically along the card's right edge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChampionRightCardShowcase(
    card: CardTemplate,
    attachedItems: List<ItemTemplate>,
    copies: Int?,
    loadout: ChampionLoadout,
    onRelicSlotClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var showCardInspector by remember(card.id) { mutableStateOf(false) }

    val previewHandCard = remember(card, attachedItems) {
        buildPreviewHandCard(card, attachedItems)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            HandCardView(
                handCard = previewHandCard,
                effectiveCost = previewHandCard.effectiveCost,
                playable = true,
                isFocused = false,
                cardWidth = 172.dp,
                cardHeight = 250.dp,
                descFontSize = 11.5.sp,
                statFontSize = 15.5.sp,
                isShowcasePreview = true,
                onClick = { showCardInspector = true }
            )

            // 3 Vertical Hexagonal Relic Sockets beside card right edge
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                repeat(3) { idx ->
                    val unlocked = idx < loadout.unlockedRelicSlots
                    val relicId = loadout.equippedRelics.getOrNull(idx)
                    val relic = relicId?.let { CardCatalog.findRelicOrNull(it) }
                    val isEpicForged = idx < loadout.cosmicForgedSlots
                    val borderColor = when {
                        !unlocked -> SlateBorder.copy(alpha = 0.75f)
                        isEpicForged -> EpicViolet
                        relic != null -> rarityColor(relic.rarity)
                        else -> HextechGold
                    }

                    Surface(
                        modifier = Modifier
                            .size(width = 48.dp, height = 52.dp)
                            .testTag("relic_socket_$idx")
                            .clickable(enabled = unlocked) { onRelicSlotClick(idx) },
                        shape = HexagonShape,
                        color = if (unlocked) Color(0xE8142138) else Color(0xD00B111C),
                        border = BorderStroke(1.8.dp, borderColor),
                        shadowElevation = 6.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    !unlocked -> Icons.Default.Lock
                                    relic != null -> Icons.Default.Check
                                    else -> Icons.Default.Add
                                },
                                contentDescription = relic?.name ?: if (unlocked) "Ô Cổ Vật Trống" else "Ô Bị Khóa",
                                tint = borderColor,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }
        }

        if (attachedItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                attachedItems.forEach { item ->
                    val itemColor = rarityColor(item.rarity)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xD90A111F),
                        border = BorderStroke(0.8.dp, itemColor.copy(alpha = 0.75f))
                    ) {
                        Text(
                            text = "${item.name}: ${item.description}",
                            style = interBodyStyle(9.5.sp, ParchmentWhite),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        } else if (copies != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${card.name} • x$copies trong bộ bài",
                style = interBodyStyle(10.5.sp, MutedSilver),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    if (showCardInspector) {
        AlertDialog(
            onDismissRequest = { showCardInspector = false },
            containerColor = ObsidianSurface,
            title = null,
            text = {
                com.example.ui.components.CardTile(
                    template = card,
                    effectiveCost = previewHandCard.effectiveCost,
                    bonusPower = previewHandCard.bonusPower,
                    bonusHealth = previewHandCard.bonusHealth,
                    attachedItems = attachedItems
                )
            },
            confirmButton = {
                TextButton(onClick = { showCardInspector = false }) {
                    Text("Đóng", color = HextechGoldBright)
                }
            }
        )
    }
}

@Composable
private fun CompactStat(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            style = fantasyHeadingStyle(9.sp, MutedSilver)
        )
        Text(
            text = value,
            style = tabularStatStyle(13.sp, color)
        )
    }
}
