package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.Keyword
import com.example.model.SpellSpeed
import com.example.ui.theme.ArcaneCyan
import com.example.ui.theme.CrimsonNexus
import com.example.ui.theme.EmeraldHeal
import com.example.ui.theme.EpicViolet
import com.example.ui.theme.HextechGold
import com.example.ui.theme.HextechGoldBright
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.LegendaryAmber
import com.example.ui.theme.ParchmentWhite
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class SpellSkillVfxEvent(
    val id: String,
    val card: CardTemplate,
    val sourceIsPlayer: Boolean,
    val targetCenterNorm: Offset? = null,
    val isSkill: Boolean = card.spellSpeed == SpellSpeed.SKILL
)

private enum class SpellVisualStyle {
    BLIZZARD_AOE,
    FROST_TARGET,
    ARCANE_PROJECTILE,
    TEMPEST_LIGHTNING,
    SHIELD_GOLDEN_BUFF,
    SEISMIC_COSMIC
}

private fun visualStyleFor(card: CardTemplate): SpellVisualStyle = when {
    card.id == "skill_glacial_storm" ||
        card.id == "card_wings_of_cryophoenix" ||
        card.id == "card_crystal_stormbird" ||
        card.effectType == CardEffectType.GLACIAL_STORM_SKILL ||
        card.effectType == CardEffectType.WINGS_OF_CRYOPHOENIX -> SpellVisualStyle.BLIZZARD_AOE

    card.id == "card_icevale_archer" ||
        card.effectType == CardEffectType.FROSTBITE_TARGET ||
        card.effectType == CardEffectType.FROSTBITE_TWO ||
        card.effectType == CardEffectType.ANIVIA_CHAMPION_SPELL -> SpellVisualStyle.FROST_TARGET

    card.id == "card_flame_eyed_caprine" ||
        card.effectType == CardEffectType.DEAL_DAMAGE_ANY ||
        card.effectType == CardEffectType.DEAL_DAMAGE_NEXUS ||
        card.effectType == CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS -> SpellVisualStyle.ARCANE_PROJECTILE

    card.effectType == CardEffectType.STUN_TARGET ||
        card.effectType == CardEffectType.RECALL_OR_STUN -> SpellVisualStyle.TEMPEST_LIGHTNING

    card.effectType == CardEffectType.GIVE_BARRIER_AND_DRAW ||
        card.effectType == CardEffectType.RALLY_AND_SHIELD ||
        card.effectType == CardEffectType.RALLY ||
        card.effectType == CardEffectType.FREE_ATTACK ||
        card.effectType == CardEffectType.SINGLE_COMBAT_STRIKE ||
        card.effectType == CardEffectType.PLANT_CHIMES -> SpellVisualStyle.SHIELD_GOLDEN_BUFF

    else -> SpellVisualStyle.SEISMIC_COSMIC
}

private fun accentColorFor(card: CardTemplate, style: SpellVisualStyle, sourceIsPlayer: Boolean): Color = when (style) {
    SpellVisualStyle.BLIZZARD_AOE,
    SpellVisualStyle.FROST_TARGET -> ArcaneCyan
    SpellVisualStyle.ARCANE_PROJECTILE -> when {
        card.id == "card_final_spark" -> HextechGoldBright
        card.id == "card_super_mega_rocket" || card.id == "card_flame_eyed_caprine" -> LegendaryAmber
        !sourceIsPlayer -> CrimsonNexus
        else -> Color(0xFF38BDF8)
    }
    SpellVisualStyle.TEMPEST_LIGHTNING -> Color(0xFF5EEAD4)
    SpellVisualStyle.SHIELD_GOLDEN_BUFF -> HextechGoldBright
    SpellVisualStyle.SEISMIC_COSMIC -> if (card.effectType == CardEffectType.DRAIN) EmeraldHeal else EpicViolet
}

