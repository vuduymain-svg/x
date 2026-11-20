package com.example.data.repository

import android.content.Context
import com.example.data.local.GameDatabase
import com.example.data.local.dao.GameDao
import com.example.data.local.entity.ChampionLoadoutEntity
import com.example.data.local.entity.PlayerMetaEntity
import com.example.data.local.entity.SavedRunEntity
import com.example.model.CardCatalog
import com.example.model.ChampionLoadout
import com.example.model.DeckCardEntry
import com.example.model.LegendProfile
import com.example.model.RunState
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class GameRepository(
    private val dao: GameDao,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
    private val runStateAdapter = moshi.adapter(RunState::class.java)

    val championLoadouts: Flow<Map<String, ChampionLoadout>> =
        dao.observeChampionLoadouts().map { list ->
            list.associate { it.championId to it.toDomain() }
        }

    val legendProfile: Flow<LegendProfile> =
        dao.observePlayerMeta().map { it?.toDomain() ?: LegendProfile() }

    val activeSavedRun: Flow<RunState?> =
        dao.observeSavedRun().map { entity ->
            entity?.let { deserializeRunState(it.runJson) }
        }

    suspend fun ensureSeeded() = withContext(ioDispatcher) {
        val allRelicIds = CardCatalog.relics.map { it.id }
        val existingMeta = dao.getPlayerMeta()
        if (existingMeta == null) {
            dao.upsertPlayerMeta(
                PlayerMetaEntity.fromDomain(
                    LegendProfile(
                        legendLevel = 30,
                        legendPoints = 0,
                        wildFragments = 999_999,
                        starCrystals = 999_999,
                        novaCrystals = 999_999,
                        gemstones = 999_999,
                        ownedRelicIds = allRelicIds
                    )
                )
            )
        }

        val existingSaved = dao.getSavedRun()?.let { deserializeRunState(it.runJson) }
        if (existingSaved != null &&
            (CardCatalog.findChampionOrNull(existingSaved.championId) == null ||
                com.example.model.AdventureCatalog.findAdventureOrNull(existingSaved.adventureId) == null)
        ) {
            dao.clearSavedRun()
        }

        for (champ in CardCatalog.champions) {
            val existing = dao.getChampionLoadout(champ.id)
            if (existing == null) {
                val initialRelics = if (champ.id == "champ_anivia") {
                    listOf("relic_ludens_tempest", "relic_starforged", "relic_archangels_staff")
                } else {
                    listOf(null, null, null)
                }
                val loadout = ChampionLoadout(
                    championId = champ.id,
                    isUnlocked = true,
                    starLevel = if (champ.id == "champ_anivia") 2 else 1,
                    championLevel = if (champ.id == "champ_anivia") 30 else 4,
                    championXp = 0,
                    fragments = 99_999,
                    unlockedConstellationNodes = emptyList(),
                    equippedRelics = initialRelics,
                    unlockedRelicSlots = 3,
                    cosmicForgedSlots = if (champ.id == "champ_anivia") 3 else 0
                )
                dao.upsertChampionLoadout(ChampionLoadoutEntity.fromDomain(loadout))
            }
        }
    }

    suspend fun saveChampionLoadout(loadout: ChampionLoadout) = withContext(ioDispatcher) {
        dao.upsertChampionLoadout(ChampionLoadoutEntity.fromDomain(loadout))
    }

    suspend fun saveLegendProfile(profile: LegendProfile) = withContext(ioDispatcher) {
        dao.upsertPlayerMeta(PlayerMetaEntity.fromDomain(profile))
    }

    suspend fun saveActiveRun(runState: RunState) = withContext(ioDispatcher) {
        dao.upsertSavedRun(SavedRunEntity(id = 1, runJson = serializeRunState(runState)))
    }

    suspend fun clearActiveRun() = withContext(ioDispatcher) {
        dao.clearSavedRun()
    }

    private fun serializeRunState(run: RunState): String {
        return runStateAdapter.toJson(run)
    }

    private fun deserializeRunState(raw: String): RunState? {
        return try {
            runStateAdapter.fromJson(raw)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: GameRepository? = null

        fun getInstance(context: Context): GameRepository {
            CardCatalog.ensureLocalized(context.applicationContext ?: context)
            return INSTANCE ?: synchronized(this) {
                val db = GameDatabase.getDatabase(context)
                val instance = GameRepository(db.gameDao())
                INSTANCE = instance
                instance
            }
        }
    }
}
