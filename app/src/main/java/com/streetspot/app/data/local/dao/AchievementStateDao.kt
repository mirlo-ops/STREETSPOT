package com.streetspot.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.streetspot.app.data.model.AchievementState
import kotlinx.coroutines.flow.Flow

@Dao
interface AchievementStateDao {
    @Query("SELECT * FROM achievement_states ORDER BY achievementId")
    fun observeAll(): Flow<List<AchievementState>>

    @Query("SELECT * FROM achievement_states WHERE achievementId = :achievementId LIMIT 1")
    suspend fun find(achievementId: String): AchievementState?

    @Query("SELECT * FROM achievement_states ORDER BY achievementId")
    suspend fun getAll(): List<AchievementState>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putAll(states: List<AchievementState>)

    @Query("DELETE FROM achievement_states")
    suspend fun deleteAll()
}
