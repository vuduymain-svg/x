package com.example.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.R
import com.example.model.CardCatalog
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.DeckCardEntry
import com.example.model.HandCardInstance
import com.example.model.ItemTemplate
import com.example.model.Keyword
import com.example.model.KeywordCategory
import com.example.model.SpellSpeed
import com.example.model.StateCondition
import com.example.model.UnitOnBoard
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.AccentSpellMana
import com.example.ui.theme.BgDeep
import com.example.ui.theme.BorderGoldMuted
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.CurseMagenta
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.InterFontFamily
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

private fun cardBorderSpec(
    template: CardTemplate,
    focused: Boolean = false,
    isLeveledUp: Boolean = false
): BorderStroke = when {
    isLeveledUp -> BorderStroke(1.5.dp, AccentIce)
    focused -> BorderStroke(1.5.dp, AccentGold)
    template.isChampion -> BorderStroke(1.5.dp, AccentGold)
    template.type == CardType.CURSE -> BorderStroke(1.dp, CurseMagenta.copy(alpha = 0.7f))
    else -> BorderStroke(1.dp, BorderSubtle)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CardTile(
    template: CardTemplate,
    effectiveCost: Int = template.cost,
    bonusPower: Int = 0,
    bonusHealth: Int = 0,
    attachedItems: List<ItemTemplate> = emptyList(),
    extraKeywords: Set<Keyword> = emptySet(),
    isFleeting: Boolean = false,
    playable: Boolean = true,
    highlighted: Boolean = false,
    isLeveledUp: Boolean = false,
    compact: Boolean = false,
    testTag: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    remember(context) { CardCatalog.ensureLocalized(context) }

    var inspectedKeyword by remember { mutableStateOf<Keyword?>(null) }
    var inspectedSpellSpeed by remember { mutableStateOf<SpellSpeed?>(null) }

    val regionColor = regionAccentColor(template.region)
    val borderStroke = cardBorderSpec(template, focused = highlighted, isLeveledUp = isLeveledUp)
    val accentColor = when {
        isLeveledUp -> AccentIce
        highlighted -> AccentGold
        template.type == CardType.CURSE -> CurseMagenta
        template.isChampion -> AccentGold
        template.type == CardType.SPELL -> AccentIce
        else -> TextSecondary
    }

    val allKeywords = (template.keywords + extraKeywords + attachedItems.mapNotNull { it.grantedKeyword }).toList()
    val hasUnitStats = template.type == CardType.UNIT ||
        template.type == CardType.CHAMPION ||
        template.type == CardType.EQUIPMENT

    Surface(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .then(
                if (onClick != null) Modifier
                    .minimumInteractiveComponentSize()
                    .clickable(onClick = onClick)
                else Modifier
            ),
        shape = RoundedCornerShape(GameRadius.card),
        color = if (playable) SurfaceBase else BgDeep,
        border = borderStroke,
        shadowElevation = 0.dp
    ) {
        val tileArtRes = championPortraitRes(template.id, isLeveledUp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) GameSpacing.sm else GameSpacing.md),
            verticalArrangement = Arrangement.spacedBy(if (compact) GameSpacing.xs else GameSpacing.sm)
        ) {
            // Header Row: Mana badge + Cinzel Name + Item badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, AccentIce, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$effectiveCost",
                        style = statNumericStyle(13.sp, TextPrimary)
                    )
                }

                Spacer(modifier = Modifier.width(GameSpacing.sm))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = template.name,
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (compact) 12.sp else 14.sp,
                        color = if (template.isChampion) AccentGold else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                    ) {
                        Text(
                            text = template.region.displayName,
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = regionColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "•",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                        if (template.spellSpeed != SpellSpeed.NONE) {
                            Surface(
                                modifier = Modifier
                                    .testTag("spell_speed_badge_${template.spellSpeed.nameRef}")
                                    .clickable { inspectedSpellSpeed = template.spellSpeed },
                                shape = RoundedCornerShape(GameRadius.badge),
                                color = SurfaceElevated,
                                border = BorderStroke(1.dp, AccentIce.copy(alpha = 0.55f))
                            ) {
                                Text(
                                    text = template.spellSpeed.displayName,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = AccentIce,
                                    modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 1.dp)
                                )
                            }
                        } else {
                            Text(
                                text = template.type.displayName,
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (!compact && template.isCollectibleReward && !template.isChampion) {
                            Text(
                                text = "• ${template.rarity.displayName}",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = rarityColor(template.rarity),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isLeveledUp) {
                            Text(
                                text = "• Cấp 2",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentIce,
                                maxLines = 1
                            )
                        }
                        if (isFleeting) {
                            Text(
                                text = "• ${Keyword.FLEETING.displayName}",
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = AccentSpellMana,
                                maxLines = 1
                            )
                        }
                    }
                }

                if (attachedItems.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(GameRadius.badge),
                        color = SurfaceElevated,
                        border = BorderStroke(1.dp, AccentSpellMana.copy(alpha = 0.65f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Vật phẩm",
                                tint = AccentSpellMana,
                                modifier = Modifier.size(11.dp)
                            )
                            Text(
                                text = "${attachedItems.size}",
                                style = statNumericStyle(10.sp, TextPrimary)
                            )
                        }
                    }
                }
            }

            // Artwork Window
            if (tileArtRes != null && !compact) {
                val isSpellCard = template.type == CardType.SPELL
                val artShape = if (isSpellCard) RoundedCornerShape(GameRadius.panel) else RoundedCornerShape(GameRadius.control)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .padding(horizontal = if (isSpellCard) GameSpacing.md else 0.dp)
                        .clip(artShape)
                        .background(BgDeep)
                        .border(
                            width = 1.dp,
                            color = if (isSpellCard) AccentIce.copy(alpha = 0.55f) else BorderSubtle,
                            shape = artShape
                        )
                ) {
                    Image(
                        painter = painterResource(id = tileArtRes),
                        contentDescription = template.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Description & Max 2 Keywords Box
            if (!compact || allKeywords.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(GameRadius.control),
                    color = BgDeep,
                    border = BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(
                        modifier = Modifier.padding(GameSpacing.sm),
                        verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                    ) {
                        if (allKeywords.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                                verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                            ) {
                                allKeywords.take(2).forEach { kw ->
                                    val kwColor = when (kw.category) {
                                        KeywordCategory.COMBAT_POSITIVE -> AccentIce
                                        KeywordCategory.NEGATIVE_STATUS -> SemanticDanger
                                        KeywordCategory.CARD_PROPERTY -> AccentGold
                                    }
                                    Surface(
                                        modifier = Modifier
                                            .testTag("keyword_badge_${kw.nameRef}")
                                            .clickable { inspectedKeyword = kw },
                                        shape = RoundedCornerShape(GameRadius.badge),
                                        color = SurfaceElevated,
                                        border = BorderStroke(1.dp, kwColor.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = kw.displayName,
                                            fontFamily = PlusJakartaSansFamily,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = kwColor,
                                            modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        if (!compact && allKeywords.isNotEmpty() && template.description.isNotBlank()) {
                            HorizontalDivider(
                                thickness = 1.dp,
                                color = BorderSubtle
                            )
                        }

                        if (!compact) {
                            Text(
                                text = template.description,
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = TextPrimary,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (attachedItems.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
                ) {
                    attachedItems.forEach { item ->
                        Surface(
                            shape = RoundedCornerShape(GameRadius.badge),
                            color = SurfaceElevated,
                            border = BorderStroke(1.dp, rarityColor(item.rarity).copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = "${item.name} (${item.description})",
                                fontFamily = InterFontFamily,
                                fontSize = 10.sp,
                                color = TextSecondary,
                                modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // Bottom-Right Attack & Health Badges
            if (hasUnitStats) {
                val totalPow = template.basePower + bonusPower
                val totalHp = template.baseHealth + bonusHealth
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (template.isChampion) {
                        Icon(
                            imageVector = Icons.Outlined.StarOutline,
                            contentDescription = "Tướng",
                            tint = AccentGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    StatBadge(value = totalPow, label = "Công", color = SemanticAttack)
                    Spacer(modifier = Modifier.width(GameSpacing.sm))
                    StatBadge(value = totalHp, label = "Máu", color = SemanticDanger)
                }
            }
        }
    }

    inspectedKeyword?.let { kw ->
        KeywordTooltipDialog(
            title = kw.displayName + if (kw.isStackable) " (Cộng dồn)" else "",
            subtitle = kw.nameRef,
            description = kw.description,
            accentColor = when (kw.category) {
                KeywordCategory.COMBAT_POSITIVE -> AccentIce
                KeywordCategory.NEGATIVE_STATUS -> SemanticDanger
                KeywordCategory.CARD_PROPERTY -> AccentGold
            },
            onDismiss = { inspectedKeyword = null }
        )
    }

    inspectedSpellSpeed?.let { speed ->
        KeywordTooltipDialog(
            title = speed.displayName,
            subtitle = speed.nameRef,
            description = speed.description,
            accentColor = AccentIce,
            onDismiss = { inspectedSpellSpeed = null }
        )
    }
}

@Composable
fun KeywordTooltipDialog(
    title: String,
    subtitle: String,
    description: String,
    accentColor: Color = AccentGold,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("keyword_tooltip_dialog"),
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                Text(
                    text = title,
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = accentColor
                )
                Text(
                    text = subtitle,
                    fontFamily = PlusJakartaSansFamily,
                    fontSize = 11.sp,
                    color = TextSecondary
                )
            }
        },
        text = {
            Surface(
                shape = RoundedCornerShape(GameRadius.control),
                color = BgDeep,
                border = BorderStroke(1.dp, BorderSubtle)
            ) {
                Text(
                    text = description,
                    fontFamily = InterFontFamily,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(GameSpacing.md)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close_keyword_tooltip_button")
            ) {
                Text("Đóng", fontFamily = PlusJakartaSansFamily, fontWeight = FontWeight.SemiBold, color = AccentGold)
            }
        }
    )
}

@Composable
fun StatBadge(
    value: Int,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(GameRadius.badge),
        color = SurfaceElevated,
        border = BorderStroke(1.dp, color.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            Text(
                text = "$value",
                style = statNumericStyle(12.sp, TextPrimary)
            )
            Text(
                text = label,
                fontFamily = PlusJakartaSansFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = color
            )
        }
    }
}

@DrawableRes
fun cardPortraitRes(cardId: String): Int? = when (cardId) {
    "card_jinx", "card_super_mega_rocket" -> R.drawable.img_champ_jinx
    "card_lux", "card_final_spark" -> R.drawable.img_champ_lux
    "card_yasuo", "card_steel_tempest" -> R.drawable.img_champ_yasuo
    "card_anivia" -> R.drawable.img_champ_anivia
    "card_eggnivia" -> R.drawable.img_anivia_egg
    "card_wild_mystics" -> R.drawable.img_wild_mystics
    "card_mortal_prophet" -> R.drawable.img_mortal_prophet
    "card_guardian_follower" -> R.drawable.img_guardian_follower
    "card_flame_eyed_caprine" -> R.drawable.img_flame_eyed_caprine
    "card_crystal_stormbird" -> R.drawable.img_crystal_stormbird
    "card_harsh_winds", "card_anivia_harsh_winds", "card_frozen_arrow", "card_flash_freeze" -> R.drawable.img_harsh_winds
    "card_wings_of_cryophoenix", "skill_glacial_storm" -> R.drawable.img_wings_of_cryophoenix
    "card_clash_of_giants", "card_freljord_titan", "card_glacial_colossus", "card_ancient_yeti",
    "card_uzgar", "card_rhasa", "card_rumul", "card_lost_soul", "card_twinblade_revenant" -> R.drawable.img_clash_of_giants
    else -> null
}

@DrawableRes
fun championPortraitRes(cardId: String, isLeveledUp: Boolean): Int? =
    if (isLeveledUp && cardId == "card_anivia") {
        R.drawable.img_wings_of_cryophoenix
    } else {
        cardPortraitRes(cardId)
    }

@DrawableRes
fun unitPortraitRes(unit: UnitOnBoard): Int? =
    championPortraitRes(unit.template.id, unit.isLeveledUp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HandCardView(
    handCard: HandCardInstance,
    effectiveCost: Int,
    playable: Boolean,
    isFocused: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    descFontSize: TextUnit,
    statFontSize: TextUnit,
    fanAngle: Float = 0f,
    fanLiftY: Dp = 0.dp,
    isDragging: Boolean = false,
    isShowcasePreview: Boolean = false,
    enablePressElevation: Boolean = true,
    testTag: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val template = handCard.template
    val regionColor = regionAccentColor(template.region)
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val elevated = enablePressElevation && !isShowcasePreview && (isPressed || isFocused) && !isDragging

    val scale by animateFloatAsState(
        targetValue = if (elevated) 1.38f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "hand_card_scale"
    )
    val liftY by animateDpAsState(
        targetValue = if (elevated) -cardHeight * 0.40f else fanLiftY,
        animationSpec = tween(durationMillis = 150),
        label = "hand_card_lift"
    )
    val rotation by animateFloatAsState(
        targetValue = if (elevated || isDragging) 0f else fanAngle,
        animationSpec = tween(durationMillis = 150),
        label = "hand_card_rotation"
    )

    val borderStroke = when {
        isFocused || elevated -> BorderStroke(1.5.dp, AccentIce)
        template.isChampion -> BorderStroke(1.5.dp, AccentGold)
        template.type == CardType.CURSE -> BorderStroke(1.dp, CurseMagenta.copy(alpha = 0.7f))
        else -> BorderStroke(1.dp, BorderSubtle)
    }

    val portraitRes = cardPortraitRes(template.id)
    val artHeight = cardHeight * 0.44f
    val nameFontSize = (cardWidth.value * 0.095f).coerceIn(8.5f, 11.5f).sp
    val infoFontSize = (cardWidth.value * 0.080f).coerceIn(7.4f, 9.8f).sp
    val infoLineHeight = (cardWidth.value * 0.094f).coerceIn(8.8f, 11.4f).sp
    val tinyStatSize = maxOf((statFontSize.value * 0.82f), (cardWidth.value * 0.110f)).coerceIn(9.5f, 13.5f).sp
    val hasUnitStats = template.type == CardType.UNIT ||
        template.type == CardType.CHAMPION ||
        template.type == CardType.EQUIPMENT

    Surface(
        modifier = modifier
            .zIndex(if (elevated) 50f else 1f)
            .alpha(if (isDragging) 0.25f else 1f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                rotationZ = rotation
                translationY = liftY.toPx()
            }
            .size(width = cardWidth, height = cardHeight)
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(GameRadius.card),
        color = if (playable) SurfaceBase else BgDeep,
        border = borderStroke,
        shadowElevation = 0.dp
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Artwork Window with embedded Mana Gem & bottom-edge Nameplate Scrim
                val isSpellCard = template.type == CardType.SPELL
                val artShape = if (isSpellCard) RoundedCornerShape(GameRadius.card) else RoundedCornerShape(topStart = GameRadius.card, topEnd = GameRadius.card)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artHeight)
                        .padding(
                            start = if (isSpellCard) GameSpacing.xs else 0.dp,
                            end = if (isSpellCard) GameSpacing.xs else 0.dp,
                            top = if (isSpellCard) GameSpacing.xs else 0.dp
                        )
                        .clip(artShape)
                        .background(SurfaceElevated)
                        .then(
                            if (isSpellCard) {
                                Modifier.border(1.dp, AccentIce.copy(alpha = 0.55f), artShape)
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (portraitRes != null) {
                        Image(
                            painter = painterResource(id = portraitRes),
                            contentDescription = template.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = when (template.type) {
                                CardType.SPELL -> Icons.Outlined.AutoFixHigh
                                CardType.CHAMPION -> Icons.Outlined.StarOutline
                                CardType.EQUIPMENT -> Icons.Outlined.Gavel
                                CardType.LANDMARK -> Icons.Outlined.HourglassBottom
                                CardType.CURSE -> Icons.Outlined.Bolt
                                CardType.UNIT -> Icons.Outlined.SportsKabaddi
                            },
                            contentDescription = template.name,
                            tint = if (template.isChampion) AccentGold else regionColor,
                            modifier = Modifier.size(cardWidth * 0.34f)
                        )
                    }

                    // Bottom gradient scrim on artwork so card name stays razor-sharp
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(artHeight * 0.44f)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xE6080E1A))
                                )
                            )
                    )

                    Text(
                        text = template.name,
                        fontFamily = CinzelFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = nameFontSize,
                        color = if (template.isChampion) AccentGold else TextPrimary,
                        lineHeight = nameFontSize * 1.08f,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }

                HorizontalDivider(
                    thickness = 1.dp,
                    color = if (template.isChampion) BorderGoldMuted else BorderSubtle
                )

                // Lower Card Info Panel: Keywords / Spell Speed + Readable Effect Text + ATK/HP Stats
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(BgDeep)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val mergedKeywords = remember(template.keywords, handCard.extraKeywords) {
                        (template.keywords + handCard.extraKeywords).toList()
                    }
                    val kwHeader = if (mergedKeywords.isNotEmpty()) {
                        mergedKeywords.take(2).joinToString(" • ") { it.displayName }
                    } else if (template.spellSpeed != SpellSpeed.NONE) {
                        template.spellSpeed.displayName
                    } else null

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (kwHeader != null) {
                            Text(
                                text = kwHeader,
                                fontFamily = PlusJakartaSansFamily,
                                fontSize = (infoFontSize.value * 0.94f).coerceAtLeast(7.2f).sp,
                                fontWeight = FontWeight.Bold,
                                color = if (template.spellSpeed != SpellSpeed.NONE && mergedKeywords.isEmpty()) AccentIce else AccentGold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            HorizontalDivider(thickness = 0.6.dp, color = BorderSubtle.copy(alpha = 0.7f))
                        }
                        Text(
                            text = template.description,
                            fontFamily = InterFontFamily,
                            fontSize = infoFontSize,
                            lineHeight = infoLineHeight,
                            color = TextPrimary.copy(alpha = 0.90f),
                            maxLines = if (isShowcasePreview) 4 else 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (handCard.attachedItems.isNotEmpty() && (hasUnitStats || isShowcasePreview)) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = "Vật phẩm",
                                tint = AccentSpellMana,
                                modifier = Modifier.size(cardWidth * 0.14f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        if (hasUnitStats) {
                            val totalPow = template.basePower + handCard.bonusPower
                            val totalHp = template.baseHealth + handCard.bonusHealth
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(GameRadius.badge))
                                    .background(SurfaceElevated)
                                    .border(1.dp, SemanticAttack.copy(alpha = 0.8f), RoundedCornerShape(GameRadius.badge))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$totalPow",
                                    style = statNumericStyle(tinyStatSize, TextPrimary)
                                )
                            }
                            Spacer(modifier = Modifier.width(GameSpacing.xs))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(GameRadius.badge))
                                    .background(SurfaceElevated)
                                    .border(1.dp, SemanticDanger.copy(alpha = 0.8f), RoundedCornerShape(GameRadius.badge))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = "$totalHp",
                                    style = statNumericStyle(tinyStatSize, TextPrimary)
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(GameRadius.badge))
                                    .background(SurfaceElevated)
                                    .border(1.dp, AccentIce.copy(alpha = 0.65f), RoundedCornerShape(GameRadius.badge))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = template.spellSpeed.displayName,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontSize = (infoFontSize.value * 0.90f).coerceAtLeast(7f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentIce,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // Top-Left Tabular Mana Gem
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(3.dp)
                    .size(cardWidth * 0.25f)
                    .zIndex(5f)
                    .clip(CircleShape)
                    .background(Color(0xEE0E1A2E))
                    .border(
                        width = 1.2.dp,
                        color = if (playable) AccentIce else BorderSubtle,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "$effectiveCost",
                    style = statNumericStyle(tinyStatSize, if (playable) AccentIce else TextSecondary)
                )
            }

            if (handCard.isFleeting) {
                Icon(
                    imageVector = Icons.Outlined.HourglassBottom,
                    contentDescription = "Thoáng chốc",
                    tint = AccentSpellMana,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(GameSpacing.xs)
                        .size(cardWidth * 0.16f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UnitToken(
    unit: UnitOnBoard,
    isAttacking: Boolean = false,
    isBlockingLabel: String? = null,
    isSelected: Boolean = false,
    isValidBlockCandidate: Boolean = false,
    isDragging: Boolean = false,
    tileWidth: Dp = 102.dp,
    tileHeight: Dp = 126.dp,
    statFontSize: TextUnit = 14.sp,
    testTag: String? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val spawnScale = remember(unit.instanceId) { Animatable(0.88f) }
    LaunchedEffect(unit.instanceId) {
        spawnScale.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 180)
        )
    }

    var prevLeveled by remember(unit.instanceId) { mutableStateOf(unit.isLeveledUp) }
    val levelUpScale = remember(unit.instanceId) { Animatable(1f) }
    LaunchedEffect(unit.isLeveledUp) {
        if (!prevLeveled && unit.isLeveledUp) {
            prevLeveled = true
            levelUpScale.snapTo(1.16f)
            levelUpScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        } else {
            prevLeveled = unit.isLeveledUp
        }
    }

    var prevHealth by remember(unit.instanceId) { mutableIntStateOf(unit.health) }
    var damageDelta by remember(unit.instanceId) { mutableIntStateOf(0) }
    LaunchedEffect(unit.health) {
        if (unit.health < prevHealth) {
            damageDelta = prevHealth - unit.health
            prevHealth = unit.health
            kotlinx.coroutines.delay(600)
            damageDelta = 0
        } else {
            prevHealth = unit.health
        }
    }

    val isChampion = unit.template.isChampion
    val isLeveledChampion = isChampion && unit.isLeveledUp
    val targetBorderColor = when {
        damageDelta > 0 -> SemanticDanger
        isSelected -> AccentIce
        isAttacking -> SemanticAttack
        isBlockingLabel != null -> SemanticHeal
        isValidBlockCandidate -> AccentIce
        isLeveledChampion -> AccentIce
        isChampion -> AccentGold
        else -> BorderSubtle
    }
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = tween(durationMillis = 150),
        label = "unit_border_color"
    )
    val borderWidth = if (isChampion || isSelected || isAttacking || isBlockingLabel != null || isValidBlockCandidate) {
        1.5.dp
    } else {
        1.dp
    }

    val regionColor = regionAccentColor(unit.template.region)
    val portraitRes = unitPortraitRes(unit)
    val statTextSize = (statFontSize.value * 1.0f).coerceIn(11f, 16f).sp

    val activeKeywords = remember(unit) {
        buildList {
            if (unit.hasBarrier) add(Triple(Icons.Outlined.Shield, AccentGold, "Lá chắn"))
            if (unit.hasSpellShield) add(Triple(Icons.Outlined.AutoFixHigh, AccentSpellMana, "Khiên phép"))
            if (unit.isFrostbitten) add(Triple(Icons.Outlined.AcUnit, AccentIce, "Đóng băng"))
            if (unit.isStunned) add(Triple(Icons.Outlined.FlashOn, SemanticDanger, "Choáng"))
            if (unit.keywords.contains(Keyword.QUICK_ATTACK) || unit.keywords.contains(Keyword.DOUBLE_ATTACK)) {
                add(Triple(Icons.Outlined.Bolt, SemanticAttack, "Đánh nhanh"))
            }
            if (unit.keywords.contains(Keyword.OVERWHELM)) {
                add(Triple(Icons.Outlined.Whatshot, SemanticDanger, "Áp đảo"))
            }
            if (unit.keywords.contains(Keyword.ELUSIVE)) {
                add(Triple(Icons.Outlined.VisibilityOff, AccentIce, "Lẩn tránh"))
            }
            if (unit.keywords.contains(Keyword.TOUGH) || unit.keywords.contains(Keyword.REGENERATION)) {
                add(Triple(Icons.Outlined.Shield, SemanticHeal, "Kiên cường/Hồi phục"))
            }
        }
    }

    Box(
        modifier = modifier
            .alpha(if (isDragging) 0.25f else 1f)
            .graphicsLayer {
                val combinedScale = spawnScale.value * levelUpScale.value
                scaleX = combinedScale
                scaleY = combinedScale
            }
            .size(width = tileWidth, height = tileHeight)
            .minimumInteractiveComponentSize()
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 2.dp),
            shape = RoundedCornerShape(GameRadius.card),
            color = if (unit.isFrostbitten) Color(0xFF172738) else SurfaceBase,
            border = BorderStroke(borderWidth, borderColor),
            shadowElevation = 0.dp
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(BgDeep),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(
                        targetState = portraitRes,
                        animationSpec = tween(250),
                        label = "unit_portrait_morph"
                    ) { resId ->
                        if (resId != null) {
                            Image(
                                painter = painterResource(id = resId),
                                contentDescription = unit.template.name,
                                contentScale = ContentScale.Crop,
                                alignment = Alignment.TopCenter,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                regionColor.copy(alpha = 0.34f),
                                                SurfaceElevated,
                                                BgDeep
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(bottom = tileHeight * 0.22f)
                                        .size(tileWidth * 0.44f)
                                        .clip(CircleShape)
                                        .background(regionColor.copy(alpha = 0.18f))
                                        .border(
                                            width = 1.2.dp,
                                            color = (if (isChampion) AccentGold else regionColor).copy(alpha = 0.72f),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = when {
                                            unit.template.type == CardType.LANDMARK -> Icons.Outlined.HourglassBottom
                                            isChampion -> Icons.Outlined.StarOutline
                                            else -> Icons.Outlined.SportsKabaddi
                                        },
                                        contentDescription = unit.template.name,
                                        tint = if (isChampion) AccentGold else regionColor,
                                        modifier = Modifier.size(tileWidth * 0.27f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom info gradient scrim so Unit Name, Keyword/Status, and ATK/HP are crystal-clear on the board
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(tileHeight * 0.44f)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color(0xBD070D18), Color(0xEB050A13))
                            )
                        )
                )

                val statusOrKeywordTag: Pair<String, Color>? = when {
                    unit.isStunned -> "Choáng" to SemanticDanger
                    unit.isFrostbitten -> "Đóng Băng" to AccentIce
                    unit.hasBarrier -> "Lá Chắn" to AccentGold
                    unit.hasSpellShield -> "Khiên Phép" to AccentSpellMana
                    unit.keywords.isNotEmpty() -> {
                        val kw = unit.keywords.first()
                        val kwTint = when (kw.category) {
                            KeywordCategory.COMBAT_POSITIVE -> AccentIce
                            KeywordCategory.NEGATIVE_STATUS -> SemanticDanger
                            KeywordCategory.CARD_PROPERTY -> AccentGold
                        }
                        kw.displayName to kwTint
                    }
                    else -> null
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(
                            start = 3.dp,
                            end = 3.dp,
                            bottom = if (unit.template.type != CardType.LANDMARK) (statTextSize.value + 10f).dp else 4.dp
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = unit.template.name,
                        fontFamily = PlusJakartaSansFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = (tileWidth.value * 0.122f).coerceIn(7.8f, 10.5f).sp,
                        lineHeight = (tileWidth.value * 0.135f).coerceIn(8.8f, 11.5f).sp,
                        color = if (isChampion) AccentGold else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    if (statusOrKeywordTag != null && tileHeight >= 68.dp) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xB30D192B))
                                .border(0.7.dp, statusOrKeywordTag.second.copy(alpha = 0.65f), RoundedCornerShape(3.dp))
                                .padding(horizontal = 4.dp, vertical = 0.5.dp)
                        ) {
                            Text(
                                text = statusOrKeywordTag.first,
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (tileWidth.value * 0.098f).coerceIn(6.5f, 8.5f).sp,
                                color = statusOrKeywordTag.second,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Bottom-Left Attack & Bottom-Right Health tabular badges
                if (unit.template.type != CardType.LANDMARK) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 3.dp, bottom = 3.dp)
                            .clip(RoundedCornerShape(GameRadius.badge))
                            .background(SurfaceBase.copy(alpha = 0.95f))
                            .border(1.dp, SemanticAttack, RoundedCornerShape(GameRadius.badge))
                            .padding(horizontal = GameSpacing.xs, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${unit.strikePower}",
                            style = statNumericStyle(
                                fontSize = statTextSize,
                                color = if (unit.isFrostbitten) AccentIce else TextPrimary
                            )
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 3.dp, bottom = 3.dp)
                            .clip(RoundedCornerShape(GameRadius.badge))
                            .background(SurfaceBase.copy(alpha = 0.95f))
                            .border(1.dp, SemanticDanger, RoundedCornerShape(GameRadius.badge))
                            .padding(horizontal = GameSpacing.xs, vertical = 1.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${unit.health}",
                            style = statNumericStyle(
                                fontSize = statTextSize,
                                color = if (unit.health < unit.maxHealth) AccentGold else TextPrimary
                            )
                        )
                    }
                }
            }
        }

        // Top-Edge Champion Crest / Landmark Countdown / Keyword Medallions
        if (unit.template.type == CardType.LANDMARK) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                shape = RoundedCornerShape(GameRadius.badge),
                color = SurfaceBase.copy(alpha = 0.90f),
                border = BorderStroke(1.dp, AccentGold)
            ) {
                Text(
                    text = "${unit.countdownRemaining}",
                    style = statNumericStyle(statTextSize, TextPrimary),
                    modifier = Modifier.padding(horizontal = GameSpacing.sm, vertical = 1.dp)
                )
            }
        } else if (activeKeywords.isNotEmpty() || isChampion) {
            val iconSize = (tileWidth * 0.19f).coerceIn(10.dp, 14.dp)
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .clip(RoundedCornerShape(GameRadius.badge))
                    .background(SurfaceBase.copy(alpha = 0.84f))
                    .border(
                        width = 1.dp,
                        color = if (isChampion) AccentGold else BorderSubtle,
                        shape = RoundedCornerShape(GameRadius.badge)
                    )
                    .padding(horizontal = 4.dp, vertical = 1.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isChampion) {
                    if (unit.isLeveledUp) {
                        Text(
                            text = "Lv2",
                            fontFamily = PlusJakartaSansFamily,
                            fontSize = (iconSize.value * 0.65f).coerceAtLeast(7f).sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentIce
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.StarOutline,
                        contentDescription = if (unit.isLeveledUp) "Tướng đã thăng cấp" else "Tướng",
                        tint = AccentGold,
                        modifier = Modifier.size(iconSize)
                    )
                }
                activeKeywords.take(2).forEach { (icon, tint, label) ->
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = tint,
                        modifier = Modifier.size(iconSize)
                    )
                }
            }
        }

        // Floating Damage Delta Pill
        AnimatedVisibility(
            visible = damageDelta > 0,
            enter = fadeIn(tween(120)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(GameRadius.badge),
                color = SemanticDanger,
                border = BorderStroke(1.dp, TextPrimary)
            ) {
                Text(
                    text = "-$damageDelta",
                    style = statNumericStyle((statFontSize.value * 0.9f).coerceAtLeast(10f).sp, TextPrimary),
                    modifier = Modifier.padding(horizontal = GameSpacing.xs, vertical = 1.dp)
                )
            }
        }
    }
}

