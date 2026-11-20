package com.example.engine

import com.example.model.AdventureConfig
import com.example.model.CardCatalog
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.ChampionLoadout
import com.example.model.HandCardInstance
import com.example.model.ItemTemplate
import com.example.model.Keyword
import com.example.model.MapNode
import com.example.model.NodeType
import com.example.model.PowerTemplate
import com.example.model.RelicTemplate
import com.example.model.RunState
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard
import java.util.UUID
import kotlin.random.Random

object CombatEngine {

    internal const val EMPTY_DECK_FATIGUE_DAMAGE = 2

    internal fun recordRoundHallowedBuff(state: CombatState, unitId: String, amount: Int): CombatState {
        if (amount <= 0) return state
        val updated = state.roundHallowedBuffs + (unitId to ((state.roundHallowedBuffs[unitId] ?: 0) + amount))
        return state.copy(roundHallowedBuffs = updated)
    }

    fun canPlayCard(state: CombatState, card: HandCardInstance): Boolean =
        canPlayCard(
            state = state,
            card = card.template,
            isPlayer = true,
            effectiveCost = state.effectivePlayCost(card)
        )

    fun canPlayCard(
        state: CombatState,
        card: CardTemplate,
        isPlayer: Boolean,
        effectiveCost: Int = card.cost
    ): Boolean {
        val expectedPriority = if (isPlayer) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        if (state.priorityHolder != expectedPriority) return false
        if (state.phase == CombatPhase.GAME_OVER || state.phase == CombatPhase.COMBAT_RESOLUTION_PHASE) return false

        val type = card.type
        val speed = card.spellSpeed

        // Check mana
        val isSpellLike = type == CardType.SPELL || type == CardType.CURSE
        val available = if (isPlayer) {
            if (isSpellLike) state.playerMana + state.playerSpellMana else state.playerMana
        } else {
            if (isSpellLike) state.enemyMana + state.enemySpellMana else state.enemyMana
        }
        if (available < effectiveCost) return false

        // Check board space for units/landmarks
        val board = if (isPlayer) state.playerBoard else state.enemyBoard
        if ((type == CardType.UNIT || type == CardType.CHAMPION || type == CardType.LANDMARK) &&
            board.size >= 6
        ) return false

        // Equipment requires at least 1 unit on board
        if (type == CardType.EQUIPMENT && board.isEmpty()) return false

        // Check timing windows per Spell Speed & Combat Spec (Phần 1/3)
        val fastUsedInCombat = if (isPlayer) state.playerFastSpellUsedInCombat else state.enemyFastSpellUsedInCombat
        return when (state.phase) {
            CombatPhase.MAIN_ACTION_PHASE -> when (speed) {
                SpellSpeed.BURST -> true
                SpellSpeed.FAST -> true
                SpellSpeed.FOCUS, SpellSpeed.SLOW, SpellSpeed.NONE, SpellSpeed.SKILL -> state.spellStack.isEmpty()
            }
            CombatPhase.SPELL_STACK_PHASE -> {
                speed == SpellSpeed.BURST || speed == SpellSpeed.FAST
            }
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> when (speed) {
                SpellSpeed.BURST -> true
                SpellSpeed.FAST -> !fastUsedInCombat
                else -> false
            }
            CombatPhase.COMBAT_RESOLUTION_PHASE,
            CombatPhase.GAME_OVER -> false
        }
    }

    private fun nextId(): String = UUID.randomUUID().toString().take(8)

    enum class ConstellationEffectPolarity {
        BUFF,   // Tăng chỉ số, hồi máu, tạo lá chắn, triệu hồi...
        DEBUFF  // Đóng băng, choáng, gây sát thương, giảm chỉ số, triệu hồi về tay...
    }

    /**
     * QUY TẮC SỨC MẠNH CHÒM SAO:
     * Mọi hiệu ứng Sức Mạnh Chòm Sao của người chơi nếu mang tính chất DEBUFF
     * tuyệt đối chỉ được phép chọn mục tiêu trong danh sách quân địch (enemyBoard),
     * không bao giờ tác động lên quân bản thân (playerBoard).
     */
    internal fun getConstellationTargets(
        state: CombatState,
        polarity: ConstellationEffectPolarity,
        restrictToIds: Set<String>? = null
    ): List<UnitOnBoard> {
        val pool = when (polarity) {
            ConstellationEffectPolarity.DEBUFF -> state.enemyBoard
            ConstellationEffectPolarity.BUFF -> state.playerBoard
        }
        return pool.filter { unit ->
            unit.template.type != CardType.LANDMARK &&
                (restrictToIds == null || restrictToIds.contains(unit.instanceId))
        }
    }

