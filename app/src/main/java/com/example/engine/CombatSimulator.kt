package com.example.engine

import com.example.model.CardEffectType
import com.example.model.CardTemplate
import com.example.model.CardType
import com.example.model.Keyword
import com.example.model.UnitOnBoard

/**
 * Kết quả mô phỏng một cặp giao tranh (hoặc đòn đánh không bị chặn)
 * có tính trước sát thương từ hàng chờ phép (spellStack), thứ tự đánh (Quick Attack / Double Attack),
 * Giáp (Tough / Barrier), Đóng Băng (Frostbite) và Áp Đảo (Overwhelm).
 */
data class PairSimulationResult(
    val attackerDies: Boolean,
    val blockerDies: Boolean,
    val blockerDiedOnStack: Boolean,
    val attackerDiedOnStack: Boolean,
    val damageToDefenderNexus: Int,
    val damageToAttackerNexusHeal: Int,
    val blockerDamageTaken: Int,
    val attackerDamageTaken: Int
)

/**
 * Bộ định giá ThreatScore & mô phỏng giao tranh tổng quát theo Quy-tắc-AI.pdf (Phần 2.1 & 2.2).
 */
object CombatSimulator {

    /**
     * ThreatScore = (CurrentAttack * 1.5) + CurrentHealth + KeywordBonus + RoleBonus
     */
    fun threatScore(unit: UnitOnBoard, state: CombatState? = null): Double {
        if (unit.template.type == CardType.LANDMARK) {
            return 3.0 + unit.template.cost * 0.6
        }
        val baseStats = (unit.effectivePower * 1.5) + unit.health.toDouble()
        val kwBonus = if (unit.isSilenced) 0.0 else keywordBonus(unit)
        val roleBonus = roleBonus(unit, state)
        val barrierBonus = if (unit.hasBarrier) 1.5 else 0.0
        val spellShieldBonus = if (unit.hasSpellShield) 1.2 else 0.0
        val deathlessBonus = if (unit.hasDeathless) 2.2 else 0.0
        return baseStats + kwBonus + roleBonus + barrierBonus + spellShieldBonus + deathlessBonus
    }

    private fun keywordBonus(unit: UnitOnBoard): Double {
        var bonus = 0.0
        val kws = unit.keywords
        if (kws.contains(Keyword.DOUBLE_ATTACK)) bonus += 4.5
        if (kws.contains(Keyword.OVERWHELM)) bonus += 3.0
        if (kws.contains(Keyword.ELUSIVE)) bonus += 2.8
        if (kws.contains(Keyword.QUICK_ATTACK)) bonus += 2.5
        if (kws.contains(Keyword.LIFESTEAL)) bonus += 2.5
        if (kws.contains(Keyword.REGENERATION)) bonus += 2.0
        if (kws.contains(Keyword.CHALLENGER)) bonus += 1.8
        if (kws.contains(Keyword.FEARSOME)) bonus += 1.5
        if (kws.contains(Keyword.TOUGH)) bonus += 1.5
        if (kws.contains(Keyword.FATED)) bonus += 1.2
        if (kws.contains(Keyword.AUGMENT)) bonus += 1.2
        if (kws.contains(Keyword.BRASH)) bonus += 1.0
        if (kws.contains(Keyword.FORMIDABLE)) bonus += 1.5
        if (kws.contains(Keyword.IMPACT)) bonus += 1.0 * maxOf(1, unit.impactStacks)
        if (kws.contains(Keyword.EPHEMERAL)) bonus -= 1.5
        if (kws.contains(Keyword.IMMOBILE) || kws.contains(Keyword.CANT_BLOCK)) bonus -= 1.0
        return bonus
    }

    private fun roleBonus(unit: UnitOnBoard, state: CombatState?): Double {
        if (unit.isSilenced) return 0.0
        var bonus = 0.0
        if (unit.template.isChampion) {
            bonus += 4.0
            if (!unit.isLeveledUp) {
                val nearLevel = unit.levelProgress > 0 ||
                    (unit.template.id == "card_anivia" && ((state?.playerMaxMana ?: 0) >= 8 || state?.isEnlightened == true)) ||
                    (unit.template.id == "card_lux" && (state?.playerSpellsManaSpent ?: 0) >= 3) ||
                    (unit.template.id == "card_yasuo" && (state?.enemiesStunnedCount ?: 0) >= 2) ||
                    (unit.template.id == "card_ashe" && (state?.enemiesFrostbittenCount ?: 0) >= 2)
                if (nearLevel) bonus += 2.5
            }
        }
        // Đơn vị dạng Trứng hồi sinh / hiệu ứng đầu vòng / tạo bài liên tục
        if (unit.template.id == "card_eggnivia") {
            bonus += 5.5
        } else if (unit.template.id in continuousEffectUnitIds) {
            bonus += 2.5
        }
        return bonus
    }

