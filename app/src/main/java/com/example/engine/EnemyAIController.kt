package com.example.engine

import android.os.Build
import android.util.Log
import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.Keyword
import com.example.model.SpellSpeed
import com.example.model.UnitOnBoard
import kotlinx.coroutines.CancellationException

/**
 * Hệ thống Trí Tuệ Nhân Tạo Tổng Quát Đa Tình Huống (Generalized AI) theo Quy-tắc-AI.pdf (Phần 2):
 * - 2.1: Định giá thực thể bằng CombatSimulator.threatScore & playValue
 * - 2.2: Mô phỏng giao tranh bằng CombatSimulator.simulatePair (tính trước spellStack, Quick Attack, Tough/Barrier, Overwhelm)
 * - 2.3: Ma trận quyết định 4 bước IDLE_ACTION (Lethal Open Attack -> Board Dev -> Attack -> Proactive Spell),
 *        3 tầng DECLARING_BLOCK (Favorable -> Value -> Chump Block <= 4 HP),
 *        và phản hồi RESOLVING_STACK (Defensive Trick ThreatScore >= 8.0, Disruption, Pass đúng lúc)
 * - 2.4: Failsafe bắt buộc tránh vòng lặp vô hạn hoặc treo priority ở ENEMY.
 */
object EnemyAIController {

    private const val DEFENSIVE_TRICK_THREAT_THRESHOLD = 8.0
    private const val CHUMP_BLOCK_SAFETY_HP = 4
    private const val MAX_AI_ACTIONS_PER_ROUND = 24

    private fun isUnitTestEnvironment(): Boolean {
        return try {
            Class.forName("org.junit.Test")
            true
        } catch (_: Throwable) {
            Build.FINGERPRINT == null || Build.FINGERPRINT == "robolectric"
        }
    }

    fun executeTurn(state: CombatState): CombatState {
        if (state.phase == CombatPhase.GAME_OVER || state.priorityHolder != PriorityHolder.ENEMY) {
            return state
        }

        val logs = state.combatLog.toMutableList()
        return try {
            if (state.consecutiveAiActionsInRound >= MAX_AI_ACTIONS_PER_ROUND) {
                logs.add("AI đạt ngưỡng an toàn hành động — nhường lượt tránh lặp vô hạn.")
                return safeFailsafePass(state, logs)
            }

            val result = executeTurnInternal(state, logs)
            if (result == state) {
                safeFailsafePass(state, logs)
            } else if (result.round != state.round || result.priorityHolder != PriorityHolder.ENEMY) {
                result.copy(consecutiveAiActionsInRound = 0)
            } else {
                result.copy(consecutiveAiActionsInRound = state.consecutiveAiActionsInRound + 1)
            }
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            try {
                Log.e("EnemyAI", "AI decision failed", e)
            } catch (_: RuntimeException) {
                println("[EnemyAI] AI decision failed: ${e.stackTraceToString()}")
            }
            if (isUnitTestEnvironment()) {
                throw e
            }
            safeFailsafePass(state, logs)
        }
    }

