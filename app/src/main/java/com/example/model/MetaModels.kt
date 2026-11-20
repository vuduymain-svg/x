package com.example.model

data class StarPowerSpec(
    val starLevel: Int,
    val title: String,
    val description: String,
    val fragmentCost: Int,
    val starCrystalCost: Int = 0,
    val novaCrystalCost: Int = 0
)

data class ConstellationMinorNode(
    val id: String,
    val title: String,
    val description: String,
    val fragmentCost: Int,
    val gemstoneCost: Int = 0,
    val bonusMaxHp: Int = 0,
    val bonusRerolls: Int = 0,
    val bonusStartingGold: Int = 0
)

data class ChampionTemplate(
    val id: String,
    val name: String,
    val title: String,
    val region: Region,
    val cardId: String,
    val archetypeSummary: String,
    val unlockFragmentCost: Int = 0,
    val baseMaxHp: Int = 30,
    val baseDeck: List<DeckCardEntry>,
    val starPowers: List<StarPowerSpec>,
    val constellationNodes: List<ConstellationMinorNode>
) {
    fun nextStarPower(currentStar: Int): StarPowerSpec? =
        starPowers.filter { it.starLevel > currentStar }.minByOrNull { it.starLevel }
}

data class RarityOdds(
    val common: Double,
    val rare: Double,
    val epic: Double,
    val legendary: Double
)

fun rarityOddsForLevel(championLevel: Int): RarityOdds {
    val lv = championLevel.coerceIn(1, 30)
    if (lv >= 30) {
        return RarityOdds(common = 0.445, rare = 0.400, epic = 0.125, legendary = 0.030)
    }
    val t = (lv - 1) / 29.0
    return RarityOdds(
        common = 0.75 - t * (0.75 - 0.445),
        rare = 0.20 + t * (0.400 - 0.20),
        epic = 0.05 + t * (0.125 - 0.05),
        legendary = t * 0.030
    )
}

data class ChampionLoadout(
    val championId: String,
    val isUnlocked: Boolean = true,
    val starLevel: Int = 1,
    val championLevel: Int = 1,
    val championXp: Int = 0,
    val fragments: Int = 10,
    val unlockedConstellationNodes: List<String> = emptyList(),
    val equippedRelics: List<String?> = listOf(null, null, null),
    val unlockedRelicSlots: Int = 1,
    val cosmicForgedSlots: Int = 0
) {
    val maxNexusHp: Int
        get() {
            val champ = CardCatalog.findChampionOrNull(championId)
            val baseHp = champ?.baseMaxHp ?: 30
            val levelHpBonus = ((championLevel.coerceIn(1, 30) - 1) * 10) / 29
            val nodeHpBonus = champ?.constellationNodes
                ?.filter { unlockedConstellationNodes.contains(it.id) }
                ?.sumOf { it.bonusMaxHp } ?: 0
            return baseHp + levelHpBonus + nodeHpBonus
        }

    val victoryHeal: Int
        get() = when {
            championLevel >= 24 -> 4
            championLevel >= 12 -> 3
            else -> 2
        }

    val startingGold: Int
        get() {
            val champ = CardCatalog.findChampionOrNull(championId)
            val levelGold = 100 + ((championLevel.coerceIn(1, 30) - 1) * 200) / 29
            val nodeGold = champ?.constellationNodes
                ?.filter { unlockedConstellationNodes.contains(it.id) }
                ?.sumOf { it.bonusStartingGold } ?: 0
            return levelGold + nodeGold
        }

    val startingRerolls: Int
        get() {
            val champ = CardCatalog.findChampionOrNull(championId)
            val levelRerolls = 2 + (if (championLevel >= 10) 1 else 0) + (if (championLevel >= 22) 1 else 0)
            val nodeRerolls = champ?.constellationNodes
                ?.filter { unlockedConstellationNodes.contains(it.id) }
                ?.sumOf { it.bonusRerolls } ?: 0
            return levelRerolls + nodeRerolls
        }

    val startingManaBonus: Int
        get() = if (starLevel >= 2) 1 else 0

    val rarityOdds: RarityOdds
        get() = rarityOddsForLevel(championLevel)
}

enum class NodeType(val displayName: String, val shortCode: String) {
    START("Khởi Hành", "KHỞI ĐẦU"),
    COMBAT("Giao Chiến", "TRẬN"),
    ELITE_COMBAT("Kẻ Địch Tinh Anh", "TINH ANH"),
    MID_BOSS("Trùm Giữa Chặng", "TRÙM PHỤ"),
    FINAL_BOSS("Trùm Cuối", "TRÙM CUỐI"),
    POWER_NODE("Đền Thờ Sức Mạnh", "SỨC MẠNH"),
    HEALER_NODE("Thánh Địa Hồi Phục", "HỒI PHỤC"),
    SHOP_NODE("Thương Nhân Lang Thang", "CỬA HÀNG"),
    REINFORCEMENT_NODE("Quân Tiếp Viện", "TIẾP VIỆN"),
    ITEM_CHEST("Rương Trang Bị", "RƯƠNG ĐỒ"),
    NARRATIVE_EVENT("Sự Kiện Bí Ẩn", "SỰ KIỆN")
}

data class MapNode(
    val id: String,
    val act: Int, // 1 = Start to Mid-Boss, 2 = Mid-Boss to Final Boss
    val row: Int, // 0..6 vertical progression
    val column: Int, // 0, 1, 2 horizontal branching lane
    val type: NodeType,
    val title: String,
    val subtitle: String,
    val enemyName: String = "",
    val enemyPowerTitle: String = "",
    val enemyPowerDescription: String = "",
    val enemyMaxHp: Int = 20,
    val enemyStartingMana: Int = 1,
    val enemyDeckCardIds: List<String> = emptyList(),
    val goldReward: Int = 60,
    val nextNodeIds: List<String> = emptyList()
)

data class GlobalMutator(
    val id: String,
    val name: String,
    val description: String
)

data class AdventureConfig(
    val id: String,
    val name: String,
    val region: Region,
    val starRating: Float,
    val difficultyLabel: String,
    val description: String,
    val bossName: String,
    val rewardXp: Int,
    val rewardFragments: Int,
    val mutators: List<GlobalMutator>,
    val mapNodes: List<MapNode>
)

data class RunState(
    val runId: String,
    val championId: String,
    val adventureId: String,
    val currentNodeId: String,
    val completedNodeIds: List<String> = emptyList(),
    val currentNexusHp: Int,
    val maxNexusHp: Int,
    val gold: Int,
    val rerollsRemaining: Int,
    val revivesRemaining: Int = 1,
    val activePowerIds: List<String> = emptyList(),
    val runDeck: List<DeckCardEntry> = emptyList(),
    val battlesWon: Int = 0,
    val isVictory: Boolean = false,
    val isDefeat: Boolean = false
)

data class LegendProfile(
    val legendLevel: Int = 1,
    val legendPoints: Int = 0,
    val wildFragments: Int = 30,
    val starCrystals: Int = 10,
    val novaCrystals: Int = 1,
    val gemstones: Int = 25,
    val ownedRelicIds: List<String> = listOf("relic_stalkers_blade", "relic_archangels_staff", "relic_ludens_tempest", "relic_guinsoos_rageblade"),
    val completedAdventureIds: List<String> = emptyList(),
    val totalRunsWon: Int = 0
) {
    val pointsToNextLevel: Int
        get() = legendLevel * 100
}