    private val continuousEffectUnitIds = setOf(
        "card_anivia",
        "card_mortal_prophet",
        "card_braum",
        "card_garen",
        "card_jinx",
        "card_karma",
        "card_viktor",
        "card_lux"
    )

    /**
     * PlayValue = (ThreatScore + PlayEffectValue) / (ManaCost + 1)
     */
    fun playValue(card: CardTemplate, state: CombatState): Double {
        val simulatedUnit = UnitOnBoard(
            instanceId = "sim_${card.id}",
            template = card,
            ownerIsPlayer = false,
            power = card.basePower,
            health = card.baseHealth,
            maxHealth = card.baseHealth,
            keywords = card.keywords,
            impactStacks = card.impactStacks,
            hasBarrier = card.keywords.contains(Keyword.BARRIER),
            hasSpellShield = card.keywords.contains(Keyword.SPELLSHIELD),
            hasDeathless = card.keywords.contains(Keyword.DEATHLESS)
        )
        var effectValue = 0.0
        if (card.keywords.contains(Keyword.CHALLENGER)) {
            val highThreatKillable = state.playerBoard
                .filter { it.template.type != CardType.LANDMARK && card.basePower >= it.health }
                .maxOfOrNull { threatScore(it, state) } ?: 0.0
            if (highThreatKillable >= 6.0) {
                effectValue += 4.0 + (highThreatKillable * 0.25)
            }
        }
        when (card.effectType) {
            CardEffectType.FROSTBITE_TARGET -> {
                val maxPlayerAtk = state.playerBoard.maxOfOrNull { it.effectivePower } ?: 0
                if (maxPlayerAtk > 0) effectValue += maxPlayerAtk * 1.1 + 1.5
            }
            CardEffectType.DEAL_DAMAGE_ANY,
            CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS -> {
                val lowHpCount = state.playerBoard.count { it.health <= card.effectValue.coerceAtLeast(1) }
                effectValue += lowHpCount * 2.5 + 1.5
            }
            CardEffectType.STUN_TARGET -> {
                if (state.playerBoard.isNotEmpty()) effectValue += 3.0
            }
            else -> {}
        }
        if (card.id == "card_crystal_stormbird" || card.id == "card_flame_eyed_caprine") {
            val lowHpPlayers = state.playerBoard.count { it.health <= 2 }
            effectValue += lowHpPlayers * 2.2 + 1.5
        } else if (card.id == "card_rhasa") {
            effectValue += minOf(2, state.playerBoard.size) * 4.0
        } else if (card.id == "card_brightsteel_protector" && state.enemyBoard.isNotEmpty()) {
            effectValue += 2.5
        }

        val baseScore = threatScore(simulatedUnit, state) + effectValue
        return baseScore / (card.cost + 1.0)
    }