    /**
     * Kích hoạt các Sức Mạnh Chòm Sao khi kẻ địch tuyên bố tấn công.
     */
    internal fun applyEnemyAttackConstellationPowers(
        state: CombatState,
        enemyAttackerIds: List<String>,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // Anivia 6★: Khi kẻ địch tấn công, Đóng Băng kẻ địch mạnh nhất (chỉ tác động lên phe địch)
        if (s.championId == "champ_anivia" && s.starLevel >= 6) {
            val attackerSet = enemyAttackerIds.toSet()
            val attackingEnemies = getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF, attackerSet)
            val enemyCandidates = attackingEnemies.ifEmpty {
                getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF)
            }
            val strongestEnemy = enemyCandidates
                .filter { !it.isFrostbitten }
                .maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
                ?: enemyCandidates.maxWithOrNull(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })

            if (strongestEnemy != null) {
                s = frostbiteUnit(
                    state = s,
                    unitId = strongestEnemy.instanceId,
                    isEnemy = true,
                    isSpell = false,
                    fromAllyEffect = false,
                    logs = logs
                )
                logs.add("Tường Băng (Anivia 6★): Đóng Băng kẻ địch mạnh nhất (${strongestEnemy.template.name})!")
            }
        }

        return s
    }

    /**
     * Kiểm tra bài phép có bắt buộc người chơi chỉ định mục tiêu trước khi thi triển hay không.
     */
    fun requiresPlayerTarget(card: CardTemplate): Boolean {
        if (card.type != CardType.SPELL && card.type != CardType.CURSE) return false
        return when (card.effectType) {
            CardEffectType.DEAL_DAMAGE_ANY,
            CardEffectType.STUN_TARGET,
            CardEffectType.FROSTBITE_TARGET,
            CardEffectType.FROSTBITE_TWO,
            CardEffectType.ANIVIA_CHAMPION_SPELL,
            CardEffectType.RECALL_OR_STUN,
            CardEffectType.GIVE_BARRIER_AND_DRAW,
            CardEffectType.RALLY_AND_SHIELD,
            CardEffectType.SINGLE_COMBAT_STRIKE,
            CardEffectType.DRAIN,
            CardEffectType.FREE_ATTACK -> true
            else -> false
        }
    }

    /**
     * Trả về cặp (enemyTargetIds, allyTargetIds) hợp lệ để người chơi chọn mục tiêu cho bài phép.
     */
    fun getValidSpellTargetIds(state: CombatState, card: CardTemplate): Pair<Set<String>, Set<String>> {
        val enemyIds = state.enemyBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
        val allyIds = state.playerBoard.filter { it.template.type != CardType.LANDMARK }.map { it.instanceId }.toSet()
        return when (card.effectType) {
            CardEffectType.STUN_TARGET,
            CardEffectType.FROSTBITE_TARGET,
            CardEffectType.FROSTBITE_TWO,
            CardEffectType.ANIVIA_CHAMPION_SPELL,
            CardEffectType.RECALL_OR_STUN -> enemyIds to emptySet()

            CardEffectType.GIVE_BARRIER_AND_DRAW,
            CardEffectType.RALLY_AND_SHIELD -> emptySet<String>() to allyIds

            CardEffectType.FREE_ATTACK ->
                emptySet<String>() to state.playerBoard.filter { it.template.type != CardType.LANDMARK && it.canAttack }.map { it.instanceId }.toSet()

            CardEffectType.DEAL_DAMAGE_ANY,
            CardEffectType.DRAIN,
            CardEffectType.SINGLE_COMBAT_STRIKE -> enemyIds to allyIds

            else -> emptySet<String>() to emptySet()
        }
    }

    internal fun canonicalEnemyPowerKey(title: String): String = when (title) {
        "Ready Ranks", "Sẵn Sàng Đội Hình" -> "READY_RANKS"
        "Stone Ward", "Phòng Hộ Đá" -> "STONE_WARD"
        "Prismatic Shield", "Khiên Lăng Kính" -> "PRISMATIC_SHIELD"
        "For Demacia!", "Vì Demacia!" -> "FOR_DEMACIA"
        "Unseen Path", "Lối Đi Vô Hình" -> "UNSEEN_PATH"
        "Tempest Training", "Huấn Luyện Cuồng Phong" -> "TEMPEST_TRAINING"
        "Living Shadow", "Phân Thân Bóng Tối" -> "LIVING_SHADOW"
        "Enlightened Harmony", "Hòa Hợp Khai Sáng" -> "ENLIGHTENED_HARMONY"
        "Volatile Munitions", "Đạn Dược Bất Ổn" -> "VOLATILE_MUNITIONS"
        "Hextech Upgrade", "Nâng Cấp Hextech" -> "HEXTECH_UPGRADE"
        "Rocket Barrage", "Mưa Tên Lửa" -> "ROCKET_BARRAGE"
        "Glorious Evolution", "Tiến Hóa Huy Hoàng" -> "GLORIOUS_EVOLUTION"
        "Blazing Dawn", "Bình Minh Rực Cháy" -> "BLAZING_DAWN"
        "Undying Terror", "Nỗi Kinh Hoàng Bất Tử" -> "UNDYING_TERROR"
        "Endless Winter", "Mùa Đông Vô Tận" -> "ENDLESS_WINTER"
        "Star Forger", "Đấng Sáng Tạo Sao" -> "STAR_FORGER"
        else -> title
    }

    fun createEncounter(
        runState: RunState,
        loadout: ChampionLoadout,
        adventure: AdventureConfig,
        node: MapNode
    ): CombatState {
        val powers = runState.activePowerIds.mapNotNull { id ->
            CardCatalog.findPowerOrNull(id) ?: run { CardCatalog.getPower(id); null }
        }
        val relics = loadout.equippedRelics.mapNotNull { id ->
            id?.let { CardCatalog.findRelicOrNull(it) ?: run { CardCatalog.getRelic(it); null } }
        }

        // Expand RunState.runDeck into individual HandCardInstances
        val expandedDeck = mutableListOf<HandCardInstance>()
        runState.runDeck.forEach { entry ->
            val tmpl = CardCatalog.findCardOrNull(entry.cardId) ?: run {
                CardCatalog.getCard(entry.cardId)
                return@forEach
            }
            val items = entry.attachedItemIds.mapNotNull { itemId ->
                CardCatalog.findItemOrNull(itemId) ?: run { CardCatalog.getItem(itemId); null }
            }
            val costRed = items.sumOf { it.costReduction }
            val bonusPow = items.sumOf { it.bonusPower }
            val bonusHp = items.sumOf { it.bonusHealth }
            val itemKeywords = items.mapNotNull { it.grantedKeyword }.toSet()

            repeat(entry.copies) {
                expandedDeck.add(
                    HandCardInstance(
                        instanceId = "hc_${nextId()}",
                        template = tmpl,
                        attachedItems = items,
                        effectiveCost = maxOf(0, tmpl.cost - costRed),
                        bonusPower = bonusPow,
                        bonusHealth = bonusHp,
                        extraKeywords = itemKeywords,
                        isFleeting = tmpl.keywords.contains(Keyword.FLEETING)
                    )
                )
            }
        }
        expandedDeck.shuffle()

        // Farsight Alteration: pull Farsight cards to top so they are drawn in opening hand
        val farsights = expandedDeck.filter { c -> c.attachedItems.any { it.farsightGameStartDraw } }
        val others = expandedDeck.filterNot { c -> c.attachedItems.any { it.farsightGameStartDraw } }
        val orderedDeck = farsights + others

        // Pipeline 1.2: Rút ngẫu nhiên 6 lá khởi đầu, bắt buộc có ít nhất 1 Anh Hùng nếu bộ bài có Anh Hùng
        val openingHand = orderedDeck.take(6).toMutableList()
        val remainingDeck = orderedDeck.drop(6).toMutableList()
        if (openingHand.none { it.template.isChampion }) {
            val champIdx = remainingDeck.indexOfFirst { it.template.isChampion }
            if (champIdx >= 0 && openingHand.isNotEmpty()) {
                val champCard = remainingDeck.removeAt(champIdx)
                val swapOutIdx = openingHand.indexOfLast { c -> c.attachedItems.none { it.farsightGameStartDraw } }
                    .takeIf { it >= 0 } ?: (openingHand.size - 1)
                val swappedOut = openingHand.removeAt(swapOutIdx)
                openingHand.add(champCard)
                remainingDeck.add(swappedOut)
                remainingDeck.shuffle()
            }
        }

        val startingMana = (1 +
            loadout.startingManaBonus +
            (if (loadout.starLevel >= 5) 1 else 0) +
            relics.sumOf { it.startingManaBonus }).coerceIn(1, 10)

        val startingSpellMana = if (powers.any { it.id == "pow_sorcery" }) 3 else 0
        val celestialStartMana = if (adventure.mutators.any { it.id == "mut_celestial_might" }) 4 else 1
        val enemyStartMana = maxOf(1, node.enemyStartingMana, celestialStartMana).coerceIn(1, 10)

        val enemyPool = node.enemyDeckCardIds.mapNotNull { id ->
            CardCatalog.findCardOrNull(id) ?: run { CardCatalog.getCard(id); null }
        }.ifEmpty { listOf(CardCatalog.getCard("card_cithria")) }
        val openingEnemyHand = List(4) { enemyPool.random() }

        val initialLog = mutableListOf(
            "Chạm trán: ${node.title} vs ${node.enemyName}",
            "Năng Lượng Khởi Điểm: Bạn $startingMana | Địch $enemyStartMana"
        )
        if (adventure.mutators.any { it.id == "mut_celestial_might" }) {
            initialLog.add("Hiệu Ứng [Hiểm Họa Vũ Trụ]: Địch khởi đầu với $enemyStartMana Năng Lượng!")
        }
        if (node.enemyPowerTitle.isNotBlank()) {
            initialLog.add("Sức Mạnh Địch [${node.enemyPowerTitle}]: ${node.enemyPowerDescription}")
        }

        val enemyPowerKey = canonicalEnemyPowerKey(node.enemyPowerTitle)
        val effectiveEnemyMaxHp = node.enemyMaxHp + (if (enemyPowerKey == "STONE_WARD") 5 else 0)
        val initialEnemyHallowed = if (enemyPowerKey == "UNDYING_TERROR") 2 else 0

        var state = CombatState(
            encounterNode = node,
            adventure = adventure,
            championId = loadout.championId,
            starLevel = loadout.starLevel,
            equippedRelics = relics,
            activePowers = powers,
            mutators = adventure.mutators,
            round = 1,
            playerNexusHp = runState.currentNexusHp,
            playerMaxHp = runState.maxNexusHp,
            enemyNexusHp = effectiveEnemyMaxHp,
            enemyMaxHp = effectiveEnemyMaxHp,
            playerMana = startingMana,
            playerMaxMana = startingMana,
            playerSpellMana = startingSpellMana,
            enemyMana = enemyStartMana,
            enemyMaxMana = enemyStartMana,
            enemySpellMana = 0,
            playerHasAttackToken = true,
            enemyHasAttackToken = false,
            enemyHallowedStacks = initialEnemyHallowed,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0,
            playerHand = openingHand,
            playerDrawPile = remainingDeck,
            enemyHand = openingEnemyHand,
            enemyDrawPool = enemyPool,
            combatLog = initialLog
        )

        // Apply Game Start / Round 1 Star Powers & Powers
        state = applyRoundStartHooks(state, isGameStart = true)
        return syncChampionSpellsInHand(state)
    }

    private fun applyRoundStartHooks(state: CombatState, isGameStart: Boolean): CombatState {
        var s = state
        val logs = s.combatLog.toMutableList()

        // Mutator: Spirit Breeze
        if (s.mutators.any { it.id == "mut_spirit_breeze" }) {
            s = s.copy(
                playerSpellMana = minOf(3, s.playerSpellMana + 1),
                enemySpellMana = minOf(3, s.enemySpellMana + 1)
            )
        }

        // Mutator: Dư Âm Ác Mộng
        if (s.encounterNode.type == NodeType.FINAL_BOSS &&
            s.mutators.any { it.id == "mut_nightmare_curse" } &&
            s.playerHand.size < 10 &&
            (isGameStart || (s.round % 2 == 0 && canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) != "STAR_FORGER"))
        ) {
            s = s.copy(
                playerHand = s.playerHand + createHandInstance(
                    CardCatalog.getCard("card_curse_suppression"),
                    isCreated = false
                )
            )
            logs.add("Dư Âm Ác Mộng yểm Lời Nguyền Áp Chế lên tay bạn!")
        }

        // Power: Sorcery
        if (s.activePowers.any { it.id == "pow_sorcery" }) {
            s = s.copy(playerSpellMana = 3)
        }

        // Power: Sinh Lực Dồi Dào
        if (s.activePowers.any { it.id == "pow_vitality" }) {
            val healed = minOf(s.playerMaxHp, s.playerNexusHp + 2)
            if (healed > s.playerNexusHp) {
                s = s.copy(playerNexusHp = healed)
                logs.add("Sinh Lực Dồi Dào hồi +2 Máu cho Nhà Chính.")
            }
        }

        // Power: Thống Trị
        if (s.activePowers.any { it.id == "pow_domination" }) {
            s = s.copy(playerHasAttackToken = true)
        }

        // Power: Dòng Chảy Năng Lượng Hoàn Mỹ
        if (s.activePowers.any { it.id == "pow_manaflow" }) {
            s = gainPlayerManaGem(s, empty = false, logs, "Dòng Chảy Năng Lượng Hoàn Mỹ")
        }

        // Pipeline 1.1: Kiểm tra Khai Sáng DUY NHẤT tại Đầu Vòng Đấu sau khi nạp lại mana (regularMana >= 10)
        s = s.copy(isEnlightened = s.playerMana >= 10)

        // Sức Mạnh Sao Của Tướng
        when (s.championId) {
            "champ_jinx" -> {
                if (isGameStart && s.starLevel >= 2 && s.playerHand.size < 10) {
                    val rocket = createHandInstance(CardCatalog.getCard("card_mystic_shot"), isFleeting = true)
                    s = s.copy(playerHand = s.playerHand + rocket)
                    logs.add("Jinx 2★ tạo Phát Bắn Thần Bí Chóng Tàn.")
                }
            }
            "champ_lux" -> {
                if (s.starLevel >= 2) {
                    s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 1))
                }
                if (s.starLevel >= 6) {
                    s = s.copy(
                        playerHasAttackToken = true,
                        playerBoard = s.playerBoard.map { it.copy(hasBarrier = true) }
                    )
                    logs.add("Lux 6★ Tập Hợp & ban Lá Chắn cho đồng minh!")
                }
            }
            "champ_yasuo" -> {
                val debuffEnemies = getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF)
                if (s.starLevel >= 1 && debuffEnemies.isNotEmpty()) {
                    val target = if (s.starLevel >= 3) {
                        debuffEnemies.maxByOrNull { it.effectivePower }
                    } else {
                        debuffEnemies.minByOrNull { it.effectivePower }
                    }
                    if (target != null) {
                        s = stunEnemyUnit(s, target.instanceId, logs)
                    }
                }
                if (s.starLevel >= 3 && s.playerHand.size < 10) {
                    val tempest = createHandInstance(CardCatalog.getCard("card_steel_tempest"), isFleeting = true)
                    s = s.copy(playerHand = s.playerHand + tempest)
                }
            }
            "champ_garen" -> {
                if (s.starLevel >= 6 || s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp && !it.isSilenced }) {
                    s = s.copy(playerHasAttackToken = true)
                    if (!isGameStart && s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp && !it.isSilenced }) {
                        logs.add("Garen Thăng Cấp kích hoạt Tập Hợp!")
                    }
                }
            }
            "champ_ashe" -> {
                val debuffEnemies = getConstellationTargets(s, ConstellationEffectPolarity.DEBUFF)
                if (s.starLevel >= 1 && debuffEnemies.isNotEmpty()) {
                    if (s.starLevel >= 6) {
                        val newlyFrostbitten = debuffEnemies.count { !it.isFrostbitten }
                        val debuffIds = debuffEnemies.map { it.instanceId }.toSet()
                        s = s.copy(
                            enemyBoard = s.enemyBoard.map {
                                if (debuffIds.contains(it.instanceId)) it.copy(isFrostbitten = true) else it
                            },
                            enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                        )
                        logs.add("Ashe 6★ Đóng Băng TẤT CẢ kẻ địch!")
                    } else {
                        val strongest = debuffEnemies.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                            ?: debuffEnemies.maxByOrNull { it.effectivePower }
                        if (strongest != null) {
                            val newlyFrostbitten = if (!strongest.isFrostbitten) 1 else 0
                            s = s.copy(
                                enemyBoard = s.enemyBoard.map {
                                    if (it.instanceId == strongest.instanceId) it.copy(isFrostbitten = true) else it
                                },
                                enemiesFrostbittenCount = s.enemiesFrostbittenCount + newlyFrostbitten
                            )
                            logs.add("Sức Mạnh Sao Ashe Đóng Băng ${strongest.template.name}.")
                        }
                    }
                }
                if (s.starLevel >= 3) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { topAlly ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == topAlly.instanceId) {
                                it.copy(power = it.power + 2, health = it.health + 2, maxHealth = it.maxHealth + 2)
                            } else it
                        })
                        logs.add("Ashe 3★ ban +2|+2 cho ${topAlly.template.name}!")
                    }
                }
            }
            "champ_anivia" -> {
                if (isGameStart && s.starLevel >= 2) {
                    logs.add("Anivia 2★ Băng Giá Tràn Trề kích hoạt (+1 Năng Lượng Khởi Điểm).")
                }
            }
        }

        // Garen Thăng Cấp
        if (s.championId != "champ_garen" && s.playerBoard.any { it.template.id == "card_garen" && it.isLeveledUp && !it.isSilenced }) {
            s = s.copy(playerHasAttackToken = true)
            logs.add("Garen Thăng Cấp kích hoạt Tập Hợp!")
        }

        // Người Máy Đạn Đạo
        if (!isGameStart && s.playerBoard.any { it.template.id == "card_ballistic_bot" && !it.isSilenced } && s.playerHand.size < 10) {
            val ignition = createHandInstance(CardCatalog.getCard("card_ignition"), isFleeting = true)
            s = s.copy(playerHand = s.playerHand + ignition)
            logs.add("Người Máy Đạn Đạo tạo Tia Lửa Chóng Tàn.")
        }

        // Relic: Archangel's Staff (if leveled or on board)
        if (s.playerBoard.any { u -> u.template.isChampion } &&
            s.equippedRelics.any { it.id == "relic_archangels_staff" }
        ) {
            s = s.copy(playerSpellMana = 3)
        }

        // Relic: Grand General's Counterplan
        if (!isGameStart &&
            s.playerBoard.any { it.template.isChampion } &&
            s.equippedRelics.any { it.id == "relic_grand_generals" } &&
            s.playerHand.size < 10
        ) {
            val spellId = when (s.championId) {
                "champ_jinx" -> "card_get_excited"
                "champ_lux" -> "card_prismatic_barrier"
                "champ_yasuo" -> "card_steel_tempest"
                "champ_garen" -> "card_single_combat"
                "champ_anivia" -> "card_anivia_harsh_winds"
                else -> "card_flash_freeze"
            }
            s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard(spellId), isFleeting = true))
        }

        // Sức Mạnh Địch Đầu Vòng Đấu
        if (!isGameStart) {
            when (canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle)) {
                "FOR_DEMACIA" -> {
                    if (s.round % 2 == 0 && s.enemyBoard.isNotEmpty()) {
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                        })
                        logs.add("Vì Demacia! Quân địch nhận +1|+1.")
                    }
                }
                "TEMPEST_TRAINING" -> {
                    if (s.round % 2 == 0) {
                        s.playerBoard.minByOrNull { it.effectivePower }?.let { weakest ->
                            s = s.copy(playerBoard = s.playerBoard.map {
                                if (it.instanceId == weakest.instanceId) {
                                    it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN)
                                } else it
                            })
                            logs.add("Huấn Luyện Cuồng Phong làm Choáng ${weakest.template.name} của bạn!")
                        }
                    }
                }
                "PRISMATIC_SHIELD" -> {
                    val topEnemy = s.enemyBoard.maxByOrNull { it.effectivePower }
                    if (topEnemy != null) {
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == topEnemy.instanceId) it.copy(hasBarrier = true) else it
                        })
                        logs.add("Lux Trùm Phụ ban Lá Chắn cho ${topEnemy.template.name}.")
                    }
                }
                "ROCKET_BARRAGE" -> {
                    s = dealDamageToPlayerNexus(s, 1, logs)
                }
                "ENDLESS_WINTER" -> {
                    val topAlly = s.playerBoard.maxByOrNull { it.effectivePower }
                    if (topAlly != null) {
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == topAlly.instanceId) it.copy(isFrostbitten = true) else it
                        })
                        logs.add("Ashe Trùm Phụ Đóng Băng ${topAlly.template.name}!")
                    }
                }
                "STAR_FORGER" -> {
                    s = s.copy(enemyBoard = s.enemyBoard.map {
                        it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                    })
                    if (s.round % 2 == 0 && s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_curse_suppression"),
                                isCreated = false
                            )
                        )
                        logs.add("Aurelion Sol yểm Lời Nguyền Áp Chế lên tay bạn!")
                    }
                }
            }
        }

        // Đếm Ngược Địa Danh
        val tickedBoard = mutableListOf<UnitOnBoard>()
        var extraDraws = 0
        var extraSpellMana = 0
        s.playerBoard.forEach { u ->
            if (u.template.type == CardType.LANDMARK && u.countdownRemaining > 0) {
                val rem = u.countdownRemaining - 1
                if (rem <= 0) {
                    extraDraws += 2
                    extraSpellMana += 2
                    logs.add("${u.template.name} hoàn tất Đếm Ngược: Rút 2 lá & +2 Năng Lượng Phép!")
                } else {
                    tickedBoard.add(u.copy(countdownRemaining = rem))
                }
            } else {
                tickedBoard.add(u)
            }
        }
        s = s.copy(
            playerBoard = tickedBoard,
            playerSpellMana = minOf(3, s.playerSpellMana + extraSpellMana),
            combatLog = logs.takeLast(16)
        )
        repeat(extraDraws) { s = drawPlayerCard(s) }
        s = applyDeepBoardBuffs(s, logs)
        s = refreshEvolveAndSeenKeywords(s, logs)
        s = checkChampionLevelUps(s, logs, isRoundStart = !isGameStart)

        // Hồi sinh Trứng Anivia vào Đầu Vòng Đấu khi đạt Khai Sáng (chỉ số gốc theo diedAtLevel, loại bỏ mọi buff cũ)
        if (!isGameStart && s.isEnlightened && s.playerBoard.any { it.template.id == "card_eggnivia" && !it.isSilenced }) {
            val aniviaTmpl = CardCatalog.getCard("card_anivia")
            s = s.copy(
                playerBoard = s.playerBoard.map { u ->
                    if (u.template.id == "card_eggnivia" && !u.isSilenced) {
                        val diedLvl = u.diedAtLevel ?: 2
                        val leveled = diedLvl >= 2
                        val newPow = if (leveled) aniviaTmpl.basePower + aniviaTmpl.levelUpPowerBonus else aniviaTmpl.basePower
                        val newHp = if (leveled) aniviaTmpl.baseHealth + aniviaTmpl.levelUpHealthBonus else aniviaTmpl.baseHealth
                        u.copy(
                            template = aniviaTmpl,
                            power = newPow,
                            health = newHp,
                            maxHealth = newHp,
                            keywords = aniviaTmpl.keywords + s.equippedRelics.mapNotNull { it.grantedKeyword },
                            isLeveledUp = leveled,
                            diedAtLevel = null,
                            hasBarrier = false,
                            isFrostbitten = false,
                            frostbiteReduction = null,
                            isStunned = false,
                            gloomStacks = 0,
                            spiritStacks = 0,
                            impactStacks = 0
                        )
                    } else u
                }
            )
            logs.add("Trứng Anivia hồi sinh thành Anivia!")
        }

        s = syncChampionSpellsInHand(s)
        return checkGameOver(s.copy(combatLog = logs.takeLast(16)))
    }

    /**
     * Pipeline 1.4: Tính toán sát thương động cho Kỹ năng và Bài phép (cộng dồn Relic + Power).
     */
    fun calculateFinalDamage(
        baseDamage: Int,
        card: CardTemplate? = null,
        state: CombatState,
        sourceIsPlayer: Boolean = true,
        extraBonus: Int = 0
    ): Int {
        if (baseDamage <= 0) return 0
        val relicBonus = if (sourceIsPlayer) state.equippedRelics.sumOf { it.spellSkillDamageBonus } else 0
        val powerBonus = if (sourceIsPlayer) state.activePowers.sumOf { it.spellSkillDamageBonus } else 0
        val mult = if (sourceIsPlayer && state.championId == "champ_jinx" && state.starLevel >= 6) 2 else 1
        return maxOf(0, (baseDamage + relicBonus + powerBonus + extraBonus) * mult)
    }

    /**
     * Pipeline 1.3: Biến đổi bản sao Anh Hùng trên tay thành Bài Phép Anh Hùng khi có trên sân và hoàn nguyên khi rời sân.
     */
    internal fun syncChampionSpellsInHand(state: CombatState): CombatState {
        val activeChampionIds = state.playerBoard
            .filter { it.template.isChampion }
            .map { it.template.id }
            .toSet()
        var changed = false
        val updatedHand = state.playerHand.map { hc ->
            val champId = hc.template.id
            if (hc.template.isChampion && activeChampionIds.contains(champId)) {
                val spellId = CardCatalog.championSpellIdFor(champId)
                val spellTmpl = spellId?.let { CardCatalog.findCardOrNull(it) }
                if (spellTmpl != null) {
                    changed = true
                    HandCardInstance(
                        instanceId = hc.instanceId,
                        template = spellTmpl,
                        attachedItems = hc.attachedItems,
                        effectiveCost = maxOf(0, spellTmpl.cost - hc.attachedItems.sumOf { it.costReduction }),
                        bonusPower = 0,
                        bonusHealth = 0,
                        extraKeywords = emptySet(),
                        isFleeting = hc.isFleeting || spellTmpl.keywords.contains(Keyword.FLEETING),
                        isCreated = hc.isCreated,
                        hasChimeBoon = hc.hasChimeBoon,
                        hasPortalBoon = hc.hasPortalBoon,
                        hasPuffcapTrap = hc.hasPuffcapTrap,
                        linkedShackledUnitId = hc.linkedShackledUnitId,
                        transformedFromCard = hc
                    )
                } else hc
            } else if (hc.transformedFromCard != null) {
                val origChampId = hc.transformedFromCard.template.id
                if (!activeChampionIds.contains(origChampId)) {
                    changed = true
                    hc.transformedFromCard.copy(
                        instanceId = hc.instanceId,
                        isFleeting = hc.isFleeting
                    )
                } else hc
            } else {
                hc
            }
        }
        return if (changed) state.copy(playerHand = updatedHand) else state
    }

    private fun createHandInstance(
        template: CardTemplate,
        isFleeting: Boolean = false,
        isCreated: Boolean = true
    ): HandCardInstance {
        return HandCardInstance(
            instanceId = "hc_${nextId()}",
            template = template,
            effectiveCost = template.cost,
            isFleeting = isFleeting || template.keywords.contains(Keyword.FLEETING),
            isCreated = isCreated
        )
    }

    fun drawPlayerCard(state: CombatState): CombatState {
        if (state.playerDrawPile.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Bộ bài của bạn đã hết! Kiệt sức gây $EMPTY_DECK_FATIGUE_DAMAGE sát thương lên Nhà Chính.")
            val afterFatigue = dealDamageToPlayerNexus(state, EMPTY_DECK_FATIGUE_DAMAGE, logs)
            return afterFatigue.copy(combatLog = logs.takeLast(16))
        }
        val topCard = state.playerDrawPile.first()
        val remainingPile = state.playerDrawPile.drop(1)
        val logs = state.combatLog.toMutableList()
        var s = state.copy(playerDrawPile = remainingPile)

        // Check Puffcap Traps
        if (topCard.hasPuffcapTrap > 0) {
            s = dealDamageToPlayerNexus(s, topCard.hasPuffcapTrap, logs)
            logs.add("Bẫy Nấm Độc gây ${topCard.hasPuffcapTrap} sát thương!")
        }

        // Check Chime Boons
        if (topCard.hasChimeBoon && s.playerHand.isNotEmpty()) {
            val idx = Random.nextInt(s.playerHand.size)
            val updatedHand = s.playerHand.toMutableList()
            val chosen = updatedHand[idx]
            updatedHand[idx] = chosen.copy(bonusPower = chosen.bonusPower + 1, bonusHealth = chosen.bonusHealth + 1)
            s = s.copy(playerHand = updatedHand)
            logs.add("Chuông Vũ Trụ ban +1|+1 cho ${chosen.template.name}!")
        }

        if (s.playerHand.size < 10) {
            s = s.copy(playerHand = s.playerHand + topCard, combatLog = logs.takeLast(16))
        } else {
            logs.add("Đầy tay! Tiêu hủy ${topCard.template.name}.")
            s = s.copy(combatLog = logs.takeLast(16))
        }
        s = applyDeepBoardBuffs(s, logs)
        s = syncChampionSpellsInHand(s)
        return s.copy(combatLog = logs.takeLast(16))
    }

    fun playCardFromHand(
        state: CombatState,
        handCardId: String,
        targetUnitId: String? = null
    ): CombatState {
        val card = state.playerHand.find { it.instanceId == handCardId } ?: return state
        if (!state.canPlayCard(card)) return state

        val cost = state.effectivePlayCost(card)
        val isSpellLike = card.template.type == CardType.SPELL || card.template.type == CardType.CURSE

        var newMana = state.playerMana
        var newSpellMana = state.playerSpellMana
        if (isSpellLike) {
            val spellUsed = minOf(maxOf(0, newSpellMana), cost)
            newSpellMana = maxOf(0, newSpellMana - spellUsed)
            newMana = maxOf(0, newMana - (cost - spellUsed))
        } else {
            newMana = maxOf(0, newMana - cost)
        }

        val logs = state.combatLog.toMutableList()
        val itemManaRefill = card.attachedItems.sumOf { it.refillManaOnPlay }
        if (itemManaRefill > 0) {
            newMana = minOf(state.playerMaxMana, newMana + itemManaRefill)
            logs.add("${card.template.name} hồi +$itemManaRefill Năng Lượng.")
        }

        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val usedFastInCombat = isSpellLike &&
            card.template.spellSpeed == SpellSpeed.FAST &&
            isInCombat

        var s = state.copy(
            playerMana = newMana,
            playerSpellMana = newSpellMana,
            playerHand = state.playerHand.filterNot { it.instanceId == handCardId },
            playerCardsPlayedThisRound = state.playerCardsPlayedThisRound + 1,
            playerFastSpellUsedInCombat = state.playerFastSpellUsedInCombat || usedFastInCombat,
            combatLog = logs
        )

        // Augment: +1|+0 to existing allied Augment units when playing a created card (before resolution)
        if (card.isCreated) {
            var augmentTriggered = false
            s = s.copy(playerBoard = s.playerBoard.map { u ->
                if (!u.isSilenced && u.keywords.contains(Keyword.AUGMENT)) {
                    augmentTriggered = true
                    u.copy(power = u.power + 1)
                } else u
            })
            if (augmentTriggered) {
                logs.add("Nâng Cấp kích hoạt (+1|+0) từ lá bài được tạo ${card.template.name}!")
            }
        }

        // Jinx 1★ / 3★ Power: Playing a card deals burn damage
        if (s.championId == "champ_jinx" && s.starLevel >= 1) {
            s = dealDamageToEnemyNexus(s, 1, logs)
            if (s.starLevel >= 3 && s.enemyBoard.isNotEmpty()) {
                val target = s.enemyBoard.random()
                s = damageUnit(s, target.instanceId, isEnemy = true, damage = 1, logs = logs)
            }
            logs.add("Sức Mạnh Sao Jinx gây 1 sát thương!")
        }

        // Play: Draw 1 item or card hook
        if (card.attachedItems.any { it.onPlayDrawOne } ||
            card.template.id == "card_zaunite_urchin" ||
            card.template.id == "card_shadow_assassin"
        ) {
            s = drawPlayerCard(s)
        }

        // Mutator: Glorious Assembly
        if (s.mutators.any { it.id == "mut_glorious_assembly" } && isSpellLike) {
            s = s.copy(playerBoard = s.playerBoard.map { it.copy(power = it.power + 1) })
        }

        var nextPriority = PriorityHolder.PLAYER
        var nextPhase = s.phase
        var nextLastCaster = s.lastStackCaster

        when (card.template.type) {
            CardType.UNIT, CardType.CHAMPION, CardType.LANDMARK -> {
                s = summonPlayerUnit(s, card, logs)
                if (card.template.effectType == CardEffectType.FROSTBITE_TARGET) {
                    val chosen = targetUnitId?.let { id -> s.enemyBoard.find { it.instanceId == id } }
                        ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                    if (chosen != null) {
                        s = frostbiteUnit(s, chosen.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                    }
                }
                if (card.template.id == "card_brightsteel_protector") {
                    val top = targetUnitId?.let { id -> s.playerBoard.find { it.instanceId == id } }
                        ?: s.playerBoard.maxByOrNull { it.effectivePower }
                    top?.let { ally ->
                        s = triggerFatedOnTarget(s, ally.instanceId, sourceIsPlayer = true, logs = logs)
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == ally.instanceId) it.copy(hasBarrier = true) else it
                        })
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                }
                // Rule 9.3: Summoning Unit/Landmark passes priority immediately to opponent
                nextPriority = PriorityHolder.ENEMY
            }
            CardType.EQUIPMENT -> {
                val host = targetUnitId?.let { id -> s.playerBoard.find { it.instanceId == id } }
                    ?: s.playerBoard.maxByOrNull { it.effectivePower }
                if (host != null) {
                    val (cleanHost, previousGear) = detachEquipmentFromUnit(host)
                    if (previousGear != null) {
                        s = returnEquipmentToHand(s, previousGear, toPlayer = true, logs = logs)
                    }
                    s = triggerFatedOnTarget(s, host.instanceId, sourceIsPlayer = true, logs = logs)
                    s = s.copy(playerBoard = s.playerBoard.map { unit ->
                        if (unit.instanceId == host.instanceId) {
                            val (baseUnit, _) = detachEquipmentFromUnit(unit)
                            baseUnit.copy(
                                power = baseUnit.power + card.template.basePower,
                                health = baseUnit.health + card.template.baseHealth,
                                maxHealth = baseUnit.maxHealth + card.template.baseHealth,
                                keywords = baseUnit.keywords + card.template.keywords,
                                hasBarrier = baseUnit.hasBarrier || card.template.keywords.contains(Keyword.BARRIER),
                                hasSpellShield = baseUnit.hasSpellShield || card.template.keywords.contains(Keyword.SPELLSHIELD),
                                hasDeathless = baseUnit.hasDeathless || card.template.keywords.contains(Keyword.DEATHLESS),
                                equippedGear = card.template,
                                equippedGearName = card.template.name
                            )
                        } else unit
                    })
                    s = refreshEvolveAndSeenKeywords(s, logs)
                    logs.add("Trang bị ${card.template.name} cho ${host.template.name}!")
                }
                // Rule 9.3: Equipment passes priority immediately to opponent
                nextPriority = PriorityHolder.ENEMY
            }
            CardType.SPELL, CardType.CURSE -> {
                if (card.template.effectType == CardEffectType.PURGE_SHACKLES_CURSE) {
                    val shackledTargetId = card.linkedShackledUnitId
                        ?: s.playerBoard.firstOrNull { it.isShackled }?.instanceId
                    if (shackledTargetId != null) {
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == shackledTargetId) it.copy(isShackled = false, keywords = it.keywords - Keyword.SHACKLED) else it
                        })
                    }
                }
                s = s.copy(
                    playerSpellsManaSpent = s.playerSpellsManaSpent + cost,
                    playerSpellsPlayedThisRound = s.playerSpellsPlayedThisRound + 1
                )

                if (card.template.keywords.contains(Keyword.ELEMENTAL)) {
                    s = onPlayerSkillOrElementalPlayed(s, logs, card.template.name)
                }

                // Lux Star Power 1★: Spell grants strongest ally +1|+1
                if (s.championId == "champ_lux" && s.starLevel >= 1) {
                    s.playerBoard.maxByOrNull { it.effectivePower }?.let { top ->
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) {
                                it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                            } else it
                        })
                    }
                }
                // Lux 3★: Every 6 spell mana -> Rally + Final Spark
                if (s.championId == "champ_lux" && s.starLevel >= 3 && s.playerSpellsManaSpent >= 6) {
                    s = s.copy(
                        playerSpellsManaSpent = s.playerSpellsManaSpent - 6,
                        playerHasAttackToken = true
                    )
                    if (s.playerHand.size < 10) {
                        s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_final_spark"), true))
                    }
                    logs.add("Lux 3★ kích hoạt: Tập Hợp & tạo Cầu Vồng Tối Thượng!")
                }

                // Rule 9.3: Burst/Focus resolve immediately and KEEP priority; Fast/Slow enter stack and pass priority
                if (card.template.spellSpeed == SpellSpeed.BURST || card.template.spellSpeed == SpellSpeed.FOCUS) {
                    s = resolveSingleSpellEffect(
                        state = s,
                        card = card.template,
                        sourceIsPlayer = true,
                        bonusDmg = 0,
                        logs = logs,
                        targetUnitId = targetUnitId
                    )
                    if (s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && nextPhase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
                        nextPhase = s.phase
                        nextPriority = s.priorityHolder
                    } else {
                        nextPriority = PriorityHolder.PLAYER
                    }
                } else {
                    // Fated triggers on play when an allied unit on board is targeted by Fast/Slow spell
                    val fatedTargetId = when (card.template.effectType) {
                        CardEffectType.SINGLE_COMBAT_STRIKE -> targetUnitId?.split("|")?.firstOrNull()
                        else -> targetUnitId
                    }
                    if (fatedTargetId != null) {
                        s = triggerFatedOnTarget(s, fatedTargetId, sourceIsPlayer = true, logs = logs)
                    }
                    val entry = SpellStackEntry(
                        id = "st_${nextId()}",
                        sourceIsPlayer = true,
                        card = card.template,
                        targetUnitId = targetUnitId,
                        damageBonus = 0
                    )
                    s = s.copy(spellStack = s.spellStack + entry)
                    nextLastCaster = PriorityHolder.PLAYER
                    if (nextPhase == CombatPhase.MAIN_ACTION_PHASE) {
                        nextPhase = CombatPhase.SPELL_STACK_PHASE
                    }
                    nextPriority = PriorityHolder.ENEMY
                    logs.add("Đặt ${card.template.name} (${card.template.spellSpeed.displayName}) lên hàng chờ phép.")
                }
            }
        }

        val castDuringBlock = isSpellLike && s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        s = checkChampionLevelUps(s, logs)
        s = syncChampionSpellsInHand(s)
        s = s.copy(
            phase = nextPhase,
            priorityHolder = nextPriority,
            consecutivePasses = 0,
            lastStackCaster = nextLastCaster,
            playerCastSpellInBlock = s.playerCastSpellInBlock || castDuringBlock,
            combatLog = logs.takeLast(16)
        )
        return checkGameOver(s)
    }

    private fun summonPlayerUnit(
        state: CombatState,
        card: HandCardInstance,
        logs: MutableList<String>,
        forceEphemeral: Boolean = false
    ): CombatState {
        if (state.playerBoard.size >= 6) return state

        var s = state
        val tmpl = card.template
        val itemKeywords = card.extraKeywords
        var keywords = tmpl.keywords + itemKeywords
        if (forceEphemeral) keywords = keywords + Keyword.EPHEMERAL

        if (s.equippedRelics.any { it.id == "relic_troll_kings_crown" }) {
            keywords = keywords + Keyword.OVERWHELM
        }

        // Hextech Fabricator random keyword
        if (card.attachedItems.any { it.grantRandomKeywordOnSummon }) {
            keywords = keywords + Keyword.sharablePool.random()
        }

        var power = tmpl.basePower + card.bonusPower
        var health = tmpl.baseHealth + card.bonusHealth
        if (tmpl.isChampion) {
            power += s.equippedRelics.sumOf { it.bonusPower }
            health += s.equippedRelics.sumOf { it.bonusHealth }
            s.equippedRelics.mapNotNull { it.grantedKeyword }.forEach { keywords = keywords + it }
        }
        if (s.championId == "champ_garen" && s.starLevel >= 2) {
            power += 1; health += 1
        }
        if (s.championId == "champ_garen" && s.starLevel >= 4) {
            keywords = keywords + Keyword.TOUGH
        }
        if (s.championId == "champ_lux" && s.starLevel >= 4) {
            health += 2; keywords = keywords + Keyword.SHARPSIGHT
        }
        if (s.championId == "champ_jinx" && s.starLevel >= 4) {
            health += 1; keywords = keywords + Keyword.IMPACT
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 4) {
            power += 1; health += 1
        }
        if (s.championId == "champ_yasuo" && s.starLevel >= 2 && keywords.contains(Keyword.QUICK_ATTACK)) {
            power += 1
        }

        // Daybreak bonus
        if (keywords.contains(Keyword.DAYBREAK) && s.playerCardsPlayedThisRound == 1) {
            power += 1; health += 1
            keywords = keywords + Keyword.CHALLENGER
            logs.add("Hừng Đông kích hoạt trên ${tmpl.name} (+1|+1 & Thách Đấu)!")
        }

        // Spirit stack bonus
        val spiritCount = tmpl.spiritStacks + card.attachedItems.sumOf { it.spiritStacks }
        power += spiritCount
        health += spiritCount

        val impactCount = tmpl.impactStacks + card.attachedItems.sumOf { it.impactStacks } +
            (if (s.championId == "champ_jinx" && s.starLevel >= 4) 1 else 0)

        // Lurk bonus accumulated this match
        if (keywords.contains(Keyword.LURK)) {
            power += s.playerLurkBonus
        }

        // Deep bonus (+3|+3 when deck has <= 15 cards)
        var deepApplied = false
        if (keywords.contains(Keyword.DEEP) && s.playerDrawPile.size <= 15) {
            power += 3
            health += 3
            deepApplied = true
            logs.add("Thẩm Sâu kích hoạt trên ${tmpl.name} (+3|+3)!")
        }

        // Track Evolve unique positive keywords
        val newSeenKeywords = s.playerUniqueKeywordsSeen + keywords.filter { it.isSharablePositive }
        var evolveApplied = false
        if (keywords.contains(Keyword.EVOLVE) && newSeenKeywords.size >= 6) {
            power += 2; health += 2
            evolveApplied = true
        }

        // Power: Evolution (+1|+1 per positive keyword)
        if (s.activePowers.any { it.id == "pow_evolution" }) {
            val kwCount = keywords.count { it.isSharablePositive }
            power += kwCount; health += kwCount
        }

        val unit = UnitOnBoard(
            instanceId = "u_${nextId()}",
            template = tmpl,
            ownerIsPlayer = true,
            power = power,
            health = health,
            maxHealth = health,
            keywords = keywords,
            impactStacks = impactCount,
            spiritStacks = spiritCount,
            hasBarrier = keywords.contains(Keyword.BARRIER),
            hasSpellShield = keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = keywords.contains(Keyword.DEATHLESS),
            attachedItems = card.attachedItems,
            countdownRemaining = tmpl.countdownRounds,
            hasEnlightenedBonusApplied = false,
            hasDeepBonusApplied = deepApplied,
            hasEvolveBonusApplied = evolveApplied
        )

        // Attune
        if (keywords.contains(Keyword.ATTUNE)) {
            s = s.copy(playerSpellMana = minOf(3, s.playerSpellMana + 1))
        }

        // Power: Sharing is Caring
        var updatedBoard = s.playerBoard
        if (s.activePowers.any { it.id == "pow_sharing_is_caring" }) {
            val sharable = keywords.filter { it.isSharablePositive }.toSet()
            updatedBoard = updatedBoard.map { existing ->
                existing.copy(keywords = existing.keywords + sharable)
            }
        }

        s = s.copy(
            playerBoard = updatedBoard + unit,
            playerUniqueKeywordsSeen = newSeenKeywords
        )
        s = refreshEvolveAndSeenKeywords(s, logs)
        logs.add("Triệu hồi ${tmpl.name} (${unit.power}|${unit.health}).")

        // On-Summon Skills & Triggers (Pipelines 3.3 & 4.2)
        when (tmpl.id) {
            "card_wild_mystics" -> {
                if (s.playerHand.size < 10) {
                    val arrow = createHandInstance(CardCatalog.getCard("card_frozen_arrow"), isCreated = true)
                    s = s.copy(playerHand = s.playerHand + arrow)
                    logs.add("Thầy Tu Hoang Dã tạo 1 lá Tên Đóng Băng lên tay!")
                }
            }
            "card_flame_eyed_caprine" -> {
                val capDmg = calculateFinalDamage(1, tmpl, s, sourceIsPlayer = true)
                s = dealDamageToEnemyNexus(s, capDmg, logs)
                s.enemyBoard.toList().forEach { enemy ->
                    s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = capDmg, isSpell = true, fromAllyEffect = false, logs = logs)
                }
                logs.add("Hỏa Nhân gây $capDmg sát thương lên Nhà Chính địch và toàn bộ quân địch!")
            }
            "card_crystal_stormbird" -> {
                s = onPlayerSkillOrElementalPlayed(s, logs, tmpl.name)
                val stormDmg = calculateFinalDamage(2, tmpl, s, sourceIsPlayer = true)
                s.enemyBoard.toList().forEach { enemy ->
                    s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = stormDmg, isSpell = true, fromAllyEffect = false, logs = logs)
                }
                s.playerBoard.toList().forEach { ally ->
                    if (ally.instanceId != unit.instanceId) {
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = stormDmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                }
                logs.add("Kỹ Năng Chim Bão gây $stormDmg sát thương lên TẤT CẢ bài quân khác!")
            }
            "card_rhasa" -> {
                val victims = s.enemyBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .sortedWith(compareBy<UnitOnBoard> { it.effectivePower }.thenBy { it.health })
                    .take(2)
                victims.forEach { v ->
                    s = removeUnit(s, v.instanceId, isEnemy = true, logs = logs)
                }
                if (victims.isNotEmpty()) {
                    logs.add("Rhasa Phân Ly tiêu diệt ${victims.size} kẻ địch có Công thấp nhất!")
                }
            }
            "card_rumul" -> {
                val targetAlly = s.playerBoard
                    .filter { it.template.type != CardType.LANDMARK }
                    .maxByOrNull { it.health }
                if (targetAlly != null) {
                    s = s.copy(playerBoard = s.playerBoard.map { u ->
                        if (u.instanceId == targetAlly.instanceId) {
                            u.copy(health = u.health + 4, maxHealth = u.maxHealth + 4)
                        } else u
                    })
                    logs.add("Rumul ban +0|+4 cho ${targetAlly.template.name}!")
                }
            }
        }

        // Power: Trifarian Might (5+ Power strikes weakest enemy)
        if (s.activePowers.any { it.id == "pow_trifarian_might" } && unit.effectivePower >= 5) {
            s.enemyBoard.minByOrNull { it.health }?.let { weakest ->
                s = performNonCombatStrike(
                    state = s,
                    strikerId = unit.instanceId,
                    strikerIsPlayer = true,
                    targetId = weakest.instanceId,
                    targetIsEnemy = true,
                    logs = logs
                )
                logs.add("Sức Mạnh Tam Cực: ${tmpl.name} đánh ${weakest.template.name} gây ${unit.strikePower} sát thương!")
            }
        }

        // Relic: Stalker's Blade
        if (tmpl.isChampion && s.equippedRelics.any { it.id == "relic_stalkers_blade" }) {
            s.enemyBoard.minByOrNull { it.health }?.let { weakest ->
                s = performNonCombatStrike(
                    state = s,
                    strikerId = unit.instanceId,
                    strikerIsPlayer = true,
                    targetId = weakest.instanceId,
                    targetIsEnemy = true,
                    logs = logs
                )
                logs.add("Kiếm Truy Sát: ${tmpl.name} đánh ${weakest.template.name}!")
            }
        }

        // Shadow Totem or Stabilize Ephemeral Copy
        if (!forceEphemeral && (card.attachedItems.any { it.onSummonEphemeralCopy } ||
                (tmpl.isChampion && s.activePowers.any { it.id == "pow_stabilize" }))
        ) {
            if (s.playerBoard.size < 6) {
                s = summonPlayerUnit(s, card, logs, forceEphemeral = true)
            }
        }

        return syncChampionSpellsInHand(s)
    }

    fun resolveSpellStack(state: CombatState): CombatState {
        if (state.spellStack.isEmpty()) return state
        var s = state
        val logs = s.combatLog.toMutableList()
        // LIFO stack resolution (top of stack resolves first)
        for (entry in state.spellStack.reversed()) {
            s = resolveSingleSpellEffect(
                state = s,
                card = entry.card,
                sourceIsPlayer = entry.sourceIsPlayer,
                bonusDmg = entry.damageBonus,
                logs = logs,
                targetUnitId = entry.targetUnitId
            )
        }
        val returnPhase = if (s.phase == CombatPhase.SPELL_STACK_PHASE) {
            CombatPhase.MAIN_ACTION_PHASE
        } else {
            s.phase
        }
        val nextPriority = when (returnPhase) {
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE -> PriorityHolder.PLAYER
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ->
                if (s.playerInitiatedCombat) PriorityHolder.ENEMY else PriorityHolder.PLAYER
            else -> s.lastStackCaster?.opponent() ?: s.priorityHolder
        }
        s = s.copy(
            spellStack = emptyList(),
            phase = returnPhase,
            priorityHolder = nextPriority,
            consecutivePasses = 0,
            lastStackCaster = null,
            combatLog = logs.takeLast(16)
        )
        s = checkChampionLevelUps(s, logs)
        s = checkGameOver(s)
        if (s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && s.blockCommitted && s.playerWon == null) {
            return if (s.playerInitiatedCombat) {
                finishPlayerAttackCombat(s)
            } else {
                finishEnemyAttackCombat(s)
            }
        }
        return s
    }

    internal fun resolveSingleSpellEffect(
        state: CombatState,
        card: CardTemplate,
        sourceIsPlayer: Boolean,
        bonusDmg: Int = 0,
        logs: MutableList<String>,
        targetUnitId: String? = null
    ): CombatState {
        var s = state
        val dmg = calculateFinalDamage(card.effectValue, card, s, sourceIsPlayer, bonusDmg)

        when (card.effectType) {
            CardEffectType.DEAL_DAMAGE_ANY -> {
                if (targetUnitId == "ENEMY_NEXUS" || (sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên Nhà Chính địch.")
                } else if (targetUnitId == "PLAYER_NEXUS" || (!sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên Nhà Chính của bạn.")
                } else if (targetUnitId != null) {
                    val onEnemy = s.enemyBoard.any { it.instanceId == targetUnitId }
                    val onPlayer = s.playerBoard.any { it.instanceId == targetUnitId }
                    if (onEnemy) {
                        if (!sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = false, logs = logs)
                        }
                        s = damageUnit(s, targetUnitId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = !sourceIsPlayer, logs = logs)
                        logs.add("${card.name} gây $dmg sát thương.")
                    } else if (onPlayer) {
                        if (sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = true, logs = logs)
                        }
                        s = damageUnit(s, targetUnitId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = sourceIsPlayer, logs = logs)
                        logs.add("${card.name} gây $dmg sát thương.")
                    } else {
                        logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    }
                } else if (sourceIsPlayer) {
                    val target = s.enemyBoard.minByOrNull { it.health }
                    if (target != null) {
                        s = damageUnit(s, target.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    } else {
                        s = dealDamageToEnemyNexus(s, dmg, logs)
                    }
                    logs.add("${card.name} gây $dmg sát thương.")
                } else {
                    val target = s.playerBoard.minByOrNull { it.health }
                    if (target != null) {
                        s = damageUnit(s, target.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    } else {
                        s = dealDamageToPlayerNexus(s, dmg, logs)
                    }
                    logs.add("${card.name} của địch gây $dmg sát thương.")
                }
            }
            CardEffectType.DEAL_DAMAGE_NEXUS -> {
                if (sourceIsPlayer) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên Nhà Chính địch.")
                } else {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên Nhà Chính của bạn.")
                }
            }
            CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS -> {
                if (sourceIsPlayer) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = maxOf(2, dmg / 2), isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    logs.add("${card.name} bắn $dmg lên Nhà Chính & toàn bộ quân địch!")
                } else {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    s.playerBoard.toList().forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = maxOf(2, dmg / 2), isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    logs.add("${card.name} của địch bắn $dmg lên Nhà Chính & quân của bạn!")
                }
            }
            CardEffectType.STUN_TARGET -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = if (targetUnitId != null) {
                    targetBoard.find { it.instanceId == targetUnitId }
                } else {
                    targetBoard.maxByOrNull { it.effectivePower }
                }
                val resolvedTarget = if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    false
                } else if (target != null) {
                    s = stunUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                    true
                } else {
                    false
                }
                if (card.id == "card_concussive_palm" && (targetUnitId == null || resolvedTarget)) {
                    val tailTmpl = CardCatalog.getCard("card_tail_of_the_dragon")
                    if (sourceIsPlayer && s.playerBoard.size < 6) {
                        s = summonPlayerUnit(s, createHandInstance(tailTmpl), logs)
                    } else if (!sourceIsPlayer && s.enemyBoard.size < 6) {
                        s = summonEnemyUnitFromPool(s, tailTmpl, spendMana = 0, logs = logs)
                    }
                }
            }
            CardEffectType.FROSTBITE_TARGET -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = if (targetUnitId != null) {
                    targetBoard.find { it.instanceId == targetUnitId }
                } else {
                    targetBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                        ?: targetBoard.maxByOrNull { it.effectivePower }
                }
                if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                } else if (target != null) {
                    s = frostbiteUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                }
            }
            CardEffectType.RECALL_OR_STUN -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                val target = if (targetUnitId != null) {
                    targetBoard.find { it.instanceId == targetUnitId }
                } else {
                    targetBoard.maxByOrNull { it.effectivePower }
                }
                if (targetUnitId != null && target == null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                } else if (target != null) {
                    s = recallUnit(s, target.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                }
            }
            CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val chosenAlly = if (targetUnitId != null) {
                    allyBoard.find { it.instanceId == targetUnitId }
                } else {
                    allyBoard.maxByOrNull { it.effectivePower }
                }
                if (chosenAlly != null) {
                    if (targetUnitId != null) {
                        s = triggerFatedOnTarget(s, chosenAlly.instanceId, sourceIsPlayer = sourceIsPlayer, logs = logs)
                    }
                    s = if (sourceIsPlayer) {
                        s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == chosenAlly.instanceId) it.copy(hasBarrier = true) else it
                        })
                    } else {
                        s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == chosenAlly.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    if (sourceIsPlayer) {
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                }
                s = if (sourceIsPlayer) {
                    logs.add("${card.name} ban Lá Chắn và rút 1 lá.")
                    drawPlayerCard(s)
                } else {
                    logs.add("${card.name} của địch ban Lá Chắn và rút 1 lá.")
                    drawEnemyCard(s)
                }
            }
            CardEffectType.RALLY_AND_SHIELD -> {
                val allyBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val top = if (targetUnitId != null) {
                    allyBoard.find { it.instanceId == targetUnitId }
                } else {
                    allyBoard.maxByOrNull { it.effectivePower }
                }
                if (sourceIsPlayer) {
                    top?.let { chosen ->
                        if (targetUnitId != null) {
                            s = triggerFatedOnTarget(s, chosen.instanceId, sourceIsPlayer = true, logs = logs)
                        }
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == chosen.instanceId) it.copy(hasBarrier = true) else it
                        })
                        s = refreshEvolveAndSeenKeywords(s, logs)
                    }
                    s = s.copy(playerHasAttackToken = true)
                    logs.add("Khiên Vàng Tập Hợp kích hoạt Tập Hợp!")
                } else {
                    top?.let { chosen ->
                        if (targetUnitId != null) {
                            s = triggerFatedOnTarget(s, chosen.instanceId, sourceIsPlayer = false, logs = logs)
                        }
                        s = s.copy(enemyBoard = s.enemyBoard.map {
                            if (it.instanceId == chosen.instanceId) it.copy(hasBarrier = true) else it
                        })
                    }
                    s = s.copy(enemyHasAttackToken = true)
                    logs.add("Khiên Vàng Tập Hợp của địch kích hoạt Tập Hợp!")
                }
            }
            CardEffectType.SINGLE_COMBAT_STRIKE -> {
                val parts = targetUnitId?.split("|")
                val ally = when {
                    parts != null && parts.size == 2 -> {
                        if (sourceIsPlayer) s.playerBoard.find { it.instanceId == parts[0] }
                        else s.enemyBoard.find { it.instanceId == parts[0] }
                    }
                    sourceIsPlayer -> s.playerBoard.maxByOrNull { it.strikePower }
                    else -> s.enemyBoard.maxByOrNull { it.strikePower }
                }
                val enemy = when {
                    parts != null && parts.size == 2 -> {
                        if (sourceIsPlayer) s.enemyBoard.find { it.instanceId == parts[1] }
                        else s.playerBoard.find { it.instanceId == parts[1] }
                    }
                    targetUnitId != null -> {
                        if (sourceIsPlayer) s.enemyBoard.find { it.instanceId == targetUnitId }
                        else s.playerBoard.find { it.instanceId == targetUnitId }
                    }
                    sourceIsPlayer -> s.enemyBoard.minByOrNull { it.health }
                    else -> s.playerBoard.minByOrNull { it.health }
                }
                if (ally != null && enemy != null) {
                    if (enemy.hasSpellShield) {
                        s = if (sourceIsPlayer) {
                            s.copy(enemyBoard = s.enemyBoard.map {
                                if (it.instanceId == enemy.instanceId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
                            })
                        } else {
                            s.copy(playerBoard = s.playerBoard.map {
                                if (it.instanceId == enemy.instanceId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
                            })
                        }
                        logs.add("Khiên Phép của ${enemy.template.name} đã hóa giải Đấu Tay Đôi!")
                    } else {
                        val allyStrikeDmg = ally.strikePower
                        val enemyStrikeDmg = enemy.strikePower
                        s = performNonCombatStrike(
                            state = s,
                            strikerId = ally.instanceId,
                            strikerIsPlayer = sourceIsPlayer,
                            targetId = enemy.instanceId,
                            targetIsEnemy = sourceIsPlayer,
                            overrideDamage = allyStrikeDmg,
                            strikerSnapshot = ally,
                            deferStrikeHook = true,
                            logs = logs
                        )
                        s = performNonCombatStrike(
                            state = s,
                            strikerId = enemy.instanceId,
                            strikerIsPlayer = !sourceIsPlayer,
                            targetId = ally.instanceId,
                            targetIsEnemy = !sourceIsPlayer,
                            overrideDamage = enemyStrikeDmg,
                            strikerSnapshot = enemy,
                            deferStrikeHook = true,
                            logs = logs
                        )
                        if (allyStrikeDmg > 0) {
                            s = onUnitStrike(s, ally.instanceId, isPlayerStriker = sourceIsPlayer, hitNexus = false, logs = logs)
                        }
                        if (enemyStrikeDmg > 0) {
                            s = onUnitStrike(s, enemy.instanceId, isPlayerStriker = !sourceIsPlayer, hitNexus = false, logs = logs)
                        }
                        logs.add("Đấu Tay Đôi: ${ally.template.name} & ${enemy.template.name} giao đấu!")
                    }
                } else if (targetUnitId != null) {
                    logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                }
            }
            CardEffectType.PREDICT_DRAW -> {
                if (sourceIsPlayer) {
                    s = drawPlayerCard(s)
                    logs.add("Thủ Thuật Thời Gian Tiên Đoán và rút 1 lá.")
                } else {
                    s = drawEnemyCard(s)
                    logs.add("Thủ Thuật Thời Gian của địch rút 1 lá.")
                }
            }
            CardEffectType.PLANT_CHIMES -> {
                if (sourceIsPlayer) {
                    val updatedPile = s.playerDrawPile.toMutableList()
                    repeat(minOf(card.effectValue, updatedPile.size)) {
                        val idx = Random.nextInt(updatedPile.size)
                        updatedPile[idx] = updatedPile[idx].copy(hasChimeBoon = true)
                    }
                    s = s.copy(playerDrawPile = updatedPile)
                    if (s.playerBoard.isNotEmpty()) {
                        val top = s.playerBoard.first()
                        s = s.copy(playerBoard = s.playerBoard.map {
                            if (it.instanceId == top.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                        })
                    }
                    logs.add("Gieo ${card.effectValue} Chuông vào bộ bài!")
                }
            }
            CardEffectType.PLANT_PUFFCAPS -> {
                if (sourceIsPlayer) {
                    logs.add("Gieo ${card.effectValue} Bẫy Nấm Độc vào bộ bài địch!")
                    s = dealDamageToEnemyNexus(s, 1, logs)
                } else {
                    val updatedPile = s.playerDrawPile.toMutableList()
                    repeat(minOf(card.effectValue, updatedPile.size)) {
                        val idx = Random.nextInt(updatedPile.size)
                        updatedPile[idx] = updatedPile[idx].copy(hasPuffcapTrap = updatedPile[idx].hasPuffcapTrap + 1)
                    }
                    s = s.copy(playerDrawPile = updatedPile)
                    logs.add("Địch gieo ${card.effectValue} Nấm Độc vào bộ bài bạn!")
                }
            }
            CardEffectType.FROSTBITE_TWO -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                if (targetBoard.isNotEmpty()) {
                    val primary = targetUnitId?.let { id -> targetBoard.find { it.instanceId == id } }
                    val others = targetBoard
                        .filterNot { it.instanceId == primary?.instanceId }
                        .sortedWith(compareBy<UnitOnBoard> { it.isFrostbitten }.thenByDescending { it.effectivePower })
                    val targets = (listOfNotNull(primary) + others).take(2)
                    targets.forEach { t ->
                        s = frostbiteUnit(s, t.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                }
                s = if (sourceIsPlayer) drawPlayerCard(s) else drawEnemyCard(s)
            }
            CardEffectType.ANIVIA_CHAMPION_SPELL -> {
                val targetIsEnemy = sourceIsPlayer
                val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
                if (targetBoard.isNotEmpty()) {
                    val primary = targetUnitId?.let { id -> targetBoard.find { it.instanceId == id } }
                    val others = targetBoard
                        .filterNot { it.instanceId == primary?.instanceId }
                        .sortedWith(compareBy<UnitOnBoard> { it.isFrostbitten }.thenByDescending { it.effectivePower })
                    val targets = (listOfNotNull(primary) + others).take(2)
                    targets.forEach { t ->
                        s = frostbiteUnit(s, t.instanceId, isEnemy = targetIsEnemy, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                }
                if (sourceIsPlayer) {
                    val aniviaCopy = createHandInstance(CardCatalog.getCard("card_anivia"), isFleeting = false, isCreated = true)
                    val pile = s.playerDrawPile.toMutableList()
                    val insertIdx = if (pile.isEmpty()) 0 else Random.nextInt(pile.size + 1)
                    pile.add(insertIdx, aniviaCopy)
                    s = s.copy(playerDrawPile = pile)
                    logs.add("Anivia: Cuồng Phong tráo 1 Anivia vào bộ bài!")
                }
            }
            CardEffectType.GLACIAL_STORM_SKILL -> {
                if (sourceIsPlayer) {
                    s.enemyBoard.toList().forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    logs.add("${card.name} gây $dmg sát thương lên toàn bộ kẻ địch & Nhà Chính địch!")
                } else {
                    s.playerBoard.toList().forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    logs.add("${card.name} của địch gây $dmg sát thương lên quân bạn & Nhà Chính!")
                }
            }
            CardEffectType.WINGS_OF_CRYOPHOENIX -> {
                val isExempt: (UnitOnBoard) -> Boolean = { u ->
                    u.template.isChampion || u.template.id == "card_wild_mystics"
                }
                if (sourceIsPlayer) {
                    s.enemyBoard.toList().filterNot(isExempt).forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s.playerBoard.toList().filterNot(isExempt).forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                    logs.add("Đôi Cánh Phượng Hoàng Băng gây $dmg sát thương lên tất cả bài quân (trừ Anh Hùng & Thầy Tu Hoang Dã).")
                } else {
                    s.playerBoard.toList().filterNot(isExempt).forEach { ally ->
                        s = damageUnit(s, ally.instanceId, isEnemy = false, damage = dmg, isSpell = true, fromAllyEffect = false, logs = logs)
                    }
                    s.enemyBoard.toList().filterNot(isExempt).forEach { enemy ->
                        s = damageUnit(s, enemy.instanceId, isEnemy = true, damage = dmg, isSpell = true, fromAllyEffect = true, logs = logs)
                    }
                }
            }
            CardEffectType.SUMMON_TWO_GIANTS -> {
                val count = maxOf(1, card.effectValue)
                if (sourceIsPlayer) {
                    repeat(count) {
                        if (s.playerBoard.size < 6) {
                            val giant = CardCatalog.giantFollowersPool.random()
                            s = summonPlayerUnit(s, createHandInstance(giant), logs)
                        }
                    }
                    logs.add("Khổng Lồ Chạm Trán triệu hồi các tùy tùng Khổng Lồ Freljord!")
                } else {
                    repeat(count) {
                        if (s.enemyBoard.size < 6) {
                            val giant = CardCatalog.giantFollowersPool.random()
                            s = summonEnemyUnitFromPool(s, giant, spendMana = 0, logs = logs)
                        }
                    }
                }
            }
            CardEffectType.DRAIN -> {
                if (targetUnitId == "ENEMY_NEXUS" || (sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToEnemyNexus(s, dmg, logs)
                    s = applyLifesteal(s, forPlayer = sourceIsPlayer, dmg = dmg)
                    logs.add("${card.name} Hút Cạn $dmg sát thương từ Nhà Chính địch.")
                } else if (targetUnitId == "PLAYER_NEXUS" || (!sourceIsPlayer && targetUnitId == "NEXUS")) {
                    s = dealDamageToPlayerNexus(s, dmg, logs)
                    s = applyLifesteal(s, forPlayer = sourceIsPlayer, dmg = dmg)
                    logs.add("${card.name} của địch Hút Cạn $dmg sát thương từ Nhà Chính của bạn.")
                } else if (targetUnitId != null) {
                    val onEnemy = s.enemyBoard.any { it.instanceId == targetUnitId }
                    val onPlayer = s.playerBoard.any { it.instanceId == targetUnitId }
                    if (onEnemy || onPlayer) {
                        val targetIsEnemy = onEnemy
                        if (targetIsEnemy && !sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = false, logs = logs)
                        } else if (!targetIsEnemy && sourceIsPlayer) {
                            s = triggerFatedOnTarget(s, targetUnitId, sourceIsPlayer = true, logs = logs)
                        }
                        val fromAlly = (targetIsEnemy && !sourceIsPlayer) || (!targetIsEnemy && sourceIsPlayer)
                        val (afterHit, actualDealt) = damageUnitWithResult(
                            state = s,
                            unitId = targetUnitId,
                            isEnemy = targetIsEnemy,
                            damage = dmg,
                            isSpell = true,
                            fromAllyEffect = fromAlly,
                            logs = logs
                        )
                        s = applyLifesteal(afterHit, forPlayer = sourceIsPlayer, dmg = actualDealt)
                        logs.add("${card.name} Hút Cạn gây $actualDealt sát thương thực tế.")
                    } else {
                        logs.add("${card.name} bị mất mục tiêu nên vô hiệu!")
                    }
                } else {
                    val target = if (sourceIsPlayer) s.enemyBoard.minByOrNull { it.health } else s.playerBoard.minByOrNull { it.health }
                    if (target != null) {
                        val (afterHit, actualDealt) = damageUnitWithResult(
                            state = s,
                            unitId = target.instanceId,
                            isEnemy = sourceIsPlayer,
                            damage = dmg,
                            isSpell = true,
                            fromAllyEffect = false,
                            logs = logs
                        )
                        s = applyLifesteal(afterHit, forPlayer = sourceIsPlayer, dmg = actualDealt)
                    } else {
                        if (sourceIsPlayer) s = dealDamageToEnemyNexus(s, dmg, logs)
                        else s = dealDamageToPlayerNexus(s, dmg, logs)
                        s = applyLifesteal(s, forPlayer = sourceIsPlayer, dmg = dmg)
                    }
                    logs.add("${card.name} kích hoạt Hút Cạn.")
                }
            }
            CardEffectType.RALLY -> {
                s = if (sourceIsPlayer) s.copy(playerHasAttackToken = true) else s.copy(enemyHasAttackToken = true)
                logs.add("${card.name} kích hoạt Tập Hợp!")
            }
            CardEffectType.FREE_ATTACK -> {
                val ownBoard = if (sourceIsPlayer) s.playerBoard else s.enemyBoard
                val chosen = targetUnitId?.let { id -> ownBoard.find { it.instanceId == id && it.canAttack } }
                    ?: ownBoard.filter { it.canAttack }.maxByOrNull { it.effectivePower }
                if (chosen != null) {
                    s = startFreeAttack(s, listOf(chosen.instanceId), playerIsAttacking = sourceIsPlayer, logs = logs)
                } else {
                    logs.add("${card.name} không có quân hợp lệ để Tấn Công Tự Do!")
                }
            }
            CardEffectType.PURGE_SUPPRESSION_CURSE, CardEffectType.PURGE_SHACKLES_CURSE, CardEffectType.PURGE_TERROR_CURSE -> {
                logs.add("Đã hóa giải Lời Nguyền ${card.name} khỏi tay.")
            }
            else -> {}
        }
        s = if (sourceIsPlayer) {
            s.copy(playerGraveyard = s.playerGraveyard + card)
        } else {
            s.copy(enemyGraveyard = s.enemyGraveyard + card)
        }
        return s
    }

    // --- ATTACK & BLOCK DECLARATION ---

    fun enterAttackDeclaration(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.MAIN_ACTION_PHASE ||
            !state.playerHasAttackToken ||
            state.spellStack.isNotEmpty() ||
            state.attackingUnitIds.isNotEmpty() ||
            state.playerBoard.none { it.canAttack }
        ) return state
        return state.copy(
            phase = CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            playerInitiatedCombat = true,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null
        )
    }

    fun cancelAttackDeclaration(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.spellStack.isNotEmpty()
        ) return state
        return state.copy(
            phase = CombatPhase.MAIN_ACTION_PHASE,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false
        )
    }

    fun toggleUnitAttack(state: CombatState, unitId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        val unit = state.playerBoard.find { it.instanceId == unitId } ?: return state
        if (!unit.canAttack) return state

        val current = state.attackingUnitIds.toMutableList()
        val updatedChallenges = state.challengedPairings.toMutableMap()
        var nextSelected = state.selectedAttackerForChallenger
        if (current.contains(unitId)) {
            current.remove(unitId)
            updatedChallenges.remove(unitId)
            if (nextSelected == unitId) nextSelected = null
        } else {
            current.add(unitId)
        }
        return state.copy(
            attackingUnitIds = current,
            challengedPairings = updatedChallenges,
            selectedAttackerForChallenger = nextSelected
        )
    }

    fun selectChallengerAttacker(state: CombatState, attackerId: String?): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        if (attackerId == null) return state.copy(selectedAttackerForChallenger = null)
        val unit = state.playerBoard.find { it.instanceId == attackerId } ?: return state
        val hasVulnerableTarget = state.enemyBoard.any {
            it.keywords.contains(Keyword.VULNERABLE) &&
                it.template.type != CardType.LANDMARK &&
                !it.isStunned &&
                !it.isShackled
        }
        if ((!unit.keywords.contains(Keyword.CHALLENGER) && !hasVulnerableTarget) ||
            !state.attackingUnitIds.contains(attackerId)
        ) return state
        val toggled = if (state.selectedAttackerForChallenger == attackerId) null else attackerId
        return state.copy(selectedAttackerForChallenger = toggled)
    }

    fun assignChallengerTarget(state: CombatState, attackerId: String, enemyId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.spellStack.isNotEmpty()
        ) return state
        val atk = state.playerBoard.find { it.instanceId == attackerId } ?: return state
        val enemy = state.enemyBoard.find { it.instanceId == enemyId } ?: return state
        if (!state.attackingUnitIds.contains(attackerId)) return state
        if (!atk.keywords.contains(Keyword.CHALLENGER) && !enemy.keywords.contains(Keyword.VULNERABLE)) return state
        if (enemy.template.type == CardType.LANDMARK || enemy.isStunned || enemy.isShackled) return state

        val updated = state.challengedPairings.toMutableMap()
        updated.entries.find { it.value == enemyId }?.let { updated.remove(it.key) }
        updated[attackerId] = enemyId
        return state.copy(
            challengedPairings = updated,
            selectedAttackerForChallenger = null
        )
    }

    fun clearChallengerTarget(state: CombatState, attackerId: String): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE
        ) return state
        val updated = state.challengedPairings.toMutableMap()
        updated.remove(attackerId)
        return state.copy(challengedPairings = updated)
    }

    fun commitPlayerAttack(state: CombatState): CombatState {
        if (state.priorityHolder != PriorityHolder.PLAYER ||
            state.phase != CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            !state.playerHasAttackToken ||
            !state.playerInitiatedCombat ||
            state.attackingUnitIds.isEmpty()
        ) return state

        var s = state
        val logs = s.combatLog.toMutableList()
        val attackers = s.attackingUnitIds.mapNotNull { id -> s.playerBoard.find { it.instanceId == id } }

        // Check Scout Rally
        val allScout = attackers.all { it.keywords.contains(Keyword.SCOUT) }
        val scoutRally = allScout && !s.scoutUsedThisRound

        // 1. Hallowed bonus for leftmost attacker (scoped to current round; runs before Support!)
        if (s.playerHallowedStacks > 0 && attackers.isNotEmpty()) {
            val firstId = attackers.first().instanceId
            s = recordRoundHallowedBuff(s, firstId, s.playerHallowedStacks)
            s = s.copy(playerBoard = s.playerBoard.map {
                if (it.instanceId == firstId) it.copy(power = it.power + s.playerHallowedStacks) else it
            })
            logs.add("Thiêng Liêng ban cho ${attackers.first().template.name} +${s.playerHallowedStacks}|+0!")
        }

        // 2. Lurk: check top of deck when a Lurker attacks (once per round)
        s = triggerPlayerLurkIfEligible(s, attackers, logs)

        // 3. Support: each attacker with Support buffs the ally immediately to its right (left-to-right)
        s = applyAttackSupportBuffs(s, s.attackingUnitIds, playerIsAttacking = true, logs = logs)

        // Guinsoo's Rageblade & Ashe Attack triggers
        attackers.forEach { atk ->
            if (atk.template.isChampion && s.equippedRelics.any { it.id == "relic_guinsoos_rageblade" }) {
                s = s.copy(playerBoard = s.playerBoard.map {
                    if (it.instanceId == atk.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                })
            }
            if (atk.template.id == "card_ashe" && !atk.isSilenced) {
                val target = s.enemyBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                    ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                if (target != null) {
                    s = frostbiteUnit(s, target.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                }
            }
        }

        // Resolve any queued Fast spells before transitioning to defender's block phase
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }

        // Pipeline 3.1: Đẩy Kỹ năng Nguyên Tố của Anivia lên Skill Stack khi tấn công
        val attackSkills = mutableListOf<SpellStackEntry>()
        attackers.forEach { atk ->
            val liveAtk = s.playerBoard.find { it.instanceId == atk.instanceId }
            if (liveAtk != null && liveAtk.template.id == "card_anivia" && !liveAtk.isSilenced && !liveAtk.isStunned) {
                val baseStormDmg = if (liveAtk.isLeveledUp) 2 else 1
                val skillCard = CardCatalog.getCard("skill_glacial_storm").copy(effectValue = baseStormDmg)
                s = onPlayerSkillOrElementalPlayed(s, logs, skillCard.name)
                attackSkills.add(
                    SpellStackEntry(
                        id = "st_${nextId()}",
                        sourceIsPlayer = true,
                        card = skillCard,
                        targetUnitId = null,
                        damageBonus = 0
                    )
                )
                logs.add("Kỹ năng ${skillCard.name} của Anivia (Lv${if (liveAtk.isLeveledUp) 2 else 1}) vào hàng chờ!")
            }
        }

        val survivingAttackerIds = s.attackingUnitIds.filter { id ->
            s.playerBoard.any { it.instanceId == id && !it.isStunned }
        }
        val lockedBlocks = s.challengedPairings.filter { (atkId, enemyId) ->
            survivingAttackerIds.contains(atkId) &&
                s.enemyBoard.any { it.instanceId == enemyId && !it.isStunned && !it.isShackled }
        }

        logs.add("Tuyên bố tấn công với ${survivingAttackerIds.size} quân! Địch đang chọn quân chặn...")
        return s.copy(
            playerHasAttackToken = false,
            scoutUsedThisRound = s.scoutUsedThisRound || allScout,
            pendingScoutRally = scoutRally,
            playerInitiatedCombat = true,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.ENEMY,
            consecutivePasses = 0,
            lastStackCaster = if (attackSkills.isNotEmpty()) PriorityHolder.PLAYER else s.lastStackCaster,
            spellStack = s.spellStack + attackSkills,
            attackingUnitIds = survivingAttackerIds,
            blockPairings = lockedBlocks,
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = lockedBlocks.keys.toSet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            combatLog = logs.takeLast(16)
        )
    }

    fun assignPlayerBlocker(state: CombatState, attackerId: String, blockerId: String): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ||
            state.priorityHolder != PriorityHolder.PLAYER ||
            state.playerInitiatedCombat ||
            state.blockCommitted ||
            state.lockedBlockAttackerIds.contains(attackerId)
        ) return state
        val attacker = state.enemyBoard.find { it.instanceId == attackerId } ?: return state
        val blocker = state.playerBoard.find { it.instanceId == blockerId } ?: return state
        if (!blocker.canBlockAttacker(attacker)) return state

        val updated = state.blockPairings.toMutableMap()
        val prevKey = updated.entries.find { it.value == blockerId }?.key
        if (prevKey != null) {
            if (state.lockedBlockAttackerIds.contains(prevKey)) return state
            updated.remove(prevKey)
        }
        updated[attackerId] = blockerId
        return state.copy(blockPairings = updated, selectedEnemyAttackerForBlock = null)
    }

    fun removePlayerBlocker(state: CombatState, attackerId: String): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE ||
            state.priorityHolder != PriorityHolder.PLAYER ||
            state.playerInitiatedCombat ||
            state.blockCommitted ||
            state.lockedBlockAttackerIds.contains(attackerId)
        ) return state
        val updated = state.blockPairings.toMutableMap()
        updated.remove(attackerId)
        return state.copy(blockPairings = updated)
    }

    fun commitPlayerBlock(state: CombatState): CombatState {
        if (state.phase != CombatPhase.COMBAT_DECLARE_BLOCK_PHASE || state.priorityHolder != PriorityHolder.PLAYER) return state
        if (state.playerInitiatedCombat) {
            if (state.spellStack.isNotEmpty()) {
                return resolveSpellStack(state)
            }
            return finishPlayerAttackCombat(state)
        }
        if (!state.blockCommitted && state.spellStack.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Đã chốt đội hình chặn — Địch có thể phản hồi phép.")
            return state.copy(
                blockCommitted = true,
                confirmedBlockedAttackerIds = state.blockPairings.keys.toSet(),
                priorityHolder = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        }
        return finishEnemyAttackCombat(state)
    }

    internal fun finishPlayerAttackCombat(state: CombatState): CombatState {
        var s = state.copy(blockCommitted = false)
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }
        val logs = s.combatLog.toMutableList()
        s = resolveCombatResolution(s, playerIsAttacking = true, logs = logs)
        val scoutRally = s.pendingScoutRally
        val postCombatPriority = if (scoutRally) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        return checkGameOver(
            s.copy(
                playerHasAttackToken = s.playerHasAttackToken || scoutRally,
                pendingScoutRally = false,
                isFreeAttack = false,
                blockCommitted = false,
                playerCastSpellInBlock = false,
                enemyCastBurstInBlock = false,
                playerFastSpellUsedInCombat = false,
                enemyFastSpellUsedInCombat = false,
                phase = if (s.playerWon != null) CombatPhase.GAME_OVER else CombatPhase.MAIN_ACTION_PHASE,
                priorityHolder = postCombatPriority,
                consecutivePasses = 0,
                lastStackCaster = null,
                attackingUnitIds = emptyList(),
                blockPairings = emptyMap(),
                confirmedBlockedAttackerIds = emptySet(),
                challengedPairings = emptyMap(),
                lockedBlockAttackerIds = emptySet(),
                selectedAttackerForChallenger = null,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        )
    }

    internal fun finishEnemyAttackCombat(state: CombatState): CombatState {
        var s = state.copy(blockCommitted = false)
        if (s.spellStack.isNotEmpty()) {
            s = resolveSpellStack(s)
            if (s.playerWon != null) return s
        }
        val logs = s.combatLog.toMutableList()
        s = resolveCombatResolution(s, playerIsAttacking = false, logs = logs)
        val nextEnemyToken = if (s.isFreeAttack) s.enemyHasAttackToken else false
        return checkGameOver(
            s.copy(
                enemyHasAttackToken = nextEnemyToken,
                isFreeAttack = false,
                blockCommitted = false,
                playerCastSpellInBlock = false,
                enemyCastBurstInBlock = false,
                playerFastSpellUsedInCombat = false,
                enemyFastSpellUsedInCombat = false,
                phase = if (s.playerWon != null) CombatPhase.GAME_OVER else CombatPhase.MAIN_ACTION_PHASE,
                priorityHolder = PriorityHolder.PLAYER,
                consecutivePasses = 0,
                lastStackCaster = null,
                attackingUnitIds = emptyList(),
                blockPairings = emptyMap(),
                confirmedBlockedAttackerIds = emptySet(),
                challengedPairings = emptyMap(),
                lockedBlockAttackerIds = emptySet(),
                selectedAttackerForChallenger = null,
                selectedEnemyAttackerForBlock = null,
                combatLog = logs.takeLast(16)
            )
        )
    }

    internal fun resolveCombatResolution(
        state: CombatState,
        playerIsAttacking: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val attackers = s.attackingUnitIds.toList()

        // Compute strike power dynamically against current state so earlier left-to-right pair results take effect immediately
        fun strikePowerOf(currState: CombatState, unit: UnitOnBoard, isPlayersUnit: Boolean): Int {
            val aura = if (isPlayersUnit && !unit.isFrostbitten && !unit.keywords.contains(Keyword.FORMIDABLE) &&
                currState.championId == "champ_ashe" && currState.starLevel >= 2
            ) {
                currState.enemyBoard.count { it.isFrostbitten }
            } else 0
            return unit.strikePower + aura
        }

        fun applyOverwhelmOrStandardStrike(
            currState: CombatState,
            attacker: UnitOnBoard,
            defender: UnitOnBoard,
            strikeDmg: Int
        ): Pair<CombatState, Boolean> {
            var st = currState
            if (strikeDmg <= 0) return st to false

            val defHadBarrier = defender.hasBarrier
            val defHasTough = defender.keywords.contains(Keyword.TOUGH)
            val hasOverwhelm = attacker.keywords.contains(Keyword.OVERWHELM)

            var nexusOverflow = 0
            val damageAssignedToBlocker = if (hasOverwhelm) {
                // Barrier absorbs damage before Tough applies; without Barrier, Tough adds +1 to lethal assignment
                val lethalNeeded = defender.health + (if (!defHadBarrier && defHasTough) 1 else 0)
                nexusOverflow = maxOf(0, strikeDmg - lethalNeeded)
                minOf(strikeDmg, lethalNeeded)
            } else {
                strikeDmg
            }

            val (afterHit, actualDealtToBlocker) = damageUnitWithResult(
                state = st,
                unitId = defender.instanceId,
                isEnemy = playerIsAttacking,
                damage = damageAssignedToBlocker,
                isSpell = false,
                fromAllyEffect = false,
                logs = logs
            )
            st = afterHit

            if (nexusOverflow > 0) {
                st = if (playerIsAttacking) {
                    dealDamageToEnemyNexus(st, nexusOverflow, logs)
                } else {
                    dealDamageToPlayerNexus(st, nexusOverflow, logs)
                }
            }

            val totalDealt = actualDealtToBlocker + nexusOverflow
            if (attacker.keywords.contains(Keyword.LIFESTEAL) && totalDealt > 0) {
                st = applyLifesteal(st, forPlayer = playerIsAttacking, dmg = totalDealt)
            }

            val defStillOnBoard = (if (playerIsAttacking) st.enemyBoard else st.playerBoard)
                .any { it.instanceId == defender.instanceId }
            if (!defStillOnBoard) {
                st = applyKillBonuses(st, attacker.instanceId, isPlayerKiller = playerIsAttacking, logs = logs)
            }

            if (attacker.impactStacks > 0) {
                st = if (playerIsAttacking) {
                    dealDamageToEnemyNexus(st, attacker.impactStacks, logs)
                } else {
                    dealDamageToPlayerNexus(st, attacker.impactStacks, logs)
                }
                logs.add("${attacker.template.name} Tác Động gây ${attacker.impactStacks} sát thương Nhà Chính!")
            }

            return st to (nexusOverflow > 0)
        }

        for (atkId in attackers) {
            val atk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId } ?: continue
            if (atk.isStunned) continue
            val blkId = s.blockPairings[atkId]
            val blk = blkId?.let { id -> (if (playerIsAttacking) s.enemyBoard else s.playerBoard).find { it.instanceId == id } }
            val activeBlk = blk?.takeIf { !it.isStunned }
            val wasBlocked = s.confirmedBlockedAttackerIds.contains(atkId) || blkId != null

            val initialAtkDmg = strikePowerOf(s, atk, playerIsAttacking)
            var attackerStruck = false
            var blockerStruck = false

            if (!wasBlocked && activeBlk == null) {
                // Unblocked strike directly to Nexus
                if (initialAtkDmg > 0) {
                    attackerStruck = true
                    val strikes = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1
                    var totalNexusDmg = 0
                    repeat(strikes) {
                        val liveAtk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId }
                            ?: return@repeat
                        val dmg = strikePowerOf(s, liveAtk, playerIsAttacking)
                        if (dmg > 0) {
                            totalNexusDmg += dmg
                            if (playerIsAttacking) {
                                s = dealDamageToEnemyNexus(s, dmg, logs)
                            } else {
                                s = dealDamageToPlayerNexus(s, dmg, logs)
                            }
                            if (liveAtk.keywords.contains(Keyword.LIFESTEAL)) {
                                s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dmg)
                            }
                            if (liveAtk.impactStacks > 0) {
                                s = if (playerIsAttacking) {
                                    dealDamageToEnemyNexus(s, liveAtk.impactStacks, logs)
                                } else {
                                    dealDamageToPlayerNexus(s, liveAtk.impactStacks, logs)
                                }
                                logs.add("${liveAtk.template.name} Tác Động gây ${liveAtk.impactStacks} sát thương Nhà Chính!")
                            }
                            s = onUnitStrike(s, atkId, isPlayerStriker = playerIsAttacking, hitNexus = true, logs = logs)
                        }
                    }
                    logs.add("${atk.template.name} đánh thẳng Nhà Chính gây $totalNexusDmg sát thương!")
                }
            } else if (activeBlk == null) {
                // Phantom / Ghost block: blocker died, recalled, obliterated, captured, or stunned after block confirmation
                if (atk.keywords.contains(Keyword.OVERWHELM)) {
                    if (initialAtkDmg > 0) {
                        attackerStruck = true
                        val strikes = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1
                        var totalNexusDmg = 0
                        repeat(strikes) {
                            val liveAtk = (if (playerIsAttacking) s.playerBoard else s.enemyBoard).find { it.instanceId == atkId }
                                ?: return@repeat
                            val dmg = strikePowerOf(s, liveAtk, playerIsAttacking)
                            if (dmg > 0) {
                                totalNexusDmg += dmg
                                if (playerIsAttacking) {
                                    s = dealDamageToEnemyNexus(s, dmg, logs)
                                } else {
                                    s = dealDamageToPlayerNexus(s, dmg, logs)
                                }
                                if (liveAtk.keywords.contains(Keyword.LIFESTEAL)) {
                                    s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = dmg)
                                }
                                if (liveAtk.impactStacks > 0) {
                                    s = if (playerIsAttacking) {
                                        dealDamageToEnemyNexus(s, liveAtk.impactStacks, logs)
                                    } else {
                                        dealDamageToPlayerNexus(s, liveAtk.impactStacks, logs)
                                    }
                                    logs.add("${liveAtk.template.name} Tác Động gây ${liveAtk.impactStacks} sát thương Nhà Chính!")
                                }
                                s = onUnitStrike(s, atkId, isPlayerStriker = playerIsAttacking, hitNexus = true, logs = logs)
                            }
                        }
                        logs.add("${atk.template.name} (Áp Đảo) xuyên thẳng Nhà Chính gây $totalNexusDmg sát thương!")
                    }
                } else {
                    logs.add("${atk.template.name} đã bị chặn trước đó nên không đánh Nhà Chính.")
                }
            } else {
                // Combat between Attacker and active Blocker
                val hasFirstStrike = atk.keywords.contains(Keyword.QUICK_ATTACK) || atk.keywords.contains(Keyword.DOUBLE_ATTACK)
                val strikesCount = if (atk.keywords.contains(Keyword.DOUBLE_ATTACK)) 2 else 1

                if (hasFirstStrike) {
                    if (initialAtkDmg > 0) {
                        attackerStruck = true
                        val (afterFirst, hitNexus1) = applyOverwhelmOrStandardStrike(s, atk, activeBlk, initialAtkDmg)
                        s = afterFirst
                        s = onUnitStrike(s, atk.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = hitNexus1, logs = logs)
                    }

                    val survivingBlk = (if (playerIsAttacking) s.enemyBoard else s.playerBoard)
                        .find { it.instanceId == activeBlk.instanceId && !it.isStunned }
                    val survivingAtkAfter1 = (if (playerIsAttacking) s.playerBoard else s.enemyBoard)
                        .find { it.instanceId == atk.instanceId && !it.isStunned }

                    if (survivingBlk != null) {
                        // Capture blocker's simultaneous strike power BEFORE Strike 2 hits
                        val blkDmg = strikePowerOf(s, survivingBlk, !playerIsAttacking)
                        var strike2Executed = false
                        var hitNexus2 = false

                        if (strikesCount == 2 && survivingAtkAfter1 != null) {
                            val secondAtkDmg = strikePowerOf(s, survivingAtkAfter1, playerIsAttacking)
                            if (secondAtkDmg > 0) {
                                attackerStruck = true
                                strike2Executed = true
                                val (afterStrike2, h2) = applyOverwhelmOrStandardStrike(s, survivingAtkAfter1, survivingBlk, secondAtkDmg)
                                s = afterStrike2
                                hitNexus2 = h2
                            }
                        }

                        if (blkDmg > 0 && survivingAtkAfter1 != null) {
                            blockerStruck = true
                            val (afterBlkHit, actualBlkDealt) = damageUnitWithResult(
                                state = s,
                                unitId = survivingAtkAfter1.instanceId,
                                isEnemy = !playerIsAttacking,
                                damage = blkDmg,
                                isSpell = false,
                                fromAllyEffect = false,
                                logs = logs
                            )
                            s = afterBlkHit
                            if (survivingBlk.keywords.contains(Keyword.LIFESTEAL) && actualBlkDealt > 0) {
                                s = applyLifesteal(s, forPlayer = !playerIsAttacking, dmg = actualBlkDealt)
                            }
                            val atkAfterReturn = (if (playerIsAttacking) s.playerBoard else s.enemyBoard)
                                .find { it.instanceId == survivingAtkAfter1.instanceId }
                            if (atkAfterReturn == null) {
                                s = applyKillBonuses(s, survivingBlk.instanceId, isPlayerKiller = !playerIsAttacking, logs = logs)
                            }
                        }

                        if (strike2Executed && survivingAtkAfter1 != null) {
                            s = onUnitStrike(s, survivingAtkAfter1.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = hitNexus2, logs = logs)
                        }
                        if (blockerStruck) {
                            s = onUnitStrike(s, survivingBlk.instanceId, isPlayerStriker = !playerIsAttacking, hitNexus = false, logs = logs)
                        }
                    } else if (strikesCount == 2 && survivingAtkAfter1 != null) {
                        // Blocker died on Strike 1 -> Strike 2 only hits Nexus if Attacker has Overwhelm
                        val secondAtkDmg = strikePowerOf(s, survivingAtkAfter1, playerIsAttacking)
                        if (secondAtkDmg > 0 && survivingAtkAfter1.keywords.contains(Keyword.OVERWHELM)) {
                            if (playerIsAttacking) {
                                s = dealDamageToEnemyNexus(s, secondAtkDmg, logs)
                            } else {
                                s = dealDamageToPlayerNexus(s, secondAtkDmg, logs)
                            }
                            if (survivingAtkAfter1.keywords.contains(Keyword.LIFESTEAL)) {
                                s = applyLifesteal(s, forPlayer = playerIsAttacking, dmg = secondAtkDmg)
                            }
                            if (survivingAtkAfter1.impactStacks > 0) {
                                s = if (playerIsAttacking) {
                                    dealDamageToEnemyNexus(s, survivingAtkAfter1.impactStacks, logs)
                                } else {
                                    dealDamageToPlayerNexus(s, survivingAtkAfter1.impactStacks, logs)
                                }
                            }
                            s = onUnitStrike(s, survivingAtkAfter1.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = true, logs = logs)
                        }
                    }
                } else {
                    // Simultaneous normal combat: snapshot both strike powers first
                    val atkDmg = initialAtkDmg
                    val blkDmg = strikePowerOf(s, activeBlk, !playerIsAttacking)
                    var atkHitNexus = false

                    if (atkDmg > 0) {
                        attackerStruck = true
                        val (afterAtk, h) = applyOverwhelmOrStandardStrike(s, atk, activeBlk, atkDmg)
                        s = afterAtk
                        atkHitNexus = h
                    }
                    if (blkDmg > 0) {
                        blockerStruck = true
                        val (afterBlkHit, actualBlkDealt) = damageUnitWithResult(
                            state = s,
                            unitId = atk.instanceId,
                            isEnemy = !playerIsAttacking,
                            damage = blkDmg,
                            isSpell = false,
                            fromAllyEffect = false,
                            logs = logs
                        )
                        s = afterBlkHit
                        if (activeBlk.keywords.contains(Keyword.LIFESTEAL) && actualBlkDealt > 0) {
                            s = applyLifesteal(s, forPlayer = !playerIsAttacking, dmg = actualBlkDealt)
                        }
                        val atkSurvived = (if (playerIsAttacking) s.playerBoard else s.enemyBoard)
                            .find { it.instanceId == atk.instanceId }
                        if (atkSurvived == null) {
                            s = applyKillBonuses(s, activeBlk.instanceId, isPlayerKiller = !playerIsAttacking, logs = logs)
                        }
                    }
                    if (attackerStruck) {
                        s = onUnitStrike(s, atk.instanceId, isPlayerStriker = playerIsAttacking, hitNexus = atkHitNexus, logs = logs)
                    }
                    if (blockerStruck) {
                        s = onUnitStrike(s, activeBlk.instanceId, isPlayerStriker = !playerIsAttacking, hitNexus = false, logs = logs)
                    }
                }
            }

            // Sức Mạnh Địch: Phân Thân Bóng Tối (Zed Trùm Phụ)
            if (!playerIsAttacking && canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "LIVING_SHADOW") {
                val survivingAtk = s.enemyBoard.find { it.instanceId == atkId }
                if (survivingAtk != null && !survivingAtk.keywords.contains(Keyword.EPHEMERAL)) {
                    s = s.copy(enemyBoard = s.enemyBoard.map { u ->
                        if (u.instanceId == atkId) {
                            u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
                        } else u
                    })
                    logs.add("Phân Thân Bóng Tối cường hóa ${survivingAtk.template.name} (+1|+1)!")
                }
            }

            // Ephemeral attacker or blocker dies ONLY after actually striking
            val postAtkBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
            val postAtk = postAtkBoard.find { it.instanceId == atkId }
            if (attackerStruck && postAtk != null && !postAtk.isStunned && postAtk.keywords.contains(Keyword.EPHEMERAL)) {
                s = removeUnit(s, postAtk.instanceId, isEnemy = !playerIsAttacking, logs = logs)
            }
            if (activeBlk != null) {
                val postBlkBoard = if (playerIsAttacking) s.enemyBoard else s.playerBoard
                val postBlk = postBlkBoard.find { it.instanceId == activeBlk.instanceId }
                if (blockerStruck && postBlk != null && !postBlk.isStunned && postBlk.keywords.contains(Keyword.EPHEMERAL)) {
                    s = removeUnit(s, postBlk.instanceId, isEnemy = playerIsAttacking, logs = logs)
                }
            }
        }

        // Garen 1★/3★ strike scaling
        if (s.championId == "champ_garen" && s.starLevel >= 1 && playerIsAttacking) {
            val bonus = if (s.starLevel >= 3) 2 else 1
            s = s.copy(playerBoard = s.playerBoard.map { u ->
                if (attackers.contains(u.instanceId)) {
                    u.copy(power = u.power + bonus, health = u.health + bonus, maxHealth = u.maxHealth + bonus)
                } else u
            })
            if (s.starLevel >= 3 && attackers.isNotEmpty() && s.playerHand.size < 10) {
                s = s.copy(playerHand = s.playerHand + createHandInstance(CardCatalog.getCard("card_single_combat"), isFleeting = true))
                logs.add("Garen 3★ tạo Đấu Tay Đôi Chóng Tàn!")
            }
        }

        return checkGameOver(s)
    }

    private fun performNonCombatStrike(
        state: CombatState,
        strikerId: String,
        strikerIsPlayer: Boolean,
        targetId: String,
        targetIsEnemy: Boolean,
        overrideDamage: Int? = null,
        strikerSnapshot: UnitOnBoard? = null,
        deferStrikeHook: Boolean = false,
        logs: MutableList<String>
    ): CombatState {
        val strikerBoard = if (strikerIsPlayer) state.playerBoard else state.enemyBoard
        val striker = strikerBoard.find { it.instanceId == strikerId } ?: strikerSnapshot ?: return state
        val dmg = overrideDamage ?: striker.strikePower
        if (dmg <= 0) return state

        val (afterHit, actualDealt) = damageUnitWithResult(
            state = state,
            unitId = targetId,
            isEnemy = targetIsEnemy,
            damage = dmg,
            isSpell = false,
            fromAllyEffect = false,
            logs = logs
        )
        var s = afterHit
        if (striker.keywords.contains(Keyword.LIFESTEAL) && actualDealt > 0) {
            s = applyLifesteal(s, forPlayer = strikerIsPlayer, dmg = actualDealt)
        }
        val targetSurvived = (if (targetIsEnemy) s.enemyBoard else s.playerBoard).any { it.instanceId == targetId }
        if (!targetSurvived) {
            s = applyKillBonuses(s, strikerId, isPlayerKiller = strikerIsPlayer, logs = logs)
        }
        if (!deferStrikeHook) {
            s = onUnitStrike(s, strikerId, isPlayerStriker = strikerIsPlayer, hitNexus = false, logs = logs)
        }
        return s
    }

    private fun applyKillBonuses(
        state: CombatState,
        killerId: String,
        isPlayerKiller: Boolean,
        logs: MutableList<String>
    ): CombatState {
        val board = if (isPlayerKiller) state.playerBoard else state.enemyBoard
        val updated = board.map { u ->
            if (u.instanceId == killerId && !u.isSilenced && u.keywords.contains(Keyword.FURY)) {
                logs.add("${u.template.name} kích hoạt Cuồng Nộ (+1|+1)!")
                u.copy(power = u.power + 1, health = u.health + 1, maxHealth = u.maxHealth + 1)
            } else u
        }
        return if (isPlayerKiller) state.copy(playerBoard = updated) else state.copy(enemyBoard = updated)
    }

    private fun applyLifesteal(state: CombatState, forPlayer: Boolean, dmg: Int): CombatState {
        if (dmg <= 0) return state
        return if (forPlayer) {
            state.copy(playerNexusHp = minOf(state.playerMaxHp, state.playerNexusHp + dmg))
        } else {
            state.copy(enemyNexusHp = minOf(state.enemyMaxHp, state.enemyNexusHp + dmg))
        }
    }

    private fun damageUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        damage: Int,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String>
    ): CombatState = damageUnitWithResult(state, unitId, isEnemy, damage, isSpell, fromAllyEffect, logs).first

    private fun damageUnitWithResult(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        damage: Int,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String>
    ): Pair<CombatState, Int> {
        if (damage <= 0) return state to 0
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state to 0

        if (!isEnemy && target.template.id == "card_guardian_follower" && !target.isSilenced && fromAllyEffect) {
            logs.add("${target.template.name} miễn nhiễm sát thương từ bài phép/Kỹ Năng đồng minh.")
            return state to 0
        }

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            val next = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
            return next to 0
        }

        if (target.hasBarrier) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasBarrier = false) else it
            }
            logs.add("Lá Chắn của ${target.template.name} đã đỡ sát thương!")
            val next = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
            return next to 0
        }

        val afterTough = if (target.keywords.contains(Keyword.TOUGH)) maxOf(0, damage - 1) else damage
        val crystallizeMult = if (isEnemy && target.effectivePower == 0 && state.championId == "champ_anivia" && state.starLevel >= 6) 2 else 1
        val actualDmg = afterTough * crystallizeMult
        if (actualDmg <= 0) return state to 0
        val newHp = target.health - actualDmg

        if (newHp <= 0) {
            return removeUnit(state, unitId, isEnemy, logs) to actualDmg
        } else {
            var newPow = target.power
            var updatedMaxHp = target.maxHealth
            var finalHp = newHp

            if (isEnemy && state.activePowers.any { it.id == "pow_enfeebling_strike" }) {
                newPow = maxOf(0, newPow - actualDmg)
            }
            if (!isEnemy && actualDmg > 0 && state.championId == "champ_anivia" && state.starLevel >= 4) {
                newPow += 1
                finalHp += 1
                updatedMaxHp += 1
                logs.add("Bền Bỉ (4★): ${target.template.name} sống sót qua sát thương và nhận +1|+1!")
            }

            val updated = board.map {
                if (it.instanceId == unitId) it.copy(health = finalHp, maxHealth = updatedMaxHp, power = newPow) else it
            }
            var s = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)

            if (actualDmg > 0 && target.template.id == "card_mortal_prophet" && !target.isSilenced) {
                if (!isEnemy) {
                    s = dealDamageToEnemyNexus(s, 1, logs)
                    logs.add("Tiên Tri Chết Chóc sống sót: Gây 1 sát thương lên Nhà Chính địch!")
                } else {
                    s = dealDamageToPlayerNexus(s, 1, logs)
                }
            }
            return s to actualDmg
        }
    }

    private fun cleanCombatUnitReferences(
        state: CombatState,
        unitId: String,
        removeFromBlocksEvenIfCommitted: Boolean = false
    ): CombatState {
        val cleanAttackers = state.attackingUnitIds.filterNot { it == unitId }
        val cleanChallenges = state.challengedPairings.filter { (k, v) -> k != unitId && v != unitId }
        return if (!state.blockCommitted || removeFromBlocksEvenIfCommitted) {
            val cleanPairings = state.blockPairings.filter { (k, v) -> k != unitId && v != unitId }
            val cleanLocked = state.lockedBlockAttackerIds - unitId
            state.copy(
                attackingUnitIds = cleanAttackers,
                challengedPairings = cleanChallenges,
                blockPairings = cleanPairings,
                lockedBlockAttackerIds = cleanLocked
            )
        } else {
            val cleanPairings = state.blockPairings.filter { (k, v) -> k != unitId && v != unitId }
            state.copy(
                attackingUnitIds = cleanAttackers,
                challengedPairings = cleanChallenges,
                blockPairings = cleanPairings
            )
        }
    }

    internal fun releaseCapturedUnits(
        state: CombatState,
        captor: UnitOnBoard,
        logs: MutableList<String>
    ): CombatState {
        if (captor.capturedUnits.isEmpty()) return state
        var s = state
        for (cap in captor.capturedUnits) {
            val restored = cap.copy(
                isStunned = false,
                isFrostbitten = false,
                frostbiteReduction = null,
                hasBarrier = cap.keywords.contains(Keyword.BARRIER)
            )
            if (restored.ownerIsPlayer) {
                if (s.playerBoard.size < 6) {
                    s = s.copy(playerBoard = s.playerBoard + restored)
                    logs.add("${restored.template.name} được giải thoát trở lại Hàng Chờ!")
                }
            } else {
                if (s.enemyBoard.size < 6) {
                    s = s.copy(enemyBoard = s.enemyBoard + restored)
                    logs.add("${restored.template.name} của địch được giải thoát trở lại Hàng Chờ!")
                }
            }
        }
        return s
    }

    internal fun removeUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val idx = board.indexOfFirst { it.instanceId == unitId }
        if (idx < 0) return s
        val target = board[idx]

        val lastBreathRally = !target.isSilenced && target.attachedItems.any { it.onLastBreathRally }

        // Deathless revival: counts as death & revive (new instanceId, Hallowed/Last Breath/Graveyard triggered, Stunned, 1 HP)
        if (target.hasDeathless) {
            val withoutOld = board.filterNot { it.instanceId == unitId }
            s = if (isEnemy) {
                s.copy(
                    enemyBoard = withoutOld,
                    enemyGraveyard = s.enemyGraveyard + target.template,
                    roundHallowedBuffs = s.roundHallowedBuffs - unitId
                )
            } else {
                s.copy(
                    playerBoard = withoutOld,
                    playerGraveyard = s.playerGraveyard + target.template,
                    roundHallowedBuffs = s.roundHallowedBuffs - unitId
                )
            }
            if (!target.isSilenced && target.keywords.contains(Keyword.HALLOWED)) {
                s = if (!isEnemy) s.copy(playerHallowedStacks = s.playerHallowedStacks + 1)
                else s.copy(enemyHallowedStacks = s.enemyHallowedStacks + 1)
            }
            if (target.equippedGear != null) {
                s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
            }
            if (lastBreathRally) {
                s = if (!isEnemy) s.copy(playerHasAttackToken = true) else s.copy(enemyHasAttackToken = true)
                logs.add("Trăn Trối Tập Hợp: Nhận Biểu Tượng Tấn Công!")
            }
            s = releaseCapturedUnits(s, target, logs)

            val (unequippedTarget, _) = detachEquipmentFromUnit(target)
            val revivedKeywords = (unequippedTarget.keywords - Keyword.DEATHLESS) + Keyword.STUN
            val revived = unequippedTarget.copy(
                instanceId = if (isEnemy) "eu_${nextId()}" else "u_${nextId()}",
                health = 1,
                hasDeathless = false,
                hasBarrier = revivedKeywords.contains(Keyword.BARRIER),
                hasSpellShield = revivedKeywords.contains(Keyword.SPELLSHIELD),
                isStunned = true,
                isFrostbitten = false,
                frostbiteReduction = null,
                isSilenced = false,
                gloomStacks = 0,
                fatedUsedThisRound = false,
                keywords = revivedKeywords,
                equippedGear = null,
                equippedGearName = null,
                capturedUnits = emptyList()
            )
            val currentBoardAfterRelease = (if (isEnemy) s.enemyBoard else s.playerBoard).toMutableList()
            val insertIdx = minOf(idx, currentBoardAfterRelease.size)
            currentBoardAfterRelease.add(insertIdx, revived)
            s = if (isEnemy) s.copy(enemyBoard = currentBoardAfterRelease) else s.copy(playerBoard = currentBoardAfterRelease)
            logs.add("${target.template.name} hồi sinh nhờ Bất Tử (1 Máu, Choáng)!")
            return cleanCombatUnitReferences(s, unitId)
        }

        // Anivia Last Breath: Transform into Eggnivia (0|2), storing diedAtLevel (1 or 2)
        if (!isEnemy && target.template.id == "card_anivia" && !target.isSilenced) {
            s = s.copy(roundHallowedBuffs = s.roundHallowedBuffs - unitId)
            if (target.equippedGear != null) {
                s = returnEquipmentToHand(s, target.equippedGear, toPlayer = true, logs = logs)
            }
            val eggTmpl = CardCatalog.getCard("card_eggnivia")
            val diedLvl = if (target.isLeveledUp) 2 else 1
            val egg = UnitOnBoard(
                instanceId = target.instanceId,
                template = eggTmpl,
                ownerIsPlayer = true,
                power = eggTmpl.basePower,
                health = eggTmpl.baseHealth,
                maxHealth = eggTmpl.baseHealth,
                keywords = eggTmpl.keywords,
                attachedItems = target.attachedItems,
                equippedGear = null,
                equippedGearName = null,
                diedAtLevel = diedLvl
            )
            logs.add("Trăng Trối Anivia (Lv$diedLvl): Biến đổi thành Trứng Anivia (0|2)!")
            s = s.copy(playerBoard = s.playerBoard.map { if (it.instanceId == unitId) egg else it })
            if (lastBreathRally) {
                s = s.copy(playerHasAttackToken = true)
                logs.add("Trăn Trối Tập Hợp: Nhận Biểu Tượng Tấn Công!")
            }
            s = releaseCapturedUnits(s, target, logs)
            s = syncChampionSpellsInHand(s)
            return cleanCombatUnitReferences(s, unitId)
        }

        val updated = board.filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            s.copy(
                enemyBoard = updated,
                enemyGraveyard = s.enemyGraveyard + target.template,
                roundHallowedBuffs = s.roundHallowedBuffs - unitId
            )
        } else {
            s.copy(
                playerBoard = updated,
                playerGraveyard = s.playerGraveyard + target.template,
                roundHallowedBuffs = s.roundHallowedBuffs - unitId
            )
        }
        if (target.equippedGear != null) {
            s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} đã bị hạ gục.")

        // Pipeline 4.2: Linh Hồn Lạc Lối Last Breath -> Tạo 1 Song Kiếm Ác Quỷ lên tay
        if (!target.isSilenced && target.template.id == "card_lost_soul") {
            val revenantTmpl = CardCatalog.getCard("card_twinblade_revenant")
            if (!isEnemy && s.playerHand.size < 10) {
                s = s.copy(playerHand = s.playerHand + createHandInstance(revenantTmpl, isCreated = true))
                logs.add("Trăn Trối Linh Hồn Lạc Lối: Tạo Song Kiếm Ác Quỷ lên tay!")
            } else if (isEnemy && s.enemyHand.size < 10) {
                s = s.copy(enemyHand = s.enemyHand + revenantTmpl)
            }
        }

        if (!target.isSilenced && target.keywords.contains(Keyword.HALLOWED)) {
            s = if (!isEnemy) s.copy(playerHallowedStacks = s.playerHallowedStacks + 1)
            else s.copy(enemyHallowedStacks = s.enemyHallowedStacks + 1)
        }
        if (lastBreathRally) {
            s = if (!isEnemy) s.copy(playerHasAttackToken = true) else s.copy(enemyHasAttackToken = true)
            logs.add("Trăn Trối Tập Hợp: Nhận Biểu Tượng Tấn Công!")
        }

        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, unitId)
    }

    fun recallUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val updated = (if (isEnemy) s.enemyBoard else s.playerBoard).filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            val nextHand = if (s.enemyHand.size < 10) s.enemyHand + target.template else s.enemyHand
            s.copy(
                enemyBoard = updated,
                enemyHand = nextHand,
                roundHallowedBuffs = s.roundHallowedBuffs - unitId
            )
        } else {
            val recalledCard = createHandInstance(target.template, isFleeting = false, isCreated = false)
            val nextHand = if (s.playerHand.size < 10) s.playerHand + recalledCard else s.playerHand
            s.copy(
                playerBoard = updated,
                playerHand = nextHand,
                roundHallowedBuffs = s.roundHallowedBuffs - unitId
            )
        }
        if (target.equippedGear != null) {
            s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} bị Triệu Hồi về tay.")
        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, unitId)
    }

    fun obliterateUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val updated = (if (isEnemy) s.enemyBoard else s.playerBoard).filterNot { it.instanceId == unitId }
        s = if (isEnemy) {
            s.copy(enemyBoard = updated, roundHallowedBuffs = s.roundHallowedBuffs - unitId)
        } else {
            s.copy(playerBoard = updated, roundHallowedBuffs = s.roundHallowedBuffs - unitId)
        }
        if (target.equippedGear != null) {
            s = returnEquipmentToHand(s, target.equippedGear, toPlayer = !isEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} bị Hủy Diệt!")
        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, unitId)
    }

    fun captureUnit(
        state: CombatState,
        captorId: String,
        captorIsPlayer: Boolean,
        targetId: String,
        targetIsEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val captorBoard = if (captorIsPlayer) s.playerBoard else s.enemyBoard
        val captor = captorBoard.find { it.instanceId == captorId } ?: return s
        val targetBoard = if (targetIsEnemy) s.enemyBoard else s.playerBoard
        val target = targetBoard.find { it.instanceId == targetId } ?: return s
        if (captorId == targetId) return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = targetBoard.map {
                if (it.instanceId == targetId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (targetIsEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        s = s.copy(roundHallowedBuffs = s.roundHallowedBuffs - targetId)

        val (unequippedTarget, gear) = detachEquipmentFromUnit(target)
        val storedCopy = unequippedTarget.copy(
            isStunned = false,
            isFrostbitten = false,
            frostbiteReduction = null,
            hasBarrier = unequippedTarget.keywords.contains(Keyword.BARRIER),
            equippedGear = null,
            equippedGearName = null,
            capturedUnits = emptyList()
        )
        val updatedCaptorBoard = (if (captorIsPlayer) s.playerBoard else s.enemyBoard).map {
            if (it.instanceId == captorId) it.copy(capturedUnits = it.capturedUnits + storedCopy) else it
        }
        s = if (captorIsPlayer) s.copy(playerBoard = updatedCaptorBoard) else s.copy(enemyBoard = updatedCaptorBoard)

        val updatedTargetBoard = (if (targetIsEnemy) s.enemyBoard else s.playerBoard).filterNot { it.instanceId == targetId }
        s = if (targetIsEnemy) s.copy(enemyBoard = updatedTargetBoard) else s.copy(playerBoard = updatedTargetBoard)
        if (gear != null) {
            s = returnEquipmentToHand(s, gear, toPlayer = !targetIsEnemy, logs = logs)
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${captor.template.name} đã Giam Cầm ${target.template.name}!")
        s = syncChampionSpellsInHand(s)
        return cleanCombatUnitReferences(s, targetId)
    }

    fun silenceUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải Câm Lặng!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val basePow = target.template.basePower
        val baseMaxHp = target.template.baseHealth
        val keptHp = maxOf(1, minOf(target.health, baseMaxHp))
        val silenced = target.copy(
            power = basePow,
            health = keptHp,
            maxHealth = baseMaxHp,
            keywords = setOf(Keyword.SILENCE),
            impactStacks = 0,
            spiritStacks = 0,
            gloomStacks = 0,
            hasBarrier = false,
            hasSpellShield = false,
            isStunned = false,
            isFrostbitten = false,
            frostbiteReduction = null,
            isSilenced = true,
            isShackled = false,
            hasDeathless = false,
            hasEnlightenedBonusApplied = false,
            hasDeepBonusApplied = false,
            hasEvolveBonusApplied = false,
            supportPowerBonus = 0,
            supportHealthBonus = 0,
            supportKeyword = null,
            equippedGear = null,
            equippedGearName = null,
            capturedUnits = emptyList()
        )
        val updatedBoard = board.map { if (it.instanceId == unitId) silenced else it }
        s = if (isEnemy) {
            s.copy(
                enemyBoard = updatedBoard,
                roundHallowedBuffs = s.roundHallowedBuffs - unitId
            )
        } else {
            s.copy(
                playerBoard = updatedBoard,
                roundHallowedBuffs = s.roundHallowedBuffs - unitId,
                roundSupportPowerBuffs = s.roundSupportPowerBuffs - unitId,
                roundSupportHealthBuffs = s.roundSupportHealthBuffs - unitId,
                roundSupportKeywords = s.roundSupportKeywords - unitId
            )
        }
        s = releaseCapturedUnits(s, target, logs)
        logs.add("${target.template.name} bị Câm Lặng!")
        return s
    }

    fun applySpirit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        stacks: Int = 1,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        if (stacks <= 0) return state
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state
        val updated = board.map { u ->
            if (u.instanceId == unitId) {
                u.copy(
                    power = u.power + stacks,
                    health = u.health + stacks,
                    maxHealth = u.maxHealth + stacks,
                    spiritStacks = u.spiritStacks + stacks,
                    keywords = u.keywords + Keyword.SPIRIT
                )
            } else u
        }
        logs.add("${target.template.name} nhận Tinh Linh +$stacks|+$stacks.")
        val updatedState = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        return if (!isEnemy) refreshEvolveAndSeenKeywords(updatedState, logs) else updatedState
    }

    fun applyGloom(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        stacks: Int = 1,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        if (stacks <= 0) return state
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải U Ám!")
            return if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        }

        val newHp = target.health - stacks
        val newMaxHp = maxOf(0, target.maxHealth - stacks)
        val updated = board.map { u ->
            if (u.instanceId == unitId) {
                u.copy(
                    health = newHp,
                    maxHealth = newMaxHp,
                    gloomStacks = u.gloomStacks + stacks,
                    keywords = u.keywords + Keyword.GLOOM
                )
            } else u
        }
        var s = if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        logs.add("${target.template.name} chịu U Ám -$stacks|-$stacks.")
        if (newHp <= 0) {
            s = removeUnit(s, unitId, isEnemy = isEnemy, logs = logs)
        }
        return s
    }

    internal fun frostbiteUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = true,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        val board = if (isEnemy) state.enemyBoard else state.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return state

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải Đóng Băng!")
            return if (isEnemy) state.copy(enemyBoard = updated) else state.copy(playerBoard = updated)
        }

        val wasFrostbitten = target.isFrostbitten
        val dropAmount = target.effectivePower
        val nextReduction = (target.frostbiteReduction ?: 0) + dropAmount
        val updated = board.map {
            if (it.instanceId == unitId) {
                it.copy(isFrostbitten = true, frostbiteReduction = nextReduction)
            } else it
        }
        logs.add("Đã Đóng Băng ${target.template.name}.")
        return if (isEnemy) {
            state.copy(
                enemyBoard = updated,
                enemiesFrostbittenCount = state.enemiesFrostbittenCount + (if (!wasFrostbitten) 1 else 0)
            )
        } else {
            state.copy(playerBoard = updated)
        }
    }

    internal fun stunUnit(
        state: CombatState,
        unitId: String,
        isEnemy: Boolean,
        isSpell: Boolean = false,
        fromAllyEffect: Boolean = false,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val board = if (isEnemy) s.enemyBoard else s.playerBoard
        val target = board.find { it.instanceId == unitId } ?: return s

        if (isSpell && !fromAllyEffect && target.hasSpellShield) {
            val updated = board.map {
                if (it.instanceId == unitId) it.copy(hasSpellShield = false, keywords = it.keywords - Keyword.SPELLSHIELD) else it
            }
            logs.add("Khiên Phép của ${target.template.name} đã hóa giải bài phép!")
            return if (isEnemy) s.copy(enemyBoard = updated) else s.copy(playerBoard = updated)
        }

        val updatedBoard = board.map {
            if (it.instanceId == unitId) it.copy(isStunned = true, keywords = it.keywords + Keyword.STUN) else it
        }
        s = if (isEnemy) {
            s.copy(
                enemyBoard = updatedBoard,
                enemiesStunnedCount = s.enemiesStunnedCount + 1
            )
        } else {
            s.copy(playerBoard = updatedBoard)
        }
        s = cleanCombatUnitReferences(s, unitId)
        logs.add("Đã làm Choáng ${target.template.name}.")

        if (isEnemy) {
            val yasuos = s.playerBoard.filter { it.template.id == "card_yasuo" && !it.isSilenced }
            yasuos.forEach { yasuo ->
                val baseDmg = if (yasuo.isLeveledUp) yasuo.effectivePower else 2
                val dmg = calculateFinalDamage(baseDmg, yasuo.template, s, sourceIsPlayer = true)
                s = damageUnit(s, unitId, isEnemy = true, damage = dmg, logs = logs)
                logs.add("Yasuo chém ${target.template.name} đang Choáng gây $dmg sát thương!")
            }
            if (s.championId == "champ_yasuo" && s.starLevel >= 6) {
                val star6Dmg = calculateFinalDamage(5, null, s, sourceIsPlayer = true)
                s = damageUnit(s, unitId, isEnemy = true, damage = star6Dmg, logs = logs)
            }
            s = s.copy(playerBoard = s.playerBoard.map {
                if (it.template.id == "card_fae_blader" && !it.isSilenced) it.copy(power = it.power + 2) else it
            })
        }
        return s
    }

    private fun stunEnemyUnit(state: CombatState, unitId: String, logs: MutableList<String>): CombatState {
        return stunUnit(state, unitId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
    }

    private fun dealDamageToEnemyNexus(state: CombatState, dmg: Int, logs: MutableList<String>): CombatState {
        if (dmg <= 0) return state
        var s = state.copy(
            enemyNexusHp = maxOf(0, state.enemyNexusHp - dmg),
            playerPlunderActive = true
        )
        if (s.activePowers.any { it.id == "pow_raiding_party" } && s.playerHand.isNotEmpty()) {
            val firstUnitIdx = s.playerHand.indexOfFirst {
                it.template.type == CardType.UNIT || it.template.type == CardType.CHAMPION
            }
            if (firstUnitIdx >= 0) {
                val updatedHand = s.playerHand.toMutableList()
                val card = updatedHand[firstUnitIdx]
                updatedHand[firstUnitIdx] = card.copy(bonusPower = card.bonusPower + 1, bonusHealth = card.bonusHealth + 1)
                s = s.copy(playerHand = updatedHand)
            }
        }
        return checkGameOver(s)
    }

    private fun dealDamageToPlayerNexus(state: CombatState, dmg: Int, logs: MutableList<String>): CombatState {
        if (dmg <= 0) return state
        val s = state.copy(playerNexusHp = maxOf(0, state.playerNexusHp - dmg))
        return checkGameOver(s)
    }

    // --- PRIORITY PASS & ROUND TRANSITION ---

    fun passPriority(state: CombatState): CombatState {
        if (state.phase == CombatPhase.GAME_OVER || state.priorityHolder != PriorityHolder.PLAYER) return state
        val logs = state.combatLog.toMutableList()

        // Rule 9.2: Passing during DECLARE_BLOCK with 0+ blockers commits blocks
        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
            return commitPlayerBlock(state)
        }

        // Rule 9.2: Passing while Spell Stack is non-empty resolves stack LIFO and transfers priority
        if (state.spellStack.isNotEmpty()) {
            logs.add("Bạn đã bỏ qua — đang thực thi hàng chờ phép.")
            return resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
        }

        // Rule 9.3: Passing in MAIN_ACTION_PHASE increments consecutivePasses; 2 passes ends round
        val newPasses = state.consecutivePasses + 1
        if (newPasses >= 2) {
            logs.add("Cả hai bên đều bỏ qua. Kết thúc Vòng ${state.round}.")
            return advanceToNextRound(state, logs)
        }

        logs.add("Bạn đã chuyển lượt.")
        return state.copy(
            consecutivePasses = newPasses,
            priorityHolder = PriorityHolder.ENEMY,
            combatLog = logs.takeLast(16)
        )
    }

    internal fun drawEnemyCard(state: CombatState): CombatState {
        if (state.enemyDrawPool.isEmpty()) {
            val logs = state.combatLog.toMutableList()
            logs.add("Bộ bài địch đã hết! Kiệt sức gây $EMPTY_DECK_FATIGUE_DAMAGE sát thương lên Nhà Chính địch.")
            val afterFatigue = dealDamageToEnemyNexus(state, EMPTY_DECK_FATIGUE_DAMAGE, logs)
            return afterFatigue.copy(combatLog = logs.takeLast(16))
        }
        if (state.enemyHand.size >= 10) return state
        return state.copy(enemyHand = state.enemyHand + state.enemyDrawPool.random())
    }

    internal fun summonEnemyUnitFromPool(state: CombatState, chosen: CardTemplate, spendMana: Int, logs: MutableList<String>): CombatState {
        var bonusPow = if (chosen.keywords.contains(Keyword.LURK)) state.enemyLurkBonus else 0
        var bonusHp = 0
        val extraKw = mutableSetOf<Keyword>()
        if (state.adventure.starRating >= 3.0f) {
            bonusPow += 1; bonusHp += 1
        }
        when (canonicalEnemyPowerKey(state.encounterNode.enemyPowerTitle)) {
            "VOLATILE_MUNITIONS" -> extraKw.add(Keyword.IMPACT)
            "UNSEEN_PATH" -> if (chosen.keywords.contains(Keyword.ELUSIVE)) bonusPow += 1
            "GLORIOUS_EVOLUTION" -> {
                bonusPow += 1; bonusHp += 1
                val availableKw = Keyword.sharablePool.filterNot { it in chosen.keywords }
                extraKw.add((availableKw.ifEmpty { Keyword.sharablePool }).random())
            }
            "BLAZING_DAWN" -> {
                bonusPow += 1; bonusHp += 1
            }
        }

        val enemyUnit = UnitOnBoard(
            instanceId = "eu_${nextId()}",
            template = chosen,
            ownerIsPlayer = false,
            power = chosen.basePower + bonusPow,
            health = chosen.baseHealth + bonusHp,
            maxHealth = chosen.baseHealth + bonusHp,
            keywords = chosen.keywords + extraKw,
            impactStacks = chosen.impactStacks + (if (extraKw.contains(Keyword.IMPACT)) 1 else 0),
            hasBarrier = chosen.keywords.contains(Keyword.BARRIER),
            hasSpellShield = chosen.keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = chosen.keywords.contains(Keyword.DEATHLESS)
        )
        logs.add("Địch triệu hồi ${chosen.name} (${enemyUnit.power}|${enemyUnit.health}).")
        return state.copy(
            enemyMana = maxOf(0, state.enemyMana - spendMana),
            enemyHand = removeFirstTemplate(state.enemyHand, chosen.id),
            enemyBoard = state.enemyBoard + enemyUnit
        )
    }

    internal fun removeFirstTemplate(hand: List<CardTemplate>, cardId: String): List<CardTemplate> {
        val idx = hand.indexOfFirst { it.id == cardId }
        if (idx < 0) return hand
        return hand.toMutableList().apply { removeAt(idx) }
    }

    internal fun advanceToNextRound(state: CombatState, logs: MutableList<String>): CombatState {
        var s = state

        // Ephemeral units die at Round End via removeUnit (triggering Last Breath, Hallowed, Deathless, Captured release)
        val ephemeralPlayerIds = s.playerBoard.filter { it.keywords.contains(Keyword.EPHEMERAL) }.map { it.instanceId }
        ephemeralPlayerIds.forEach { id ->
            s = removeUnit(s, id, isEnemy = false, logs = logs)
        }
        val ephemeralEnemyIds = s.enemyBoard.filter { it.keywords.contains(Keyword.EPHEMERAL) }.map { it.instanceId }
        ephemeralEnemyIds.forEach { id ->
            s = removeUnit(s, id, isEnemy = true, logs = logs)
        }

        val cleanPlayerBoard = s.playerBoard.map { u ->
            val healedHp = if (u.keywords.contains(Keyword.REGENERATION)) u.maxHealth else u.health
            val hallowedBuff = s.roundHallowedBuffs[u.instanceId] ?: 0
            val supPow = s.roundSupportPowerBuffs[u.instanceId] ?: 0
            val supHp = s.roundSupportHealthBuffs[u.instanceId] ?: 0
            val supKws = s.roundSupportKeywords[u.instanceId] ?: emptySet()
            val newMaxHp = maxOf(1, u.maxHealth - supHp)
            val newHp = minOf(newMaxHp, maxOf(1, healedHp - supHp))
            u.copy(
                power = maxOf(0, u.power - hallowedBuff - supPow),
                health = newHp,
                maxHealth = newMaxHp,
                hasBarrier = false,
                isStunned = false,
                isFrostbitten = false,
                frostbiteReduction = null,
                fatedUsedThisRound = false,
                keywords = (u.keywords - Keyword.STUN) - supKws
            )
        }
        val cleanEnemyBoard = s.enemyBoard.map { u ->
            val healedHp = if (u.keywords.contains(Keyword.REGENERATION)) u.maxHealth else u.health
            val hallowedBuff = s.roundHallowedBuffs[u.instanceId] ?: 0
            val supPow = s.roundSupportPowerBuffs[u.instanceId] ?: 0
            val supHp = s.roundSupportHealthBuffs[u.instanceId] ?: 0
            val supKws = s.roundSupportKeywords[u.instanceId] ?: emptySet()
            val newMaxHp = maxOf(1, u.maxHealth - supHp)
            val newHp = minOf(newMaxHp, maxOf(1, healedHp - supHp))
            u.copy(
                power = maxOf(0, u.power - hallowedBuff - supPow),
                health = newHp,
                maxHealth = newMaxHp,
                hasBarrier = false,
                isStunned = false,
                isFrostbitten = false,
                frostbiteReduction = null,
                fatedUsedThisRound = false,
                keywords = (u.keywords - Keyword.STUN) - supKws
            )
        }

        val cleanHand = s.playerHand.filterNot { it.isFleeting }

        val nextRound = s.round + 1
        val newPlayerMax = (s.playerMaxMana + 1).coerceIn(1, 10)
        val newEnemyMax = (s.enemyMaxMana + 1).coerceIn(1, 10)
        val playerSpellBank = minOf(3, maxOf(0, s.playerSpellMana) + maxOf(0, s.playerMana))
        val enemySpellBank = minOf(3, maxOf(0, s.enemySpellMana) + maxOf(0, s.enemyMana))

        val playerGetsToken = nextRound % 2 == 1
        val roundInitiator = if (playerGetsToken) PriorityHolder.PLAYER else PriorityHolder.ENEMY
        logs.add("--- Vòng $nextRound (Lượt Tấn Công: ${if (playerGetsToken) "Bạn" else "Địch"}) ---")

        s = s.copy(
            round = nextRound,
            playerMana = newPlayerMax,
            playerMaxMana = newPlayerMax,
            playerSpellMana = playerSpellBank,
            enemyMana = newEnemyMax,
            enemyMaxMana = newEnemyMax,
            enemySpellMana = enemySpellBank,
            playerHasAttackToken = playerGetsToken,
            enemyHasAttackToken = !playerGetsToken,
            scoutUsedThisRound = false,
            pendingScoutRally = false,
            playerLurkTriggeredThisRound = false,
            enemyLurkTriggeredThisRound = false,
            roundHallowedBuffs = emptyMap(),
            roundSupportPowerBuffs = emptyMap(),
            roundSupportHealthBuffs = emptyMap(),
            roundSupportKeywords = emptyMap(),
            playerInitiatedCombat = playerGetsToken,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            playerBoard = cleanPlayerBoard,
            enemyBoard = cleanEnemyBoard,
            playerHand = cleanHand,
            playerCardsPlayedThisRound = 0,
            playerSpellsPlayedThisRound = 0,
            playerPlunderActive = false,
            attackingUnitIds = emptyList(),
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            phase = CombatPhase.MAIN_ACTION_PHASE,
            priorityHolder = roundInitiator,
            consecutivePasses = 0,
            consecutiveAiActionsInRound = 0,
            lastStackCaster = null,
            combatLog = logs.takeLast(16)
        )

        s = drawPlayerCard(s)
        s = drawEnemyCard(s)
        s = applyRoundStartHooks(s, isGameStart = false)
        return s
    }

    internal fun checkChampionLevelUps(
        state: CombatState,
        logs: MutableList<String>,
        isRoundStart: Boolean = false
    ): CombatState {
        var s = state
        var rallyTriggered = false
        val newlyLeveledIds = mutableListOf<String>()
        val updatedBoard = s.playerBoard.map { u ->
            if (u.template.isChampion && !u.isLeveledUp && !u.isSilenced) {
                val shouldLevel = when (u.template.id) {
                    "card_jinx" -> s.playerHand.isEmpty() || s.playerSpellsPlayedThisRound >= 2
                    "card_lux" -> s.playerSpellsManaSpent >= 6
                    "card_yasuo" -> s.enemiesStunnedCount >= 3 || s.round >= 3
                    "card_ashe" -> s.enemiesFrostbittenCount >= 3 || s.round >= 3
                    "card_garen" -> u.levelProgress >= 2
                    "card_anivia" -> isRoundStart && s.isEnlightened
                    else -> s.round >= 3
                }
                if (shouldLevel) {
                    logs.add("★ ${u.template.name} ĐÃ THĂNG CẤP! (+${u.template.levelUpPowerBonus}|+${u.template.levelUpHealthBonus})")
                    newlyLeveledIds.add(u.template.id)
                    if (s.equippedRelics.any { it.id == "relic_crownguard" }) rallyTriggered = true
                    u.copy(
                        isLeveledUp = true,
                        power = u.power + u.template.levelUpPowerBonus,
                        health = u.health + u.template.levelUpHealthBonus,
                        maxHealth = u.maxHealth + u.template.levelUpHealthBonus
                    )
                } else u
            } else u
        }
        s = s.copy(playerBoard = updatedBoard)
        if (rallyTriggered) {
            s = s.copy(playerHasAttackToken = true)
            logs.add("Di Sản Hoàng Tộc kích hoạt Tập Hợp!")
        }
        for (champId in newlyLeveledIds) {
            when (champId) {
                "card_jinx" -> {
                    s = dealDamageToEnemyNexus(s, 4, logs)
                    if (s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_super_mega_rocket"),
                                isFleeting = true
                            )
                        )
                    }
                    logs.add("Jinx Thăng Cấp gây 4 sát thương Nhà Chính & tạo Tên Lửa Đạn Đạo Siêu Khủng Khiếp!")
                }
                "card_lux" -> {
                    if (s.playerHand.size < 10) {
                        s = s.copy(
                            playerHand = s.playerHand + createHandInstance(
                                CardCatalog.getCard("card_final_spark"),
                                isFleeting = true
                            )
                        )
                    }
                    logs.add("Lux Thăng Cấp tạo Cầu Vồng Tối Thượng 0 tiêu hao!")
                }
            }
        }
        return s
    }

    internal fun onPlayerSkillOrElementalPlayed(
        state: CombatState,
        logs: MutableList<String>,
        sourceName: String
    ): CombatState {
        var s = state.copy(playerElementalAndSkillsPlayed = state.playerElementalAndSkillsPlayed + 1)

        // Anivia 3★ Ân Huệ Mùa Đông II: Nhận 1 ngọc năng lượng rỗng
        if (s.championId == "champ_anivia" && s.starLevel >= 3) {
            s = gainPlayerManaGem(s, empty = true, logs, "Ân Huệ Mùa Đông II")
        }
        return s
    }

    internal fun gainPlayerManaGem(
        state: CombatState,
        empty: Boolean,
        logs: MutableList<String>,
        sourceLabel: String = "Tăng Ngọc Năng Lượng"
    ): CombatState {
        var s = state
        if (s.playerMaxMana >= 10) {
            // Anivia 2★ Băng Giá Tràn Trề: Nếu được Khai Sáng, ban +1|+1 cho đồng minh
            if (s.isEnlightened && s.championId == "champ_anivia" && s.starLevel >= 2 && s.playerBoard.isNotEmpty()) {
                s = s.copy(
                    playerBoard = s.playerBoard.map {
                        it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1)
                    }
                )
                logs.add("Băng Giá Tràn Trề (Khai Sáng): Toàn bộ đồng minh nhận +1|+1!")
            }
            return s
        }
        val newMax = minOf(10, s.playerMaxMana + 1)
        val newCurrent = if (empty) minOf(newMax, s.playerMana) else minOf(newMax, s.playerMana + 1)
        s = s.copy(playerMaxMana = newMax, playerMana = newCurrent)
        logs.add("$sourceLabel: Nhận +1 ngọc năng lượng${if (empty) " rỗng" else ""} (Tối đa: $newMax).")
        return s
    }

    internal fun applyDeepBoardBuffs(state: CombatState, logs: MutableList<String>): CombatState {
        if (state.playerDrawPile.size > 15) return state
        var changed = false
        val updated = state.playerBoard.map { u ->
            if (!u.isSilenced && u.keywords.contains(Keyword.DEEP) && !u.hasDeepBonusApplied) {
                changed = true
                logs.add("${u.template.name} đạt Thẩm Sâu (+3|+3)!")
                u.copy(
                    power = u.power + 3,
                    health = u.health + 3,
                    maxHealth = u.maxHealth + 3,
                    hasDeepBonusApplied = true
                )
            } else u
        }
        return if (changed) state.copy(playerBoard = updated) else state
    }

    internal fun refreshEvolveAndSeenKeywords(state: CombatState, logs: MutableList<String>): CombatState {
        val boardKeywords = state.playerBoard
            .filterNot { it.isSilenced }
            .flatMap { unit ->
                buildList {
                    unit.keywords.filter { it.isSharablePositive }.forEach { add(it) }
                    if (unit.hasBarrier) add(Keyword.BARRIER)
                    if (unit.hasSpellShield) add(Keyword.SPELLSHIELD)
                    if (unit.hasDeathless) add(Keyword.DEATHLESS)
                }
            }
        val allSeen = state.playerUniqueKeywordsSeen + boardKeywords
        var changed = allSeen != state.playerUniqueKeywordsSeen
        val updatedBoard = if (allSeen.size >= 6) {
            state.playerBoard.map { u ->
                if (!u.isSilenced && u.keywords.contains(Keyword.EVOLVE) && !u.hasEvolveBonusApplied) {
                    changed = true
                    logs.add("${u.template.name} kích hoạt Tiến Hóa (+2|+2)!")
                    u.copy(
                        power = u.power + 2,
                        health = u.health + 2,
                        maxHealth = u.maxHealth + 2,
                        hasEvolveBonusApplied = true
                    )
                } else u
            }
        } else {
            state.playerBoard
        }
        return if (changed) state.copy(playerBoard = updatedBoard, playerUniqueKeywordsSeen = allSeen) else state
    }

    internal fun triggerFatedOnTarget(
        state: CombatState,
        targetUnitId: String,
        sourceIsPlayer: Boolean,
        logs: MutableList<String>
    ): CombatState {
        val board = if (sourceIsPlayer) state.playerBoard else state.enemyBoard
        var triggered = false
        val updated = board.map { u ->
            if (u.instanceId == targetUnitId && !u.isSilenced && u.keywords.contains(Keyword.FATED) && !u.fatedUsedThisRound) {
                triggered = true
                logs.add("${u.template.name} kích hoạt Định Mệnh (+1|+1)!")
                u.copy(
                    power = u.power + 1,
                    health = u.health + 1,
                    maxHealth = u.maxHealth + 1,
                    fatedUsedThisRound = true
                )
            } else u
        }
        if (!triggered) return state
        return if (sourceIsPlayer) state.copy(playerBoard = updated) else state.copy(enemyBoard = updated)
    }

    private fun triggerPlayerLurkIfEligible(
        state: CombatState,
        attackers: List<UnitOnBoard>,
        logs: MutableList<String>
    ): CombatState {
        if (state.playerLurkTriggeredThisRound) return state
        if (attackers.none { !it.isSilenced && it.keywords.contains(Keyword.LURK) }) return state
        val topCard = state.playerDrawPile.firstOrNull() ?: return state
        val topIsLurker = topCard.template.keywords.contains(Keyword.LURK) || topCard.extraKeywords.contains(Keyword.LURK)
        if (!topIsLurker) return state

        logs.add("Ẩn Nấp kích hoạt! Toàn bộ quân Ẩn Nấp nhận +1|+0.")
        return state.copy(
            playerLurkTriggeredThisRound = true,
            playerLurkBonus = state.playerLurkBonus + 1,
            playerBoard = state.playerBoard.map { u ->
                if (!u.isSilenced && u.keywords.contains(Keyword.LURK)) u.copy(power = u.power + 1) else u
            },
            playerHand = state.playerHand.map { hc ->
                if (hc.template.keywords.contains(Keyword.LURK) || hc.extraKeywords.contains(Keyword.LURK)) {
                    hc.copy(bonusPower = hc.bonusPower + 1)
                } else hc
            },
            playerDrawPile = state.playerDrawPile.map { hc ->
                if (hc.template.keywords.contains(Keyword.LURK) || hc.extraKeywords.contains(Keyword.LURK)) {
                    hc.copy(bonusPower = hc.bonusPower + 1)
                } else hc
            }
        )
    }

    internal fun applyAttackSupportBuffs(
        state: CombatState,
        orderedAttackerIds: List<String>,
        playerIsAttacking: Boolean,
        logs: MutableList<String>
    ): CombatState {
        if (orderedAttackerIds.size < 2) return state
        var s = state
        val roundPow = s.roundSupportPowerBuffs.toMutableMap()
        val roundHp = s.roundSupportHealthBuffs.toMutableMap()
        val roundKw = s.roundSupportKeywords.toMutableMap()

        for (i in 0 until orderedAttackerIds.size - 1) {
            val board = if (playerIsAttacking) s.playerBoard else s.enemyBoard
            val supporter = board.find { it.instanceId == orderedAttackerIds[i] } ?: continue
            if (supporter.isSilenced || !supporter.keywords.contains(Keyword.SUPPORT)) continue
            val rightId = orderedAttackerIds[i + 1]
            val supported = board.find { it.instanceId == rightId } ?: continue

            val grantPow = supporter.supportPowerBonus
            val grantHp = supporter.supportHealthBonus
            val grantKw = supporter.supportKeyword
            if (grantPow == 0 && grantHp == 0 && grantKw == null) continue

            val updatedBoard = board.map { u ->
                if (u.instanceId == rightId) {
                    val hadKwAlready = grantKw != null && u.keywords.contains(grantKw)
                    if (grantKw != null && !hadKwAlready) {
                        roundKw[rightId] = (roundKw[rightId] ?: emptySet()) + grantKw
                    }
                    if (grantPow != 0) roundPow[rightId] = (roundPow[rightId] ?: 0) + grantPow
                    if (grantHp != 0) roundHp[rightId] = (roundHp[rightId] ?: 0) + grantHp
                    u.copy(
                        power = u.power + grantPow,
                        health = u.health + grantHp,
                        maxHealth = u.maxHealth + grantHp,
                        keywords = if (grantKw != null) u.keywords + grantKw else u.keywords,
                        hasBarrier = u.hasBarrier || grantKw == Keyword.BARRIER,
                        hasSpellShield = u.hasSpellShield || grantKw == Keyword.SPELLSHIELD,
                        hasDeathless = u.hasDeathless || grantKw == Keyword.DEATHLESS
                    )
                } else u
            }
            s = if (playerIsAttacking) s.copy(playerBoard = updatedBoard) else s.copy(enemyBoard = updatedBoard)
            logs.add("${supporter.template.name} Hỗ Trợ ${supported.template.name} (+$grantPow|+$grantHp${grantKw?.let { ", ${it.displayName}" } ?: ""})!")
        }
        s = s.copy(
            roundSupportPowerBuffs = roundPow,
            roundSupportHealthBuffs = roundHp,
            roundSupportKeywords = roundKw
        )
        if (playerIsAttacking) {
            s = refreshEvolveAndSeenKeywords(s, logs)
        }
        return s
    }

    private fun detachEquipmentFromUnit(unit: UnitOnBoard): Pair<UnitOnBoard, CardTemplate?> {
        val gear = unit.equippedGear ?: return unit to null
        val retainKeywords = buildSet {
            addAll(unit.template.keywords)
            unit.attachedItems.mapNotNull { it.grantedKeyword }.forEach { add(it) }
            unit.supportKeyword?.let { add(it) }
            if (unit.isSilenced) add(Keyword.SILENCE)
        }
        val strippedKeywords = (unit.keywords - gear.keywords) + (unit.keywords.intersect(retainKeywords))
        val cleanMaxHp = maxOf(1, unit.maxHealth - gear.baseHealth)
        val detached = unit.copy(
            power = maxOf(0, unit.power - gear.basePower),
            maxHealth = cleanMaxHp,
            health = minOf(cleanMaxHp, maxOf(1, unit.health)),
            keywords = strippedKeywords,
            equippedGear = null,
            equippedGearName = null
        )
        return detached to gear
    }

    private fun returnEquipmentToHand(
        state: CombatState,
        gear: CardTemplate?,
        toPlayer: Boolean,
        logs: MutableList<String>
    ): CombatState {
        if (gear == null) return state
        return if (toPlayer) {
            if (state.playerHand.size >= 10) state
            else {
                logs.add("Trang bị ${gear.name} trở về tay.")
                state.copy(playerHand = state.playerHand + createHandInstance(gear, isFleeting = false, isCreated = false))
            }
        } else {
            if (state.enemyHand.size >= 10) state
            else {
                logs.add("Trang bị ${gear.name} của địch trở về tay.")
                state.copy(enemyHand = state.enemyHand + gear)
            }
        }
    }

    private fun onUnitStrike(
        state: CombatState,
        strikerId: String,
        isPlayerStriker: Boolean,
        hitNexus: Boolean,
        logs: MutableList<String>
    ): CombatState {
        var s = state
        val board = if (isPlayerStriker) s.playerBoard else s.enemyBoard
        val striker = board.find { it.instanceId == strikerId }
        val updatedBoard = board.map { u ->
            if (u.instanceId == strikerId) u.copy(levelProgress = u.levelProgress + 1) else u
        }
        val isReputation = isPlayerStriker && (striker?.effectivePower ?: 0) >= 5
        s = if (isPlayerStriker) {
            s.copy(
                playerBoard = updatedBoard,
                playerTotalStrikes = s.playerTotalStrikes + 1,
                playerReputationStrikes = s.playerReputationStrikes + if (isReputation) 1 else 0,
                playerNexusStrikesCount = s.playerNexusStrikesCount + if (hitNexus) 1 else 0
            )
        } else {
            s.copy(
                enemyBoard = updatedBoard,
                enemyTotalStrikes = s.enemyTotalStrikes + 1,
                enemyNexusStrikesCount = s.enemyNexusStrikesCount + if (hitNexus) 1 else 0
            )
        }
        if (isPlayerStriker) {
            s = checkChampionLevelUps(s, logs)
        }
        return s
    }

    fun startFreeAttack(
        state: CombatState,
        attackerIds: List<String>,
        playerIsAttacking: Boolean,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        var s = state
        val sourceBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
        val validAttackers = attackerIds.mapNotNull { id -> sourceBoard.find { it.instanceId == id && it.canAttack } }
        if (validAttackers.isEmpty()) return s
        val validIds = validAttackers.map { it.instanceId }

        if (playerIsAttacking && s.playerHallowedStacks > 0) {
            val firstId = validIds.first()
            s = recordRoundHallowedBuff(s, firstId, s.playerHallowedStacks)
            s = s.copy(playerBoard = s.playerBoard.map {
                if (it.instanceId == firstId) it.copy(power = it.power + s.playerHallowedStacks) else it
            })
            logs.add("Thiêng Liêng ban cho ${validAttackers.first().template.name} +${s.playerHallowedStacks}|+0!")
        } else if (!playerIsAttacking && s.enemyHallowedStacks > 0) {
            val firstId = validIds.first()
            s = recordRoundHallowedBuff(s, firstId, s.enemyHallowedStacks)
            s = s.copy(enemyBoard = s.enemyBoard.map {
                if (it.instanceId == firstId) it.copy(power = it.power + s.enemyHallowedStacks) else it
            })
            logs.add("Thiêng Liêng ban cho ${validAttackers.first().template.name} +${s.enemyHallowedStacks}|+0!")
        }

        if (playerIsAttacking) {
            s = triggerPlayerLurkIfEligible(s, validAttackers, logs)
        }

        s = applyAttackSupportBuffs(s, validIds, playerIsAttacking = playerIsAttacking, logs = logs)

        validAttackers.forEach { atk ->
            if (playerIsAttacking && atk.template.isChampion && s.equippedRelics.any { it.id == "relic_guinsoos_rageblade" }) {
                s = s.copy(playerBoard = s.playerBoard.map {
                    if (it.instanceId == atk.instanceId) it.copy(power = it.power + 1, health = it.health + 1, maxHealth = it.maxHealth + 1) else it
                })
            }
            if (playerIsAttacking && atk.template.id == "card_ashe" && !atk.isSilenced) {
                val target = s.enemyBoard.filter { !it.isFrostbitten }.maxByOrNull { it.effectivePower }
                    ?: s.enemyBoard.maxByOrNull { it.effectivePower }
                if (target != null) {
                    s = frostbiteUnit(s, target.instanceId, isEnemy = true, isSpell = false, fromAllyEffect = false, logs = logs)
                }
            }
        }

        if (!playerIsAttacking) {
            s = applyEnemyAttackConstellationPowers(s, validIds, logs)
        }

        val attackSkills = mutableListOf<SpellStackEntry>()
        if (playerIsAttacking) {
            validAttackers.forEach { atk ->
                val liveAtk = s.playerBoard.find { it.instanceId == atk.instanceId }
                if (liveAtk != null && liveAtk.template.id == "card_anivia" && !liveAtk.isSilenced && !liveAtk.isStunned) {
                    val baseStormDmg = if (liveAtk.isLeveledUp) 2 else 1
                    val skillCard = CardCatalog.getCard("skill_glacial_storm").copy(effectValue = baseStormDmg)
                    s = onPlayerSkillOrElementalPlayed(s, logs, skillCard.name)
                    attackSkills.add(
                        SpellStackEntry(
                            id = "st_${nextId()}",
                            sourceIsPlayer = true,
                            card = skillCard,
                            targetUnitId = null,
                            damageBonus = 0
                        )
                    )
                    logs.add("Kỹ năng ${skillCard.name} của Anivia vào hàng chờ!")
                }
            }
        }

        val currentBoard = if (playerIsAttacking) s.playerBoard else s.enemyBoard
        val survivingIds = validIds.filter { id -> currentBoard.any { it.instanceId == id && !it.isStunned } }
        if (survivingIds.isEmpty()) return s

        logs.add("Tấn Công Tự Do với ${survivingIds.size} quân!")
        return s.copy(
            isFreeAttack = true,
            playerInitiatedCombat = playerIsAttacking,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = if (playerIsAttacking) PriorityHolder.ENEMY else PriorityHolder.PLAYER,
            consecutivePasses = 0,
            lastStackCaster = if (attackSkills.isNotEmpty()) PriorityHolder.PLAYER else s.lastStackCaster,
            spellStack = s.spellStack + attackSkills,
            attackingUnitIds = survivingIds,
            blockPairings = emptyMap(),
            confirmedBlockedAttackerIds = emptySet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = emptySet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            combatLog = logs.takeLast(16)
        )
    }

    fun queueSkillToStack(
        state: CombatState,
        skillCard: CardTemplate,
        sourceIsPlayer: Boolean,
        targetUnitId: String? = null,
        bonusDmg: Int = 0,
        logs: MutableList<String> = mutableListOf()
    ): CombatState {
        val entry = SpellStackEntry(
            id = "st_${nextId()}",
            sourceIsPlayer = sourceIsPlayer,
            card = skillCard,
            targetUnitId = targetUnitId,
            damageBonus = bonusDmg
        )
        var s = state
        if (sourceIsPlayer) {
            s = onPlayerSkillOrElementalPlayed(s, logs, skillCard.name)
        }
        val nextPhase = if (s.phase == CombatPhase.MAIN_ACTION_PHASE) CombatPhase.SPELL_STACK_PHASE else s.phase
        val nextPriority = if (sourceIsPlayer) PriorityHolder.ENEMY else PriorityHolder.PLAYER
        logs.add("Kỹ Năng ${skillCard.name} vào hàng chờ phản hồi.")
        return s.copy(
            spellStack = s.spellStack + entry,
            phase = nextPhase,
            lastStackCaster = if (sourceIsPlayer) PriorityHolder.PLAYER else PriorityHolder.ENEMY,
            consecutivePasses = 0,
            priorityHolder = nextPriority,
            combatLog = logs.takeLast(16)
        )
    }

    internal fun checkGameOver(state: CombatState): CombatState {
        return when {
            state.enemyNexusHp <= 0 -> state.copy(phase = CombatPhase.GAME_OVER, playerWon = true)
            state.playerNexusHp <= 0 -> state.copy(phase = CombatPhase.GAME_OVER, playerWon = false)
            else -> state
        }
    }

    internal fun generateId(): String = nextId()
}