@Composable
fun SpellSkillEffectOverlay(
    events: List<SpellSkillVfxEvent>,
    topBannerOffset: Dp,
    onFinished: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (events.isEmpty()) return

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("spell_skill_vfx_overlay")
    ) {
        events.forEachIndexed { index, event ->
            SpellSkillVfxLayer(
                event = event,
                topBannerOffset = topBannerOffset + (index * 46).dp,
                onFinished = onFinished
            )
        }
    }
}

@Composable
private fun SpellSkillVfxLayer(
    event: SpellSkillVfxEvent,
    topBannerOffset: Dp,
    onFinished: (String) -> Unit
) {
    val progress = remember(event.id) { Animatable(0f) }
    val bannerScale = remember(event.id) { Animatable(0.76f) }

    LaunchedEffect(event.id) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing)
        )
        onFinished(event.id)
    }
    LaunchedEffect(event.id) {
        bannerScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val p = progress.value
    val fadeAlpha = when {
        p < 0.14f -> (0.15f + (p / 0.14f) * 0.85f).coerceIn(0f, 1f)
        p > 0.72f -> ((1f - p) / 0.28f).coerceIn(0f, 1f)
        else -> 1f
    }

    val style = remember(event.card.id, event.card.effectType) { visualStyleFor(event.card) }
    val accent = remember(event.card.id, style, event.sourceIsPlayer) {
        accentColorFor(event.card, style, event.sourceIsPlayer)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 1. Full-board elemental / arcane Canvas animation
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width <= 1f || size.height <= 1f) return@Canvas
            val defaultTarget = if (event.sourceIsPlayer) {
                Offset(size.width * 0.52f, size.height * 0.28f)
            } else {
                Offset(size.width * 0.52f, size.height * 0.72f)
            }
            val targetPt = event.targetCenterNorm?.let { norm ->
                Offset(
                    x = (norm.x * size.width).coerceIn(size.width * 0.12f, size.width * 0.88f),
                    y = (norm.y * size.height).coerceIn(size.height * 0.12f, size.height * 0.88f)
                )
            } ?: defaultTarget

            val originPt = if (event.card.spellSpeed == SpellSpeed.FAST ||
                event.card.spellSpeed == SpellSpeed.SLOW ||
                event.isSkill
            ) {
                Offset(size.width * 0.18f, size.height * 0.50f)
            } else {
                Offset(
                    x = size.width * 0.50f,
                    y = if (event.sourceIsPlayer) size.height * 0.86f else size.height * 0.14f
                )
            }

            when (style) {
                SpellVisualStyle.BLIZZARD_AOE -> drawBlizzardAoeVfx(
                    p = p,
                    alpha = fadeAlpha,
                    accent = accent,
                    targetEnemyHalf = event.sourceIsPlayer,
                    fullBoard = event.card.id == "card_wings_of_cryophoenix" || event.card.id == "card_crystal_stormbird"
                )
                SpellVisualStyle.FROST_TARGET -> drawFrostTargetVfx(
                    p = p,
                    alpha = fadeAlpha,
                    target = targetPt,
                    accent = accent
                )
                SpellVisualStyle.ARCANE_PROJECTILE -> drawArcaneProjectileVfx(
                    p = p,
                    alpha = fadeAlpha,
                    origin = originPt,
                    target = targetPt,
                    accent = accent
                )
                SpellVisualStyle.TEMPEST_LIGHTNING -> drawTempestVfx(
                    p = p,
                    alpha = fadeAlpha,
                    target = targetPt,
                    accent = accent
                )
                SpellVisualStyle.SHIELD_GOLDEN_BUFF -> drawGoldenShieldVfx(
                    p = p,
                    alpha = fadeAlpha,
                    target = targetPt,
                    accent = accent,
                    isClash = event.card.effectType == CardEffectType.SINGLE_COMBAT_STRIKE
                )
                SpellVisualStyle.SEISMIC_COSMIC -> drawSeismicCosmicVfx(
                    p = p,
                    alpha = fadeAlpha,
                    target = targetPt,
                    accent = accent
                )
            }
        }

        // 2. Non-touch-blocking Spell/Skill Cast Banner Crest near upper center
        val badgeText = if (event.isSkill || event.card.spellSpeed == SpellSpeed.SKILL) {
            "KỸ NĂNG"
        } else {
            event.card.spellSpeed.displayName.uppercase()
        }
        val portraitRes = cardPortraitRes(event.card.id)
        val ownerBorder = if (event.sourceIsPlayer) accent else CrimsonNexus

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topBannerOffset)
                .testTag("spell_skill_vfx_${event.card.id}")
                .graphicsLayer {
                    scaleX = bannerScale.value
                    scaleY = bannerScale.value
                    alpha = fadeAlpha
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xEB0B1526))
                .border(1.8.dp, ownerBorder, RoundedCornerShape(24.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF172742))
                    .border(1.3.dp, HextechGoldBright, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (portraitRes != null) {
                    Image(
                        painter = painterResource(id = portraitRes),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    val iconVector = when (style) {
                        SpellVisualStyle.BLIZZARD_AOE, SpellVisualStyle.FROST_TARGET -> Icons.Default.AcUnit
                        SpellVisualStyle.SHIELD_GOLDEN_BUFF -> Icons.Default.Shield
                        SpellVisualStyle.ARCANE_PROJECTILE, SpellVisualStyle.TEMPEST_LIGHTNING -> Icons.Default.Bolt
                        SpellVisualStyle.SEISMIC_COSMIC -> Icons.Default.AutoAwesome
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = event.card.name,
                    style = fantasyHeadingStyle(12.5.sp, ParchmentWhite),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = badgeText,
                        fontFamily = InterFontFamily,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent
                    )
                    if (event.card.keywords.contains(Keyword.ELEMENTAL)) {
                        Text(
                            text = "• NGUYÊN TỐ",
                            fontFamily = InterFontFamily,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = HextechGoldBright
                        )
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawBlizzardAoeVfx(
    p: Float,
    alpha: Float,
    accent: Color,
    targetEnemyHalf: Boolean,
    fullBoard: Boolean
) {
    val zoneCenterY = when {
        fullBoard -> size.height * 0.50f
        targetEnemyHalf -> size.height * 0.29f
        else -> size.height * 0.71f
    }
    val zoneCenter = Offset(size.width * 0.50f, zoneCenterY)
    val sweepRadius = size.maxDimension * (0.20f + 0.56f * p)

    // Frosty icy aura wash over target arena
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.28f * alpha),
                accent.copy(alpha = 0.34f * alpha),
                Color.Transparent
            ),
            center = zoneCenter,
            radius = sweepRadius
        ),
        radius = sweepRadius,
        center = zoneCenter
    )

    // Sweeping blizzard wind arcs across the lane
    for (i in 0 until 4) {
        val yOffset = zoneCenterY + (i - 1.5f) * (size.height * 0.085f)
        val sweepStartX = -size.width * 0.15f + size.width * 0.95f * p
        val sweepEndX = sweepStartX + size.width * 0.38f
        drawLine(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    accent.copy(alpha = 0.68f * alpha),
                    Color.White.copy(alpha = 0.85f * alpha),
                    Color.Transparent
                ),
                startX = sweepStartX,
                endX = sweepEndX
            ),
            start = Offset(sweepStartX, yOffset + 12.dp.toPx()),
            end = Offset(sweepEndX, yOffset - 12.dp.toPx()),
            strokeWidth = (4.5f - i * 0.6f).dp.toPx(),
            cap = StrokeCap.Round
        )
    }

    // Concentric crystal frost shockwaves
    val ring1R = size.minDimension * (0.14f + 0.62f * p)
    val ring2R = size.minDimension * (0.06f + 0.44f * p)
    drawCircle(
        color = accent.copy(alpha = 0.72f * alpha),
        radius = ring1R,
        center = zoneCenter,
        style = Stroke(width = 4.dp.toPx())
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.75f * alpha),
        radius = ring2R,
        center = zoneCenter,
        style = Stroke(width = 2.2.dp.toPx())
    )

    // 16 bursting ice crystal shards
    val shardCount = 16
    for (i in 0 until shardCount) {
        val angle = (i * (360f / shardCount) + p * 42f) * (PI.toFloat() / 180f)
        val dist = size.minDimension * (0.09f + (0.38f + (i % 3) * 0.10f) * p)
        val cx = zoneCenter.x + cos(angle) * dist
        val cy = zoneCenter.y + sin(angle) * dist * 0.65f
        val shardLen = (9f + (i % 3) * 4f).dp.toPx() * (1f - p * 0.35f)
        drawIceDiamond(
            center = Offset(cx, cy),
            angleRad = angle,
            length = shardLen,
            color = if (i % 2 == 0) Color.White.copy(alpha = 0.90f * alpha) else accent.copy(alpha = 0.85f * alpha)
        )
    }
}

