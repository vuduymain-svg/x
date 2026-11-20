package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.screens.AdventureMapScreen
import com.example.ui.screens.AdventureSelectScreen
import com.example.ui.screens.CardDraftScreen
import com.example.ui.screens.ChampionDetailScreen
import com.example.ui.screens.CombatScreen
import com.example.ui.screens.HealerScreen
import com.example.ui.screens.HomeHubScreen
import com.example.ui.screens.ItemChestScreen
import com.example.ui.screens.NarrativeEventScreen
import com.example.ui.screens.PowerDraftScreen
import com.example.ui.screens.ReinforcementScreen
import com.example.ui.screens.RunSummaryScreen
import com.example.ui.screens.ShopScreen
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PathOfChampionsTheme
import com.example.viewmodel.AppScreen
import com.example.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        GameViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.example.model.CardCatalog.ensureLocalized(applicationContext)
        enableEdgeToEdge()
        setContent {
            PathOfChampionsTheme {
                PathOfChampionsApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PathOfChampionsApp(
    viewModel: GameViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.bannerMessage) {
        val msg = uiState.bannerMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        val isCombatScreen = uiState.screen is AppScreen.Combat
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isCombatScreen) Modifier else Modifier.padding(innerPadding))
        ) {
            when (val screen = uiState.screen) {
                AppScreen.HomeHub -> {
                    HomeHubScreen(
                        uiState = uiState,
                        onSelectChampion = viewModel::selectChampion,
                        onUnlockChampion = viewModel::unlockChampion,
                        onOpenChampionDetail = viewModel::openChampionDetail,
                        onOpenAdventures = viewModel::openAdventureSelect,
                        onQuickBattle = viewModel::quickStartBattle,
                        onResumeRun = viewModel::resumeSavedRun,
                        onAbandonRun = viewModel::abandonSavedRun,
                        onGrantUnlimitedTestResources = viewModel::grantUnlimitedTestResources
                    )
                }

                is AppScreen.ChampionDetail -> {
                    ChampionDetailScreen(
                        championId = screen.championId,
                        uiState = uiState,
                        onUpgradeStar = viewModel::upgradeStarPower,
                        onUnlockConstellation = viewModel::unlockConstellationNode,
                        onEquipRelic = viewModel::equipRelic,
                        onCosmicForge = viewModel::cosmicForgeSlot,
                        onMaxChampionForTesting = viewModel::maxChampionForTesting,
                        onBack = viewModel::navigateHome
                    )
                }

                AppScreen.AdventureSelect -> {
                    AdventureSelectScreen(
                        uiState = uiState,
                        onStartAdventure = viewModel::startAdventure,
                        onBack = viewModel::navigateHome
                    )
                }

                AppScreen.AdventureMap -> {
                    val run = uiState.activeRun
                    if (run != null) {
                        AdventureMapScreen(
                            run = run,
                            isNodeAvailable = viewModel::isNodeAvailable,
                            onSelectNode = viewModel::selectMapNode,
                            onAbandonRun = viewModel::abandonSavedRun,
                            onBackHome = viewModel::navigateHome
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

                AppScreen.Combat -> {
                    val combat = uiState.combatState
                    val run = uiState.activeRun
                    if (combat != null && run != null) {
                        CombatScreen(
                            combat = combat,
                            revivesRemaining = run.revivesRemaining,
                            onPlayCard = { cardId -> viewModel.playCard(cardId) },
                            onPlayTargetedCard = viewModel::playCard,
                            onEnterAttack = viewModel::enterAttack,
                            onCancelAttack = viewModel::cancelAttack,
                            onToggleAttacker = viewModel::toggleAttacker,
                            onSelectChallengerAttacker = viewModel::selectChallengerAttacker,
                            onChallengeEnemyUnit = viewModel::challengeEnemyUnit,
                            onClearChallenge = viewModel::clearChallenge,
                            onDragBenchUnitToAttack = viewModel::dragBenchUnitToAttack,
                            onRemoveAttacker = viewModel::removeAttacker,
                            onCommitAttack = viewModel::commitAttack,
                            onSelectEnemyAttackerForBlock = viewModel::selectEnemyAttackerForBlock,
                            onAssignBlocker = viewModel::assignBlocker,
                            onRemoveBlocker = viewModel::removeBlocker,
                            onCommitBlock = viewModel::commitBlock,
                            onResolveStack = viewModel::resolveSpellStack,
                            onPassTurn = viewModel::passTurn,
                            onVictoryContinue = viewModel::finishCombatVictory,
                            onUseRevive = viewModel::useReviveAndRetry,
                            onAcceptDefeat = viewModel::acceptDefeat,
                            onAbandonRun = viewModel::abandonSavedRun
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

                AppScreen.CardDraft -> {
                    val run = uiState.activeRun
                    if (run != null) {
                        CardDraftScreen(
                            run = run,
                            offers = uiState.cardDraftOptions,
                            onPick = viewModel::pickCardReward,
                            onReroll = viewModel::rerollCardDraft,
                            onSkip = viewModel::skipCardDraft
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

                AppScreen.PowerDraft -> {
                    val run = uiState.activeRun
                    if (run != null) {
                        PowerDraftScreen(
                            run = run,
                            powers = uiState.powerDraftOptions,
                            onPick = viewModel::pickPowerReward,
                            onReroll = viewModel::rerollPowerDraft
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

                AppScreen.Shop -> {
                    ShopScreen(
                        uiState = uiState,
                        onBuyCard = viewModel::buyShopCard,
                        onSelectItem = viewModel::selectShopItemToAttach,
                        onAttachItemToCard = viewModel::attachPendingShopItemToDeckCard,
                        onBuyPower = viewModel::buyShopPower,
                        onLeave = viewModel::leaveShop
                    )
                }

                AppScreen.Healer -> {
                    val run = uiState.activeRun
                    if (run != null) {
                        HealerScreen(
                            run = run,
                            onHeal = viewModel::healAtHealer,
                            onCutCard = viewModel::cutCardAtHealer
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

                AppScreen.Reinforcement -> {
                    val run = uiState.activeRun
                    if (run != null) {
                        ReinforcementScreen(
                            run = run,
                            packages = uiState.reinforcementOptions,
                            onPick = viewModel::pickReinforcementPackage
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

                AppScreen.ItemChest -> {
                    ItemChestScreen(
                        uiState = uiState,
                        onAttachToCard = viewModel::applyChestItemToCard
                    )
                }

                AppScreen.NarrativeEvent -> {
                    NarrativeEventScreen(
                        uiState = uiState,
                        onChooseOption = viewModel::chooseNarrativeOption
                    )
                }

                AppScreen.RunSummary -> {
                    val summary = uiState.runRewardsSummary
                    if (summary != null) {
                        RunSummaryScreen(
                            summary = summary,
                            onDone = viewModel::navigateHome
                        )
                    } else {
                        LaunchedEffect(Unit) { viewModel.navigateHome() }
                    }
                }

            }
        }
    }
}
