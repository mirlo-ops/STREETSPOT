package com.streetspot.app.data.repository

import com.streetspot.app.data.model.SpotWithModel
import com.streetspot.app.domain.progression.AchievementProgress
import com.streetspot.app.domain.progression.XpCalculation
import com.streetspot.app.domain.statistics.StatisticsSnapshot
import kotlinx.coroutines.flow.Flow

data class HomeSnapshot(
    val totalXp: Int = 0,
    val totalSpots: Int = 0,
    val brandsDiscovered: Int = 0,
    val rareFinds: Int = 0,
    val recentSpots: List<SpotWithModel> = emptyList(),
    val nextAchievement: AchievementProgress? = null,
)

interface ProgressRepository {
    fun observeHome(): Flow<HomeSnapshot>

    fun observeAchievements(): Flow<List<AchievementProgress>>

    fun observeStatistics(): Flow<StatisticsSnapshot>

    suspend fun recalculateWithinTransaction(): XpCalculation

    suspend fun rebuild()
}