    /**
     * Tính trước hiệu ứng của hàng chờ phép (spellStack) lên một đơn vị cụ thể trên sân.
     * Trả về: Triple(remainingHp, isNeutralizedByStunOrRecall, isFrostbittenByStack)
     */
    private fun simulatePendingStackOnUnit(
        unit: UnitOnBoard,
        pendingStack: List<SpellStackEntry>,
        state: CombatState?
    ): Triple<Int, Boolean, Boolean> {
        if (pendingStack.isEmpty()) return Triple(unit.health, false, unit.isFrostbitten)

        var hp = unit.health
        var hasBarrier = unit.hasBarrier
        var hasSpellShield = unit.hasSpellShield
        val isTough = !unit.isSilenced && unit.keywords.contains(Keyword.TOUGH)
        var neutralized = unit.isStunned || unit.isShackled
        var frostbitten = unit.isFrostbitten

        // Stack resolves LIFO
        for (entry in pendingStack.reversed()) {
            val isHostileToUnit = entry.sourceIsPlayer != unit.ownerIsPlayer
            val card = entry.card
            val hitsUnit = when (card.effectType) {
                CardEffectType.GLACIAL_STORM_SKILL -> isHostileToUnit
                CardEffectType.WINGS_OF_CRYOPHOENIX ->
                    !unit.template.isChampion && unit.template.id != "card_wild_mystics"
                CardEffectType.STUN_TARGET,
                CardEffectType.RECALL_OR_STUN,
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO,
                CardEffectType.ANIVIA_CHAMPION_SPELL,
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS,
                CardEffectType.DRAIN -> entry.targetUnitId == unit.instanceId
                else -> false
            }
            if (!hitsUnit) continue

            if (isHostileToUnit && hasSpellShield) {
                hasSpellShield = false
                continue
            }

            when (card.effectType) {
                CardEffectType.STUN_TARGET, CardEffectType.RECALL_OR_STUN -> {
                    neutralized = true
                }
                CardEffectType.FROSTBITE_TARGET,
                CardEffectType.FROSTBITE_TWO,
                CardEffectType.ANIVIA_CHAMPION_SPELL -> {
                    frostbitten = true
                }
                CardEffectType.GLACIAL_STORM_SKILL,
                CardEffectType.WINGS_OF_CRYOPHOENIX,
                CardEffectType.DEAL_DAMAGE_ANY,
                CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS,
                CardEffectType.DRAIN -> {
                    val rawDmg = if (state != null) {
                        CombatEngine.calculateFinalDamage(
                            baseDamage = card.effectValue,
                            card = card,
                            state = state,
                            sourceIsPlayer = entry.sourceIsPlayer,
                            extraBonus = entry.damageBonus
                        )
                    } else {
                        card.effectValue + entry.damageBonus
                    }
                    if (rawDmg > 0) {
                        if (hasBarrier) {
                            hasBarrier = false
                        } else {
                            val afterTough = if (isTough) maxOf(0, rawDmg - 1) else rawDmg
                            hp -= afterTough
                        }
                    }
                }
                else -> {}
            }
        }
        return Triple(hp, neutralized, frostbitten)
    }

