package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.model.ChampionLoadout
import com.example.model.LegendProfile

@Entity(tableName = "champion_loadouts")
data class ChampionLoadoutEntity(
    @PrimaryKey val championId: String,
    val isUnlocked: Boolean,
    val starLevel: Int,
    val championLevel: Int,
    val championXp: Int,
    val fragments: Int,
    val unlockedConstellationNodesCsv: String,
    val relicSlot0: String?,
    val relicSlot1: String?,
    val relicSlot2: String?,
    val unlockedRelicSlots: Int,
    val cosmicForgedSlots: Int
) {
    fun toDomain(): ChampionLoadout = ChampionLoadout(
        championId = championId,
        isUnlocked = isUnlocked,
        starLevel = starLevel,
        championLevel = championLevel,
        championXp = championXp,
        fragments = fragments,
        unlockedConstellationNodes = if (unlockedConstellationNodesCsv.isBlank()) emptyList()
        else unlockedConstellationNodesCsv.split(",").filter { it.isNotBlank() },
        equippedRelics = listOf(relicSlot0, relicSlot1, relicSlot2),
        unlockedRelicSlots = unlockedRelicSlots,
        cosmicForgedSlots = cosmicForgedSlots
    )

    companion object {
        fun fromDomain(domain: ChampionLoadout): ChampionLoadoutEntity = ChampionLoadoutEntity(
            championId = domain.championId,
            isUnlocked = domain.isUnlocked,
            starLevel = domain.starLevel,
            championLevel = domain.championLevel,
            championXp = domain.championXp,
            fragments = domain.fragments,
            unlockedConstellationNodesCsv = domain.unlockedConstellationNodes.joinToString(","),
            relicSlot0 = domain.equippedRelics.getOrNull(0),
            relicSlot1 = domain.equippedRelics.getOrNull(1),
            relicSlot2 = domain.equippedRelics.getOrNull(2),
            unlockedRelicSlots = domain.unlockedRelicSlots,
            cosmicForgedSlots = domain.cosmicForgedSlots
        )
    }
}

@Entity(tableName = "player_meta")
data class PlayerMetaEntity(
    @PrimaryKey val id: Int = 1,
    val legendLevel: Int,
    val legendPoints: Int,
    val wildFragments: Int,
    val starCrystals: Int,
    val novaCrystals: Int,
    val gemstones: Int,
    val ownedRelicIdsCsv: String,
    val completedAdventureIdsCsv: String,
    val totalRunsWon: Int
) {
    fun toDomain(): LegendProfile = LegendProfile(
        legendLevel = legendLevel,
        legendPoints = legendPoints,
        wildFragments = wildFragments,
        starCrystals = starCrystals,
        novaCrystals = novaCrystals,
        gemstones = gemstones,
        ownedRelicIds = if (ownedRelicIdsCsv.isBlank()) emptyList()
        else ownedRelicIdsCsv.split(",").filter { it.isNotBlank() },
        completedAdventureIds = if (completedAdventureIdsCsv.isBlank()) emptyList()
        else completedAdventureIdsCsv.split(",").filter { it.isNotBlank() },
        totalRunsWon = totalRunsWon
    )

    companion object {
        fun fromDomain(domain: LegendProfile): PlayerMetaEntity = PlayerMetaEntity(
            id = 1,
            legendLevel = domain.legendLevel,
            legendPoints = domain.legendPoints,
            wildFragments = domain.wildFragments,
            starCrystals = domain.starCrystals,
            novaCrystals = domain.novaCrystals,
            gemstones = domain.gemstones,
            ownedRelicIdsCsv = domain.ownedRelicIds.joinToString(","),
            completedAdventureIdsCsv = domain.completedAdventureIds.joinToString(","),
            totalRunsWon = domain.totalRunsWon
        )
    }
}

@Entity(tableName = "saved_runs")
data class SavedRunEntity(
    @PrimaryKey val id: Int = 1,
    val runJson: String
)