@Composable
fun RunDeckDialog(
    runDeck: List<DeckCardEntry>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Bộ bài hiện tại (${runDeck.sumOf { it.copies }} lá)",
                    fontFamily = CinzelFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AccentGold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Outlined.Close, contentDescription = "Đóng bộ bài", tint = TextSecondary)
                }
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)
            ) {
                items(runDeck, key = { it.cardId }) { entry ->
                    val card = CardCatalog.getCard(entry.cardId)
                    val items = entry.attachedItemIds.map { CardCatalog.getItem(it) }
                    Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                        Text(
                            text = "${entry.copies}x ${card.name}",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = TextPrimary
                        )
                        CardTile(
                            template = card,
                            effectiveCost = maxOf(0, card.cost - items.sumOf { it.costReduction }),
                            bonusPower = items.sumOf { it.bonusPower },
                            bonusHealth = items.sumOf { it.bonusHealth },
                            attachedItems = items,
                            compact = false
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
fun KeywordGlossaryDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    remember(context) { CardCatalog.ensureLocalized(context) }

    var selectedCategory by remember { mutableStateOf<KeywordCategory?>(null) }
    val filteredKeywords = remember(selectedCategory) {
        if (selectedCategory == null) Keyword.entries
        else Keyword.entries.filter { it.category == selectedCategory }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBase,
        shape = RoundedCornerShape(GameRadius.panel),
        title = {
            Text(
                text = "Sổ tay từ khóa & luật chơi",
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = AccentGold
            )
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 450.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(GameSpacing.sm)
                ) {
                    FilterTag("Tất cả", selectedCategory == null) { selectedCategory = null }
                    FilterTag("Tích cực", selectedCategory == KeywordCategory.COMBAT_POSITIVE) {
                        selectedCategory = KeywordCategory.COMBAT_POSITIVE
                    }
                    FilterTag("Trạng thái", selectedCategory == KeywordCategory.NEGATIVE_STATUS) {
                        selectedCategory = KeywordCategory.NEGATIVE_STATUS
                    }
                }
                Spacer(modifier = Modifier.height(GameSpacing.sm))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(GameSpacing.sm)) {
                    items(filteredKeywords, key = { it.name }) { kw ->
                        Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                            Text(
                                text = kw.displayName + if (kw.isStackable) " (Cộng dồn)" else "",
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                color = when (kw.category) {
                                    KeywordCategory.COMBAT_POSITIVE -> AccentIce
                                    KeywordCategory.NEGATIVE_STATUS -> SemanticDanger
                                    KeywordCategory.CARD_PROPERTY -> AccentGold
                                }
                            )
                            Text(
                                text = kw.description,
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                            HorizontalDivider(color = BorderSubtle)
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.height(GameSpacing.sm))
                        Text(
                            text = "Điều kiện trạng thái",
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = AccentGold
                        )
                    }
                    items(StateCondition.entries, key = { it.name }) { sc ->
                        Column(verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)) {
                            Text(
                                text = sc.displayName,
                                fontFamily = PlusJakartaSansFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = SemanticHeal
                            )
                            Text(
                                text = sc.description,
                                fontFamily = InterFontFamily,
                                fontSize = 12.sp,
                                color = TextSecondary
                            )
                        }
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
private fun FilterTag(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(GameRadius.control),
        color = if (selected) SurfaceElevated else BgDeep,
        border = BorderStroke(1.dp, if (selected) BorderGoldMuted else BorderSubtle)
    ) {
        Text(
            text = label,
            fontFamily = PlusJakartaSansFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            color = if (selected) AccentGold else TextSecondary,
            modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm)
        )
    }
}