    /**
     * Mô phỏng một cặp giao tranh attacker vs blocker có xét spellStack, Tough, Barrier,
     * Frostbite, Quick Attack, Double Attack và Overwhelm.
     */
    fun simulatePair(
        attacker: UnitOnBoard,
        blocker: UnitOnBoard?,
        pendingStack: List<SpellStackEntry> = emptyList(),
        state: CombatState? = null
    ): PairSimulationResult {
        val (atkHpAfterStack, atkNeutralized, atkFrost) = simulatePendingStackOnUnit(attacker, pendingStack, state)
        val attackerDiedOnStack = atkHpAfterStack <= 0 && !attacker.hasDeathless
        if (attackerDiedOnStack || atkNeutralized) {
            val blkDiedOnStack = if (blocker != null) {
                val (blkHp, _, _) = simulatePendingStackOnUnit(blocker, pendingStack, state)
                blkHp <= 0 && !blocker.hasDeathless
            } else false
            return PairSimulationResult(
                attackerDies = attackerDiedOnStack,
                blockerDies = blkDiedOnStack,
                blockerDiedOnStack = blkDiedOnStack,
                attackerDiedOnStack = attackerDiedOnStack,
                damageToDefenderNexus = 0,
                damageToAttackerNexusHeal = 0,
                blockerDamageTaken = if (blocker != null) maxOf(0, blocker.health - simulatePendingStackOnUnit(blocker, pendingStack, state).first) else 0,
                attackerDamageTaken = maxOf(0, attacker.health - atkHpAfterStack)
            )
        }

        val atkIsFormidable = !attacker.isSilenced && attacker.keywords.contains(Keyword.FORMIDABLE)
        val rawAtkPower = when {
            atkIsFormidable -> atkHpAfterStack
            atkFrost -> 0
            else -> attacker.strikePower
        }.coerceAtLeast(0)
        val atkHasOverwhelm = !attacker.isSilenced && attacker.keywords.contains(Keyword.OVERWHELM)
        val atkHasQuick = !attacker.isSilenced &&
            (attacker.keywords.contains(Keyword.QUICK_ATTACK) || attacker.keywords.contains(Keyword.DOUBLE_ATTACK))
        val atkHasDouble = !attacker.isSilenced && attacker.keywords.contains(Keyword.DOUBLE_ATTACK)
        val atkHasLifesteal = !attacker.isSilenced && attacker.keywords.contains(Keyword.LIFESTEAL)
        val atkTough = !attacker.isSilenced && attacker.keywords.contains(Keyword.TOUGH)
        val atkImpact = if (!attacker.isSilenced) attacker.impactStacks else 0

        if (blocker == null) {
            val strikes = if (atkHasDouble) 2 else 1
            val nexusDmg = (rawAtkPower * strikes) + atkImpact
            val lifestealHeal = if (atkHasLifesteal) rawAtkPower * strikes else 0
            return PairSimulationResult(
                attackerDies = false,
                blockerDies = false,
                blockerDiedOnStack = false,
                attackerDiedOnStack = false,
                damageToDefenderNexus = nexusDmg,
                damageToAttackerNexusHeal = lifestealHeal,
                blockerDamageTaken = 0,
                attackerDamageTaken = maxOf(0, attacker.health - atkHpAfterStack)
            )
        }

        val (blkHpAfterStack, _, blkFrost) = simulatePendingStackOnUnit(blocker, pendingStack, state)
        val blockerDiedOnStack = blkHpAfterStack <= 0 && !blocker.hasDeathless
        if (blockerDiedOnStack) {
            val strikes = if (atkHasDouble) 2 else 1
            val nexusDmg = if (atkHasOverwhelm) {
                (rawAtkPower * strikes) + atkImpact
            } else {
                // Ô chặn trống nhưng nếu đã khóa chặn không Overwhelm vẫn tính Impact;
                // nếu chưa khóa chặn (AI đang cân nhắc đặt blocker), không có lý do tự sát nếu không cứu được gì.
                atkImpact
            }
            val lifestealHeal = if (atkHasLifesteal && atkHasOverwhelm) rawAtkPower * strikes else 0
            return PairSimulationResult(
                attackerDies = false,
                blockerDies = true,
                blockerDiedOnStack = true,
                attackerDiedOnStack = false,
                damageToDefenderNexus = nexusDmg,
                damageToAttackerNexusHeal = lifestealHeal,
                blockerDamageTaken = blocker.health,
                attackerDamageTaken = maxOf(0, attacker.health - atkHpAfterStack)
            )
        }

        val blkIsFormidable = !blocker.isSilenced && blocker.keywords.contains(Keyword.FORMIDABLE)
        val rawBlkPower = when {
            blkIsFormidable -> blkHpAfterStack
            blkFrost -> 0
            else -> blocker.strikePower
        }.coerceAtLeast(0)
        val blkTough = !blocker.isSilenced && blocker.keywords.contains(Keyword.TOUGH)
        var blkBarrier = blocker.hasBarrier
        var atkBarrier = attacker.hasBarrier

        var curAtkHp = atkHpAfterStack
        var curBlkHp = blkHpAfterStack
        var totalNexusDmg = atkImpact
        var totalAtkDealt = 0

        fun applyStrikeToBlocker(power: Int): Int {
            if (power <= 0) return 0
            if (atkHasOverwhelm) {
                val absorbed = minOf(power, maxOf(0, curBlkHp))
                val bleed = maxOf(0, power - maxOf(0, curBlkHp))
                totalNexusDmg += bleed
                if (blkBarrier) {
                    blkBarrier = false
                    totalAtkDealt += bleed
                    return bleed
                } else {
                    val unitDmg = if (blkTough) maxOf(0, absorbed - 1) else absorbed
                    curBlkHp -= unitDmg
                    totalAtkDealt += unitDmg + bleed
                    return unitDmg + bleed
                }
            } else {
                if (blkBarrier) {
                    blkBarrier = false
                    return 0
                }
                val unitDmg = if (blkTough) maxOf(0, power - 1) else power
                curBlkHp -= unitDmg
                totalAtkDealt += unitDmg
                return unitDmg
            }
        }

        fun applyStrikeToAttacker(power: Int) {
            if (power <= 0) return
            if (atkBarrier) {
                atkBarrier = false
                return
            }
            val unitDmg = if (atkTough) maxOf(0, power - 1) else power
            curAtkHp -= unitDmg
        }

        if (atkHasQuick) {
            applyStrikeToBlocker(rawAtkPower)
            val blockerKilledFirst = curBlkHp <= 0 && !blocker.hasDeathless
            if (!blockerKilledFirst) {
                applyStrikeToAttacker(rawBlkPower)
            }
            if (atkHasDouble && (curAtkHp > 0 || attacker.hasDeathless)) {
                if (blockerKilledFirst) {
                    if (atkHasOverwhelm) {
                        totalNexusDmg += rawAtkPower
                        totalAtkDealt += rawAtkPower
                    }
                } else {
                    applyStrikeToBlocker(rawAtkPower)
                }
            }
        } else {
            applyStrikeToBlocker(rawAtkPower)
            applyStrikeToAttacker(rawBlkPower)
        }

        val atkDies = curAtkHp <= 0 && !attacker.hasDeathless
        val blkDies = curBlkHp <= 0 && !blocker.hasDeathless
        val lifestealHeal = if (atkHasLifesteal) totalAtkDealt else 0

        return PairSimulationResult(
            attackerDies = atkDies,
            blockerDies = blkDies,
            blockerDiedOnStack = false,
            attackerDiedOnStack = false,
            damageToDefenderNexus = totalNexusDmg,
            damageToAttackerNexusHeal = lifestealHeal,
            blockerDamageTaken = maxOf(0, blocker.health - curBlkHp),
            attackerDamageTaken = maxOf(0, attacker.health - curAtkHp)
        )
    }