private fun DrawScope.drawFrostTargetVfx(
    p: Float,
    alpha: Float,
    target: Offset,
    accent: Color
) {
    val ringR = size.minDimension * (0.05f + 0.22f * p)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.42f * alpha), accent.copy(alpha = 0.25f * alpha), Color.Transparent),
            center = target,
            radius = ringR * 1.4f
        ),
        radius = ringR * 1.4f,
        center = target
    )
    drawCircle(
        color = accent.copy(alpha = 0.85f * alpha),
        radius = ringR,
        center = target,
        style = Stroke(width = 3.5.dp.toPx())
    )
    for (i in 0 until 8) {
        val angle = (i * 45f - p * 35f) * (PI.toFloat() / 180f)
        val dist = size.minDimension * (0.18f * (1f - p * 0.65f))
        val pos = Offset(target.x + cos(angle) * dist, target.y + sin(angle) * dist)
        drawIceDiamond(
            center = pos,
            angleRad = angle,
            length = 11.dp.toPx(),
            color = if (i % 2 == 0) Color.White.copy(alpha = 0.92f * alpha) else accent.copy(alpha = 0.88f * alpha)
        )
    }
}

private fun DrawScope.drawArcaneProjectileVfx(
    p: Float,
    alpha: Float,
    origin: Offset,
    target: Offset,
    accent: Color
) {
    val travelP = (p / 0.45f).coerceIn(0f, 1f)
    val impactP = ((p - 0.32f) / 0.68f).coerceIn(0f, 1f)

    // Comet / beam path from origin to target
    val head = Offset(
        x = origin.x + (target.x - origin.x) * travelP,
        y = origin.y + (target.y - origin.y) * travelP
    )
    val tailP = (travelP - 0.35f).coerceIn(0f, 1f)
    val tail = Offset(
        x = origin.x + (target.x - origin.x) * tailP,
        y = origin.y + (target.y - origin.y) * tailP
    )
    val beamAlpha = (1f - impactP * 0.85f) * alpha
    if (beamAlpha > 0.02f) {
        drawLine(
            brush = Brush.linearGradient(
                colors = listOf(Color.Transparent, accent.copy(alpha = 0.85f * beamAlpha), Color.White.copy(alpha = beamAlpha)),
                start = tail,
                end = head
            ),
            start = tail,
            end = head,
            strokeWidth = 7.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(
            color = Color.White.copy(alpha = beamAlpha),
            radius = 8.dp.toPx(),
            center = head
        )
    }

    // Impact shockwave & spark burst
    if (impactP > 0f) {
        val burstR = size.minDimension * (0.04f + 0.26f * impactP)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.55f * alpha), accent.copy(alpha = 0.35f * alpha), Color.Transparent),
                center = target,
                radius = burstR * 1.35f
            ),
            radius = burstR * 1.35f,
            center = target
        )
        drawCircle(
            color = accent.copy(alpha = 0.85f * (1f - impactP * 0.5f) * alpha),
            radius = burstR,
            center = target,
            style = Stroke(width = 4.dp.toPx())
        )
        for (i in 0 until 10) {
            val angle = (i * 36f) * (PI.toFloat() / 180f)
            val innerR = burstR * 0.35f
            val outerR = burstR * 1.15f
            drawLine(
                color = if (i % 2 == 0) HextechGoldBright.copy(alpha = alpha) else accent.copy(alpha = alpha),
                start = Offset(target.x + cos(angle) * innerR, target.y + sin(angle) * innerR),
                end = Offset(target.x + cos(angle) * outerR, target.y + sin(angle) * outerR),
                strokeWidth = 2.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawTempestVfx(
    p: Float,
    alpha: Float,
    target: Offset,
    accent: Color
) {
    val radius = size.minDimension * (0.07f + 0.22f * p)
    for (i in 0 until 3) {
        val startAngle = p * 260f + i * 120f
        drawArc(
            color = if (i == 0) HextechGoldBright.copy(alpha = alpha) else accent.copy(alpha = 0.88f * alpha),
            startAngle = startAngle,
            sweepAngle = 82f,
            useCenter = false,
            topLeft = Offset(target.x - radius, target.y - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f),
            style = Stroke(width = (4.5f - i).dp.toPx(), cap = StrokeCap.Round)
        )
    }
    drawCircle(
        color = Color.White.copy(alpha = 0.5f * alpha * (1f - p * 0.4f)),
        radius = radius * 0.62f,
        center = target,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawGoldenShieldVfx(
    p: Float,
    alpha: Float,
    target: Offset,
    accent: Color,
    isClash: Boolean
) {
    val radius = size.minDimension * (0.06f + 0.20f * p)
    if (isClash) {
        val slashLen = size.minDimension * (0.14f + 0.10f * p)
        drawLine(
            color = HextechGoldBright.copy(alpha = alpha),
            start = Offset(target.x - slashLen, target.y - slashLen * 0.7f),
            end = Offset(target.x + slashLen, target.y + slashLen * 0.7f),
            strokeWidth = 4.5.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color.White.copy(alpha = alpha),
            start = Offset(target.x + slashLen, target.y - slashLen * 0.7f),
            end = Offset(target.x - slashLen, target.y + slashLen * 0.7f),
            strokeWidth = 4.5.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(HextechGold.copy(alpha = 0.35f * alpha), Color.Transparent),
            center = target,
            radius = radius * 1.4f
        ),
        radius = radius * 1.4f,
        center = target
    )
    drawCircle(
        color = accent.copy(alpha = 0.90f * alpha),
        radius = radius,
        center = target,
        style = Stroke(width = 4.dp.toPx())
    )
    drawCircle(
        color = ArcaneCyan.copy(alpha = 0.65f * alpha),
        radius = radius * 0.72f,
        center = target,
        style = Stroke(width = 2.dp.toPx())
    )
}

private fun DrawScope.drawSeismicCosmicVfx(
    p: Float,
    alpha: Float,
    target: Offset,
    accent: Color
) {
    val r1 = size.minDimension * (0.08f + 0.34f * p)
    val r2 = size.minDimension * (0.04f + 0.22f * p)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(accent.copy(alpha = 0.36f * alpha), Color.Transparent),
            center = target,
            radius = r1
        ),
        radius = r1,
        center = target
    )
    drawCircle(
        color = accent.copy(alpha = 0.82f * alpha),
        radius = r1,
        center = target,
        style = Stroke(width = 3.6.dp.toPx())
    )
    drawCircle(
        color = HextechGoldBright.copy(alpha = 0.75f * alpha),
        radius = r2,
        center = target,
        style = Stroke(width = 2.2.dp.toPx())
    )
}

private fun DrawScope.drawIceDiamond(
    center: Offset,
    angleRad: Float,
    length: Float,
    color: Color
) {
    val cosA = cos(angleRad)
    val sinA = sin(angleRad)
    val perpX = -sinA
    val perpY = cosA
    val halfW = length * 0.34f
    val path = Path().apply {
        moveTo(center.x + cosA * length, center.y + sinA * length)
        lineTo(center.x + perpX * halfW, center.y + perpY * halfW)
        lineTo(center.x - cosA * length * 0.65f, center.y - sinA * length * 0.65f)
        lineTo(center.x - perpX * halfW, center.y - perpY * halfW)
        close()
    }
    drawPath(path = path, color = color)
}