    private fun executeTurnInternal(state: CombatState, logs: MutableList<String>): CombatState {
        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE && state.playerInitiatedCombat && !state.blockCommitted) {
            return handleBlockDeclaration(state, logs)
        }

        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE &&
            state.playerInitiatedCombat &&
            state.blockCommitted &&
            !state.playerCastSpellInBlock &&
            state.spellStack.any { it.card.id == "skill_glacial_storm" }
        ) {
            logs.add("Địch không có hành động — Bão Băng Giá tự động áp dụng!")
            return CombatEngine.resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
        }

        if (state.spellStack.isNotEmpty()) {
            return handleSpellStackResponse(state, logs)
        }

        return when (state.phase) {
            CombatPhase.SPELL_STACK_PHASE -> handleSpellStackResponse(state, logs)
            CombatPhase.MAIN_ACTION_PHASE -> handleMainActionPhase(state, logs)
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                if (state.playerInitiatedCombat) {
                    if (state.blockCommitted) {
                        CombatEngine.finishPlayerAttackCombat(state)
                    } else {
                        handleBlockDeclaration(state, logs)
                    }
                } else if (state.blockCommitted) {
                    val reactiveSpell = pickReactiveCombatSpell(state, playerIsAttacking = false)
                    if (reactiveSpell != null) {
                        castEnemySpell(state, reactiveSpell.first, logs, targetUnitId = reactiveSpell.second)
                    } else if (state.enemyCastBurstInBlock) {
                        logs.add("Địch chuyển quyền phản hồi sau phép Siêu Tốc.")
                        state.copy(
                            enemyCastBurstInBlock = false,
                            priorityHolder = PriorityHolder.PLAYER,
                            consecutivePasses = 0,
                            combatLog = logs.takeLast(16)
                        )
                    } else {
                        CombatEngine.finishEnemyAttackCombat(state)
                    }
                } else {
                    state.copy(priorityHolder = PriorityHolder.PLAYER)
                }
            }
            CombatPhase.COMBAT_DECLARE_ATTACK_PHASE,
            CombatPhase.COMBAT_RESOLUTION_PHASE,
            CombatPhase.GAME_OVER -> state
        }
    }

    /**
     * Failsafe bắt buộc (Mục 2.4): Bất kỳ khi nào gặp ngoại lệ, kẹt trạng thái hoặc đạt giới hạn hành động,
     * ép chuyển quyền ưu tiên / giải quyết Stack / kết thúc giao tranh an toàn.
     */
    internal fun safeFailsafePass(state: CombatState, logs: MutableList<String>): CombatState {
        val cleanState = state.copy(consecutiveAiActionsInRound = 0)
        if (cleanState.phase == CombatPhase.GAME_OVER) return cleanState
        if (cleanState.spellStack.isNotEmpty()) {
            return CombatEngine.resolveSpellStack(cleanState.copy(combatLog = logs.takeLast(16)))
        }
        return when (cleanState.phase) {
            CombatPhase.COMBAT_DECLARE_BLOCK_PHASE -> {
                if (cleanState.playerInitiatedCombat) {
                    if (!cleanState.blockCommitted) {
                        cleanState.copy(
                            blockCommitted = true,
                            priorityHolder = PriorityHolder.PLAYER,
                            consecutivePasses = 0,
                            combatLog = logs.takeLast(16)
                        )
                    } else {
                        CombatEngine.finishPlayerAttackCombat(cleanState)
                    }
                } else {
                    CombatEngine.finishEnemyAttackCombat(cleanState)
                }
            }
            else -> {
                val nextPasses = cleanState.consecutivePasses + 1
                if (nextPasses >= 2) {
                    logs.add("Địch bỏ qua. Kết thúc Vòng ${cleanState.round}.")
                    CombatEngine.advanceToNextRound(cleanState, logs)
                } else {
                    logs.add("Địch đã chuyển lượt.")
                    cleanState.copy(
                        consecutivePasses = nextPasses,
                        priorityHolder = PriorityHolder.PLAYER,
                        combatLog = logs.takeLast(16)
                    )
                }
            }
        }
    }

    // Case A: Phản hồi Chuỗi Phép (RESOLVING_STACK) - Mục 2.3.C
    private fun handleSpellStackResponse(state: CombatState, logs: MutableList<String>): CombatState {
        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val responseSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                CombatEngine.canPlayCard(state, it, isPlayer = false)
        }

        // 1. Phản hồi cứu quân (Defensive Trick): bảo vệ đơn vị AI có ThreatScore >= 8.0 sắp bị tiêu diệt
        val threatenedHighValueUnit = findThreatenedAiUnit(state, DEFENSIVE_TRICK_THREAT_THRESHOLD)

        // 2. Phản hồi ngắt nhịp (Disruption): kiểm tra xem người chơi có đang tung đòn kết liễu Nhà Chính AI hay không
        val playerAttackers = if (!state.playerInitiatedCombat && state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
            emptyList()
        } else {
            state.attackingUnitIds.mapNotNull { id -> state.playerBoard.find { it.instanceId == id } }
        }
        val incomingToAiNexus = CombatSimulator.estimateIncomingNexusDamage(
            attackers = playerAttackers,
            blockPairings = state.blockPairings,
            defenderBoard = state.enemyBoard,
            pendingStack = state.spellStack,
            attackerIsPlayer = true,
            state = state
        )
        val isPlayerThreateningLethal = incomingToAiNexus >= state.enemyNexusHp

        var chosenTargetId: String? = null
        val reactiveSpell = responseSpells.firstOrNull { spell ->
            when (spell.effectType) {
                CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                    val barrierTarget = threatenedHighValueUnit
                        ?: findThreatenedAiUnit(state, maxOf(4.5, spell.cost * 1.5))
                    val target = barrierTarget?.takeIf {
                        !it.hasBarrier && spell.cost <= CombatSimulator.threatScore(it, state) * 0.8
                    }
                    if (target != null) {
                        chosenTargetId = target.instanceId
                        true
                    } else false
                }
                CardEffectType.RALLY_AND_SHIELD -> {
                    val target = threatenedHighValueUnit?.takeIf {
                        !it.hasBarrier && spell.cost <= CombatSimulator.threatScore(it, state) * 0.8
                    }
                    if (target != null) {
                        chosenTargetId = target.instanceId
                        true
                    } else false
                }
                CardEffectType.STUN_TARGET,
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO -> {
                    val lethalAttackers = playerAttackers.filter { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                    val candidate = if (isPlayerThreateningLethal && lethalAttackers.isNotEmpty()) {
                        lethalAttackers.maxByOrNull { CombatSimulator.simulatePair(it, null, state.spellStack, state).damageToDefenderNexus }
                    } else if (threatenedHighValueUnit != null) {
                        // Đóng băng/choáng kẻ tấn công đang giao tranh với đơn vị chủ lực của AI
                        val combatAttackerId = state.blockPairings.entries
                            .firstOrNull { it.value == threatenedHighValueUnit.instanceId }?.key
                        state.playerBoard.find { it.instanceId == combatAttackerId }
                            ?.takeIf { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                    } else if (lethalAttackers.isNotEmpty()) {
                        lethalAttackers.maxByOrNull { CombatSimulator.threatScore(it, state) }
                    } else if (isInCombat && !state.playerInitiatedCombat) {
                        // AI đang tấn công và người chơi tung phép trong cửa sổ giao tranh -> choáng/đóng băng quân phòng thủ của người chơi
                        val blockerTargets = state.blockPairings.values
                            .mapNotNull { id -> state.playerBoard.find { it.instanceId == id } }
                        (blockerTargets.ifEmpty { state.playerBoard })
                            .filter { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                            .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    } else null

                    if (candidate != null) {
                        chosenTargetId = candidate.instanceId
                        true
                    } else false
                }
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DRAIN -> {
                    val killableAttacker = playerAttackers
                        .filter { !it.hasBarrier && !it.hasSpellShield && it.health <= spell.effectValue }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    val highValueKill = state.playerBoard
                        .filter { !it.hasBarrier && !it.hasSpellShield && it.health <= spell.effectValue }
                        .filter { CombatSimulator.threatScore(it, state) >= spell.cost * 1.2 || isPlayerThreateningLethal }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }

                    val chosen = killableAttacker ?: highValueKill
                    if (chosen != null) {
                        chosenTargetId = chosen.instanceId
                        true
                    } else if (state.playerNexusHp <= spell.effectValue) {
                        chosenTargetId = "PLAYER_NEXUS"
                        true
                    } else false
                }
                CardEffectType.SINGLE_COMBAT_STRIKE -> {
                    val pair = state.enemyBoard.firstNotNullOfOrNull { e ->
                        state.playerBoard.firstOrNull { p ->
                            val pThreat = CombatSimulator.threatScore(p, state)
                            !p.hasSpellShield &&
                                e.strikePower >= p.health &&
                                e.health > p.strikePower &&
                                (isPlayerThreateningLethal || pThreat >= spell.cost * 1.2)
                        }?.let { p -> e to p }
                    }
                    if (pair != null) {
                        chosenTargetId = "${pair.first.instanceId}|${pair.second.instanceId}"
                        true
                    } else false
                }
                else -> false
            }
        }

        if (reactiveSpell != null) {
            return castEnemySpell(state, reactiveSpell, logs, targetUnitId = chosenTargetId)
        }

        // Bỏ qua đúng lúc -> giải quyết Stack LIFO
        logs.add("Địch bỏ qua — đang thực thi hàng chờ phép.")
        return CombatEngine.resolveSpellStack(state.copy(combatLog = logs.takeLast(16)))
    }

    private fun findThreatenedAiUnit(
        state: CombatState,
        minThreatScore: Double = DEFENSIVE_TRICK_THREAT_THRESHOLD
    ): UnitOnBoard? {
        val candidateUnits = state.enemyBoard
            .filter { CombatSimulator.threatScore(it, state) >= minThreatScore }
            .sortedByDescending { CombatSimulator.threatScore(it, state) }
        if (candidateUnits.isEmpty()) return null

        // Kiểm tra chết do spellStack
        for (unit in candidateUnits) {
            val simNoCombat = CombatSimulator.simulatePair(
                attacker = unit,
                blocker = null,
                pendingStack = state.spellStack,
                state = state
            )
            if (simNoCombat.attackerDiedOnStack) return unit
        }

        // Kiểm tra chết trong cặp giao tranh đang chờ
        if (state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
            for ((atkId, blkId) in state.blockPairings) {
                val playerAtk = state.playerBoard.find { it.instanceId == atkId }
                val aiBlk = candidateUnits.find { it.instanceId == blkId }
                if (playerAtk != null && aiBlk != null) {
                    if (CombatSimulator.simulatePair(playerAtk, aiBlk, state.spellStack, state).blockerDies) {
                        return aiBlk
                    }
                }
            }
        }
        return null
    }

    // Case B: Pha Hành động tự do (IDLE_ACTION / MAIN_ACTION_PHASE) - Mục 2.3.A
    private fun handleMainActionPhase(state: CombatState, logs: MutableList<String>): CombatState {
        // Bước 1 – Kiểm tra Đòn Kết Liễu (Lethal Check -> Open Attack ngay lập tức)
        if (state.enemyHasAttackToken) {
            val allAttackers = state.enemyBoard.filter { it.canAttack }.take(6)
            if (allAttackers.isNotEmpty()) {
                val optimalDmg = CombatSimulator.simulateOptimalDefenseTotalDamage(
                    attackers = allAttackers,
                    defenders = state.playerBoard,
                    pendingStack = state.spellStack,
                    state = state
                )
                if (optimalDmg >= state.playerNexusHp) {
                    logs.add("AI phát hiện đòn kết liễu ($optimalDmg sát thương) — Tấn công phủ đầu ngay!")
                    return launchEnemyAttack(state, allAttackers.map { it.instanceId }, logs)
                }
            }
        }

        // Bước 2 – Phát triển Bàn đấu (Board Development) theo công thức PlayValue
        if (state.enemyBoard.size < 6) {
            val playableUnits = state.enemyHand.filter {
                (it.type == CardType.UNIT || it.type == CardType.CHAMPION || it.type == CardType.LANDMARK) &&
                    it.cost <= state.enemyMana
            }
            val bestUnit = playableUnits.maxByOrNull { card ->
                CombatSimulator.playValue(card, state)
            }
            if (bestUnit != null) {
                val summoned = CombatEngine.summonEnemyUnitFromPool(state, bestUnit, bestUnit.cost, logs)
                return CombatEngine.checkGameOver(
                    summoned.copy(
                        consecutivePasses = 0,
                        priorityHolder = PriorityHolder.PLAYER,
                        combatLog = logs.takeLast(16)
                    )
                )
            }
        }

        // Bước 3 – Tuyên bố Tấn công (Attack Declaration) bằng CombatSimulator
        if (state.enemyHasAttackToken) {
            val eligibleAttackers = state.enemyBoard.filter { it.canAttack }
            if (eligibleAttackers.isNotEmpty()) {
                val chosenAttackers = selectProfitableAttackers(eligibleAttackers, state)
                if (chosenAttackers.isNotEmpty()) {
                    return launchEnemyAttack(state, chosenAttackers.map { it.instanceId }, logs)
                }
            }
        }

        // Bước 4 – Sử dụng Phép Chậm / Phép chủ động (Proactive Spells)
        val totalMana = state.enemyMana + state.enemySpellMana
        val proactiveChoice = pickProactiveSpell(state, totalMana)
        if (proactiveChoice != null) {
            return castEnemySpell(state, proactiveChoice.first, logs, targetUnitId = proactiveChoice.second)
        }

        // Không còn hành động có lợi -> Pass Priority
        val nextPasses = state.consecutivePasses + 1
        if (nextPasses >= 2) {
            logs.add("Địch bỏ qua. Kết thúc Vòng ${state.round}.")
            return CombatEngine.advanceToNextRound(state, logs)
        }
        logs.add("Địch đã chuyển lượt.")
        return state.copy(
            consecutivePasses = nextPasses,
            priorityHolder = PriorityHolder.PLAYER,
            combatLog = logs.takeLast(16)
        )
    }

    private fun selectProfitableAttackers(
        eligibleAttackers: List<UnitOnBoard>,
        state: CombatState
    ): List<UnitOnBoard> {
        val potentialAllDmg = CombatSimulator.simulateOptimalDefenseTotalDamage(
            attackers = eligibleAttackers.take(6),
            defenders = state.playerBoard,
            pendingStack = state.spellStack,
            state = state
        )
        val forcesMajorPressure = potentialAllDmg >= 4 || potentialAllDmg >= (state.playerNexusHp * 0.25)

        return eligibleAttackers.filter { atk ->
            val atkScore = CombatSimulator.threatScore(atk, state)
            val eligibleBlockers = state.playerBoard.filter { it.canBlockAttacker(atk) }
            if (eligibleBlockers.isEmpty()) {
                return@filter atk.strikePower > 0 || atk.impactStacks > 0
            }

            // Nếu có Thách Đấu, AI có quyền tự chọn mục tiêu kéo lợi nhất
            if (!atk.isSilenced && atk.keywords.contains(Keyword.CHALLENGER)) {
                val challengeTargets = state.playerBoard.filter {
                    it.template.type != CardType.LANDMARK && !it.isStunned && !it.isShackled
                }
                val goodPull = challengeTargets.any { target ->
                    val sim = CombatSimulator.simulatePair(atk, target, state.spellStack, state)
                    !sim.attackerDies || (sim.blockerDies && CombatSimulator.threatScore(target, state) > atkScore)
                }
                if (goodPull) return@filter true
            }

            // 1. Sống sót sau phương án chặn tối ưu của đối phương
            val worstSim = eligibleBlockers
                .map { blk -> blk to CombatSimulator.simulatePair(atk, blk, state.spellStack, state) }
                .minByOrNull { (_, sim) -> sim.damageToDefenderNexus - if (sim.blockerDies) 10 else 0 }

            val survivesAllBlocks = eligibleBlockers.all { blk ->
                !CombatSimulator.simulatePair(atk, blk, state.spellStack, state).attackerDies
            }
            if (survivesAllBlocks) return@filter true

            // 2. Đổi mạng có lợi (giết quân địch ThreatScore cao hơn giá trị của chính mình)
            val favorableTrade = worstSim != null &&
                worstSim.second.blockerDies &&
                CombatSimulator.threatScore(worstSim.first, state) >= atkScore
            if (favorableTrade) return@filter true

            // 3. Ép đối phương mất lượng lớn máu Nhà Chính hoặc hy sinh quân chủ lực khi đủ lực tấn công
            forcesMajorPressure && (atk.effectivePower >= 3 || atk.keywords.contains(Keyword.OVERWHELM))
        }.take(6)
    }

    private fun launchEnemyAttack(
        state: CombatState,
        chosenIds: List<String>,
        logs: MutableList<String>
    ): CombatState {
        var s = state

        // 1. Hallowed leftmost buff (round-scoped, runs before Support)
        if (s.enemyHallowedStacks > 0 && chosenIds.isNotEmpty()) {
            val firstId = chosenIds.first()
            val firstUnit = s.enemyBoard.find { it.instanceId == firstId }
            s = CombatEngine.recordRoundHallowedBuff(s, firstId, s.enemyHallowedStacks)
            s = s.copy(enemyBoard = s.enemyBoard.map {
                if (it.instanceId == firstId) it.copy(power = it.power + s.enemyHallowedStacks) else it
            })
            if (firstUnit != null) {
                logs.add("Thiêng Liêng ban cho ${firstUnit.template.name} +${s.enemyHallowedStacks}|+0!")
            }
        }

        // 2. AI Lurk
        val hasLurkAttacker = s.enemyBoard.any { chosenIds.contains(it.instanceId) && !it.isSilenced && it.keywords.contains(Keyword.LURK) }
        val topEnemyIsLurk = s.enemyDrawPool.firstOrNull()?.keywords?.contains(Keyword.LURK) == true
        if (!s.enemyLurkTriggeredThisRound && hasLurkAttacker && topEnemyIsLurk) {
            s = s.copy(
                enemyLurkTriggeredThisRound = true,
                enemyLurkBonus = s.enemyLurkBonus + 1,
                enemyBoard = s.enemyBoard.map {
                    if (!it.isSilenced && it.keywords.contains(Keyword.LURK)) it.copy(power = it.power + 1) else it
                }
            )
            logs.add("Ẩn Nấp của địch kích hoạt! Quân Ẩn Nấp nhận +1|+0.")
        }

        // 3. Support left-to-right
        s = CombatEngine.applyAttackSupportBuffs(s, chosenIds, playerIsAttacking = false, logs = logs)

        if (CombatEngine.canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "READY_RANKS") {
            s = s.copy(enemyBoard = s.enemyBoard.map {
                if (chosenIds.contains(it.instanceId)) it.copy(power = it.power + 1) else it
            })
            logs.add("Sẵn Sàng Đội Hình ban cho quân tấn công của địch +1|+0!")
        }
        s = CombatEngine.applyEnemyAttackConstellationPowers(s, chosenIds, logs)

        // 4. Phân bổ mục tiêu Thách Đấu (Challenger) & Dễ Bị Tổn Thương (Vulnerable) theo ThreatScore & Overwhelm
        val aiChallenges = mutableMapOf<String, String>()
        val availablePlayerTargets = s.playerBoard
            .filter { it.template.type != CardType.LANDMARK && !it.isStunned && !it.isShackled }
            .toMutableList()

        for (atkId in chosenIds) {
            val atk = s.enemyBoard.find { it.instanceId == atkId } ?: continue
            val atkScore = CombatSimulator.threatScore(atk, s)
            val target = if (!atk.isSilenced && atk.keywords.contains(Keyword.CHALLENGER)) {
                val killableTargets = availablePlayerTargets.filter { p ->
                    val sim = CombatSimulator.simulatePair(atk, p, s.spellStack, s)
                    sim.blockerDies && (!sim.attackerDies || CombatSimulator.threatScore(p, s) >= atkScore)
                }
                if (atk.keywords.contains(Keyword.OVERWHELM)) {
                    // Thách Đấu + Áp Đảo: kéo quân máu thấp nhất để tối đa hóa sát thương xuyên vào Nhà Chính
                    killableTargets.minByOrNull { it.health }
                        ?: availablePlayerTargets.minByOrNull { it.health }
                } else {
                    // Thách Đấu thường: kéo mục tiêu ThreatScore cao nhất mà AI tiêu diệt được
                    killableTargets.maxByOrNull { CombatSimulator.threatScore(it, s) }
                        ?: availablePlayerTargets
                            .filter { it.template.isChampion && CombatSimulator.simulatePair(atk, it, s.spellStack, s).blockerDies }
                            .maxByOrNull { CombatSimulator.threatScore(it, s) }
                }
            } else {
                availablePlayerTargets
                    .filter { it.keywords.contains(Keyword.VULNERABLE) && CombatSimulator.simulatePair(atk, it, s.spellStack, s).blockerDies }
                    .maxByOrNull { CombatSimulator.threatScore(it, s) }
            }

            if (target != null) {
                aiChallenges[atkId] = target.instanceId
                availablePlayerTargets.remove(target)
                logs.add("${atk.template.name} kéo ${target.template.name} vào chặn!")
            }
        }

        logs.add("Địch tấn công với ${chosenIds.size} quân! Hãy chọn quân chặn.")
        var attackState = s.copy(
            enemyHasAttackToken = false,
            playerInitiatedCombat = false,
            blockCommitted = false,
            playerCastSpellInBlock = false,
            enemyCastBurstInBlock = false,
            playerFastSpellUsedInCombat = false,
            enemyFastSpellUsedInCombat = false,
            phase = CombatPhase.COMBAT_DECLARE_BLOCK_PHASE,
            priorityHolder = PriorityHolder.ENEMY,
            consecutivePasses = 0,
            attackingUnitIds = chosenIds,
            blockPairings = aiChallenges,
            confirmedBlockedAttackerIds = aiChallenges.keys.toSet(),
            challengedPairings = emptyMap(),
            lockedBlockAttackerIds = aiChallenges.keys.toSet(),
            selectedAttackerForChallenger = null,
            selectedEnemyAttackerForBlock = null,
            combatLog = logs.takeLast(16)
        )
        val attackFastSpell = pickReactiveCombatSpell(attackState, playerIsAttacking = false, fastOnly = true)
        return if (attackFastSpell != null) {
            castEnemySpell(attackState, attackFastSpell.first, logs, targetUnitId = attackFastSpell.second)
        } else {
            attackState.copy(priorityHolder = PriorityHolder.PLAYER)
        }
    }

    // Case C: Pha Phòng thủ (DECLARING_BLOCK) - Tối ưu 3 tầng theo Mục 2.3.B
    private fun handleBlockDeclaration(state: CombatState, logs: MutableList<String>): CombatState {
        val attackers = state.attackingUnitIds.mapNotNull { id ->
            state.playerBoard.find { it.instanceId == id }
        }
        val pairings = state.blockPairings.toMutableMap()
        val lockedBlockerIds = pairings.values.toSet()
        val availableBlockers = state.enemyBoard
            .filter { it.canBlock && !lockedBlockerIds.contains(it.instanceId) }
            .toMutableList()

        val leveledAsheActive = state.playerBoard.any { it.template.id == "card_ashe" && it.isLeveledUp }

        fun legalBlockersFor(atk: UnitOnBoard): List<UnitOnBoard> =
            availableBlockers.filter { blk ->
                blk.canBlockAttacker(atk) && (!leveledAsheActive || blk.effectivePower > 0)
            }

        val unblockedAttackers = attackers.filter { !pairings.containsKey(it.instanceId) }.toMutableList()

        // Tầng 1 (Favorable Trade): blocker sống sót và tiêu diệt được kẻ tấn công (có tính trước spellStack)
        val tier1Candidates = mutableListOf<Triple<UnitOnBoard, UnitOnBoard, Double>>()
        for (atk in unblockedAttackers) {
            val atkScore = CombatSimulator.threatScore(atk, state)
            for (blk in legalBlockersFor(atk)) {
                val sim = CombatSimulator.simulatePair(atk, blk, state.spellStack, state)
                if (!sim.blockerDiedOnStack && !sim.blockerDies && sim.attackerDies) {
                    val blkScore = CombatSimulator.threatScore(blk, state)
                    tier1Candidates += Triple(atk, blk, atkScore * 100.0 - blkScore)
                }
            }
        }
        for ((atk, blk, _) in tier1Candidates.sortedByDescending { it.third }) {
            if (!pairings.containsKey(atk.instanceId) && availableBlockers.contains(blk)) {
                pairings[atk.instanceId] = blk.instanceId
                availableBlockers.remove(blk)
                unblockedAttackers.remove(atk)
            }
        }

        // Tầng 2 (Value Trade): blocker có ThreatScore thấp hơn để đổi mạng tiêu diệt kẻ công ThreatScore cao hơn
        val tier2Candidates = mutableListOf<Triple<UnitOnBoard, UnitOnBoard, Double>>()
        for (atk in unblockedAttackers) {
            val atkScore = CombatSimulator.threatScore(atk, state)
            for (blk in legalBlockersFor(atk)) {
                val sim = CombatSimulator.simulatePair(atk, blk, state.spellStack, state)
                val blkScore = CombatSimulator.threatScore(blk, state)
                if (!sim.blockerDiedOnStack && sim.attackerDies && atkScore > blkScore) {
                    tier2Candidates += Triple(atk, blk, (atkScore - blkScore) * 100.0 - blkScore)
                }
            }
        }
        for ((atk, blk, _) in tier2Candidates.sortedByDescending { it.third }) {
            if (!pairings.containsKey(atk.instanceId) && availableBlockers.contains(blk)) {
                pairings[atk.instanceId] = blk.instanceId
                availableBlockers.remove(blk)
                unblockedAttackers.remove(atk)
            }
        }

        // Tầng 3 (Chump Block): nếu sát thương còn lại đưa HP Nhà Chính AI <= 0 hoặc <= 4 HP (ngưỡng an toàn)
        var currentIncoming = CombatSimulator.estimateIncomingNexusDamage(
            attackers = attackers,
            blockPairings = pairings,
            defenderBoard = state.enemyBoard,
            pendingStack = state.spellStack,
            attackerIsPlayer = true,
            state = state
        )
        if (currentIncoming >= state.enemyNexusHp || (state.enemyNexusHp - currentIncoming) <= CHUMP_BLOCK_SAFETY_HP) {
            // Ưu tiên chặn quân KHÔNG có Áp Đảo trước; chỉ chặn Áp Đảo nếu giúp giảm sát thương sinh tồn
            val sortedThreatAttackers = unblockedAttackers.sortedWith(
                compareBy<UnitOnBoard> { if (!it.isSilenced && it.keywords.contains(Keyword.OVERWHELM)) 1 else 0 }
                    .thenByDescending { CombatSimulator.simulatePair(it, null, state.spellStack, state).damageToDefenderNexus }
            )
            for (atk in sortedThreatAttackers) {
                if (currentIncoming < state.enemyNexusHp && (state.enemyNexusHp - currentIncoming) > CHUMP_BLOCK_SAFETY_HP) {
                    break
                }
                val unblockedDmg = CombatSimulator.simulatePair(atk, null, state.spellStack, state).damageToDefenderNexus
                if (unblockedDmg <= 0) continue

                val isOverwhelm = !atk.isSilenced && atk.keywords.contains(Keyword.OVERWHELM)
                val chosenChump = legalBlockersFor(atk)
                    .filter { blk ->
                        val sim = CombatSimulator.simulatePair(atk, blk, state.spellStack, state)
                        if (isOverwhelm) {
                            sim.damageToDefenderNexus < unblockedDmg
                        } else {
                            !sim.blockerDiedOnStack || currentIncoming >= state.enemyNexusHp
                        }
                    }
                    .minByOrNull { blk -> CombatSimulator.threatScore(blk, state) }

                if (chosenChump != null) {
                    val simWithChump = CombatSimulator.simulatePair(atk, chosenChump, state.spellStack, state)
                    pairings[atk.instanceId] = chosenChump.instanceId
                    availableBlockers.remove(chosenChump)
                    currentIncoming = maxOf(0, currentIncoming - (unblockedDmg - simWithChump.damageToDefenderNexus))
                }
            }
        }

        val confirmedIds = pairings.keys.toSet()
        val blockedState = state.copy(
            blockPairings = pairings,
            confirmedBlockedAttackerIds = confirmedIds,
            blockCommitted = true,
            combatLog = logs.takeLast(16)
        )

        val reactiveSpell = pickReactiveCombatSpell(blockedState, playerIsAttacking = true)
        if (reactiveSpell != null) {
            logs.add("Địch đã gán ${pairings.size} quân chặn và dùng ${reactiveSpell.first.name}.")
            var afterSpell = castEnemySpell(blockedState, reactiveSpell.first, logs, targetUnitId = reactiveSpell.second)
            if (reactiveSpell.first.spellSpeed == SpellSpeed.BURST && !afterSpell.enemyFastSpellUsedInCombat) {
                val followUpFast = pickReactiveCombatSpell(afterSpell, playerIsAttacking = true, fastOnly = true)
                if (followUpFast != null) {
                    afterSpell = castEnemySpell(afterSpell, followUpFast.first, logs, targetUnitId = followUpFast.second)
                }
            }
            return afterSpell.copy(
                priorityHolder = PriorityHolder.PLAYER,
                blockCommitted = true,
                confirmedBlockedAttackerIds = confirmedIds,
                enemyCastBurstInBlock = false
            )
        }

        if (blockedState.spellStack.any { it.card.id == "skill_glacial_storm" }) {
            logs.add("Địch đã kéo ${pairings.size} quân chặn và không có hành động — Bão Băng Giá chuẩn bị áp dụng.")
            return blockedState.copy(
                priorityHolder = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                combatLog = logs.takeLast(16)
            )
        }

        logs.add("Địch đã xác nhận ${pairings.size} quân chặn — đến lượt bạn phản hồi trước khi giao tranh.")
        return blockedState.copy(
            priorityHolder = PriorityHolder.PLAYER,
            consecutivePasses = 0,
            combatLog = logs.takeLast(16)
        )
    }

    private fun pickProactiveSpell(state: CombatState, totalMana: Int): Pair<CardTemplate, String?>? {
        val candidateSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                it.cost <= totalMana &&
                CombatEngine.canPlayCard(state, it, isPlayer = false)
        }.sortedByDescending { it.cost }

        for (spell in candidateSpells) {
            when (spell.effectType) {
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DRAIN -> {
                    if (state.playerNexusHp <= spell.effectValue) return spell to "PLAYER_NEXUS"
                    val bestTarget = state.playerBoard
                        .filter { !it.hasBarrier && !it.hasSpellShield && it.health <= spell.effectValue }
                        .filter { CombatSimulator.threatScore(it, state) >= spell.cost * 1.2 || state.enemyHasAttackToken }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    if (bestTarget != null) return spell to bestTarget.instanceId
                }
                CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS,
                CardEffectType.DEAL_DAMAGE_NEXUS -> {
                    if (state.playerNexusHp <= spell.effectValue ||
                        state.playerBoard.any { it.health <= spell.effectValue }
                    ) {
                        return spell to null
                    }
                }
                CardEffectType.STUN_TARGET,
                CardEffectType.FROSTBITE_TARGET -> {
                    val bestTarget = state.playerBoard
                        .filter { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                        .filter { CombatSimulator.threatScore(it, state) >= spell.cost * 1.3 || state.enemyHasAttackToken }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    if (bestTarget != null) return spell to bestTarget.instanceId
                }
                CardEffectType.GIVE_BARRIER_AND_DRAW,
                CardEffectType.RALLY_AND_SHIELD -> {
                    val bestAlly = state.enemyBoard
                        .filter { !it.hasBarrier }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    if (bestAlly != null && (state.enemyHasAttackToken || state.enemyBoard.isNotEmpty())) {
                        return spell to bestAlly.instanceId
                    }
                }
                CardEffectType.RALLY -> if (!state.enemyHasAttackToken && state.enemyBoard.isNotEmpty()) return spell to null
                CardEffectType.FREE_ATTACK -> {
                    val bestAtk = state.enemyBoard.filter { it.canAttack }.maxByOrNull { CombatSimulator.threatScore(it, state) }
                    if (bestAtk != null) return spell to bestAtk.instanceId
                }
                CardEffectType.SINGLE_COMBAT_STRIKE -> {
                    val pair = state.enemyBoard.firstNotNullOfOrNull { e ->
                        state.playerBoard.firstOrNull { p ->
                            !p.hasSpellShield &&
                                e.strikePower >= p.health &&
                                e.health > p.strikePower &&
                                CombatSimulator.threatScore(p, state) >= spell.cost * 1.2
                        }?.let { p -> e to p }
                    }
                    if (pair != null) return spell to "${pair.first.instanceId}|${pair.second.instanceId}"
                }
                CardEffectType.PREDICT_DRAW,
                CardEffectType.PLANT_PUFFCAPS -> return spell to null
                else -> {}
            }
        }
        return null
    }

    private fun pickReactiveCombatSpell(
        state: CombatState,
        playerIsAttacking: Boolean,
        fastOnly: Boolean = false
    ): Pair<CardTemplate, String?>? {
        val combatSpells = state.enemyHand.filter {
            it.type == CardType.SPELL &&
                (!fastOnly || it.spellSpeed == SpellSpeed.FAST) &&
                CombatEngine.canPlayCard(state, it, isPlayer = false)
        }
        for (spell in combatSpells) {
            when (spell.effectType) {
                CardEffectType.GIVE_BARRIER_AND_DRAW -> {
                    val candidate = state.enemyBoard
                        .filter { !it.hasBarrier && (state.blockPairings.values.contains(it.instanceId) || state.attackingUnitIds.contains(it.instanceId)) }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                        ?: state.enemyBoard.firstOrNull { !it.hasBarrier && CombatSimulator.threatScore(it, state) >= DEFENSIVE_TRICK_THREAT_THRESHOLD }
                    if (candidate != null) return spell to candidate.instanceId
                }
                CardEffectType.STUN_TARGET,
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO -> {
                    val targets = if (playerIsAttacking) {
                        state.attackingUnitIds.mapNotNull { id -> state.playerBoard.find { it.instanceId == id } }
                    } else {
                        state.playerBoard
                    }
                    val chosen = targets
                        .filter { !it.isStunned && (spell.effectType == CardEffectType.STUN_TARGET || !it.isFrostbitten) }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    if (chosen != null) return spell to chosen.instanceId
                }
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DRAIN -> {
                    val killable = state.playerBoard
                        .filter { !it.hasBarrier && !it.hasSpellShield && it.health <= spell.effectValue }
                        .maxByOrNull { CombatSimulator.threatScore(it, state) }
                    if (killable != null) return spell to killable.instanceId
                    if (state.playerNexusHp <= spell.effectValue) return spell to "PLAYER_NEXUS"
                    if (!playerIsAttacking && state.blockCommitted) {
                        val barrierBlocker = state.blockPairings.values.firstOrNull { blkId ->
                            state.playerBoard.any { p -> p.instanceId == blkId && p.hasBarrier }
                        }
                        if (barrierBlocker != null) return spell to barrierBlocker
                    }
                }
                CardEffectType.SINGLE_COMBAT_STRIKE -> {
                    val pair = state.enemyBoard.firstNotNullOfOrNull { e ->
                        state.playerBoard.firstOrNull { p ->
                            !p.hasSpellShield && e.strikePower >= p.health && e.health > p.strikePower
                        }?.let { p -> e to p }
                    }
                    if (pair != null) return spell to "${pair.first.instanceId}|${pair.second.instanceId}"
                }
                else -> {}
            }
        }
        return null
    }

    private fun castEnemySpell(
        state: CombatState,
        spell: CardTemplate,
        logs: MutableList<String>,
        targetUnitId: String? = null
    ): CombatState {
        val spellUsed = minOf(maxOf(0, state.enemySpellMana), spell.cost)
        val unitUsed = spell.cost - spellUsed
        val isInCombat = state.phase == CombatPhase.COMBAT_DECLARE_ATTACK_PHASE ||
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE
        val usedFastInCombat = spell.spellSpeed == SpellSpeed.FAST && isInCombat
        val castBurstInBlock = spell.spellSpeed == SpellSpeed.BURST &&
            state.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE

        var s = state.copy(
            enemySpellMana = maxOf(0, state.enemySpellMana - spellUsed),
            enemyMana = maxOf(0, state.enemyMana - unitUsed),
            enemyHand = CombatEngine.removeFirstTemplate(state.enemyHand, spell.id),
            enemyFastSpellUsedInCombat = state.enemyFastSpellUsedInCombat || usedFastInCombat,
            enemyCastBurstInBlock = state.enemyCastBurstInBlock || castBurstInBlock
        )
        if (CombatEngine.canonicalEnemyPowerKey(s.encounterNode.enemyPowerTitle) == "ENLIGHTENED_HARMONY") {
            val healed = minOf(s.enemyMaxHp, s.enemyNexusHp + 1)
            if (healed > s.enemyNexusHp) {
                s = s.copy(enemyNexusHp = healed)
                logs.add("Hòa Hợp Khai Sáng hồi +1 Máu cho Nhà Chính địch.")
            }
        }

        return if (spell.spellSpeed == SpellSpeed.BURST || spell.spellSpeed == SpellSpeed.FOCUS) {
            s = CombatEngine.resolveSingleSpellEffect(
                state = s,
                card = spell,
                sourceIsPlayer = false,
                logs = logs,
                targetUnitId = targetUnitId
            )
            val nextPriority = if (s.isFreeAttack && s.phase == CombatPhase.COMBAT_DECLARE_BLOCK_PHASE) {
                s.priorityHolder
            } else {
                PriorityHolder.ENEMY
            }
            CombatEngine.checkGameOver(
                s.copy(
                    consecutivePasses = 0,
                    priorityHolder = nextPriority,
                    combatLog = logs.takeLast(16)
                )
            )
        } else {
            val entry = SpellStackEntry(
                id = "st_${CombatEngine.generateId()}",
                sourceIsPlayer = false,
                card = spell,
                targetUnitId = targetUnitId
            )
            val nextPhase = if (s.phase == CombatPhase.MAIN_ACTION_PHASE) CombatPhase.SPELL_STACK_PHASE else s.phase
            logs.add("Địch đặt ${spell.name} (${spell.spellSpeed.displayName}) lên hàng chờ phép.")
            s.copy(
                spellStack = s.spellStack + entry,
                phase = nextPhase,
                lastStackCaster = PriorityHolder.ENEMY,
                consecutivePasses = 0,
                priorityHolder = PriorityHolder.PLAYER,
                combatLog = logs.takeLast(16)
            )
        }
    }
}
