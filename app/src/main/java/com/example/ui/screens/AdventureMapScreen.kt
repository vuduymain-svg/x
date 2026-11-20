package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GroupAdd
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.SportsKabaddi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AdventureCatalog
import com.example.model.MapNode
import com.example.model.NodeType
import com.example.model.RunState
import com.example.ui.components.LorCarvedTopBar
import com.example.ui.components.LorMetalTone
import com.example.ui.components.LorMetallicPanel
import com.example.ui.components.LorScreenBackdrop
import com.example.ui.components.RunDeckDialog
import com.example.ui.components.RunResourceHud
import com.example.ui.components.regionAccentColor
import com.example.ui.theme.AccentGold
import com.example.ui.theme.AccentIce
import com.example.ui.theme.AccentSpellMana
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CinzelFontFamily
import com.example.ui.theme.GameRadius
import com.example.ui.theme.GameSpacing
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SemanticAttack
import com.example.ui.theme.SemanticDanger
import com.example.ui.theme.SemanticHeal
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.statNumericStyle
import com.example.ui.theme.uiBodyStyle

@Composable
fun AdventureMapScreen(
    run: RunState,
    isNodeAvailable: (RunState, MapNode) -> Boolean,
    onSelectNode: (String) -> Unit,
    onAbandonRun: () -> Unit,
    onBackHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBackHome)

    var showDeck by remember { mutableStateOf(false) }
    val adventure = AdventureCatalog.getAdventure(run.adventureId)
    val regionGlow = regionAccentColor(adventure.region)
    val maxRow = adventure.mapNodes.maxOfOrNull { it.row } ?: 6
    val rowHeightDp = 104.dp
    val totalHeight = rowHeightDp * (maxRow + 1) + 40.dp

    val mapCanvasContent: @Composable (Modifier) -> Unit = { mapModifier ->
        Box(
            modifier = mapModifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
            ) {
                // Crisp Cartographic Ley-Line Paths (no neon glow strokes)
                Canvas(modifier = Modifier.fillMaxSize()) {
                    if (size.width <= 1f || size.height <= 1f) return@Canvas
                    val center = Offset(size.width * 0.5f, size.height * 0.5f)
                    drawCircle(
                        color = BorderSubtle,
                        radius = size.width * 0.42f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawCircle(
                        color = BorderSubtle,
                        radius = size.width * 0.28f,
                        center = center,
                        style = Stroke(width = 1.dp.toPx())
                    )

                    val rowSpacing = size.height / (maxRow + 1).toFloat()
                    fun nodeCenter(node: MapNode): Offset {
                        val x = when (node.column) {
                            0 -> size.width * 0.22f
                            2 -> size.width * 0.78f
                            else -> size.width * 0.50f
                        }
                        val y = rowSpacing * node.row + rowSpacing * 0.5f
                        return Offset(x, y)
                    }

                    adventure.mapNodes.forEach { fromNode ->
                        val start = nodeCenter(fromNode)
                        fromNode.nextNodeIds.forEach { targetId ->
                            val toNode = adventure.mapNodes.find { it.id == targetId }
                            if (toNode != null) {
                                val end = nodeCenter(toNode)
                                val isPathActive = run.currentNodeId == fromNode.id &&
                                    isNodeAvailable(run, toNode)
                                val isPathCompleted = run.completedNodeIds.contains(fromNode.id) &&
                                    run.completedNodeIds.contains(toNode.id)

                                val path = Path().apply {
                                    moveTo(start.x, start.y)
                                    val midY = (start.y + end.y) / 2f
                                    cubicTo(start.x, midY, end.x, midY, end.x, end.y)
                                }
                                drawPath(
                                    path = path,
                                    color = when {
                                        isPathActive -> AccentGold
                                        isPathCompleted -> SemanticHeal.copy(alpha = 0.75f)
                                        else -> BorderSubtle
                                    },
                                    style = Stroke(
                                        width = if (isPathActive) 2.5.dp.toPx() else 1.5.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                )
                            }
                        }
                    }
                }

                // Render rows top-down (row 0 Start -> row 6 Final Boss)
                Column(modifier = Modifier.fillMaxSize()) {
                    for (rowIndex in 0..maxRow) {
                        val nodesInRow = adventure.mapNodes.filter { it.row == rowIndex }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(rowHeightDp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val left = nodesInRow.find { it.column == 0 }
                            val mid = nodesInRow.find { it.column == 1 }
                            val right = nodesInRow.find { it.column == 2 }

                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (left != null) {
                                    MapNodeChip(
                                        node = left,
                                        completed = run.completedNodeIds.contains(left.id),
                                        available = isNodeAvailable(run, left),
                                        isCurrent = run.currentNodeId == left.id,
                                        onClick = { onSelectNode(left.id) }
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (mid != null) {
                                    MapNodeChip(
                                        node = mid,
                                        completed = run.completedNodeIds.contains(mid.id),
                                        available = isNodeAvailable(run, mid),
                                        isCurrent = run.currentNodeId == mid.id,
                                        onClick = { onSelectNode(mid.id) }
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                if (right != null) {
                                    MapNodeChip(
                                        node = right,
                                        completed = run.completedNodeIds.contains(right.id),
                                        available = isNodeAvailable(run, right),
                                        isCurrent = run.currentNodeId == right.id,
                                        onClick = { onSelectNode(right.id) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    LorScreenBackdrop(
        modifier = modifier,
        accentGlow = regionGlow
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter
        ) {
            if (maxWidth >= 600.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 1080.dp)
                ) {
                    LorMetallicPanel(
                        modifier = Modifier
                            .width(292.dp)
                            .fillMaxHeight()
                            .padding(GameSpacing.sm),
                        tone = LorMetalTone.GOLD,
                        cornerRadius = GameRadius.panel,
                        borderWidth = 1.dp,
                        contentPadding = PaddingValues(GameSpacing.md)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(GameSpacing.md)
                        ) {
                            LorCarvedTopBar(
                                title = adventure.name,
                                subtitle = adventure.difficultyLabel,
                                onBack = onBackHome,
                                backTestTag = "map_back_home_button",
                                backDescription = "Lưu & Về sảnh"
                            )

                            RunResourceHud(
                                run = run,
                                onOpenDeck = { showDeck = true },
                                onAbandon = onAbandonRun
                            )

                            Text(
                                text = "Chọn chặng khả dụng tiếp theo trên tuyến đường.",
                                style = uiBodyStyle(12.sp, TextSecondary)
                            )
                        }
                    }

                    mapCanvasContent(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                ) {
                    LorCarvedTopBar(
                        title = adventure.name,
                        subtitle = "${adventure.difficultyLabel} • Đi theo tuyến đường đồng thau",
                        onBack = onBackHome,
                        backTestTag = "map_back_home_button",
                        backDescription = "Lưu & Về sảnh"
                    )

                    RunResourceHud(
                        run = run,
                        onOpenDeck = { showDeck = true },
                        onAbandon = onAbandonRun,
                        modifier = Modifier.padding(horizontal = GameSpacing.md, vertical = GameSpacing.sm)
                    )

                    mapCanvasContent(Modifier.fillMaxSize())
                }
            }
        }
    }

    if (showDeck) {
        RunDeckDialog(runDeck = run.runDeck, onDismiss = { showDeck = false })
    }
}

@Composable
private fun MapNodeChip(
    node: MapNode,
    completed: Boolean,
    available: Boolean,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val accent = nodeAccent(node.type)
    val targetBorder = when {
        available -> AccentGold
        completed -> SemanticHeal
        isCurrent -> AccentIce
        else -> BorderSubtle
    }
    val animatedBorder by animateColorAsState(
        targetValue = targetBorder,
        animationSpec = tween(durationMillis = 150),
        label = "map_node_border"
    )

    LorMetallicPanel(
        modifier = Modifier
            .width(128.dp)
            .testTag("map_node_${node.id}"),
        tone = if (available) LorMetalTone.GOLD else LorMetalTone.CUSTOM,
        customBorderColor = animatedBorder,
        cornerRadius = GameRadius.card,
        borderWidth = if (available) 1.5.dp else 1.dp,
        onClick = if (available) onClick else null,
        contentPadding = PaddingValues(horizontal = GameSpacing.sm, vertical = GameSpacing.sm)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(GameSpacing.xs)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(GameSpacing.xs)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(SurfaceElevated)
                        .border(1.dp, if (completed) SemanticHeal else accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (completed) Icons.Outlined.CheckCircle else nodeIcon(node.type),
                        contentDescription = node.type.displayName,
                        tint = if (completed) SemanticHeal else accent,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = if (isCurrent) "Hiện tại" else node.type.shortCode,
                    fontFamily = PlusJakartaSansFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = if (available) AccentGold else accent
                )
            }
            Text(
                text = node.title,
                fontFamily = CinzelFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = if (available || completed) TextPrimary else TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (node.type == NodeType.COMBAT || node.type == NodeType.ELITE_COMBAT ||
                node.type == NodeType.MID_BOSS || node.type == NodeType.FINAL_BOSS
            ) {
                Text(
                    text = "${node.enemyMaxHp} Máu • +${node.goldReward} Vàng",
                    style = statNumericStyle(10.sp, if (available) AccentGold else TextSecondary),
                    maxLines = 1
                )
            } else {
                Text(
                    text = node.subtitle,
                    style = uiBodyStyle(10.sp, TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun nodeIcon(type: NodeType): ImageVector = when (type) {
    NodeType.START -> Icons.Outlined.Flag
    NodeType.COMBAT -> Icons.Outlined.SportsKabaddi
    NodeType.ELITE_COMBAT -> Icons.Outlined.LocalFireDepartment
    NodeType.MID_BOSS, NodeType.FINAL_BOSS -> Icons.Outlined.LocalFireDepartment
    NodeType.POWER_NODE -> Icons.Outlined.Bolt
    NodeType.HEALER_NODE -> Icons.Outlined.FavoriteBorder
    NodeType.SHOP_NODE -> Icons.Outlined.ShoppingBag
    NodeType.REINFORCEMENT_NODE -> Icons.Outlined.GroupAdd
    NodeType.ITEM_CHEST -> Icons.Outlined.Inventory2
    NodeType.NARRATIVE_EVENT -> Icons.AutoMirrored.Outlined.HelpOutline
}

private fun nodeAccent(type: NodeType): Color = when (type) {
    NodeType.START -> AccentIce
    NodeType.COMBAT -> SemanticAttack
    NodeType.ELITE_COMBAT, NodeType.MID_BOSS, NodeType.FINAL_BOSS -> SemanticDanger
    NodeType.POWER_NODE -> AccentSpellMana
    NodeType.HEALER_NODE -> SemanticHeal
    NodeType.SHOP_NODE -> AccentGold
    NodeType.REINFORCEMENT_NODE -> AccentIce
    NodeType.ITEM_CHEST -> AccentSpellMana
    NodeType.NARRATIVE_EVENT -> AccentGold
}
