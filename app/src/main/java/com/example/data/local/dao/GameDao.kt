package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.ChampionLoadoutEntity
import com.example.data.local.entity.PlayerMetaEntity
import com.example.data.local.entity.SavedRunEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM champion_loadouts")
    fun observeChampionLoadouts(): Flow<List<ChampionLoadoutEntity>>

    @Query("SELECT * FROM champion_loadouts WHERE championId = :id LIMIT 1")
    suspend fun getChampionLoadout(id: String): ChampionLoadoutEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChampionLoadout(entity: ChampionLoadoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllChampionLoadouts(entities: List<ChampionLoadoutEntity>)

    @Query("SELECT * FROM player_meta WHERE id = 1 LIMIT 1")
    fun observePlayerMeta(): Flow<PlayerMetaEntity?>

    @Query("SELECT * FROM player_meta WHERE id = 1 LIMIT 1")
    suspend fun getPlayerMeta(): PlayerMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayerMeta(entity: PlayerMetaEntity)

    @Query("DELETE FROM champion_loadouts WHERE championId != :keepId")
    suspend fun deleteOtherChampionLoadouts(keepId: String)

    @Query("SELECT * FROM saved_runs WHERE id = 1 LIMIT 1")
    fun observeSavedRun(): Flow<SavedRunEntity?>

    @Query("SELECT * FROM saved_runs WHERE id = 1 LIMIT 1")
    suspend fun getSavedRun(): SavedRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedRun(entity: SavedRunEntity)

    @Query("DELETE FROM saved_runs WHERE id = 1")
    suspend fun clearSavedRun()
}
