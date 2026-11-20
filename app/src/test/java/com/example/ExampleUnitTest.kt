package com.example

import com.example.data.local.dao.GameDao
import com.example.data.local.entity.ChampionLoadoutEntity
import com.example.data.local.entity.PlayerMetaEntity
import com.example.data.local.entity.SavedRunEntity
import com.example.data.repository.GameRepository
import com.example.engine.CombatEngine
import com.example.engine.CombatPhase
import com.example.engine.CombatSimulator
import com.example.engine.CombatState
import com.example.engine.EnemyAIController
import com.example.engine.PriorityHolder
import com.example.engine.SpellStackEntry
import com.example.model.AdventureCatalog
import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.ChampionLoadout
import com.example.model.DeckCardEntry
import com.example.model.HandCardInstance
import com.example.model.ItemCategory
import com.example.model.ItemTemplate
import com.example.model.Keyword
import com.example.model.LocalizationRepository
import com.example.model.NodeType
import com.example.model.Rarity
import com.example.model.Region
import com.example.model.RunState
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard
import com.example.ui.components.cardPortraitRes
import com.example.viewmodel.AppScreen
import com.example.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private class FakeGameDao : GameDao {
    private val loadouts = MutableStateFlow<List<ChampionLoadoutEntity>>(emptyList())
    private val meta = MutableStateFlow<PlayerMetaEntity?>(null)
    private val savedRun = MutableStateFlow<SavedRunEntity?>(null)

    override fun observeChampionLoadouts(): Flow<List<ChampionLoadoutEntity>> = loadouts
    override suspend fun getChampionLoadout(id: String): ChampionLoadoutEntity? =
        loadouts.value.find { it.championId == id }

    override suspend fun upsertChampionLoadout(entity: ChampionLoadoutEntity) {
        loadouts.value = loadouts.value.filterNot { it.championId == entity.championId } + entity
    }

    override suspend fun upsertAllChampionLoadouts(entities: List<ChampionLoadoutEntity>) {
        val ids = entities.map { it.championId }.toSet()
        loadouts.value = loadouts.value.filterNot { ids.contains(it.championId) } + entities
    }

    override fun observePlayerMeta(): Flow<PlayerMetaEntity?> = meta
    override suspend fun getPlayerMeta(): PlayerMetaEntity? = meta.value
    override suspend fun upsertPlayerMeta(entity: PlayerMetaEntity) {
        meta.value = entity
    }

    override suspend fun deleteOtherChampionLoadouts(keepId: String) {
        loadouts.value = loadouts.value.filter { it.championId == keepId }
    }

    override fun observeSavedRun(): Flow<SavedRunEntity?> = savedRun
    override suspend fun getSavedRun(): SavedRunEntity? = savedRun.value
    override suspend fun upsertSavedRun(entity: SavedRunEntity) {
        savedRun.value = entity
    }

    override suspend fun clearSavedRun() {
        savedRun.value = null
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExampleUnitTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combat engine enforces keyword blocking rules for Elusive Fearsome and Brash`() {
        val elusiveAttacker = UnitOnBoard(
            instanceId = "a1",
            template = CardCatalog.getCard("card_sumpworks_mapper"),
            ownerIsPlayer = true,
            power = 1,
            health = 1,
            maxHealth = 1,
            keywords = setOf(Keyword.ELUSIVE)
        )
        val normalBlocker = UnitOnBoard(
            instanceId = "b1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )
        val sharpsightBlocker = UnitOnBoard(
            instanceId = "b2",
            template = CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.SHARPSIGHT)
        )
        val brashAttacker = UnitOnBoard(
            instanceId = "a2",
            template = CardCatalog.getCard("card_ashe"),
            ownerIsPlayer = true,
            power = 5,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.BRASH)
        )

        assertFalse(normalBlocker.canBlockAttacker(elusiveAttacker))
        assertTrue(sharpsightBlocker.canBlockAttacker(elusiveAttacker))
        // Brash requires 3+ Health to block
        assertFalse(normalBlocker.canBlockAttacker(brashAttacker))
    }

    @Test
    fun `combat engine resolves Overwhelm Quick Attack and Deathless accurately`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val attacker = UnitOnBoard(
            instanceId = "atk_1",
            template = CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = true,
            power = 5,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.OVERWHELM, Keyword.QUICK_ATTACK)
        )
        val deathlessDefender = UnitOnBoard(
            instanceId = "def_1",
            template = CardCatalog.getCard("card_rekindled_knight"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.DEATHLESS),
            hasDeathless = true
        )

        val initialState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerBoard = listOf(attacker),
            enemyBoard = listOf(deathlessDefender),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("atk_1")
        )

        val afterDeclare = CombatEngine.commitPlayerAttack(initialState)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, afterDeclare.phase)
        assertEquals(PriorityHolder.ENEMY, afterDeclare.priorityHolder)

        val afterBlock = EnemyAIController.executeTurn(afterDeclare)
        assertTrue(afterBlock.blockCommitted)
        assertEquals(PriorityHolder.PLAYER, afterBlock.priorityHolder)

        val afterAttack = CombatEngine.passPriority(afterBlock)
        // Quick Attack kills defender before return strike -> attacker unharmed
        assertEquals(3, afterAttack.playerBoard.first().health)
        // Overwhelm deals 5 - 2 = 3 excess damage to enemy Nexus (15 -> 12)
        assertEquals(12, afterAttack.enemyNexusHp)
        // Deathless revives defender with 1 HP and Stunned
        assertEquals(1, afterAttack.enemyBoard.size)
        assertEquals(1, afterAttack.enemyBoard.first().health)
        assertTrue(afterAttack.enemyBoard.first().isStunned)
        assertFalse(afterAttack.enemyBoard.first().hasDeathless)
    }

    @Test
    fun `playing Burst spell with Spellslinger and Suppression calculates effective cost and resolves immediately`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val burstCard = HandCardInstance(
            instanceId = "hc_1",
            template = CardCatalog.getCard("card_prismatic_barrier"), // base cost 2
            effectiveCost = 2
        )
        val ally = UnitOnBoard(
            instanceId = "u_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = listOf(CardCatalog.getPower("pow_spellslinger")),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 1,
            playerSpellMana = 0,
            playerHand = listOf(burstCard),
            playerBoard = listOf(ally)
        )

        assertEquals(1, state.effectivePlayCost(burstCard))
        assertTrue(state.canPlayCard(burstCard))

        val afterPlay = CombatEngine.playCardFromHand(state, "hc_1")
        assertEquals(0, afterPlay.playerMana)
        assertTrue(afterPlay.playerBoard.first().hasBarrier)
        // Lux 1★ grants +1|+1 when casting a spell
        assertEquals(3, afterPlay.playerBoard.first().power)
        assertEquals(3, afterPlay.playerBoard.first().health)
    }

    @Test
    fun `viewModel manages adventure launch DAG node progression and healer restoration`() = runTest {
        val repo = GameRepository(FakeGameDao(), testDispatcher)
        repo.ensureSeeded()
        val vm = GameViewModel(repo)
        advanceUntilIdle()

        vm.startAdventure("adv_demacia_outskirts")
        advanceUntilIdle()

        val run = vm.uiState.value.activeRun
        assertNotNull(run)
        assertEquals(AppScreen.AdventureMap, vm.uiState.value.screen)

        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val powerNode = adv.mapNodes.first { it.id == "d_power_1" }
        assertTrue(vm.isNodeAvailable(run!!, powerNode))

        // Select Power Shrine
        vm.selectMapNode("d_power_1")
        assertEquals(AppScreen.PowerDraft, vm.uiState.value.screen)
        assertEquals(3, vm.uiState.value.powerDraftOptions.size)

        val chosenPower = vm.uiState.value.powerDraftOptions.first()
        vm.pickPowerReward(chosenPower)
        advanceUntilIdle()

        val updatedRun = vm.uiState.value.activeRun!!
        assertTrue(updatedRun.activePowerIds.contains(chosenPower.id))
        assertTrue(updatedRun.completedNodeIds.contains("d_power_1"))
        assertEquals(AppScreen.AdventureMap, vm.uiState.value.screen)
        // Branching nodes d_fight_1a and d_fight_1b both become available
        assertTrue(vm.isNodeAvailable(updatedRun, adv.mapNodes.first { it.id == "d_fight_1a" }))
        assertTrue(vm.isNodeAvailable(updatedRun, adv.mapNodes.first { it.id == "d_fight_1b" }))
    }

    @Test
    fun `playing unit passes priority to enemy and Burst spell keeps priority on player`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val unitCard = HandCardInstance(
            instanceId = "hc_unit",
            template = CardCatalog.getCard("card_cithria"), // 1-cost unit
            effectiveCost = 1
        )
        val burstCard = HandCardInstance(
            instanceId = "hc_burst",
            template = CardCatalog.getCard("card_prismatic_barrier"), // 2-cost Burst
            effectiveCost = 2
        )
        val enemyUnit = CardCatalog.getCard("card_cithria")
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 3,
            enemyMana = 2,
            playerHand = listOf(burstCard, unitCard),
            enemyHand = listOf(enemyUnit),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Burst spell resolves immediately and keeps priority on PLAYER
        val afterBurst = CombatEngine.playCardFromHand(state, "hc_burst")
        assertEquals(PriorityHolder.PLAYER, afterBurst.priorityHolder)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, afterBurst.phase)

        // Playing a unit summons it and switches priority to ENEMY
        val afterUnit = CombatEngine.playCardFromHand(afterBurst, "hc_unit")
        assertEquals(1, afterUnit.playerBoard.size)
        assertEquals(PriorityHolder.ENEMY, afterUnit.priorityHolder)

        // EnemyAIController plays its unit and returns priority to PLAYER
        val afterAi = EnemyAIController.executeTurn(afterUnit)
        assertEquals(1, afterAi.enemyBoard.size)
        assertEquals(PriorityHolder.PLAYER, afterAi.priorityHolder)
    }

    @Test
    fun `fast spell enters SPELL_STACK_PHASE and resolves LIFO when opponent passes`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val fastShot = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"), // 2-cost Fast, deal 2
            effectiveCost = 2
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 2,
            enemyMana = 1,
            playerHand = listOf(fastShot),
            enemyHand = emptyList(),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        val onStack = CombatEngine.playCardFromHand(state, "hc_fast")
        assertEquals(CombatPhase.SPELL_STACK_PHASE, onStack.phase)
        assertEquals(PriorityHolder.ENEMY, onStack.priorityHolder)
        assertEquals(1, onStack.spellStack.size)
        assertEquals(PriorityHolder.PLAYER, onStack.lastStackCaster)

        // AI has no reactive Fast spell -> AI passes to resolve Spell Stack LIFO
        val resolved = EnemyAIController.executeTurn(onStack)
        assertTrue(resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, resolved.phase)
        // Priority returns to opponent of lastStackCaster (PLAYER -> ENEMY)
        assertEquals(PriorityHolder.ENEMY, resolved.priorityHolder)
        // Jinx 1★ dealt 1 on play + Mystic Shot dealt 2 = 3 damage total (15 -> 12)
        assertEquals(12, resolved.enemyNexusHp)
    }

    @Test
    fun `consecutive passes advance round bank spell mana draw cards and swap attack token`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val drawCard = HandCardInstance(
            instanceId = "hc_draw",
            template = CardCatalog.getCard("card_cithria"),
            effectiveCost = 1
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 4, // up to 3 unspent Unit Mana banks into Spell Mana
            playerMaxMana = 4,
            playerSpellMana = 0,
            enemyMana = 1,
            enemyMaxMana = 1,
            enemySpellMana = 0,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            playerDrawPile = listOf(drawCard),
            enemyDrawPool = listOf(CardCatalog.getCard("card_cithria")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0
        )

        val afterPlayerPass = CombatEngine.passPriority(state)
        assertEquals(1, afterPlayerPass.consecutivePasses)
        assertEquals(PriorityHolder.ENEMY, afterPlayerPass.priorityHolder)

        // Enemy has no cards in hand -> AI passes (consecutivePasses == 2 -> Round 2)
        val nextRound = EnemyAIController.executeTurn(afterPlayerPass)
        assertEquals(2, nextRound.round)
        assertEquals(5, nextRound.playerMana)
        assertEquals(5, nextRound.playerMaxMana)
        assertEquals(3, nextRound.playerSpellMana)
        assertEquals(1, nextRound.enemySpellMana)
        assertFalse(nextRound.playerHasAttackToken)
        assertTrue(nextRound.enemyHasAttackToken)
        assertEquals(PriorityHolder.ENEMY, nextRound.priorityHolder)
        assertEquals(1, nextRound.playerHand.size)
        assertEquals(1, nextRound.enemyHand.size)
    }

    @Test
    fun `viewModel automatically triggers EnemyAIController after 700ms delay when priority shifts to enemy`() = runTest(testDispatcher) {
        val repo = GameRepository(FakeGameDao(), testDispatcher)
        repo.ensureSeeded()
        val vm = GameViewModel(repo, aiStepDelayMs = 700L)
        advanceUntilIdle()

        vm.startAdventure("adv_demacia_outskirts")
        advanceUntilIdle()
        vm.selectMapNode("d_power_1")
        vm.pickPowerReward(vm.uiState.value.powerDraftOptions.first())
        advanceUntilIdle()

        vm.selectMapNode("d_fight_1a")
        runCurrent()

        val startCombat = vm.uiState.value.combatState
        assertNotNull(startCombat)
        assertEquals(PriorityHolder.PLAYER, startCombat!!.priorityHolder)

        // Pass priority -> ENEMY gets priority; AI should NOT execute before 700ms
        vm.passTurn()
        runCurrent()
        val afterPass = vm.uiState.value.combatState!!
        assertEquals(PriorityHolder.ENEMY, afterPass.priorityHolder)

        advanceTimeBy(699L)
        runCurrent()
        assertEquals(afterPass, vm.uiState.value.combatState)

        // At 700ms, EnemyAIController executes automatically (summons 1-cost unit -> PLAYER, or passes -> Round 2)
        advanceTimeBy(2L)
        runCurrent()
        val afterAi = vm.uiState.value.combatState!!
        assertTrue(afterAi != afterPass)
        assertTrue(afterAi.priorityHolder == PriorityHolder.PLAYER || afterAi.round == 2)

        // Once AI finishes any Round 2 opening action, priority returns to PLAYER
        advanceUntilIdle()
        assertEquals(PriorityHolder.PLAYER, vm.uiState.value.combatState!!.priorityHolder)
    }

    @Test
    fun `fast spell during attack declaration resolves stack via AI and returns priority to PLAYER`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val ally = UnitOnBoard(
            instanceId = "p1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val fastShot = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 2,
            enemyMana = 1,
            playerHasAttackToken = true,
            playerBoard = listOf(ally),
            playerHand = listOf(fastShot),
            enemyHand = emptyList(),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p1"),
            priorityHolder = PriorityHolder.PLAYER
        )

        val onStack = CombatEngine.playCardFromHand(state, "hc_fast")
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, onStack.phase)
        assertEquals(PriorityHolder.ENEMY, onStack.priorityHolder)
        assertEquals(1, onStack.spellStack.size)

        val resolved = EnemyAIController.executeTurn(onStack)
        assertTrue(resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, resolved.phase)
        assertEquals(PriorityHolder.PLAYER, resolved.priorityHolder)
        assertEquals(13, resolved.enemyNexusHp)
    }

    @Test
    fun `fast spell while defending resolves stack and keeps PLAYER in COMBAT_DECLARE_BLOCK_PHASE`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val enemyAtk = UnitOnBoard(
            instanceId = "e1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val fastShot = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 2,
            enemyMana = 1,
            playerHasAttackToken = false,
            enemyHasAttackToken = false,
            playerInitiatedCombat = false,
            enemyBoard = listOf(enemyAtk),
            attackingUnitIds = listOf("e1"),
            playerHand = listOf(fastShot),
            enemyHand = emptyList(),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        val onStack = CombatEngine.playCardFromHand(state, "hc_fast")
        assertEquals(PriorityHolder.ENEMY, onStack.priorityHolder)

        val resolved = EnemyAIController.executeTurn(onStack)
        assertTrue(resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, resolved.phase)
        assertEquals(PriorityHolder.PLAYER, resolved.priorityHolder)
        assertTrue(resolved.enemyBoard.isEmpty())
        assertEquals(30, resolved.playerNexusHp)
    }

    @Test
    fun `enemy node powers Stone Ward For Demacia Enlightened Harmony Living Shadow and Glorious Evolution work`() {
        // 1. Stone Ward adds +5 Max HP
        val demacia = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val stoneWardNode = demacia.mapNodes.first { it.id == "d_fight_1b" }
        val baseRun = RunState(
            runId = "run_1",
            adventureId = demacia.id,
            championId = "champ_garen",
            currentNodeId = "d_fight_1b",
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 100,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = listOf(DeckCardEntry("card_cithria", 4))
        )
        val loadout = ChampionLoadout("champ_garen", starLevel = 1)
        val stoneEncounter = CombatEngine.createEncounter(baseRun, loadout, demacia, stoneWardNode)
        assertEquals(stoneWardNode.enemyMaxHp + 5, stoneEncounter.enemyMaxHp)
        assertEquals(stoneWardNode.enemyMaxHp + 5, stoneEncounter.enemyNexusHp)

        // 2. For Demacia! buffs enemy board +1|+1 on even rounds
        val bossNode = demacia.mapNodes.first { it.id == "d_boss" }
        val garenEnemy = UnitOnBoard(
            instanceId = "eg_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val round1BossState = CombatState(
            encounterNode = bossNode,
            adventure = demacia,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 28,
            enemyMaxHp = 28,
            enemyBoard = listOf(garenEnemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )
        val round2BossState = CombatEngine.passPriority(round1BossState)
        assertEquals(2, round2BossState.round)
        assertEquals(3, round2BossState.enemyBoard.first().power)
        assertEquals(3, round2BossState.enemyBoard.first().health)

        // 3. Enlightened Harmony heals Karma Nexus +1 when enemy casts spell
        val ionia = AdventureCatalog.getAdventure("adv_ionia_woods")
        val karmaBoss = ionia.mapNodes.first { it.id == "i_boss" }
        val karmaState = CombatState(
            encounterNode = karmaBoss,
            adventure = ionia,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 30,
            enemyMaxHp = 35,
            enemyMana = 4,
            playerBoard = listOf(
                UnitOnBoard("p_1", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())
            ),
            enemyHand = listOf(CardCatalog.getCard("card_mystic_shot")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterKarmaCast = EnemyAIController.executeTurn(karmaState)
        assertEquals(31, afterKarmaCast.enemyNexusHp)

        // 4. Glorious Evolution grants +1|+1 and random keyword on enemy summon
        val zaun = AdventureCatalog.getAdventure("adv_zaun_foundry")
        val viktorBoss = zaun.mapNodes.first { it.id == "z_boss" }
        val viktorState = CombatState(
            encounterNode = viktorBoss,
            adventure = zaun,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 45,
            enemyMaxHp = 45,
            enemyMana = 3,
            enemyHand = listOf(CardCatalog.getCard("card_cithria")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterViktorSummon = EnemyAIController.executeTurn(viktorState)
        val summoned = afterViktorSummon.enemyBoard.first()
        // Base Cithria is 2|2, +1|+1 from 3.0★ adventure, +1|+1 from Glorious Evolution -> 4|4 + extra keyword
        assertEquals(4, summoned.power)
        assertEquals(4, summoned.health)
        assertTrue(summoned.keywords.size >= 2)
    }

    @Test
    fun `wild inspiration discounts created cards concussive palm summons tail and jinx level up creates rocket`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        // Wild Inspiration discounts created card by 1
        val createdSpell = HandCardInstance(
            instanceId = "hc_created",
            template = CardCatalog.getCard("card_concussive_palm"), // cost 4
            effectiveCost = 4,
            isCreated = true
        )
        val jinxBoard = UnitOnBoard(
            instanceId = "u_jinx",
            template = CardCatalog.getCard("card_jinx"),
            ownerIsPlayer = true,
            power = 4,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.QUICK_ATTACK)
        )
        val enemyUnit = UnitOnBoard(
            instanceId = "e_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_jinx",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = listOf(CardCatalog.getPower("pow_wild_inspiration")),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 3,
            enemyMana = 1,
            playerHand = listOf(createdSpell),
            playerBoard = listOf(jinxBoard),
            enemyBoard = listOf(enemyUnit),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        assertEquals(3, state.effectivePlayCost(createdSpell))
        assertTrue(state.canPlayCard(createdSpell))

        // Playing Concussive Palm empties hand -> Jinx levels up (+4 Nexus dmg & creates Fleeting Super Mega Death Rocket!)
        val onStack = CombatEngine.playCardFromHand(state, "hc_created")
        val resolved = EnemyAIController.executeTurn(onStack)

        assertTrue(resolved.enemyBoard.first().isStunned)
        assertTrue(resolved.playerBoard.any { it.template.id == "card_tail_of_the_dragon" })
        assertTrue(resolved.playerBoard.first { it.template.id == "card_jinx" }.isLeveledUp)
        assertTrue(resolved.playerHand.any { it.template.id == "card_super_mega_rocket" && it.isCreated })
        // Enemy Nexus: 15 - 1 (Jinx 1★ on play) - 4 (Jinx Level Up) = 10
        assertEquals(10, resolved.enemyNexusHp)
    }

    @Test
    fun `slain units and resolved spells move to graveyard while survivors return to bench`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val strongAlly = UnitOnBoard(
            instanceId = "p_strong",
            template = CardCatalog.getCard("card_garen"), // 5|5
            ownerIsPlayer = true,
            power = 5,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.REGENERATION)
        )
        val fragileEnemy = UnitOnBoard(
            instanceId = "e_fragile",
            template = CardCatalog.getCard("card_cithria"), // 2|2
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val burstSpell = HandCardInstance(
            instanceId = "hc_burst",
            template = CardCatalog.getCard("card_prismatic_barrier"),
            effectiveCost = 2
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 15,
            enemyMaxHp = 15,
            playerMana = 4,
            enemyMana = 1,
            playerHasAttackToken = true,
            playerHand = listOf(burstSpell),
            playerBoard = listOf(strongAlly),
            enemyBoard = listOf(fragileEnemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // 1. Cast Burst spell -> effect activates -> spell goes to playerGraveyard
        state = CombatEngine.playCardFromHand(state, "hc_burst")
        assertTrue(state.playerGraveyard.any { it.id == "card_prismatic_barrier" })

        // 2. Move strongAlly from bench into Shared Battlefield attack & resolve block
        state = CombatEngine.enterAttackDeclaration(state)
        state = CombatEngine.toggleUnitAttack(state, "p_strong")
        assertEquals(listOf("p_strong"), state.attackingUnitIds)

        state = CombatEngine.commitPlayerAttack(state)
        state = state.copy(
            priorityHolder = PriorityHolder.ENEMY,
            blockPairings = mapOf("p_strong" to "e_fragile")
        )
        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = state.combatLog.toMutableList())

        // Slain enemy enters enemyGraveyard; surviving ally returns to bench (attackingUnitIds cleared)
        assertTrue(state.enemyBoard.isEmpty())
        assertTrue(state.enemyGraveyard.any { it.id == "card_cithria" })
        assertEquals(1, state.playerBoard.count { it.instanceId == "p_strong" })
    }

    @Test
    fun `anivia lv30 baseline stats starter deck and unlimited test resources match spec`() = runTest {
        val anivia = CardCatalog.getChampion("champ_anivia")
        assertEquals(9, anivia.baseDeck.size)
        assertEquals(18, anivia.baseDeck.sumOf { it.copies })
        assertEquals(listOf(2, 3, 4, 5, 6), anivia.starPowers.map { it.starLevel })

        val lv30Baseline = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 2,
            championLevel = 30
        )
        assertEquals(40, lv30Baseline.maxNexusHp)
        assertEquals(4, lv30Baseline.victoryHeal)
        assertEquals(300, lv30Baseline.startingGold)
        assertEquals(4, lv30Baseline.startingRerolls)
        assertEquals(1, lv30Baseline.startingManaBonus)
        assertEquals(0.445, lv30Baseline.rarityOdds.common, 0.0001)
        assertEquals(0.400, lv30Baseline.rarityOdds.rare, 0.0001)
        assertEquals(0.125, lv30Baseline.rarityOdds.epic, 0.0001)
        assertEquals(0.030, lv30Baseline.rarityOdds.legendary, 0.0001)

        val repo = GameRepository(FakeGameDao(), testDispatcher)
        repo.ensureSeeded()
        val vm = GameViewModel(repo)
        advanceUntilIdle()

        assertEquals(999_999, vm.uiState.value.legendProfile.wildFragments)
        assertEquals(999_999, vm.uiState.value.legendProfile.starCrystals)
        assertEquals(999_999, vm.uiState.value.legendProfile.novaCrystals)
        assertEquals(999_999, vm.uiState.value.legendProfile.gemstones)
        assertTrue(vm.uiState.value.legendProfile.ownedRelicIds.containsAll(CardCatalog.relics.map { it.id }))

        val seededAnivia = vm.uiState.value.championLoadouts["champ_anivia"]!!
        assertTrue(seededAnivia.isUnlocked)
        assertEquals(30, seededAnivia.championLevel)

        // Upgrade Anivia star power sequentially skipping omitted 1★ (2 -> 3)
        vm.upgradeStarPower("champ_anivia", 3)
        advanceUntilIdle()
        assertEquals(3, vm.uiState.value.championLoadouts["champ_anivia"]!!.starLevel)

        // Test Mode max helper jumps to 6★ Lv.30 + all constellation nodes
        vm.maxChampionForTesting("champ_anivia")
        advanceUntilIdle()
        val maxed = vm.uiState.value.championLoadouts["champ_anivia"]!!
        assertEquals(6, maxed.starLevel)
        assertEquals(30, maxed.championLevel)
        assertEquals(anivia.constellationNodes.size, maxed.unlockedConstellationNodes.size)
    }

    @Test
    fun `anivia elemental mana ramp ho nhan summon damage wild mystics arrow and clash of giants discount work`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val caprineCard = HandCardInstance(
            instanceId = "hc_cap",
            template = CardCatalog.getCard("card_flame_eyed_caprine"), // 3|2 Tough
            effectiveCost = 4
        )
        val mysticsCard = HandCardInstance(
            instanceId = "hc_mys",
            template = CardCatalog.getCard("card_wild_mystics"), // 2|2 Overwhelm
            effectiveCost = 2
        )
        val harshWinds = HandCardInstance(
            instanceId = "hc_hw",
            template = CardCatalog.getCard("card_harsh_winds"), // 5-cost Burst Elemental + draw 1
            effectiveCost = 5
        )
        val clash = HandCardInstance(
            instanceId = "hc_clash",
            template = CardCatalog.getCard("card_clash_of_giants"), // 13-cost Slow Elemental
            effectiveCost = 13
        )
        val enemyA = UnitOnBoard("e1", CardCatalog.getCard("card_cithria"), false, 5, 4, 4, emptySet())
        val enemyB = UnitOnBoard("e2", CardCatalog.getCard("card_laurent_protege"), false, 3, 4, 4, emptySet())

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 3,
            equippedRelics = listOf(CardCatalog.getRelic("relic_ludens_tempest")),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 9,
            playerMaxMana = 9,
            playerHand = listOf(mysticsCard, caprineCard, harshWinds, clash),
            playerDrawPile = listOf(HandCardInstance("draw_1", CardCatalog.getCard("card_cithria"), effectiveCost = 1)),
            enemyBoard = listOf(enemyA, enemyB),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Playing Wild Mystics creates Tên Đóng Băng in hand
        state = CombatEngine.playCardFromHand(state, "hc_mys")
        assertTrue(state.playerHand.any { it.template.id == "card_frozen_arrow" })
        val enemyTurnPassed = state.copy(priorityHolder = PriorityHolder.PLAYER)

        // Playing Hỏa Nhân deals 1 + 1 (Luden) = 2 to Enemy Nexus and all enemy units
        state = CombatEngine.playCardFromHand(enemyTurnPassed, "hc_cap")
        assertEquals(18, state.enemyNexusHp)
        assertEquals(2, state.enemyBoard.first { it.instanceId == "e1" }.health)
        assertEquals(2, state.enemyBoard.first { it.instanceId == "e2" }.health)

        // Playing Harsh Winds at 9 Max Mana:
        // - Frostbites 2 enemies + draws 1 card
        // - Winter's Boon II grants +1 empty Mana gem (Max 9 -> 10)
        // - Clash of Giants drops cost 13 -> 12
        state = state.copy(playerMana = 5, priorityHolder = PriorityHolder.PLAYER)
        state = CombatEngine.playCardFromHand(state, "hc_hw")
        assertEquals(10, state.playerMaxMana)
        assertFalse(state.isEnlightened) // Enlightened is checked strictly at Round Start when regularMana >= 10
        assertTrue(state.enemyBoard.all { it.isFrostbitten })
        assertTrue(state.playerHand.any { it.instanceId == "draw_1" })
        val clashInHand = state.playerHand.first { it.instanceId == "hc_clash" }
        assertEquals(12, state.effectivePlayCost(clashInHand))
    }

    @Test
    fun `crystal stormbird guardian follower mortal prophet endurance and eggnivia revival work`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val guardian = UnitOnBoard("u_guard", CardCatalog.getCard("card_guardian_follower"), true, 4, 5, 5, setOf(Keyword.CHALLENGER))
        val prophet = UnitOnBoard("u_proph", CardCatalog.getCard("card_mortal_prophet"), true, 0, 5, 5, emptySet())
        val anivia = UnitOnBoard("u_anivia", CardCatalog.getCard("card_anivia"), true, 3, 5, 5, setOf(Keyword.ELEMENTAL))
        val stormbird = HandCardInstance("hc_bird", CardCatalog.getCard("card_crystal_stormbird"), effectiveCost = 5)
        val frostbittenEnemy = UnitOnBoard(
            instanceId = "e_zero",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 4,
            maxHealth = 4,
            keywords = emptySet(),
            isFrostbitten = true // 0 Power -> takes double damage from Anivia 6★ Crystallize!
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 6, // Includes 4★ Endurance & 6★ Crystallize
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 10,
            playerNexusHp = 35,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 10,
            playerMaxMana = 10,
            isEnlightened = true,
            playerHasAttackToken = true,
            playerHand = listOf(stormbird),
            playerBoard = listOf(guardian, prophet, anivia),
            enemyBoard = listOf(frostbittenEnemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Play Chim Bão (4/5):
        // - Elemental skill increments elemental count -> 3★ + 2★ (Enlightened) grants all allies +1|+1:
        //   * Guardian Follower (4/5 -> 5/6) takes 0 damage from allied skill (stays 6 HP)
        //   * Mortal Prophet (0/5 -> 1/6) takes 2 -> 1/4, Endurance +1|+1 -> 2|5, deals 1 to Enemy Nexus (20 -> 19)
        //   * Anivia Lv1 (3/5 -> 4/6) takes 2 -> 4/4, Endurance +1|+1 -> 5/5
        //   * Frostbitten Enemy (0 Power) takes 2 * 2 = 4 damage via 6★ Crystallize -> slain!
        state = CombatEngine.playCardFromHand(state, "hc_bird")
        assertEquals(6, state.playerBoard.first { it.instanceId == "u_guard" }.health)
        val prophAfter = state.playerBoard.first { it.instanceId == "u_proph" }
        assertEquals(2, prophAfter.power)
        assertEquals(5, prophAfter.health)
        assertEquals(19, state.enemyNexusHp)
        assertTrue(state.enemyBoard.isEmpty())

        // Level up Anivia via Round Start while Enlightened -> +1|+1 (5/5 -> 6/6)
        state = state.copy(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )
        state = CombatEngine.passPriority(state)
        val leveledAnivia = state.playerBoard.first { it.instanceId == "u_anivia" }
        assertTrue(leveledAnivia.isLeveledUp)
        assertEquals(6, leveledAnivia.power)
        assertEquals(6, leveledAnivia.health)

        // Anivia Lv2 dies -> Eggnivia (diedAtLevel = 2) -> Round Start hatches back into Anivia Lv2 (4/6)!
        val enemyStrong = UnitOnBoard("e_big", CardCatalog.getCard("card_garen"), false, 10, 10, 10, emptySet())
        state = state.copy(
            priorityHolder = PriorityHolder.PLAYER,
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            enemyBoard = listOf(enemyStrong),
            attackingUnitIds = listOf("u_anivia"),
            blockPairings = mapOf("u_anivia" to "e_big")
        )
        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = state.combatLog.toMutableList())
        val egg = state.playerBoard.find { it.instanceId == "u_anivia" }
        assertNotNull(egg)
        assertEquals("card_eggnivia", egg!!.template.id)
        assertEquals(2, egg.health)
        assertEquals(2, egg.diedAtLevel)

        // Advance round -> Round Start at 10 regularMana hatches Eggnivia into Lv2 Anivia (4/6)
        state = state.copy(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )
        val nextRound = CombatEngine.passPriority(state)
        val hatched = nextRound.playerBoard.find { it.instanceId == "u_anivia" }
        assertNotNull(hatched)
        assertEquals("card_anivia", hatched!!.template.id)
        assertTrue(hatched.isLeveledUp)
        assertEquals(4, hatched.power)
        assertEquals(6, hatched.health)
    }

    @Test
    fun `mana safeguards clamp bounds trigger perfected manaflow on round 1 and refill mana on item play`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val anivia = CardCatalog.getChampion("champ_anivia")
        val runWithManaflow = RunState(
            runId = "run_mana_test",
            adventureId = adv.id,
            championId = "champ_anivia",
            currentNodeId = node.id,
            completedNodeIds = emptyList(),
            currentNexusHp = 40,
            maxNexusHp = 40,
            gold = 300,
            rerollsRemaining = 4,
            revivesRemaining = 1,
            activePowerIds = listOf("pow_manaflow"),
            runDeck = anivia.baseDeck
        )
        // High starting mana sources (5★ + Lv30 + relics) are clamped to max 10
        val maxedLoadout = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 6,
            championLevel = 30,
            equippedRelics = listOf("relic_starforged", "relic_starforged", "relic_starforged")
        )
        val clampedEncounter = CombatEngine.createEncounter(runWithManaflow, maxedLoadout, adv, node)
        assertTrue(clampedEncounter.playerMaxMana in 1..10)
        assertTrue(clampedEncounter.playerMana in 0..clampedEncounter.playerMaxMana)

        // Round 1 with Perfected Manaflow grants +1 Mana gem immediately (base 2 -> 3)
        val baseLoadout = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 2,
            championLevel = 30
        )
        val round1Manaflow = CombatEngine.createEncounter(runWithManaflow, baseLoadout, adv, node)
        assertEquals(3, round1Manaflow.playerMaxMana)
        assertEquals(3, round1Manaflow.playerMana)

        // Item with refillManaOnPlay refills playerMana up to playerMaxMana
        val refillItem = ItemTemplate(
            id = "test_mana_refill",
            name = "Mana Crystal",
            rarity = Rarity.RARE,
            category = ItemCategory.ECONOMY,
            description = "Play: Refill 2 Mana",
            refillManaOnPlay = 2
        )
        val unitWithRefill = HandCardInstance(
            instanceId = "hc_refill",
            template = CardCatalog.getCard("card_wild_mystics"), // cost 2
            attachedItems = listOf(refillItem),
            effectiveCost = 2
        )
        val manaState = round1Manaflow.copy(
            playerMana = 3,
            playerMaxMana = 3,
            playerHand = listOf(unitWithRefill)
        )
        val afterPlay = CombatEngine.playCardFromHand(manaState, "hc_refill")
        assertEquals(3, afterPlay.playerMana)
        assertTrue(afterPlay.playerMana <= afterPlay.playerMaxMana)
    }

    @Test
    fun `champion roster only contains anivia stale db loadouts are cleaned and deck cards have unique art`() = runTest {
        assertEquals(listOf("champ_anivia"), CardCatalog.champions.map { it.id })

        val dao = FakeGameDao()
        val repo = GameRepository(dao, testDispatcher)
        // Simulate stale database rows from earlier multi-champion builds
        dao.upsertChampionLoadout(ChampionLoadoutEntity.fromDomain(ChampionLoadout(championId = "champ_jinx", isUnlocked = true)))
        dao.upsertChampionLoadout(ChampionLoadoutEntity.fromDomain(ChampionLoadout(championId = "champ_yasuo", isUnlocked = true)))
        repo.saveActiveRun(
            RunState(
                runId = "stale_run",
                adventureId = "adv_demacia_outskirts",
                championId = "champ_jinx",
                currentNodeId = "d_fight_1a",
                completedNodeIds = emptyList(),
                currentNexusHp = 30,
                maxNexusHp = 30,
                gold = 100,
                rerollsRemaining = 2,
                revivesRemaining = 1,
                activePowerIds = emptyList(),
                runDeck = emptyList()
            )
        )

        repo.ensureSeeded()
        val vm = GameViewModel(repo)
        advanceUntilIdle()

        assertEquals(setOf("champ_anivia"), vm.uiState.value.championLoadouts.keys)
        assertNotNull(dao.getChampionLoadout("champ_jinx"))
        assertNotNull(dao.getChampionLoadout("champ_yasuo"))
        assertEquals("champ_anivia", vm.uiState.value.selectedChampionId)
        assertNull(dao.getSavedRun())

        // All 9 cards in Anivia's starter deck + Eggnivia token have distinct non-null illustrations
        val aniviaCardIds = CardCatalog.getChampion("champ_anivia").baseDeck.map { it.cardId } + "card_eggnivia"
        val artResources = aniviaCardIds.map { cardId ->
            val res = cardPortraitRes(cardId)
            assertNotNull("Expected illustration for $cardId", res)
            res!!
        }
        assertEquals(10, artResources.size)
        assertEquals(10, artResources.toSet().size)
    }

    @Test
    fun `zero damage and frostbitten attackers do not break barrier or spellshield`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val frostbittenAtk = UnitOnBoard(
            instanceId = "atk_frost",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 5,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.IMPACT),
            impactStacks = 1,
            isFrostbitten = true
        )
        val barrierBlocker = UnitOnBoard(
            instanceId = "blk_bar",
            template = CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.BARRIER),
            hasBarrier = true
        )
        val spellShieldEnemy = UnitOnBoard(
            instanceId = "e_ss",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.SPELLSHIELD),
            hasSpellShield = true
        )
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(frostbittenAtk),
            enemyBoard = listOf(barrierBlocker, spellShieldEnemy),
            attackingUnitIds = listOf("atk_frost"),
            blockPairings = mapOf("atk_frost" to "blk_bar"),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        )

        val afterCombat = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        // 0-power Frostbitten attacker neither breaks blocker's Barrier nor deals Impact
        assertTrue(afterCombat.enemyBoard.first { it.instanceId == "blk_bar" }.hasBarrier)
        assertEquals(20, afterCombat.enemyNexusHp)

        // Direct 0-damage spell via resolveSingleSpellEffect does not break SpellShield or Barrier
        val zeroDmgSpell = CardCatalog.getCard("card_mystic_shot").copy(effectValue = 0)
        val afterZeroSpell = CombatEngine.resolveSingleSpellEffect(
            afterCombat,
            zeroDmgSpell,
            sourceIsPlayer = true,
            bonusDmg = 0,
            logs = mutableListOf()
        )
        assertTrue(afterZeroSpell.enemyBoard.first { it.instanceId == "blk_bar" }.hasBarrier)
        assertTrue(afterZeroSpell.enemyBoard.first { it.instanceId == "e_ss" }.hasSpellShield)
    }

    @Test
    fun `enemy overwhelm and double attack deal excess damage to player nexus accounting for tough`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        // Enemy 6|5 Double Attack + Overwhelm vs Player 3|2 Tough
        // 1st strike: effectiveHealth = 2 + 1 (Tough) = 3 -> excess = 6 - 3 = 3 to Player Nexus, blocker dies
        // 2nd strike: blocker dead -> Overwhelm deals full 6 to Player Nexus -> total 9 damage (30 -> 21)
        val enemyDoubleOverwhelm = UnitOnBoard(
            instanceId = "e_da_ow",
            template = CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = false,
            power = 6,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.DOUBLE_ATTACK, Keyword.OVERWHELM)
        )
        val playerToughBlocker = UnitOnBoard(
            instanceId = "p_tough",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )
        // Standard enemy 5|5 Overwhelm vs Player 3|2 Tough -> effectiveHealth = 3 -> excess = 2 (21 -> 19)
        val enemyStdOverwhelm = UnitOnBoard(
            instanceId = "e_std_ow",
            template = CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = false,
            power = 5,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.OVERWHELM)
        )
        val playerToughBlocker2 = UnitOnBoard(
            instanceId = "p_tough_2",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 1,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )

        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(playerToughBlocker, playerToughBlocker2),
            enemyBoard = listOf(enemyDoubleOverwhelm, enemyStdOverwhelm),
            attackingUnitIds = listOf("e_da_ow", "e_std_ow"),
            blockPairings = mapOf("e_da_ow" to "p_tough", "e_std_ow" to "p_tough_2"),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            playerInitiatedCombat = false
        )

        val afterCombat = CombatEngine.resolveCombatResolution(state, playerIsAttacking = false, logs = mutableListOf())
        assertEquals(30 - 9 - 2, afterCombat.playerNexusHp)
        assertTrue(afterCombat.playerBoard.isEmpty())
    }

    @Test
    fun `fury triggers in standard combat resolution for both attacker and blocker`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val furyAttacker = UnitOnBoard(
            instanceId = "p_fury",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.FURY)
        )
        val weakBlocker = UnitOnBoard(
            instanceId = "e_weak",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val weakAttacker = UnitOnBoard(
            instanceId = "p_weak",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 1,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val furyBlocker = UnitOnBoard(
            instanceId = "e_fury",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 3,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.FURY)
        )

        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(furyAttacker, weakAttacker),
            enemyBoard = listOf(weakBlocker, furyBlocker),
            attackingUnitIds = listOf("p_fury", "p_weak"),
            blockPairings = mapOf("p_fury" to "e_weak", "p_weak" to "e_fury"),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        )

        val afterCombat = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        val survivingFuryAtk = afterCombat.playerBoard.single { it.instanceId == "p_fury" }
        assertEquals(5, survivingFuryAtk.power) // 4 + 1 from Fury
        assertEquals(3, survivingFuryAtk.health) // 4 - 2 + 1 = 3

        val survivingFuryBlk = afterCombat.enemyBoard.single { it.instanceId == "e_fury" }
        assertEquals(4, survivingFuryBlk.power) // 3 + 1 from Fury
        assertEquals(4, survivingFuryBlk.health) // 4 - 1 + 1 = 4
    }

    @Test
    fun `innate barrier keyword persists after barrier breaks and round end`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val innateBarrierUnit = UnitOnBoard(
            instanceId = "p_bar",
            template = CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = true,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.BARRIER, Keyword.DEATHLESS),
            hasBarrier = true,
            hasDeathless = true
        )
        val enemyHitter = UnitOnBoard(
            instanceId = "e_hit",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 5,
            maxHealth = 5,
            keywords = emptySet()
        )
        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_lux",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(innateBarrierUnit),
            enemyBoard = listOf(enemyHitter),
            attackingUnitIds = listOf("p_bar"),
            blockPairings = mapOf("p_bar" to "e_hit")
        )

        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        val afterHit = state.playerBoard.single { it.instanceId == "p_bar" }
        assertFalse(afterHit.hasBarrier)
        assertTrue(afterHit.keywords.contains(Keyword.BARRIER))

        // Next round clears temporary hasBarrier without stripping innate Keyword.BARRIER
        state = CombatEngine.advanceToNextRound(state.copy(playerBoard = listOf(afterHit.copy(hasBarrier = true))), mutableListOf())
        val nextRoundUnit = state.playerBoard.single { it.instanceId == "p_bar" }
        assertFalse(nextRoundUnit.hasBarrier)
        assertTrue(nextRoundUnit.keywords.contains(Keyword.BARRIER))

        // Deathless revival restores hasBarrier for units with innate Keyword.BARRIER
        val lethalShot = CardCatalog.getCard("card_mystic_shot").copy(effectValue = 5)
        val afterDeathless = CombatEngine.resolveSingleSpellEffect(
            state.copy(playerBoard = listOf(nextRoundUnit)),
            lethalShot,
            sourceIsPlayer = false,
            logs = mutableListOf()
        )
        val revived = afterDeathless.playerBoard.single()
        assertFalse(revived.instanceId == "p_bar")
        assertEquals(1, revived.health)
        assertTrue(revived.hasBarrier)
    }

    @Test
    fun `hallowed attack buff lasts only for current round and resets at round transition`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val ally = UnitOnBoard(
            instanceId = "p_hallow_atk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerHallowedStacks = 3,
            playerBoard = listOf(ally),
            attackingUnitIds = listOf("p_hallow_atk"),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE
        )

        state = CombatEngine.commitPlayerAttack(state)
        assertEquals(5, state.playerBoard.single { it.instanceId == "p_hallow_atk" }.power)

        state = CombatEngine.advanceToNextRound(state, mutableListOf())
        assertEquals(2, state.round)
        assertEquals(2, state.playerBoard.single { it.instanceId == "p_hallow_atk" }.power)
    }

    @Test
    fun `enemy lifesteal heals enemy nexus and ashe frostbite aura does not buff frostbitten allies`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val enemyLifesteal = UnitOnBoard(
            instanceId = "e_ls",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.LIFESTEAL)
        )
        val enemyFrozen = UnitOnBoard(
            instanceId = "e_frz",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 3,
            maxHealth = 3,
            keywords = emptySet(),
            isFrostbitten = true
        )
        val playerFrostbittenAtk = UnitOnBoard(
            instanceId = "p_frz",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet(),
            isFrostbitten = true
        )
        val playerHealthyAtk = UnitOnBoard(
            instanceId = "p_ok",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )

        // 1. Enemy Lifesteal unblocked strike heals Enemy Nexus (10 -> 14), not Player Nexus (20 -> 16)
        val enemyAtkState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_ashe",
            starLevel = 2,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 20,
            playerMaxHp = 30,
            enemyNexusHp = 10,
            enemyMaxHp = 20,
            enemyBoard = listOf(enemyLifesteal, enemyFrozen),
            playerBoard = listOf(playerFrostbittenAtk, playerHealthyAtk),
            attackingUnitIds = listOf("e_ls"),
            blockPairings = emptyMap(),
            playerInitiatedCombat = false
        )
        val afterEnemyAtk = CombatEngine.resolveCombatResolution(enemyAtkState, playerIsAttacking = false, logs = mutableListOf())
        assertEquals(16, afterEnemyAtk.playerNexusHp)
        assertEquals(14, afterEnemyAtk.enemyNexusHp)

        // 2. Ashe 2★ Frostbite aura (+1 per Frostbitten enemy):
        //    - p_frz is Frostbitten -> stays 0 strike power, deals 0 to enemy Nexus
        //    - p_ok is NOT Frostbitten -> gets +1 aura -> deals 3 to enemy Nexus (14 -> 11)
        val playerAtkState = afterEnemyAtk.copy(
            attackingUnitIds = listOf("p_frz", "p_ok"),
            blockPairings = emptyMap(),
            playerInitiatedCombat = true
        )
        val afterPlayerAtk = CombatEngine.resolveCombatResolution(playerAtkState, playerIsAttacking = true, logs = mutableListOf())
        assertEquals(11, afterPlayerAtk.enemyNexusHp)
    }

    @Test
    fun `strict id lookups log missing ids and findOrNull returns null`() {
        assertNull(CardCatalog.findCardOrNull("missing_card_id"))
        assertNull(CardCatalog.findItemOrNull("missing_item_id"))
        assertNull(CardCatalog.findPowerOrNull("missing_power_id"))
        assertNull(CardCatalog.findRelicOrNull("missing_relic_id"))
        assertNull(CardCatalog.findChampionOrNull("missing_champion_id"))
        assertNull(AdventureCatalog.findAdventureOrNull("missing_adventure_id"))

        val recordedCardErrors = mutableListOf<Pair<String, String>>()
        val recordedAdvErrors = mutableListOf<String>()
        CardCatalog.missingIdObserver = { kind, id -> recordedCardErrors.add(kind to id) }
        AdventureCatalog.missingIdObserver = { id -> recordedAdvErrors.add(id) }
        try {
            CardCatalog.getCard("missing_card_id")
            CardCatalog.getItem("missing_item_id")
            CardCatalog.getPower("missing_power_id")
            CardCatalog.getRelic("missing_relic_id")
            CardCatalog.getChampion("missing_champion_id")
            AdventureCatalog.getAdventure("missing_adventure_id")
        } finally {
            CardCatalog.missingIdObserver = null
            AdventureCatalog.missingIdObserver = null
        }

        assertEquals(
            listOf(
                "card" to "missing_card_id",
                "item" to "missing_item_id",
                "power" to "missing_power_id",
                "relic" to "missing_relic_id",
                "champion" to "missing_champion_id"
            ),
            recordedCardErrors
        )
        assertEquals(listOf("missing_adventure_id"), recordedAdvErrors)
    }

    @Test
    fun `celestial might and nightmare curse mutators apply in combat encounters`() {
        val targonAdv = AdventureCatalog.getAdventure("adv_targon_nightmare")
        val champ = CardCatalog.getChampion("champ_anivia")
        val loadout = ChampionLoadout(championId = champ.id, isUnlocked = true, starLevel = 1)
        val runState = RunState(
            runId = "run_mut_test",
            adventureId = targonAdv.id,
            championId = champ.id,
            currentNodeId = "d_fight_1a",
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 0,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = champ.baseDeck
        )

        // Regular node with enemyStartingMana = 1 gets bumped to 4 by mut_celestial_might
        // and does NOT get card_curse_suppression because node.type != FINAL_BOSS
        val lowManaRegularNode = AdventureCatalog.getAdventure("adv_demacia_outskirts")
            .mapNodes.first { it.id == "d_fight_1a" }
        val regularCombat = CombatEngine.createEncounter(runState, loadout, targonAdv, lowManaRegularNode)
        assertEquals(4, regularCombat.enemyMana)
        assertEquals(4, regularCombat.enemyMaxMana)
        assertTrue(regularCombat.playerHand.none { it.template.id == "card_curse_suppression" })

        // Final Boss node with mut_nightmare_curse afflicts opening hand with card_curse_suppression
        val bossNode = targonAdv.mapNodes.first { it.type == NodeType.FINAL_BOSS }
        val bossCombat = CombatEngine.createEncounter(runState, loadout, targonAdv, bossNode)
        assertEquals(4, bossCombat.enemyMana)
        assertTrue(bossCombat.playerHand.any { it.template.id == "card_curse_suppression" })
    }

    @Test
    fun `drawing from empty deck deals fatigue damage and can end game`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerEmptyState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 4,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerDrawPile = emptyList()
        )

        val afterFirstPlayerFatigue = CombatEngine.drawPlayerCard(playerEmptyState)
        assertEquals(4 - CombatEngine.EMPTY_DECK_FATIGUE_DAMAGE, afterFirstPlayerFatigue.playerNexusHp)
        assertNull(afterFirstPlayerFatigue.playerWon)
        assertTrue(afterFirstPlayerFatigue.combatLog.any { it.contains("hết", ignoreCase = true) })

        val afterLethalPlayerFatigue = CombatEngine.drawPlayerCard(afterFirstPlayerFatigue)
        assertEquals(0, afterLethalPlayerFatigue.playerNexusHp)
        assertEquals(CombatPhase.GAME_OVER, afterLethalPlayerFatigue.phase)
        assertEquals(false, afterLethalPlayerFatigue.playerWon)

        val enemyEmptyState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 20,
            playerMaxHp = 30,
            enemyNexusHp = CombatEngine.EMPTY_DECK_FATIGUE_DAMAGE,
            enemyMaxHp = 20,
            enemyDrawPool = emptyList()
        )
        val afterLethalEnemyFatigue = CombatEngine.drawEnemyCard(enemyEmptyState)
        assertEquals(0, afterLethalEnemyFatigue.enemyNexusHp)
        assertEquals(CombatPhase.GAME_OVER, afterLethalEnemyFatigue.phase)
        assertEquals(true, afterLethalEnemyFatigue.playerWon)
    }

    @Test
    fun `player and ai share fast spell response windows during attack and block phases`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerUnit = UnitOnBoard(
            instanceId = "p_unit",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )
        val enemyUnit = UnitOnBoard(
            instanceId = "e_unit",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 3,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val playerFastShot = HandCardInstance(
            instanceId = "hc_mystic",
            template = CardCatalog.getCard("card_mystic_shot"),
            effectiveCost = 2
        )
        val playerSlowSpell = HandCardInstance(
            instanceId = "hc_slow",
            template = CardCatalog.getCard("card_golden_aegis"),
            effectiveCost = 4
        )

        // 1. Attack declaration spell-response window (COMBAT_DECLARE_ATTACK_PHASE)
        var atkWindowState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 5,
            playerMaxMana = 5,
            enemyMana = 5,
            enemyMaxMana = 5,
            playerBoard = listOf(playerUnit),
            enemyBoard = listOf(enemyUnit),
            playerHand = listOf(playerFastShot, playerSlowSpell),
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")),
            attackingUnitIds = listOf("p_unit"),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        assertTrue(atkWindowState.canPlayCard(playerFastShot))
        assertFalse(atkWindowState.canPlayCard(playerSlowSpell))

        atkWindowState = CombatEngine.playCardFromHand(atkWindowState, "hc_mystic")
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, atkWindowState.phase)
        assertEquals(PriorityHolder.ENEMY, atkWindowState.priorityHolder)
        assertEquals(1, atkWindowState.spellStack.size)

        // AI responds with reactive Fast spell (Steel Tempest) and returns priority to player
        atkWindowState = EnemyAIController.executeTurn(atkWindowState)
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, atkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, atkWindowState.priorityHolder)
        assertEquals(2, atkWindowState.spellStack.size)

        // Player passes to resolve stack LIFO; priority returns to player in COMBAT_DECLARE_ATTACK_PHASE
        atkWindowState = CombatEngine.passPriority(atkWindowState)
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, atkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, atkWindowState.priorityHolder)
        assertTrue(atkWindowState.spellStack.isEmpty())
        assertTrue(atkWindowState.playerBoard.single().isStunned)
        assertTrue(atkWindowState.enemyBoard.isEmpty())

        // 2. Block declaration spell-response window (COMBAT_DECLARE_BLOCK_PHASE)
        var blkWindowState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 5,
            playerMaxMana = 5,
            enemyMana = 5,
            enemyMaxMana = 5,
            playerBoard = listOf(playerUnit),
            enemyBoard = listOf(enemyUnit),
            playerHand = listOf(playerFastShot, playerSlowSpell),
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")),
            attackingUnitIds = listOf("e_unit"),
            playerInitiatedCombat = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        assertTrue(blkWindowState.canPlayCard(playerFastShot))
        assertFalse(blkWindowState.canPlayCard(playerSlowSpell))

        blkWindowState = CombatEngine.playCardFromHand(blkWindowState, "hc_mystic")
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, blkWindowState.phase)
        assertEquals(PriorityHolder.ENEMY, blkWindowState.priorityHolder)
        assertEquals(1, blkWindowState.spellStack.size)

        // AI responds with reactive Fast spell and passes priority back to defending player
        blkWindowState = EnemyAIController.executeTurn(blkWindowState)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, blkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, blkWindowState.priorityHolder)
        assertEquals(2, blkWindowState.spellStack.size)

        // Defending player commits block -> resolves spell stack first, then combat resolution
        blkWindowState = CombatEngine.passPriority(blkWindowState)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, blkWindowState.phase)
        assertEquals(PriorityHolder.PLAYER, blkWindowState.priorityHolder)
        assertTrue(blkWindowState.spellStack.isEmpty())
        // Mystic Shot (2 dmg) killed e_unit (2 hp) before combat resolution hit Player Nexus
        assertEquals(30, blkWindowState.playerNexusHp)
    }

    @Test
    fun `new battle resets playerMaxMana and enemyMaxMana to match baseline capped at 10`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val anivia = CardCatalog.getChampion("champ_anivia")
        val star1Loadout = ChampionLoadout(
            championId = "champ_anivia",
            isUnlocked = true,
            starLevel = 1,
            championLevel = 1
        )
        val runState = RunState(
            runId = "run_reset_mana",
            adventureId = adv.id,
            championId = "champ_anivia",
            currentNodeId = node.id,
            completedNodeIds = emptyList(),
            currentNexusHp = 30,
            maxNexusHp = 30,
            gold = 100,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = anivia.baseDeck
        )

        var firstBattle = CombatEngine.createEncounter(runState, star1Loadout, adv, node)
        assertEquals(1, firstBattle.playerMaxMana)
        assertEquals(1, firstBattle.enemyMaxMana)

        // Advance first battle to Round 7 (7/7 mana)
        repeat(6) {
            firstBattle = CombatEngine.advanceToNextRound(firstBattle, mutableListOf())
        }
        assertEquals(7, firstBattle.playerMaxMana)
        assertEquals(7, firstBattle.enemyMaxMana)

        // Advance beyond Round 10 -> strictly capped at 10
        repeat(5) {
            firstBattle = CombatEngine.advanceToNextRound(firstBattle, mutableListOf())
        }
        assertEquals(10, firstBattle.playerMaxMana)
        assertEquals(10, firstBattle.enemyMaxMana)

        // Starting a fresh encounter resets playerMaxMana and enemyMaxMana to baseline (1 for 1★, 2 for 2★)
        val freshBattle = CombatEngine.createEncounter(runState, star1Loadout, adv, node)
        assertEquals(1, freshBattle.round)
        assertEquals(1, freshBattle.playerMana)
        assertEquals(1, freshBattle.playerMaxMana)
        assertEquals(1, freshBattle.enemyMana)
        assertEquals(1, freshBattle.enemyMaxMana)

        val star2Loadout = star1Loadout.copy(starLevel = 2)
        val star2Battle = CombatEngine.createEncounter(runState, star2Loadout, adv, node)
        assertEquals(2, star2Battle.playerMana)
        assertEquals(2, star2Battle.playerMaxMana)
    }

    @Test
    fun `bot attack round gives priority to AI first and blocks player attack declarations`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val playerUnit = UnitOnBoard(
            instanceId = "p_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val enemyUnit = UnitOnBoard(
            instanceId = "e_1",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )
        // Round 1 ends -> Round 2 is Bot's Attack Round (enemyHasAttackToken = true, priorityHolder = ENEMY)
        val round1End = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 1,
            playerMaxMana = 1,
            enemyMana = 1,
            enemyMaxMana = 1,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            playerBoard = listOf(playerUnit),
            enemyBoard = listOf(enemyUnit),
            playerDrawPile = listOf(
                HandCardInstance("hc_draw", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
            ),
            enemyDrawPool = listOf(CardCatalog.getCard("card_cithria")),
            enemyHand = listOf(CardCatalog.getCard("card_cithria")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )

        var round2 = CombatEngine.passPriority(round1End)
        assertEquals(2, round2.round)
        assertFalse(round2.playerHasAttackToken)
        assertTrue(round2.enemyHasAttackToken)
        assertEquals(PriorityHolder.ENEMY, round2.priorityHolder)

        // Player cannot play cards or enter/toggle/commit attack while priorityHolder == ENEMY
        val playerHandCard = round2.playerHand.first()
        assertFalse(round2.canPlayCard(playerHandCard))
        assertEquals(round2, CombatEngine.enterAttackDeclaration(round2))

        // AI acts first on Bot Attack round (summons 1-cost unit -> passes priority to PLAYER)
        round2 = EnemyAIController.executeTurn(round2)
        assertEquals(2, round2.enemyBoard.size)
        assertEquals(PriorityHolder.PLAYER, round2.priorityHolder)

        // Player STILL cannot initiate an attack on Bot's attack round because playerHasAttackToken == false
        assertFalse(round2.playerHasAttackToken)
        val attemptedAttack = CombatEngine.enterAttackDeclaration(round2)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, attemptedAttack.phase)
        val forcedAttack = CombatEngine.toggleUnitAttack(
            round2.copy(phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE),
            "p_1"
        )
        assertTrue(forcedAttack.attackingUnitIds.isEmpty())
        val forcedCommit = CombatEngine.commitPlayerAttack(
            round2.copy(
                phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
                attackingUnitIds = listOf("p_1")
            )
        )
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, forcedCommit.phase)
    }

    @Test
    fun `full bot attack to player block with burst spell and AI fast response resolves cleanly`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerBlocker = UnitOnBoard(
            instanceId = "p_blk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 3,
            maxHealth = 3,
            keywords = emptySet()
        )
        val enemyAttacker = UnitOnBoard(
            instanceId = "e_atk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = emptySet()
        )
        val playerBarrierSpell = HandCardInstance(
            instanceId = "hc_bar",
            template = CardCatalog.getCard("card_prismatic_barrier"), // 2-cost Burst
            effectiveCost = 2
        )
        val playerFastSpell = HandCardInstance(
            instanceId = "hc_fast",
            template = CardCatalog.getCard("card_mystic_shot"), // 2-cost Fast
            effectiveCost = 2
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 2,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 4,
            playerMaxMana = 4,
            enemyMana = 4,
            enemyMaxMana = 4,
            playerHasAttackToken = false,
            enemyHasAttackToken = true,
            playerInitiatedCombat = false,
            playerBoard = listOf(playerBlocker),
            enemyBoard = listOf(enemyAttacker),
            playerHand = listOf(playerBarrierSpell, playerFastSpell),
            playerDrawPile = listOf(
                HandCardInstance("hc_draw", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
            ),
            enemyHand = listOf(CardCatalog.getCard("card_mystic_shot")), // AI holds Fast burn spell
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )

        // 1. AI has Attack Token & priority -> AI declares attack with e_atk (4|4 vs 3|3)
        state = EnemyAIController.executeTurn(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertFalse(state.playerInitiatedCombat)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(listOf("e_atk"), state.attackingUnitIds)

        // 2. Player assigns blocker and casts Burst Prismatic Barrier (keeps priority on PLAYER)
        state = CombatEngine.assignPlayerBlocker(state, attackerId = "e_atk", blockerId = "p_blk")
        assertEquals(mapOf("e_atk" to "p_blk"), state.blockPairings)
        state = CombatEngine.playCardFromHand(state, "hc_bar")
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.playerBoard.single().hasBarrier)
        assertTrue(state.playerCastSpellInBlock)

        // 3. Player commits block -> passes priority back to AI to respond with Fast/Burst spells
        state = CombatEngine.commitPlayerBlock(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertTrue(state.blockCommitted)
        assertEquals(PriorityHolder.ENEMY, state.priorityHolder)

        // 4. AI casts Mystic Shot (2 dmg) to pop Barrier -> passes priority back to PLAYER
        state = EnemyAIController.executeTurn(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(1, state.spellStack.size)

        // 5. Player passes/commits on stack -> Mystic Shot pops Barrier, combat strikes trade (p_blk slain, e_atk drops to 1 HP), return to MAIN_ACTION_PHASE
        state = CombatEngine.commitPlayerBlock(state)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, state.phase)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.spellStack.isEmpty())
        assertFalse(state.blockCommitted)
        assertEquals(30, state.playerNexusHp)
        assertTrue(state.playerBoard.isEmpty())
        assertEquals(1, state.enemyBoard.single().health)
    }

    @Test
    fun `player attack with AI reactive fast spell gives player final response window before resolving`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val playerAttacker = UnitOnBoard(
            instanceId = "p_atk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = emptySet()
        )
        val enemyDefender = UnitOnBoard(
            instanceId = "e_def",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 2,
            health = 4,
            maxHealth = 4,
            keywords = emptySet()
        )

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_garen",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 3,
            playerMaxMana = 3,
            enemyMana = 4,
            enemyMaxMana = 4,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            playerBoard = listOf(playerAttacker),
            enemyBoard = listOf(enemyDefender),
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")), // 2-cost Fast stun
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_atk"),
            priorityHolder = PriorityHolder.PLAYER
        )

        state = CombatEngine.commitPlayerAttack(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertTrue(state.playerInitiatedCombat)
        assertEquals(PriorityHolder.ENEMY, state.priorityHolder)

        // AI assigns blocker AND casts Steel Tempest -> yields final response window to PLAYER
        state = EnemyAIController.executeTurn(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertTrue(state.playerInitiatedCombat)
        assertTrue(state.blockCommitted)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(1, state.spellStack.size)

        // Player clicks OK / passes -> resolves Steel Tempest (stuns p_atk) and resolves combat cleanly -> MAIN_ACTION_PHASE
        state = CombatEngine.passPriority(state)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, state.phase)
        assertTrue(state.spellStack.isEmpty())
        assertTrue(state.attackingUnitIds.isEmpty())
        assertEquals(20, state.enemyNexusHp)
        assertEquals(4, state.enemyBoard.single().health)
    }

    // --- PHẦN 1/3: LUỒNG GIAO TRANH, TỐC ĐỘ PHÉP VÀ PHẢN HỒI ---

    private fun testBaseState(
        playerMana: Int = 10,
        enemyMana: Int = 10,
        playerBoard: List<UnitOnBoard> = emptyList(),
        enemyBoard: List<UnitOnBoard> = emptyList(),
        playerHand: List<HandCardInstance> = emptyList(),
        enemyHand: List<com.example.model.CardTemplate> = emptyList(),
        phase: CombatPhase = CombatPhase.MAIN_ACTION_PHASE,
        priorityHolder: PriorityHolder = PriorityHolder.PLAYER,
        playerHasAttackToken: Boolean = true,
        playerInitiatedCombat: Boolean = true,
        blockCommitted: Boolean = false,
        attackingUnitIds: List<String> = emptyList(),
        blockPairings: Map<String, String> = emptyMap(),
        confirmedBlockedAttackerIds: Set<String> = emptySet(),
        spellStack: List<com.example.engine.SpellStackEntry> = emptyList(),
        lastStackCaster: PriorityHolder? = null
    ): CombatState {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        return CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 3,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = playerMana,
            playerMaxMana = 10,
            enemyMana = enemyMana,
            enemyMaxMana = 10,
            playerHasAttackToken = playerHasAttackToken,
            enemyHasAttackToken = !playerHasAttackToken,
            playerInitiatedCombat = playerInitiatedCombat,
            blockCommitted = blockCommitted,
            phase = phase,
            priorityHolder = priorityHolder,
            lastStackCaster = lastStackCaster,
            playerBoard = playerBoard,
            enemyBoard = enemyBoard,
            playerHand = playerHand,
            enemyHand = enemyHand,
            attackingUnitIds = attackingUnitIds,
            blockPairings = blockPairings,
            confirmedBlockedAttackerIds = confirmedBlockedAttackerIds,
            spellStack = spellStack
        )
    }

    @Test
    fun `four spell speeds obey exact timing windows across main stack and combat`() {
        val burst = HandCardInstance("h_burst", CardCatalog.getCard("card_flash_freeze"), effectiveCost = 3)
        val fast = HandCardInstance("h_fast", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)
        val focus = HandCardInstance("h_focus", CardCatalog.getCard("card_time_trick"), effectiveCost = 2)
        val slow = HandCardInstance("h_slow", CardCatalog.getCard("card_golden_aegis"), effectiveCost = 4)

        val mainEmpty = testBaseState(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            playerHand = listOf(burst, fast, focus, slow)
        )
        assertTrue(mainEmpty.canPlayCard(burst))
        assertTrue(mainEmpty.canPlayCard(fast))
        assertTrue(mainEmpty.canPlayCard(focus))
        assertTrue(mainEmpty.canPlayCard(slow))

        val stackOpen = testBaseState(
            phase = CombatPhase.SPELL_STACK_PHASE,
            playerHand = listOf(burst, fast, focus, slow),
            spellStack = listOf(
                com.example.engine.SpellStackEntry(
                    id = "st_1",
                    sourceIsPlayer = false,
                    card = CardCatalog.getCard("card_mystic_shot")
                )
            )
        )
        assertTrue(stackOpen.canPlayCard(burst))
        assertTrue(stackOpen.canPlayCard(fast))
        assertFalse(stackOpen.canPlayCard(focus))
        assertFalse(stackOpen.canPlayCard(slow))

        val declareAttack = testBaseState(phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE)
        assertTrue(declareAttack.canPlayCard(burst))
        assertTrue(declareAttack.canPlayCard(fast))
        assertFalse(declareAttack.canPlayCard(focus))
        assertFalse(declareAttack.canPlayCard(slow))

        val declareBlock = testBaseState(phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE)
        assertTrue(declareBlock.canPlayCard(burst))
        assertTrue(declareBlock.canPlayCard(fast))
        assertFalse(declareBlock.canPlayCard(focus))
        assertFalse(declareBlock.canPlayCard(slow))
    }

    @Test
    fun `burst spells resolve immediately can be chained consecutively and keep priority`() {
        val e1 = UnitOnBoard("e1", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 4, health = 4, maxHealth = 4, keywords = emptySet())
        val e2 = UnitOnBoard("e2", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 5, health = 4, maxHealth = 4, keywords = emptySet())
        val b1 = HandCardInstance("hc_b1", CardCatalog.getCard("card_flash_freeze"), effectiveCost = 3)
        val b2 = HandCardInstance("hc_b2", CardCatalog.getCard("card_flash_freeze"), effectiveCost = 3)

        var state = testBaseState(
            playerMana = 8,
            enemyBoard = listOf(e1, e2),
            playerHand = listOf(b1, b2)
        )

        state = CombatEngine.playCardFromHand(state, "hc_b1", targetUnitId = "e1")
        assertTrue(state.spellStack.isEmpty())
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.enemyBoard.first { it.instanceId == "e1" }.isFrostbitten)
        assertEquals(5, state.playerMana)

        state = CombatEngine.playCardFromHand(state, "hc_b2", targetUnitId = "e2")
        assertTrue(state.spellStack.isEmpty())
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.enemyBoard.first { it.instanceId == "e2" }.isFrostbitten)
        assertEquals(2, state.playerMana)
    }

    @Test
    fun `each side can play at most one Fast spell per combat and counter resets next combat`() {
        val f1 = HandCardInstance("hc_f1", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)
        val f2 = HandCardInstance("hc_f2", CardCatalog.getCard("card_get_excited"), effectiveCost = 3)
        val atk = UnitOnBoard("p_atk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 3, maxHealth = 3, keywords = emptySet())
        val killable1 = UnitOnBoard("p_kill1", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 2, health = 2, maxHealth = 2, keywords = emptySet())
        val killable2 = UnitOnBoard("p_kill2", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 1, health = 2, maxHealth = 2, keywords = emptySet())
        val eDef = UnitOnBoard("e_def", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 5, maxHealth = 5, keywords = emptySet())

        var state = testBaseState(
            playerMana = 10,
            playerBoard = listOf(atk, killable1, killable2),
            enemyBoard = listOf(eDef),
            playerHand = listOf(f1, f2),
            enemyHand = listOf(CardCatalog.getCard("card_mystic_shot"), CardCatalog.getCard("card_mystic_shot")),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_atk")
        )

        // Player casts 1st Fast spell during attack declaration
        state = CombatEngine.playCardFromHand(state, "hc_f1", targetUnitId = "e_def")
        assertTrue(state.playerFastSpellUsedInCombat)
        assertEquals(PriorityHolder.ENEMY, state.priorityHolder)
        assertEquals(1, state.spellStack.size)

        // AI responds with 1st Fast spell on stack
        state = EnemyAIController.executeTurn(state)
        assertTrue(state.enemyFastSpellUsedInCombat)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertEquals(2, state.spellStack.size)

        // Player CANNOT cast a 2nd Fast spell during this same combat
        assertFalse(state.canPlayCard(f2))
        val unchanged = CombatEngine.playCardFromHand(state, "hc_f2", targetUnitId = "ENEMY_NEXUS")
        assertEquals(2, unchanged.spellStack.size)

        // Pass to resolve stack -> stays in DECLARE_ATTACK with playerFastSpellUsedInCombat STILL true
        state = CombatEngine.passPriority(state)
        assertTrue(state.spellStack.isEmpty())
        assertEquals(CombatPhase.COMBAT_DECLARE_ATTACK_PHASE, state.phase)
        assertTrue(state.playerFastSpellUsedInCombat)
        assertFalse(state.canPlayCard(f2))

        // Commit attack -> AI blocks -> AI still blocked from casting a 2nd Fast spell in this combat
        state = CombatEngine.commitPlayerAttack(state)
        state = EnemyAIController.executeTurn(state)
        assertTrue(state.blockCommitted)
        assertEquals(PriorityHolder.PLAYER, state.priorityHolder)
        assertTrue(state.spellStack.isEmpty()) // AI's 2nd Mystic Shot was blocked by enemyFastSpellUsedInCombat!
        assertFalse(state.canPlayCard(f2)) // Player's 2nd Fast spell still blocked in Step 4!

        // Finish combat -> returns to MAIN_ACTION_PHASE and resets Fast combat flags
        state = CombatEngine.passPriority(state)
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, state.phase)
        assertFalse(state.playerFastSpellUsedInCombat)
        assertFalse(state.enemyFastSpellUsedInCombat)
    }

    @Test
    fun `phantom block rule blocks Nexus damage after blocker dies post-confirmation except Overwhelm`() {
        val normalAtk = UnitOnBoard(
            "p_norm",
            CardCatalog.getCard("card_boomcrew_rookie"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.IMPACT),
            impactStacks = 1
        )
        val overwhelmAtk = UnitOnBoard(
            "p_ovw",
            CardCatalog.getCard("card_ruthless_raider"),
            ownerIsPlayer = true,
            power = 5,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.OVERWHELM, Keyword.DOUBLE_ATTACK)
        )
        val blk1 = UnitOnBoard("e_b1", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 1, health = 2, maxHealth = 2, keywords = emptySet())
        val blk2 = UnitOnBoard("e_b2", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 1, health = 2, maxHealth = 2, keywords = emptySet())
        val mystic1 = HandCardInstance("hc_m1", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)
        val getExcited = HandCardInstance("hc_g2", CardCatalog.getCard("card_get_excited"), effectiveCost = 3)

        // Part A: Normal + Impact attacker whose blocker is killed in Step 4 deals 0 Nexus damage (no Impact either)
        var stateA = testBaseState(
            playerBoard = listOf(normalAtk),
            enemyBoard = listOf(blk1),
            playerHand = listOf(mystic1),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            playerInitiatedCombat = true,
            blockCommitted = true,
            attackingUnitIds = listOf("p_norm"),
            blockPairings = mapOf("p_norm" to "e_b1"),
            confirmedBlockedAttackerIds = setOf("p_norm")
        )
        stateA = CombatEngine.playCardFromHand(stateA, "hc_m1", targetUnitId = "e_b1")
        stateA = EnemyAIController.executeTurn(stateA) // AI passes -> resolves Mystic Shot + finishes combat
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, stateA.phase)
        assertTrue(stateA.enemyBoard.isEmpty())
        assertEquals(20, stateA.enemyNexusHp) // Phantom block stopped both strike AND Impact!

        // Part B: Overwhelm + Double Attack attacker whose blocker is killed in Step 4 deals full 2x strike power (10)
        var stateB = testBaseState(
            playerBoard = listOf(overwhelmAtk),
            enemyBoard = listOf(blk2),
            playerHand = listOf(getExcited),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            playerInitiatedCombat = true,
            blockCommitted = true,
            attackingUnitIds = listOf("p_ovw"),
            blockPairings = mapOf("p_ovw" to "e_b2"),
            confirmedBlockedAttackerIds = setOf("p_ovw")
        )
        stateB = CombatEngine.playCardFromHand(stateB, "hc_g2", targetUnitId = "e_b2")
        stateB = EnemyAIController.executeTurn(stateB)
        assertEquals(10, stateB.enemyNexusHp) // 20 - (5 * 2) = 10
    }

    @Test
    fun `recall obliterate capture and stun preserve ghost block only after block confirmation`() {
        val atk = UnitOnBoard("p_atk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 4, health = 4, maxHealth = 4, keywords = emptySet())
        val blk = UnitOnBoard("e_blk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 3, maxHealth = 3, keywords = emptySet())
        val captor = UnitOnBoard("p_cap", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 3, maxHealth = 3, keywords = emptySet())

        // Pre-confirmation (!blockCommitted): removal clears block -> attacker strikes Nexus
        val preConfirm = testBaseState(
            playerBoard = listOf(atk),
            enemyBoard = listOf(blk),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            playerInitiatedCombat = true,
            blockCommitted = false,
            attackingUnitIds = listOf("p_atk"),
            blockPairings = mapOf("p_atk" to "e_blk")
        )
        val preRecalled = CombatEngine.recallUnit(preConfirm, "e_blk", isEnemy = true, isSpell = true)
        assertTrue(preRecalled.blockPairings.isEmpty())
        assertTrue(preRecalled.confirmedBlockedAttackerIds.isEmpty())
        val resolvedPre = CombatEngine.finishPlayerAttackCombat(preRecalled)
        assertEquals(16, resolvedPre.enemyNexusHp)

        // Post-confirmation (blockCommitted = true): Recall / Obliterate / Capture / Stun all preserve wasBlocked = true
        val postConfirm = preConfirm.copy(
            playerBoard = listOf(atk, captor),
            blockCommitted = true,
            confirmedBlockedAttackerIds = setOf("p_atk")
        )

        val postRecall = CombatEngine.recallUnit(postConfirm, "e_blk", isEnemy = true, isSpell = true)
        assertEquals(20, CombatEngine.finishPlayerAttackCombat(postRecall).enemyNexusHp)
        assertTrue(postRecall.enemyHand.any { it.id == "card_cithria" })

        val postObliterate = CombatEngine.obliterateUnit(postConfirm, "e_blk", isEnemy = true, isSpell = true)
        assertEquals(20, CombatEngine.finishPlayerAttackCombat(postObliterate).enemyNexusHp)
        assertTrue(postObliterate.enemyGraveyard.isEmpty())

        val postCapture = CombatEngine.captureUnit(postConfirm, "p_cap", captorIsPlayer = true, targetId = "e_blk", targetIsEnemy = true, isSpell = true)
        assertEquals(20, CombatEngine.finishPlayerAttackCombat(postCapture).enemyNexusHp)
        // Killing captor releases e_blk back to enemy bench
        val captorSlain = CombatEngine.removeUnit(postCapture, "p_cap", isEnemy = false, logs = mutableListOf())
        assertTrue(captorSlain.enemyBoard.any { it.instanceId == "e_blk" })

        val postStun = CombatEngine.stunUnit(postConfirm, "e_blk", isEnemy = true, isSpell = true)
        assertEquals(20, CombatEngine.finishPlayerAttackCombat(postStun).enemyNexusHp)
    }

    @Test
    fun `attacker killed or stunned before strike deals zero Nexus damage and blocker deals zero Nexus damage`() {
        val atk = UnitOnBoard("e_atk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 5, health = 2, maxHealth = 2, keywords = emptySet())
        val blk = UnitOnBoard("p_blk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 4, health = 4, maxHealth = 4, keywords = emptySet())
        val mystic = HandCardInstance("hc_m", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)

        var stateKill = testBaseState(
            playerBoard = listOf(blk),
            enemyBoard = listOf(atk),
            playerHand = listOf(mystic),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            playerInitiatedCombat = false,
            blockCommitted = false,
            attackingUnitIds = listOf("e_atk"),
            blockPairings = mapOf("e_atk" to "p_blk")
        )
        stateKill = CombatEngine.playCardFromHand(stateKill, "hc_m", targetUnitId = "e_atk")
        stateKill = EnemyAIController.executeTurn(stateKill) // AI passes -> Mystic Shot kills e_atk
        stateKill = CombatEngine.commitPlayerBlock(stateKill)
        stateKill = EnemyAIController.executeTurn(stateKill)
        assertEquals(30, stateKill.playerNexusHp)
        assertEquals(20, stateKill.enemyNexusHp) // Blocker never deals Nexus damage
    }

    @Test
    fun `scenario 5D AI responds to Fast damage spell on blocker with Burst Barrier and saves blocker`() {
        val atk = UnitOnBoard("p_atk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 1, health = 4, maxHealth = 4, keywords = emptySet())
        val blk = UnitOnBoard("e_blk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 3, health = 2, maxHealth = 2, keywords = emptySet())
        val mystic = HandCardInstance("hc_m", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)

        var state = testBaseState(
            playerBoard = listOf(atk),
            enemyBoard = listOf(blk),
            playerHand = listOf(mystic),
            enemyHand = listOf(CardCatalog.getCard("card_prismatic_barrier")),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            playerInitiatedCombat = true,
            blockCommitted = true,
            attackingUnitIds = listOf("p_atk"),
            blockPairings = mapOf("p_atk" to "e_blk"),
            confirmedBlockedAttackerIds = setOf("p_atk")
        )

        state = CombatEngine.playCardFromHand(state, "hc_m", targetUnitId = "e_blk")
        assertEquals(PriorityHolder.ENEMY, state.priorityHolder)

        // AI casts Prismatic Barrier (Burst) targeting e_blk, keeping priority, then passes to resolve Mystic Shot
        state = EnemyAIController.executeTurn(state)
        assertTrue(state.enemyBoard.single().hasBarrier)
        state = EnemyAIController.executeTurn(state)
        // Barrier absorbed the 2-dmg Mystic Shot -> e_blk then took 1 combat strike damage from p_atk and survives with 1 HP!
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, state.phase)
        assertEquals(1, state.enemyBoard.size)
        assertEquals(1, state.enemyBoard.single().health)
        assertFalse(state.enemyBoard.single().hasBarrier)
    }

    @Test
    fun `scenario 5E SpellShield blocks one targeted spell across damage stun frostbite recall and single combat`() {
        val shielded = UnitOnBoard(
            "e_ss",
            CardCatalog.getCard("card_aurelion_sol"),
            ownerIsPlayer = false,
            power = 8,
            health = 8,
            maxHealth = 8,
            keywords = setOf(Keyword.SPELLSHIELD),
            hasSpellShield = true
        )
        val ally = UnitOnBoard("p_a", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 5, maxHealth = 5, keywords = emptySet())
        val mystic = HandCardInstance("hc_m", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)
        val frost = HandCardInstance("hc_fr", CardCatalog.getCard("card_flash_freeze"), effectiveCost = 3)
        val singleCombat = HandCardInstance("hc_sc", CardCatalog.getCard("card_single_combat"), effectiveCost = 2)

        var state = testBaseState(
            playerMana = 10,
            playerBoard = listOf(ally),
            enemyBoard = listOf(shielded),
            playerHand = listOf(mystic, frost, singleCombat)
        )

        // 1st spell (Flash Freeze Burst) pops SpellShield without frostbiting
        state = CombatEngine.playCardFromHand(state, "hc_fr", targetUnitId = "e_ss")
        assertFalse(state.enemyBoard.single().hasSpellShield)
        assertFalse(state.enemyBoard.single().isFrostbitten)

        // 2nd spell (Mystic Shot Fast) hits cleanly for 2 damage (8 -> 6 HP)
        state = CombatEngine.playCardFromHand(state, "hc_m", targetUnitId = "e_ss")
        state = EnemyAIController.executeTurn(state)
        assertEquals(6, state.enemyBoard.single().health)

        // Fresh SpellShield unit blocks Single Combat entirely (neither unit strikes!)
        val shielded2 = shielded.copy(instanceId = "e_ss2", hasSpellShield = true)
        var state2 = testBaseState(
            playerMana = 10,
            playerBoard = listOf(ally),
            enemyBoard = listOf(shielded2),
            playerHand = listOf(singleCombat)
        )
        state2 = CombatEngine.playCardFromHand(state2, "hc_sc", targetUnitId = "p_a|e_ss2")
        state2 = EnemyAIController.executeTurn(state2)
        assertFalse(state2.enemyBoard.single().hasSpellShield)
        assertEquals(5, state2.playerBoard.single().health)
        assertEquals(8, state2.enemyBoard.single().health)
    }

    @Test
    fun `scenario 5F stack resolves LIFO and fizzles when target left play`() {
        val ally = UnitOnBoard("p_a", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 2, health = 2, maxHealth = 2, keywords = emptySet())
        val enemy = UnitOnBoard("e_t", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 2, maxHealth = 2, keywords = emptySet())
        val mysticOnEnemy = HandCardInstance("hc_p1", CardCatalog.getCard("card_mystic_shot"), effectiveCost = 2)
        val barrierOnEnemy = HandCardInstance("hc_p2", CardCatalog.getCard("card_prismatic_barrier"), effectiveCost = 2)

        var state = testBaseState(
            playerBoard = listOf(ally),
            enemyBoard = listOf(enemy),
            playerHand = listOf(mysticOnEnemy, barrierOnEnemy),
            spellStack = listOf(
                com.example.engine.SpellStackEntry("st_bot", sourceIsPlayer = false, card = CardCatalog.getCard("card_mystic_shot"), targetUnitId = "p_a"),
                com.example.engine.SpellStackEntry("st_top", sourceIsPlayer = true, card = CardCatalog.getCard("card_mystic_shot"), targetUnitId = "e_t")
            ),
            phase = CombatPhase.SPELL_STACK_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            lastStackCaster = PriorityHolder.PLAYER
        )

        // Burst Barrier resolves immediately BEFORE stack resolves, saving p_a from bottom Mystic Shot!
        state = CombatEngine.playCardFromHand(state, "hc_p2", targetUnitId = "p_a")
        assertTrue(state.playerBoard.single().hasBarrier)

        // Resolve stack LIFO: st_top kills e_t; st_bot pops p_a's Barrier -> p_a survives!
        state = CombatEngine.resolveSpellStack(state)
        assertTrue(state.enemyBoard.isEmpty())
        assertEquals(1, state.playerBoard.size)
        assertEquals(2, state.playerBoard.single().health)
        assertFalse(state.playerBoard.single().hasBarrier)

        // Fizzle test: spell targeting missing unit logs fizzle and deals no damage
        val fizzleState = testBaseState(
            playerBoard = listOf(ally),
            enemyBoard = emptyList(),
            spellStack = listOf(
                com.example.engine.SpellStackEntry("st_f", sourceIsPlayer = true, card = CardCatalog.getCard("card_mystic_shot"), targetUnitId = "missing_id")
            ),
            phase = CombatPhase.SPELL_STACK_PHASE
        )
        val afterFizzle = CombatEngine.resolveSpellStack(fizzleState)
        assertEquals(20, afterFizzle.enemyNexusHp)
        assertTrue(afterFizzle.combatLog.any { it.contains("vô hiệu") })
    }

    @Test
    fun `concussive palm during block response summons Tail of the Dragon onto bench not as blocker`() {
        val atk = UnitOnBoard("e_atk", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 3, health = 3, maxHealth = 3, keywords = emptySet())
        val otherEnemy = UnitOnBoard("e_other", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 2, maxHealth = 2, keywords = emptySet())
        val palm = HandCardInstance("hc_palm", CardCatalog.getCard("card_concussive_palm"), effectiveCost = 4)

        var state = testBaseState(
            playerMana = 4,
            playerBoard = emptyList(),
            enemyBoard = listOf(atk, otherEnemy),
            playerHand = listOf(palm),
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            playerInitiatedCombat = false,
            blockCommitted = false,
            attackingUnitIds = listOf("e_atk")
        )

        state = CombatEngine.playCardFromHand(state, "hc_palm", targetUnitId = "e_other")
        state = EnemyAIController.executeTurn(state) // AI passes -> resolves Concussive Palm
        // Tail of the Dragon summoned onto playerBench, NOT auto-assigned to block e_atk
        assertEquals(1, state.playerBoard.size)
        assertEquals("card_tail_of_the_dragon", state.playerBoard.single().template.id)
        assertTrue(state.blockPairings.isEmpty())
    }

    @Test
    fun `challenger locks forced blocker across attack commit and prevents manual blocker removal`() {
        val challenger = UnitOnBoard("p_chal", CardCatalog.getCard("card_laurent_protege"), ownerIsPlayer = true, power = 2, health = 4, maxHealth = 4, keywords = setOf(Keyword.CHALLENGER))
        val weakEnemy = UnitOnBoard("e_weak", CardCatalog.getCard("card_sumpworks_mapper"), ownerIsPlayer = false, power = 1, health = 1, maxHealth = 1, keywords = setOf(Keyword.ELUSIVE))
        val bigEnemy = UnitOnBoard("e_big", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 4, health = 4, maxHealth = 4, keywords = emptySet())

        var state = testBaseState(
            playerBoard = listOf(challenger),
            enemyBoard = listOf(weakEnemy, bigEnemy),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_chal")
        )

        state = CombatEngine.selectChallengerAttacker(state, "p_chal")
        assertEquals("p_chal", state.selectedAttackerForChallenger)
        state = CombatEngine.assignChallengerTarget(state, "p_chal", "e_weak")
        assertEquals(mapOf("p_chal" to "e_weak"), state.challengedPairings)

        state = CombatEngine.commitPlayerAttack(state)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, state.phase)
        assertEquals(mapOf("p_chal" to "e_weak"), state.blockPairings)
        assertTrue(state.lockedBlockAttackerIds.contains("p_chal"))

        // AI block declaration preserves locked Challenger block (even though e_weak is Elusive!)
        state = EnemyAIController.executeTurn(state)
        assertEquals("e_weak", state.blockPairings["p_chal"])

        // Resolve Step 4 -> p_chal slays e_weak
        state = CombatEngine.passPriority(state)
        assertFalse(state.enemyBoard.any { it.instanceId == "e_weak" })
    }

    // --- PHẦN 2/3: TỪ KHÓA TẤN CÔNG, PHÒNG THỦ VÀ SÁT THƯƠNG ---

    @Test
    fun `part 2 3 quick double attack overwhelm barrier tough combinations resolve accurately`() {
        val qaOw = UnitOnBoard(
            "p_qa_ow",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 5,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.QUICK_ATTACK, Keyword.OVERWHELM)
        )
        val toughBlk = UnitOnBoard(
            "e_tough",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 4,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.TOUGH)
        )
        // Quick Attack + Overwhelm (5 power) vs Tough (2 HP -> lethalNeeded = 3):
        // Blocker dies in First Strike (deals 0 back), Nexus takes 5 - 3 = 2!
        val qaState = testBaseState(
            playerBoard = listOf(qaOw),
            enemyBoard = listOf(toughBlk),
            attackingUnitIds = listOf("p_qa_ow"),
            blockPairings = mapOf("p_qa_ow" to "e_tough")
        )
        val afterQaOw = CombatEngine.resolveCombatResolution(qaState, playerIsAttacking = true, logs = mutableListOf())
        assertEquals(18, afterQaOw.enemyNexusHp)
        assertEquals(3, afterQaOw.playerBoard.single().health)
        assertTrue(afterQaOw.enemyBoard.isEmpty())

        // Double Attack + Quick Attack still strikes twice (not 3 times); without Overwhelm, hit 2 hits nothing after blocker dies
        val daNoOw = UnitOnBoard(
            "p_da",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.DOUBLE_ATTACK, Keyword.QUICK_ATTACK)
        )
        val chumpBlk = UnitOnBoard("e_chump", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 1, health = 2, maxHealth = 2, keywords = emptySet())
        val afterDaNoOw = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(daNoOw),
                enemyBoard = listOf(chumpBlk),
                attackingUnitIds = listOf("p_da"),
                blockPairings = mapOf("p_da" to "e_chump")
            ),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(20, afterDaNoOw.enemyNexusHp)
        assertEquals(4, afterDaNoOw.playerBoard.single().health)

        // Double Attack + Impact unblocked strikes twice -> (4 + 1) * 2 = 10 Nexus damage (never 3 strikes with QA+DA)
        val daQaImpact = daNoOw.copy(
            keywords = setOf(Keyword.DOUBLE_ATTACK, Keyword.QUICK_ATTACK, Keyword.IMPACT),
            impactStacks = 1
        )
        val afterUnblockedDa = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(daQaImpact),
                enemyBoard = emptyList(),
                attackingUnitIds = listOf("p_da"),
                blockPairings = emptyMap()
            ),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(10, afterUnblockedDa.enemyNexusHp)

        // Overwhelm (6 power) vs Barrier (3 HP) & Tough+Barrier (3 HP): Barrier absorbs 3, Overwhelm deals 6 - 3 = 3 to Nexus
        val owAtk = UnitOnBoard("p_ow", CardCatalog.getCard("card_wild_mystics"), ownerIsPlayer = true, power = 6, health = 4, maxHealth = 4, keywords = setOf(Keyword.OVERWHELM))
        val barToughBlk = UnitOnBoard(
            "e_bt",
            CardCatalog.getCard("card_brightsteel_protector"),
            ownerIsPlayer = false,
            power = 2,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.BARRIER, Keyword.TOUGH),
            hasBarrier = true
        )
        val afterOwBar = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(owAtk),
                enemyBoard = listOf(barToughBlk),
                attackingUnitIds = listOf("p_ow"),
                blockPairings = mapOf("p_ow" to "e_bt")
            ),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(17, afterOwBar.enemyNexusHp)
        val survivingBlk = afterOwBar.enemyBoard.single()
        assertEquals(3, survivingBlk.health)
        assertFalse(survivingBlk.hasBarrier)
    }

    @Test
    fun `part 2 3 fearsome brash elusive sharpsight challenger and vulnerable blocking rules work`() {
        val elusiveAtk = UnitOnBoard("e_elu", CardCatalog.getCard("card_sumpworks_mapper"), ownerIsPlayer = false, power = 2, health = 2, maxHealth = 2, keywords = setOf(Keyword.ELUSIVE))
        val fearsomeAtk = UnitOnBoard("e_fear", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 3, health = 3, maxHealth = 3, keywords = setOf(Keyword.FEARSOME))
        val brashAtk = UnitOnBoard("e_brash", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 3, health = 3, maxHealth = 3, keywords = setOf(Keyword.BRASH))

        val normal22 = UnitOnBoard("p_22", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 2, health = 2, maxHealth = 2, keywords = emptySet())
        val sharpsight13 = UnitOnBoard("p_ss", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 1, health = 3, maxHealth = 3, keywords = setOf(Keyword.SHARPSIGHT))
        val brashCapable33 = UnitOnBoard("p_33", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 3, maxHealth = 3, keywords = emptySet())
        val cantBlock = UnitOnBoard("p_cb", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 5, health = 5, maxHealth = 5, keywords = setOf(Keyword.CANT_BLOCK))
        val immobile = UnitOnBoard("p_imm", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 4, health = 4, maxHealth = 4, keywords = setOf(Keyword.IMMOBILE))

        assertFalse(normal22.canBlockAttacker(elusiveAtk))
        assertTrue(sharpsight13.canBlockAttacker(elusiveAtk))
        assertFalse(normal22.canBlockAttacker(fearsomeAtk))
        assertTrue(brashCapable33.canBlockAttacker(fearsomeAtk))
        assertFalse(normal22.canBlockAttacker(brashAtk))
        assertTrue(sharpsight13.canBlockAttacker(brashAtk))
        assertFalse(cantBlock.canBlock)
        assertFalse(immobile.canAttack)
        assertFalse(immobile.canBlock)

        // Vulnerable enemy with Can't Block + Elusive can still be pulled by a normal (non-Challenger) attacker!
        val nonChallengerAtk = UnitOnBoard("p_norm", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 3, maxHealth = 3, keywords = emptySet())
        val vulnEnemy = UnitOnBoard(
            "e_vuln",
            CardCatalog.getCard("card_sumpworks_mapper"),
            ownerIsPlayer = false,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.VULNERABLE, Keyword.ELUSIVE, Keyword.CANT_BLOCK)
        )
        var state = testBaseState(
            playerBoard = listOf(nonChallengerAtk),
            enemyBoard = listOf(vulnEnemy),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_norm")
        )
        state = CombatEngine.selectChallengerAttacker(state, "p_norm")
        assertEquals("p_norm", state.selectedAttackerForChallenger)
        state = CombatEngine.assignChallengerTarget(state, "p_norm", "e_vuln")
        state = CombatEngine.commitPlayerAttack(state)
        assertEquals(mapOf("p_norm" to "e_vuln"), state.blockPairings)
        assertTrue(state.lockedBlockAttackerIds.contains("p_norm"))

        // AI attack uses Challenger / Vulnerable to lock player blockers cleanly
        val aiChal = UnitOnBoard("e_chal", CardCatalog.getCard("card_laurent_protege"), ownerIsPlayer = false, power = 4, health = 5, maxHealth = 5, keywords = setOf(Keyword.CHALLENGER))
        val playerWeak = UnitOnBoard("p_w", CardCatalog.getCard("card_sumpworks_mapper"), ownerIsPlayer = true, power = 2, health = 2, maxHealth = 2, keywords = setOf(Keyword.ELUSIVE))
        val aiAtkState = testBaseState(
            playerBoard = listOf(playerWeak),
            enemyBoard = listOf(aiChal),
            playerHasAttackToken = false,
            playerInitiatedCombat = false,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterAiDecl = EnemyAIController.executeTurn(aiAtkState)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, afterAiDecl.phase)
        assertEquals(mapOf("e_chal" to "p_w"), afterAiDecl.blockPairings)
        assertTrue(afterAiDecl.lockedBlockAttackerIds.contains("e_chal"))
        // Player cannot remove a Challenger-locked blocker
        val afterRemoveAttempt = CombatEngine.removePlayerBlocker(afterAiDecl, "e_chal")
        assertEquals("p_w", afterRemoveAttempt.blockPairings["e_chal"])
    }

    @Test
    fun `part 2 3 formidable frostbite buffs and lifesteal barrier fury combinations work`() {
        // Formidable + Frostbite: Frostbite drops Power to 0, but Formidable still strikes using Health!
        val formUnit = UnitOnBoard(
            "p_form",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.FORMIDABLE)
        )
        var state = testBaseState(playerBoard = listOf(formUnit))
        state = CombatEngine.frostbiteUnit(state, "p_form", isEnemy = false, isSpell = false)
        val frozenForm = state.playerBoard.single()
        assertEquals(0, frozenForm.effectivePower)
        assertEquals(5, frozenForm.strikePower)

        // Buffing a Frostbitten unit raises effectivePower back above 0 during the same round, and Frostbite clears at round end
        val buffedFrozen = CombatEngine.applySpirit(state, "p_form", isEnemy = false, stacks = 2).playerBoard.single()
        assertEquals(2, buffedFrozen.effectivePower) // 4 + 2 - 4 = 2
        assertEquals(7, buffedFrozen.strikePower) // Formidable uses 7 health
        val nextRoundState = CombatEngine.advanceToNextRound(state.copy(playerBoard = listOf(buffedFrozen)), mutableListOf())
        assertEquals(6, nextRoundState.playerBoard.single().effectivePower) // Frostbite expired, +2|+2 Spirit remains

        // Lifesteal vs Barrier deals 0 actual damage -> heals 0; Lifesteal+Overwhelm heals for Nexus overflow
        val lsAtk = UnitOnBoard("p_ls", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 5, health = 4, maxHealth = 4, keywords = setOf(Keyword.LIFESTEAL))
        val barBlk = UnitOnBoard("e_bar", CardCatalog.getCard("card_brightsteel_protector"), ownerIsPlayer = false, power = 1, health = 2, maxHealth = 2, keywords = setOf(Keyword.BARRIER), hasBarrier = true)
        val afterLsBlocked = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(lsAtk),
                enemyBoard = listOf(barBlk),
                attackingUnitIds = listOf("p_ls"),
                blockPairings = mapOf("p_ls" to "e_bar")
            ).copy(playerNexusHp = 20),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(20, afterLsBlocked.playerNexusHp) // 0 damage dealt through Barrier -> 0 heal

        val lsOwAtk = lsAtk.copy(keywords = setOf(Keyword.LIFESTEAL, Keyword.OVERWHELM))
        val afterLsOw = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(lsOwAtk),
                enemyBoard = listOf(barBlk),
                attackingUnitIds = listOf("p_ls"),
                blockPairings = mapOf("p_ls" to "e_bar")
            ).copy(playerNexusHp = 20),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(17, afterLsOw.enemyNexusHp) // 5 - 2 = 3 Overwhelm damage to Enemy Nexus
        assertEquals(23, afterLsOw.playerNexusHp) // Lifesteal heals for actual 3 Nexus damage dealt!

        // Lifesteal + Fury on Single Combat non-combat strike triggers both heal & Fury +1|+1
        val lsFuryAlly = UnitOnBoard("p_lsf", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 4, health = 5, maxHealth = 5, keywords = setOf(Keyword.LIFESTEAL, Keyword.FURY))
        val weakEnemy = UnitOnBoard("e_w", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 3, maxHealth = 3, keywords = emptySet())
        val singleCombat = HandCardInstance("hc_sc", CardCatalog.getCard("card_single_combat"), effectiveCost = 2)
        var scState = testBaseState(
            playerBoard = listOf(lsFuryAlly),
            enemyBoard = listOf(weakEnemy),
            playerHand = listOf(singleCombat)
        ).copy(playerNexusHp = 20)
        scState = CombatEngine.playCardFromHand(scState, "hc_sc", targetUnitId = "p_lsf|e_w")
        scState = EnemyAIController.executeTurn(scState)
        assertEquals(24, scState.playerNexusHp) // Lifesteal heals 4 on Single Combat strike!
        val survivor = scState.playerBoard.single()
        assertEquals(5, survivor.power) // Fury +1|+1
        assertEquals(4, survivor.health) // 5 - 2 + 1 = 4
    }

    @Test
    fun `part 2 3 spellshield multi target deathless revival and silence work properly`() {
        // Multi-target spell (Wings of the Cryophoenix): Enemy SpellShield blocks it; Ally SpellShield is NOT popped by own spell!
        val allySS = UnitOnBoard("p_ss", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 5, maxHealth = 5, keywords = setOf(Keyword.SPELLSHIELD), hasSpellShield = true)
        val enemySS = UnitOnBoard("e_ss", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 3, health = 4, maxHealth = 4, keywords = setOf(Keyword.SPELLSHIELD), hasSpellShield = true)
        val enemyNoSS = UnitOnBoard("e_no", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 5, maxHealth = 5, keywords = emptySet())
        val wings = HandCardInstance("hc_w", CardCatalog.getCard("card_wings_of_cryophoenix"), effectiveCost = 7)

        var multiState = testBaseState(
            playerBoard = listOf(allySS),
            enemyBoard = listOf(enemySS, enemyNoSS),
            playerHand = listOf(wings)
        )
        multiState = CombatEngine.playCardFromHand(multiState, "hc_w")
        multiState = EnemyAIController.executeTurn(multiState)
        assertTrue(multiState.playerBoard.single().hasSpellShield) // Allied spell did NOT break own SpellShield
        assertEquals(1, multiState.playerBoard.single().health) // Non-champion ally took 4 Wings dmg (5 -> 1)
        val postEnemySS = multiState.enemyBoard.first { it.instanceId == "e_ss" }
        assertFalse(postEnemySS.hasSpellShield)
        assertEquals(4, postEnemySS.health) // Blocked Wings damage
        assertEquals(1, multiState.enemyBoard.first { it.instanceId == "e_no" }.health) // Unshielded enemy took 4 dmg (5 -> 1)

        // Deathless + Double Attack: hit 1 kills enemy -> revives with NEW instanceId, 1 HP, Stunned, no Deathless; hit 2 does NOT strike revived unit & counts as death for Fury/Hallowed
        val daFuryAtk = UnitOnBoard("p_daf", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 3, health = 5, maxHealth = 5, keywords = setOf(Keyword.DOUBLE_ATTACK, Keyword.FURY))
        val deathlessBlk = UnitOnBoard("e_dl", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 2, health = 2, maxHealth = 2, keywords = setOf(Keyword.DEATHLESS), hasDeathless = true)
        val afterDeathlessCombat = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(daFuryAtk),
                enemyBoard = listOf(deathlessBlk),
                attackingUnitIds = listOf("p_daf"),
                blockPairings = mapOf("p_daf" to "e_dl")
            ),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        val revived = afterDeathlessCombat.enemyBoard.single()
        assertFalse(revived.instanceId == "e_dl") // New instanceId on Deathless revival
        assertEquals(1, revived.health)
        assertTrue(revived.isStunned)
        assertFalse(revived.hasDeathless)
        assertEquals(1, afterDeathlessCombat.enemyHallowedStacks + if (deathlessBlk.keywords.contains(Keyword.HALLOWED)) 0 else 1) // Verify next Fury check
        assertEquals(4, afterDeathlessCombat.playerBoard.single().power) // Fury triggered on Deathless death!

        // Silence removes keywords, buffs, Frostbite, Stun, Gloom, caps HP to base MaxHealth while preserving damage, and releases Captured units
        val capturedAlly = UnitOnBoard("p_cap", CardCatalog.getCard("card_cithria"), ownerIsPlayer = true, power = 2, health = 2, maxHealth = 2, keywords = emptySet())
        val buffedCaptor = UnitOnBoard(
            "e_boss",
            CardCatalog.getCard("card_cithria"), // Base 2|2
            ownerIsPlayer = false,
            power = 7,
            health = 5, // Base maxHealth is 2 -> capped to 2
            maxHealth = 6,
            keywords = setOf(Keyword.ELUSIVE, Keyword.TOUGH, Keyword.OVERWHELM),
            isFrostbitten = true,
            frostbiteReduction = 7,
            isStunned = true,
            gloomStacks = 2,
            capturedUnits = listOf(capturedAlly)
        )
        val afterSilence = CombatEngine.silenceUnit(
            testBaseState(enemyBoard = listOf(buffedCaptor)),
            "e_boss",
            isEnemy = true,
            isSpell = false
        )
        val silencedUnit = afterSilence.enemyBoard.single()
        assertTrue(silencedUnit.isSilenced)
        assertEquals(2, silencedUnit.effectivePower) // Base power 2, Frostbite & Gloom cleared
        assertEquals(2, silencedUnit.maxHealth)
        assertEquals(2, silencedUnit.health)
        assertFalse(silencedUnit.isStunned)
        assertFalse(silencedUnit.keywords.contains(Keyword.ELUSIVE))
        assertEquals(1, afterSilence.playerBoard.size) // Captured ally released!
    }

    @Test
    fun `part 2 3 ephemeral augment deep evolve fated lurk spirit gloom hallowed support scout work`() {
        // 1. Ephemeral + Quick Attack: kills blocker cleanly without dying in strike, THEN self-destructs immediately after striking;
        // a second non-striking Ephemeral ally on bench survives combat and dies at Round End triggering Hallowed
        val ephQaHallowed = UnitOnBoard(
            "p_eqh",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 4,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.EPHEMERAL, Keyword.QUICK_ATTACK, Keyword.HALLOWED)
        )
        val ephBenchHallowed = UnitOnBoard(
            "p_ebh",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.EPHEMERAL, Keyword.HALLOWED)
        )
        val weakBlk = UnitOnBoard("e_w", CardCatalog.getCard("card_cithria"), ownerIsPlayer = false, power = 3, health = 2, maxHealth = 2, keywords = emptySet())
        var ephState = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(ephQaHallowed, ephBenchHallowed),
                enemyBoard = listOf(weakBlk),
                attackingUnitIds = listOf("p_eqh"),
                blockPairings = mapOf("p_eqh" to "e_w")
            ),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        // Striker died immediately after striking via Ephemeral; bench Ephemeral survived combat
        assertEquals(listOf("p_ebh"), ephState.playerBoard.map { it.instanceId })
        assertEquals(1, ephState.playerHallowedStacks)
        ephState = CombatEngine.advanceToNextRound(ephState, mutableListOf())
        assertTrue(ephState.playerBoard.isEmpty()) // Bench Ephemeral died at Round End
        assertEquals(2, ephState.playerHallowedStacks) // Hallowed incremented on Round End death!

        // 2. Augment only triggers on created cards, Fated triggers once per round on targeted ally (not in hand)
        val augFatedUnit = UnitOnBoard(
            "p_af",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 3,
            maxHealth = 3,
            keywords = setOf(Keyword.AUGMENT, Keyword.FATED)
        )
        val nonCreatedBarrier = HandCardInstance("hc_nc", CardCatalog.getCard("card_prismatic_barrier"), effectiveCost = 2, isCreated = false)
        val createdBarrier = HandCardInstance("hc_cr", CardCatalog.getCard("card_prismatic_barrier"), effectiveCost = 2, isCreated = true)
        var afState = testBaseState(
            playerBoard = listOf(augFatedUnit),
            playerHand = listOf(nonCreatedBarrier, createdBarrier)
        )
        afState = CombatEngine.playCardFromHand(afState, "hc_nc", targetUnitId = "p_af")
        // Non-created spell: Augment did NOT trigger, Fated DID trigger (+1|+1 -> 3|4)
        assertEquals(3, afState.playerBoard.single().power)
        assertEquals(4, afState.playerBoard.single().health)
        afState = CombatEngine.playCardFromHand(afState, "hc_cr", targetUnitId = "p_af")
        // Created spell: Augment triggers (+1|+0 -> 4|4), Fated already used this round -> no 2nd +1|+1!
        assertEquals(4, afState.playerBoard.single().power)
        assertEquals(4, afState.playerBoard.single().health)

        // 3. Deep (+3|+3 when deck <= 15) and Evolve (>= 6 unique sharable positive keywords, duplicates ignored)
        val deepEvolveCard = CardCatalog.getCard("card_cithria").copy(
            id = "test_deep_evolve",
            name = "Deep Evolver",
            cost = 2,
            basePower = 2,
            baseHealth = 2,
            keywords = setOf(Keyword.DEEP, Keyword.EVOLVE, Keyword.TOUGH, Keyword.FURY)
        )
        val fourKwAlly = UnitOnBoard(
            "p_4kw",
            CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 2,
            health = 2,
            maxHealth = 2,
            keywords = setOf(Keyword.OVERWHELM, Keyword.QUICK_ATTACK, Keyword.ELUSIVE, Keyword.CHALLENGER)
        )
        val deepDeck16 = List(16) { HandCardInstance("d_$it", CardCatalog.getCard("card_cithria"), effectiveCost = 1) }
        var deState = testBaseState(
            playerBoard = listOf(fourKwAlly),
            playerHand = listOf(HandCardInstance("hc_de", deepEvolveCard, effectiveCost = 2))
        ).copy(playerDrawPile = deepDeck16)
        deState = CombatEngine.playCardFromHand(deState, "hc_de")
        val spawnedBeforeDeep = deState.playerBoard.first { it.template.id == "test_deep_evolve" }
        // 6 unique sharable positive keywords seen (OVERWHELM, QUICK_ATTACK, ELUSIVE, CHALLENGER, TOUGH, FURY) -> Evolve +2|+2 triggered!
        // Deck has 16 cards -> Deep NOT triggered yet!
        assertTrue(spawnedBeforeDeep.hasEvolveBonusApplied)
        assertFalse(spawnedBeforeDeep.hasDeepBonusApplied)
        assertEquals(4, spawnedBeforeDeep.power)
        assertEquals(4, spawnedBeforeDeep.health)
        // Draw 1 card -> deck drops from 16 to 15 -> Deep triggers retroactively (+3|+3 -> 7|7)!
        deState = CombatEngine.drawPlayerCard(deState)
        val afterDeepDraw = deState.playerBoard.first { it.template.id == "test_deep_evolve" }
        assertTrue(afterDeepDraw.hasDeepBonusApplied)
        assertEquals(7, afterDeepDraw.power)
        assertEquals(7, afterDeepDraw.health)

        // 4. Lurk (once per round if top card has Lurk) + Hallowed + Support + Scout
        val lurkScoutCard = CardCatalog.getCard("card_cithria").copy(
            id = "test_lurk_scout",
            name = "Lurk Scout",
            cost = 2,
            basePower = 2,
            baseHealth = 3,
            keywords = setOf(Keyword.LURK, Keyword.SCOUT)
        )
        val supporterCard = CardCatalog.getCard("card_cithria").copy(
            id = "test_supporter",
            name = "Hallowed Supporter",
            cost = 2,
            basePower = 2,
            baseHealth = 3,
            keywords = setOf(Keyword.SUPPORT, Keyword.SCOUT),
            supportPowerBonus = 2,
            supportHealthBonus = 1,
            supportKeyword = Keyword.OVERWHELM
        )
        val leftSupporter = UnitOnBoard("p_sup", supporterCard, ownerIsPlayer = true, power = 2, health = 3, maxHealth = 3, keywords = setOf(Keyword.SUPPORT, Keyword.SCOUT))
        val rightLurker = UnitOnBoard("p_lrk", lurkScoutCard, ownerIsPlayer = true, power = 2, health = 3, maxHealth = 3, keywords = setOf(Keyword.LURK, Keyword.SCOUT))
        val lurkerInHand = HandCardInstance("hc_lurk_hand", lurkScoutCard, effectiveCost = 2)
        val lurkerOnTop = HandCardInstance("hc_lurk_top", lurkScoutCard, effectiveCost = 2)

        var comboState = testBaseState(
            playerBoard = listOf(leftSupporter, rightLurker),
            playerHand = listOf(lurkerInHand),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_sup", "p_lrk")
        ).copy(
            playerHallowedStacks = 2,
            playerDrawPile = listOf(lurkerOnTop)
        )
        comboState = CombatEngine.commitPlayerAttack(comboState)
        // Hallowed buffed leftmost attacker (p_sup: 2 + 2 = 4); Lurk buffed rightLurker (+1 -> 3) & hand/deck;
        // Support buffed rightLurker (+2|+1 + Overwhelm -> 5|4)!
        assertEquals(4, comboState.playerBoard.first { it.instanceId == "p_sup" }.power)
        val supportedLurker = comboState.playerBoard.first { it.instanceId == "p_lrk" }
        assertEquals(5, supportedLurker.power)
        assertEquals(4, supportedLurker.health)
        assertTrue(supportedLurker.keywords.contains(Keyword.OVERWHELM))
        assertEquals(1, comboState.playerHand.single().bonusPower)
        // Finish Scout combat -> Player keeps Attack Token!
        comboState = CombatEngine.finishPlayerAttackCombat(comboState)
        assertTrue(comboState.playerHasAttackToken)
        assertTrue(comboState.scoutUsedThisRound)

        // Gloom (-1|-1 kills at 0 HP) & Support temporary buff expires at Round End
        val afterRoundEnd = CombatEngine.advanceToNextRound(comboState, mutableListOf())
        assertEquals(2, afterRoundEnd.playerBoard.first { it.instanceId == "p_sup" }.power) // Hallowed expired
        val lurkerPostRound = afterRoundEnd.playerBoard.first { it.instanceId == "p_lrk" }
        assertEquals(3, lurkerPostRound.power) // Lurk (+1) kept, Support (+2|+1, Overwhelm) expired!
        assertEquals(3, lurkerPostRound.health)
        assertFalse(lurkerPostRound.keywords.contains(Keyword.OVERWHELM))

        val afterGloom = CombatEngine.applyGloom(afterRoundEnd, "p_sup", isEnemy = false, stacks = 3)
        assertFalse(afterGloom.playerBoard.any { it.instanceId == "p_sup" }) // 3 HP - 3 Gloom = 0 -> died!
    }

    @Test
    fun `part 3 attack strike nexus strike impact distinction and garen two strike level up`() {
        // 1. Impact deals Nexus damage on Attack Strike, NOT Nexus Strike; unblocked/Overwhelm excess counts as Nexus Strike
        val impactAtk = UnitOnBoard(
            instanceId = "p_imp",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 3,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.IMPACT),
            impactStacks = 2
        )
        val sturdyBlk = UnitOnBoard(
            instanceId = "e_blk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 1,
            health = 5,
            maxHealth = 5,
            keywords = emptySet()
        )
        val afterBlockedImpact = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(impactAtk),
                enemyBoard = listOf(sturdyBlk),
                attackingUnitIds = listOf("p_imp"),
                blockPairings = mapOf("p_imp" to "e_blk")
            ).copy(enemyNexusHp = 20),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(18, afterBlockedImpact.enemyNexusHp) // Impact dealt 2 Nexus damage
        assertEquals(1, afterBlockedImpact.playerTotalStrikes)
        assertEquals(0, afterBlockedImpact.playerNexusStrikesCount) // Impact alone is NOT a Nexus Strike!

        // 2. Non-combat Strike (Single Combat): triggers Strike & Garen level-up on 2 strikes WITHOUT triggering Attack effects (Hallowed)
        val garen = UnitOnBoard(
            instanceId = "p_garen",
            template = CardCatalog.getCard("card_garen"), // 5|5, levels up on 2 strikes
            ownerIsPlayer = true,
            power = 5,
            health = 5,
            maxHealth = 5,
            keywords = setOf(Keyword.REGENERATION)
        )
        val weakEnemy1 = UnitOnBoard("e_1", CardCatalog.getCard("card_cithria"), false, 2, 2, 2, emptySet())
        val weakEnemy2 = UnitOnBoard("e_2", CardCatalog.getCard("card_cithria"), false, 2, 2, 2, emptySet())
        val sc1 = HandCardInstance("hc_sc1", CardCatalog.getCard("card_single_combat"), effectiveCost = 2)
        val sc2 = HandCardInstance("hc_sc2", CardCatalog.getCard("card_single_combat"), effectiveCost = 2)

        var garenState = testBaseState(
            playerBoard = listOf(garen),
            enemyBoard = listOf(weakEnemy1, weakEnemy2),
            playerHand = listOf(sc1, sc2),
            playerMana = 10
        ).copy(playerHallowedStacks = 3, round = 1)

        garenState = CombatEngine.playCardFromHand(garenState, "hc_sc1", targetUnitId = "p_garen|e_1")
        garenState = EnemyAIController.executeTurn(garenState)
        assertEquals(3, garenState.playerHallowedStacks) // Attack Hallowed NOT consumed by Strike spell
        assertEquals(5, garenState.playerBoard.single().power)
        assertEquals(1, garenState.playerTotalStrikes)
        assertEquals(1, garenState.playerBoard.single().levelProgress)
        assertFalse(garenState.playerBoard.single().isLeveledUp)
        // Garen took 2 damage during strike 1 (5 -> 3 HP)
        assertEquals(3, garenState.playerBoard.single().health)

        // Strike 2 -> Garen reaches 2 strikes -> Levels up (+1|+1) while PRESERVING 2 damage taken!
        garenState = CombatEngine.playCardFromHand(garenState.copy(priorityHolder = PriorityHolder.PLAYER), "hc_sc2", targetUnitId = "p_garen|e_2")
        garenState = EnemyAIController.executeTurn(garenState)
        val leveledGaren = garenState.playerBoard.single()
        assertTrue(leveledGaren.isLeveledUp)
        assertEquals(6, leveledGaren.power)
        assertEquals(2, leveledGaren.health) // 5 - 2 (sc1) - 2 (sc2) + 1 (LevelUp) = 2 (4 total damage preserved on 6 maxHealth)
        assertEquals(6, leveledGaren.maxHealth)
    }

    @Test
    fun `part 3 drain barrier tough spellshield rally free attack and skill stack work accurately`() {
        val drainSpell = CardTemplate(
            id = "test_drain_3",
            name = "Hút Linh Hồn",
            region = Region.SHADOW_ISLES,
            type = CardType.SPELL,
            cost = 2,
            spellSpeed = SpellSpeed.FAST,
            description = "Hút Cạn 3.",
            effectType = CardEffectType.DRAIN,
            effectValue = 3
        )
        val barrierEnemy = UnitOnBoard("e_bar", CardCatalog.getCard("card_cithria"), false, 2, 4, 4, setOf(Keyword.BARRIER), hasBarrier = true)
        val toughEnemy = UnitOnBoard("e_tough", CardCatalog.getCard("card_cithria"), false, 2, 4, 4, setOf(Keyword.TOUGH))

        var drainState = testBaseState(
            enemyBoard = listOf(barrierEnemy, toughEnemy),
            playerHand = listOf(
                HandCardInstance("hc_d1", drainSpell, effectiveCost = 2),
                HandCardInstance("hc_d2", drainSpell, effectiveCost = 2)
            ),
            playerMana = 10
        ).copy(playerNexusHp = 20, playerMaxHp = 30)

        // 1. Drain vs Barrier -> 0 actual damage dealt -> 0 heal!
        drainState = CombatEngine.playCardFromHand(drainState, "hc_d1", targetUnitId = "e_bar")
        drainState = CombatEngine.resolveSpellStack(drainState)
        assertFalse(drainState.enemyBoard.first { it.instanceId == "e_bar" }.hasBarrier)
        assertEquals(4, drainState.enemyBoard.first { it.instanceId == "e_bar" }.health)
        assertEquals(20, drainState.playerNexusHp)

        // 2. Drain 3 vs Tough -> deals 2 actual damage -> heals +2 (20 -> 22)!
        drainState = CombatEngine.playCardFromHand(drainState.copy(priorityHolder = PriorityHolder.PLAYER), "hc_d2", targetUnitId = "e_tough")
        drainState = CombatEngine.resolveSpellStack(drainState)
        assertEquals(2, drainState.enemyBoard.first { it.instanceId == "e_tough" }.health)
        assertEquals(22, drainState.playerNexusHp)

        // 3. Skill enters stack and allows opponent Burst Barrier response before resolving
        val dmgSkill = CardTemplate(
            id = "test_skill_3",
            name = "Tia Năng Lượng",
            region = Region.DEMACIA,
            type = CardType.SPELL,
            cost = 0,
            spellSpeed = SpellSpeed.SKILL,
            description = "Kỹ Năng. Gây 3 sát thương.",
            effectType = CardEffectType.DEAL_DAMAGE_ANY,
            effectValue = 3
        )
        var skillState = testBaseState(
            enemyBoard = listOf(UnitOnBoard("e_target", CardCatalog.getCard("card_cithria"), false, 2, 3, 3, emptySet())),
            enemyHand = listOf(CardCatalog.getCard("card_prismatic_barrier")),
            enemyMana = 3
        )
        skillState = CombatEngine.queueSkillToStack(skillState, dmgSkill, sourceIsPlayer = true, targetUnitId = "e_target")
        assertEquals(CombatPhase.SPELL_STACK_PHASE, skillState.phase)
        assertEquals(PriorityHolder.ENEMY, skillState.priorityHolder)
        // AI responds with Burst Prismatic Barrier (keeps priority on Burst), then passes on stack to resolve LIFO
        skillState = EnemyAIController.executeTurn(skillState)
        assertTrue(skillState.enemyBoard.single().hasBarrier)
        skillState = EnemyAIController.executeTurn(skillState)
        assertTrue(skillState.spellStack.isEmpty())
        assertEquals(3, skillState.enemyBoard.single().health) // Barrier blocked the Skill!

        // 4. Free Attack starts combat WITHOUT requiring or consuming Attack Token, triggers Attack buffs,
        //    and Rally grants Attack Token afterward
        val freeAttackSpell = CardTemplate(
            id = "test_free_atk",
            name = "Xung Phong Tự Do",
            region = Region.DEMACIA,
            type = CardType.SPELL,
            cost = 1,
            spellSpeed = SpellSpeed.SLOW,
            description = "Đồng minh chỉ định Tấn Công Tự Do.",
            effectType = CardEffectType.FREE_ATTACK
        )
        val rallySpell = CardTemplate(
            id = "test_rally",
            name = "Hiệu Triệu Tập Hợp",
            region = Region.DEMACIA,
            type = CardType.SPELL,
            cost = 1,
            spellSpeed = SpellSpeed.FAST,
            description = "Tập Hợp.",
            effectType = CardEffectType.RALLY
        )
        val freeAlly = UnitOnBoard("p_free", CardCatalog.getCard("card_cithria"), true, 3, 3, 3, emptySet())
        var freeState = testBaseState(
            playerBoard = listOf(freeAlly),
            playerHand = listOf(
                HandCardInstance("hc_fa", freeAttackSpell, effectiveCost = 1),
                HandCardInstance("hc_rl", rallySpell, effectiveCost = 1)
            ),
            playerMana = 10
        ).copy(playerHasAttackToken = false, playerHallowedStacks = 2, enemyNexusHp = 20)

        freeState = CombatEngine.playCardFromHand(freeState, "hc_fa", targetUnitId = "p_free")
        freeState = EnemyAIController.executeTurn(freeState) // AI passes on stack -> Free Attack launches
        assertTrue(freeState.isFreeAttack)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, freeState.phase)
        assertEquals(5, freeState.playerBoard.single().power) // Hallowed triggered on Free Attack!
        assertEquals(listOf("p_free"), freeState.attackingUnitIds)
        freeState = EnemyAIController.executeTurn(freeState) // AI commits 0 blockers
        freeState = CombatEngine.commitPlayerBlock(freeState) // Resolve combat
        assertEquals(15, freeState.enemyNexusHp)
        assertFalse(freeState.playerHasAttackToken) // Attack Token remained false!
        assertFalse(freeState.isFreeAttack)

        // Rally grants Attack Token
        freeState = CombatEngine.playCardFromHand(freeState.copy(priorityHolder = PriorityHolder.PLAYER), "hc_rl")
        freeState = CombatEngine.resolveSpellStack(freeState)
        assertTrue(freeState.playerHasAttackToken)
    }

    @Test
    fun `part 3 equipment lifecycle and dynamic left to right combat and leave play rules`() {
        // 1. Equipment attaches +2|+1 & Challenger; replacement returns old gear to hand; bearer Death & Recall return gear to hand; Silence strips gear
        val ally = UnitOnBoard("p_host", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())
        val gear1 = CardCatalog.getCard("card_forged_blade") // +2|+1, Challenger
        val gear2 = CardTemplate(
            id = "test_gear_2",
            name = "Khiên Cổ Ngữ",
            region = Region.DEMACIA,
            type = CardType.EQUIPMENT,
            cost = 1,
            basePower = 1,
            baseHealth = 2,
            keywords = setOf(Keyword.TOUGH),
            description = "+1|+2 và Cứng Cáp."
        )
        var eqState = testBaseState(
            playerBoard = listOf(ally),
            playerHand = listOf(
                HandCardInstance("hc_g1", gear1, effectiveCost = 2),
                HandCardInstance("hc_g2", gear2, effectiveCost = 1)
            ),
            playerMana = 10
        )
        eqState = CombatEngine.playCardFromHand(eqState, "hc_g1", targetUnitId = "p_host")
        val equipped1 = eqState.playerBoard.single()
        assertEquals(4, equipped1.power)
        assertEquals(3, equipped1.health)
        assertTrue(equipped1.keywords.contains(Keyword.CHALLENGER))

        // Replace with gear2 -> returns gear1 to hand cleanly, stats swap cleanly (2+1=3 power, 2+2=4 health, Tough not Challenger)
        eqState = CombatEngine.playCardFromHand(eqState.copy(priorityHolder = PriorityHolder.PLAYER), "hc_g2", targetUnitId = "p_host")
        assertTrue(eqState.playerHand.any { it.template.id == "card_forged_blade" })
        val equipped2 = eqState.playerBoard.single()
        assertEquals(3, equipped2.power)
        assertEquals(4, equipped2.health)
        assertTrue(equipped2.keywords.contains(Keyword.TOUGH))
        assertFalse(equipped2.keywords.contains(Keyword.CHALLENGER))

        // Bearer killed -> gear2 returns to hand
        val afterKill = CombatEngine.removeUnit(eqState.copy(playerHand = emptyList()), "p_host", isEnemy = false, logs = mutableListOf())
        assertEquals(listOf("test_gear_2"), afterKill.playerHand.map { it.template.id })

        // Bearer recalled / obliterated / captured -> gear2 returns to hand cleanly without Last Breath
        val lbRallyItem = ItemTemplate(
            id = "test_lbr",
            name = "Cờ Tập Hợp",
            rarity = Rarity.RARE,
            category = ItemCategory.TRIGGER,
            description = "Trăn Trối: Tập Hợp.",
            onLastBreathRally = true
        )
        val unitWithLbAndGear = equipped2.copy(
            keywords = equipped2.keywords + Keyword.HALLOWED,
            attachedItems = listOf(lbRallyItem)
        )
        val baseLeaveState = testBaseState(
            playerBoard = listOf(unitWithLbAndGear),
            enemyBoard = listOf(UnitOnBoard("e_cap", CardCatalog.getCard("card_cithria"), false, 4, 4, 4, emptySet())),
            attackingUnitIds = listOf("e_atk"),
            blockPairings = mapOf("e_atk" to "p_host")
        ).copy(
            confirmedBlockedAttackerIds = setOf("e_atk"),
            blockCommitted = true,
            playerHasAttackToken = false,
            playerHallowedStacks = 0
        )

        val afterRecall = CombatEngine.recallUnit(baseLeaveState, "p_host", isEnemy = false, isSpell = false)
        assertEquals(setOf("card_cithria", "test_gear_2"), afterRecall.playerHand.map { it.template.id }.toSet())
        assertFalse(afterRecall.playerHasAttackToken) // Last Breath did NOT fire on Recall
        assertEquals(0, afterRecall.playerHallowedStacks)
        assertTrue(afterRecall.confirmedBlockedAttackerIds.contains("e_atk")) // wasBlocked preserved!

        val afterObliterate = CombatEngine.obliterateUnit(baseLeaveState, "p_host", isEnemy = false, isSpell = false)
        assertEquals(listOf("test_gear_2"), afterObliterate.playerHand.map { it.template.id })
        assertFalse(afterObliterate.playerHasAttackToken) // Last Breath did NOT fire on Obliterate
        assertEquals(0, afterObliterate.playerHallowedStacks)
        assertTrue(afterObliterate.confirmedBlockedAttackerIds.contains("e_atk"))

        val afterCapture = CombatEngine.captureUnit(baseLeaveState, captorId = "e_cap", captorIsPlayer = false, targetId = "p_host", targetIsEnemy = false, isSpell = false)
        assertEquals(listOf("test_gear_2"), afterCapture.playerHand.map { it.template.id })
        assertFalse(afterCapture.playerHasAttackToken) // Last Breath did NOT fire on Capture
        assertTrue(afterCapture.confirmedBlockedAttackerIds.contains("e_atk"))

        // True death triggers BOTH Last Breath Rally & Hallowed
        val afterTrueDeath = CombatEngine.removeUnit(baseLeaveState, "p_host", isEnemy = false, logs = mutableListOf())
        assertTrue(afterTrueDeath.playerHasAttackToken)
        assertEquals(1, afterTrueDeath.playerHallowedStacks)

        // Silence strips gear stats & keywords cleanly
        val afterSilence = CombatEngine.silenceUnit(baseLeaveState, "p_host", isEnemy = false, isSpell = false).playerBoard.single()
        assertNull(afterSilence.equippedGear)
        assertEquals(2, afterSilence.power)
        assertEquals(2, afterSilence.maxHealth)

        // 2. Dynamic left-to-right combat order: Pair 1 kills Frostbitten enemy -> Pair 2 loses Ashe 2★ Frostbite aura dynamically!
        val atk1 = UnitOnBoard("p_1", CardCatalog.getCard("card_cithria"), true, 2, 3, 3, emptySet())
        val atk2 = UnitOnBoard("p_2", CardCatalog.getCard("card_cithria"), true, 2, 3, 3, emptySet())
        val frzEnemy = UnitOnBoard("e_frz", CardCatalog.getCard("card_cithria"), false, 2, 2, 2, emptySet(), isFrostbitten = true)
        val afterDynamicPairs = CombatEngine.resolveCombatResolution(
            testBaseState(
                playerBoard = listOf(atk1, atk2),
                enemyBoard = listOf(frzEnemy),
                attackingUnitIds = listOf("p_1", "p_2"),
                blockPairings = mapOf("p_1" to "e_frz") // p_1 +1 aura = 3 kills e_frz -> p_2 then strikes unblocked with 2 instead of 3!
            ).copy(championId = "champ_ashe", starLevel = 2, enemyNexusHp = 20),
            playerIsAttacking = true,
            logs = mutableListOf()
        )
        assertEquals(18, afterDynamicPairs.enemyNexusHp)

        // 3. Silenced Champion cannot level up, and Curse Shackles purge unshackles linked unit
        val silencedGaren = UnitOnBoard("p_sg", CardCatalog.getCard("card_garen"), true, 5, 5, 5, setOf(Keyword.SILENCE), isSilenced = true, levelProgress = 5)
        val shackledAlly = UnitOnBoard("p_sh", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, setOf(Keyword.SHACKLED), isShackled = true)
        val shacklesCard = HandCardInstance("hc_shk", CardCatalog.getCard("card_curse_shackles"), effectiveCost = 2, linkedShackledUnitId = "p_sh")
        var checkState = CombatEngine.checkChampionLevelUps(
            testBaseState(playerBoard = listOf(silencedGaren, shackledAlly), playerHand = listOf(shacklesCard), playerMana = 5).copy(round = 4),
            mutableListOf()
        )
        assertFalse(checkState.playerBoard.first { it.instanceId == "p_sg" }.isLeveledUp)
        checkState = CombatEngine.playCardFromHand(checkState, "hc_shk")
        assertFalse(checkState.playerBoard.first { it.instanceId == "p_sh" }.isShackled)
        assertTrue(checkState.playerBoard.first { it.instanceId == "p_sh" }.canAttack)
    }

    @Test
    fun `globals-vi_vn json loads and maps nameRef values cleanly while keeping enum identifiers intact`() {
        val assetCandidates = listOf(
            java.io.File("src/main/assets/globals-vi_vn.json"),
            java.io.File("app/src/main/assets/globals-vi_vn.json")
        )
        val jsonFile = assetCandidates.firstOrNull { it.exists() }
        assertNotNull("Expected app/src/main/assets/globals-vi_vn.json to exist", jsonFile)

        LocalizationRepository.clearForTesting()
        val loaded = LocalizationRepository.loadFromJson(jsonFile!!.readText(Charsets.UTF_8))
        assertTrue(loaded)
        assertTrue(LocalizationRepository.isLoaded)

        // Internal nameRefs remain exact English Riot identifiers
        assertEquals("Overwhelm", Keyword.OVERWHELM.nameRef)
        assertEquals("Frostbite", Keyword.FROSTBITE.nameRef)
        assertEquals("QuickStrike", Keyword.QUICK_ATTACK.nameRef)
        assertEquals("Elusive", Keyword.ELUSIVE.nameRef)
        assertEquals("Slow", SpellSpeed.SLOW.nameRef)
        assertEquals("Fast", SpellSpeed.FAST.nameRef)
        assertEquals("Burst", SpellSpeed.BURST.nameRef)
        assertEquals("Common", Rarity.COMMON.nameRef)
        assertEquals("Rare", Rarity.RARE.nameRef)
        assertEquals("Epic", Rarity.EPIC.nameRef)
        assertEquals("ShadowIsles", Region.SHADOW_ISLES.nameRef)

        // Display names & descriptions resolve to official Vietnamese localization
        assertEquals("Áp Đảo", Keyword.OVERWHELM.displayName)
        assertEquals("Đóng Băng", Keyword.FROSTBITE.displayName)
        assertEquals("Tấn Công Nhanh", Keyword.QUICK_ATTACK.displayName)
        assertEquals("Bóng Ma", Keyword.ELUSIVE.displayName)
        assertEquals("Chỉ có thể bị chặn bởi một đơn vị có Bóng Ma.", Keyword.ELUSIVE.description)
        assertEquals("Chậm", SpellSpeed.SLOW.displayName)
        assertEquals("Nhanh", SpellSpeed.FAST.displayName)
        assertEquals("Siêu Tốc", SpellSpeed.BURST.displayName)
        assertEquals("THƯỜNG", Rarity.COMMON.displayName)
        assertEquals("HIẾM", Rarity.RARE.displayName)
        assertEquals("SỬ THI", Rarity.EPIC.displayName)
        assertEquals("HUYỀN THOẠI", Rarity.LEGENDARY.displayName)
        assertEquals("Quần Đảo Bóng Đêm", Region.SHADOW_ISLES.displayName)
        assertEquals("Piltover & Zaun", Region.PILTOVER_ZAUN.displayName)

        // Blank descriptions in JSON cleanly retain non-empty fallback tooltip copy
        assertEquals("Biển Sâu", Keyword.DEEP.displayName)
        assertTrue(Keyword.DEEP.description.isNotBlank())
    }

    @Test
    fun `qa_test_1 starting hand draws 6 cards with at least 1 Anivia guaranteed`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val aniviaChamp = CardCatalog.getChampion("champ_anivia")
        val loadout = ChampionLoadout(championId = "champ_anivia", isUnlocked = true, starLevel = 1, championLevel = 1)
        val runState = RunState(
            runId = "qa_test_1",
            adventureId = adv.id,
            championId = "champ_anivia",
            currentNodeId = node.id,
            completedNodeIds = emptyList(),
            currentNexusHp = 40,
            maxNexusHp = 40,
            gold = 100,
            rerollsRemaining = 2,
            revivesRemaining = 1,
            activePowerIds = emptyList(),
            runDeck = aniviaChamp.baseDeck
        )

        repeat(20) {
            val encounter = CombatEngine.createEncounter(runState, loadout, adv, node)
            assertEquals(6, encounter.playerHand.size)
            assertTrue(
                "Opening 6-card hand must contain at least 1 Champion",
                encounter.playerHand.any { it.template.isChampion && it.template.id == "card_anivia" }
            )
        }
    }

    @Test
    fun `qa_test_2 enlightened requires regularMana 10 at Round Start and ignores spellMana`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val anivia = UnitOnBoard("u_anv", CardCatalog.getCard("card_anivia"), true, 3, 5, 5, setOf(Keyword.ELEMENTAL))
        val enemy = UnitOnBoard("e1", CardCatalog.getCard("card_cithria"), false, 0, 5, 5, emptySet())
        val fillerCards = List(5) { idx ->
            HandCardInstance("p_deck_$idx", CardCatalog.getCard("card_cithria"), effectiveCost = 1)
        }
        val enemyPool = List(5) { CardCatalog.getCard("card_cithria") }

        // Round Start with regularMana = 8 -> next round refills to 9 regularMana + 3 spellMana = 12 total -> NOT Enlightened!
        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 8,
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 8, // Triggers AI block so combat tests Glacial Storm Nexus damage cleanly
            enemyMaxHp = 20,
            playerMana = 3,
            playerMaxMana = 8,
            playerSpellMana = 3,
            isEnlightened = false,
            playerHasAttackToken = true,
            playerDrawPile = fillerCards,
            enemyDrawPool = enemyPool,
            playerBoard = listOf(anivia),
            enemyBoard = listOf(enemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 1
        )

        val round9State = CombatEngine.passPriority(state)
        assertEquals(9, round9State.playerMana)
        assertEquals(3, round9State.playerSpellMana)
        assertFalse("9 regular + 3 spellMana (12 total) must NOT trigger Enlightened", round9State.isEnlightened)
        val aniviaR9 = round9State.playerBoard.single()
        assertFalse(aniviaR9.isLeveledUp)

        // Attacking at Lv1 pushes Glacial Storm Skill (1 dmg) onto spellStack -> AI blocks -> resolves skill + combat
        val attackDeclared = CombatEngine.toggleUnitAttack(
            CombatEngine.enterAttackDeclaration(round9State.copy(playerHasAttackToken = true, priorityHolder = PriorityHolder.PLAYER)),
            "u_anv"
        )
        val afterCommit = CombatEngine.commitPlayerAttack(attackDeclared)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, afterCommit.phase)
        assertEquals(1, afterCommit.spellStack.size)
        assertEquals("skill_glacial_storm", afterCommit.spellStack.single().card.id)
        assertEquals(SpellSpeed.SKILL, afterCommit.spellStack.single().card.spellSpeed)
        assertEquals(1, afterCommit.elementalCardsPlayedCount)

        val afterAiBlock = EnemyAIController.executeTurn(afterCommit)
        assertTrue(afterAiBlock.blockCommitted)
        assertEquals(1, afterAiBlock.blockPairings.size)
        assertEquals(PriorityHolder.ENEMY, afterAiBlock.priorityHolder)
        assertEquals(1, afterAiBlock.spellStack.size)
        val afterSkillAndCombat = EnemyAIController.executeTurn(afterAiBlock)
        assertTrue(afterSkillAndCombat.spellStack.isEmpty())
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, afterSkillAndCombat.phase)
        assertEquals(7, afterSkillAndCombat.enemyNexusHp) // Glacial Storm Lv1 dealt exact 1 dmg to Enemy Nexus (8 -> 7)

        // Next Round Start: regularMana reaches 10 -> isEnlightened == true & Anivia levels up to 4|6 (Skill deals 2)
        val round10State = CombatEngine.passPriority(
            afterSkillAndCombat.copy(phase = CombatPhase.MAIN_ACTION_PHASE, priorityHolder = PriorityHolder.PLAYER, consecutivePasses = 1)
        )
        assertEquals(10, round10State.playerMana)
        assertTrue(round10State.isEnlightened)
        val aniviaLv2 = round10State.playerBoard.single()
        assertTrue(aniviaLv2.isLeveledUp)
        assertEquals(4, aniviaLv2.power)
        assertEquals(6, aniviaLv2.health)
    }

    @Test
    fun `qa_test_3 eggnivia death at Lv1 strips 6_8 buff and revives at base 3_5 on Enlightened Round Start`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        // Lv1 Anivia buffed to 6/8
        val buffedAniviaLv1 = UnitOnBoard(
            instanceId = "u_anv",
            template = CardCatalog.getCard("card_anivia"),
            ownerIsPlayer = true,
            power = 6,
            health = 8,
            maxHealth = 8,
            keywords = setOf(Keyword.ELEMENTAL),
            isLeveledUp = false
        )
        val executioner = UnitOnBoard("e_kill", CardCatalog.getCard("card_garen"), false, 10, 10, 10, emptySet())

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 9,
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 9,
            playerMaxMana = 9,
            isEnlightened = false,
            playerBoard = listOf(buffedAniviaLv1),
            enemyBoard = listOf(executioner),
            attackingUnitIds = listOf("u_anv"),
            blockPairings = mapOf("u_anv" to "e_kill"),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Anivia Lv1 (6/8) dies -> transforms into Eggnivia (0/2) with diedAtLevel = 1
        state = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = mutableListOf())
        val egg = state.playerBoard.single()
        assertEquals("card_eggnivia", egg.template.id)
        assertEquals(0, egg.power)
        assertEquals(2, egg.health)
        assertEquals(1, egg.diedAtLevel)

        // Round Start reaches 10 regularMana -> Eggnivia revives at exact Lv1 base stats (3/5), stripping 6/8 buffs!
        val nextRound = CombatEngine.passPriority(
            state.copy(phase = CombatPhase.MAIN_ACTION_PHASE, priorityHolder = PriorityHolder.PLAYER, consecutivePasses = 1)
        )
        assertTrue(nextRound.isEnlightened)
        val revived = nextRound.playerBoard.single()
        assertEquals("card_anivia", revived.template.id)
        assertFalse(revived.isLeveledUp)
        assertEquals(3, revived.power)
        assertEquals(5, revived.health)
        assertEquals(5, revived.maxHealth)
    }

    @Test
    fun `qa_test_4 champion spell transformation converts hand Anivia to Champion Spell and reverts when Eggnivia`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val anivia1 = HandCardInstance("hc_anv_1", CardCatalog.getCard("card_anivia"), effectiveCost = 6)
        val anivia2 = HandCardInstance("hc_anv_2", CardCatalog.getCard("card_anivia"), effectiveCost = 6)
        val anivia3 = HandCardInstance("hc_anv_3", CardCatalog.getCard("card_anivia"), effectiveCost = 6)
        val enemy = UnitOnBoard("e1", CardCatalog.getCard("card_garen"), false, 8, 8, 8, emptySet())

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 10,
            playerMaxMana = 10,
            playerHand = listOf(anivia1, anivia2, anivia3),
            enemyBoard = listOf(enemy),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Play first Anivia -> remaining hand copies transform into Anivia: Cuồng Phong
        state = CombatEngine.playCardFromHand(state, "hc_anv_1")
        assertEquals(1, state.playerBoard.size)
        assertTrue(state.playerHand.all { it.template.id == "card_anivia_harsh_winds" })

        // Cast one Anivia: Cuồng Phong -> frostbites enemy & shuffles 1 copy of Anivia into deck
        state = state.copy(priorityHolder = PriorityHolder.PLAYER, playerMana = 5)
        state = CombatEngine.playCardFromHand(state, "hc_anv_2", targetUnitId = "e1")
        assertTrue(state.enemyBoard.single().isFrostbitten)
        assertTrue(state.playerDrawPile.any { it.template.id == "card_anivia" })

        // Anivia on board dies -> turns into Eggnivia -> remaining hc_anv_3 reverts back to Anivia!
        val boardAniviaId = state.playerBoard.single().instanceId
        state = CombatEngine.removeUnit(state, boardAniviaId, isEnemy = false, logs = mutableListOf())
        assertEquals("card_eggnivia", state.playerBoard.single().template.id)
        val reverted = state.playerHand.single { it.instanceId == "hc_anv_3" }
        assertEquals("card_anivia", reverted.template.id)
    }

    @Test
    fun `qa_test_5 wings of cryophoenix scales with spell power and spares Champions and Wild Mystics`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        val allyAnivia = UnitOnBoard("u_anv", CardCatalog.getCard("card_anivia"), true, 3, 5, 5, setOf(Keyword.ELEMENTAL))
        val allyMystics = UnitOnBoard("u_mys", CardCatalog.getCard("card_wild_mystics"), true, 2, 2, 2, setOf(Keyword.OVERWHELM))
        val allyProphet = UnitOnBoard("u_pro", CardCatalog.getCard("card_mortal_prophet"), true, 0, 7, 7, emptySet())
        val enemyChamp = UnitOnBoard("e_garen", CardCatalog.getCard("card_garen"), false, 5, 5, 5, emptySet())
        val enemyFollower = UnitOnBoard("e_fol", CardCatalog.getCard("card_laurent_protege"), false, 2, 7, 7, emptySet())
        val enemyTough = UnitOnBoard("e_tough", CardCatalog.getCard("card_flame_eyed_caprine"), false, 3, 7, 7, setOf(Keyword.TOUGH))
        val wings = HandCardInstance("hc_wings", CardCatalog.getCard("card_wings_of_cryophoenix"), effectiveCost = 7)

        var state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = listOf(CardCatalog.getRelic("relic_ludens_tempest")), // +1 dmg
            activePowers = listOf(CardCatalog.getPower("pow_spell_fury")), // +1 dmg -> 4 + 2 = 6 total dmg
            mutators = emptyList(),
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 7,
            playerMaxMana = 7,
            playerHand = listOf(wings),
            playerBoard = listOf(allyAnivia, allyMystics, allyProphet),
            enemyBoard = listOf(enemyChamp, enemyFollower, enemyTough),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        state = CombatEngine.playCardFromHand(state, "hc_wings")
        state = EnemyAIController.executeTurn(state)

        // Champions & Thầy Tu Hoang Dã take 0 damage
        assertEquals(5, state.playerBoard.first { it.instanceId == "u_anv" }.health)
        assertEquals(2, state.playerBoard.first { it.instanceId == "u_mys" }.health)
        assertEquals(5, state.enemyBoard.first { it.instanceId == "e_garen" }.health)

        // Regular followers take 4 + 1 (Luden) + 1 (Power) = 6 damage (Tough reduces to 5)
        assertEquals(1, state.playerBoard.first { it.instanceId == "u_pro" }.health)
        assertEquals(1, state.enemyBoard.first { it.instanceId == "e_fol" }.health)
        assertEquals(2, state.enemyBoard.first { it.instanceId == "e_tough" }.health)
    }

    @Test
    fun `qa_test_6 clash of giants discounts to 3 mana summons from 4-titan pool and revenant fearsome works`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val clash = HandCardInstance("hc_clash", CardCatalog.getCard("card_clash_of_giants"), effectiveCost = 13)

        val stateWith10Elemental = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerMana = 3,
            playerMaxMana = 10,
            playerElementalAndSkillsPlayed = 10,
            playerHand = listOf(clash),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )

        // Base 13 - max 10 elemental discount = 3 Mana
        assertEquals(3, stateWith10Elemental.effectivePlayCost(clash))
        var resolved = CombatEngine.playCardFromHand(stateWith10Elemental, "hc_clash")
        resolved = EnemyAIController.executeTurn(resolved)

        val validTitanIds = setOf("card_uzgar", "card_rhasa", "card_rumul", "card_lost_soul")
        assertEquals(2, resolved.playerBoard.size)
        assertTrue(resolved.playerBoard.all { validTitanIds.contains(it.template.id) })

        // Verify Rhasa (kills 2 lowest-Power enemies) and Rumul (+0|+4 to highest Health ally)
        val allyTank = UnitOnBoard("u_tank", CardCatalog.getCard("card_mortal_prophet"), true, 0, 5, 5, emptySet())
        val lowEnemy1 = UnitOnBoard("e_l1", CardCatalog.getCard("card_cithria"), false, 1, 4, 4, emptySet())
        val lowEnemy2 = UnitOnBoard("e_l2", CardCatalog.getCard("card_cithria"), false, 2, 4, 4, emptySet())
        val highEnemy = UnitOnBoard("e_hi", CardCatalog.getCard("card_garen"), false, 6, 5, 5, emptySet())

        var titanState = stateWith10Elemental.copy(
            playerMana = 10,
            playerBoard = listOf(allyTank),
            enemyBoard = listOf(lowEnemy1, lowEnemy2, highEnemy),
            playerHand = listOf(
                HandCardInstance("hc_rhasa", CardCatalog.getCard("card_rhasa"), effectiveCost = 8),
                HandCardInstance("hc_rumul", CardCatalog.getCard("card_rumul"), effectiveCost = 8)
            )
        )
        titanState = CombatEngine.playCardFromHand(titanState, "hc_rhasa").copy(priorityHolder = PriorityHolder.PLAYER, playerMana = 8)
        assertEquals(listOf("e_hi"), titanState.enemyBoard.map { it.instanceId }) // Both lowest-Power enemies destroyed!

        titanState = CombatEngine.playCardFromHand(titanState, "hc_rumul")
        // Rumul enters at 8/8 (highest current HP vs 5/5 tank and 8/6 Rhasa) and grants +0|+4 -> 8/12!
        val buffedUnit = titanState.playerBoard.first { it.template.id == "card_rumul" }
        assertEquals(12, buffedUnit.health)

        // Verify Uzgar Regeneration heals to full at Round End
        val damagedUzgar = UnitOnBoard("u_uzgar", CardCatalog.getCard("card_uzgar"), true, 8, 2, 8, setOf(Keyword.REGENERATION, Keyword.CHALLENGER))
        val regenRound = CombatEngine.passPriority(
            titanState.copy(playerBoard = listOf(damagedUzgar), phase = CombatPhase.MAIN_ACTION_PHASE, priorityHolder = PriorityHolder.PLAYER, consecutivePasses = 1)
        )
        assertEquals(8, regenRound.playerBoard.single().health)

        // Verify Linh Hồn Lạc Lối dying creates Song Kiếm Ác Quỷ (4/3 Fearsome) & enemy < 3 Power cannot block it
        val lostSoul = UnitOnBoard("u_lost", CardCatalog.getCard("card_lost_soul"), true, 5, 4, 4, emptySet())
        val afterSoulDeath = CombatEngine.removeUnit(
            stateWith10Elemental.copy(playerHand = emptyList(), playerBoard = listOf(lostSoul)),
            unitId = "u_lost",
            isEnemy = false,
            logs = mutableListOf()
        )
        val revenantInHand = afterSoulDeath.playerHand.single()
        assertEquals("card_twinblade_revenant", revenantInHand.template.id)
        val revenantUnit = UnitOnBoard("u_rev", revenantInHand.template, true, 4, 3, 3, setOf(Keyword.FEARSOME))
        val weakBlocker = UnitOnBoard("e_weak", CardCatalog.getCard("card_cithria"), false, 2, 5, 5, emptySet())
        val strongBlocker = UnitOnBoard("e_strong", CardCatalog.getCard("card_cithria"), false, 3, 5, 5, emptySet())
        assertFalse(weakBlocker.canBlockAttacker(revenantUnit))
        assertTrue(strongBlocker.canBlockAttacker(revenantUnit))
    }

    @Test
    fun `aniviaGlacialStormAutoAppliesAfterEnemyBlockWithNoActionAtLv1AndLv2`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        // 1. Lv1 Anivia attacks, AI pulls blocker with no spells -> auto-applies 1 damage Glacial Storm + combat
        val aniviaLv1 = UnitOnBoard("u_anv1", CardCatalog.getCard("card_anivia"), true, 3, 5, 5, setOf(Keyword.ELEMENTAL), isLeveledUp = false)
        val enemyBlocker = UnitOnBoard("e_blk", CardCatalog.getCard("card_cithria"), false, 1, 5, 5, emptySet())
        val lv1State = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 6,
            playerNexusHp = 40,
            playerMaxHp = 40,
            enemyNexusHp = 6,
            enemyMaxHp = 20,
            playerMana = 6,
            playerMaxMana = 6,
            playerHasAttackToken = true,
            playerBoard = listOf(aniviaLv1),
            enemyBoard = listOf(enemyBlocker),
            enemyHand = emptyList(),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER
        )
        val lv1Commit = CombatEngine.commitPlayerAttack(
            CombatEngine.toggleUnitAttack(CombatEngine.enterAttackDeclaration(lv1State), "u_anv1")
        )
        val lv1AfterAiBlock = EnemyAIController.executeTurn(lv1Commit)
        assertTrue(lv1AfterAiBlock.blockCommitted)
        assertEquals(PriorityHolder.ENEMY, lv1AfterAiBlock.priorityHolder)
        val lv1Resolved = EnemyAIController.executeTurn(lv1AfterAiBlock)
        assertTrue(lv1Resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, lv1Resolved.phase)
        assertEquals(5, lv1Resolved.enemyNexusHp) // Lv1 Glacial Storm deals 1 dmg
        assertEquals(1, lv1Resolved.enemyBoard.single().health) // 5 - 1 (storm) - 3 (anivia) = 1

        // 2. Lv2 Anivia attacks with empty enemy board (no action) -> auto-applies 2 damage Glacial Storm + 4 attack dmg
        val aniviaLv2 = UnitOnBoard("u_anv2", CardCatalog.getCard("card_anivia"), true, 4, 6, 6, setOf(Keyword.ELEMENTAL), isLeveledUp = true)
        val lv2State = lv1State.copy(
            round = 10,
            enemyNexusHp = 20,
            playerMana = 10,
            playerMaxMana = 10,
            isEnlightened = true,
            playerBoard = listOf(aniviaLv2),
            enemyBoard = emptyList()
        )
        val lv2Commit = CombatEngine.commitPlayerAttack(
            CombatEngine.toggleUnitAttack(CombatEngine.enterAttackDeclaration(lv2State), "u_anv2")
        )
        val lv2AfterAiBlock = EnemyAIController.executeTurn(lv2Commit)
        assertTrue(lv2AfterAiBlock.blockCommitted)
        assertEquals(PriorityHolder.ENEMY, lv2AfterAiBlock.priorityHolder)
        val lv2Resolved = EnemyAIController.executeTurn(lv2AfterAiBlock)
        assertTrue(lv2Resolved.spellStack.isEmpty())
        assertEquals(CombatPhase.MAIN_ACTION_PHASE, lv2Resolved.phase)
        assertEquals(14, lv2Resolved.enemyNexusHp) // 20 - 2 (storm Lv2) - 4 (anivia Lv2) = 14

        // 3. When AI DOES cast a reactive spell during block, player still retains response priority
        val enemyReactiveBlocker = UnitOnBoard("e_blk2", CardCatalog.getCard("card_cithria"), false, 2, 2, 2, emptySet())
        val reactiveState = lv1State.copy(
            enemyNexusHp = 20,
            enemyMana = 4,
            enemyMaxMana = 4,
            enemyHand = listOf(CardCatalog.getCard("card_steel_tempest")),
            enemyBoard = listOf(enemyReactiveBlocker)
        )
        val reactiveCommit = CombatEngine.commitPlayerAttack(
            CombatEngine.toggleUnitAttack(CombatEngine.enterAttackDeclaration(reactiveState), "u_anv1")
        )
        val afterAiReactive = EnemyAIController.executeTurn(reactiveCommit)
        assertEquals(PriorityHolder.PLAYER, afterAiReactive.priorityHolder)
        assertEquals(2, afterAiReactive.spellStack.size)
    }

    @Test
    fun `generalizedAiRulesAndCombatSimulatorWorkEndToEnd`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }

        // 1. CombatSimulator pre-calculates pending Glacial Storm killing 1-HP blocker & Overwhelm bleedthrough
        val overwhelmAtk = UnitOnBoard("p_ovw", CardCatalog.getCard("card_wild_mystics"), true, 5, 4, 4, setOf(Keyword.OVERWHELM))
        val fragileBlk = UnitOnBoard("e_frag", CardCatalog.getCard("card_cithria"), false, 2, 1, 1, emptySet())
        val stormEntry = SpellStackEntry(
            id = "st_storm",
            sourceIsPlayer = true,
            card = CardCatalog.getCard("skill_glacial_storm").copy(effectValue = 1),
            targetUnitId = null,
            damageBonus = 0
        )
        val simUnderStorm = CombatSimulator.simulatePair(overwhelmAtk, fragileBlk, listOf(stormEntry))
        assertTrue(simUnderStorm.blockerDiedOnStack)
        assertEquals(5, simUnderStorm.damageToDefenderNexus)

        // 2. Lethal Check (Open Attack): AI has unit in hand + lethal on board -> attacks immediately without developing unit
        val aiAttacker = UnitOnBoard("e_garen", CardCatalog.getCard("card_garen"), false, 6, 5, 5, setOf(Keyword.OVERWHELM))
        val openAtkState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 5,
            playerNexusHp = 6,
            playerMaxHp = 40,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            enemyMana = 5,
            enemyMaxMana = 5,
            enemyHasAttackToken = true,
            enemyBoard = listOf(aiAttacker),
            enemyHand = listOf(CardCatalog.getCard("card_cithria")),
            playerBoard = emptyList(),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterOpenAtk = EnemyAIController.executeTurn(openAtkState)
        assertEquals(CombatPhase.COMBAT_DECLARE_BLOCK_PHASE, afterOpenAtk.phase)
        assertEquals(listOf("e_garen"), afterOpenAtk.attackingUnitIds)
        assertEquals(1, afterOpenAtk.enemyHand.size) // Did NOT waste priority developing hand unit before open attack!

        // 3. 3-Tier Blocking: Tier 3 Chump Block activates when incoming damage drops AI Nexus <= 4 HP
        val nonOverwhelmThreat = UnitOnBoard("p_big", CardCatalog.getCard("card_uzgar"), true, 5, 8, 8, emptySet())
        val weakBlocker = UnitOnBoard("e_chump", CardCatalog.getCard("card_cithria"), false, 1, 1, 1, emptySet())
        val chumpBlockState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 5,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 8, // 8 - 5 = 3 <= 4 HP safety threshold -> forces Chump Block!
            enemyMaxHp = 20,
            playerBoard = listOf(nonOverwhelmThreat),
            enemyBoard = listOf(weakBlocker),
            attackingUnitIds = listOf("p_big"),
            playerInitiatedCombat = true,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterChumpBlock = EnemyAIController.executeTurn(chumpBlockState)
        assertTrue(afterChumpBlock.blockCommitted)
        assertEquals(mapOf("p_big" to "e_chump"), afterChumpBlock.blockPairings)

        // 4. Defensive Trick: High-Threat Champion (ThreatScore >= 8.0) threatened by Mystic Shot -> AI casts Barrier to save
        val aiChamp = UnitOnBoard("e_champ", CardCatalog.getCard("card_garen"), false, 5, 2, 5, setOf(Keyword.REGENERATION))
        assertTrue(CombatSimulator.threatScore(aiChamp) >= 8.0)
        val mysticOnStack = SpellStackEntry(
            id = "st_m1",
            sourceIsPlayer = true,
            card = CardCatalog.getCard("card_mystic_shot"),
            targetUnitId = "e_champ",
            damageBonus = 0
        )
        val trickState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 4,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            enemyMana = 3,
            enemyMaxMana = 4,
            enemyBoard = listOf(aiChamp),
            enemyHand = listOf(CardCatalog.getCard("card_prismatic_barrier")),
            spellStack = listOf(mysticOnStack),
            phase = CombatPhase.SPELL_STACK_PHASE,
            priorityHolder = PriorityHolder.ENEMY
        )
        val afterTrick = EnemyAIController.executeTurn(trickState)
        assertTrue(afterTrick.enemyBoard.single().hasBarrier)
        assertEquals(0, afterTrick.enemyHand.size)
    }

    @Test
    fun `roundHallowedBuffs is immutable in CombatState and resets at round end without cross-state pollution`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val baseUnit = UnitOnBoard("p_1", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())
        val baseState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 1,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerHallowedStacks = 3,
            playerBoard = listOf(baseUnit),
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            attackingUnitIds = listOf("p_1")
        )

        val simBranch = CombatEngine.commitPlayerAttack(baseState)
        assertEquals(3, simBranch.roundHallowedBuffs["p_1"])
        assertEquals(5, simBranch.playerBoard.single().power)
        // Original state remains completely unpolluted
        assertTrue(baseState.roundHallowedBuffs.isEmpty())
        assertEquals(2, baseState.playerBoard.single().power)

        val nextRound = CombatEngine.advanceToNextRound(simBranch, mutableListOf())
        assertTrue(nextRound.roundHallowedBuffs.isEmpty())
        assertEquals(2, nextRound.playerBoard.single().power)
    }

    @Test
    fun `enemyAi tracks consecutiveAiActionsInRound in CombatState and enforces canPlayCard fast spell rules`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val aiUnit = UnitOnBoard("e_1", CardCatalog.getCard("card_garen"), false, 5, 5, 5, emptySet())
        val playerUnit = UnitOnBoard("p_1", CardCatalog.getCard("card_cithria"), true, 2, 2, 2, emptySet())

        // 1. Burst spell keeps priority -> increments consecutiveAiActionsInRound; switching priority or round resets to 0
        val burstState = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            round = 2,
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            enemyMana = 4,
            enemyMaxMana = 4,
            enemyHasAttackToken = false,
            enemyBoard = listOf(aiUnit),
            playerBoard = listOf(playerUnit),
            enemyHand = listOf(CardCatalog.getCard("card_flash_freeze")),
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.ENEMY,
            consecutiveAiActionsInRound = 0
        )
        val afterBurst = EnemyAIController.executeTurn(burstState)
        assertEquals(PriorityHolder.ENEMY, afterBurst.priorityHolder)
        assertEquals(1, afterBurst.consecutiveAiActionsInRound)

        val afterPass = EnemyAIController.executeTurn(afterBurst)
        assertEquals(PriorityHolder.PLAYER, afterPass.priorityHolder)
        assertEquals(0, afterPass.consecutiveAiActionsInRound)

        // 2. Safety limit triggers failsafe when consecutiveAiActionsInRound >= 24
        val limitState = burstState.copy(consecutiveAiActionsInRound = 24)
        val afterLimit = EnemyAIController.executeTurn(limitState)
        assertEquals(PriorityHolder.PLAYER, afterLimit.priorityHolder)
        assertEquals(0, afterLimit.consecutiveAiActionsInRound)

        // 3. Fast spell limit in combat is enforced by CombatEngine.canPlayCard for AI
        val fastSpell = CardCatalog.getCard("card_mystic_shot")
        val combatFastAllowed = burstState.copy(
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            enemyFastSpellUsedInCombat = false
        )
        val combatFastBlocked = combatFastAllowed.copy(enemyFastSpellUsedInCombat = true)
        assertTrue(CombatEngine.canPlayCard(combatFastAllowed, fastSpell, isPlayer = false))
        assertFalse(CombatEngine.canPlayCard(combatFastBlocked, fastSpell, isPlayer = false))
    }

    @Test
    fun `impact nexus damage applies after main combat strike damage and kill triggers`() {
        val adv = AdventureCatalog.getAdventure("adv_demacia_outskirts")
        val node = adv.mapNodes.first { it.id == "d_fight_1a" }
        val atk = UnitOnBoard(
            instanceId = "p_imp_ow",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = true,
            power = 5,
            health = 4,
            maxHealth = 4,
            keywords = setOf(Keyword.OVERWHELM, Keyword.IMPACT),
            impactStacks = 2
        )
        val blk = UnitOnBoard(
            instanceId = "e_blk",
            template = CardCatalog.getCard("card_cithria"),
            ownerIsPlayer = false,
            power = 1,
            health = 2,
            maxHealth = 2,
            keywords = emptySet()
        )
        val logs = mutableListOf<String>()
        val state = CombatState(
            encounterNode = node,
            adventure = adv,
            championId = "champ_anivia",
            starLevel = 1,
            equippedRelics = emptyList(),
            activePowers = emptyList(),
            mutators = emptyList(),
            playerNexusHp = 30,
            playerMaxHp = 30,
            enemyNexusHp = 20,
            enemyMaxHp = 20,
            playerBoard = listOf(atk),
            enemyBoard = listOf(blk),
            attackingUnitIds = listOf("p_imp_ow"),
            blockPairings = mapOf("p_imp_ow" to "e_blk")
        )
        val resolved = CombatEngine.resolveCombatResolution(state, playerIsAttacking = true, logs = logs)
        // Overwhelm excess = 5 - 2 = 3, plus Impact = 2 -> total 5 damage (20 -> 15)
        assertEquals(15, resolved.enemyNexusHp)
        // Blocker death log ("đã bị hạ gục") appears BEFORE Impact Nexus damage log
        val killLogIdx = logs.indexOfFirst { it.contains("đã bị hạ gục") }
        val impactLogIdx = logs.indexOfFirst { it.contains("Tác Động") }
        assertTrue("Expected kill log before Impact log, logs=$logs", killLogIdx in 0 until impactLogIdx)
    }

    @Test
    fun `gameViewModel serializes activeRun saves sequentially and persists the latest state`() = runTest {
        val dao = FakeGameDao()
        val repo = GameRepository(dao, testDispatcher)
        repo.ensureSeeded()

        val vm = GameViewModel(repo)
        advanceUntilIdle()

        vm.quickStartBattle()
        vm.abandonSavedRun()
        advanceUntilIdle()
        assertNull(dao.getSavedRun())

        vm.startAdventure("adv_demacia_outskirts")
        advanceUntilIdle()
        val savedAfterStart = dao.getSavedRun()
        assertNotNull(savedAfterStart)
        val activeRunId = vm.uiState.value.activeRun?.runId
        assertNotNull(activeRunId)
        assertTrue(savedAfterStart!!.runJson.contains("\"adventureId\":\"adv_demacia_outskirts\""))
        assertTrue(savedAfterStart.runJson.contains("\"runId\":\"$activeRunId\""))
    }
}


