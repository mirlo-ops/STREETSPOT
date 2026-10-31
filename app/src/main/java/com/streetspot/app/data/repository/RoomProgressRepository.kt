package com.streetspot.app.data.repository

import androidx.room.withTransaction
import com.streetspot.app.data.local.StreetSpotDatabase
import com.streetspot.app.data.model.AchievementState
import com.streetspot.app.data.model.CarRarity
import com.streetspot.app.data.model.SpotWithModel
import com.streetspot.app.domain.progression.AchievementChecker
import com.streetspot.app.domain.progression.AchievementProgress
import com.streetspot.app.domain.progression.XpCalculation
import com.streetspot.app.domain.progression.XpCalculator
import com.streetspot.app.domain.statistics.StatisticsCalculator
import com.streetspot.app.domain.statistics.StatisticsSnapshot
import java.time.Duration
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class RoomProgressRepository(
    private val database: StreetSpotDatabase,
) : ProgressRepository {
    override fun observeHome(): Flow<HomeSnapshot> = combine(
        database.spotDao().observeAllWithModel(),
        database.xpTransactionDao().observeTotalXp(),
        database.achievementStateDao().observeAll(),
    ) { spots, totalXp, states ->
        val achievements = attachUnlockTimes(spots, states)
        HomeSnapshot(
            totalXp = totalXp,
            totalSpots = spots.size,
            brandsDiscovered = spots.map(SpotWithModel::brandId).distinct().size,
            rareFinds = spots.asSequence()
                .filter { it.modelRarity == CarRarity.RARE || it.modelRarity == CarRarity.LEGENDARY }
                .map { it.spot.carModelId }
                .distinct()
                .count(),
            recentSpots = spots.take(2),
            nextAchievement = AchievementChecker.next(achievements),
        )
    }

    override fun observeAchievements(): Flow<List<AchievementProgress>> = combine(
        database.spotDao().observeAllWithModel(),
        database.achievementStateDao().observeAll(),
        ::attachUnlockTimes,
    )

    override fun observeStatistics(): Flow<StatisticsSnapshot> = combine(
        database.spotDao().observeAllWithModel(),
        monthlyClock(),
    ) { spots, now ->
        StatisticsCalculator.calculate(
            spots = spots,
            now = Instant.ofEpochMilli(now),
            zoneId = ZoneId.systemDefault(),
        )
    }

    override suspend fun recalculateWithinTransaction(): XpCalculation {
        val spots = database.spotDao().getAll()
        val models = database.carModelDao().getAllWithBrand()
        val calculation = XpCalculator.calculate(
            spots = spots,
            models = models,
        )
        val dao = database.xpTransactionDao()
        dao.deleteAll()
        if (calculation.transactions.isNotEmpty()) dao.insertAll(calculation.transactions)

        val spotsWithModels = spots.map { spot ->
            val catalog = checkNotNull(models.firstOrNull { it.model.id == spot.carModelId }) {
                "Spot ${spot.id} references missing model ${spot.carModelId}"
            }
            SpotWithModel(
                spot = spot,
                brandId = catalog.brand.id,
                brandName = catalog.brand.name,
                modelName = catalog.model.name,
                modelGeneration = catalog.model.generation,
                modelCategory = catalog.model.category,
                modelRarity = catalog.model.rarity,
            )
        }
        val existingStates = database.achievementStateDao().getAll().associateBy(AchievementState::achievementId)
        val now = System.currentTimeMillis()
        val refreshedStates = AchievementChecker.calculate(spotsWithModels).map { achievement ->
            val previousUnlock = existingStates[achievement.id.storageId]?.unlockedAt
            AchievementState(
                achievementId = achievement.id.storageId,
                unlockedAt = if (achievement.isUnlocked) previousUnlock ?: now else null,
            )
        }
        database.achievementStateDao().deleteAll()
        database.achievementStateDao().putAll(refreshedStates)
        return calculation
    }

    override suspend fun rebuild() {
        database.withTransaction { recalculateWithinTransaction() }
    }

    private fun attachUnlockTimes(
        spots: List<SpotWithModel>,
        states: List<AchievementState>,
    ): List<AchievementProgress> {
        val unlockTimes = states.associate { it.achievementId to it.unlockedAt }
        return AchievementChecker.calculate(spots).map { achievement ->
            achievement.copy(unlockedAt = unlockTimes[achievement.id.storageId])
        }
    }

    private fun monthlyClock(): Flow<Long> = flow {
        while (currentCoroutineContext().isActive) {
            val now = Instant.now()
            emit(now.toEpochMilli())
            val zoneId = ZoneId.systemDefault()
            val nextMonth = YearMonth.from(now.atZone(zoneId))
                .plusMonths(1)
                .atDay(1)
                .atStartOfDay(zoneId)
                .toInstant()
            delay(Duration.between(now, nextMonth).toMillis().coerceAtLeast(1_000L))
        }
    }

}