    /**
     * Mô phỏng tổng sát thương lọt vào Nhà Chính đối phương nếu toàn bộ [attackers] tấn công ngay lập tức
     * và đối phương xếp đội hình chặn tối ưu nhất (giảm tối đa sát thương vào Nhà Chính).
     */
    fun simulateOptimalDefenseTotalDamage(
        attackers: List<UnitOnBoard>,
        defenders: List<UnitOnBoard>,
        pendingStack: List<SpellStackEntry>,
        state: CombatState?
    ): Int {
        if (attackers.isEmpty()) return 0
        val availableDefenders = defenders.filter { it.canBlock }.toMutableList()
        var totalNexusDmg = 0

        // Sắp xếp kẻ tấn công nguy hiểm nhất (sát thương không bị chặn cao nhất) trước để hậu vệ chặn tối ưu
        val sortedAttackers = attackers.sortedByDescending {
            simulatePair(it, null, pendingStack, state).damageToDefenderNexus
        }
        for (atk in sortedAttackers) {
            val unblockedDmg = simulatePair(atk, null, pendingStack, state).damageToDefenderNexus
            val eligible = availableDefenders.filter { it.canBlockAttacker(atk) }
            if (eligible.isEmpty()) {
                totalNexusDmg += unblockedDmg
                continue
            }
            // Bên phòng thủ chọn blocker giảm được nhiều sát thương Nexus nhất (ưu tiên blocker sống sót)
            val bestDef = eligible.minWithOrNull(
                compareBy<UnitOnBoard> { simulatePair(atk, it, pendingStack, state).damageToDefenderNexus }
                    .thenBy { if (simulatePair(atk, it, pendingStack, state).blockerDies) 1 else 0 }
                    .thenBy { threatScore(it, state) }
            )
            if (bestDef != null) {
                val sim = simulatePair(atk, bestDef, pendingStack, state)
                if (sim.damageToDefenderNexus < unblockedDmg) {
                    availableDefenders.remove(bestDef)
                    totalNexusDmg += sim.damageToDefenderNexus
                } else {
                    totalNexusDmg += unblockedDmg
                }
            } else {
                totalNexusDmg += unblockedDmg
            }
        }
        return totalNexusDmg
    }

    /**
     * Tính tổng sát thương đang đe dọa Nhà Chính dựa trên các cặp chặn hiện tại + quân chưa chặn + stack burn.
     */
    fun estimateIncomingNexusDamage(
        attackers: List<UnitOnBoard>,
        blockPairings: Map<String, String>,
        defenderBoard: List<UnitOnBoard>,
        pendingStack: List<SpellStackEntry>,
        attackerIsPlayer: Boolean,
        state: CombatState
    ): Int {
        var total = 0
        for (atk in attackers) {
            val blkId = blockPairings[atk.instanceId]
            val blk = blkId?.let { id -> defenderBoard.find { it.instanceId == id } }
            total += simulatePair(atk, blk, pendingStack, state).damageToDefenderNexus
        }
        for (entry in pendingStack) {
            if (entry.sourceIsPlayer != attackerIsPlayer) continue
            val isNexusTarget = entry.targetUnitId == "ENEMY_NEXUS" ||
                entry.targetUnitId == "PLAYER_NEXUS" ||
                entry.targetUnitId == "NEXUS" ||
                entry.card.effectType == CardEffectType.DEAL_DAMAGE_NEXUS ||
                entry.card.effectType == CardEffectType.DEAL_DAMAGE_UNIT_AND_NEXUS ||
                entry.card.effectType == CardEffectType.GLACIAL_STORM_SKILL
            if (isNexusTarget) {
                total += CombatEngine.calculateFinalDamage(
                    entry.card.effectValue,
                    entry.card,
                    state,
                    entry.sourceIsPlayer,
                    entry.damageBonus
                )
            }
        }
        return total
    }
}
